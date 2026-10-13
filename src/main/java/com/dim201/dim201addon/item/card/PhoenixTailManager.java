package com.dim201.dim201addon.item.card;

import com.dim201.dim201addon.item.card.ability.PhoenixTailCard;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 不死鸟之尾卡牌复活管理器
 *
 * <p>原理：注册 SRE 的 {@link AfterShieldAllowPlayerDeath} 和
 * {@link AfterShieldAllowPlayerDeathWithKiller} 事件，
 * 玩家死亡时检查背包是否持有不死鸟之尾卡牌，
 * 有则传送到对局开始位置并满血复活，消耗一张卡牌。
 *
 * <p>复活位置优先级（与 AdachiRole.tryRevive 一致）：
 * <ol>
 *   <li>对局开始时记录的玩家位置（{@link #recordGameStartPos}）</li>
 *   <li>退回到 {@link AreasWorldComponent#getSpawnPos()}</li>
 * </ol>
 */
public final class PhoenixTailManager {

    private static boolean registered = false;

    /** 对局开始时记录的玩家位置（复活用） */
    private record GameStartPos(double x, double y, double z, float yaw, float pitch) {}

    private static final ConcurrentHashMap<UUID, GameStartPos> GAME_START_POSITIONS = new ConcurrentHashMap<>();

    private PhoenixTailManager() {}

    /**
     * 记录玩家在对局开始时的位置（由 Dim201Addon 在 ModdedRoleAssigned 中调用）
     */
    public static void recordGameStartPos(ServerPlayer sp) {
        GAME_START_POSITIONS.put(sp.getUUID(),
                new GameStartPos(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot()));
    }

    /**
     * 清除玩家的对局开始位置记录（由 ResetPlayerEvent 调用）
     */
    public static void clearGameStartPos(UUID uuid) {
        GAME_START_POSITIONS.remove(uuid);
    }

    /**
     * 注册复活事件监听器（幂等，仅注册一次）
     */
    public static void register() {
        if (registered) return;
        registered = true;

        // 无击杀者版本
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) ->
                tryRevive(player, deathReason));

        // 有击杀者版本
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) ->
                tryRevive(player, deathReason));
    }

    /**
     * 尝试复活玩家
     *
     * @param player 死亡的玩家
     * @param deathReason 死亡原因
     * @return true 允许死亡，false 取消死亡（复活）
     */
    private static boolean tryRevive(Player player, ResourceLocation deathReason) {
        if (player.level().isClientSide) return true;
        if (!(player instanceof ServerPlayer sp)) return true;

        // 检查对局是否进行中
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld == null || !gameWorld.isRunning()) return true;

        // 检查背包是否持有不死鸟之尾卡牌
        if (!PhoenixTailCard.hasCard(sp.getInventory().items)) return true;

        // 消耗一张卡牌
        PhoenixTailCard.consumeOne(sp.getInventory().items);
        sp.getInventory().setChanged();

        // 火焰粒子：在死亡位置生成（传送前），提示触发复活
        ServerLevel level = sp.serverLevel();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                sp.getX(), sp.getY() + 1, sp.getZ(),
                30, 0.5, 0.8, 0.5, 0.05);

        // 传送到对局开始位置（优先），退回到 AreasWorldComponent 出生点
        GameStartPos startPos = GAME_START_POSITIONS.get(sp.getUUID());
        if (startPos != null) {
            sp.teleportTo(level, startPos.x(), startPos.y(), startPos.z(),
                    startPos.yaw(), startPos.pitch);
        } else {
            AreasWorldComponent.PosWithOrientation spawn =
                    AreasWorldComponent.KEY.get(level).getSpawnPos();
            if (spawn != null) {
                sp.teleportTo(level, spawn.pos.x(), spawn.pos.y(), spawn.pos.z(),
                        spawn.yaw, spawn.pitch);
            }
        }

        // 满血复活
        sp.setHealth(sp.getMaxHealth());

        // 提示
        CardUtils.notify(sp, "§d不死鸟之尾§r§7 · 在出生点复活！");

        // 同步背包变化到客户端
        sp.inventoryMenu.broadcastChanges();

        return false;
    }
}
