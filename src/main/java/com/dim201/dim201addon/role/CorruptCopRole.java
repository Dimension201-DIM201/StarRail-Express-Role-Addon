package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CorruptCopRole {

    public static final ResourceLocation CORRUPT_COP_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "corrupt_cop"
    );

    public static SRERole CORRUPT_COP;

    /** 刀物品 id（SRE 原版杀手刀） */
    private static final ResourceLocation KNIFE_ID = ResourceLocation.parse("trainmurdermystery:knife");

    public static void register() {
        CORRUPT_COP = TMMRoles.registerRole(new CorruptCopRoleImpl());
    }

    private static class CorruptCopRoleImpl extends NormalRole {

        public CorruptCopRoleImpl() {
            super(
                CORRUPT_COP_ID,
                0xFF000080,
                false,
                true,
                MoodType.FAKE,
                -1,
                true
            );

            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setDefaultMax(0)           // 最大数量0，不会正常刷新
                .setCanBeRandomedByOtherRoles(false);  // 不能被其他角色随机变换
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            // 转变身份时发放一把德林加（与四季映姬一致，商店才能补充子弹）
            Item derringerItem = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:derringer"));
            if (derringerItem != null) {
                return List.of(new ItemStack(derringerItem));
            }
            return List.of();
        }

        /** 腐败警察商店：刀（130金币） + 补充德林加子弹（200金币，参考四季映姬） */
        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 刀 - 130金币
            Item knifeItem = BuiltInRegistries.ITEM.get(KNIFE_ID);
            if (knifeItem != null) {
                entries.add(new ShopEntry(new ItemStack(knifeItem), 130, ShopEntry.Type.WEAPON));
            }

            // 补充德林加子弹 - 200金币（参考四季映姬：德林加已耗尽才能补充）
            ItemStack bulletStack = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:magnum_bullet")));
            bulletStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("补充德林加子弹"));
            entries.add(new ShopEntry(bulletStack, 200, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!GameUtils.haveUsedoutDerringer(player)) {
                        this.setFailedMessage(Component.literal("§c德林加未耗尽，无法补充"));
                        return false;
                    }
                    return GameUtils.refillDerringer(player, true);
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
