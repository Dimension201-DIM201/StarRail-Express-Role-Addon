package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.List;

/**
 * 骊驹早鬼（Saki）玩家组件
 * 杀手阵营
 * 技能：劲疾技「三角追击」
 *  - 按技能键 → 启动（速度I），持续 30 秒
 *  - 期间保持移动 2 秒 → 速度等级+1，最大速度 V(5)
 *  - 速度等级 ≥ 3 → 可按 G 突进（非闪现，是沿视角短距离高速度推进）
 *  - 第一次突进后 3 秒窗口内可按 G 再突进一次（最多突进 2 次）
 *  - 突进期间碰到的玩家会被击杀，最多击杀 2 人（突进上限相同）
 *  - 技能结束（时间到 / 主动停止 / 突进两次用完 / 击杀达 2 人）→ 才开始冷却
 *
 * 冷却规则：
 *  - 开局（角色分配时）先有 120s 初始冷却（参考军团）
 *  - 技能结束基础冷却 60s；技能期间每击杀 1 人，下次冷却 +30s（0 杀 60s / 1 杀 90s / 2 杀 120s）
 *
 * 说明：
 *  - 不实现 ServerTickingComponent，改用项目内通用 tick(ServerPlayer)，由 SakiRole.handleServerTick 在 isRole(Saki) 时调用
 *    （与 Reimu/Koishi 组件保持一致，避免项目内两种 tick 范式混用）
 */
public class SakiPlayerComponent implements RoleComponent {

