package com.dim201.dim201addon.packet.electric;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 服务端 → 客户端：同步当前维度的静电方块列表 */
public record ElectricManiacBlocksS2CPacket(List<BlockPos> blocks) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "electric_maniac_blocks");
    public static final Type<ElectricManiacBlocksS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricManiacBlocksS2CPacket> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ElectricManiacBlocksS2CPacket::blocks,
                    ElectricManiacBlocksS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
