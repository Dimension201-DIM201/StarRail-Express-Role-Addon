package com.dim201.dim201addon.packet.electric;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 服务端 → 客户端：触电事件（全图高亮 + 视觉效果）
 *
 * <p>支持任意数量玩家（静电接触为 2 人，静电爆炸可能为多人）。
 */
public record ElectricManiacShockS2CPacket(List<UUID> players, int durationTicks)
        implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "electric_maniac_shock");
    public static final Type<ElectricManiacShockS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricManiacShockS2CPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8
                            .map(UUID::fromString, UUID::toString)
                            .apply(ByteBufCodecs.list()),
                    ElectricManiacShockS2CPacket::players,
                    ByteBufCodecs.VAR_INT, ElectricManiacShockS2CPacket::durationTicks,
                    ElectricManiacShockS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
