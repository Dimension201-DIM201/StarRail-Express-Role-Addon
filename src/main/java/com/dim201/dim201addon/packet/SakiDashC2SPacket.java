package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 骊驹早鬼：客户端按 G 键向服务端发「尝试突进」请求（服务端再次校验条件）
 */
public record SakiDashC2SPacket() implements CustomPacketPayload {

    public static final Type<SakiDashC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "saki_dash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SakiDashC2SPacket> CODEC =
            StreamCodec.unit(new SakiDashC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
