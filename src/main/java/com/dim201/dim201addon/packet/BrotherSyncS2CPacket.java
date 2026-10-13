package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 兄弟默契状态 S2C 同步包
 *
 * 同步给东堂/虎杖客户端：搭档 UUID、当前默契能量、是否已扩展感知距离。
 * 客户端描边 Mixin 与 HUD 读取本数据。
 */
public record BrotherSyncS2CPacket(UUID partnerId, int points, boolean extended)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = Dim201Addon.id("brother_sync");
    public static final Type<BrotherSyncS2CPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BrotherSyncS2CPacket> CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.partnerId());
                        buf.writeVarInt(packet.points());
                        buf.writeBoolean(packet.extended());
                    },
                    buf -> new BrotherSyncS2CPacket(buf.readUUID(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
