package com.dim201.dim201addon.packet;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 独狼无人机驾驶输入包（C2S）。
 *
 * <p>movementBits 位定义：
 * 1=前进(W)，2=后退(S)，4=左移(A)，8=右移(D)，16=上升(空格)，32=下降(Shift)
 *
 * <p>fire 为 true 时表示玩家按下左键发射射线（仅上升沿触发一次）。
 */
public record LoneWolfDroneInputC2SPacket(int movementBits, float yaw, float pitch, boolean fire,
                                          double x, double y, double z)
        implements CustomPacketPayload {

    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;
    public static final int BIT_UP = 16;
    public static final int BIT_DOWN = 32;

    public static final Type<LoneWolfDroneInputC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "lone_wolf_drone_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoneWolfDroneInputC2SPacket> CODEC =
            StreamCodec.ofMember(LoneWolfDroneInputC2SPacket::write, LoneWolfDroneInputC2SPacket::new);

    private LoneWolfDroneInputC2SPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(movementBits);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeBoolean(fire);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
