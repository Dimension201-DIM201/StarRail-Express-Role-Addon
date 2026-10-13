package com.dim201.dim201addon.role;

import com.dim201.dim201addon.WhiteSnakePlayerComponent;
import com.dim201.dim201addon.ModComponents;
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

public class WhiteSnakeRole {

    public static final ResourceLocation WHITESNAKE_ID = ResourceLocation.fromNamespaceAndPath(
        "dim201addon", "whitesnake"
    );
    
    public static SRERole WHITESNAKE;

    public static void register() {
        WHITESNAKE = TMMRoles.registerRole(new WhiteSnakeRoleImpl());
    }

    private static class WhiteSnakeRoleImpl extends NormalRole {
        
        public WhiteSnakeRoleImpl() {
            super(
                WHITESNAKE_ID,
                0x808080, // 灰色（角色本身颜色）
                false,
                true,     // canUseKiller = true（标准杀手）
                MoodType.FAKE,
                -1,
                true
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(false)
                .setCanPickUpRevolver(true)
                .setCanBeRandomedByOtherRoles(false)
                .setDefaultEnableNeededPlayerCount(12) // 12人以上出现
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            
            ItemStack knifeStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:knife")
                )
            );
            if (knifeStack.getItem() != Items.AIR) {
                knifeStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§c§l刀"));
                entries.add(new ShopEntry(knifeStack, 200, ShopEntry.Type.WEAPON));
            }
            
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

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
            if (comp == null) return false;

            // 切换技能由专用切换键处理；这里只负责释放当前技能
            switch (comp.currentSkill) {
                case ABILITY_DISSOLVE:
                    return comp.useAbilityDissolve(player);
                case SPIRIT_DISSOLVE:
                    return comp.useSpiritDissolve(player);
                default:
                    return false;
            }
        }
        
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, WHITESNAKE)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }

            WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
            if (comp != null) {
                comp.serverTick();
            }
        }
    }
}