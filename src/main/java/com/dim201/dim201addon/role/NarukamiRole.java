package com.dim201.dim201addon.role;

import com.dim201.dim201addon.AdachiPlayerComponent;
import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.NarukamiPlayerComponent;
import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardTeamKillPunisher;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnShieldBroken;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NarukamiRole {

    public static final ResourceLocation NARUKAMI_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "narukami");
    public static final ResourceLocation NARUKAMI_KATANA_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "narukami_katana");
    public static final ResourceLocation NARUKAMI_SLASH_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "narukami_slash");
    public static final ResourceLocation NARUKAMI_MISKILL_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "narukami_miskill");

    public static SRERole NARUKAMI;

    private static final int KATANA_COOLDOWN = 15 * 20;        // 打刀冷却15秒
    private static final int SKILL1_COOLDOWN = 60 * 20;        // 吉欧达因冷却60秒
    private static final int SKILL2_COOLDOWN = 45 * 20;        // 紫电一闪冷却45秒
    private static final int SKILL3_COOLDOWN = NarukamiPlayerComponent.CROSS_SLASH_COOLDOWN_TICKS; // 十文字斩冷却120秒
    private static final int SKILL1_ROOT_DURATION = 2 * 20;    // 吉欧达因定身2秒
    private static final double SKILL1_RANGE = 10.0;           // 吉欧达因锁定范围10格
    private static final double SKILL2_DASH_DISTANCE = 5.0;    // 紫电一闪冲刺5格
    private static final double SKILL3_RANGE = 3.5;            // 十文字斩范围
    private static final int MIN_PLAYERS = 12;                 // 12人以上对局才刷新

    private static boolean eventsRegistered = false;

    public static void register() {
        NARUKAMI = TMMRoles.registerRole(new NarukamiRoleImpl());
        registerEvents();
    }

    private static class NarukamiRoleImpl extends NormalRole {

        public NarukamiRoleImpl() {
            // isInnocent=true（警长阵营，算好人方）, canUseKiller=false
            // MoodType.REAL：真实心情（FAKE会被HUD当成杀手渲染红色旗帜）
            super(NARUKAMI_ID, 0xFFFFD700, true, false, MoodType.REAL, 600, true);
            this.setCanSeeTime(true)
                .setDefaultMax(1)
                .setDefaultEnableChance(0)
                .setDefaultEnableNeededPlayerCount(12)   // 12人以上对局才出现
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
            // 警长阵营（好人方胜利条件）。若此方法名编译报错，查看 SRERole 中 vigilante 相关 setter
            this.setVigilanteTeam(true);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            items.add(new ItemStack(Dim201ModItems.UCHIGATANA));
            return items;
        }

        @Override
        public java.util.function.Predicate<net.minecraft.world.item.Item> cantPickupItem(net.minecraft.world.entity.player.Player player) {
            return item -> item == io.wifi.starrailexpress.index.TMMItems.REVOLVER;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (!isAliveAndPlaying(player)) return true;
            NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
            if (comp == null) return true;

            switch (comp.currentSkill) {
                case ZIODYNE -> {
                    if (!comp.isSkillUnlocked(NarukamiPlayerComponent.Skill.ZIODYNE)) {
                        int required = comp.getRequiredPointsForSkill(NarukamiPlayerComponent.Skill.ZIODYNE);
                        comp.sendActionBar(player, "§c§l吉欧达因未解锁！§7需要" + required + "点搜查点数");
                        return true;
                    }
                    if (comp.skill1Cooldown > 0) {
                        comp.sendActionBar(player, "§c§l吉欧达因冷却中 §f" + (int) Math.ceil(comp.skill1Cooldown / 20.0) + "秒");
                        return true;
                    }
                    Player target = findTargetInSight(player, SKILL1_RANGE);
                    if (target == null) {
                        comp.sendActionBar(player, "§c§l没有目标！");   // 没有目标不进CD
                        return true;
                    }
                    comp.skill1Cooldown = comp.awakened ? 30 * 20 : SKILL1_COOLDOWN;
                    useZiodyne(player, target);
                    comp.sync();
                    return true;
                }
                case DASH -> {
                    if (!comp.isSkillUnlocked(NarukamiPlayerComponent.Skill.DASH)) {
                        int required = comp.getRequiredPointsForSkill(NarukamiPlayerComponent.Skill.DASH);
                        comp.sendActionBar(player, "§c§l紫电一闪未解锁！§7需要" + required + "点搜查点数");
                        return true;
                    }
                    if (comp.skill2Cooldown > 0) {
                        comp.sendActionBar(player, "§c§l紫电一闪冷却中 §f" + (int) Math.ceil(comp.skill2Cooldown / 20.0) + "秒");
                        return true;
                    }
                    comp.skill2Cooldown = comp.awakened ? 30 * 20 : SKILL2_COOLDOWN;
                    useDash(player);
                    comp.sync();
                    return true;
                }
                case CROSS_SLASH -> {
                    if (!comp.isSkillUnlocked(NarukamiPlayerComponent.Skill.CROSS_SLASH)) {
                        int required = comp.getRequiredPointsForSkill(NarukamiPlayerComponent.Skill.CROSS_SLASH);
                        comp.sendActionBar(player, "§c§l十文字斩未解锁！§7需要" + required + "点搜查点数");
                        return true;
                    }
                    if (comp.skill3Cooldown > 0) {
                        comp.sendActionBar(player, "§c§l十文字斩冷却中 §f" + (int) Math.ceil(comp.skill3Cooldown / 20.0) + "秒");
                        return true;
                    }
                    comp.skill3Cooldown = comp.awakened ? 30 * 20 : SKILL3_COOLDOWN;
                    // 进入冷却：回收就绪护盾（冷却期间无护盾，结束后由组件 tick 重新授予）
                    comp.recycleCrossSlashShield(player);
                    useCrossSlash(player);
                    comp.sync();
                    return true;
                }
            }
            return true;
        }

        @Override
        public void serverTick(ServerPlayer player) {}

        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, NARUKAMI)) return;
            if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
            NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
            if (comp == null) return;
            comp.tick(player);

            ServerLevel level = player.serverLevel();

            // 设置固定总玩家数（仅一次）
            if (comp.fixedTotalPlayers == -1) {
                int totalPlayers = Math.max(1, GameUtils.getParticipatingPlayerCount(level));
                comp.setFixedTotalPlayers(totalPlayers);
            }

            // 定期更新技能解锁状态
            if (player.tickCount % 20 == 0) {
                comp.updateSkillUnlocks();
            }

            // ========== 觉醒判定：足立透已复活 且 搜查点数 >= 总玩家数/2 ==========
            if (!comp.awakened && player.tickCount % 20 == 0) {
                boolean adachiRevived = false;
                for (ServerPlayer p : level.players()) {
                    if (gameWorld.isRole(p, AdachiRole.ADACHI)) {
                        AdachiPlayerComponent ac = ModComponents.ADACHI.get(p);
                        if (ac != null && ac.hasRevived) adachiRevived = true;
                    }
                }
                int totalPlayers = (comp.fixedTotalPlayers != -1) ? comp.fixedTotalPlayers : Math.max(1, GameUtils.getParticipatingPlayerCount(level));
                int awakenThreshold = Math.max(1, totalPlayers / 2); // 搜查点数 >= 总玩家数/2
                if (adachiRevived && comp.investigationPoints >= awakenThreshold) {
                    comp.awakened = true;
                    comp.updateSkillUnlocks(); // 觉醒时也更新一下
                    awaken(player, level);
                    comp.sync();
                }
            }

            // ========== 觉醒后：足立透持续发光 ==========
            if (comp.awakened && player.tickCount % 40 == 0) {
                for (ServerPlayer p : level.players()) {
                    if (gameWorld.isRole(p, AdachiRole.ADACHI) && GameUtils.isPlayerAliveAndSurvival(p)) {
                        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, false, false, true));
                    }
                }
            }
        }
    }

    public static void onSwitchSkill(ServerPlayer player) {
        NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
        if (comp == null) return;
        comp.switchSkill(player);
    }

    public static boolean isNarukami(Player player) {
        if (player == null || player.level().isClientSide) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(NARUKAMI);
    }

    private static boolean isAliveAndPlaying(ServerPlayer player) {
        if (player == null || !player.isAlive()) return false;
        return player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR;
    }

    // ==================== 技能1：吉欧达因 ====================

    private static void useZiodyne(ServerPlayer player, Player target) {
        ServerLevel level = player.serverLevel();
        // 召唤雷电（仅视觉效果，不引燃地图、不造成雷电伤害）
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        // 定身2秒
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SKILL1_ROOT_DURATION, 127, false, false, true));
        // 自定义音效：觉醒前 skill1_normal 池（skill1_1~4随机），觉醒后 skill1_awakened 池（skill1_5~6随机）
        NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
        playNarukamiSound(level, target.getX(), target.getY(), target.getZ(),
                comp != null && comp.awakened ? "narukami.skill1_awakened" : "narukami.skill1_normal");
        comp.sendActionBar(player, "§9§l吉欧达因！§f" + target.getName().getString() + " 被雷电定身");
    }

    // ==================== 技能2：紫电一闪 ====================

    private static void useDash(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        if (look == null) return;
        // 投影到水平面后归一化：保证冲刺距离恒为 5 格，不受俯仰角影响
        // （look.scale(5) 含 Y 分量时水平距离 = 5×cos(pitch)，俯视会缩短到 3 格）
        Vec3 horizontalLook = new Vec3(look.x, 0, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6) return;  // 纯垂直视角不触发
        horizontalLook = horizontalLook.normalize();

        final Vec3 start = player.position();
        final Vec3 target = start.add(horizontalLook.scale(SKILL2_DASH_DISTANCE));
        // 1) setDeltaMovement 给真实物理推进速度；hurtMarked=true 让 MC 真正接受位移
        Vec3 dash = horizontalLook.scale(SKILL2_DASH_DISTANCE * 0.28);
        player.setDeltaMovement(player.getDeltaMovement().add(dash.x, 0.08, dash.z));
        player.hurtMarked = true;
        // 2) lerpTo 让客户端在 4 tick 内平滑插值到目标位置（y 锁定避免俯冲穿地）
        player.lerpTo(target.x, player.getY(), target.z, player.getYRot(), player.getXRot(), 4);

        // 冲刺路径电光粒子
        for (double d = 0; d <= SKILL2_DASH_DISTANCE; d += 0.4) {
            Vec3 p = start.add(horizontalLook.scale(d));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.0, p.z, 3, 0.08, 0.08, 0.08, 0.01);
        }

        // 自定义音效：skill2 池（在冲刺起点播放）
        playNarukamiSound(level, player.getX(), player.getY(), player.getZ(), "narukami.skill2");

        player.fallDistance = 0;

        NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
        comp.sendActionBar(player, "§9§l紫电一闪！");
    }

    // ==================== 技能3：十文字斩 ====================

    private static void useCrossSlash(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 center = player.position().add(look.scale(2.0)).add(0, 1.2, 0);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();

        // 横斩粒子（白色，水平一横）—— 高密度 + 扫击粒子
        for (double t = -2.5; t <= 2.5; t += 0.08) {
            Vec3 p = center.add(right.scale(t));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        // 竖斩粒子（白色，垂直一竖）—— 高密度 + 扫击粒子
        for (double t = -1.2; t <= 1.8; t += 0.08) {
            Vec3 p = center.add(0, t, 0);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        // 十字中心爆发：大量白色粒子喷涌
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 80, 1.0, 1.0, 1.0, 0.15);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 50, 0.8, 0.8, 0.8, 0.3);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 12, 0.6, 0.6, 0.6, 0.05);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0, 0, 0, 0);

        // 自定义音效：skill3 池（skill3_1~3随机）
        playNarukamiSound(level, player.getX(), player.getY(), player.getZ(), "narukami.skill3");

        // 斩杀前方扇形范围内玩家
        int killCount = 0;
        for (ServerPlayer target : level.getPlayers(p -> GameUtils.isPlayerAliveAndSurvival(p))) {
            if (target == player) continue;
            Vec3 to = target.position().add(0, 1.0, 0).subtract(player.getEyePosition(1.0f));
            double dist = to.length();
            if (dist > SKILL3_RANGE) continue;
            if (to.normalize().dot(look) < 0.5) continue;   // 前方约60°锥形

            // 先造成一次伤害，确保触发护盾等效果
            target.hurt(player.damageSources().playerAttack(player), 1.0F);

            // 通用小脑惩罚：好人误杀好人时统一处理（含豁免条件 + 警告字幕 + shot_innocent）
            CardTeamKillPunisher.applyIfApplicable(target, player, NARUKAMI_SLASH_DEATH);
            GameUtils.killPlayer(target, true, player, NARUKAMI_SLASH_DEATH);
            killCount++;
        }

        NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
        if (killCount > 0) comp.sendActionBar(player, "§9§l十文字斩！§f斩杀 " + killCount + " 人");
        else comp.sendActionBar(player, "§7十文字斩挥空了");
    }

    // ==================== 觉醒：伊耶那岐大神 ====================

    private static void awaken(ServerPlayer player, ServerLevel level) {
        // 特殊音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "narukami.awaken")),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        // 天气变晴天
        level.setWeatherParameters(120000, 0, false, false);
        // 时间变正午
        level.setDayTime(6000);

        NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
        // 觉醒时刷新所有技能CD
        comp.skill1Cooldown = 0;
        comp.skill2Cooldown = 0;
        comp.skill3Cooldown = 0;
        comp.sync();
        comp.sendActionBar(player, "§e§l人格面具觉醒：伊耶那岐大神！");
    }

    /** 播放鸣上悠技能音效事件（事件内多个音频由MC自动随机） */
    private static void playNarukamiSound(ServerLevel level, double x, double y, double z, String eventPath) {
        level.playSound(null, x, y, z,
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, eventPath)),
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // ==================== 目标搜索 ====================

    private static Player findTargetInSight(ServerPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(range);
        Player best = null;
        double bestDot = 0.5;
        for (Player candidate : player.level().getEntitiesOfClass(Player.class, box)) {
            if (candidate == player || !candidate.isAlive()) continue;
            if (candidate instanceof ServerPlayer sp && !GameUtils.isPlayerAliveAndSurvival(sp)) continue;
            if (candidate.distanceTo(player) > range) continue;
            Vec3 toTarget = candidate.position().add(0, candidate.getEyeHeight() * 0.5, 0).subtract(eyePos).normalize();
            double dot = toTarget.dot(lookVec);
            if (dot > bestDot) { bestDot = dot; best = candidate; }
        }
        return best;
    }

    // ==================== 事件注册 ====================
    // 绑定生成由 Harpymodloader.addOccupationRole(ADACHI, NARUKAMI) 处理（见 Dim201Addon）

    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        // ---------- 允许打刀攻击玩家（关键！） ----------
        AllowPlayerPunching.EVENT.register((player) -> {
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null) return false;
            
            var role = gameWorld.getRole(player);
            if (role == null || !role.equals(NARUKAMI)) return false;
            
            // 检查是否持有打刀
            ItemStack mainHand = player.getMainHandItem();
            return mainHand.is(Dim201ModItems.UCHIGATANA);
        });

        // ---------- 打刀左键攻击：直接击杀 / 误杀平民自身死亡 / 30秒冷却 ----------
        AttackEntityCallback.EVENT.register((attacker, world, hand, entity, hitResult) -> {
            if (world.isClientSide) {
                // 客户端只处理显示
                return InteractionResult.SUCCESS;
            }
            
            if (!(attacker instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ItemStack held = sp.getItemInHand(hand);
            if (!held.is(Dim201ModItems.UCHIGATANA)) return InteractionResult.PASS;

            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld == null || !gameWorld.isRunning()) return InteractionResult.PASS;
            if (!gameWorld.isRole(sp, NARUKAMI)) return InteractionResult.PASS;
            if (!(entity instanceof ServerPlayer target)) return InteractionResult.PASS;
            if (!GameUtils.isPlayerAliveAndSurvival(target) || target.isSpectator()) return InteractionResult.PASS;

            // 冷却中取消攻击
            if (sp.getCooldowns().isOnCooldown(Dim201ModItems.UCHIGATANA)) {
                return InteractionResult.FAIL;
            }

            NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(sp);
            if (comp == null) return InteractionResult.PASS;

            // 更新当前目标，更换目标时清空计数
            comp.updateKatanaTarget(target.getUUID());
            // 攻击5次才可以击杀：累计对该目标的命中数
            int hits = comp.katanaHits.getOrDefault(target.getUUID(), 0) + 1;
            if (hits < 5) {
                comp.katanaHits.put(target.getUUID(), hits);
                comp.sync();
                comp.sendActionBar(sp, "§e命中 " + hits + "/5 §7- " + target.getName().getString());
                
                // 造成1点伤害（显示攻击效果和击退）
                target.hurt(sp.damageSources().playerAttack(sp), 1.0F);
                
                return InteractionResult.SUCCESS_NO_ITEM_USED;
            }

            // 第5次命中 → 击杀，打刀进入30秒冷却
            comp.katanaHits.remove(target.getUUID());
            sp.getCooldowns().addCooldown(Dim201ModItems.UCHIGATANA, KATANA_COOLDOWN);

            // 造成伤害和击退，然后击杀
            target.hurt(sp.damageSources().playerAttack(sp), 1.0F);
            // 通用小脑惩罚：好人误杀好人时统一处理（含豁免条件 + 警告字幕 + shot_innocent）
            CardTeamKillPunisher.applyIfApplicable(target, sp, NARUKAMI_KATANA_DEATH);
            GameUtils.killPlayer(target, true, sp, NARUKAMI_KATANA_DEATH);
            comp.sync();
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        });

        // ---------- 与尸体交互：积累搜查点数（每具尸体1点） ----------
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            // 按类名识别SRE尸体实体，避免硬依赖内部包路径
            if (!entity.getClass().getSimpleName().equals("PlayerBodyEntity")) return InteractionResult.PASS;

            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld == null || !gameWorld.isRunning()) return InteractionResult.PASS;
            if (!gameWorld.isRole(sp, NARUKAMI)) return InteractionResult.PASS;

            NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(sp);
            if (comp == null) return InteractionResult.PASS;
            if (comp.searchedBodies.contains(entity.getUUID())) return InteractionResult.PASS;   // 搜过这具了

            comp.searchedBodies.add(entity.getUUID());
            comp.investigationPoints++;
            comp.updateSkillUnlocks(); // 更新解锁状态

            // 解锁提示
            String unlockMsg = null;
            int totalPlayers = Math.max(1, GameUtils.getParticipatingPlayerCount(sp.serverLevel()));
            int crossSlashRequired = comp.getRequiredPointsForSkill(NarukamiPlayerComponent.Skill.CROSS_SLASH);
            if (comp.investigationPoints == 1) unlockMsg = " §6§l吉欧达因已解锁！";
            else if (comp.investigationPoints == 3) unlockMsg = " §6§l紫电一闪已解锁！";
            else if (comp.investigationPoints == crossSlashRequired) unlockMsg = " §6§l十文字斩已解锁！";
            comp.sendActionBar(sp, "§b§l搜查点数 +1 §7| §f当前: §e" + comp.investigationPoints + (unlockMsg != null ? unlockMsg : ""));

            return InteractionResult.PASS;
        });

        // ---------- 鸣上悠死亡：掉落左轮手枪 ----------
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;

            var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null) return;
            if (!gameWorld.isRole(sp, NARUKAMI)) return;

            // 掉落左轮手枪
            sp.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
        });

        // ---------- 十文字斩就绪护盾被击破：十文字斩进入冷却 ----------
        // SRE 在 GameMode.killPlayer 的护盾拦截分支中消耗 1 层护甲并触发本事件
        OnShieldBroken.EVENT.register((victim, killer) -> {
            if (!(victim instanceof ServerPlayer sp)) return;
            var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null || !gameWorld.isRole(sp, NARUKAMI)) return;
            NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(sp);
            if (comp != null) comp.onCrossSlashShieldBroken(sp);
        });
    }

    public static void resetRoleState() {}
}
