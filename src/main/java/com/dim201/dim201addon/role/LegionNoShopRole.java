package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.LegionPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 无商店军团（全员张老师模式死亡复活专用）
 *
 * <p>与普通军团(LegionRole)的区别：
 * <ul>
 *   <li>无商店：getShopEntries() 返回空列表</li>
 *   <li>无初始物品：getDefaultItems() 返回空列表</li>
 *   <li>保留野性狂暴技能：onAbilityUse 触发 LegionPlayerComponent.toggleSkill</li>
 *   <li>保留技能期武器拾取限制</li>
 * </ul>
 *
 * <p>原理：SRE 的 getShopEntries() 无玩家上下文，无法按玩家区分商店内容，
 *   因此创建独立角色实例实现"无商店"效果。
 *   LegionPlayerComponent 组件键不变（dim201addon:legion），复用同一组件。
 */
public class LegionNoShopRole {

    public static final ResourceLocation LEGION_NO_SHOP_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "legion_no_shop");
    public static SRERole LEGION_NO_SHOP;

    public static void register() {
        LEGION_NO_SHOP = TMMRoles.registerRole(new LegionNoShopRoleImpl());
    }

    /** 判断玩家是否为无商店军团 */
    public static boolean isLegionNoShop(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning()) return false;
        SRERole role = gw.getRole(player);
        return role != null && role.equals(LEGION_NO_SHOP);
    }

    private static class LegionNoShopRoleImpl extends NormalRole {

        public LegionNoShopRoleImpl() {
            super(LEGION_NO_SHOP_ID,
                    0xFF7C0A02,    // 与军团同色
                    false,         // isInnocent=false（杀手）
                    true,          // canUseKiller=true
                    MoodType.FAKE,
                    -1,
                    true);
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setDefaultMax(0)                 // 不会被随机分配
                .setDefaultEnableChance(0)        // 刷新率0%
                .setDefaultEnableNeededPlayerCount(Integer.MAX_VALUE)  // 永不满足人数要求
                .setCanBeRandomedByOtherRoles(false)
                .setNeutrals(false);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        /** 无商店：返回空列表 */
        @Override
        public List<ShopEntry> getShopEntries() {
            return new ArrayList<>();
        }

        @Override
        public boolean onAbilityUse(@NotNull ServerPlayer player) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null) return false;
            SRERole role = gameWorld.getRole(player);
            if (role == null || !role.equals(LEGION_NO_SHOP)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(player);
            if (comp == null) return false;
            comp.toggleSkill(player);
            return true;
        }

        /** 技能期禁止拾取攻击型武器（与普通军团一致） */
        @Override
        public java.util.function.@NotNull Predicate<Item> cantPickupItem(@NotNull Player player) {
            return item -> {
                if (item == null) return false;
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                if (comp == null || !comp.skillActive) return false;
                return LegionPlayerComponent.isSkillTempStoreItem(item);
            };
        }
    }
}
