package com.dim201.dim201addon.packet.electric;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 服务端 → 客户端：静电爆炸粒子特效
 *
 * <p>客户端收到后以 center 为中心播放半径扩散至 32 格的圆环粒子动画。
 */
public record ElectricManiacBlastS2CPacket(Vec3 center) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "electric_maniac_blast");
    public static final Type<ElectricManiacBlastS2CPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricManiacBlastS2CPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, p -> p.center().x,
                    ByteBufCodecs.DOUBLE, p -> p.center().y,
                    ByteBufCodecs.DOUBLE, p -> p.center().z,
                    (x, y, z) -> new ElectricManiacBlastS2CPacket(new Vec3(x, y, z)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
