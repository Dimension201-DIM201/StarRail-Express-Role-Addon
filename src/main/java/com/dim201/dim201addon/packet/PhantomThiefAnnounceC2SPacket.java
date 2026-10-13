package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 大怪盗：客户端在背包界面点击玩家头像 → 发送目标 UUID 到服务端。
 * 服务端校验角色/对局/目标存活后，发送「预告信」（抽取目标宝物 + 90 秒追踪）。
 */
public record PhantomThiefAnnounceC2SPacket(UUID targetPlayer) implements CustomPacketPayload {

    public static final Type<PhantomThiefAnnounceC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "phantom_thief_announce"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomThiefAnnounceC2SPacket> CODEC =
            StreamCodec.ofMember(PhantomThiefAnnounceC2SPacket::write, PhantomThiefAnnounceC2SPacket::read);

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
    }

    public static PhantomThiefAnnounceC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new PhantomThiefAnnounceC2SPacket(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
