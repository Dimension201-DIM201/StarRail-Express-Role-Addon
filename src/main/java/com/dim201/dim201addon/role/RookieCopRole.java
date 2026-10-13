package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RookieCopRole {

    public static final ResourceLocation ROOKIE_COP_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "rookie_cop"
    );

    public static SRERole ROOKIE_COP;

    private static final ResourceLocation STANDARD_REVOLVER = ResourceLocation.parse("trainmurdermystery:standard_revolver");

    public static void register() {
        ROOKIE_COP = TMMRoles.registerRole(new RookieCopRoleImpl());
    }

    private static class RookieCopRoleImpl extends NormalRole {

        public RookieCopRoleImpl() {
            super(
                ROOKIE_COP_ID,
                0xFF87CEEB,
                false,
                false,
                MoodType.REAL,
                400,
                false
            );

            this.setNeutrals(true)
                .setNeutralForKiller(false)
                .setCanSeeCoin(true)
                .setCanSeeTime(false)
                .setOccupiedRoleCount(1)
                .setCanUseInstinct(false);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            Item revolverItem = BuiltInRegistries.ITEM.get(STANDARD_REVOLVER);
            if (revolverItem != null) {
                // 正义的心 - 200金币：直接调用 SRE RoleUtils 转变为义警
                ItemStack vigilanteStack = new ItemStack(revolverItem);
                vigilanteStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("正义的心"));

                entries.add(new ShopEntry(vigilanteStack, 200, ShopEntry.Type.WEAPON) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        if (!(player instanceof ServerPlayer serverPlayer)) {
                            return false;
                        }
                        return switchRole(serverPlayer, TMMRoles.VIGILANTE);
                    }
                });

                // 堕落 - 400金币：直接调用 SRE RoleUtils 转变为黑警
                ItemStack corruptStack = new ItemStack(revolverItem);
                corruptStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("堕落"));

                entries.add(new ShopEntry(corruptStack, 400, ShopEntry.Type.WEAPON) {
                    @Override
                    public boolean onBuy(@NotNull Player player) {
                        if (!(player instanceof ServerPlayer serverPlayer)) {
                            return false;
                        }
                        return switchRole(serverPlayer, CorruptCopRole.CORRUPT_COP);
                    }
                });
            }

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

        /**
         * 转变身份：校验购买者仍是新人警察后，调用 SRE 原生 RoleUtils 切换角色
         * （与 changeRole 指令同一底层 API：记录回放、统计场次、触发角色移除/赋予事件、发放默认物品），
         * 并发送标准的「欢迎登车」角色报幕代替自定义 Title
         *
         * @param player    购买者
         * @param targetRole 目标角色实例
         * @return 是否成功转变
         */
        private boolean switchRole(ServerPlayer player, SRERole targetRole) {
            var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null || targetRole == null) return false;
            var role = gameWorld.getRole(player);
            if (role == null || !role.equals(RookieCopRole.ROOKIE_COP)) {
                return false;
            }
            RoleUtils.changeRole(player, targetRole);
            RoleUtils.sendWelcomeAnnouncement(player, targetRole);
            return true;
        }
    }
}
