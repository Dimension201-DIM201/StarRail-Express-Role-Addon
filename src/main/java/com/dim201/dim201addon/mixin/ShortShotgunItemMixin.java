package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.role.DismasRole;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.item.ShortShotgunItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 霰弹枪开火监听：ShortShotgunItem 是独立蓄力武器（use/releaseUsing），
 * 不走 SRE 的 GunShootPayload，OnRevolverUsed 事件对它永远不触发。
 *
 * <p>注入在开火音效之后：蓄力不足会在音效前提前 return，不会误触发；
 * 且此时枪还未因耐久耗尽而销毁（hurtAndBreak 在音效之后），
 * onShotgunFired 内的霰弹枪检查可以正常通过。
 */
@Mixin(ShortShotgunItem.class)
public class ShortShotgunItemMixin {

    @Inject(method = "releaseUsing",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
                    shift = At.Shift.AFTER))
    private void dim201$onShotgunFired(ItemStack stack, Level world, LivingEntity user,
                                       int remainingUseTicks, CallbackInfo ci) {
        if (world.isClientSide || !(user instanceof ServerPlayer sp)) return;
        DismasRole.onShotgunFired(sp);
    }
}
