package com.dim201.dim201addon.item.card;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 自定义能力卡（菜刀/地底的太阳等）的「好人误杀好人」小脑惩罚补全。
 *
 * <h3>背景</h3>
 * SRE 的小脑惩罚流程：
 * <ol>
 *   <li>{@code GameMode.killPlayer()} 检测到好人杀好人 → 触发
 *       {@code OnTeammateKilledTeammate.EVENT(playerKilled(victim, killer, true, deathReason))}</li>
 *   <li>{@code XiaoNaoHandler} 监听该事件：
 *       {@code if (isInnocent) → if (!isXiaoNaoReason(deathReason)) return; → killPlayer(killer, shot_innocent)}</li>
 *   <li>{@code TeamKillViolationHandler.handle()} 内再次 {@code if (!isXiaoNaoReason(deathReason)) return}，
 *       会跳过警告字幕与队友击杀窗口计数。</li>
 * </ol>
 *
 * <p>问题：自定义死亡原因（{@code dim201addon:cleaver_hit} / {@code underground_sun_hit}）
 * 不在 SRE 硬编码的 isXiaoNaoReason 白名单内 → 两个处理器都直接 return，等于没罚。
 *
 * <h3>修复</h3>
 * 本类 {@link #applyIfApplicable(ServerPlayer, ServerPlayer, ResourceLocation)}
 * 完全镜像 XiaoNaoHandler 的豁免判断 + shot_innocent 处死 + 仇杀客强化，
 * 同时复刻 TeamKillViolationHandler 的时间窗口警告字幕 / 违规 mcfunction，
 * 并使用反射写入同一份 {@code TeamKillViolationHandler.teamKillRecords}，
 * 让自定义武器与原生武器的队友击杀统计共享窗口。
 */
public final class CardTeamKillPunisher {

    private CardTeamKillPunisher() {}

    /**
     * 用自定义能力击杀后，若命中「好人误杀好人」则处罚小脑。
     *
     * @return true 已触发 shot_innocent 处死
     */
    public static boolean applyIfApplicable(ServerPlayer victim, ServerPlayer killer,
                                            ResourceLocation deathReason) {
        if (!GameUtils.isPlayerAliveAndSurvival(killer)) return false;

        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());
        SRERole killerRole = gw.getRole(killer);
        SRERole victimRole = gw.getRole(victim);
        if (killerRole == null || victimRole == null) return false;

        // 仅限好人杀好人（isInnocent=true）
        if (!killerRole.isInnocent() || !victimRole.isInnocent()) return false;

        // —— 以下豁免条件完全镜像 XiaoNaoHandler ——
        if (gw.isRole(victim, TMMRoles.DISCOVERY_CIVILIAN)) return false;   // 游客
        if (victimRole.isMafiaTeam()) return false;                          // 家族成员
        if (killerRole.isNeutrals() || victimRole.isNeutrals()) return false;// 中立职业

        if (gw.isRole(victim, ModRoles.MAGICIAN)) {
            io.wifi.starrailexpress.cca.SREPlayerPsychoComponent psycho =
                    io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.get(victim);
            if (psycho != null && psycho.getPsychoTicks() > 0) return false;  // 疯狂魔术师
        }
        if (gw.isRole(killer, ModRoles.AVENGER)) {
            AvengerPlayerComponent avc = ModComponents.AVENGER.get(killer);
            if (avc != null && avc.killerUuid != null
                    && avc.killerUuid.equals(victim.getUUID())) return false; // 复仇者打对凶手
        }
        if (gw.isRole(victim, ModRoles.VOODOO)) return false;                // 巫毒娃娃

        // —— 开始处罚 ——

        // 1. 队友击杀警告字幕 / 阈值 mcfunction（镜像 TeamKillViolationHandler.handle）
        handleTeamKillWarning(killer, deathReason);

        // 2. 小脑：shot_innocent 处死击杀者（镜像 XiaoNaoHandler.L73-L85）
        //    通过反射读取 NoellesRolesConfig，避免因 auto-config 类不在编译类路径而失败
        if (readAccidentalKillPunishmentEnabled()) {
            boolean isXiaonao = true;
            if (deathReason.equals(GameConstants.DeathReasons.DERRINGER)
                    && RoleUtils.isPlayerTheJob(killer, THMiscRoles.SHIKIEIKI)) {
                isXiaonao = false;
            }
            if (isXiaonao) {
                GameUtils.killPlayer(killer, true, null, Noellesroles.id("shot_innocent"));
                // 仇杀客强化
                for (ServerPlayer p : victim.serverLevel().players()) {
                    if (gw.isRole(p, ModRoles.BLOOD_FEUDIST)) {
                        BloodFeudistPlayerComponent bf = ModComponents.BLOOD_FEUDIST.get(p);
                        if (bf != null) bf.onAccidentalKill();
                    }
                }
                // 累加队友击杀统计
                PlayerStatsManager.get(killer).incrementTotalTeamKills();
                return true;
            }
        }
        return false;
    }

    // —————————————— TeamKillViolationHandler 复刻 ——————————————

    private static final int WARN_COLOR = 0xFFFF6B35;
    private static final int VIOLATION_COLOR = 0xFFFF3333;
    private static final int SUBTITLE_DURATION = 60;

    /** 共用 SRE 原生的时间窗口映射；反射失败时退化为独立容器 */
    private static Map<UUID, Deque<Long>> sharedTeamKillRecords;

    private static void handleTeamKillWarning(ServerPlayer killer, ResourceLocation deathReason) {
        try {
            Class<?> hc = Class.forName("io.wifi.starrailexpress.SREConfig");
            Object cfg = hc.getMethod("instance").invoke(null);
            boolean enabled = cfg.getClass().getField("teamKillViolationEnabled").getBoolean(cfg);
            if (!enabled) return;
            if (killer.hasPermissions(1)) return;

            int windowSec = cfg.getClass().getField("teamKillViolationWindowSeconds").getInt(cfg);
            int threshold = cfg.getClass().getField("teamKillViolationThreshold").getInt(cfg);
            String mcFunction = (String) cfg.getClass()
                    .getField("teamKillViolationMcFunction").get(cfg);

            UUID ku = killer.getUUID();
            Deque<Long> records = getSharedRecords().computeIfAbsent(ku, k -> new ArrayDeque<>());
            long now = System.currentTimeMillis();
            records.addLast(now);
            long windowMs = windowSec * 1000L;
            while (!records.isEmpty() && records.peekFirst() < now - windowMs) {
                records.pollFirst();
            }
            int currentCount = records.size();

            if (currentCount >= threshold) {
                sendSubtitle(killer,
                        Component.translatable("subtitle.sre.teamkill_violation")
                                .withStyle(ChatFormatting.BOLD),
                        Component.translatable("subtitle.sre.teamkill_punished",
                                currentCount, threshold).withStyle(ChatFormatting.RED),
                        VIOLATION_COLOR);
                if (mcFunction != null && !mcFunction.isEmpty()) {
                    GameUtils.executeFunction(
                            killer.getServer().createCommandSourceStack(), mcFunction);
                }
                records.clear();
            } else {
                int remaining = threshold - currentCount;
                sendSubtitle(killer,
                        Component.translatable("subtitle.sre.teamkill_warning")
                                .withStyle(ChatFormatting.BOLD),
                        Component.translatable("subtitle.sre.teamkill_remaining",
                                currentCount, threshold, remaining)
                                .withStyle(ChatFormatting.GOLD),
                        WARN_COLOR);
            }
        } catch (Exception ignore) {
            // 配置反射失败：跳过警告字幕，不影响核心 shot_innocent 处罚
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Deque<Long>> getSharedRecords() {
        if (sharedTeamKillRecords != null) return sharedTeamKillRecords;
        try {
            Class<?> h = Class.forName("io.wifi.starrailexpress.game.TeamKillViolationHandler");
            Field f = h.getDeclaredField("teamKillRecords");
            f.setAccessible(true);
            sharedTeamKillRecords = (Map<UUID, Deque<Long>>) f.get(null);
        } catch (Exception e) {
            sharedTeamKillRecords = new HashMap<>();
        }
        return sharedTeamKillRecords;
    }

    private static void sendSubtitle(ServerPlayer player, Component main, Component sub, int color) {
        net.exmo.sre.subtitle.SubtitleCommand.sendToPlayer(
                player, main, sub, SUBTITLE_DURATION, color, false,
                net.exmo.sre.subtitle.SubtitleS2CPayload.POS_BOTTOM);
    }

    /**
     * 读取 NoellesRolesConfig.accidentalKillPunishment 开关。
     *
     * <p>为何用反射：{@code NoellesRolesConfig} 通过
     * {@code implements me.shedaniel.autoconfig.ConfigData} 绑定配置类，
     * 但 auto-config 注解 API 不在本模组的 compile classpath 中，直接静态访问
     * 会导致编译期报错「无法访问 ConfigData」。因此改为在运行时反射读取字段，
     * 默认返回 true（与 SRE 原配置默认值一致）。
     */
    private static boolean readAccidentalKillPunishmentEnabled() {
        try {
            Class<?> cfgClass = Class.forName(
                    "org.agmas.noellesroles.config.NoellesRolesConfig");
            java.lang.reflect.Field handlerField = cfgClass.getField("HANDLER");
            Object handler = handlerField.get(null);
            java.lang.reflect.Method instanceM = handler.getClass()
                    .getMethod("instance");
            Object cfg = instanceM.invoke(handler);
            return cfgClass.getField("accidentalKillPunishment").getBoolean(cfg);
        } catch (Exception e) {
            return true;
        }
    }
}
