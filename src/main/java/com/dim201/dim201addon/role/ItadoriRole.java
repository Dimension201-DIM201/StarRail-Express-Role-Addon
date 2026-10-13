package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ItadoriPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 虎杖悠仁 - 警长阵营
 *
 * 主动技「不义游戏响应」：东堂按下技能键后 5 秒内虎杖按下 → 双方互换位置，
 * 虎杖获得 10 秒专注（夜视 + 空手击杀连击需求 5 → 2），击杀后播放 Tier3 击掌特效。
 *
 * 被动「逕庭拳」：空手连续攻击同一目标 5 次直接击杀（无 0.5 秒间隔限制），
 * 好人误杀好人触发小脑惩罚。
 */
public class ItadoriRole {

    public static final ResourceLocation ITADORI_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "itadori");

    /** 逕庭拳击杀死亡原因 */
    public static final ResourceLocation DIVERGENT_FIST_DEATH =
            Dim201Addon.id("itadori_divergent_fist");

    public static SRERole ITADORI;

    public static void register() {
        ITADORI = TMMRoles.registerRole(new ItadoriRoleImpl());
        Harpymodloader.setRoleMaximum(ITADORI_ID, 1);
        registerEvents();
    }

    /** 判断玩家是否为虎杖悠仁 */
    public static boolean isItadori(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        return gameWorld.isRole(player, ITADORI);
    }

    /** 注册空手拳击权限 + 逕庭拳连击事件（仅虎杖生效） */
    public static void registerEvents() {
        // 允许虎杖空手出拳（SRE 默认禁止平民拳击）
        AllowPlayerPunching.EVENT.register(player -> {
            if (player.hasEffect(ModEffects.SAFE_TIME)) return false;
            if (!isItadori(player)) return false;
            return player.getMainHandItem().isEmpty();
        });

        AttackEntityCallback.EVENT.register(ItadoriRole::onPunch);
    }

    private static InteractionResult onPunch(Player attacker, Level level, InteractionHand hand,
                                             Entity entity, EntityHitResult hitResult) {
        if (SRE.isLobby) return InteractionResult.PASS;
        if (!isItadori(attacker)) return InteractionResult.PASS;
        if (!GameUtils.isPlayerAliveAndSurvival(attacker)) return InteractionResult.PASS;
        if (!(entity instanceof Player victim) || !GameUtils.isPlayerAliveAndSurvival(victim) || victim.isSpectator()) {
            return InteractionResult.PASS;
        }
        // 必须空手
        ItemStack mainHand = attacker.getMainHandItem();
        if (!mainHand.isEmpty()) return InteractionResult.PASS;
        if (attacker.getUUID().equals(victim.getUUID())) return InteractionResult.PASS;
        // 安全时间内不可攻击
        if (victim.getCooldowns().isOnCooldown(net.minecraft.world.item.Items.CLOCK)) {
            return InteractionResult.PASS;
        }

        // 客户端返回成功：播放挥拳动画，逻辑只在服务端执行
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItadoriPlayerComponent comp = ModComponents.ITADORI.get(attacker);
        comp.onPunchVictim((net.minecraft.server.level.ServerPlayer) victim);
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    private static class ItadoriRoleImpl extends NormalRole {
        ItadoriRoleImpl() {
            // 警长阵营：好人 + 真实理智 + 平民疾跑上限，颜色虎杖粉
            super(ITADORI_ID, 0xFFF48FB1, true, false, MoodType.REAL, 400, false);
            // 与东堂相同：不能开启杀手直觉，互相感知完全依赖描边渲染
            this.setCanSeeTime(false).setVigilanteTeam(true).setCanUseInstinct(false);
        }
    }
}
