package com.dim201.dim201addon.client.renderer;

import com.dim201.dim201addon.entity.LoneWolfDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 独狼无人机渲染器 — 隐形，不渲染任何可见模型。
 */
public class LoneWolfDroneRenderer extends EntityRenderer<LoneWolfDroneEntity> {

    public LoneWolfDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public void render(LoneWolfDroneEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 隐形无人机：不渲染任何内容
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(LoneWolfDroneEntity entity) {
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
    }
}
