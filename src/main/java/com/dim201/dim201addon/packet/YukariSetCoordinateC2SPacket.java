package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 八云紫右键方块 → 设置坐标 C2S
 *
 * <p>替代原「设置坐标」技能：右键准星所指方块即记录坐标（最多 10 个），
 * 坐标通过 YukariPlayerComponent 的 CCA 同步下发，客户端渲染紫色高亮。
 */
public record YukariSetCoordinateC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "yukari_set_coordinate");
    public static final Type<YukariSetCoordinateC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, YukariSetCoordinateC2SPacket> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, YukariSetCoordinateC2SPacket::pos,
                    YukariSetCoordinateC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
