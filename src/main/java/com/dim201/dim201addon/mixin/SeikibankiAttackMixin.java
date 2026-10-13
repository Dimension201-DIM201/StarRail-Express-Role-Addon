package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 赤蛮奇「飞头」武器禁用 Mixin。
 *
 * <p>飞头期间玩家无法使用武器攻击（左键），防止利用飞行优势攻击他人。
 */
@Mixin(Player.class)
public abstract class SeikibankiAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void dim201$onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.get(sp);
        if (comp != null && comp.skillActive) {
            ci.cancel();
        }
    }
}
