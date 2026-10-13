package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 电击狂人 - 杀手阵营
 *
 * <p>核心机制「静电」：
 * <ul>
 *   <li>按技能键使自身获得静电（8 秒，5 秒冷却）</li>
 *   <li>右键方块使其附着静电（30 秒），方块在电击狂人视野中高亮</li>
 *   <li>玩家靠近静电方块 3 格内会被附着静电（15 秒）</li>
 *   <li>两个静电玩家距离 < 3 格 → 尖叫、减 20 san、全图高亮、受伤动画</li>
 *   <li>电击狂人自身不会被方块静电影响</li>
 * </ul>
 *
 * <p>杀手直觉：同款强盗（非队友 10 格透视限制，队友无限制）。
 */
public class ElectricManiacRole {

    public static final ResourceLocation ELECTRIC_MANIAC_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "electric_maniac");

    public static SRERole ELECTRIC_MANIAC;

    public static void register() {
        ELECTRIC_MANIAC = TMMRoles.registerRole(new ElectricManiacRoleImpl());
    }

    /** 判断玩家是否为电击狂人（客户端安全判定） */
    public static boolean isElectricManiacClient(Player player) {
        if (player == null || player.level() == null) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(ELECTRIC_MANIAC);
    }

    private static class ElectricManiacRoleImpl extends NormalRole {

        public ElectricManiacRoleImpl() {
            super(
                    ELECTRIC_MANIAC_ID,
                    0xFF003399,        // 深电蓝（杀手阵营深色基调）
                    false,              // isInnocent = false（杀手阵营）
                    true,               // canUseKiller = true
                    MoodType.FAKE,      // 
                    -1,                 // 默认冲刺时间
                    true                // 可看时间
            );
            this.setCanSeeTime(true)
                    .setCanUseInstinct(true)
                    .setDefaultMax(1)
                    .setCanPickUpRevolver(true)
                    .setCanBeRandomedByOtherRoles(false)
                    .setServerGameTickEvent(this::handleServerTick);
        }

        /** 每 tick 检查电击狂人是否存活，不存活则跳过静电逻辑（由 Manager 统一处理） */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, ELECTRIC_MANIAC)) return;
            if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
            // 静电逻辑全部在 ElectricManiacManager.serverTick 中统一处理
        }
    }
}
