package com.dim201.dim201addon.role;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.WhiteSnakePlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class PucciRole {

    public static final ResourceLocation PUCCI_ID = ResourceLocation.fromNamespaceAndPath(
        "dim201addon", "pucci"
    );
    
    public static SRERole PUCCI;

    public static void register() {
        PUCCI = TMMRoles.registerRole(new PucciRoleImpl());
    }

    private static class PucciRoleImpl extends NormalRole {
        
        public PucciRoleImpl() {
            super(
                PUCCI_ID,
                0x808080, // 白色
                false,    // isInnocent = 非乘客
                false,     // canUseKiller = false（杀手方中立）
                MoodType.FAKE,
                Integer.MAX_VALUE, // 无限冲刺
                true      // 隐藏计分板
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setCanPickUpRevolver(true)
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(false)
                .setDefaultEnableNeededPlayerCount(12)
                .setDefaultEnableChance(0)
                .setNeutralForKiller(true)
                .setCanSeeTeammateKillerRole(true)
                .setServerGameTickEvent(this::handleServerTick)
                .setRoleData(PucciDisguiseRoleData::new);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            
            // 左轮手枪 300g
            ItemStack revolverStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:revolver")
                )
            );
            if (revolverStack.getItem() != Items.AIR) {
                revolverStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§c§l左轮手枪"));
                entries.add(new ShopEntry(revolverStack, 300, ShopEntry.Type.WEAPON));
            }
            
            // 开锁器 100g
            ItemStack lockpickStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:lockpick")
                )
            );
            if (lockpickStack.getItem() != Items.AIR) {
                lockpickStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§e§l开锁器"));
                entries.add(new ShopEntry(lockpickStack, 100, ShopEntry.Type.TOOL));
            }
            
            return entries;
        }
        
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, PUCCI)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }

            WhiteSnakePlayerComponent component = ModComponents.WHITESNAKE.get(player);
            if (component == null) {
                return;
            }

            if (player.tickCount % 40 == 0) {
                component.syncShieldsWithDiscs();
            }
            component.onArmorConsumed();
        }
    }
}