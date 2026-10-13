package com.dim201.dim201addon;

import com.dim201.dim201addon.role.DaoLeirenRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 蕾忍玩家组件
 *
 * <p>管理：
 * <ul>
 *   <li>蕾克拉（reika）：移动积累，每1格+1，上限120</li>
 *   <li>攻击模式：岚刀(默认) / 燃烧刀</li>
 *   <li>模式切换冷却（5秒）</li>
 *   <li>打刀连击计数（岚刀模式5次击杀）</li>
 *   <li>舍利子掉落标记（每局限掉1次）</li>
 *   <li>死亡位置（用于舍利子复活）</li>
 *   <li>身份隐藏标记（死亡后隐藏所有玩家身份）</li>
 * </ul>
 */
public class DaoLeirenPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DaoLeirenPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "dao_leiren"),
            DaoLeirenPlayerComponent.class);

    // ==================== 常量 ====================

    /** 蕾克拉上限 */
    public static final int MAX_REIKA = 120;
    /** 切换到燃烧刀模式消耗的蕾克拉 */
    public static final int RANDAO_SWITCH_COST = 60;
    /** 燃烧刀模式每秒消耗蕾克拉 */
    public static final int RANDAO_DRAIN_PER_SEC = 3;
    /** 模式切换冷却（5秒 = 100 tick） */
    public static final int MODE_SWITCH_COOLDOWN = 5 * 20;
    /** 岚刀模式击杀所需连击数 */
    public static final int LANDAO_KILL_HITS = 5;
    /** 岚刀模式击杀后打刀冷却（30秒，与鸣上悠一致） */
    public static final int KATANA_COOLDOWN = 30 * 20;
    /** 燃烧刀模式每次击杀后打刀冷却（1秒，防止瞬杀多人） */
    public static final int RANDAO_HIT_COOLDOWN = 20;
    /** 舍利子复活等待时间（120秒） */
    public static final long RESURRECT_DELAY = 120 * 20L;

    // ==================== 攻击模式 ====================

    public enum AttackMode {
        LANDAO("§e岚刀模式"),
        RANDAO("§c燃烧刀模式");

        public final String displayName;
        AttackMode(String displayName) { this.displayName = displayName; }
    }

    // ==================== 状态字段 ====================

    private final Player player;

    /** 蕾克拉值 */
    public int reika = 0;
    /** 当前攻击模式 */
    public AttackMode attackMode = AttackMode.LANDAO;
    /** 模式切换冷却剩余 tick */
    public int modeSwitchCooldown = 0;
    /** 燃烧刀模式蕾克拉消耗计时器（每20tick消耗3点） */
    public int randaoDrainTimer = 0;

    /** 打刀连击计数：目标UUID -> 命中次数 */
    public final Map<UUID, Integer> katanaHits = new HashMap<>();
    /** 当前打刀攻击目标UUID */
    public UUID currentKatanaTarget = null;

    /** 舍利子是否已掉落（每局蕾忍限掉1次） */
    public boolean sariraDropped = false;

    /** 蕾忍是否已死亡（用于身份隐藏） */
    public boolean isDead = false;
    /** 死亡时的游戏时间（用于舍利子复活计时） */
    public long deathGameTime = 0L;
    /** 死亡位置 */
    public double deathX = 0, deathY = 0, deathZ = 0;

    // ==================== 移动追踪 ====================

    private double lastX = 0, lastZ = 0;
    private boolean positionInitialized = false;
    /** 蕾克拉累加器（距离小数部分） */
    private double reikaAccumulator = 0;

    // ==================== 构造与生命周期 ====================

    public DaoLeirenPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.reika = 0;
        this.attackMode = AttackMode.LANDAO;
        this.modeSwitchCooldown = 0;
        this.randaoDrainTimer = 0;
        this.katanaHits.clear();
        this.currentKatanaTarget = null;
        this.sariraDropped = false;
        this.isDead = false;
        this.deathGameTime = 0L;
        this.deathX = 0; this.deathY = 0; this.deathZ = 0;
        this.positionInitialized = false;
        this.reikaAccumulator = 0;
        this.lastX = player.getX();
        this.lastZ = player.getZ();
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    // ==================== Server Tick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRole(player, DaoLeirenRole.DAO_LEIREN)) return;
        if (!gw.isRunning()) return;

        // 死亡状态下不做任何处理
        if (isDead || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        // ---- 模式切换冷却递减 ----
        if (modeSwitchCooldown > 0) {
            modeSwitchCooldown--;
            if (modeSwitchCooldown == 0) sync();
        }

        // ---- 移动积累蕾克拉 ----
        accumulateReikaFromMovement(sp);

        // ---- 燃烧刀模式蕾克拉消耗 ----
        if (attackMode == AttackMode.RANDAO) {
            randaoDrainTimer++;
            if (randaoDrainTimer >= 20) {  // 每秒
                randaoDrainTimer = 0;
                reika -= RANDAO_DRAIN_PER_SEC;
                if (reika <= 0) {
                    reika = 0;
                    // 蕾克拉耗尽自动切回岚刀模式
                    attackMode = AttackMode.LANDAO;
                    sendActionBar(sp, "§c§l蕾克拉耗尽！§7自动切换回岚刀模式");
                    sync();
                } else {
                    sync();
                }
            }
        }

        // 每20tick同步一次
        if (sp.tickCount % 20 == 0) sync();
    }

    /** 蕾克拉积累：每移动5格+1点 */
    private static final double REIKA_PER_BLOCK = 1.0 / 5.0;  // 5格=1点

    private void accumulateReikaFromMovement(ServerPlayer sp) {
        double currentX = sp.getX();
        double currentZ = sp.getZ();

        if (!positionInitialized) {
            lastX = currentX;
            lastZ = currentZ;
            positionInitialized = true;
            return;
        }

        double dx = currentX - lastX;
        double dz = currentZ - lastZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        // 限制单次最大距离，防止传送/突进刷蕾克拉
        if (distance > 10.0) distance = 0;

        if (distance > 0.01) {
            reikaAccumulator += distance * REIKA_PER_BLOCK;
            while (reikaAccumulator >= 1.0 && reika < MAX_REIKA) {
                reikaAccumulator -= 1.0;
                reika++;
            }
            // 达到上限时清空累加器
            if (reika >= MAX_REIKA) reikaAccumulator = 0;
        }

        lastX = currentX;
        lastZ = currentZ;
    }

    // ==================== 模式切换 ====================

    /**
     * 切换攻击模式
     * @return true 表示切换成功
     */
    public boolean switchMode(ServerPlayer sp) {
        // 冷却检查
        if (modeSwitchCooldown > 0) {
            int remainSec = (int) Math.ceil(modeSwitchCooldown / 20.0);
            sendActionBar(sp, "§c切换冷却中 §f" + remainSec + "秒");
            return false;
        }

        if (attackMode == AttackMode.LANDAO) {
            // 岚刀 → 燃烧刀
            if (reika < RANDAO_SWITCH_COST) {
                sendActionBar(sp, "§c蕾克拉不足！需要 " + RANDAO_SWITCH_COST + " 点（当前 " + reika + "）");
                return false;
            }
            reika -= RANDAO_SWITCH_COST;
            attackMode = AttackMode.RANDAO;
            randaoDrainTimer = 0;

            // 刷新打刀冷却
            sp.getCooldowns().removeCooldown(com.dim201.dim201addon.item.Dim201ModItems.KATANA);

            // 播放燃烧刀切换音频
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    com.dim201.dim201addon.sound.Dim201ModSounds.DAO_LEIREN_SWITCH_RAN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

            sendActionBar(sp, "§c§l切换至燃烧刀模式！§7每秒消耗3蕾克拉");
        } else {
            // 燃烧刀 → 岚刀
            attackMode = AttackMode.LANDAO;
            randaoDrainTimer = 0;

            // 播放岚刀切换音频（volume=3.0 扩大传播范围至48格）
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    com.dim201.dim201addon.sound.Dim201ModSounds.DAO_LEIREN_SWITCH_LAN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 1.0f);

            sendActionBar(sp, "§e§l切换至岚刀模式！§7连击5次击杀");
        }

        modeSwitchCooldown = MODE_SWITCH_COOLDOWN;
        sync();
        return true;
    }

    // ==================== 打刀连击管理 ====================

    /** 更换打刀攻击目标时重置计数 */
    public void updateKatanaTarget(UUID targetUUID) {
        if (currentKatanaTarget == null || !currentKatanaTarget.equals(targetUUID)) {
            currentKatanaTarget = targetUUID;
            katanaHits.clear();
        }
    }

    /** 获取对目标的连击数（+1后返回） */
    public int incrementKatanaHits(UUID targetUUID) {
        updateKatanaTarget(targetUUID);
        int hits = katanaHits.getOrDefault(targetUUID, 0) + 1;
        katanaHits.put(targetUUID, hits);
        return hits;
    }

    /** 清除目标的连击计数 */
    public void clearKatanaHits(UUID targetUUID) {
        katanaHits.remove(targetUUID);
    }

    // ==================== 死亡/复活处理 ====================

    /** 蕾忍死亡时调用，记录死亡位置和时间 */
    public void onDeath(ServerPlayer sp) {
        isDead = true;
        deathX = sp.getX();
        deathY = sp.getY();
        deathZ = sp.getZ();
        deathGameTime = sp.serverLevel().getGameTime();
        // 切回岚刀模式
        attackMode = AttackMode.LANDAO;
        katanaHits.clear();
        currentKatanaTarget = null;
        sync();
    }

    /** 蕾忍被舍利子复活时调用，重置状态 */
    public void onRevive() {
        isDead = false;
        reika = 0;
        attackMode = AttackMode.LANDAO;
        modeSwitchCooldown = 0;
        randaoDrainTimer = 0;
        katanaHits.clear();
        currentKatanaTarget = null;
        deathGameTime = 0L;
        positionInitialized = false;
        reikaAccumulator = 0;
        sync();
    }

    // ==================== NBT 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("reika", reika);
        tag.putInt("attackMode", attackMode.ordinal());
        tag.putInt("modeSwitchCooldown", modeSwitchCooldown);
        tag.putBoolean("sariraDropped", sariraDropped);
        tag.putBoolean("isDead", isDead);
        tag.putLong("deathGameTime", deathGameTime);
        tag.putDouble("deathX", deathX);
        tag.putDouble("deathY", deathY);
        tag.putDouble("deathZ", deathZ);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        reika = tag.getInt("reika");
        int modeIdx = tag.getInt("attackMode");
        attackMode = (modeIdx >= 0 && modeIdx < AttackMode.values().length)
                ? AttackMode.values()[modeIdx] : AttackMode.LANDAO;
        modeSwitchCooldown = tag.getInt("modeSwitchCooldown");
        sariraDropped = tag.getBoolean("sariraDropped");
        isDead = tag.getBoolean("isDead");
        deathGameTime = tag.getLong("deathGameTime");
        deathX = tag.getDouble("deathX");
        deathY = tag.getDouble("deathY");
        deathZ = tag.getDouble("deathZ");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }
}
