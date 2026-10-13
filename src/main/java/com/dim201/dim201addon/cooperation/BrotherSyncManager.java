package com.dim201.dim201addon.cooperation;

import com.dim201.dim201addon.packet.BrotherSyncS2CPacket;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 东堂葵 × 虎杖悠仁「兄弟默契」共享状态管理（服务端单例）。
 *
 * 职责：
 * 1. 默契能量（共享，上限 200，每 15 秒最多通过击掌获取一次）
 * 2. 击掌奖励：Tier1=25 点；Tier2=50 点 + 双方 20 秒 +10% 移速；Tier3=75 点 + 双方 20 秒无限体力；
 *    每次成功击掌交换双方物品栏，感知距离 10 → 30 格
 * 3. 不义游戏：东堂花费 50 金币开启 3 秒窗口，虎杖响应后互换位置 + 双方 2 秒无敌 + 虎杖 10 秒专注
 *
 * 不持有实体引用，全部用 UUID 索引，玩家重登/换局可安全清理。
 */
public final class BrotherSyncManager {

    private BrotherSyncManager() {}

    public static final int MAX_POINTS = 200;
    /** 默契能量获取冷却（tick）= 15 秒 */
    public static final long GAIN_COOLDOWN_TICKS = 15 * 20L;
    /** 不义游戏响应窗口（tick）= 3 秒 */
    public static final long WINDOW_TICKS = 3 * 20L;
    /** 不义游戏开启金币消耗 */
    public static final int COIN_COST = 50;
    /** 响应基础能量消耗 */
    public static final int BASE_COST = 25;
    /** 1 秒内响应无额外消耗，每多 1 秒追加消耗 */
    public static final int EXTRA_COST_PER_SECOND = 25;
    /** 互换后无敌时长（tick）= 2 秒 */
    public static final int INVINCIBLE_TICKS = 2 * 20;
    /** 虎杖专注时长（tick）= 10 秒 */
    public static final long FOCUS_TICKS = 10 * 20L;
    /** Tier2 移速加成时长（tick）= 20 秒 */
    public static final int SPEED_TICKS = 20 * 20;
    /** Tier3 无限体力时长（tick）= 20 秒 */
    public static final int INFINITE_STAMINA_TICKS = 20 * 20;

    /** Tier2 的 +10% 移速修饰符 */
    private static final ResourceLocation SPEED_MODIFIER_ID =
            Dim201Addon.id("brotherhood_speed");

    /** 一对东堂×虎杖的共享状态 */
    private static final class PairState {
        UUID todoId;
        UUID itadoriId;
        int points;
        long lastGainTick = Long.MIN_VALUE / 2;
        boolean extended;
        long windowUntilTick;
        long speedUntilTick;
        long focusUntilTick;
    }

