package com.dim201.dim201addon.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.dim201.dim201addon.Dim201Addon;

/**
 * 张老师技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求激活疾跑技能
 */
public record TeacherZhangAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<TeacherZhangAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "teacher_zhang_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, TeacherZhangAbilityC2SPacket> CODEC = 
        StreamCodec.unit(new TeacherZhangAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}