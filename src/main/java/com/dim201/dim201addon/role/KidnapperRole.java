package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.KidnapperPlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class KidnapperRole {

    public static final ResourceLocation KIDNAPPER_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "kidnapper");

    public static SRERole KIDNAPPER;

    public static void register() {
        KIDNAPPER = TMMRoles.registerRole(new KidnapperRoleImpl());
    }

    private static class KidnapperRoleImpl extends NormalRole {

        public KidnapperRoleImpl() {
            super(
                    KIDNAPPER_ID,
                    0xFF483D8B,              // 暗板岩蓝（杀手阵营深色）
                    false,                   // isInnocent：false → 杀手阵营
                    true,                    // canUseKiller：true → 走默认杀手商店 + 杀手能力
                    MoodType.FAKE,           // 假心情
                    -1,                      // maxSprintTime：默认
                    false                    // hideScoreboard：能看时间
            );
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setCanSeeTeammateKillerRole(true)
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(true);
        }

        @Override
        public @NotNull List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        // 注意：不再 override getShopEntries()
        // ——返回 null 时 ShopContent.getShopEntries() 会根据 canUseKiller==true
        //   自动使用默认杀手商店（getDefaultKnifeEntries()，SRE 内置完整商品+价格）
        //   这样绑架者自动获得刀/左轮/手雷/霰弹/心理战/绳/撬棍/开锁器/子弹/关灯/消音器等，
        //   和其他杀手角色完全一致。

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            // 技能键 → 绑架者组件上的 onAbilityUse：没绑架则抓人，已绑架则放
            KidnapperPlayerComponent comp = ModComponents.KIDNAPPER.get(player);
            if (comp == null) return false;
            return comp.onAbilityUse(player);
        }
    }
}

