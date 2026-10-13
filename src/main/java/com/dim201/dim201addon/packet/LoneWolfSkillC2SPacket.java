package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 独狼技能键 C2S 包
 *
 * <p>G 键由客户端 {@code GKeyRoleSkill} 拦截后发送（与吞噬者/蕾忍一致），
 * 避免裸 onAbilityUse 被准星目标拦截。服务端按无人机状态分流：
 * 未部署 → 部署无人机；已部署 → 切换操控目标。
 */
public record LoneWolfSkillC2SPacket() implements CustomPacketPayload {

    public static final ResourceLocation ID = Dim201Addon.id("lone_wolf_skill");
    public static final Type<LoneWolfSkillC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LoneWolfSkillC2SPacket> CODEC =
            StreamCodec.unit(new LoneWolfSkillC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
