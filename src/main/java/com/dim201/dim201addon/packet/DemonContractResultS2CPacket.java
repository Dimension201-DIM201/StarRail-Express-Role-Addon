package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 恶魔契约结算结果（服务端 → 客户端）
 *
 * <p>客户端收到后将契约 UI 切换到「阶段二：献祭」页，逐条展示代价；
 * {@code dead=true} 时显示死亡结局文本，玩家等待被服务端处决。
 *
 * @param powerLines   已获得的力量显示名
 * @param costLines    揭晓的代价显示名
 * @param whisperLines 死者低语线索（无此力量或无线索时为空列表）
 * @param totalPoints  总代价点数
 * @param dead         总代价是否达到死亡阈值
 */
public record DemonContractResultS2CPacket(List<String> powerLines,
                                           List<String> costLines,
                                           List<String> whisperLines,
                                           int totalPoints,
                                           boolean dead) implements CustomPacketPayload {

    public static final Type<DemonContractResultS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "demon_contract_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DemonContractResultS2CPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    DemonContractResultS2CPacket::powerLines,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    DemonContractResultS2CPacket::costLines,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    DemonContractResultS2CPacket::whisperLines,
                    ByteBufCodecs.VAR_INT,
                    DemonContractResultS2CPacket::totalPoints,
                    ByteBufCodecs.BOOL,
                    DemonContractResultS2CPacket::dead,
                    DemonContractResultS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
