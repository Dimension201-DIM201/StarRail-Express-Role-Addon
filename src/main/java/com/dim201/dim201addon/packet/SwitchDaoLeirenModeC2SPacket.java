package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蕾忍切换攻击模式 C2S 数据包
 *
 * <p>客户端按 G 键时发送，服务端调用 DaoLeirenPlayerComponent.switchMode 处理切换。
 */
public record SwitchDaoLeirenModeC2SPacket() implements CustomPacketPayload {

    public static final Type<SwitchDaoLeirenModeC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "switch_dao_leiren_mode")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchDaoLeirenModeC2SPacket> CODEC =
        StreamCodec.unit(new SwitchDaoLeirenModeC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
