package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 普奇神父 S2C 广播包：告诉所有客户端「被变形者 UUID」应显示为「外观目标 UUID」的皮肤。
 *
 * <p>为什么用 S2C 广播而不是 RoleData？
 * 普奇变的是**别人**——被变形者的角色没有绑定 setRoleData(PucciDisguiseRoleData::new)，
 * RoleData.getOrCreate 在非角色玩家上不会创建组件，客户端侧更是永远空壳。
 *
 * <p>Morphling 能用 RoleData 是因为它变的是**自己**——Morphling 玩家身上本来就有
 * setRoleData 绑定的 MorphlingRoleData 组件。
 *
 * <p>所以绕过 RoleData 限制：服务端 handler 成功后直接广播 S2C 包给所有客户端，
 * 客户端收到后写入 PucciClientUI.DISGUISE_CACHE 静态 Map，皮肤 Mixin 从 Map 查。
 *
 * @param disguiseTarget 被变形者 UUID（谁要换皮肤）
 * @param appearanceTarget 外观目标 UUID（变成谁的皮肤），null 表示清除变形
 */
public record PucciDisguiseS2CPacket(UUID disguiseTarget, UUID appearanceTarget)
        implements CustomPacketPayload {

    public static final Type<PucciDisguiseS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "pucci_disguise_s2c"));

    public static final StreamCodec<FriendlyByteBuf, PucciDisguiseS2CPacket> CODEC =
            StreamCodec.ofMember(PucciDisguiseS2CPacket::write, PucciDisguiseS2CPacket::read);

    public static PucciDisguiseS2CPacket read(FriendlyByteBuf buf) {
        return new PucciDisguiseS2CPacket(buf.readUUID(), buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(disguiseTarget);
        buf.writeUUID(appearanceTarget);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
