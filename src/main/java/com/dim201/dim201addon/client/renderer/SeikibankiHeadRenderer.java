package com.dim201.dim201addon.client.renderer;

import com.dim201.dim201addon.entity.SeikibankiHeadEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 赤蛮奇飞头实体渲染器。
 *
 * <p>用烘焙的原版玩家头部模型（head + hat 双层皮肤）+ 主人皮肤贴图渲染，
 * 保证头颅与主人皮肤完全一致。
 */
public class SeikibankiHeadRenderer extends EntityRenderer<SeikibankiHeadEntity> {

    private final ModelPart head;
    private final ModelPart hat;
    /** 上一次生成粒子的 tickCount，保证每 tick 只生成一次粒子 */
    private int lastParticleTick = -1;

    public SeikibankiHeadRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(ModelLayers.PLAYER);
        this.head = root.getChild("head");
        this.hat = root.getChild("hat");
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(SeikibankiHeadEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();

        // 粒子在客户端渲染时生成，用渲染插值位置确保与模型完全对齐
        if (entity.tickCount != lastParticleTick) {
            lastParticleTick = entity.tickCount;
            if (entity.getDeltaMovement().lengthSqr() > 1.0E-6) {
                double px = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double py = Mth.lerp(partialTick, entity.yOld, entity.getY()) + 0.2;
                double pz = Mth.lerp(partialTick, entity.zOld, entity.getZ());
                entity.level().addParticle(ParticleTypes.ASH, px, py, pz, 0, 0, 0);
            }
        }

        java.util.Optional<java.util.UUID> ownerUuid = entity.getOwnerUUID();

        AbstractClientPlayer owner = ownerUuid
                .map(uuid -> mc.level.getPlayerByUUID(uuid))
                .filter(AbstractClientPlayer.class::isInstance)
                .map(AbstractClientPlayer.class::cast)
                .orElse(null);
        if (owner == null) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        // 操作者用自己的视角渲染（避免服务端同步覆盖卡顿），其他观察者用 DataAccessor 同步的朝向
        boolean isOwner = mc.player != null && entity.getOwnerUUID().map(uuid -> uuid.equals(mc.player.getUUID())).orElse(false);
        float yaw = isOwner ? mc.player.getYRot() : entity.getSyncedYaw();
        float pitch = isOwner ? mc.player.getXRot() : entity.getSyncedPitch();

        // 第一人称下相机就是头颅自己，跳过渲染避免遮挡视野
        boolean selfFirstPerson = mc.getCameraEntity() == entity && mc.options.getCameraType().isFirstPerson();
        if (selfFirstPerson) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        poseStack.pushPose();

        // 在「原版写法 translate(0,1.5,0)+scale(-1,-1,1)」基础上往下移 1.5 格
        poseStack.translate(0.0F, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        // 确保可见（PlayerModel 实例可能被其他渲染器共享并修改了 visible）
        head.visible = true;
        hat.visible = true;
        head.yRot = 0.0F;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        hat.yRot = 0.0F;
        hat.xRot = pitch * Mth.DEG_TO_RAD;

        ResourceLocation skin = owner.getSkin().texture();
        var vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

        // 直接渲染 head 和 hat（hat 是 root 的子部件，不是 head 的子部件）
        head.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        hat.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SeikibankiHeadEntity entity) {
        return entity.getOwnerUUID()
                .map(uuid -> Minecraft.getInstance().level.getPlayerByUUID(uuid))
                .filter(AbstractClientPlayer.class::isInstance)
                .map(AbstractClientPlayer.class::cast)
                .map(p -> p.getSkin().texture())
                .orElseGet(net.minecraft.client.resources.DefaultPlayerSkin::getDefaultTexture);
    }
}
