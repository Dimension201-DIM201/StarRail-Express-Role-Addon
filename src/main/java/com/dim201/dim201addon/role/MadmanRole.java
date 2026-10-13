package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 疯子 - 平民阵营
 *
 * 核心机制：
 * 1. 始终处于二级无理智状态（mood < 0.2 = DEPRESSIVE_MOOD_THRESHOLD）
 *    → 触发幻觉效果（其他玩家手持物品在疯子眼中变成随机物品）
 *    → mood 由 SREPlayerMoodComponent 管理，每 tick 自然衰减/恢复
 *    → 本角色每 tick 强制 mood = 0.15f，确保始终 < 0.2f
 *
 * 2. 免疫二级无理智的移动减速：
 *    正常 mood < 0.2 会在 PlayerEntityMixin.tmm$overrideMovementSpeed 中
 *    施加 speedModifier *= 0.8f（减速 20%）。
 *    由 MadmanLowSanSlowMixin 将该判定对疯子强制改为 false，
 *    从根源取消减速，无需再用速度药水效果抵消。
 *
 * 3. 双向免小脑惩罚（由 XiaoNaoPunishmentCancelMixin 实现）：
 *    - 疯子杀平民/警 → 不被 shot_innocent 反杀
 *    - 警/平民杀疯子 → 不被 shot_innocent 反杀
 *    原理：Mixin 拦截 GameUtils.killPlayer(shot_innocent)，用 ThreadLocal
 *    缓冲普通击杀时的 victim/killer 关系，匹配则 cancel
 */
public class MadmanRole {

    public static final ResourceLocation MADMAN_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "madman"
    );

    public static SRERole MADMAN;

    public static void register() {
        MADMAN = TMMRoles.registerRole(new MadmanRoleImpl());
    }

    /** 判断玩家是否为疯子（供 Mixin 调用） */
    public static boolean isMadman(Player player) {
        if (player == null || player.level() == null) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(MADMAN);
    }

    private static class MadmanRoleImpl extends NormalRole {

        public MadmanRoleImpl() {
            super(
                MADMAN_ID,
                0xFFFF4500,          // 橙红色
                true,                // isInnocent = true（平民阵营）
                false,               // canUseKiller = false
                MoodType.REAL,       // 真实心情（启用 mood 系统）
                400,                 // 默认冲刺时间
                false                // 不显示剩余时间
            );
            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setDefaultMax(1)
                .setAbleToPickUpRevolver(false) // 每局最多 1 个疯子
                .setServerGameTickEvent(this::handleServerTick);
        }

        /** 疯子商店：一次性手枪（250 金币） + 镇静剂（50 金币） */
        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new ShopEntry(new ItemStack(ModItems.ONCE_REVOLVER), 250, ShopEntry.Type.WEAPON));
            // 镇静剂贴图本身已是红色药水，无需设置 POTION_CONTENTS 组件
            entries.add(new ShopEntry(new ItemStack(com.dim201.dim201addon.item.Dim201ModItems.SEDATIVE),
                    50, ShopEntry.Type.TOOL));
            return entries;
        }

        /**
         * 每 tick 处理：
         * 强制 mood = 0.15f（二级无理智，< 0.2）
         * 只在 mood >= 0.2f 时才 setMood，减少网络同步次数。
         * 低 san 减速由 MadmanLowSanSlowMixin 从根源取消，此处无需任何速度效果。
         */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, MADMAN)) return;
            if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

            // 强制二级无理智（mood = 0.15f < 0.2 = DEPRESSIVE_MOOD_THRESHOLD）
            // 镇静剂生效时跳过强制低心情，由 SedativeManager 统一锁定为 0.5f
            if (!com.dim201.dim201addon.item.SedativeManager.isSedated(player)) {
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
                if (mood != null && mood.getMood() >= 0.2f) {
                    mood.setMood(0.15f);
                }
            }
        }
    }
}
