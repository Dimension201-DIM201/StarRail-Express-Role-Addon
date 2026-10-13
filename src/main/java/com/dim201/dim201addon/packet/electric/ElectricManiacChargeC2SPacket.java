package com.dim201.dim201addon.packet.electric;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 电击狂人按技能键 → 自身充电 C2S */
public record ElectricManiacChargeC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "electric_maniac_charge");
    public static final Type<ElectricManiacChargeC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricManiacChargeC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static {
        CODEC = StreamCodec.unit(new ElectricManiacChargeC2SPacket());
    }
}
