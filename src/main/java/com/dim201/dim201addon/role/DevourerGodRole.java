package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 神明吞噬者 - 中立阵营
 *
 * <p>原理：吞噬者（DevourerRole）死后若拥有「吞噬神明」修饰符（DEVOURER_GOD），
 * 2 分钟后会在房间出生点复活为「神明吞噬者」（而非 SRE 难民的亡命徒 LOOSE_END）。
 *
 * <p>角色特性：
 * <ul>
 *   <li>中立阵营（isInnocent=false, canUseKiller=false），不参与好人/杀手胜利判定</li>
 *   <li>存活时游戏不会结束（通过 AllowGameEnd 事件拦截）</li>
 *   <li>杀光所有其他玩家时获得自定义胜利（CUSTOM）</li>
 *   <li>自带速度 III（amplifier=2），持续生效</li>
 *   <li>初始 1 层护盾，每吞噬 2 人 +1 层护盾</li>
 *   <li>吞噬技能：3s CD（60 ticks），无限使用，被吞噬=死亡</li>
 *   <li>每局最多 1 个神明吞噬者</li>
 * </ul>
 */
public class DevourerGodRole {

    /** 角色标识符 */
    public static final ResourceLocation DEVOURER_GOD = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "devourer_god");

    /** 被神明吞噬者吞噬的死因 ID */
    public static final ResourceLocation DEATH_REASON_DEVOUR = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "devourer_god_devour");

    public static SRERole DEVOURER_GOD_ROLE;

    public static void register() {
        DEVOURER_GOD_ROLE = TMMRoles.registerRole(new DevourerGodRoleImpl());
    }

    /** 判断玩家是否为神明吞噬者 */
    public static boolean isDevourerGod(Player player) {
        if (player == null || player.level() == null) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(DEVOURER_GOD_ROLE);
    }

    /** 神明吞噬者角色实现 */
    private static class DevourerGodRoleImpl extends NormalRole {

        public DevourerGodRoleImpl() {
            // isInnocent=false, canUseKiller=false → 中立阵营
            // 不参与好人/杀手胜利判定，存活时通过 AllowGameEnd 拦截游戏结束
            super(
                DEVOURER_GOD,
                0xAA00AA,            // 深紫色（dark_purple）
                false,               // isInnocent = false（中立阵营）
                false,               // canUseKiller = false
                MoodType.FAKE,       // 真实心情
                -1,                 // 默认冲刺时间
                true                // 不显示剩余时间
            );
            this.setCanSeeTime(true)
                .setCanUseInstinct(false)
                .setDefaultMax(0)   // 
                .setCanBeRandomedByOtherRoles(false);  // 不会随机分配，仅由复活机制产生
        }
    }
}
