package com.dim201.dim201addon.item.card;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地底的太阳 —— 核爆状态管理器
 *
 * <h3>机制说明</h3>
 * <ol>
 *   <li>{@link #startNuke(ServerPlayer)} 记录玩家 UUID → 起始 tick，注册全局调度</li>
 *   <li>{@link AfterShieldAllowPlayerDeath} 拦截核爆中玩家的所有死亡（5秒无敌）</li>
 *   <li>{@link ServerTickEvents#END_WORLD_TICK} 每 tick 检查：
 *     <ul>
 *       <li>若 tick % 10 == 0（每 0.5s）→ 执行一次爆炸：5格AABB扫描+killPlayer+爆炸粒子+音效</li>
 *       <li>若累计 100 tick（5s）→ 触发自爆：移除无敌后用 underground_sun_self 杀死自己</li>
 *     </ul>
 *   </li>
 *   <li>被击杀玩家不会被误伤自己（AABB过滤 owner），且目标必须为 isPlayerAliveAndSurvival</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * 使用 ConcurrentHashMap 记录核爆状态，避免异步事件与主循环 tick 竞争。
 */
public final class UndergroundSunManager {

    /** 总持续 tick：5 秒 */
    public static final int TOTAL_TICKS = 5 * 20;
    /** 爆炸间隔 tick：0.5 秒 */
    public static final int EXPLOSION_INTERVAL = 10;
    /** 爆炸半径（格） */
    public static final double EXPLOSION_RADIUS = 5.0;

    /** 自爆死亡原因 */
    public static final ResourceLocation DEATH_SELF = Dim201Addon.id("underground_sun_self");
    /** 被爆炸波及的死亡原因 */
    public static final ResourceLocation DEATH_HIT = Dim201Addon.id("underground_sun_hit");

    /** 记录核爆中玩家 → 起始 tick（服务端 level 绝对 tick） */
    private static final Map<UUID, Long> NUKE_PLAYERS = new ConcurrentHashMap<>();

    private static boolean registered = false;

    private UndergroundSunManager() {}

    public static void register() {
        if (registered) return;
        registered = true;

        // 死亡拦截（有/无击杀者两个事件）
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (player instanceof ServerPlayer sp) {
                if (isNuking(sp) && !DEATH_SELF.equals(deathReason)) {
                    return false;
                }
            }
            return true;
        });
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (player instanceof ServerPlayer sp) {
                if (isNuking(sp) && !DEATH_SELF.equals(deathReason)) {
                    return false;
                }
            }
            return true;
        });

        // 主循环调度爆炸 / 自爆
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!(world instanceof ServerLevel sl)) return;
            long absTick = sl.getGameTime();
            NUKE_PLAYERS.forEach((uuid, startTick) -> {
                ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(uuid);
                if (sp == null) {
                    NUKE_PLAYERS.remove(uuid);
                    return;
                }
                long elapsed = absTick - startTick;

                // 超期：自爆
                if (elapsed >= TOTAL_TICKS) {
                    NUKE_PLAYERS.remove(uuid);
                    // 先 cancel 死亡拦截，再自杀
                    selfDestruct(sp);
                    return;
                }
                // 每 0.5 秒炸一次
                if (elapsed % EXPLOSION_INTERVAL == 0) {
                    doExplosion(sp);
                }
            });
        });
    }

    public static boolean isNuking(ServerPlayer sp) {
        return NUKE_PLAYERS.containsKey(sp.getUUID());
    }

    public static void startNuke(ServerPlayer sp) {
        // 记录起始 tick（用当前世界 gameTime 作绝对基准）
        NUKE_PLAYERS.put(sp.getUUID(), sp.serverLevel().getGameTime());
        CardUtils.notify(sp, "§6地底的太阳 §7· 核爆启动！无敌 5 秒");
        // 启动时立即炸一次（提高爽感）
        doExplosion(sp);
    }

    /** 一次爆炸：5格范围内所有存活玩家直接击杀 */
    private static void doExplosion(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) return;

        ServerLevel sl = sp.serverLevel();
        Vec3 center = sp.position();
        AABB aabb = AABB.ofSize(center, EXPLOSION_RADIUS * 2, EXPLOSION_RADIUS * 2, EXPLOSION_RADIUS * 2);

        sl.getEntitiesOfClass(ServerPlayer.class, aabb).forEach(target -> {
            // 不炸自己，不炸死人/观众
            if (target.getUUID().equals(sp.getUUID())) return;
            if (!GameUtils.isPlayerAliveAndSurvival(target)) return;
            // 欧几里得距离判定（修正 AABB 宽/高/深 的立方体误差）
            if (target.distanceToSqr(sp) > EXPLOSION_RADIUS * EXPLOSION_RADIUS) return;

            GameUtils.killPlayer(target, true, sp, DEATH_HIT);
        });

        // 爆炸特效：FLAME 粒子 + 火球爆炸音效
        sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1, center.z,
                20, 2, 2, 2, 0.05);
        sl.sendParticles(ParticleTypes.FLAME, center.x, center.y + 1, center.z,
                30, 1.5, 1.5, 1.5, 0.12);
        sl.players().forEach(p -> sl.playSound(p, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.9f));
    }

    /** 自爆：使用 DEATH_SELF 死亡原因（绕过无敌拦截） */
    private static void selfDestruct(ServerPlayer sp) {
        Vec3 pos = sp.position();
        sp.serverLevel().players().forEach(p -> sp.serverLevel().playSound(p,
                pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.8f, 0.7f));
        CardUtils.notify(sp, "§6地底的太阳 §7· 焚尽一切！你随之消散……");
        GameUtils.killPlayer(sp, true, null, DEATH_SELF);
    }
}
