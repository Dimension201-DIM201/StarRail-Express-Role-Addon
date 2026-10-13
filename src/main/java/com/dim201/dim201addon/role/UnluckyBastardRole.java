package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.UnluckyBastardPlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 倒霉蛋（Unlucky Bastard）
 * 平民阵营
 *
 * 描述：「诶诶诶！这刀是谁塞在我手里的！人真的不是我杀的啊！」
 *
 * 被动能力：
 *  · 身边 10 格内有人死亡时，强制拿起一把进入 30s 冷却的刀（栽赃）
 *  · 若 30s 冷却结束时仍未死亡 → 额外获得 75 金币
 *  · 被动能力每局仅触发一次，刀会在冷却结束后自动回收
 */
public class UnluckyBastardRole {

    public static final ResourceLocation UNLUCKY_BASTARD_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "unlucky_bastard");

    public static SRERole UNLUCKY_BASTARD;

    public static void register() {
        UNLUCKY_BASTARD = TMMRoles.registerRole(new UnluckyBastardRoleImpl());
    }

    private static class UnluckyBastardRoleImpl extends NormalRole {

        public UnluckyBastardRoleImpl() {
            super(
                    UNLUCKY_BASTARD_ID,
                    0xFFFFB90F,              // 亮黄色（Gold）：平民阵营亮色
                    true,                    // isInnocent：true → 平民/好人阵营
                    false,                   // canUseKiller：不能使用杀手能力
                    MoodType.REAL,           // 真实心情
                    400,                     // maxSprintTime：平民体力 400
                    true                     // hideScoreboard：true → 看不到时间（平民阵营）
            );

            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setDefaultMax(1)                 // 每局最多 1 个
                .setCanBeRandomedByOtherRoles(true)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public @NotNull List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public @NotNull List<ShopEntry> getShopEntries() {
            return new ArrayList<>();
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            // 没有主动技能，直接返回 false
            return false;
        }

        /** 角色 tick：推进冷却倒计时（由 SRE 调用，仅对当前角色玩家触发） */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, UNLUCKY_BASTARD)) return;
            UnluckyBastardPlayerComponent comp = ModComponents.UNLUCKY_BASTARD.get(player);
            comp.tick(player);
        }
    }
}
