package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 克劳恩皮斯：客户端在背包界面点击玩家头像 → 发送目标 UUID 到服务端
 * 服务端校验身份、金币后，清除目标背包物品冷却
 */
public record ClownpieceRefreshC2SPacket(UUID targetPlayer) implements CustomPacketPayload {

    public static final Type<ClownpieceRefreshC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "clownpiece_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClownpieceRefreshC2SPacket> CODEC =
            StreamCodec.ofMember(ClownpieceRefreshC2SPacket::write, ClownpieceRefreshC2SPacket::read);

    public ClownpieceRefreshC2SPacket(UUID targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
    }

    public static ClownpieceRefreshC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new ClownpieceRefreshC2SPacket(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
