package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 克劳恩皮斯 - 杀手方中立（纯服务端逻辑）
 *
 * <p>客户端 UI 代码在 {@link com.dim201.dim201addon.client.ClownpieceClientUI}，
 * 由 {@code Dim201AddonClient.onInitializeClient()} 注册。
 * 本类禁止 import 任何客户端专属类（net.minecraft.client.*），
 * 否则服务端加载时会 NoClassDefFoundError。
 */
public class ClownpieceRole {

    private static final Logger LOGGER = LoggerFactory.getLogger("Dim201Addon-Clownpiece");

    public static final ResourceLocation CLOWNPIECE_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "clownpiece"
    );

    public static SRERole CLOWNPIECE;

    /** 点击玩家头像刷新冷却消耗 */
    public static final int REFRESH_TARGET_COST = 100;
    /** 技能键刷新范围冷却消耗 */
    public static final int REFRESH_AOE_COST = 300;
    /** 技能键刷新范围（格） */
    public static final int REFRESH_AOE_RADIUS = 16;
    /** 点击玩家头像刷新冷却技能冷却（60s，和普奇神父「认知妨碍」一致） */
    public static final int REFRESH_COOLDOWN = 60 * 20;

    public static void register() {
        CLOWNPIECE = TMMRoles.registerRole(new ClownpieceRoleImpl());
        org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(CLOWNPIECE_ID, 1);
        LOGGER.info("克劳恩皮斯注册完成");
    }

    /**
     * 刷新指定玩家背包内所有物品的冷却时间
     *
     * @param target 目标玩家
     * @return 实际清除冷却的物品数量
     */
    public static int clearAllCooldowns(ServerPlayer target) {
        int cleared = 0;
        for (ItemStack stack : target.getInventory().items) {
            if (!stack.isEmpty() && target.getCooldowns().isOnCooldown(stack.getItem())) {
                target.getCooldowns().removeCooldown(stack.getItem());
                cleared++;
            }
        }
        for (ItemStack stack : target.getInventory().armor) {
            if (!stack.isEmpty() && target.getCooldowns().isOnCooldown(stack.getItem())) {
                target.getCooldowns().removeCooldown(stack.getItem());
                cleared++;
            }
        }
        ItemStack offhand = target.getInventory().offhand.get(0);
        if (!offhand.isEmpty() && target.getCooldowns().isOnCooldown(offhand.getItem())) {
            target.getCooldowns().removeCooldown(offhand.getItem());
            cleared++;
        }
        return cleared;
    }

    private static class ClownpieceRoleImpl extends NormalRole {

        public ClownpieceRoleImpl() {
            super(
                    CLOWNPIECE_ID,
                    0xFFFF69B4,              // 热粉色
                    false,                    // isInnocent = 非好人
                    false,                    // canUseKiller = false → 中立
                    MoodType.FAKE,
                    -1,                       // maxSprintTime：默认
                    false                     // hideScoreboard：能看时间
            );
            this.setCanSeeTime(true)
                .setCanUseInstinct(false)
                .setCanSeeTeammateKillerRole(true)
                .setDefaultMax(1)
                .setNeutralForKiller(true)
                .setCanBeRandomedByOtherRoles(true);
            // ⚠️ addChild(Consumer<LimitedInventoryScreen>) 在客户端侧注册，
            // 因为 LimitedInventoryScreen 是客户端类，common 目录不能引用
        }

        @Override
        public @NotNull List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        /**
         * 商店条目返回空列表 —— 克劳恩皮斯不需要商店道具。
         * 背包界面的玩家头像按钮通过客户端 Mixin（ClownpieceInventoryMixin）注入，
         * 完全不依赖 SRE 的 ShopEntry → addChild 链路。
         */
        @Override
        public List<ShopEntry> getShopEntries() {
            return new ArrayList<>();
        }

        /**
         * 技能键 → 刷新 16 格内所有玩家物品冷却（消耗 300 金币）
         */
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop.balance < REFRESH_AOE_COST) {
                player.displayClientMessage(
                        Component.literal("§c金币不足！需要 " + REFRESH_AOE_COST + " 金币，当前 " + shop.balance),
                        true);
                return false;
            }
            shop.setBalance(shop.balance - REFRESH_AOE_COST);
            shop.sync();

            int totalCleared = 0;
            int targetCount = 0;
            for (Player nearby : player.level().players()) {
                if (!(nearby instanceof ServerPlayer sp)) continue;
                if (!sp.isAlive() || sp.isSpectator()) continue;
                if (sp.distanceTo(player) > REFRESH_AOE_RADIUS) continue;
                totalCleared += clearAllCooldowns(sp);
                targetCount++;
            }

            String msg = String.format("§6燃烧生命的火把！刷新了 §f%d §6名玩家的 §f%d §6个物品冷却",
                    targetCount, totalCleared);
            player.displayClientMessage(Component.literal(msg), true);
            return true;
        }
    }
}
