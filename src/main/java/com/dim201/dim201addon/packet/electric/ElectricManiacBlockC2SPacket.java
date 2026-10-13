package com.dim201.dim201addon.packet.electric;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 电击狂人右键方块 → 附着静电 C2S */
public record ElectricManiacBlockC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "electric_maniac_block");
    public static final Type<ElectricManiacBlockC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricManiacBlockC2SPacket> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ElectricManiacBlockC2SPacket::pos,
                    ElectricManiacBlockC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
