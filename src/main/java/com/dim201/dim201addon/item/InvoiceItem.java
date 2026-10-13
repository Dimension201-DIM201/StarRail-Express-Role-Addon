package com.dim201.dim201addon.item;

import com.dim201.dim201addon.TicketInspectorFlags;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 发票物品：
 * - 贴图套用原版纸 (paper)
 * - NBT 存储 itemId/itemName/originalPrice
 * - 右键使用：扣除原价×150%金币，消耗发票，给予对应实际物品并揭晓
 */
public class InvoiceItem extends Item {

    public static final String TAG_ITEM_ID = "item_id";
    public static final String TAG_ITEM_NAME = "item_name";
    public static final String TAG_ORIGINAL_PRICE = "original_price";
    public static final double PRICE_MULTIPLIER = 1.5;

    public InvoiceItem(Properties properties) {
        super(properties);
    }

    /** 创建一张发票物品栈，写入NBT并设置名字/lore */
    public static ItemStack createInvoice(String itemId, String itemName, int originalPrice) {
        ItemStack stack = new ItemStack(Dim201ModItems.INVOICE);
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("§a神秘发票").withStyle(ChatFormatting.GREEN));
        List<Component> lores = new ArrayList<>();
        int markup = (int) Math.ceil(originalPrice * PRICE_MULTIPLIER);
        lores.add(Component.literal("补票价：" + markup + " 金币")
            .withStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GOLD));
        lores.add(Component.literal("右键使用：扣除金币并获得物品")
            .withStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.DARK_GRAY));
        lores.add(Component.literal("使用后揭晓物品内容")
            .withStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.DARK_GRAY));
        lores.add(Component.literal("每张发票限用一次，使用后销毁")
            .withStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.RED));
        stack.set(DataComponents.LORE, new ItemLore(lores));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);

        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        data.putString(TAG_ITEM_ID, itemId);
        data.putString(TAG_ITEM_NAME, itemName == null ? "" : itemName);
        data.putInt(TAG_ORIGINAL_PRICE, originalPrice);
        stack.set(DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(data));
        return stack;
    }

    @Nullable
    public static String getItemId(ItemStack stack) {
        if (stack == null || !stack.is(Dim201ModItems.INVOICE)) return null;
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        var tag = cd.copyTag();
        if (!tag.contains(TAG_ITEM_ID)) return null;
        return tag.getString(TAG_ITEM_ID);
    }

    public static String getItemName(ItemStack stack) {
        if (stack == null || !stack.is(Dim201ModItems.INVOICE)) return "";
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "";
        return cd.copyTag().getString(TAG_ITEM_NAME);
    }

    public static int getOriginalPrice(ItemStack stack) {
        if (stack == null || !stack.is(Dim201ModItems.INVOICE)) return 0;
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getInt(TAG_ORIGINAL_PRICE);
    }

    public static int getMarkupPrice(ItemStack stack) {
        return (int) Math.ceil(getOriginalPrice(stack) * PRICE_MULTIPLIER);
    }

    /**
     * 右键使用发票：
     * 正确执行顺序（防止"扣钱不给物品"）：
     *   1. 校验金币充足
     *   2. 给物品（SRE 原生 RoleUtils.insertStackInFreeSlot，兼容 LimitedInventory）
     *   3. 仅当给物品成功时：扣金币 + 消耗发票 + 累计消费（ThreadLocal 防循环）
     *
     * 为什么用 RoleUtils.insertStackInFreeSlot：
     *   SRE 商店所有 onBuy 给物品的代码（驯马师/猎人/毒箭等）都用这个方法，
     *   它内部已经处理好 LimitedInventory 热栏限制/大背包隐藏/同步等 SRE 细节，
     *   自己实现 setItem/add/drop 会被 SRE 拦截或不同步。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        String itemId = getItemId(stack);
        if (itemId == null) {
            sp.displayClientMessage(Component.literal("§c无效发票").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        var rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return InteractionResultHolder.fail(stack);
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return InteractionResultHolder.fail(stack);

        int markupPrice = getMarkupPrice(stack);
        String revealedName = getItemName(stack);
        if (revealedName == null || revealedName.isEmpty()) revealedName = itemId;

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < markupPrice) {
            sp.displayClientMessage(
                Component.literal("§c金币不足！需要 " + markupPrice + " 金币")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResultHolder.fail(stack);
        }

        // 步骤2：先给物品（SRE 原生方式，RoleUtils.insertStackInFreeSlot 返回 true 才表示成功）
        ItemStack give = new ItemStack(item);
        boolean gaveItem = RoleUtils.insertStackInFreeSlot(sp, give);
        if (!gaveItem) {
            sp.displayClientMessage(
                Component.literal("§c物品发放失败！请检查快捷栏是否有空位")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResultHolder.fail(stack);
        }

        // 步骤3：给物品成功了才扣钱 + 累计消费（ThreadLocal 防止 tryBuy Mixin 误判为普通购买）
        TicketInspectorFlags.markInvoicePurchase();
        try {
            shop.setBalance(shop.balance - markupPrice);
            shop.total_cost += markupPrice;
            shop.sync();
        } finally {
            TicketInspectorFlags.clearInvoicePurchase();
        }

        // 消耗发票
        ItemStack afterStack = stack.copy();
        afterStack.shrink(1);
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.setItemInHand(usedHand, ItemStack.EMPTY);
        }

        // 强制同步（RoleUtils 内部可能已经同步过，兜底再同步一次确保客户端可见）
        sp.getInventory().setChanged();
        try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}

        // 揭晓 + 音效
        sp.displayClientMessage(
            Component.literal("§a补票成功！§7 揭晓物品：" + revealedName
                + "  花费 " + markupPrice + " 金币"),
            true
        );
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);

        return InteractionResultHolder.success(afterStack.isEmpty() ? ItemStack.EMPTY : afterStack);
    }
}
