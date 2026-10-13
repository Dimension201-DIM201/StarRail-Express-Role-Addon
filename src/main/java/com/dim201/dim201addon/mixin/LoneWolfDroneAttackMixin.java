package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.ModComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 独狼操控无人机期间禁止本体攻击。
 *
 * <p>左键用于发射射线而非武器攻击，服务端拦截防止误伤。
 */
@Mixin(Player.class)
public abstract class LoneWolfDroneAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void dim201$onAttackWhileDrone(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(sp);
        if (comp != null && comp.droneActive && comp.controllingDrone) {
            ci.cancel();
        }
    }
}
