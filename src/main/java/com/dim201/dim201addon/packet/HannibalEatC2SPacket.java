package com.dim201.dim201addon.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.dim201.dim201addon.Dim201Addon;

import java.util.UUID;

public record HannibalEatC2SPacket(UUID playerBody) implements CustomPacketPayload {
    public static final ResourceLocation HANNIBAL_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "hannibal_eat"
    );
    public static final Type<HannibalEatC2SPacket> ID = new Type<>(HANNIBAL_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, HannibalEatC2SPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.playerBody);
    }

    public static HannibalEatC2SPacket read(FriendlyByteBuf buf) {
        return new HannibalEatC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(HannibalEatC2SPacket::write, HannibalEatC2SPacket::read);
    }
}