package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 赤蛮奇飞头期间隐藏「原地本体」的头部模型。
 *
 * <p>头颅已作为独立实体飞出，若本体模型仍渲染头部会出现两个头。
 * 在 PlayerRenderer.render 的 HEAD 隐藏 head（含 hat 子件）、TAIL 恢复——
 * PlayerModel 实例按 renderer 共享给所有玩家，必须恢复，否则其他玩家也会无头。
 */
@Mixin(PlayerRenderer.class)
public abstract class SeikibankiBodyHeadHideMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public SeikibankiBodyHeadHideMixin(EntityRendererProvider.Context context,
                                       PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void dim201$hideBodyHead(AbstractClientPlayer player, float yaw, float partialTick,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                    CallbackInfo ci) {
        SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.getNullable(player);
        if (comp != null && comp.skillActive) {
            this.model.head.visible = false;
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void dim201$restoreBodyHead(AbstractClientPlayer player, float yaw, float partialTick,
                                        PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                        CallbackInfo ci) {
        this.model.head.visible = true;
    }
}
