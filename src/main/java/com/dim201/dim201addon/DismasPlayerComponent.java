package com.dim201.dim201addon;

import com.dim201.dim201addon.role.ClownpieceRole;
import com.dim201.dim201addon.role.DismasRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModItems;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 强盗玩家组件：承载「信念考验」「不是今天！」「战斗技巧」的全部运行时状态。
 *
 * <p>服务端权威逻辑；理智/冷却等 HUD 数据仅同步给本人（{@link #shouldSyncWith}），
 * 由客户端 {@code DismasClientHud} 渲染。状态随对局重置：CCA clear() 生命周期
 * + DismasRole 注册的 ResetPlayerEvent 双保险。
 */
public class DismasPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {

    // ==================== 状态机 ====================

    /** 信念考验阶段：空闲 / 折磨中 / 美德生效中 */
    public enum Phase { NONE, TORMENT, VIRTUE }

    /** 折磨效果：疯狂=清空理智 / 偏执=降san光环 / 绝望=失去武器 */
    public enum TormentType {
        MADNESS("疯狂"), PARANOIA("偏执"), DESPAIR("绝望");
        public final String display;
        TormentType(String display) { this.display = display; }
    }

    /** 美德效果：专注=免除下次冷却 / 勇敢=加速 / 坚定=可叠加死门抗性 */
    public enum VirtueType {
        FOCUS("专注"), BRAVERY("勇敢"), STEADFAST("坚定");
        public final String display;
        VirtueType(String display) { this.display = display; }
    }

    // ==================== 数值配置 ====================

    /** 理智判定线（SRE mood 范围 0..1，0.5 = 50 理智） */
    public static final float JUDGE_THRESHOLD = 0.5f;
    /** 折磨结束线：理智恢复到 75% 以上即摆脱折磨（无需回满） */
    public static final float TORMENT_END_THRESHOLD = 0.75f;
    /** 见到每具新尸体降低的理智 */
    public static final float CORPSE_SAN_LOSS = 0.20f;
    /** 任务完成理智恢复的削减比例（0.5 = 恢复减半） */
    public static final float TASK_MOOD_REDUCTION = 0.5f;
    /** 偏执光环：每 tick 压低周围玩家的理智 */
    public static final float PARANOIA_AURA_PER_TICK = 0.0005f;
    /** 偏执光环半径（格） */
    public static final double PARANOIA_RADIUS = 10;
    /** 尸体「见到」判定半径（格） */
    public static final double CORPSE_NOTICE_RADIUS = 8;
    /** 两次判定之间的最小间隔（3 秒，需大于判定演出时长） */
    public static final int JUDGE_COOLDOWN_TICKS = 60;
    /** 判定演出：移动禁止/无敌时长（1 秒，SRE 的 MOVE_BANED/INVINCIBLE 效果） */
    public static final int JUDGE_FREEZE_TICKS = 20;
    /** 判定结算延迟：效果结束后再 roll（比效果多 1 tick，保证「结束后结算」） */
    public static final int PENDING_JUDGE_TICKS = JUDGE_FREEZE_TICKS + 1;
    /** 战斗技巧冷却（30 秒） */
    public static final int SKILL_COOLDOWN_TICKS = 30 * 20;
    /** 被动2 基础死门抗性（触发免疫后消耗） */
    public static final double BASE_DEATH_RESISTANCE = 0.20;
    /** 坚定每层死门抗性（可叠加） */
    public static final double VIRTUE_RESISTANCE_PER_LAYER = 0.50;
    /** 「不是今天！」免疫死亡后恢复的血量 */
    public static final float NOT_TODAY_HEALTH = 6.0f;
    /** 勇敢速度效果时长（tick） */
    public static final int BRAVERY_DURATION_TICKS = 40 * 20;

    // ==================== 运行时状态 ====================

    private final Player player;

    public Phase phase = Phase.NONE;
    public TormentType tormentType = null;
    public VirtueType virtueType = null;
    /** 被动2 的基础 20% 抗性是否可用（触发免疫后消耗） */
    public boolean hasBaseResistance = true;
    /** 坚定层数（每层 50%，美德结束时清零） */
    public int virtueResLayers = 0;
    /** 专注：任意物品进入冷却时立即免除全部冷却 */
    public boolean focusFlag = false;
    /** 曲别针限购标记（每局一次） */
    public boolean lockpickBought = false;
    /** 战斗技巧冷却（tick） */
    public int skillCooldown = 0;
    private int judgeCooldown = 0;
    /** 判定演出倒计时：>0 表示演出中，归零时结算折磨/美德 */
    private int pendingJudgeTicks = 0;
    /** 已见过的尸体实体 UUID（每具只降一次理智） */
    private final Set<UUID> seenCorpses = new HashSet<>();

    public DismasPlayerComponent(Player player) {
        this.player = player;
    }

    public void init() {
        phase = Phase.NONE;
        tormentType = null;
        virtueType = null;
        hasBaseResistance = true;
        virtueResLayers = 0;
        focusFlag = false;
        lockpickBought = false;
        skillCooldown = 0;
        judgeCooldown = 0;
        pendingJudgeTicks = 0;
        seenCorpses.clear();
    }

    public void clear() {
        init();
    }

    /** 仅服务端：把 HUD 相关状态同步给本人客户端。 */
    public void sync() {
        if (!player.level().isClientSide) {
            ModComponents.DISMAS.sync(player);
        }
    }

    /** 只同步给本人：HUD 数据其他玩家无需知晓，节省带宽 */
    @Override
    public boolean shouldSyncWith(ServerPlayer observer) {
        return observer == player;
    }

    // ==================== 死门抗性（被动2 + 坚定） ====================

    /**
     * 当前总死门抗性 = 基础 20%（未消耗时） + 坚定层数 × 50%，上限 100%
     */
    public double getDeathResistance() {
        double res = 0;
        if (hasBaseResistance) {
            res += BASE_DEATH_RESISTANCE;
        }
        res += virtueResLayers * VIRTUE_RESISTANCE_PER_LAYER;
        return Math.min(1.0, res);
    }

    // ==================== 主动技能：战斗技巧 ====================

    /**
     * 战斗技巧冲刺。持刀按技能键 = 向前突进；霰弹枪开火 = 向后滑步（由 DismasRole 监听触发）。
     */
    public boolean tryDash(ServerPlayer sp, boolean forward) {
        if (skillCooldown > 0) {
            sp.displayClientMessage(Component.literal(
                    "§c§l战斗技巧冷却中 §f" + (int) Math.ceil(skillCooldown / 20.0) + "秒"), true);
            return false;
        }
        Vec3 look = sp.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0, look.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            horiz = new Vec3(0, 0, 1);
        }
        horiz = horiz.normalize();
        double power = forward ? 1.8 : 1.4;
        Vec3 dir = forward ? horiz.scale(power) : horiz.scale(-power);
        sp.setDeltaMovement(dir.x, 0.3, dir.z);
        sp.hurtMarked = true;   // 强制把速度变化同步到客户端
        sp.fallDistance = 0;    // 冲刺不产生摔落伤害
        skillCooldown = SKILL_COOLDOWN_TICKS;
        sync();                 // HUD 立即显示冷却
        sp.displayClientMessage(Component.literal(
                forward ? "§6§l[战斗技巧] §f向前突进！" : "§6§l[战斗技巧] §f向后滑步！"), true);
        return true;
    }

    // ==================== 服务端 tick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning()) return;
        if (!gw.isRole(player, DismasRole.DISMAS)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            // 死亡/观战：静默结束美德效果，防止跨死亡残留（速度/抗性/专注）
            endPhaseSilently();
            return;
        }

        if (skillCooldown > 0) skillCooldown--;
        if (judgeCooldown > 0) judgeCooldown--;

        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);

        // 专注：任意物品进入冷却 → 立即免除全部冷却（物品 + 技能），消耗专注
        if (focusFlag && hasAnyItemCooldown(sp)) {
            int cleared = ClownpieceRole.clearAllCooldowns(sp);
            skillCooldown = 0;
            focusFlag = false;
            sp.displayClientMessage(Component.literal(
                    "§b§l[专注] §f冷却被意志压制了！§7（物品 " + cleared + " 件 + 技能）"), true);
        }

        // 状态维护：折磨在理智恢复到结束线以上时结束；美德在理智跌破判定线时失去
        float moodNow = mood.getMood();
        if (phase == Phase.TORMENT && moodNow >= TORMENT_END_THRESHOLD) {
            endTorment(sp);
        } else if (phase == Phase.VIRTUE && moodNow < JUDGE_THRESHOLD) {
            endVirtue(sp);
        }

        // 偏执光环：折磨期间持续压低周围玩家的理智
        if (phase == Phase.TORMENT && tormentType == TormentType.PARANOIA) {
            for (Player other : player.level().players()) {
                if (other == player || !other.isAlive() || other.isSpectator()) continue;
                if (other.distanceTo(player) > PARANOIA_RADIUS) continue;
                SREPlayerMoodComponent.KEY.get(other).addMood(-PARANOIA_AURA_PER_TICK);
            }
        }

        // 见到尸体降理智（每 10 tick 检查一次，每具仅一次）
        if (player.tickCount % 10 == 0) {
            checkCorpses(sp, mood);
        }

        // 信念考验判定：空闲阶段且理智跌破判定线（含 0：SRE 自然流失会把理智停在 0，
        // 排除 0 会导致"低于 50 却永不判定"）。触发时先进入 1s 移动禁止+无敌的演出，
        // 效果结束后才结算折磨/美德
        if (pendingJudgeTicks > 0) {
            pendingJudgeTicks--;
            // 演出特效：附魔粒子环绕（阴影审视的具象化）
            sp.serverLevel().sendParticles(ParticleTypes.ENCHANT,
                    sp.getX(), sp.getY() + 1.0, sp.getZ(), 3, 0.6, 0.6, 0.6, 0.04);
            if (pendingJudgeTicks == 0) {
                resolveJudge(sp, mood);
            }
        } else if (phase == Phase.NONE && judgeCooldown <= 0) {
            if (mood.getMood() < JUDGE_THRESHOLD) {
                triggerJudge(sp);
            }
        }

        // HUD 同步：每秒兜底一次（理智走 SRE 自己的同步，这里主要是技能冷却秒级跳动）
        if (player.tickCount % 20 == 0) {
            sync();
        }
    }

    // ==================== 内部逻辑 ====================

    /** 检查视野内的未见过尸体，首次见到降理智 */
    private void checkCorpses(ServerPlayer sp, SREPlayerMoodComponent mood) {
        AABB box = sp.getBoundingBox().inflate(CORPSE_NOTICE_RADIUS);
        List<PlayerBodyEntity> bodies = sp.serverLevel().getEntitiesOfClass(PlayerBodyEntity.class, box);
        for (PlayerBodyEntity body : bodies) {
            if (sp.getUUID().equals(body.getPlayerUuid())) continue; // 自己的尸体不算
            if (!seenCorpses.add(body.getUUID())) continue;          // 每具仅一次
            if (!sp.hasLineOfSight(body)) {
                seenCorpses.remove(body.getUUID());                  // 被挡住没真正看到，下次再判
                continue;
            }
            mood.addMood(-CORPSE_SAN_LOSS);
            sp.displayClientMessage(Component.literal(
                    "§8你看到了尸体，阴影又沉重了几分…§7（-" + (int) (CORPSE_SAN_LOSS * 100) + " 理智）"), true);
            return; // 每 tick 至多结算一具
        }
    }

    /**
     * 判定演出：施加 1 秒移动禁止 + 无敌（SRE 效果），期间阴影审视，
     * 效果结束后由 {@link #resolveJudge} 结算。
     */
    private void triggerJudge(ServerPlayer sp) {
        judgeCooldown = JUDGE_COOLDOWN_TICKS;
        pendingJudgeTicks = PENDING_JUDGE_TICKS;
        sp.addEffect(ModEffects.of(ModEffects.MOVE_BANED, JUDGE_FREEZE_TICKS, 0, true, false, false));
        sp.addEffect(ModEffects.of(ModEffects.INVINCIBLE, JUDGE_FREEZE_TICKS, 0, true, false, false));
        sp.displayClientMessage(Component.literal("§5阴影正在审视你的信念…"), true);
    }

    /**
     * 信念考验结算：2/3 折磨 / 1/3 美德（结果走 actionbar）
     */
    private void resolveJudge(ServerPlayer sp, SREPlayerMoodComponent mood) {
        RandomSource rand = sp.getRandom();
        if (rand.nextFloat() < 2.0f / 3.0f) {
            // ---- 折磨 ----
            TormentType[] types = TormentType.values();
            tormentType = types[rand.nextInt(types.length)];
            phase = Phase.TORMENT;
            switch (tormentType) {
                case MADNESS -> mood.setMood(0f);   // 疯狂：直接清空理智
                case PARANOIA -> {                   // 偏执：光环由 tick 驱动
                }
                case DESPAIR -> destroyWeapons(sp);  // 绝望：武器彻底销毁
            }
            sp.displayClientMessage(Component.literal(
                    "§4§l[折磨·" + tormentType.display + "] §c阴影开始侵蚀你的理智…"), true);
        } else {
            // ---- 美德 ----
            VirtueType[] types = VirtueType.values();
            virtueType = types[rand.nextInt(types.length)];
            phase = Phase.VIRTUE;
            switch (virtueType) {
                case FOCUS -> focusFlag = true;      // 专注：下次冷却免除
                case BRAVERY -> sp.addEffect(new MobEffectInstance(   // 勇敢：加速
                        MobEffects.MOVEMENT_SPEED, BRAVERY_DURATION_TICKS, 1, true, false, false));
                case STEADFAST -> virtueResLayers++; // 坚定：+50% 可叠加死门抗性
            }
            mood.setMood(1f); // 美德触发恢复满理智
            sp.displayClientMessage(Component.literal(
                    "§a§l[美德·" + virtueType.display + "] §f你守住了一线清明，理智恢复满值"), true);
        }
        sync(); // phase 变化尽快反映到 HUD
    }

    private void endTorment(ServerPlayer sp) {
        phase = Phase.NONE;
        tormentType = null;
        sp.displayClientMessage(Component.literal("§7理智逐渐恢复，折磨的幻影消散了"), true);
    }

    private void endVirtue(ServerPlayer sp) {
        if (virtueType == VirtueType.BRAVERY) {
            sp.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
        virtueResLayers = 0;
        focusFlag = false;
        phase = Phase.NONE;
        virtueType = null;
        sp.displayClientMessage(Component.literal("§7理智低落，美德的效果消逝了"), true);
    }

    /** 死亡/观战时静默清理激活中的效果 */
    private void endPhaseSilently() {
        if (phase == Phase.VIRTUE && virtueType == VirtueType.BRAVERY
                && player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
        phase = Phase.NONE;
        tormentType = null;
        virtueType = null;
    }

    /** 绝望：武器直接销毁（不产生掉落物，防止捡回导致"丢失的武器回归"） */
    private void destroyWeapons(ServerPlayer sp) {
        var inv = sp.getInventory();
        boolean removed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            // 短霰弹枪不在 TMMItemTags.GUNS 标签内（SRE 的 guns.json 只收左轮类），
            // 必须显式判断，否则绝望清不掉霰弹枪
            if (!s.isEmpty() && (s.is(TMMItems.KNIFE) || s.is(TMMItemTags.GUNS)
                    || s.is(ModItems.SHORT_SHOTGUN))) {
                inv.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        if (removed) {
            sp.displayClientMessage(Component.literal("§4你的武器在阴影中彻底消散了…"), false);
        }
    }

    private boolean hasAnyItemCooldown(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && sp.getCooldowns().isOnCooldown(s.getItem())) {
                return true;
            }
        }
        return false;
    }

    // ==================== NBT 持久化（Component 抽象方法） ====================

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        phase = Phase.values()[tag.getInt("phase")];
        int torment = tag.getInt("torment");
        tormentType = torment >= 0 ? TormentType.values()[torment] : null;
        int virtue = tag.getInt("virtue");
        virtueType = virtue >= 0 ? VirtueType.values()[virtue] : null;
        hasBaseResistance = tag.getBoolean("has_base_res");
        virtueResLayers = tag.getInt("virtue_res_layers");
        focusFlag = tag.getBoolean("focus_flag");
        lockpickBought = tag.getBoolean("lockpick_bought");
        skillCooldown = tag.getInt("skill_cooldown");
        judgeCooldown = tag.getInt("judge_cooldown");
        pendingJudgeTicks = tag.getInt("pending_judge");
        // seenCorpses 不持久化不同步：纯服务端逻辑态，重连后重扣一次理智属可接受的自愈行为
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("phase", phase.ordinal());
        tag.putInt("torment", tormentType != null ? tormentType.ordinal() : -1);
        tag.putInt("virtue", virtueType != null ? virtueType.ordinal() : -1);
        tag.putBoolean("has_base_res", hasBaseResistance);
        tag.putInt("virtue_res_layers", virtueResLayers);
        tag.putBoolean("focus_flag", focusFlag);
        tag.putBoolean("lockpick_bought", lockpickBought);
        tag.putInt("skill_cooldown", skillCooldown);
        tag.putInt("judge_cooldown", judgeCooldown);
        tag.putInt("pending_judge", pendingJudgeTicks);
        // seenCorpses 同上，不写入 NBT（见 readFromNbt 注释）
    }
}
