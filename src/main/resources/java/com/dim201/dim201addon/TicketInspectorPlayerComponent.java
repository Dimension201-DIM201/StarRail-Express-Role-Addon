package com.dim201.dim201addon;

import com.dim201.dim201addon.item.InvoiceItem;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 补票员玩家数据组件：
 * 发票现在是实际物品（dim201addon:invoice），不再在Component中维护列表
 * 只保留：检测半径常量、发放发票方法、统计玩家背包发票数量
 */
public class TicketInspectorPlayerComponent implements RoleComponent {

    public static final int DETECT_RADIUS = 10;

    private final Player player;
    /** 占位：保持列表字段不删除，避免NBT读取兼容，但不会再写入 */
    @Deprecated
    public final CopyOnWriteArrayList<Object> invoices = new CopyOnWriteArrayList<>();

    public TicketInspectorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public void init() {}

    @Override
    public void clear() {}

    public void sync() {
        ModComponents.TICKET_INSPECTOR.sync(this.player);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registry) {}

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registry) {}

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registry) {}

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registry) {}

    // ====================================================================
    // 添加发票：当周围10格有人购买时调用，直接给发票物品
    // ====================================================================
    public static void onPlayerBoughtNearby(@NotNull ServerPlayer buyer,
                                            @NotNull String itemId,
                                            @NotNull String itemName,
                                            int originalPrice) {
        if (itemId == null || itemId.isEmpty()) return;
        for (ServerPlayer p : buyer.serverLevel().getServer().getPlayerList().getPlayers()) {
            if (p == buyer) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            double distSq = p.distanceToSqr(buyer);
            if (distSq > DETECT_RADIUS * DETECT_RADIUS) continue;

            var gw = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(p.level());
            if (gw == null || !gw.isRole(p, com.dim201.dim201addon.role.TicketInspectorRole.TICKET_INSPECTOR)) continue;

            // 直接生成发票物品并塞入玩家背包
            ItemStack invoiceStack = InvoiceItem.createInvoice(itemId, itemName, originalPrice);
            boolean ok = SREItemUtils.insertStackInFreeSlot(p, invoiceStack);
            if (!ok) p.drop(invoiceStack, false);

            int markup = (int) Math.ceil(originalPrice * InvoiceItem.PRICE_MULTIPLIER);
            p.displayClientMessage(
                Component.literal("§a§l「神秘发票」§r 获得 1 张  ")
                    .append(Component.literal("补票价 " + markup + " 金币")
                        .withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.WHITE),
                true
            );
        }
    }

    /** 返回玩家背包中未使用的发票数量 */
    public int getUnusedInvoiceCount() {
        if (player == null) return 0;
        int c = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s != null && s.is(com.dim201.dim201addon.item.Dim201ModItems.INVOICE)) c++;
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s != null && s.is(com.dim201.dim201addon.item.Dim201ModItems.INVOICE)) c++;
        }
        return c;
    }
}
