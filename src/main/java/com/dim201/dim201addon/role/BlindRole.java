package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 盲人 - 平民阵营
 *
 * 被动「心之眼」：永久失明 + 透视 8 格内所有玩家
 * 商店：盲杖（右键消耗，恢复视野 30 秒，恢复期内跳过失明施加）
 */
public class BlindRole {

    public static final ResourceLocation BLIND_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "blind"
    );

    public static SRERole BLIND;

    /** 透视范围（格） */
    public static final double VISION_RANGE = 8.0D;
    /** 失明效果间隔（tick），每 5 秒强制刷新一次 */
    public static final int BLIND_REFRESH_TICKS = 5 * 20;

    public static void register() {
        BLIND = TMMRoles.registerRole(new BlindRoleImpl());
    }

    /** 判断玩家是否为盲人（通用，服务端/客户端均可调用） */
    public static boolean isBlind(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        return gameWorld.isRole(player, BLIND);
    }

    /**
     * 客户端专用判定：不依赖 isRunning()（客户端同步时序下可能返回 false）。
     * 供 BlindVisionHighlight 等客户端渲染逻辑使用，避免 isRunning() 不一致导致透视失效。
     */
    public static boolean isBlindClient(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        return gameWorld.isRole(player, BLIND);
    }

    private static class BlindRoleImpl extends NormalRole {

        BlindRoleImpl() {
            super(
                    BLIND_ID,
                    0xFF2F4F4F,           // 深灰色
                    true,                  // isInnocent = true（平民阵营）
                    false,                 // canUseKiller = false
                    MoodType.REAL,
                    400,                   // 默认冲刺时间
                    false                  // hideScoreboard
            );
            this.setCanSeeTime(false)
                    .setCanUseInstinct(false)
                    .setDefaultMax(0)
                    .setServerGameTickEvent(this::handleServerTick);
        }

        /**
         * 商店：盲杖（125 金币）
         * 原理：盲杖是道具物品，右键消耗触发发光（见 BlindStickItem）
         */
        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new ShopEntry(
                    new ItemStack(com.dim201.dim201addon.item.Dim201ModItems.BLIND_STICK),
                    125,
                    ShopEntry.Type.WEAPON));
            return entries;
        }

        /**
         * 每 tick 处理：
         * 1. 永久失明（MobEffects.BLINDNESS，无限时长）
         *    每 5 秒重新施加一次，确保不会被其他机制覆盖/移除
         * 2. 盲杖恢复期内跳过失明；恢复期刚结束立即补施加，避免最长 5 秒的可见空档
         * 3. 死亡后不施加
         */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, BLIND)) return;
            if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) return;

            com.dim201.dim201addon.BlindPlayerComponent comp =
                    com.dim201.dim201addon.BlindPlayerComponent.KEY.get(player);
            long now = player.level().getGameTime();
            if (comp.isVisionRestored(now)) return; // 盲杖恢复期：跳过失明
            if (comp.hasRestoreMark()) {
                // 恢复期刚结束：立即重新失明并清除标记
                comp.clearVisionRestore();
                applyBlindness(player);
                return;
            }

            if (player.tickCount % BLIND_REFRESH_TICKS == 0) {
                applyBlindness(player);
            }
        }

        /** 施加无限时长失明（无粒子、客户端可见图标） */
        private static void applyBlindness(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    MobEffectInstance.INFINITE_DURATION,
                    0,
                    false, false, false));
        }
    }
}
