package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 大怪盗：G 键「妙手空空」→ 请求开始读条。
 * 服务端校验角色/预告状态/距离（≤ 3 格）后进入读条阶段。
 */
public record PhantomThiefStealC2SPacket() implements CustomPacketPayload {

    public static final Type<PhantomThiefStealC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "phantom_thief_steal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomThiefStealC2SPacket> CODEC =
            StreamCodec.ofMember(PhantomThiefStealC2SPacket::write, PhantomThiefStealC2SPacket::read);

    public void write(RegistryFriendlyByteBuf buf) {
    }

    public static PhantomThiefStealC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new PhantomThiefStealC2SPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
