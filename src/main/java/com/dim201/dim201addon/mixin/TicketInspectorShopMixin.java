package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.TicketInspectorFlags;
import com.dim201.dim201addon.TicketInspectorPlayerComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 补票员服务端 Mixin：
 *
 * 唯一职责：tryBuy 成功后给周围 10 格内的补票员发发票物品
 *   - 通过 total_cost 变化判断购买是否成功
 *   - 仅对实际价格 > 0 的购买触发发票（价格为 0 的条目跳过）
 *   - 通过 TicketInspectorFlags（独立工具类）排除补票员自己的发票消费
 *
 * 注意：
 *   - 本 Mixin 类内不包含任何 public static 方法（Fabric Loader 0.18.1 + Mixin 0.8.7 禁止）
 *   - ThreadLocal 标志已迁移到 TicketInspectorFlags 工具类
 *   - 商店「清空发票」选项由 TicketInspectorRole.getShopEntries() 直接返回，不需要 Mixin
 */
@Mixin(SREPlayerShopComponent.class)
public abstract class TicketInspectorShopMixin {

    @Shadow(remap = false)
    private Player player;

    @Shadow(remap = false)
    public int total_cost;

    @Unique
    private int dim201$totalCostBefore = 0;

    @Inject(method = "tryBuy(I)V", at = @At("HEAD"), remap = false)
    private void dim201$captureTotalCost(int index, CallbackInfo ci) {
        this.dim201$totalCostBefore = this.total_cost;
    }

    // ====================================================================
    // tryBuy 完成 → 给周围补票员发发票
    // ====================================================================
    @Inject(
            method = "tryBuy(I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/wifi/starrailexpress/cca/SREPlayerShopComponent;sync()V",
                    remap = false,
                    shift = At.Shift.BEFORE
            ),
            remap = false,
            cancellable = false
    )
    private void dim201$ticketInspector$afterPurchaseSuccess(int index, CallbackInfo ci) {
        if (this.total_cost <= this.dim201$totalCostBefore) return;
        // 发票消费操作跳过（防止补票员用发票时又触发新发票生成）
        if (TicketInspectorFlags.isInvoicePurchasing()) return;
        if (!(this.player instanceof ServerPlayer buyer)) return;

        try {
            var gw = SREGameWorldComponent.KEY.get(buyer.level());
            if (gw == null) return;
            SRERole role = gw.getRole(buyer);
            if (role == null) return;
            List<ShopEntry> entries = ShopContent.getShopEntries(role.getIdentifier());
            if (entries == null || index < 0 || index >= entries.size()) return;

            ShopEntry entry = entries.get(index);
            ItemStack stack = entry.stack();
            if (stack == null || stack.isEmpty()) return;

            Item item = stack.getItem();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || item == Items.AIR) return;

            String itemId = id.toString();
            String itemName = stack.getHoverName().getString();
            int effectivePrice = io.wifi.starrailexpress.cca.DynamicShopComponent.KEY.get(buyer)
                    .effectivePrice(entry);

            // 仅价格 > 0 的购买才触发发票（价格为 0 的条目如疯狂模式/关灯等不触发）
            if (effectivePrice <= 0) return;

            TicketInspectorPlayerComponent.onPlayerBoughtNearby(buyer, itemId, itemName, effectivePrice);
        } catch (Throwable ignored) {
        }
    }
}
