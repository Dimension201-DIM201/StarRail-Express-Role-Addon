package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 独狼（Lone Wolf）— 中立阵营
 *
 * <p>主动技能「隐形无人机」（G 键）：
 * <ul>
 *   <li>消耗 100 金币部署隐形无人机，本体冻结，相机绑定无人机</li>
 *   <li>再按 G 在「操控无人机」和「操控本体」之间切换</li>
 *   <li>操控无人机时左键发射射线，命中玩家 → 对独狼高亮 60s，无人机销毁</li>
 *   <li>无人机 60s 后自动销毁，销毁后技能进入 60s 冷却</li>
 * </ul>
 */
public class LoneWolfRole {

    public static final ResourceLocation LONE_WOLF_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "lone_wolf"
    );

    public static SRERole LONE_WOLF;

    /** 制式左轮（独狼默认武器），清算时间冷却钳制也用此 id */
    public static final ResourceLocation STANDARD_REVOLVER = ResourceLocation.parse("trainmurdermystery:standard_revolver");
    private static final ResourceLocation CROWBAR = ResourceLocation.parse("trainmurdermystery:crowbar");

    /** 开局武器冷却（tick）：120 秒 */
    private static final int WEAPON_COOLDOWN_TICKS = 120 * 20;
    /** 清算时间手枪冷却（tick）：2 秒 */
    public static final int SETTLEMENT_REVOLVER_COOLDOWN_TICKS = 2 * 20;

    public static void register() {
        LONE_WOLF = TMMRoles.registerRole(new LoneWolfRoleImpl());
        Harpymodloader.setRoleMaximum(LONE_WOLF_ID, 1);

        // 对局重置：清理无人机状态
        ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer sp) {
                ModComponents.LONE_WOLF.get(sp).init();
            }
        });

        // 本体死亡：清理无人机与标记。
        // 不加 isRole 守卫：死亡瞬间角色映射可能已被 SRE 移除导致清理被跳过，
        // cleanupOnDeath 为幂等操作，对非独狼调用是无害空操作。
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning()) return;
            ModComponents.LONE_WOLF.get(sp).cleanupOnDeath(sp);
        });
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning()) return;
            ModComponents.LONE_WOLF.get(sp).cleanupOnDeath(sp);
        });
    }

    private static class LoneWolfRoleImpl extends NormalRole {

        public LoneWolfRoleImpl() {
            super(
                LONE_WOLF_ID,
                0xFF8B0000,
                false,
                false,
                MoodType.FAKE,
                400,
                false
            );

            this.setNeutrals(true)
                .setNeutralForKiller(false)
                .setCanSeeCoin(true)
                .setCanSeeTime(true)
                .setOccupiedRoleCount(1)
                .setCanUseInstinct(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            Item revolverItem = BuiltInRegistries.ITEM.get(STANDARD_REVOLVER);
            Item crowbarItem = BuiltInRegistries.ITEM.get(CROWBAR);
            if (revolverItem != null) items.add(new ItemStack(revolverItem));
            if (crowbarItem != null) items.add(new ItemStack(crowbarItem));
            return items;
        }

        /**
         * G 键技能（兜底路径）：常规触发由客户端 GKeyRoleSkill 拦截并
         * 发送 LoneWolfSkillC2SPacket（避免被准星目标拦截），仅当该路径
         * 未注册时才走到这里。
         · 无无人机且无冷却 → 部署无人机（-100 金币）
         · 无人机已部署 → 切换操控目标（无人机 ↔ 本体）
         */
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
            if (gw == null || !gw.isRunning() || !gw.isRole(player, LONE_WOLF)) return false;
            if (!player.isAlive()) return true;

            LoneWolfComponent comp = ModComponents.LONE_WOLF.get(player);
            if (comp.droneActive) {
                comp.toggleControl(player);
            } else {
                comp.deployDrone(player);
            }
            return true;
        }

        /** 角色 tick：推进无人机计时/标记到期/冻结本体 */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, LONE_WOLF)) return;
            ModComponents.LONE_WOLF.get(player).tick(player);
        }
    }

    public static void registerEvents() {
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode) return GameUtils.WinStatus.NOT_MODIFY;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null || !gameWorld.isRunning()) return GameUtils.WinStatus.NOT_MODIFY;

            ServerPlayer loneWolfPlayer = null;
            int aliveCount = 0;
            for (ServerPlayer p : serverLevel.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
                aliveCount++;
                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(p);
                if (comp != null && !comp.isDead) {
                    if (gameWorld.isRole(p, LONE_WOLF)) loneWolfPlayer = p;
                }
            }
            if (loneWolfPlayer == null) return GameUtils.WinStatus.NOT_MODIFY;

            if (aliveCount == 1) {
                SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverLevel);
                if (roundEnd != null) {
                    roundEnd.CustomWinnerID = LONE_WOLF_ID.getPath();
                    roundEnd.CustomWinnerColor = 0xFF8B0000;
                    roundEnd.CustomWinnerTitle = Component.literal("§4§l独狼 获胜");
                    roundEnd.CustomWinnerSubtitle = Component.literal("§7「狼行千里，只为一战」");
                }
                return GameUtils.WinStatus.CUSTOM;
            }

            if (winStatus != GameUtils.WinStatus.NONE && winStatus != GameUtils.WinStatus.TIME) {
                return GameUtils.WinStatus.NONE;
            }

            return GameUtils.WinStatus.NOT_MODIFY;
        });

        // 开局武器冷却
        OnGameStarted.EVENT.register(serverLevel -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null || !gameWorld.isRunning()) return;
            Item revolver = BuiltInRegistries.ITEM.get(STANDARD_REVOLVER);
            Item crowbar = BuiltInRegistries.ITEM.get(CROWBAR);
            for (ServerPlayer p : serverLevel.players()) {
                if (!gameWorld.isRole(p, LONE_WOLF)) continue;
                if (revolver != null) p.getCooldowns().addCooldown(revolver, WEAPON_COOLDOWN_TICKS);
                if (crowbar != null) p.getCooldowns().addCooldown(crowbar, WEAPON_COOLDOWN_TICKS);
            }
        });
    }

    private static boolean hasAliveLoneWolf(ServerLevel serverLevel) {
        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            LoneWolfComponent comp = ModComponents.LONE_WOLF.get(player);
            if (comp != null && !comp.isDead) {
                var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                if (gameWorld == null) continue;
                var role = gameWorld.getRole(player);
                if (role != null && role.equals(LONE_WOLF)) return true;
            }
        }
        return false;
    }

    public static void checkWinCondition(ServerLevel serverLevel) {
        ServerPlayer loneWolfPlayer = null;
        int aliveOthers = 0;

        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null) continue;
            var role = gameWorld.getRole(player);

            if (role != null && role.equals(LONE_WOLF)) {
                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(player);
                if (comp != null && !comp.isDead) loneWolfPlayer = player;
            } else {
                aliveOthers++;
            }
        }

        if (loneWolfPlayer != null && aliveOthers == 0) {
            RoleUtils.customWinnerWin(serverLevel, "dim201addon.lone_wolf", 0xFF8B0000);
        }
    }
}
