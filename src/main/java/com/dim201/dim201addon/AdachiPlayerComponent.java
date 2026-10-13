package com.dim201.dim201addon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdachiPlayerComponent implements AutoSyncedComponent {

    private final Player player;

    public int boringTimer = 0;
    public int aliveTime = 0;
    public boolean hasRevived = false;
    public boolean skill2Unlocked = false;   // 灼热奋起解锁（存活 2min）
    public boolean skill3Unlocked = false;   // 恶魔微笑解锁（存活 3min，沿用旧"浓雾"语义）
    public int skill1Cooldown = 0;            // 祸津曼荼罗 CD
    public int skill2Cooldown = 0;            // 灼热奋起 CD
    public int skill3Cooldown = 0;            // 恶魔微笑 CD
    public int executeWindowTicks = 0;        // 恶魔微笑后 10s 斩杀窗口（200=10s）
    public int shieldTicksLeft = 0;           // 灼热奋起护盾剩余时间（40=2s）
    public int heatRiserParticleTicks = 0;    // 灼热奋起粒子持续 tick（40=2s，跟随玩家）
    public boolean shieldGrantedThisSession = false;  // 标记护盾是否由本组件授予以便结束时回收
    public boolean dyingFromBoredom = false;
    public final Map<UUID, Integer> rootedTargets = new HashMap<>();
    public boolean redFogActive = false;

    public double spawnX = 0, spawnY = 0, spawnZ = 0;
    public float spawnYaw = 0, spawnPitch = 0;
    public boolean hasSpawnPos = false;

    public enum Skill {
        MAGATSU("祸津曼荼罗"),
        HEAT_RISER("灼热奋起"),
        DEVIL_SMILE("恶魔微笑");
        public final String displayName;
        Skill(String displayName) { this.displayName = displayName; }
    }

    public Skill currentSkill = Skill.MAGATSU;
    public int lastSwitchTick = 0;
    private long lastTickGameTime = -1;

    public AdachiPlayerComponent(Player player) { this.player = player; }

    public void init() {
        this.boringTimer = 0; this.aliveTime = 0; this.hasRevived = false;
        this.skill2Unlocked = false; this.skill3Unlocked = false;
        this.skill1Cooldown = 0; this.skill2Cooldown = 0; this.skill3Cooldown = 0;
        this.executeWindowTicks = 0; this.shieldTicksLeft = 0;
        this.heatRiserParticleTicks = 0;
        this.shieldGrantedThisSession = false;
        this.dyingFromBoredom = false; this.rootedTargets.clear();
        this.currentSkill = Skill.MAGATSU; this.lastSwitchTick = 0;
        this.lastTickGameTime = -1;
        this.hasSpawnPos = false;
        this.redFogActive = false;
        sync();
    }

    public void clear() { init(); }
    public void sync() { ModComponents.ADACHI.sync(this.player); }
    public void sendActionBar(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); }

    public void setSpawnPos(double x, double y, double z, float yaw, float pitch) {
        this.spawnX = x; this.spawnY = y; this.spawnZ = z;
        this.spawnYaw = yaw; this.spawnPitch = pitch; this.hasSpawnPos = true;
    }

    public void switchSkill(ServerPlayer player) {
        int currentTick = player.tickCount;
        if (currentTick - lastSwitchTick < 5) return;
        lastSwitchTick = currentTick;
        if (!hasRevived) { sendActionBar(player, "§c§l技能切换锁定！§f首次复活后解锁"); return; }
        Skill[] skills = Skill.values();
        int nextIndex = (currentSkill.ordinal() + 1) % skills.length;
        currentSkill = skills[nextIndex];
        sendActionBar(player, "§6§l切换技能 §7| §f「" + currentSkill.displayName + "」");
        sync();
    }

    public boolean isRedFogActive() { return this.redFogActive; }

    public void tick(ServerPlayer player) {
        long currentTime = player.serverLevel().getGameTime();
        if (currentTime == lastTickGameTime) return;
        lastTickGameTime = currentTime;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
            // 死亡或进入观察者模式：立即关闭红雾并回收护盾（保证所有客户端同步清除）
            if (this.redFogActive) {
                this.redFogActive = false;
                sync();
            }
            if (this.shieldTicksLeft > 0 || this.shieldGrantedThisSession) {
                consumeShieldIfStillOn(player);
                this.shieldTicksLeft = 0;
                sync();
            }
            return;
        }

        // 存活时间累计（复活后冻结，解锁进度定格）
        if (!hasRevived) aliveTime++;

        // 无聊计时器：复活前不启动，复活后才开始计时
        if (hasRevived) boringTimer++;

        if (skill1Cooldown > 0) { skill1Cooldown--; if (skill1Cooldown % 20 == 0 || skill1Cooldown == 0) sync(); }
        if (skill2Cooldown > 0) { skill2Cooldown--; if (skill2Cooldown % 20 == 0 || skill2Cooldown == 0) sync(); }
        if (skill3Cooldown > 0) { skill3Cooldown--; if (skill3Cooldown % 20 == 0 || skill3Cooldown == 0) sync(); }

        // 斩杀窗口倒计时
        if (executeWindowTicks > 0) { executeWindowTicks--; if (executeWindowTicks % 20 == 0 || executeWindowTicks == 0) sync(); }
        // 灼热奋起粒子持续 tick 递减（粒子 spawn 在 AdachiRole.tickAdachi 跟随玩家位置）
        if (heatRiserParticleTicks > 0) heatRiserParticleTicks--;

        // 灼热奋起护盾倒计时 + 到期回收
        if (shieldTicksLeft > 0) {
            shieldTicksLeft--;
            if (shieldTicksLeft == 0) { consumeShieldIfStillOn(player); sync(); }
        }

        // 技能解锁检查（仅复活前累计 aliveTime，复活后冻结）
        if (!hasRevived) {
            if (aliveTime >= 2 * 60 * 20 && !skill2Unlocked) { skill2Unlocked = true; sendActionBar(player, "§6§l灼热奋起已解锁！§7（复活后可用）"); sync(); }
            if (aliveTime >= 3 * 60 * 20 && !skill3Unlocked) { skill3Unlocked = true; sendActionBar(player, "§6§l恶魔微笑已解锁！§7（复活后可用）"); sync(); }
        }

        if (player.tickCount % 20 == 0) sync();
    }

    /** 授予 1 层 SRE 原生护盾，并标记由本组件管理（结束时回收） */
    public void grantShieldIfNeeded(ServerPlayer player) {
        try {
            io.wifi.starrailexpress.cca.SREArmorPlayerComponent armor =
                    io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(player);
            if (armor == null) return;
            if (armor.getArmor() < 1) {
                armor.addArmor();
                this.shieldGrantedThisSession = true;
            }
        } catch (Throwable ignored) {}
    }

    /** 回收本组件授予的护盾（参考 LegionPlayerComponent.consumeLegionShieldIfStillOn） */
    public void consumeShieldIfStillOn(ServerPlayer player) {
        if (!this.shieldGrantedThisSession) return;
        try {
            io.wifi.starrailexpress.cca.SREArmorPlayerComponent armor =
                    io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(player);
            if (armor != null && armor.getArmor() > 0) armor.removeArmor(1);
        } catch (Throwable ignored) {} finally {
            this.shieldGrantedThisSession = false;
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.boringTimer = tag.getInt("boring_timer"); this.aliveTime = tag.getInt("alive_time");
        this.hasRevived = tag.getBoolean("has_revived"); this.skill2Unlocked = tag.getBoolean("skill2_unlocked");
        this.skill3Unlocked = tag.getBoolean("skill3_unlocked"); this.skill1Cooldown = tag.getInt("skill1_cooldown");
        this.skill2Cooldown = tag.getInt("skill2_cooldown"); this.skill3Cooldown = tag.getInt("skill3_cooldown");
        this.executeWindowTicks = tag.getInt("execute_window_ticks"); this.shieldTicksLeft = tag.getInt("shield_ticks_left");
        this.heatRiserParticleTicks = tag.getInt("heat_riser_particle_ticks");
        this.shieldGrantedThisSession = tag.getBoolean("shield_granted_this_session");
        this.dyingFromBoredom = tag.getBoolean("dying_from_boredom");
        this.redFogActive = tag.getBoolean("red_fog_active");
        this.currentSkill = Skill.values()[tag.getInt("current_skill")];
        this.lastSwitchTick = tag.getInt("last_switch_tick");
        this.spawnX = tag.getDouble("spawn_x"); this.spawnY = tag.getDouble("spawn_y"); this.spawnZ = tag.getDouble("spawn_z");
        this.spawnYaw = tag.getFloat("spawn_yaw"); this.spawnPitch = tag.getFloat("spawn_pitch");
        this.hasSpawnPos = tag.getBoolean("has_spawn_pos"); this.rootedTargets.clear();
        CompoundTag rootedTag = tag.getCompound("rooted_targets");
        for (String key : rootedTag.getAllKeys()) {
            try { UUID uuid = UUID.fromString(key); int ticks = rootedTag.getInt(key); if (ticks > 0) this.rootedTargets.put(uuid, ticks); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("boring_timer", this.boringTimer); tag.putInt("alive_time", this.aliveTime);
        tag.putBoolean("has_revived", this.hasRevived); tag.putBoolean("skill2_unlocked", this.skill2Unlocked);
        tag.putBoolean("skill3_unlocked", this.skill3Unlocked); tag.putInt("skill1_cooldown", this.skill1Cooldown);
        tag.putInt("skill2_cooldown", this.skill2Cooldown); tag.putInt("skill3_cooldown", this.skill3Cooldown);
        tag.putInt("execute_window_ticks", this.executeWindowTicks); tag.putInt("shield_ticks_left", this.shieldTicksLeft);
        tag.putInt("heat_riser_particle_ticks", this.heatRiserParticleTicks);
        tag.putBoolean("shield_granted_this_session", this.shieldGrantedThisSession);
        tag.putBoolean("dying_from_boredom", this.dyingFromBoredom); tag.putBoolean("red_fog_active", this.redFogActive);
        tag.putInt("current_skill", this.currentSkill.ordinal());
        tag.putInt("last_switch_tick", this.lastSwitchTick);
        tag.putDouble("spawn_x", this.spawnX); tag.putDouble("spawn_y", this.spawnY); tag.putDouble("spawn_z", this.spawnZ);
        tag.putFloat("spawn_yaw", this.spawnYaw); tag.putFloat("spawn_pitch", this.spawnPitch); tag.putBoolean("has_spawn_pos", this.hasSpawnPos);
        CompoundTag rootedTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : this.rootedTargets.entrySet()) rootedTag.putInt(entry.getKey().toString(), entry.getValue());
        tag.put("rooted_targets", rootedTag);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) { return true; }
}
