package com.dim201.dim201addon.contract;

import com.dim201.dim201addon.DemonContractorComponent;
import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 契约力量与代价的效果执行器（SRP：只管效果施加与维持，不管掷骰/UI 流程）。
 *
 * <p>永久属性类效果（暗影步/滞重）使用固定 id 的 {@link AttributeModifier}，
 * 重复施加幂等；{@link #removePermanent} 在对局结束时移除。
 */
public final class ContractEffects {

    private ContractEffects() {}

    /** 贪婪税倍率：1/2/3/4 件力量 */
    public static final float[] GREED_MULTIPLIERS = {1.0F, 1.6F, 2.5F, 4.0F};
    /** 总代价达到该值直接死于契约 */
    public static final int DEATH_THRESHOLD = 10;

    private static final ResourceLocation SHADOW_STEP_ID =
            ResourceLocation.fromNamespaceAndPath("dim201addon", "contract_shadow_step");
    private static final ResourceLocation SLOW_FEET_ID =
            ResourceLocation.fromNamespaceAndPath("dim201addon", "contract_slow_feet");

    private static final int EFFECT_REFRESH_TICKS = 5 * 20;
    private static final long INTEREST_INTERVAL = 60 * 20L;
    private static final long INTEREST_AMOUNT = 50L;
    private static final long EXPOSURE_INTERVAL = 60 * 20L;
    private static final long EXPOSURE_GLOW_TICKS = 5 * 20L;
    private static final long FEVER_INTERVAL = 60 * 20L;
    private static final long FEVER_TICKS = 8 * 20L;
    private static final long DEMON_GAZE_TICKS = 10 * 20L;
    private static final float MOOD_CAP_FRAIL = 0.8F;

    /** 签订成功时：力量中需要立即落地的非标记效果（护甲/属性） */
    public static void applyPowerInstant(ServerPlayer sp, ContractPower power) {
        switch (power) {
            case SHADOW_STEP -> addSpeedModifier(sp, SHADOW_STEP_ID, 0.15D);
            case IRON_SKIN -> {
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(sp);
                if (armor != null && armor.getArmor() <= 0) armor.addArmor(1);
            }
            default -> {
                // 夜视/迷雾/清心/标记类力量由 maintainTick 与客户端事件处理
            }
        }
    }

    /** 签订成功时：永久烙印的即时落地（属性 + 计时锚点） */
    public static void applyBrandInstant(ServerPlayer sp, DemonContractorComponent comp,
                                        ContractCost brand, long now) {
        switch (brand) {
            case SLOW_FEET -> addSpeedModifier(sp, SLOW_FEET_ID, -0.10D);
            case INTEREST -> comp.setLastInterestAt(now);
            case EXPOSURE -> comp.setLastExposureAt(now);
            case DEMON_FEVER -> comp.setLastFeverAt(now);
            default -> {
                // 失明/san 钳制/发光类由 maintainTick 处理
            }
        }
    }

    /** 即时代价结算（签订瞬间一次性） */
    public static void applyInstantCost(ServerPlayer sp, ContractCost cost, long now) {
        DemonContractorComponent comp = DemonContractorComponent.KEY.get(sp);
        switch (cost) {
            case GOLD_50 -> CardUtils.spendGold(sp, 50);
            case GOLD_100 -> CardUtils.spendGold(sp, 100);
            case GOLD_150 -> CardUtils.spendGold(sp, 150);
            case MIND_CRASH -> {
                SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(sp);
                if (mood != null) mood.addMood(-mood.getMood());
            }
            case DEMON_GAZE -> {
                sp.setGlowingTag(true);
                comp.setGlowUntil(now + DEMON_GAZE_TICKS);
            }
            case BLIND_SHORT -> sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 30 * 20, 0, false, false, false));
            case EXHAUSTION -> {
                sp.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 30 * 20, 0, false, false, false));
                sp.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SLOWDOWN, 30 * 20, 0, false, false, false));
            }
            default -> {}
        }
    }

    /**
     * 每 tick 维持契约效果（由角色 tick 调用）。
     * 顺序：定时效果 → 发光到期 → 清心补回 san → 脆弱心智钳制上限。
     */
    public static void maintainTick(ServerPlayer sp, DemonContractorComponent comp, long now) {
        // 夜视 / 迷雾（永久失明）
        if (sp.tickCount % EFFECT_REFRESH_TICKS == 0) {
            if (comp.hasPower(ContractPower.NIGHT_VISION)) {
                sp.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION,
                        0, false, false, false));
            }
            if (comp.hasBrand(ContractCost.FOG_CURSE)) {
                sp.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, MobEffectInstance.INFINITE_DURATION,
                        0, false, false, false));
            }
        }

        // 临时发光到期（恶魔注视 / 周期暴露）
        if (comp.getGlowUntil() > 0 && now >= comp.getGlowUntil()) {
            sp.setGlowingTag(false);
            comp.setGlowUntil(-1L);
        }

        // 周期暴露：每 60 秒发光 5 秒
        if (comp.hasBrand(ContractCost.EXPOSURE) && comp.getLastExposureAt() > 0
                && now - comp.getLastExposureAt() >= EXPOSURE_INTERVAL) {
            sp.setGlowingTag(true);
            comp.setGlowUntil(now + EXPOSURE_GLOW_TICKS);
            comp.setLastExposureAt(now);
        }

        // 恶魔低烧：每 60 秒发作反胃 + 虚弱 8 秒
        if (comp.hasBrand(ContractCost.DEMON_FEVER) && comp.getLastFeverAt() > 0
                && now - comp.getLastFeverAt() >= FEVER_INTERVAL) {
            sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, (int) FEVER_TICKS, 0, false, false, true));
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) FEVER_TICKS, 0, false, false, true));
            comp.setLastFeverAt(now);
        }

        // 恶魔利息：每 60 秒 -50 金币
        if (comp.hasBrand(ContractCost.INTEREST) && comp.getLastInterestAt() > 0
                && now - comp.getLastInterestAt() >= INTEREST_INTERVAL) {
            CardUtils.spendGold(sp, (int) INTEREST_AMOUNT);
            comp.setLastInterestAt(now);
            CardUtils.notify(sp, "§8§o恶魔收取了它的利息（§650 金币§8§o）");
        }

        // 清心：免疫 san 降低——本 tick 检测到下降则补回
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(sp);
        if (mood != null) {
            float current = mood.getMood();
            if (comp.hasPower(ContractPower.CLARITY) && current < comp.getLastMood() - 0.0001F) {
                mood.addMood(comp.getLastMood() - current);
                current = comp.getLastMood();
            }
            // 脆弱心智：san 上限 -20%
            if (comp.hasBrand(ContractCost.FRAIL_MIND) && current > MOOD_CAP_FRAIL) {
                mood.addMood(MOOD_CAP_FRAIL - current);
                current = MOOD_CAP_FRAIL;
            }
            comp.setLastMood(current);
        }
    }

    /** 对局结束移除全部属性修饰符、发光与契约药水效果（玩家实体跨局复用，不能残留） */
    public static void removePermanent(ServerPlayer sp) {
        removeSpeedModifier(sp, SHADOW_STEP_ID);
        removeSpeedModifier(sp, SLOW_FEET_ID);
        sp.setGlowingTag(false);
        sp.removeEffect(MobEffects.NIGHT_VISION);
        sp.removeEffect(MobEffects.BLINDNESS);
    }

    private static void addSpeedModifier(ServerPlayer sp, ResourceLocation id, double amount) {
        AttributeInstance inst = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        if (inst.getModifier(id) == null) {
            inst.addPermanentModifier(new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void removeSpeedModifier(ServerPlayer sp, ResourceLocation id) {
        AttributeInstance inst = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) inst.removeModifier(id);
    }
}
