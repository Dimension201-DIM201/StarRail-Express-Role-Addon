package com.dim201.dim201addon.item;

import com.dim201.dim201addon.role.MadmanRole;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 镇静剂状态管理器
 *
 * <p>原理：使用 {@link ConcurrentHashMap} 跟踪镇静中的玩家。
 * 镇静期间每 tick 将心情值锁定为 0.5f（50%），到期后恢复原始心情值。
 *
 * <p>线程安全：服务端单线程调用，但 ConcurrentHashMap 保证了
 * 对局重置/玩家断线等并发清理场景的安全性。
 */
public final class SedativeManager {

    private SedativeManager() {}

    /** 镇静期间锁定的心情值（50%） */
    public static final float LOCKED_MOOD = 0.5f;

    /** 镇静持续时间（tick）：30 秒 = 600 ticks */
    public static final int DURATION_TICKS = 600;

    /** 疯子使用镇静剂时持续时间减半（tick）：15 秒 = 300 ticks */
    public static final int MADMAN_DURATION_TICKS = DURATION_TICKS / 2;

    /**
     * 根据玩家身份返回应施加的镇静持续时间
     *
     * <p>疯子因自身机制与镇静效果冲突，引用时持续时间减半（15 秒），
     * 其余角色为基础时长（30 秒）。
     */
    public static int getDurationFor(ServerPlayer player) {
        return MadmanRole.isMadman(player) ? MADMAN_DURATION_TICKS : DURATION_TICKS;
    }

    private record SedationState(long untilTick, float originalMood) {}

    private static final ConcurrentHashMap<UUID, SedationState> sedatedPlayers = new ConcurrentHashMap<>();

    /** 施加镇静效果 */
    public static void sedate(ServerPlayer player, int durationTicks) {
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
        float original = mood != null ? mood.getMood() : LOCKED_MOOD;
        sedatedPlayers.put(player.getUUID(),
                new SedationState(player.tickCount + durationTicks, original));
    }

    /** 判断玩家当前是否处于镇静状态 */
    public static boolean isSedated(ServerPlayer player) {
        SedationState state = sedatedPlayers.get(player.getUUID());
        if (state == null) return false;
        return player.tickCount <= state.untilTick();
    }

    /**
     * 服务端每 tick 末尾调用：管理所有镇静中的玩家
     *
     * <p>对镇静中的玩家：将心情值锁定为 0.5f
     * <p>对到期的玩家：恢复原始心情值并移除条目
     */
    public static void tickAll(MinecraftServer server) {
        if (sedatedPlayers.isEmpty()) return;

        var it = sedatedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID uuid = entry.getKey();
            SedationState state = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                // 玩家离线，清理条目
                it.remove();
                continue;
            }

            if (player.tickCount > state.untilTick()) {
                // 镇静到期：恢复原始心情值
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                if (mood != null) {
                    mood.setMood(state.originalMood());
                }
                it.remove();
                continue;
            }

            // 镇静期间：锁定心情值为 0.5f
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
            if (mood != null && Math.abs(mood.getMood() - LOCKED_MOOD) > 0.01f) {
                mood.setMood(LOCKED_MOOD);
            }
        }
    }

    /** 清除所有镇静状态（对局结束时调用） */
    public static void clearAll() {
        sedatedPlayers.clear();
    }

    /** 清除指定玩家的镇静状态 */
    public static void clear(UUID uuid) {
        sedatedPlayers.remove(uuid);
    }
}
