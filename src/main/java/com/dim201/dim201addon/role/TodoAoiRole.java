package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;

/**
 * 东堂葵 - 平民阵营
 *
 * 与虎杖悠仁的双人合作角色：
 * 1. 主动技「不义游戏」：花费 50 金币开启 3 秒窗口，虎杖响应后互换位置 + 击掌音效 + 双方 2 秒无敌
 * 2. 被动「兄弟情义」：东堂在 3 格内时代替虎杖承受伤害
 * 3. 10 格内与虎杖互相高亮透视（SRE 描边渲染，不依赖直觉/发光药水）
 */
public class TodoAoiRole {

    public static final ResourceLocation TODO_AOI_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "todo_aoi");

    public static SRERole TODO_AOI;

    /** 角色疾跑体力上限（tick） */
    public static final int MAX_SPRINT_TICKS = 400;
    /** 被动「兄弟情义」：东堂在此距离内代替虎杖承受伤害（格） */
    public static final double SHIELD_RANGE = 3.0D;
    /** 互相感知基础距离（格） */
    public static final double PERCEPTION_RANGE = 10.0D;
    /** 击掌后扩展感知距离（格） */
    public static final double PERCEPTION_RANGE_EXTENDED = 30.0D;

    public static void register() {
        TODO_AOI = TMMRoles.registerRole(new TodoAoiRoleImpl());
        // 东堂只能作为虎杖的关联同伴经展开阶段加入：抽取池计数读 ROLE_MAX 而非 defaultMax，
        // 必须显式置 0 才会从随机池移除（展开 addAll 不检查此计数，同伴出现不受影响）
        Harpymodloader.setRoleMaximum(TODO_AOI_ID, 0);
    }

    /** 判断玩家是否为东堂葵 */
    public static boolean isTodo(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        return gameWorld.isRole(player, TODO_AOI);
    }

    private static class TodoAoiRoleImpl extends NormalRole {
        TodoAoiRoleImpl() {
            super(TODO_AOI_ID, 0xFF7D3CB5, true, false, MoodType.REAL, 400, false);
            // enableChance=0 双保险：即使 ROLE_MAX 被配置改动也不独立随机出现，只能被关联展开
            this.setCanSeeTime(false)
                    .setCanUseInstinct(false)
                    .setDefaultMax(0)
                    .setDefaultEnableChance(0);
        }
    }
}
