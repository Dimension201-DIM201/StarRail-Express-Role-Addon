package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.item.Dim201ModItems;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 舍利子拾取限制 Mixin
 *
 * <p>拦截 ItemEntity.playerTouch：当掉落物是舍利子且拾取者非好人阵营时，取消拾取。
 * <p>原理：SRE 没有原生的"特定物品仅特定阵营可拾取"机制，
 *   需通过 Mixin 在拾取入口处拦截。
 */
@Mixin(ItemEntity.class)
public abstract class SariraPickupMixin {

    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void dim201$onPlayerTouch(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide) return;

        ItemStack stack = self.getItem();
        if (!stack.is(Dim201ModItems.SARIRA)) return;

        // 检查玩家角色阵营
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return;

        SRERole role = gameWorld.getRole(player);
        if (role == null) return;  // 无角色时不拦截（让正常拾取流程处理）

        // 非好人阵营不能拾取
        if (!role.isInnocent()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§c舍利子排斥你的气息...只有好人才能拾取"),
                true);
            ci.cancel();
        }
    }
}