    /** 两个 UUID 都指向同一个 PairState */
    private static final Map<UUID, PairState> INDEX = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BrotherSyncManager::serverTick);
        // 被动「兄弟情义」：虎杖 3 格内时代替东堂承受伤害
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                BrotherSyncManager::onAllowDamage);
    }

    /** 虎杖受伤时，若东堂在 3 格内则由东堂代替承受伤害（返回 false 取消虎杖受伤） */
    private static boolean onAllowDamage(
            net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer itadori)) return true;
        if (!ItadoriRole.isItadori(itadori)) return true;
        if (!GameUtils.isPlayerAliveAndSurvival(itadori)) return true;

        ServerPlayer todo = findCounterpart(itadori.server, itadori.getUUID(), true);
        if (todo == null || !GameUtils.isPlayerAliveAndSurvival(todo)) {
            return true;
        }
        if (itadori.distanceToSqr(todo) > TodoAoiRole.SHIELD_RANGE * TodoAoiRole.SHIELD_RANGE) {
            return true;
        }
        // 东堂代替承受伤害；东堂受伤不会递归触发转移（只对虎杖生效）
        todo.hurt(source, amount);
        return false;
    }

    // ==================== 对外查询 ====================

    /** 虎杖当前是否处于专注状态（逕庭拳连击需求降为 2） */
    public static boolean isFocusActive(UUID itadoriId, long gameTime) {
        PairState st = INDEX.get(itadoriId);
        return st != null && gameTime < st.focusUntilTick;
    }

    // ==================== 击掌入口（coopmod Mixin 调用） ====================

    /**
     * coopmod 击掌成功回调。仅当双方恰好是东堂 × 虎杖时处理。
     * @param tier coopmod 击掌档位 0~3
     */
    public static void onHighFive(ServerPlayer p1, ServerPlayer p2, int tier) {
        boolean p1Todo = TodoAoiRole.isTodo(p1);
        boolean p2Todo = TodoAoiRole.isTodo(p2);
        boolean p1Ita = ItadoriRole.isItadori(p1);
        boolean p2Ita = ItadoriRole.isItadori(p2);

        ServerPlayer todo;
        ServerPlayer itadori;
        if (p1Todo && p2Ita) {
            todo = p1; itadori = p2;
        } else if (p2Todo && p1Ita) {
            todo = p2; itadori = p1;
        } else {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(todo) || !GameUtils.isPlayerAliveAndSurvival(itadori)) {
            return;
        }

        PairState st = getOrCreate(todo, itadori);
        long now = todo.level().getGameTime();

        // 成功击掌：无条件交换物品栏 + 扩展感知距离
        swapInventories(todo, itadori);
        st.extended = true;

        // 能量获取受 15 秒冷却限制（物品栏交换不受限）
        if (now - st.lastGainTick >= GAIN_COOLDOWN_TICKS) {
            st.lastGainTick = now;
            int gain = switch (tier) {
                case 1 -> 25;
                case 2 -> 50;
                case 3 -> 75;
                default -> 0;
            };
            if (gain > 0) {
                st.points = Math.min(MAX_POINTS, st.points + gain);
                String msg = switch (tier) {
                    case 1 -> "§e§l默契击掌！§f 默契能量 +" + gain;
                    case 2 -> "§b§l出色击掌！§f 默契能量 +" + gain + " §7（双方 +10% 移速 20 秒）";
                    default -> "§6§l完美击掌！§f 默契能量 +" + gain + " §7（双方无限体力 20 秒）";
                };
                actionBar(todo, msg);
                actionBar(itadori, msg);
                if (tier == 2) {
                    applySpeedBuff(todo, now);
                    applySpeedBuff(itadori, now);
                } else if (tier == 3) {
                    applyEffect(todo, ModEffects.INFINITE_STAMINA, INFINITE_STAMINA_TICKS);
                    applyEffect(itadori, ModEffects.INFINITE_STAMINA, INFINITE_STAMINA_TICKS);
                }
            } else {
                actionBar(todo, "§7默契能量获取冷却中（物品栏已交换，感知距离已扩展）");
                actionBar(itadori, "§7默契能量获取冷却中（物品栏已交换，感知距离已扩展）");
            }
        } else {
            long remain = (GAIN_COOLDOWN_TICKS - (now - st.lastGainTick) + 19) / 20;
            actionBar(todo, "§7默契能量冷却 " + remain + " 秒（物品栏已交换，感知距离已扩展）");
            actionBar(itadori, "§7默契能量冷却 " + remain + " 秒（物品栏已交换，感知距离已扩展）");
        }

        // 击掌音效与火花
        Vec3 mid = todo.position().add(itadori.position()).scale(0.5).add(0, 1.0, 0);
        todo.level().playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.4F, 1.0F);
        todo.level().playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0F, 1.2F);

        sync(st, todo.server);
    }

    // ==================== 不义游戏 ====================

    /** 东堂按下技能键：花费 50 金币开启 3 秒响应窗口 */
    public static void onTodoPress(ServerPlayer todo) {
        ServerPlayer itadori = findCounterpart(todo.server, todo.getUUID(), false);
        if (itadori == null) {
            actionBar(todo, "§c虎杖悠仁不在线，无法发动不义游戏");
            return;
        }
        var shop = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(todo);
        if (shop.balance < COIN_COST) {
            actionBar(todo, "§c金币不足，发动不义游戏需要 " + COIN_COST + " 金币");
            return;
        }
        PairState st = getOrCreate(todo, itadori);
        long now = todo.level().getGameTime();
        if (now < st.windowUntilTick) {
            long remain = (st.windowUntilTick - now + 19) / 20;
            actionBar(todo, "§e不义游戏等待响应中… §7剩余 " + remain + " 秒");
            return;
        }
        // 扣除金币
        shop.balance -= COIN_COST;
        shop.sync();
        st.windowUntilTick = now + WINDOW_TICKS;
        actionBar(todo, "§b§l不义游戏！§f 等待虎杖响应（3 秒内按技能键，消耗 " + COIN_COST + " 金币）");
        // 不提示虎杖，由虎杖自行察觉并响应
        sync(st, todo.server);
    }

    /** 虎杖按下技能键：响应不义游戏 */
    public static void onItadoriPress(ServerPlayer itadori) {
        PairState st = INDEX.get(itadori.getUUID());
        if (st == null) {
            actionBar(itadori, "§c东堂没有发起不义游戏");
            return;
        }
        long now = itadori.level().getGameTime();
        if (now >= st.windowUntilTick) {
            actionBar(itadori, "§c响应窗口已结束");
            return;
        }
        ServerPlayer todo = itadori.server.getPlayerList().getPlayer(st.todoId);
        if (todo == null || !GameUtils.isPlayerAliveAndSurvival(todo)) {
            actionBar(itadori, "§c东堂已不在线");
            return;
        }

        // 能量消耗：1 秒内 25 点（基础），每多 1 秒 +25
        long elapsed = now - (st.windowUntilTick - WINDOW_TICKS);
        int extraSeconds = Math.max(0, (int) Math.ceil(elapsed / 20.0D) - 1);
        int cost = BASE_COST + EXTRA_COST_PER_SECOND * extraSeconds;
        if (st.points < cost) {
            actionBar(itadori, "§c默契能量不足（需要 " + cost + " 点，当前 " + st.points + " 点）");
            actionBar(todo, "§c默契能量不足，不义游戏失败");
            st.windowUntilTick = 0;
            sync(st, itadori.server);
            return;
        }

        st.points -= cost;
        st.windowUntilTick = 0;
        st.focusUntilTick = now + FOCUS_TICKS;

        swapPositions(todo, itadori);

        // 双方 2 秒无敌
        applyEffect(todo, ModEffects.INVINCIBLE, INVINCIBLE_TICKS);
        applyEffect(itadori, ModEffects.INVINCIBLE, INVINCIBLE_TICKS);
        // 虎杖 10 秒夜视
        applyEffect(itadori, MobEffects.NIGHT_VISION, (int) FOCUS_TICKS);

        Vec3 mid = todo.position().add(itadori.position()).scale(0.5).add(0, 1.0, 0);
        // 击掌音效：双方直接播放 + 世界位置播放给附近玩家
        playBrotherClap(todo, mid);
        playBrotherClap(itadori, mid);
        burstParticles(todo);
        burstParticles(itadori);

        actionBar(todo, "§b§l不义游戏！§f 互换成功（-" + cost + " 默契能量，2 秒无敌）");
        actionBar(itadori, "§b§l不义游戏！§f 互换成功（-" + cost + " 默契能量，10 秒专注：连击需求降为 2）");

        sync(st, itadori.server);
    }

    // ==================== 内部实现 ====================

    private static void serverTick(MinecraftServer server) {
        long now = server.overworld().getGameTime();

        // 每 20 tick 检查一次：若东堂与虎杖均在线但尚未建立配对，则自动建立并同步
        // 这样开局就能互相透视，无需等待首次击掌/技能
        if (now % 20 == 0) {
            ensurePairAndSync(server);
        }

        // INDEX 中两个 UUID 指向同一 PairState，distinct 去重后逐个处理
        for (PairState st : INDEX.values().stream().distinct().toList()) {
            // 响应窗口超时
            if (st.windowUntilTick != 0 && now >= st.windowUntilTick) {
                st.windowUntilTick = 0;
                ServerPlayer todo = server.getPlayerList().getPlayer(st.todoId);
                if (todo != null) {
                    actionBar(todo, "§7虎杖没有响应，不义游戏结束");
                }
                sync(st, server);
            }
            // Tier2 移速到期移除
            if (st.speedUntilTick != 0 && now >= st.speedUntilTick) {
                st.speedUntilTick = 0;
                removeSpeedBuff(server.getPlayerList().getPlayer(st.todoId));
                removeSpeedBuff(server.getPlayerList().getPlayer(st.itadoriId));
            }
        }
    }

    private static void applySpeedBuff(ServerPlayer player, long now) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        var modifier = new AttributeModifier(
                SPEED_MODIFIER_ID, 0.10D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        attr.removeModifier(SPEED_MODIFIER_ID);
        attr.addTransientModifier(modifier);
        PairState st = INDEX.get(player.getUUID());
        if (st != null) st.speedUntilTick = now + SPEED_TICKS;
    }

    private static void removeSpeedBuff(ServerPlayer player) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(SPEED_MODIFIER_ID);
    }

    private static void applyEffect(ServerPlayer player, Holder<MobEffect> effect, int ticks) {
        // ambient=false, visible=false：不显示药水粒子与图标角标（屏幕右上角图标仍显示）
        player.addEffect(new MobEffectInstance(effect, ticks, 0, false, true));
    }

    /** 互换双方位置与朝向，清零速度，重置摔落距离 */
    private static void swapPositions(ServerPlayer a, ServerPlayer b) {
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        float ya = a.getYRot(), xa = a.getXRot();
        float yb = b.getYRot(), xb = b.getXRot();

        a.moveTo(pb.x, pb.y, pb.z, yb, xb);
        a.setDeltaMovement(Vec3.ZERO);
        a.fallDistance = 0;
        a.connection.teleport(pb.x, pb.y, pb.z, yb, xb);

        b.moveTo(pa.x, pa.y, pa.z, ya, xa);
        b.setDeltaMovement(Vec3.ZERO);
        b.fallDistance = 0;
        b.connection.teleport(pa.x, pa.y, pa.z, ya, xa);
    }

    /** 交换双方完整物品栏（主背包 36 + 盔甲 4 + 副手 1） */
    private static void swapInventories(ServerPlayer a, ServerPlayer b) {
        int size = a.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            var stackA = a.getInventory().getItem(i);
            var stackB = b.getInventory().getItem(i);
            a.getInventory().setItem(i, stackB);
            b.getInventory().setItem(i, stackA);
        }
        a.getInventory().setChanged();
        b.getInventory().setChanged();
        a.containerMenu.broadcastChanges();
        b.containerMenu.broadcastChanges();
        a.inventoryMenu.broadcastChanges();
        b.inventoryMenu.broadcastChanges();
    }

    private static void burstParticles(ServerPlayer p) {
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
        p.serverLevel().sendParticles(ParticleTypes.CRIT,
                p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.4, 0.4, 0.4, 0.1);
    }

    /**
     * 播放兄弟击掌音效：直接对目标玩家播放（保证听到）+ 在中点播放世界音给附近玩家。
     * 优先使用 coopmod 的拍击音效；未安装 coopmod 时回退到原版暴击/烟花音。
     */
    private static void playBrotherClap(ServerPlayer target, Vec3 worldPos) {
        net.minecraft.server.level.ServerLevel level = target.serverLevel();
        try {
            net.minecraft.sounds.SoundEvent clap = com.cooptest.ModSounds.CLAP_SOUNDS[
                    target.level().getRandom().nextInt(com.cooptest.ModSounds.CLAP_SOUNDS.length)];
            // 直接对玩家播放
            target.playSound(clap, 1.0F, 1.0F);
            // 世界位置播放给附近
            level.playSound(null, worldPos.x, worldPos.y, worldPos.z,
                    clap, SoundSource.PLAYERS, 1.5F, 1.0F);
            level.playSound(null, worldPos.x, worldPos.y, worldPos.z,
                    com.cooptest.ModSounds.PERFECT_DAP, SoundSource.PLAYERS, 1.2F, 0.9F);
        } catch (Throwable t) {
            // coopmod 未安装：回退原版音效
            target.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
            level.playSound(null, worldPos.x, worldPos.y, worldPos.z,
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 0.9F);
            level.playSound(null, worldPos.x, worldPos.y, worldPos.z,
                    SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.2F, 1.0F);
        }
    }

    /**
     * 虎杖专注状态下击杀后播放「黑闪」特效 = coopmod Tier3 击掌触发特效。
     * 粒子/冲击波/音效均镜像 executeTier3。
     */
    public static void playTier3Fx(net.minecraft.server.level.ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.5, 0.5, 0.5, 0.15);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 20, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.3);
        // 10 格战斗冲击波环
        for (int ring = 1; ring <= 5; ring++) {
            double r = ring * 2.0;
            int points = (int) (r * 8);
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians(360.0 / points * i);
                double x = pos.x + Math.cos(angle) * r;
                double z = pos.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.CRIT, x, pos.y + 0.5, z, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }
        // 推开 10 格内其他实体
        AABB box = new AABB(pos.add(-10, -10, -10), pos.add(10, 10, 10));
        for (var entity : level.getEntities(null, box)) {
            double dist = entity.position().distanceTo(pos);
            if (dist > 10 || dist < 0.5) continue;
            Vec3 dir = entity.position().subtract(pos);
            if (dir.lengthSqr() < 0.01) continue;
            dir = dir.normalize();
            double strength = (1.0 - dist / 10.0) * 2.0 + 0.5;
            entity.setDeltaMovement(dir.x * strength, strength * 0.5, dir.z * strength);
            entity.hurtMarked = true;
        }
        // 音效：coopmod tier3 三件套（EPIC_DAP + EXPLOSION_IMPACT + 原版爆炸/烟花/钟）
        try {
            level.playSound(null, pos.x, pos.y, pos.z,
                    com.cooptest.ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0F, 1.0F);
            level.playSound(null, pos.x, pos.y, pos.z,
                    com.cooptest.ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.5F, 1.0F);
        } catch (Throwable ignored) {
            // coopmod 未安装时跳过自定义音效
        }
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.3F);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    private static PairState getOrCreate(ServerPlayer todo, ServerPlayer itadori) {
        PairState existing = INDEX.get(todo.getUUID());
        if (existing != null) return existing;
        PairState st = new PairState();
        st.todoId = todo.getUUID();
        st.itadoriId = itadori.getUUID();
        INDEX.put(todo.getUUID(), st);
        INDEX.put(itadori.getUUID(), st);
        return st;
    }

    /**
     * 每秒检查：若东堂与虎杖均在线但配对未建立，则自动建立并同步一次。
     * 保证开局即可互相透视，无需等待首次交互。
     */
    private static void ensurePairAndSync(MinecraftServer server) {
        ServerPlayer todo = null, itadori = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (TodoAoiRole.isTodo(p)) todo = p;
            else if (ItadoriRole.isItadori(p)) itadori = p;
        }
        if (todo == null || itadori == null) return;
        boolean isNew = INDEX.get(todo.getUUID()) == null;
        PairState st = getOrCreate(todo, itadori);
        if (isNew) {
            sync(st, server);
        }
    }

    /**
     * 在线查找另一方。
     * @param wantTodo true 找东堂；false 找虎杖
     */
    private static ServerPlayer findCounterpart(MinecraftServer server, UUID selfId, boolean wantTodo) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(selfId)) continue;
            if (wantTodo ? TodoAoiRole.isTodo(p) : ItadoriRole.isItadori(p)) {
                return p;
            }
        }
        return null;
    }

    private static void sync(PairState st, MinecraftServer server) {
        sendTo(server, st.todoId, st.itadoriId, st);
        sendTo(server, st.itadoriId, st.todoId, st);
    }

    private static void sendTo(MinecraftServer server, UUID receiverId, UUID partnerId, PairState st) {
        ServerPlayer p = server.getPlayerList().getPlayer(receiverId);
        if (p != null) {
            ServerPlayNetworking.send(p, new BrotherSyncS2CPacket(partnerId, st.points, st.extended));
        }
    }

    /** 对局结束/角色移除：清理某玩家所在配对（两个组件 clear 时各调一次，幂等） */
    public static void resetFor(UUID playerId) {
        PairState st = INDEX.remove(playerId);
        if (st != null) {
            INDEX.remove(st.todoId);
            INDEX.remove(st.itadoriId);
        }
    }

    /** 对局结束兜底：清空所有配对状态，防止跨局残留 */
    public static void resetAll() {
        INDEX.clear();
    }

    /** 东堂葵死亡：给予虎杖一把左轮手枪（兄弟羁绊） */
    public static void onTodoDeath(ServerPlayer todo) {
        ServerPlayer itadori = findCounterpart(todo.server, todo.getUUID(), false);
        if (itadori == null || !GameUtils.isPlayerAliveAndSurvival(itadori)) {
            return;
        }
        net.minecraft.world.item.ItemStack revolver =
                new net.minecraft.world.item.ItemStack(io.wifi.starrailexpress.index.TMMItems.REVOLVER);
        if (!itadori.getInventory().add(revolver)) {
            // 背包满则掉落在虎杖脚下
            itadori.drop(revolver, false);
        }
        actionBar(itadori, "§b§l兄弟羁绊！§f 东堂葵倒下了，你获得了一把左轮手枪");
        itadori.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
    }

    private static void actionBar(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.WHITE), true);
    }
}
