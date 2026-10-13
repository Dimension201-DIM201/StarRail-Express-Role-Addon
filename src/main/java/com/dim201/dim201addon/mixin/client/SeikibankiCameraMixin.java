package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 赤蛮奇飞头相机旋转修正。
 *
 * <p>setCameraEntity(head) 后相机旋转来自头颅的 yaw/pitch，
 * 但鼠标修改的是玩家的 yaw/pitch（Mouse.updateMouse 总调用 player.changeLookDirection），
 * 且头颅 yaw/pitch 会被服务端同步包覆盖，导致上下卡顿、头颅不跟随。
 * 在 Camera.setup 末尾把相机旋转强制设为玩家的 yaw/pitch。
 */
@Mixin(Camera.class)
public abstract class SeikibankiCameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void seikibanki$usePlayerRotation(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 仅头颅的操作者（技能激活者）需要用自己的视角覆盖相机旋转
        SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.getNullable(mc.player);
        if (comp == null || !comp.skillActive) return;

        this.setRotation(mc.player.getYRot(), mc.player.getXRot());
    }
}
