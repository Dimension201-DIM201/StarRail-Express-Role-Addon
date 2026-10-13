package com.dim201.dim201addon.client.electric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * 静电爆炸客户端动画
 *
 * <p>收到爆炸包后，以爆炸中心为原点，在 {@value #ANIMATION_TICKS} tick 内
 * 播放水平圆环粒子扩散动画，半径从 0 扩散到 32 格。
 * 同时负责清理过期的触电高亮状态。
 */
public final class ElectricManiacBlastAnimator {

    private ElectricManiacBlastAnimator() {}

    /** 圆环扩散动画时长（tick）：1 秒扩散至 32 格 */
    private static final int ANIMATION_TICKS = 20;
    /** 最大半径（格） */
    private static final double MAX_RADIUS = 32.0D;
    /** 圆环粒子数量 */
    private static final int RING_PARTICLES = 90;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ElectricManiacBlastAnimator::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        if (client.level == null) {
            ElectricManiacClientState.activeBlasts.clear();
            ElectricManiacClientState.shockedPlayersUntil.clear();
            return;
        }
        long now = client.level.getGameTime();

        // 清理过期触电高亮
        ElectricManiacClientState.shockedPlayersUntil
                .entrySet().removeIf(e -> e.getValue() < now);

        // 先清理过期动画（CopyOnWriteArrayList 迭代器不支持 remove，用 removeIf）
        ElectricManiacClientState.activeBlasts
                .removeIf(fx -> now - fx.startGameTime() > ANIMATION_TICKS);

        // 推进爆炸圆环动画（快照遍历，遍历期间不会修改列表）
        for (ElectricManiacClientState.BlastFx fx : ElectricManiacClientState.activeBlasts) {
            long elapsed = now - fx.startGameTime();
            spawnRing(fx.center(), (double) elapsed / ANIMATION_TICKS * MAX_RADIUS);
        }
    }

    /** 在中心高度生成一圈水平圆环粒子 */
    private static void spawnRing(Vec3 center, double radius) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < RING_PARTICLES; i++) {
            double angle = 2.0D * Math.PI * i / RING_PARTICLES;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            // 微量垂直抖动，避免粒子完全共面闪烁
            double y = center.y + (i % 3 - 1) * 0.15D;
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
