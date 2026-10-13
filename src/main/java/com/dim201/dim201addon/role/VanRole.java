package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class VanRole {

    public static final ResourceLocation VAN_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "van"
    );
    
    public static SRERole VAN;

    // 修改：飞刀
    private static final ResourceLocation THROWING_KNIFE = ResourceLocation.parse("noellesroles:throwing_knife");
    private static final ResourceLocation LOCKPICK = ResourceLocation.parse("trainmurdermystery:lockpick");

    public static void register() {
        VAN = TMMRoles.registerRole(new VanRoleImpl());
    }

    private static class VanRoleImpl extends NormalRole {
        
        public VanRoleImpl() {
            super(
                VAN_ID,
                0xFF8B4513,
                false,
                true,
                MoodType.FAKE,
                -1,
                true
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(true);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            
            // 修改：120金币的飞刀
            Item throwingKnifeItem = BuiltInRegistries.ITEM.get(THROWING_KNIFE);
            if (throwingKnifeItem != null) {
                entries.add(new ShopEntry(new ItemStack(throwingKnifeItem), 120, ShopEntry.Type.WEAPON));
            }
            
            Item lockpickItem = BuiltInRegistries.ITEM.get(LOCKPICK);
            if (lockpickItem != null) {
                entries.add(new ShopEntry(new ItemStack(lockpickItem), 80, ShopEntry.Type.TOOL));
            }
            
            // 变身道具 - 修改：200金币
            ItemStack transformStack = new ItemStack(Items.TOTEM_OF_UNDYING);
            transformStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, 
                net.minecraft.network.chat.Component.literal("§c§l变身"));
            
            entries.add(new ShopEntry(transformStack, 200, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return false;
                    }
                    
                    // 检查是否是 Van
                    var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null) return false;
                    var role = gameWorld.getRole(player);
                    if (role == null || !role.equals(VanRole.VAN)) {
                        return false;
                    }
                    
                    // 转变为 VanDDF
                    VanDDFRole.transformFromVan(serverPlayer);
                    
                    return true;
                }
            });
            
            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            SREAbilityPlayerComponent comp = SREAbilityPlayerComponent.KEY.get(player);
            if (comp.cooldown > 0) {
                return true;
            }
            return false;
        }
    }
}