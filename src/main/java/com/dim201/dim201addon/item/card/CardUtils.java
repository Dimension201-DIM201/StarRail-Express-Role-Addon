package com.dim201.dim201addon.item.card;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import io.wifi.starrailexpress.util.SREItemUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力卡牌通用工具类
 *
 * <p>封装所有卡牌重复使用的工具方法，避免每张卡牌手写。
 * 每张卡牌直接继承 Item（不强制走基类），需要时调用此类静态方法即可。
 */
public final class CardUtils {

    // ==================== 基础 Properties 工厂 ====================

    /** 生成卡牌默认 Properties：stacksTo(1)，按稀有度上色 */
    public static Item.Properties cardProps(Rarity rarity) {
        return new Item.Properties().stacksTo(1).rarity(rarity);
    }

    /** 生成可堆叠卡牌 Properties（如消耗品按 16 堆叠） */
    public static Item.Properties cardProps(Rarity rarity, int maxStack) {
        return new Item.Properties().stacksTo(maxStack).rarity(rarity);
    }

    // ==================== 右键使用的通用流程 ====================

    /**
     * 右键前置检查：仅服务端执行 + 玩家存活 + 冷却检查
     *
     * @return null 表示通过检查；否则返回应显示的失败原因
     */
    @Nullable
    public static Component preUseCheck(ServerPlayer sp, Item card, int cooldownTicks) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return Component.literal("§c你当前无法使用此物品！");
        }
        if (cooldownTicks > 0 && sp.getCooldowns().isOnCooldown(card)) {
            return Component.literal("§c冷却中...");
        }
        return null;
    }

    /** 给玩家施加冷却 */
    public static void applyCooldown(ServerPlayer sp, Item card, int ticks) {
        if (ticks > 0) {
            sp.getCooldowns().addCooldown(card, ticks);
        }
    }

    /** 发送 actionbar 提示（不会污染聊天栏） */
    public static void notify(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg), true);
    }

    /** 发送 chat 提示（带前缀，用于重要通知） */
    public static void chat(ServerPlayer sp, String msg) {
        sp.displayClientMessage(Component.literal(msg), false);
    }

    // ==================== 角色/阵营检查 ====================

    /** 是否好人阵营（isInnocent=true） */
    public static boolean isInnocent(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null) return false;
        var role = gw.getRole(sp);
        return role != null && role.isInnocent();
    }

    /** 是否杀手阵营（canUseKiller=true） */
    public static boolean isKiller(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null) return false;
        var role = gw.getRole(sp);
        return role != null && role.canUseKiller();
    }

    /** 检查是否对局进行中 */
    public static boolean isGameRunning(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        return gw != null && gw.isRunning();
    }

    // ==================== 背包操作（严格使用 SRE 原生方法） ====================

    /**
     * 给玩家物品（对局中只开放快捷栏，塞大背包物品会不可见）：
     * <ol>
     *   <li>SRE 原生 {@code insertStackInFreeSlot}：塞入快捷栏 0-8 格（对局发物品的标准语义）</li>
     *   <li>快捷栏满时直接掉落在玩家脚下，可直接拾取</li>
     * </ol>
     * 严禁走 {@code Inventory.add} —— 对局中大背包被锁定，塞进去等于蒸发。
     */
    public static boolean giveItem(ServerPlayer sp, ItemStack stack) {
        // 1. SRE 原生快捷栏塞入（只填 0-8 格，对局可见）
        if (SREItemUtils.insertStackInFreeSlot(sp, stack)) {
            syncInventory(sp);
            return true;
        }
        // 2. 快捷栏满 → 掉落玩家脚下（不塞大背包）
        sp.drop(stack, false);
        syncInventory(sp);
        return true;
    }

    /** 同步背包变更到客户端 */
    private static void syncInventory(ServerPlayer sp) {
        sp.getInventory().setChanged();
        sp.inventoryMenu.broadcastChanges();
    }

    /** 扣除 1 个手持物品（右键消耗场景） */
    public static void consumeHeld(ServerPlayer sp, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = sp.getItemInHand(hand);
        if (!stack.isEmpty()) {
            stack.shrink(1);
            sp.setItemInHand(hand, stack);
        }
    }

    // ==================== 商店条目工厂 ====================

    /** 快速创建卡牌商店条目（不限购） */
    public static ShopEntry shop(Item card, int price, ShopEntry.Type type) {
        return new ShopEntry(card.getDefaultInstance(), price, type);
    }

    /** 快速创建卡牌商店条目（可自定义名称颜色） */
    public static ShopEntry shop(Item card, int price, ShopEntry.Type type, ChatFormatting color) {
        ItemStack stack = card.getDefaultInstance();
        MutableComponent name = Component.translatable(card.getDescriptionId(stack));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name.withStyle(color));
        return new ShopEntry(stack, price, type);
    }

    /** 批量创建商店条目（角色 getShopEntries 中一行添加多张卡牌） */
    public static List<ShopEntry> shopList(Object... pairs) {
        List<ShopEntry> entries = new ArrayList<>();
        // pairs: Item, int (price), Type, Item, int (price), Type ...
        for (int i = 0; i + 2 < pairs.length; i += 3) {
            entries.add(shop((Item) pairs[i], (Integer) pairs[i + 1], (ShopEntry.Type) pairs[i + 2]));
        }
        return entries;
    }

    // ==================== 背包中是否含有某卡牌（携带生效类使用） ====================

    /** 玩家背包（主包+盔甲+副手）中是否含有指定物品 */
    public static boolean hasInInventory(ServerPlayer sp, Item item) {
        return sp.getInventory().contains(item.getDefaultInstance());
    }

    /** 玩家背包中指定物品的数量（≥0） */
    public static int countInInventory(ServerPlayer sp, Item item) {
        if (item == null) return 0;
        int n = 0;
        for (ItemStack s : sp.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : sp.getInventory().armor) if (s.is(item)) n += s.getCount();
        for (ItemStack s : sp.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** 从背包任意位置消耗一张卡牌（用于携带生效类消耗） */
    public static void consumeOneFromInventory(ServerPlayer sp, Item item) {
        for (ItemStack s : sp.getInventory().items) {
            if (s.is(item)) {
                s.shrink(1);
                sp.getInventory().setChanged();
                sp.inventoryMenu.broadcastChanges();
                return;
            }
        }
        for (ItemStack s : sp.getInventory().offhand) {
            if (s.is(item)) {
                s.shrink(1);
                sp.getInventory().setChanged();
                sp.inventoryMenu.broadcastChanges();
                return;
            }
        }
    }

    // ==================== 金币操作 ====================

    /** 获取玩家金币余额 */
    public static int getGold(ServerPlayer sp) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        return shop != null ? shop.balance : 0;
    }

    /** 尝试扣除金币，余额不足返回 false */
    public static boolean spendGold(ServerPlayer sp, int amount) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop == null || shop.balance < amount) return false;
        shop.setBalance(shop.balance - amount);
        shop.sync();
        return true;
    }

    /** 增加金币（交易出售收入） */
    public static void addGold(ServerPlayer sp, int amount) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop == null) return;
        shop.setBalance(shop.balance + amount);
        shop.sync();
    }

    private CardUtils() {}
}
