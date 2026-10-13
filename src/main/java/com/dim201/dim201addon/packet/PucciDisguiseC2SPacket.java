package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 普奇神父：让 transformTarget 变成 appearanceTarget 的样子
 */
public record PucciDisguiseC2SPacket(UUID transformTarget, UUID appearanceTarget) implements CustomPacketPayload {

    public static final Type<PucciDisguiseC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "pucci_disguise"));

    public static final StreamCodec<FriendlyByteBuf, PucciDisguiseC2SPacket> CODEC =
            StreamCodec.ofMember(PucciDisguiseC2SPacket::write, PucciDisguiseC2SPacket::read);

    public static PucciDisguiseC2SPacket read(FriendlyByteBuf buf) {
        return new PucciDisguiseC2SPacket(buf.readUUID(), buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(transformTarget);
        buf.writeUUID(appearanceTarget);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