    public static final ComponentKey<SakiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "saki"),
            SakiPlayerComponent.class);

    public static final ResourceLocation DASH_DEATH_REASON = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "saki_dash");

    // 常量
    private static final int MAX_SKILL_TICKS = 15 * 20;          // 技能持续 15s（用户要求：从 30s 改为 15s）
    private static final int SKILL_COOLDOWN_TICKS = 60 * 20;     // 基础冷却 60s（技能结束才开始）
    private static final int STARTING_COOLDOWN_TICKS = 120 * 20; // 开局冷却 120s（参考军团，角色分配时写入）
    private static final int KILL_EXTRA_COOLDOWN_TICKS = 30 * 20; // 技能期间每击杀 1 人，下次冷却 +30s
    private static final int MOVE_REQUIRED_TICKS_PER_LEVEL = 2 * 20; // 连续移动 2s 升一级速度
    private static final int MAX_SPEED_LEVEL = 5;                // 速度 V
    private static final int MIN_SPEED_FOR_DASH = 3;             // 速度 Ⅲ 以上可突进
    private static final int SECOND_DASH_WINDOW_TICKS = 3 * 20;  // 第一次突进后 3s 内可第二次
    private static final int MAX_DASH_COUNT = 2;                 // 最多突进 2 次
    private static final int MAX_KILLS = 2;                      // 最多击杀 2 人
    // 突进距离：speedLevel=3 → 6 格；每高 1 级 +1 格（6/7/8 三档）
    private static double dashDistanceBlocks(int speedLevel) {
        int lvl = Math.max(MIN_SPEED_FOR_DASH, Math.min(MAX_SPEED_LEVEL, speedLevel));
        return 6.0 + (lvl - MIN_SPEED_FOR_DASH);
    }
    private static final double DASH_HIT_RADIUS = 1.8;           // 突进时 AABB 扩展半径（1.8 保证擦肩而过也能命中）
    private static final int DASH_ACTIVE_WINDOW_TICKS = 15;      // 突进判定窗口 15 tick ≈ 0.75s

    private final Player player;

    // 组件状态（按 NBT 同名保存）
    public int skillTicksLeft = 0;                 // 技能剩余时间（tick），>0 代表技能激活中
    public int cooldownTicksLeft = 0;              // 技能冷却剩余时间（tick），>0 期间不能启动技能
    public int speedLevel = 0;                     // 当前速度等级，0~5；0 说明技能未启动
    public int moveAccumTicks = 0;                 // 连续移动累计时间，每满 MOVE_REQUIRED_TICKS_PER_LEVEL 升级
    private Vec3 lastPos = null;                   // 前 tick 位置（辅助位置差值判断）
    private float lastWalkDist = 0.0f;             // 前 tick walkDist（MC 原生步行累积：只要玩家在按键/移动就会增长，跳跃/疾跑/上下楼梯都算，最靠谱指标）
    public int dashCount = 0;                      // 本次技能已突进次数
    public int secondDashWindowTicksLeft = 0;      // 第二次突进窗口剩余（=0 关闭）
    public int killCount = 0;                      // 本次技能击杀计数（最多 MAX_KILLS，到后自动停止技能）
    private int dashActiveTicks = 0;               // 突进刚做完后 6 tick 内作为"突进判定窗口"

    public SakiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return this.player == sp;
    }

    @Override
    public void init() {
        skillTicksLeft = 0;
        cooldownTicksLeft = STARTING_COOLDOWN_TICKS; // 开局冷却 120s（参考军团）
        speedLevel = 0;
        moveAccumTicks = 0;
        lastPos = null;
        lastWalkDist = 0.0f;
        dashCount = 0;
        secondDashWindowTicksLeft = 0;
        killCount = 0;
        dashActiveTicks = 0;
        clearSpeedEffectIfNeeded();
        sync();
    }

    @Override
    public void clear() {
        skillTicksLeft = 0;
        cooldownTicksLeft = 0;
        speedLevel = 0;
        moveAccumTicks = 0;
        lastPos = null;
        lastWalkDist = 0.0f;
        dashCount = 0;
        secondDashWindowTicksLeft = 0;
        killCount = 0;
        dashActiveTicks = 0;
        clearSpeedEffectIfNeeded();
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ================== 公共对外入口（SakiRole.onAbilityUse / 网络包 G 键）==================

    /**
     * 按下 SRE 技能键（G 键）：
     *   - 技能未激活 → 启动三角追击
     *   - 技能激活中 → 执行一次突进（第一次需 speed≥3，第二次需 3s 窗口内）
     *  复用同一个技能键，不额外引入独立按键。
     */
    public boolean useAbility(ServerPlayer sp) {
        if (!Dim201Addon.isInGameAlive(sp)) return false;
        // 场景 ①：技能未激活 → 尝试启动
        if (skillTicksLeft <= 0) {
            if (cooldownTicksLeft > 0) {
                int secs = (cooldownTicksLeft + 19) / 20;
                sendActionBar(sp, "§e三角追击 §f冷却中，还剩 §c" + secs + " §f秒");
                return false;
            }
            skillTicksLeft = MAX_SKILL_TICKS;
            speedLevel = 1;
            moveAccumTicks = 0;
            dashCount = 0;
            secondDashWindowTicksLeft = 0;
            killCount = 0;
            dashActiveTicks = 0;
            lastPos = sp.position();
            applySpeedEffect();
            sendActionBar(sp, "§6§l劲疾技「三角追击」 §f启动！持续 15 秒，移动 2 秒后速度 +1 级");
            sp.playNotifySound(SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8f, 1.15f);
            sync();
            return true;
        }
        // 场景 ②：技能已激活 → 技能键作为"突进"按键（复用同一个技能键）
        tryPerformDash(sp);
        return true;
    }

    /**
     * 执行一次突进请求（技能激活时再按技能键会走到这里）
     * 条件校验都是纯服务端，防止客户端作弊
     */
    private void tryPerformDash(ServerPlayer sp) {
        if (dashCount >= MAX_DASH_COUNT) return;
        if (dashCount == 0) {
            if (speedLevel < MIN_SPEED_FOR_DASH) {
                sendActionBar(sp, "§7突进需要速度等级 ≥ §eⅢ §7（当前 " + speedRoman(speedLevel) + "）");
                return;
            }
        } else {
            if (secondDashWindowTicksLeft <= 0) {
                sendActionBar(sp, "§7第二次突进窗口已关闭");
                return;
            }
        }
        doDash(sp);
    }

    // ================== 核心：突进（非闪现，沿视线方向设置运动速度 + 服务端 setPos 推几格）==================

    private void doDash(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle();
        if (look == null) return;
        // 突进距离根据速度等级动态计算：speedⅢ=6 格、speedⅣ=7 格、speedⅤ=8 格
        final double dist = dashDistanceBlocks(speedLevel);
        final Vec3 origin = sp.position();
        final Vec3 target = origin.add(look.scale(dist));
        // 1) 非闪现：addDeltaMovement 给"真实物理推进"速度；hurtMarked=true 让 MC 真正接受位移
        Vec3 dash = look.normalize().scale(dist * 0.28);
        sp.setDeltaMovement(sp.getDeltaMovement().add(dash.x, Math.max(0.0, dash.y * 0.25 + 0.08), dash.z));
        sp.hurtMarked = true;
        // 2) lerpTo 让客户端产生平滑推进动画（6 参数：x y z yaw pitch steps，MC 1.21 签名）
        ServerLevel level = sp.serverLevel();
        sp.lerpTo(target.x, sp.getY(), target.z, sp.getYRot(), sp.getXRot(), 4);
        // ======== 关键：只有「第二段突进（dashCount==1 调用 → 调用后 dashCount==2）」才允许击杀 ========
        // 第一段突进（dashCount==0 → 调用后变 1）：只位移、不扫掠、不开击杀窗口
        // 第二段突进（dashCount==1 → 调用后变 2）：扫掠 + 开 dashActiveTicks 窗口，允许击杀 ≤2 人
        final boolean isSecondDash = (dashCount == 1);
        dashCount++;                 // 先递增，后判断（因为调用前 dashCount 是"还没突进几次"）
        if (isSecondDash) {
            if (killCount < MAX_KILLS) tryHitSweepPath(sp, origin, target);
            dashActiveTicks = DASH_ACTIVE_WINDOW_TICKS;
            secondDashWindowTicksLeft = 0;           // 第二段用完：关窗口
            sendActionBar(sp, String.format("§c二段突刺 %d 格！ §f击杀触碰玩家后技能结束（最多 2 人）", (int) dist));
        } else {
            dashActiveTicks = 0;                     // 第一段：彻底不开击杀窗口
            secondDashWindowTicksLeft = SECOND_DASH_WINDOW_TICKS; // 打开 3s 二段窗口
            sendActionBar(sp, String.format("§a一段突刺 %d 格！ §f3 秒内再按 §e技能键 §f追加二段突刺（二段击杀敌人）", (int) dist));
        }
        sp.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.25f);
        level.playSound(null, sp.blockPosition(), SoundEvents.WOLF_SHAKE, SoundSource.PLAYERS, 0.6f, 1.4f);
        sync();
    }

    // ================== tick：每 tick 推进计时、移动判定、速度升级、击杀判定、结束/冷却 ==================

    public void tick(ServerPlayer sp) {
        if (!Dim201Addon.isInGameAlive(sp)) {
            // 死亡/观察者：技能立即终止
            if (skillTicksLeft > 0 || dashCount > 0) forceEndSkill(sp, true);
            return;
        }

        // 一、冷却倒计时（仅技能未激活时）
        if (skillTicksLeft <= 0 && cooldownTicksLeft > 0) {
            int prevSec = (cooldownTicksLeft + 19) / 20;
            cooldownTicksLeft--;
            int curSec = (cooldownTicksLeft + 19) / 20;
            if (curSec != prevSec && (curSec == 30 || curSec == 10 || curSec == 5 || curSec == 3 || curSec == 1)) {
                sendActionBar(sp, "§e三角追击 §f冷却：§c" + curSec + " §f秒");
            } else if (cooldownTicksLeft == 0) {
                sendActionBar(sp, "§a✔ 劲疾技「三角追击」冷却结束！按技能键启动");
            }
            sync();
            return;   // 冷却阶段下面都不用跑
        }

        // 二、技能激活阶段
        if (skillTicksLeft > 0) {
            // 1) 技能总剩余 -1
            skillTicksLeft--;

            // 2) 第二次突进窗口倒计时
            if (secondDashWindowTicksLeft > 0) secondDashWindowTicksLeft--;

            // 3) 突进活跃窗口倒计时（用于碰人击杀判定）
            if (dashActiveTicks > 0) dashActiveTicks--;

            // 4) 连续移动累计 2s → 速度升级
            //    判定策略：每 tick 在移动 → +1 tick；不移动 → 只扣 2 tick（而不是清零，避免卡墙角 1 tick 直接归零）
            //    这样只要玩家"基本在移动"，40 tick（≈2s）就升级；偶尔卡一下/停顿 0.1s 只扣少量，不破坏体验
            boolean actuallyMoving = isPlayerActuallyMoving(sp);
            if (actuallyMoving) {
                moveAccumTicks++;
                if (moveAccumTicks >= MOVE_REQUIRED_TICKS_PER_LEVEL && speedLevel < MAX_SPEED_LEVEL) {
                    moveAccumTicks = 0;
                    speedLevel++;
                    applySpeedEffect();
                    if (speedLevel == MIN_SPEED_FOR_DASH) {
                        sendActionBar(sp, "§6速度提升至 §f" + speedRoman(speedLevel) + " §6，按 §e技能键 §6可进行一段突进！");
                    } else {
                        sendActionBar(sp, "§6速度提升至 §f" + speedRoman(speedLevel));
                    }
                }
            } else {
                // 停下来只缓慢扣减（每 tick 扣 1，最多减到 0），不再清零或扣太狠
                if (moveAccumTicks > 0) moveAccumTicks = Math.max(0, moveAccumTicks - 1);
            }

            // 5) 突进活跃 → 检查碰撞玩家 → 击杀（最多 2 人，仅限第二段突进 dashCount==2 时 dashActiveTicks 才>0）
            if (dashActiveTicks > 0 && killCount < MAX_KILLS) {
                tryHitNearbyPlayers(sp);
            }

            // 6) 终止条件（任何一条触发 → 停止技能、进入冷却）
            boolean shouldEnd = false;
            if (killCount >= MAX_KILLS) shouldEnd = true;
            if (dashCount >= MAX_DASH_COUNT && secondDashWindowTicksLeft <= 0) shouldEnd = true;
            if (skillTicksLeft <= 0) shouldEnd = true;
            if (shouldEnd) forceEndSkill(sp, false);
            sync();
        }
    }

    /**
     * 判定玩家是否"真的在移动"——无论走路 / 疾跑 / 跳跃 / 走楼梯 / 轻微走 / 被推搡 全都算。
     * 三重信号三选一（任一成立即认为在移动）：
     *   ① walkDist 相较上一 tick 有增长（MC 原生，MC 原生 walkDist 指标，最靠谱：只要玩家有按键控制角色走路/疾跑/跳跃，哪怕位置被墙/网络延迟不怎么变 walkDist 都会增长
     *   ② xz 实际位置差 distSq > 0.004²（阈值放极宽松，轻微走时哪怕只走一点点也算
     *   ③ xz velocity 模 > 0.004²（velocity 立即生效，server 端 velocity 一非 0 也算）
     */
    private boolean isPlayerActuallyMoving(ServerPlayer sp) {
        // ① walkDist：MC 内部统计的「步行累积距离」——走路/疾跑/跳跃/上下楼梯/水中游动 等全都加
        float wd = sp.walkDist;
        float wdDiff = wd - lastWalkDist;
        lastWalkDist = wd;
        if (wdDiff > 0.0005f) return true;          // walkDist 有一点点增长就认定在移动

        Vec3 now = sp.position();
        if (lastPos != null) {
            double dx = now.x - lastPos.x;
            double dz = now.z - lastPos.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > 0.004 * 0.004) {          // ② 位置差值放宽到 0.004²
                lastPos = now;
                return true;
            }
        }
        lastPos = now;
        // ③ velocity 差值放宽：velocity
        Vec3 v = sp.getDeltaMovement();
        double vxzSq = v.x * v.x + v.z * v.z;
        return vxzSq > 0.004 * 0.004;
    }

    /**
     * 突进活跃窗口内：AABB 扩展 1.8m 碰到玩家 → kill（最多 2 人）
     */
    private void tryHitNearbyPlayers(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        AABB box = sp.getBoundingBox().inflate(DASH_HIT_RADIUS);
        hitFromBox(sp, level, box);
    }

    /**
     * 起点→终点扫掠检测：在 origin -> target 线段上均匀取 7 个采样点（含两端）合并 AABB
     * 解决"突进速度过快，单 tick 当前位置采样没命中擦肩而过的目标"的问题
     */
    private void tryHitSweepPath(ServerPlayer sp, Vec3 origin, Vec3 target) {
        ServerLevel level = sp.serverLevel();
        AABB startBox = new AABB(origin.x - DASH_HIT_RADIUS, origin.y - DASH_HIT_RADIUS, origin.z - DASH_HIT_RADIUS,
                origin.x + DASH_HIT_RADIUS, origin.y + DASH_HIT_RADIUS + 1.8, origin.z + DASH_HIT_RADIUS);
        AABB endBox   = new AABB(target.x - DASH_HIT_RADIUS, target.y - DASH_HIT_RADIUS, target.z - DASH_HIT_RADIUS,
                target.x + DASH_HIT_RADIUS, target.y + DASH_HIT_RADIUS + 1.8, target.z + DASH_HIT_RADIUS);
        // 7 个采样点（0, 1/6, 2/6, 3/6, 4/6, 5/6, 1），合并到一个大 AABB 再一次性 getEntities
        AABB sweepBox = startBox.minmax(endBox);
        for (int i = 1; i <= 5; i++) {
            double t = i / 6.0;
            Vec3 p = origin.add(target.subtract(origin).scale(t));
            sweepBox = sweepBox.minmax(new AABB(
                    p.x - DASH_HIT_RADIUS, p.y - DASH_HIT_RADIUS, p.z - DASH_HIT_RADIUS,
                    p.x + DASH_HIT_RADIUS, p.y + DASH_HIT_RADIUS + 1.8, p.z + DASH_HIT_RADIUS));
        }
        hitFromBox(sp, level, sweepBox);
    }

    private void hitFromBox(ServerPlayer killer, ServerLevel level, AABB box) {
        if (killCount >= MAX_KILLS) return;
        List<Entity> list = level.getEntities((Entity) null, box, e -> e instanceof ServerPlayer);
        for (Entity e : list) {
            if (killCount >= MAX_KILLS) break;
            ServerPlayer target = (ServerPlayer) e;
            if (target.getUUID().equals(killer.getUUID())) continue;          // 不杀自己
            if (!Dim201Addon.isInGameAlive(target)) continue;
            // 普通击杀：可被护盾阻挡（护盾消耗后本次攻击被挡，不执行死亡）
            GameUtils.killPlayer(target, true, killer, DASH_DEATH_REASON);
            killCount++;
            // 击杀后轻微减速，表现为"撞击到目标"手感
            killer.setDeltaMovement(killer.getDeltaMovement().multiply(0.5, 1.0, 0.5));
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
        }
    }

    // ================== 技能结束 / 冷却 ==================

    /**
     * 停止技能（时间到 / 突进用完 / 击杀满 2 人 / 死亡 都会走这里）
     * 关键原则：冷却在"技能结束时才开始计时"
     */
    public void forceEndSkill(ServerPlayer sp, boolean silent) {
        boolean wasActive = (skillTicksLeft > 0 || dashCount > 0);
        // 本次技能击杀数（须在 killCount 清零前读取，用于计算击杀加成冷却）
        int killsThisSkill = killCount;
        skillTicksLeft = 0;
        dashCount = 0;
        secondDashWindowTicksLeft = 0;
        dashActiveTicks = 0;
        moveAccumTicks = 0;
        lastPos = null;
        // ✅ 冷却：结束才开始；基础 60s + 本次技能每击杀 1 人额外 +60s
        if (wasActive) {
            cooldownTicksLeft = SKILL_COOLDOWN_TICKS + killsThisSkill * KILL_EXTRA_COOLDOWN_TICKS;
        }
        killCount = 0;
        speedLevel = 0;
        clearSpeedEffectIfNeeded();
        if (!silent) {
            int secs = (cooldownTicksLeft + 19) / 20;
            String killNote = killsThisSkill > 0
                    ? " §7（本次击杀 §c" + killsThisSkill + " §7人，冷却延长）"
                    : "";
            sendActionBar(sp, "§6三角追击 §f结束，进入 §c" + secs + " §f秒冷却" + killNote);
        }
        sync();
    }

    /** 给玩家刷新一下速度效果，避免原速度效果过期（技能中始终覆盖 speedLevel-1 的 MOVEMENT_SPEED） */
    private void applySpeedEffect() {
        if (!(player instanceof ServerPlayer sp)) return;
        if (speedLevel < 1) return;
        int amplifier = Math.max(0, speedLevel - 1);           // 速度 I 对应 amplifier 0，速度 V 对应 4
        // 效果时长比技能剩余多 2 tick，避免间隙
        int dur = Math.max(40, skillTicksLeft + 2);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, amplifier, false, false, true));
    }

    private void clearSpeedEffectIfNeeded() {
        if (!(player instanceof ServerPlayer sp)) return;
        sp.removeEffect(MobEffects.MOVEMENT_SPEED);
    }

    // ==================== 工具 ====================

    private static String speedRoman(int l) {
        return switch (l) {
            case 0 -> "0";
            case 1 -> "Ⅰ";
            case 2 -> "Ⅱ";
            case 3 -> "Ⅲ";
            case 4 -> "Ⅳ";
            default -> "Ⅴ";
        };
    }

    public void sendActionBar(ServerPlayer p, String msg) {
        if (p == null) return;
        p.displayClientMessage(Component.literal(msg), true);
    }

    public void sendMessage(ServerPlayer p, String msg) {
        if (p == null) return;
        p.displayClientMessage(Component.literal(msg), false);
    }

    // ==================== NBT ====================

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        skillTicksLeft = tag.getInt("SkillTicks");
        cooldownTicksLeft = tag.getInt("CooldownTicks");
        speedLevel = tag.getInt("SpeedLevel");
        moveAccumTicks = tag.getInt("MoveAccum");
        dashCount = tag.getInt("DashCount");
        secondDashWindowTicksLeft = tag.getInt("SecondDashWin");
        killCount = tag.getInt("KillCount");
        dashActiveTicks = tag.getInt("DashActive");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("SkillTicks", skillTicksLeft);
        tag.putInt("CooldownTicks", cooldownTicksLeft);
        tag.putInt("SpeedLevel", speedLevel);
        tag.putInt("MoveAccum", moveAccumTicks);
        tag.putInt("DashCount", dashCount);
        tag.putInt("SecondDashWin", secondDashWindowTicksLeft);
        tag.putInt("KillCount", killCount);
        tag.putInt("DashActive", dashActiveTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }
}
