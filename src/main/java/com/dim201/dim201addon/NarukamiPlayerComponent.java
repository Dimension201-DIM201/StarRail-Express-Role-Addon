package com.dim201.dim201addon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NarukamiPlayerComponent implements AutoSyncedComponent {

    private final Player player;

    /** 搜查点数（与尸体交互获得） */
    public int investigationPoints = 0;
    /** 已搜查过的尸体UUID（每具尸体只能获得1点） */
    public final Set<UUID> searchedBodies = new HashSet<>();
    /** 当前打刀攻击的目标UUID，更换目标时重置计数 */
    public UUID currentKatanaTarget = null;
    /** 打刀命中计数：目标UUID -> 命中次数（5次击杀） */
    public final Map<UUID, Integer> katanaHits = new HashMap<>();
    public int skill1Cooldown = 0;
    public int skill2Cooldown = 0;
    public int skill3Cooldown = 0;
    /** 人格面具·伊耶那岐大神 是否已觉醒 */
    public boolean awakened = false;
    /** 技能解锁状态（从服务端同步） */
    public boolean ziodyneUnlocked = false;
    public boolean dashUnlocked = false;
    public boolean crossSlashUnlocked = false;
    /** 十文字斩就绪护盾当前是否由本机制持有（防止与其他来源的护甲混淆） */
    public boolean crossSlashShieldGranted = false;
    /** 固定总玩家数（对局开始时确定） */
    public int fixedTotalPlayers = -1;

    /** 十文字斩常规冷却（tick）：120 秒；觉醒后冷却 30 秒 */
    public static final int CROSS_SLASH_COOLDOWN_TICKS = 120 * 20;
    public static final int CROSS_SLASH_AWAKENED_COOLDOWN_TICKS = 30 * 20;

    public enum Skill {
        ZIODYNE("吉欧达因"),
        DASH("紫电一闪"),
        CROSS_SLASH("十文字斩");

        public final String displayName;

        Skill(String displayName) {
            this.displayName = displayName;
        }
        
        /** 获取解锁所需搜查点数（基于总玩家数动态计算） */
        public int getRequiredPoints(int totalPlayers) {
            return switch (this) {
                case ZIODYNE -> 1;
                case DASH -> 3;
                case CROSS_SLASH -> Math.max(1, totalPlayers / 2 - 1); // 总玩家数/2-1，最小为1
            };
        }
    }

    public Skill currentSkill = Skill.ZIODYNE;
    public int lastSwitchTick = 0;
    private long lastTickGameTime = -1;

    public NarukamiPlayerComponent(Player player) { this.player = player; }

    public void init() {
        this.investigationPoints = 0;
        this.searchedBodies.clear();
        this.currentKatanaTarget = null;
        this.katanaHits.clear();
        this.skill1Cooldown = 0; this.skill2Cooldown = 0; this.skill3Cooldown = 0;
        this.awakened = false;
        this.ziodyneUnlocked = false;
        this.dashUnlocked = false;
        this.crossSlashUnlocked = false;
        this.crossSlashShieldGranted = false;
        this.fixedTotalPlayers = -1;
        this.currentSkill = Skill.ZIODYNE;
        this.lastSwitchTick = 0;
        this.lastTickGameTime = -1;
        sync();
    }
    
    /** 设置固定总玩家数（仅在对局开始时调用一次） */
    public void setFixedTotalPlayers(int totalPlayers) {
        if (this.fixedTotalPlayers == -1) {
            this.fixedTotalPlayers = Math.max(1, totalPlayers);
            updateSkillUnlocks(); // 更新一下解锁状态
        }
    }

    public void clear() {
        // 对局结束/重置：先回收十文字斩护盾，避免残留护甲带入下一局
        if (player instanceof ServerPlayer sp) {
            recycleCrossSlashShield(sp);
        }
        init();
    }
    public void sync() { ModComponents.NARUKAMI.sync(this.player); }
    public void sendActionBar(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); }
    
    /** 更换打刀攻击目标时重置计数 */
    public void updateKatanaTarget(UUID targetUUID) {
        if (this.currentKatanaTarget == null || !this.currentKatanaTarget.equals(targetUUID)) {
            this.currentKatanaTarget = targetUUID;
            this.katanaHits.clear();
        }
    }

    /** 更新技能解锁状态（服务端调用） */
    public void updateSkillUnlocks() {
        int totalPlayers = (this.fixedTotalPlayers != -1) ? this.fixedTotalPlayers : getTotalPlayers();
        this.ziodyneUnlocked = this.investigationPoints >= Skill.ZIODYNE.getRequiredPoints(totalPlayers);
        this.dashUnlocked = this.investigationPoints >= Skill.DASH.getRequiredPoints(totalPlayers);
        this.crossSlashUnlocked = this.investigationPoints >= Skill.CROSS_SLASH.getRequiredPoints(totalPlayers);
        sync();
    }
    
    public boolean isSkillUnlocked(Skill skill) {
        return switch (skill) {
            case ZIODYNE -> this.ziodyneUnlocked;
            case DASH -> this.dashUnlocked;
            case CROSS_SLASH -> this.crossSlashUnlocked;
        };
    }
    
    /** 获取指定技能解锁所需的搜查点数 */
    public int getRequiredPointsForSkill(Skill skill) {
        int totalPlayers = (this.fixedTotalPlayers != -1) ? this.fixedTotalPlayers : getTotalPlayers();
        return skill.getRequiredPoints(totalPlayers);
    }
    
    /** 获取当前参与游戏的玩家总数（兼容客户端和服务端） */
    private int getTotalPlayers() {
        int totalPlayers = 1;
        try {
            var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(this.player.level());
            if (gameWorld != null) {
                // 尽可能用 SRE 的 getParticipatingPlayerCount
                totalPlayers = Math.max(1, io.wifi.starrailexpress.game.GameUtils.getParticipatingPlayerCount(this.player.level()));
            } else if (this.player.level() != null) {
                // 备用方式
                totalPlayers = Math.max(1, this.player.level().players().size());
            }
        } catch (Exception e) {
            totalPlayers = 1;
        }
        return totalPlayers;
    }

    /** 切换到下一个已解锁的技能 */
    public void switchSkill(ServerPlayer player) {
        int currentTick = player.tickCount;
        if (currentTick - lastSwitchTick < 5) return;
        lastSwitchTick = currentTick;

        Skill[] skills = Skill.values();
        // 收集已解锁技能
        java.util.List<Skill> unlocked = new java.util.ArrayList<>();
        for (Skill s : skills) {
            if (isSkillUnlocked(s)) unlocked.add(s);
        }
        if (unlocked.size() <= 1) {
            sendActionBar(player, "§c§l没有可切换的技能！§7（通过搜查尸体获得点数解锁）");
            return;
        }
        int idx = unlocked.indexOf(currentSkill);
        if (idx < 0) idx = 0;
        currentSkill = unlocked.get((idx + 1) % unlocked.size());
        sendActionBar(player, "§9§l切换技能 §7| §f「" + currentSkill.displayName + "」");
        sync();
    }

    public void tick(ServerPlayer player) {
        long currentTime = player.serverLevel().getGameTime();
        if (currentTime == lastTickGameTime) return;
        lastTickGameTime = currentTime;
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) return;

        if (skill1Cooldown > 0) { skill1Cooldown--; if (skill1Cooldown % 20 == 0 || skill1Cooldown == 0) sync(); }
        if (skill2Cooldown > 0) { skill2Cooldown--; if (skill2Cooldown % 20 == 0 || skill2Cooldown == 0) sync(); }
        if (skill3Cooldown > 0) { skill3Cooldown--; if (skill3Cooldown % 20 == 0 || skill3Cooldown == 0) sync(); }

        // 十文字斩就绪护盾：已解锁且不在冷却中时维持 1 层 SRE 常驻护甲
        maintainCrossSlashShield(player);

        if (player.tickCount % 20 == 0) sync();
    }

    // ==================== 十文字斩就绪护盾 ====================
    // 机制：十文字斩未处于冷却时持有 1 层 SRE 原生护盾，可抵挡一次致命攻击；
    // 护盾被击破（OnShieldBroken 事件）后十文字斩进入冷却，冷却结束后重新获得护盾。
    // 使用常驻护甲（armor）而非限时护甲（timedArmor），便于与其他来源的护盾区分。

    /**
     * 每 tick 维持护盾状态：
     * 冷却归零且已解锁 → 补 1 层常驻护甲；标记与实际护甲不一致时自愈。
     */
    private void maintainCrossSlashShield(ServerPlayer sp) {
        if (!crossSlashUnlocked) return;
        var armor = io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp);
        if (armor == null) return;

        if (skill3Cooldown <= 0) {
            // 标记残留但护甲已不在（被外部清零等异常情况）：重置标记后重新授予
            if (crossSlashShieldGranted && armor.getArmor() <= 0) {
                crossSlashShieldGranted = false;
            }
            if (!crossSlashShieldGranted) {
                armor.addArmor(1);
                crossSlashShieldGranted = true;
                sync();
            }
        }
    }

    /** 主动释放十文字斩：回收未被击破的护盾（冷却期间无护盾） */
    public void recycleCrossSlashShield(ServerPlayer sp) {
        if (!crossSlashShieldGranted) return;
        try {
            var armor = io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp);
            if (armor != null && armor.getArmor() > 0) {
                armor.removeArmor(1);
            }
        } catch (Throwable ignored) {
            // 护甲组件异常时仅清标记，不影响技能释放
        } finally {
            crossSlashShieldGranted = false;
        }
    }

    /**
     * 护盾被击破回调（由 NarukamiRole 的 OnShieldBroken 监听器调用）。
     *
     * <p>SRE 消耗护甲时先扣限时护甲再扣常驻护甲：事件触发时若仍有护甲
     * （getArmor()&gt;0），说明被击破的是其他来源的限时护甲，本机制的护盾还在，不处理；
     * 护甲归零才说明本机制的护盾被击破，让十文字斩进入冷却。
     */
    public void onCrossSlashShieldBroken(ServerPlayer sp) {
        if (!crossSlashUnlocked || !crossSlashShieldGranted) return;
        var armor = io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp);
        if (armor != null && armor.getArmor() > 0) return;

        crossSlashShieldGranted = false;
        skill3Cooldown = awakened ? CROSS_SLASH_AWAKENED_COOLDOWN_TICKS : CROSS_SLASH_COOLDOWN_TICKS;
        sync();
        sendActionBar(sp, "§9§l护盾被击破！§7十文字斩进入 " + (skill3Cooldown / 20) + " 秒冷却");
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.investigationPoints = tag.getInt("investigation_points");
        this.skill1Cooldown = tag.getInt("skill1_cooldown");
        this.skill2Cooldown = tag.getInt("skill2_cooldown");
        this.skill3Cooldown = tag.getInt("skill3_cooldown");
        this.awakened = tag.getBoolean("awakened");
        this.ziodyneUnlocked = tag.getBoolean("ziodyne_unlocked");
        this.dashUnlocked = tag.getBoolean("dash_unlocked");
        this.crossSlashUnlocked = tag.getBoolean("cross_slash_unlocked");
        this.crossSlashShieldGranted = tag.getBoolean("cross_slash_shield_granted");
        this.fixedTotalPlayers = tag.getInt("fixed_total_players");
        if (this.fixedTotalPlayers == 0) this.fixedTotalPlayers = -1; // 兼容旧数据
        int skillIdx = tag.getInt("current_skill");
        if (skillIdx < 0 || skillIdx >= Skill.values().length) skillIdx = 0;
        this.currentSkill = Skill.values()[skillIdx];
        this.lastSwitchTick = tag.getInt("last_switch_tick");
        this.searchedBodies.clear();
        ListTag bodies = tag.getList("searched_bodies", Tag.TAG_STRING);
        for (int i = 0; i < bodies.size(); i++) {
            try { this.searchedBodies.add(UUID.fromString(bodies.getString(i))); }
            catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("current_katana_target")) {
            try { this.currentKatanaTarget = UUID.fromString(tag.getString("current_katana_target")); }
            catch (IllegalArgumentException ignored) { this.currentKatanaTarget = null; }
        }
        this.katanaHits.clear();
        if (tag.contains("katana_hits")) {
            CompoundTag hitsTag = tag.getCompound("katana_hits");
            for (String key : hitsTag.getAllKeys()) {
                try { this.katanaHits.put(UUID.fromString(key), hitsTag.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("investigation_points", this.investigationPoints);
        tag.putInt("skill1_cooldown", this.skill1Cooldown);
        tag.putInt("skill2_cooldown", this.skill2Cooldown);
        tag.putInt("skill3_cooldown", this.skill3Cooldown);
        tag.putBoolean("awakened", this.awakened);
        tag.putBoolean("ziodyne_unlocked", this.ziodyneUnlocked);
        tag.putBoolean("dash_unlocked", this.dashUnlocked);
        tag.putBoolean("cross_slash_unlocked", this.crossSlashUnlocked);
        tag.putBoolean("cross_slash_shield_granted", this.crossSlashShieldGranted);
        tag.putInt("fixed_total_players", this.fixedTotalPlayers);
        tag.putInt("current_skill", this.currentSkill.ordinal());
        tag.putInt("last_switch_tick", this.lastSwitchTick);
        ListTag bodies = new ListTag();
        for (UUID uuid : this.searchedBodies) bodies.add(StringTag.valueOf(uuid.toString()));
        tag.put("searched_bodies", bodies);
        if (this.currentKatanaTarget != null) {
            tag.putString("current_katana_target", this.currentKatanaTarget.toString());
        }
        CompoundTag hitsTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : this.katanaHits.entrySet()) {
            hitsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("katana_hits", hitsTag);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) { return player == this.player; }
}
