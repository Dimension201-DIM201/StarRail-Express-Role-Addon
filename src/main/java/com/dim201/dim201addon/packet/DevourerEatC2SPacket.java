package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 吞噬者技能 C2S 网络包
 *
 * 客户端按 G 键时发送，服务端根据吞噬者当前状态决定吞噬或吐出：
 * - 已有吞噬目标 → 吐出
 * - 无吞噬目标 → 吞噬准星 2.15 格内最近的玩家
 */
public record DevourerEatC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "devourer_eat"
    );
    public static final Type<DevourerEatC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DevourerEatC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static {
        CODEC = StreamCodec.unit(new DevourerEatC2SPacket());
    }
}
