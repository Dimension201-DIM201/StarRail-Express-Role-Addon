package com.dim201.dim201addon.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.dim201.dim201addon.Dim201Addon;

/**
 * 杀手在肚内按技能键 → 服务端：触发开膛破肚反杀吞噬者
 *
 * 服务端通过 PelicanManager.getPelicanForStashed(player.uuid) 找到对应的吞噬者，
 * 由 DevourerPlayerComponent.killerTryEscape() 执行时间校验和反杀逻辑。
 */
public record DevourerKillerEscapeC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "devourer_killer_escape"
    );
    public static final Type<DevourerKillerEscapeC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DevourerKillerEscapeC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static {
        CODEC = StreamCodec.unit(new DevourerKillerEscapeC2SPacket());
    }
}
