package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空白卡牌：天弓千亦的能力卡牌
 *
 * <p>右键消耗类：按下右键夺取面前3格内玩家的全部物品。
 *
 * <p>获得时被动：玩家获得此卡牌时清空自身背包（保留空白卡牌本身）。
 * 原理：通过 ServerTickEvents 检测玩家新持有卡牌 → 清空背包并标记，
 * 避免后续重复清空。
 */
public class BlankCard extends Item {

    /** 夺取范围：玩家面前 3 格 */
    private static final double STEAL_RANGE = 3.0;

    /** 已初始化的玩家（获得卡牌后已清空背包） */
    private static final ConcurrentHashMap<UUID, Boolean> INITIALIZED = new ConcurrentHashMap<>();

    /** 已注册 tick 监听器 */
    private static boolean tickRegistered = false;

    /**
     * 对局结束时清理状态（由 ResetPlayerEvent 调用）
     * 清空 INITIALIZED 标记，防止下局购买空白卡牌时因残留标记而跳过清空背包
     */
    public static void resetState() {
        INITIALIZED.clear();
    }

    public BlankCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 16));
        registerTickListener();
    }

    /** 注册 tick 事件，检测玩家新持有卡牌时清空背包 */
    private synchronized void registerTickListener() {
        if (tickRegistered) return;
        tickRegistered = true;

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!(world instanceof ServerLevel sl)) return;
            for (ServerPlayer sp : sl.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(sp)) continue;
                UUID uuid = sp.getUUID();
                if (CardUtils.hasInInventory(sp, this) && !INITIALIZED.containsKey(uuid)) {
                    clearInventoryKeepCard(sp);
                    INITIALIZED.put(uuid, Boolean.TRUE);
                    CardUtils.notify(sp, "§d空白§r§7 卡面空无一物，你的物品已被清空");
                }
            }
        });
    }

    /**
     * 清空玩家背包，仅保留空白卡牌本身
     * 原理：遍历玩家背包所有槽位，若非空白卡牌则清空
     */
    private void clearInventoryKeepCard(ServerPlayer sp) {
        // 收集空白卡牌的数量（因为 stacksTo=16，可能有多张）
        int blankCount = CardUtils.countInInventory(sp, this);

        // 清空主背包
        for (int i = 0; i < sp.getInventory().items.size(); i++) {
            ItemStack stack = sp.getInventory().items.get(i);
            if (!stack.is(this)) {
                sp.getInventory().items.set(i, ItemStack.EMPTY);
            }
        }
        // 清空盔甲位
        for (int i = 0; i < sp.getInventory().armor.size(); i++) {
            sp.getInventory().armor.set(i, ItemStack.EMPTY);
        }
        // 清空副手
        for (int i = 0; i < sp.getInventory().offhand.size(); i++) {
            sp.getInventory().offhand.set(i, ItemStack.EMPTY);
        }

        // 确保保留对应数量的空白卡牌
        // 由于 items 中原来的空白卡牌数量可能不足（被清空了），需要重新放置
        int remaining = blankCount;
        for (int i = 0; i < sp.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = sp.getInventory().items.get(i);
            if (stack.is(this)) {
                remaining -= stack.getCount();
            }
        }
        // 如果还有未放置的空白卡牌数量，放入空槽
        int toPlace = remaining;
        if (toPlace > 0) {
            for (int i = 0; i < sp.getInventory().items.size() && toPlace > 0; i++) {
                if (sp.getInventory().items.get(i).isEmpty()) {
                    int n = Math.min(toPlace, new ItemStack(this).getMaxStackSize());
                    sp.getInventory().items.set(i, new ItemStack(this, n));
                    toPlace -= n;
                }
            }
        }

        sp.getInventory().setChanged();
        sp.inventoryMenu.broadcastChanges();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        // 查找面前 3 格内的存活玩家
        Vec3 look = sp.getLookAngle();
        Vec3 start = sp.position();
        Vec3 end = start.add(look.scale(STEAL_RANGE));
        AABB box = new AABB(start, end).inflate(1.5, 1.5, 1.5);

        List<ServerPlayer> targets = sp.serverLevel().getEntitiesOfClass(ServerPlayer.class, box, e -> {
            if (!GameUtils.isPlayerAliveAndSurvival(e)) return false;
            // 必须在玩家视线方向前方（水平角度 ±90° 内）
            Vec3 dir = e.position().subtract(sp.position());
            double dot = look.x * dir.x + look.z * dir.z;
            return dot > 0; // 前方
        });

        if (targets.isEmpty()) {
            CardUtils.notify(sp, "§c面前3格内没有可夺取的玩家");
            return InteractionResultHolder.pass(stack);
        }

        ServerPlayer target = targets.get(0);

        // 夺取目标背包所有物品
        int stolenCount = stealAllItems(sp, target);

        if (stolenCount > 0) {
            CardUtils.notify(sp, "§d空白§r§7 从 §6" + target.getGameProfile().getName()
                    + "§7 手中夺取了 §6" + stolenCount + " §7件物品！");
            CardUtils.notify(target, "§c你的物品被空白夺走了！");
        } else {
            CardUtils.notify(sp, "§7目标背包空空如也");
        }

        // 消耗
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 从目标玩家背包夺取所有物品给使用者
     *
     * @return 成功夺取的物品件数
     */
    private int stealAllItems(ServerPlayer thief, ServerPlayer target) {
        int stolen = 0;

        // 遍历目标主背包
        for (int i = 0; i < target.getInventory().items.size(); i++) {
            ItemStack item = target.getInventory().items.get(i);
            if (item.isEmpty()) continue;
            stolen += transferItem(thief, target, item, i);
        }
        // 遍历目标盔甲
        for (int i = 0; i < target.getInventory().armor.size(); i++) {
            ItemStack item = target.getInventory().armor.get(i);
            if (item.isEmpty()) continue;
            stolen += transferItem(thief, target, item, i);
        }
        // 遍历目标副手
        for (int i = 0; i < target.getInventory().offhand.size(); i++) {
            ItemStack item = target.getInventory().offhand.get(i);
            if (item.isEmpty()) continue;
            stolen += transferItem(thief, target, item, i);
        }

        target.getInventory().setChanged();
        target.inventoryMenu.broadcastChanges();
        thief.getInventory().setChanged();
        thief.inventoryMenu.broadcastChanges();

        return stolen;
    }

    /**
     * 转移单个物品堆，返回转移的件数
     */
    private int transferItem(ServerPlayer thief, ServerPlayer target, ItemStack item, int slotIndex) {
        int count = item.getCount();
        ItemStack copy = item.copy();
        copy.setCount(count);

        // 使用 SRE 原生方法给予物品（避免被框架拦截）
        boolean given = RoleUtils.insertStackInFreeSlot(thief, copy);
        if (given) {
            // 从目标背包移除
            item.setCount(0);
            return count;
        } else {
            // 给予失败（目标背包已满），尝试掉落到地上
            target.serverLevel().addFreshEntity(
                    new net.minecraft.world.entity.item.ItemEntity(target.serverLevel(),
                            target.getX(), target.getY(), target.getZ(), copy));
            item.setCount(0);
            return count;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7天弓千亦的能力，卡面空无一物"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7获得时清空背包（保留本卡牌），右键夺取面前3格内1名玩家的全部物品"));
        tooltip.add(Component.literal("§6交易价格: " + com.dim201.dim201addon.item.Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8这张卡没有意义，因为它的内容就是空白"));
    }
}
