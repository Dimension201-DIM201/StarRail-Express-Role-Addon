package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SwitchSkillC2SPacket() implements CustomPacketPayload {

    public static final Type<SwitchSkillC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "switch_skill")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchSkillC2SPacket> CODEC =
        StreamCodec.unit(new SwitchSkillC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
