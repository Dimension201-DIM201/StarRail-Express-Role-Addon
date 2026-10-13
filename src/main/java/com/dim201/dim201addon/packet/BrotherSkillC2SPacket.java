package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 东堂葵 / 虎杖悠仁 合作技能 C2S 包
 *
 * 同一个包两种语义，由服务端按发送者角色分流：
 * - 东堂按下 → 开启「不义游戏」5 秒响应窗口
 * - 虎杖按下 → 响应窗口内的东堂，执行互换
 */
public record BrotherSkillC2SPacket() implements CustomPacketPayload {

    public static final ResourceLocation ID = Dim201Addon.id("brother_skill");
    public static final Type<BrotherSkillC2SPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BrotherSkillC2SPacket> CODEC =
            StreamCodec.unit(new BrotherSkillC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
