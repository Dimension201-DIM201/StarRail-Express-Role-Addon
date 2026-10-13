package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.ModComponents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 独狼无人机相机旋转修正。
 *
 * <p>setCameraEntity(drone) 后相机旋转来自无人机的 yaw/pitch，
 * 但鼠标修改的是玩家的 yaw/pitch，导致卡顿。
 * 在 Camera.setup 末尾把相机旋转强制设为玩家的 yaw/pitch。
 */
@Mixin(Camera.class)
public abstract class LoneWolfDroneCameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void dim201$droneUsePlayerRotation(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(mc.player);
        if (comp == null || !comp.droneActive || !comp.controllingDrone) return;

        this.setRotation(mc.player.getYRot(), mc.player.getXRot());
    }
}
