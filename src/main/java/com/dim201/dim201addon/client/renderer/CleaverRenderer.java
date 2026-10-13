package com.dim201.dim201addon.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.jetbrains.annotations.Nullable;

/**
 * 山姥的菜刀实体渲染器
 *
 * <p>继承 ArrowRenderer（与 SRE 飞刀一致的箭形渲染），
 * 直接套用飞刀的贴图（用户要求"丢出去的菜刀材质套用普通的刀"），
 * 避免额外制作实体贴图。放大 1.25x 更有菜刀感。
 */
public class CleaverRenderer extends ArrowRenderer {

    /** 复用 SRE 飞刀的实体贴图 */
    private static final ResourceLocation KNIFE_TEXTURE = ResourceLocation.tryParse(
            "noellesroles:textures/entity/throwing_knife.png");

    public CleaverRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @Nullable ResourceLocation getTextureLocation(Entity entity) {
        return KNIFE_TEXTURE;
    }

    @Override
    public void render(AbstractArrow entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(1.25F, 1.25F, 1.25F);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
