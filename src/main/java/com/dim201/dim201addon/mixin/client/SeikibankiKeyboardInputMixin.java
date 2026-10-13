package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 赤蛮奇飞头期间冻结玩家本体的移动输入（参考操纵师同名 Mixin）。
 *
 * <p>在 {@link KeyboardInput#tick} TAIL 清零本体的移动/跳跃/潜行字段，
 * 使按键只用于远程驱动头颅，玩家身体不会走动。
 * 不修改 {@code KeyMapping#isDown}，因此控制器仍能读到原始按键状态。
 */
@Mixin(KeyboardInput.class)
public abstract class SeikibankiKeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void dim201$freezeBody(boolean isSneaking, float sneakSpeed, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.getNullable(client.player);
        if (comp == null || !comp.skillActive) return;

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
