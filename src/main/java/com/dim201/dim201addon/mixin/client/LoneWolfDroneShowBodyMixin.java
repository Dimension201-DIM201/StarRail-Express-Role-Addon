package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.ModComponents;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 独狼操控无人机期间让原地本体可见。
 *
 * <p>原版 EntityRenderDispatcher.shouldRender 在「本地玩家 + 第一人称」时返回 false，
 * 导致 setCameraEntity(drone) 后本体完全不可见。操控无人机时强制允许渲染本体。
 */
@Mixin(EntityRenderDispatcher.class)
public class LoneWolfDroneShowBodyMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void dim201$showBodyWhileDrone(Entity entity, Frustum frustum, double x, double y, double z,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player) {
            LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(player);
            if (comp != null && comp.droneActive && comp.controllingDrone) {
                cir.setReturnValue(true);
            }
        }
    }
}
