package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 吞噬者 - 平民阵营
 *
 * 核心机制：
 * 1. 按技能键花费 150 金币吞噬面前 2.15 格内的一个玩家
 *    → 复用 SRE 鹈鹕的 PelicanManager.stashPlayer 机制
 *    → 被吞噬玩家进入旁观者模式，视角锁定在吞噬者身上
 *
 * 2. 最多只能吞噬一个玩家
 *    → 已有吞噬目标时再次按键 → 吐出玩家
 *
 * 3. 120 秒冷却，吐出后开始计时
 *    → 吞噬不触发冷却（可以立即吐出）
 *    → 吐出后才开始 120 秒冷却
 *
 * 技能键：SRE 通用 G 键（abilityBind），通过 GKeyRoleSkill 注册
 */
public class DevourerRole {

    public static final ResourceLocation DEVOURER = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "devourer");

    /** 吞噬费用（金币） */
    public static final int EAT_COST = 150;
    /** 吞噬范围（格） */
    public static final double EAT_RANGE = 2.15D;
    /** 冷却时间（tick）= 120 秒 × 20 */
    public static final int COOLDOWN_TICKS = 120 * 20;

    public static SRERole DEVOURER_ROLE;

    public static void register() {
        DEVOURER_ROLE = TMMRoles.registerRole(new DevourerRoleImpl());
    }

    /** 判断玩家是否为吞噬者 */
    public static boolean isDevourer(Player player) {
        if (player == null || player.level() == null) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(DEVOURER_ROLE);
    }

    private static class DevourerRoleImpl extends NormalRole {

        public DevourerRoleImpl() {
            super(
                DEVOURER,
                0xFFDEB887,          // 浅褐色（burlywood）
                true,                // isInnocent = true（平民阵营）
                false,               // canUseKiller = false
                MoodType.REAL,       // 真实心情
                400,                 // 默认冲刺时间
                false                // 不显示剩余时间
            );
            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setDefaultMax(1);   // 每局最多 1 个吞噬者
        }
    }
}
