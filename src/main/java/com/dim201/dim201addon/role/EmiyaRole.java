package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.EmiyaPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EmiyaRole {

    public static final ResourceLocation EMIYA_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "emiya");

    public static SRERole EMIYA;

    private static boolean eventsRegistered = false;

    public static void register() {
        EMIYA = TMMRoles.registerRole(new EmiyaRoleImpl());
        Harpymodloader.setRoleMaximum(EMIYA_ID, 1);
        registerEvents();
    }

    /**
     * 自定义ShopEntry：购买后不给予物品，而是切换投影目标
     */
    public static class EmiyaProjectionShopEntry extends ShopEntry {
        private final EmiyaPlayerComponent.ProjectionItem projectionItem;

        public EmiyaProjectionShopEntry(ItemStack stack, EmiyaPlayerComponent.ProjectionItem projectionItem) {
            super(stack, 0, Type.TOOL);
            this.projectionItem = projectionItem;
        }

        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null || !gameWorld.isRole(player, EMIYA)) {
                // 非卫宫士郎玩家，正常给物品
                return super.onBuy(player);
            }

            // 卫宫士郎玩家：切换投影目标，不给物品
            EmiyaPlayerComponent comp = ModComponents.EMIYA.get(serverPlayer);
            if (comp != null) {
                comp.setProjection(projectionItem, serverPlayer);
            }
            // 返回true表示购买成功，但不会调用super.onBuy，所以不会给物品
            return true;
        }

        @Override
        public boolean canBuy(@NotNull Player player) {
            // 任何人都可以购买，只有卫宫士郎会触发切换投影
            return true;
        }
    }

    private static class EmiyaRoleImpl extends NormalRole {

        public EmiyaRoleImpl() {
            // 警长阵营：isInnocent=true, canUseKiller=false, vigilanteTeam
            super(EMIYA_ID, 0xFF00BFFF, true, false, MoodType.REAL, 600, true);
            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setInstinctNightVision(false)
                .setAbleToPickUpRevolver(true)
                .setDefaultMax(1)
                .setVigilanteTeam(true)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 添加投影物品（价格0，购买后切换投影目标）
            for (EmiyaPlayerComponent.ProjectionItem projItem : EmiyaPlayerComponent.ProjectionItem.values()) {
                Item item = projItem.getItem();
                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);
                    // 自定义名称，显示魔力消耗
                    stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                        Component.literal("§b§l投影切换 §7| §f" + projItem.displayName + " §8[§e魔力 " + projItem.manaCost + "§8]"));
                    entries.add(new EmiyaProjectionShopEntry(stack, projItem));
                }
            }

            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) return true;

            EmiyaPlayerComponent comp = ModComponents.EMIYA.get(player);
            if (comp == null) return true;

            // 使用投影魔术
            comp.useProjection(player);
            return true;
        }

        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, EMIYA)) return;
            if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
            EmiyaPlayerComponent comp = ModComponents.EMIYA.get(player);
            if (comp == null) return;
            // tick逻辑在EmiyaPlayerComponent.serverTick中处理
        }
    }

    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        // ---------- 卫宫士郎死亡：掉落左轮手枪 ----------
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;

            var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null) return;
            if (!gameWorld.isRole(sp, EMIYA)) return;

            // 掉落左轮手枪
            sp.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
        });
    }

    public static void resetRoleState() {
    }

    // 辅助方法：判断是否是卫宫士郎
    public static boolean isEmiya(Player player) {
        if (player.level().isClientSide) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(EMIYA);
    }
}
