package com.dim201.dim201addon.item.card;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无碰撞状态管理器
 *
 * <p>原理：使用 ConcurrentHashMap 记录处于"移除碰撞体积"状态的玩家UUID及其到期时间戳。
 * {@link io.wifi.starrailexpress.rules.CollisionRules#cantCollide} 中的 Predicate
 * 通过查询此管理器决定是否让玩家跳过碰撞判定（与 SRE "轻灵"修饰符实现一致）。
 *
 * <p>线程安全：使用 ConcurrentHashMap 保证并发访问安全。
 */
public final class NoClipManager {

    /** 玩家UUID → 无碰撞状态到期时间戳（tick） */
    private static final ConcurrentHashMap<UUID, Long> ACTIVE = new ConcurrentHashMap<>();

    private NoClipManager() {}

    /**
     * 激活玩家的无碰撞状态
     *
     * @param sp 玩家
     * @param durationTicks 持续时间（tick）
     */
    public static void activate(ServerPlayer sp, long durationTicks) {
        long expireTick = sp.server.getTickCount() + durationTicks;
        ACTIVE.put(sp.getUUID(), expireTick);
    }

    /**
     * 检查玩家当前是否处于无碰撞状态
     * 同时清理已过期的记录
     *
     * @param uuid 玩家UUID
     * @param currentTick 当前 tick（用于过期判断）
     */
    public static boolean isActive(UUID uuid, long currentTick) {
        Long expire = ACTIVE.get(uuid);
        if (expire == null) return false;
        if (expire <= currentTick) {
            ACTIVE.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * 强制移除玩家的无碰撞状态（玩家死亡/退出等情况）
     */
    public static void deactivate(UUID uuid) {
        ACTIVE.remove(uuid);
    }

    /**
     * 清空所有状态（对局结束）
     */
    public static void reset() {
        ACTIVE.clear();
    }
}
