package com.dim201.dim201addon.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dim201.dim201addon.HannibalRole;
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerBodyEntityRenderer.class)
public abstract class HannibalEatenBodyMixin {

    @Inject(method = "renderBody", at = @At("HEAD"), cancellable = true)
    public void hannibalHideEatenBody(PlayerBodyEntity livingEntity, float f, float g,
            PoseStack matrixStack, MultiBufferSource vertexConsumerProvider,
            int light, float alpha, CallbackInfo ci) {

        // 如果尸体被汉尼拔处理过，取消渲染（变成白骨/消失）
        if (HannibalRole.isBodyEaten(livingEntity.getUUID())) {
            ci.cancel();
        }
    }
}