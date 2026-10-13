package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class KoishiRole {

    public static final ResourceLocation KOISHI_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "koishi"
    );
    
    public static SRERole KOISHI;

    public static void register() {
        KOISHI = TMMRoles.registerRole(new KoishiRoleImpl());
    }

    private static class KoishiRoleImpl extends NormalRole {
        
        public KoishiRoleImpl() {
            super(
                KOISHI_ID,
                0xFF7CFC00, // 青绿色
                false,      // isInnocent = false（不是无辜的）
                false,      // canUseKiller = false（不能使用杀手能力）
                MoodType.FAKE,  // 假心情
                -1,         // maxSprintTime（默认）
                true        // canSeeTime = true
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(false)
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(false)
                .setCanSeeTeammateKillerRole(true)
                .setNeutralForKiller(true)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            
            // 刀 — 130金币
            ItemStack knifeStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:knife")
                )
            );
            if (knifeStack.getItem() != Items.AIR) {
                knifeStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§c§l刀"));
                entries.add(new ShopEntry(knifeStack, 130, ShopEntry.Type.WEAPON));
            }
            
            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            KoishiPlayerComponent comp = ModComponents.KOISHI.get(player);
            return comp.useSkill(player);
        }
        
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, KOISHI)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }

            KoishiPlayerComponent comp = ModComponents.KOISHI.get(player);
            comp.tick(player);
        }
    }
}