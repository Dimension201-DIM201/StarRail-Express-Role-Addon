package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 独狼操控无人机期间冻结本体移动输入。
 *
 * <p>在 {@link KeyboardInput#tick} TAIL 清零移动/跳跃/潜行字段，
 * 按键只用于远程驱动无人机，本体不会走动。
 */
@Mixin(KeyboardInput.class)
public abstract class LoneWolfDroneKeyboardMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void dim201$freezeBodyForDrone(boolean isSneaking, float sneakSpeed, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(client.player);
        if (comp == null || !comp.droneActive || !comp.controllingDrone) return;

        Input self = (Input) (Object) this;
        self.up = false;
        self.down = false;
        self.left = false;
        self.right = false;
        self.jumping = false;
        self.shiftKeyDown = false;
        self.forwardImpulse = 0f;
        self.leftImpulse = 0f;
    }
}
