package com.dim201.dim201addon.client.electric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 电击狂人客户端状态
 *
 * <p>存储从服务端同步的静电方块位置、触电高亮玩家和爆炸粒子动画，供渲染使用。
 */
public final class ElectricManiacClientState {

    private ElectricManiacClientState() {}

    /** 当前维度的静电方块位置 */
    public static final Set<BlockPos> electrifiedBlocks = new HashSet<>();

    /** 触电全图高亮中的玩家：UUID → 到期客户端游戏时间（level.getGameTime()） */
    public static final Map<UUID, Long> shockedPlayersUntil = new ConcurrentHashMap<>();

    /** 活动中的静电爆炸粒子动画 */
    public static final List<BlastFx> activeBlasts = new CopyOnWriteArrayList<>();

    /** 静电方块高亮颜色：电光蓝 */
    public static final int BLOCK_HIGHLIGHT_COLOR = 0x00BFFF;

    /** 触电玩家高亮颜色：亮黄 */
    public static final int SHOCK_HIGHLIGHT_COLOR = 0xFFFF00;

    /** 静电爆炸圆环粒子动画 */
    public record BlastFx(Vec3 center, long startGameTime) {}

    /** 断开连接/对局结束兜底：清空全部客户端状态 */
    public static void reset() {
        electrifiedBlocks.clear();
        shockedPlayersUntil.clear();
        activeBlasts.clear();
    }
}
