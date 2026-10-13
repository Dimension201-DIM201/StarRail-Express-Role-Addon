package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 赤蛮奇「飞头」驾驶输入包（C2S）。
 *
 * <p>客户端每 tick 发送一次：按键位掩码 + 当前视角朝向，
 * 服务端校验技能状态后权威驱动头颅实体移动（参考操纵师的远程驾驶链路）。
 *
 * <p>movementBits 位定义：
 * 1=前进(W)，2=后退(S)，4=左移(A)，8=右移(D)，16=上升(空格)，32=下降(Shift)
 */
public record SeikibankiFlyInputC2SPacket(int movementBits, float yaw, float pitch)
        implements CustomPacketPayload {

    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;
    public static final int BIT_UP = 16;
    public static final int BIT_DOWN = 32;

    public static final Type<SeikibankiFlyInputC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "seikibanki_fly_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeikibankiFlyInputC2SPacket> CODEC =
            StreamCodec.ofMember(SeikibankiFlyInputC2SPacket::write, SeikibankiFlyInputC2SPacket::new);

    private SeikibankiFlyInputC2SPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(movementBits);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
