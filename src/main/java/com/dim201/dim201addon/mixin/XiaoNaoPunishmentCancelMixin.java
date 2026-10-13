package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.KidnapperPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.MadmanRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.game.GameUtils;

import java.util.UUID;

/**
 * 小脑惩罚统一取消 Mixin（合并绑架者 + 疯子两条免惩罚逻辑）
 *
 * <p>拦截 GameUtils.killPlayer 的 shot_innocent（误杀平民反噬）：
 *
 * <p>1) 绑架者免惩罚：好人质被击杀时，killer 不应被反噬。
 *    依据 KidnapperPlayerComponent 静态缓冲（lastGoodHostageKillerUuid +
 *    lastGoodHostageKilledTick），同 tick 匹配则 cancel。
 *    标记时机见 KidnapperPlayerComponent.registerStaticEvents() 情况 C。
 *
 * <p>2) 疯子双向免惩罚：
 *    - 疯子杀平民/警 → 反杀疯子时 victim 是疯子 → cancel
 *    - 警/平民杀疯子 → 普通击杀 HEAD 记录 killer UUID 到 ThreadLocal，
 *      嵌套的小脑反杀 HEAD 检查 ThreadLocal 匹配 victim → cancel
 *
 * <p>原理：OnTeammateKilledTeammate 是非拦截型事件，且附属 mod 监听器注册在 SRE 之后，
 * 无法用事件阻止 XiaoNaoHandler，只能 Mixin 拦截 killPlayer 本身。
 * 利用 killPlayer 的嵌套调用关系，ThreadLocal 在同一线程调用栈中正确传递。
 */
@Mixin(GameUtils.class)
public abstract class XiaoNaoPunishmentCancelMixin {

    @Unique
    private static final ResourceLocation DIM201$SHOT_INNOCENT =
            ResourceLocation.fromNamespaceAndPath("noellesroles", "shot_innocent");

    /** 疯子缓冲：最近被杀的疯子的 killer UUID（同一线程调用栈内有效） */
    @Unique
    private static final ThreadLocal<UUID> dim201$madmanVictimKiller = new ThreadLocal<>();

    /**
     * 注入 killPlayer(5 参数版) HEAD：
     * - shot_innocent：依次检查绑架者缓冲、疯子双向条件，命中则 cancel
     * - 普通击杀：victim 是疯子时记录 killer UUID 到 ThreadLocal
     *
     * @param victim      即将被杀的玩家（反噬场景下是"误杀者"本人）
     * @param spawnBody   是否生成尸体
     * @param killer      名义上的击杀者
     * @param deathReason 死亡原因
     * @param forceDeath  是否强制死亡
     * @param ci          Mixin CallbackInfo，用于 cancel
     */
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void dim201$cancelXiaoNao(
            Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (victim == null || deathReason == null) return;

        if (deathReason.equals(DIM201$SHOT_INNOCENT)) {
            // 1) 绑架者：好人质被合法击杀，取消对 killer 的反噬（同 tick 严格匹配）
            if (KidnapperPlayerComponent.lastGoodHostageKillerUuid != null
                    && victim.getUUID().equals(KidnapperPlayerComponent.lastGoodHostageKillerUuid)
                    && victim.level() instanceof net.minecraft.server.level.ServerLevel sl
                    && sl.getGameTime() == KidnapperPlayerComponent.lastGoodHostageKilledTick) {
                ci.cancel();
                return;
            }

            // 2) 疯子情况1：被反杀者(victim)是疯子 → cancel（疯子杀人不惩罚）
            if (MadmanRole.isMadman(victim)) {
                ci.cancel();
                return;
            }

            // 3) 疯子情况2：被反杀者(victim)杀了疯子 → cancel（杀疯子不惩罚）
            UUID madmanKiller = dim201$madmanVictimKiller.get();
            if (madmanKiller != null && madmanKiller.equals(victim.getUUID())) {
                dim201$madmanVictimKiller.remove();
                ci.cancel();
                return;
            }
        } else {
            // 普通击杀：victim 是疯子时，记录 killer UUID 到 ThreadLocal
            if (MadmanRole.isMadman(victim) && killer != null) {
                dim201$madmanVictimKiller.set(killer.getUUID());
            }
        }
    }

    /**
     * 注入 killPlayer(5 参数版) TAIL：
     * 只在普通击杀（非 shot_innocent）结束时清除 ThreadLocal。
     * shot_innocent 被 cancel 时不会执行 TAIL，不影响外层调用栈的 ThreadLocal 清除。
     */
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("TAIL"), remap = false)
    private static void dim201$clearMadmanBuffer(
            Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (!deathReason.equals(DIM201$SHOT_INNOCENT)) {
            dim201$madmanVictimKiller.remove();
        }
    }

    /**
     * 辅助：判断玩家是否在绑架中（kidnapperUuid != null）
     * 用于任何需要跳过惩罚 / 跳过操作拦截的 mixin 内判断。
     * 【Mixin 规范】：Mixin 类里所有自定义辅助方法必须是 private
     */
    @Unique
    @SuppressWarnings("unused")
    private static boolean dim201$isKidnappedHostage(Player p) {
        if (p == null) return false;
        var c = ModComponents.KIDNAPPER.getNullable(p);
        return c != null && c.kidnapperUuid != null;
    }
}
