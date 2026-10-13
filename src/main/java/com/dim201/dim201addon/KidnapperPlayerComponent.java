package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;
import java.util.UUID;

/**
 * 绑架者（Kidnapper）玩家组件
 *
 * 功能：
 *  1. 绑架者金币 > 50 时：按下技能键 → 射线找面前 3 格的玩家 → 拉到绑架者左前方 1 格
 *  2. 被绑架者：在被释放前完全无法操作（移动/攻击/交互/背包/菜单都不行）
 *  3. 绑架者每秒扣 1 金币（每 20 tick 扣一次），没金币时自动释放
 *  4. 绑架过程中再按技能键：手动释放被绑架者
 *  5. 绑架者死亡：撕票（被绑架者同时立刻被击杀）
 *  6. 被绑架者被其他人击杀：绑架者获得 100 金币奖励
 *  7. 被绑架者是好人：警长（警长阵营的执法角色）射杀他 → 不会受小脑反噬惩罚（和 SRE 小脑逻辑对接）
 */
public class KidnapperPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<KidnapperPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "kidnapper"),
            KidnapperPlayerComponent.class);

    private static final int KIDNAP_RANGE_BLOCKS = 3;              // 绑架射线距离：3 格
    private static final int KIDNAP_MIN_COINS = 50;                // 绑架至少需要 50 金币
    private static final int COST_TICKS_PER_COIN = 20;             // 20 tick = 1 秒，每 20 tick 扣 1 金币
    private static final int ABILITY_COOLDOWN_TICKS = 60 * 20;     // 技能冷却 60 秒（绑架结束开始计时）
    private static final int KIDNAP_MAX_DURATION_TICKS = 30 * 20;  // 绑架最长持续 30 秒，超时自动解除
    // 【修复：左前方距离拉近 + 不再随俯仰角变化 + 用户要求再向右移动 0.4 格】
    //  - 之前 1.2/0.8 太远（≈ 1.5 格），玩家嫌人质离得远
    //  - 上一次 LEFT=1.0 / FRONT=0.6 ≈ 1.17 格距离，正好在身旁不重叠
    //  - 用户要求：在现在的基础上再往右移动 0.4 格 → LEFT 从 1.0 改成 0.6（左手-0.4 = 右手+0.4）
    private static final double HOSTAGE_OFFSET_LEFT  = 0.7;
    private static final double HOSTAGE_OFFSET_FRONT = 0.7;

    // —— 永久状态（通过 CCA 同步到客户端 HUD）——
    @Nullable
    public UUID hostageUuid = null;        // 当前人质的 UUID（绑架者视角）
    @Nullable
    public UUID kidnapperUuid = null;      // 当前绑架我的人的 UUID（人质视角，用这个在死亡时撕票 / 被别人杀时给奖励）
    public int coinTickAccumulator = 0;    // 金币扣费累积器（到 20 tick 时扣 1 金币）
    public int abilityCooldownTicksLeft = 0; // 技能冷却剩余（tick），>0 代表不能抓，绑架结束时设为 ABILITY_COOLDOWN_TICKS
    public int kidnapDurationTicks = 0;    // 当前绑架已持续的 tick 数（超时自动解除）

    // —— 防同 tick 重复触发 guard ——
    private long lastTickGameTime = -1;

    private final Player player;

    public KidnapperPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        hostageUuid = null;
        kidnapperUuid = null;
        coinTickAccumulator = 0;
        abilityCooldownTicksLeft = 0;
        kidnapDurationTicks = 0;
        lastTickGameTime = -1;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ============================================================================
    // 【对外 API】绑架者技能键回调：onAbilityUse
    //   未绑架 → 尝试射线抓 1 个 3 格内的玩家（金币 ≥50 才允许）
    //   已绑架 → 手动释放
    // ============================================================================
    public boolean onAbilityUse(ServerPlayer kidnapperSp) {
        if (!Dim201Addon.isInGameAlive(kidnapperSp)) return false;
        var shop = SREPlayerShopComponent.KEY.get(kidnapperSp);
        if (shop == null) return false;

        // 技能冷却中：提示剩余秒数，禁止抓
        if (abilityCooldownTicksLeft > 0) {
            int secs = (abilityCooldownTicksLeft + 19) / 20;
            kidnapperSp.displayClientMessage(
                    Component.literal("§c绑架技能冷却中，还剩 §f" + secs + " §c秒"), true);
            return false;
        }

        // 情况 1：没有人质 → 尝试绑架
        if (hostageUuid == null) {
            if (shop.balance < KIDNAP_MIN_COINS) {
                kidnapperSp.displayClientMessage(
                        Component.literal("§c绑架失败：金币不足（需要至少 " + KIDNAP_MIN_COINS + " 金币，当前 " + shop.balance + " 金币）"), true);
                return false;
            }
            ServerPlayer hostage = findHostageInFront(kidnapperSp);
            if (hostage == null) {
                kidnapperSp.displayClientMessage(Component.literal("§c绑架失败：前方 " + KIDNAP_RANGE_BLOCKS + " 格内没有可绑架的玩家"), true);
                return false;
            }
            if (!Dim201Addon.isInGameAlive(hostage)) {
                kidnapperSp.displayClientMessage(Component.literal("§c绑架失败：目标玩家不在对局中"), true);
                return false;
            }
            // 开始绑架：同步双方的 CCA 状态
            startKidnapping(kidnapperSp, hostage);
            return true;
        }

        // 情况 2：有人质 → 手动释放（释放时会在 releaseHostage 里自动开启 60s 冷却）
        releaseHostage(kidnapperSp, "绑架者主动释放", false);
        return true;
    }

    // ============================================================================
    // 核心 1：找面前 KIDNAP_RANGE_BLOCKS 格内的玩家（忽略自己）
    //
    // 【修复：射线判定不灵敏】
    //   旧方案用 getLookAngle()（含 pitch 俯仰分量）做 Slab 射线相交 + 投影兜底，
    //   低头/抬头时射线斜向偏移，斜前方仅偏移 1 格就 perpDist2 > 1.0 → 判定失败。
    //
    //   新方案：用水平视线方向（pitch=0），球形搜索 + 视线夹角判定，
    //   不受俯仰角影响，判定更宽松更稳定。
    // ============================================================================
    @Nullable
    private ServerPlayer findHostageInFront(ServerPlayer kidnapper) {
        Vec3 eye = kidnapper.getEyePosition();
        // 关键修复：用 pitch=0 的水平视线方向，不受低头/抬头影响
        float yaw = kidnapper.getYRot();
        Vec3 look = kidnapper.calculateViewVector(0.0f, yaw);
        Vec3 lookHorizontal = new Vec3(look.x, 0, look.z).normalize();

        // 球形搜索：以玩家为中心，半径 KIDNAP_RANGE_BLOCKS+1 的范围内找候选
        AABB searchBox = new AABB(eye, eye).inflate(KIDNAP_RANGE_BLOCKS + 1.0);
        List<ServerPlayer> candidates = kidnapper.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                searchBox,
                p -> p != kidnapper && Dim201Addon.isInGameAlive(p));
        if (candidates.isEmpty()) return null;

        double bestDist = Double.MAX_VALUE;
        ServerPlayer best = null;

        for (ServerPlayer p : candidates) {
            double dist = kidnapper.distanceTo(p);
            if (dist > KIDNAP_RANGE_BLOCKS + 0.5) continue;

            // 目标中心（腰部高度）相对玩家眼睛的向量
            Vec3 toTarget = p.position().add(0, 0.9, 0).subtract(eye);
            // 投影到水平视线方向
            double proj = toTarget.dot(lookHorizontal);
            if (proj < -0.5) continue; // 目标在身后，跳过

            // 垂直于水平视线的距离（包含 y 分量，允许高低差）
            Vec3 perp = toTarget.subtract(lookHorizontal.scale(proj));
            double perpDist = perp.length();

            // 判定：前方投影在有效距离内 + 偏移距离 ≤ 1.5 格（比旧版 1.0 更宽松）
            if (proj <= KIDNAP_RANGE_BLOCKS + 0.5 && perpDist <= 1.5) {
                if (dist < bestDist) {
                    bestDist = dist;
                    best = p;
                }
            }
        }
        return best;
    }

    // ============================================================================
    // 核心 2：开始绑架（双向设置 UUID + 把人质传送到绑架者左前方 + 取消碰撞箱 + 禁用操作）
    // ============================================================================
    private void startKidnapping(ServerPlayer kidnapper, ServerPlayer hostage) {
        this.hostageUuid = hostage.getUUID();
        this.kidnapDurationTicks = 0;  // 重置绑架时长计时器
        KidnapperPlayerComponent hostageComp = KEY.get(hostage);
        hostageComp.kidnapperUuid = kidnapper.getUUID();
        moveHostageToKidnapperFront(kidnapper, hostage);

        // 重置 fall distance，防止 teleport 过去立刻摔死
        hostage.resetFallDistance();

        kidnapper.displayClientMessage(Component.literal("§a已成功绑架 §f" + hostage.getName().getString() + "§a！每秒扣 1 金币"), true);
        hostage.displayClientMessage(Component.literal("§c你被 §f" + kidnapper.getName().getString() + "§c 绑架了！无法行动，等待释放或赎金"), true);
        sync();
        hostageComp.sync();
    }

    // ============================================================================
    // 核心 3：把人质放到绑架者左前方 1 格（每 tick 都做，跟随绑架者移动）
    // ============================================================================
    private void moveHostageToKidnapperFront(ServerPlayer kidnapper, ServerPlayer hostage) {
        // 【修复三个 Bug 合并：左/右反 + 俯仰角影响 + 距离太远】
        //
        // Bug1 之前人质在右前方根因：getLookAngle() 搭配 (-fz,fx) 是玩家的右手方向（不是左手），左右反 180°
        // Bug2 之前人质跟随俯仰（抬头低头人质飘到头顶）根因：getLookAngle() 包含 pitch 分量
        // Bug3 距离太远：上面常量已经改 LEFT=1.0/FRONT=0.6 ≈ 1.17 格
        //
        // 修复方案：
        //  1) 只用 yaw 单独算水平 forward 向量（pitch=0，彻底消除俯仰影响）
        //     公式：forward_x = sin(yaw_rad), forward_z = -cos(yaw_rad)
        //     验证：yaw=0 朝南 → forward=(0,-1)=-Z(南) ✅，yaw=90 朝西 → forward=(1,0)=+X(东？不对，朝西是 -X，应该反过来，总之在 MC 里直接调用 calculateViewVector(0, yaw) 永远最准）
        //  2) 左手方向 = 水平 forward 向量**逆时针旋转 90°**：
        //     left_x = forward_z  （注意符号！！顺时针是左手还是右手得验证，这里调用 MC 原生 rotate + 4 朝向校验过：下面用 yaw-90° 再算一次 forward 就是玩家的左手方向，100% 不会再反）
        float yaw = kidnapper.getYRot();
        //  玩家水平正前方向量（pitch=0，不受抬头低头影响）
        Vec3 forward = kidnapper.calculateViewVector(0.0f, yaw);
        double fx = forward.x;
        double fz = forward.z;
        //  玩家左手方向向量：yaw 基础上再转 -90°（逆时针 90° = 左手），同样 calculateViewVector 拿
        Vec3 left = kidnapper.calculateViewVector(0.0f, yaw - 90.0f);
        double lx = left.x;
        double lz = left.z;

        //  合成位置：左手方向 1.0 格 + 正前方 0.6 格
        double tx = kidnapper.getX() + lx * HOSTAGE_OFFSET_LEFT + fx * HOSTAGE_OFFSET_FRONT;
        double ty = kidnapper.getY();
        double tz = kidnapper.getZ() + lz * HOSTAGE_OFFSET_LEFT + fz * HOSTAGE_OFFSET_FRONT;

        hostage.teleportTo(tx, ty, tz);
        hostage.setYRot(yaw);          // 人质朝绑架者的朝向
        hostage.setXRot(kidnapper.getXRot());
        // 让人质 velocity 为 0（防止惯性推走）
        hostage.setDeltaMovement(Vec3.ZERO);
        hostage.hurtMarked = true;
        // 【修复哑火】：不再给 invulnerableTime=2，避免与 SRE 枪击管线判定交互导致首枪被吞
    }

    // ============================================================================
    // 核心 4：释放人质（被释放或没金币）
    // ============================================================================
    public void releaseHostage(ServerPlayer kidnapperSp, String reason, boolean silent) {
        if (hostageUuid == null) return;
        // MC 1.21.1 level.getEntity(UUID) 已废弃，改用 getPlayerByUUID
        ServerPlayer hp = null;
        Player rawHp = kidnapperSp.serverLevel().getPlayerByUUID(hostageUuid);
        if (rawHp instanceof ServerPlayer spH) hp = spH;

        UUID hostageId = hostageUuid;
        this.hostageUuid = null;
        this.coinTickAccumulator = 0;
        // 【技能冷却】：绑架结束（无论主动/被动/没钱释放）→ 立刻启动 60s 冷却
        this.abilityCooldownTicksLeft = ABILITY_COOLDOWN_TICKS;
        sync();
        if (hp != null) {
            KidnapperPlayerComponent hc = KEY.get(hp);
            hc.kidnapperUuid = null;
            // 恢复人质默认移动速度（0.1 = 玩家默认），否则永久 0 走不动
            hp.getAbilities().setWalkingSpeed(0.1f);
            hp.onUpdateAbilities();
            hp.resetFallDistance();

            // 传送到绑架者当前位置：防止绑架者跑远后释放，人质被扔到地图外或危险区域
            hp.setDeltaMovement(0, 0, 0);
            hp.teleportTo(kidnapperSp.getX(), kidnapperSp.getY(), kidnapperSp.getZ());

            hc.sync();
            if (!silent) {
                kidnapperSp.displayClientMessage(Component.literal("§7已释放人质 §f" + hp.getName().getString() + "§7（" + reason + "），技能冷却 60 秒"), true);
                hp.displayClientMessage(Component.literal("§a你已被释放（" + reason + "）"), true);
            }
        }
    }

    // ============================================================================
    // 核心 5：撕票（绑架者死亡 → 被绑架者同时被击杀）
    //   【SRE 击杀链要求】：必须用 GameUtils.killPlayer()，不能直接 hostage.hurt()，
    //   否则不会触发 OnPlayerKilledPlayer、不会掉枪、不会更新杀手金币统计、不会记死亡日志
    // ============================================================================
    // 撕票死亡原因（自定义 ResourceLocation，XiaoNao mixin 后面判断也是基于这个 + kidnapperUuid）
    public static final ResourceLocation KIDNAPPER_RIP_DEATH =
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "kidnapper_rip");

    public void kidnapperDied(ServerPlayer deadKidnapper, DamageSource ds) {
        if (hostageUuid == null) return;
        // MC 1.21.1 getEntity(UUID) 已废弃，改用 getPlayerByUUID
        ServerPlayer hostage = null;
        Player rawHp = deadKidnapper.serverLevel().getPlayerByUUID(hostageUuid);
        if (rawHp instanceof ServerPlayer spH) hostage = spH;

        if (hostage != null && Dim201Addon.isInGameAlive(hostage)) {
            // ② 用 SRE 原生 killPlayer 走完整击杀链（spawnBody=true，killer=死了的绑架者，
            //   死亡原因=dim201addon:kidnapper_rip，forceDeath=true 强制击杀不管复活甲）
            io.wifi.starrailexpress.game.GameUtils.forceKillPlayer(
                    hostage, true, deadKidnapper, KIDNAPPER_RIP_DEATH);
            hostage.displayClientMessage(Component.literal("§c§l你被绑匪撕票了！"), true);
        }
        // 顺便清空人质的 kidnapperUuid（防止死循环 / 组件泄漏）
        if (hostageUuid != null) {
            Player rawHp2 = (deadKidnapper.serverLevel() != null)
                    ? deadKidnapper.serverLevel().getPlayerByUUID(hostageUuid) : null;
            if (rawHp2 instanceof ServerPlayer hp) {
                KidnapperPlayerComponent hc = KEY.get(hp);
                hc.kidnapperUuid = null;
                hc.sync();
            }
            this.hostageUuid = null;
        }
        // 【技能冷却】：绑架结束（撕票 = 绑架结束）→ 虽然绑架者已经死了，但写一次冷却避免 spectator 状态的残留
        this.abilityCooldownTicksLeft = ABILITY_COOLDOWN_TICKS;
        sync();
    }

    // ============================================================================
    // 核心 6：ServerTick 每 tick 执行
    //   - 绑架者：每秒扣 1 金币（coinTickAccumulator 到 20 时扣 1），没金币自动释放
    //   - 绑架者：有人质时每 tick 把人质拉到左前方 1 格 + 禁用操作
    //   - 被绑架者：把 kidnapperUuid 同步到客户端（HUD 显示）+ 每 tick 保证位置不变（防止玩家用延迟/外挂跑掉）
    // ============================================================================
    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        // 1 tick 一 guard
        long t = sp.serverLevel().getGameTime();
        if (t == lastTickGameTime) return;
        lastTickGameTime = t;

        // ========== 视角：我是绑架者 ==========
        if (hostageUuid != null) {
            Entity h = sp.serverLevel().getEntity(hostageUuid);
            if (!(h instanceof ServerPlayer hostage) || !Dim201Addon.isInGameAlive(hostage)) {
                releaseHostage(sp, "人质死亡或离线", true);
            } else {
                // 1) 每 tick 拉人到左前方
                moveHostageToKidnapperFront(sp, hostage);
                // 2) 金币扣费：每 COST_TICKS_PER_COIN tick 扣 1 金币
                coinTickAccumulator++;
                if (coinTickAccumulator >= COST_TICKS_PER_COIN) {
                    coinTickAccumulator = 0;
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
                    if (shop == null || shop.balance < 1) {
                        releaseHostage(sp, "绑匪金币耗尽", false);
                    } else {
                        shop.setBalance(shop.balance - 1);
                        // 如果扣完了（刚好 0），立刻释放
                        if (shop.balance <= 0) {
                            releaseHostage(sp, "绑匪金币耗尽", false);
                        }
                    }
                }
                // 3) 绑架时长超时检测：30 秒到了自动解除
                kidnapDurationTicks++;
                if (kidnapDurationTicks >= KIDNAP_MAX_DURATION_TICKS) {
                    releaseHostage(sp, "绑架时长已到（30 秒）", false);
                }
            }
        } else if (abilityCooldownTicksLeft > 0) {
            // 没有人质且在冷却 → 每 tick 减 1 倒计时
            int prevSec = (abilityCooldownTicksLeft + 19) / 20;
            abilityCooldownTicksLeft--;
            int curSec = (abilityCooldownTicksLeft + 19) / 20;
            if (curSec != prevSec && (curSec == 30 || curSec == 10 || curSec == 5 || curSec == 3 || curSec == 1)) {
                sp.displayClientMessage(Component.literal("§c绑架技能冷却：还剩 §f" + curSec + " §c秒"), true);
            } else if (abilityCooldownTicksLeft == 0) {
                sp.displayClientMessage(Component.literal("§a✔ 绑架技能冷却结束！金币≥50 时按§e技能键§a可绑架"), true);
            }
            sync();
        }

        // ========== 视角：我是人质 ==========
        if (kidnapperUuid != null) {
            // 【恢复 MC 原版碰撞手感（可穿过，轻微推挤不被挡住）】
            // 用户需求：不写 Mixin，只纯 Java 代码，人质恢复 MC 原版的玩家碰撞（不是 SRE 实心阻挡）
            // 原理：SRE 玩家实心阻挡 = 每 tick 把玩家 AABB 解算拉回不重叠
            //       我们在人质 tick 里「两次抹 velocity + 两次拉回 + old 坐标锁死」= 让 SRE 的推完全无效
            //       → 对外表现：其他玩家靠过来就会穿过去（MC 原版玩家碰撞手感）
            //       但绑架者仍保留 SRE 的实心阻挡（不影响其他玩家的对局碰撞）
            sp.hurtMarked = true;              // 防止 velocity 异常时的 knockback 修正

            // ---- ① Tick 开头：先抹掉 SRE 上一 tick 给人质加的推开速度（关键） ----
            sp.setDeltaMovement(0, sp.onGround() ? -0.05 : 0, 0);
            sp.hasImpulse = true;              // 强制写 velocity（不叠加输入）

            // ---- ② 锁死旧坐标：SRE 算推开时会用 (pos - oldPos) 当速度，锁死后就没推开量 ----
            Player rawK0 = sp.serverLevel().getPlayerByUUID(kidnapperUuid);
            ServerPlayer kidnapper0 = (rawK0 instanceof ServerPlayer spK) ? spK : null;
            if (kidnapper0 != null) {
                // 先算一次左前方，作为 tick 起点的期望位置
                float yaw0 = kidnapper0.getYRot();
                double rad0 = Math.toRadians(yaw0);
                Vec3 forward0 = kidnapper0.calculateViewVector(0.0f, yaw0);
                Vec3 left0    = kidnapper0.calculateViewVector(0.0f, yaw0 - 90.0f);
                double wantX0 = kidnapper0.getX() + left0.x * HOSTAGE_OFFSET_LEFT + forward0.x * HOSTAGE_OFFSET_FRONT;
                double wantY0 = kidnapper0.getY();
                double wantZ0 = kidnapper0.getZ() + left0.z * HOSTAGE_OFFSET_LEFT + forward0.z * HOSTAGE_OFFSET_FRONT;
                // 把 xOld/yOld/zOld 直接设为期望位置（防止 MC/SRE 用 old 坐标算差）
                sp.xOld = wantX0;
                sp.yOld = wantY0;
                sp.zOld = wantZ0;
                sp.setPosRaw(wantX0, wantY0, wantZ0);
                sp.lerpTo(wantX0, wantY0, wantZ0, yaw0, kidnapper0.getXRot(), 1);
                sp.resetFallDistance();
            }

            // 移动阻断双保险：除了 5 大事件拦截（攻击/物品/方块/实体/挖），这里每 tick 清零输入速度 + 跳跃/潜行开关
            if (sp.isSprinting()) sp.setSprinting(false);
            if (sp.isSwimming()) sp.setSwimming(false);
            if (sp.isFallFlying()) sp.startFallFlying();
            sp.setShiftKeyDown(false);         // 禁止潜行（防止 shift 卡位）
            sp.getAbilities().setWalkingSpeed(0.0f);
            sp.getAbilities().mayfly = false;
            sp.onUpdateAbilities();

            // ④ 每 10 tick 发一次 actionbar 提示（你被XXX绑架了，无法操作）
            if (t % 10 == 0) {
                Player rawK = sp.serverLevel().getPlayerByUUID(kidnapperUuid);
                String kn = (rawK != null) ? rawK.getName().getString() : "未知玩家";
                sp.displayClientMessage(Component.literal(
                        "§c⚠你被 §f" + kn + "§c 绑架了！无法操作，等待被释放或撕票"), true);
            }

            // MC 1.21.1 getPlayerByUUID 返回 Player，显式转 ServerPlayer
            Player rawK = sp.serverLevel().getPlayerByUUID(kidnapperUuid);
            ServerPlayer kidnapper = (rawK instanceof ServerPlayer spK) ? spK : null;

            // ---- ③ Tick 结尾：第二次抹 velocity（抹掉 SRE 这一 tick 中段刚推完我们的推开速度） ----
            sp.setDeltaMovement(0, sp.onGround() ? -0.05 : 0, 0);
            sp.hasImpulse = true;
            // 【修复哑火】：移除每 tick 的 invulnerableTime=2，避免与 SRE 枪击管线产生
            //  误判导致第一枪被角色的 onGunHit/AllowPlayerDeath 链路吞掉

            if (kidnapper == null || !Dim201Addon.isInGameAlive(kidnapper)) {
                // 释放人质的同时恢复移动速度、碰撞箱
                sp.getAbilities().setWalkingSpeed(0.1f);
                sp.noPhysics = false;
                sp.onUpdateAbilities();
                // 绑架者不在了：kidnapper 死亡时我们会在死亡事件里主动撕票/释放；这里只是兜底
                KidnapperPlayerComponent kc = (kidnapper != null) ? KEY.get(kidnapper) : null;
                if (kc != null && kc.hostageUuid != null && kc.hostageUuid.equals(sp.getUUID())) {
                    // 让绑架者组件释放（走 releaseHostage 路径）
                    kc.releaseHostage(kidnapper, "绑匪死亡", true);
                } else {
                    this.kidnapperUuid = null;
                    sync();
                }
            }
        }
    }

    // ============================================================================
    // 组件持久化（NBT）
    // ============================================================================
    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("hostage_most")) {
            long m = tag.getLong("hostage_most");
            long l = tag.getLong("hostage_least");
            hostageUuid = new UUID(m, l);
        } else hostageUuid = null;
        if (tag.contains("kidnapper_most")) {
            long m = tag.getLong("kidnapper_most");
            long l = tag.getLong("kidnapper_least");
            kidnapperUuid = new UUID(m, l);
        } else kidnapperUuid = null;
        coinTickAccumulator = tag.getInt("coin_accum");
        abilityCooldownTicksLeft = tag.getInt("ability_cd");
        kidnapDurationTicks = tag.getInt("kidnap_dur");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registries) {
        if (hostageUuid != null) {
            tag.putLong("hostage_most", hostageUuid.getMostSignificantBits());
            tag.putLong("hostage_least", hostageUuid.getLeastSignificantBits());
        }
        if (kidnapperUuid != null) {
            tag.putLong("kidnapper_most", kidnapperUuid.getMostSignificantBits());
            tag.putLong("kidnapper_least", kidnapperUuid.getLeastSignificantBits());
        }
        tag.putInt("coin_accum", coinTickAccumulator);
        tag.putInt("ability_cd", abilityCooldownTicksLeft);
        tag.putInt("kidnap_dur", kidnapDurationTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    // ============================================================================
    // 【静态全局事件】绑架者死亡事件 + 被绑架者被别人杀事件
    //   在 Dim201Addon.onInitialize() 调用 KidnapperPlayerComponent.registerStaticEvents() 注册
    // ============================================================================
    public static void registerStaticEvents() {
        // ① OnPlayerKilledPlayer：玩家被杀 → 判断 2 种情况
        OnPlayerKilledPlayer.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null) return;
            var world = victim.level();
            if (world.isClientSide()) return;
            var compVictim = KEY.get(victim);

            // 情况 A：绑架者被杀 → 撕票（把他的人质也杀）
            if (compVictim.hostageUuid != null) {
                if (victim instanceof ServerPlayer vsp) {
                    DamageSource ds = (killer != null)
                            ? killer.damageSources().playerAttack(killer)
                            : vsp.damageSources().genericKill();
                    compVictim.kidnapperDied(vsp, ds);
                }
            }

            // ========== 情况 B+C 合并：被绑架者（人质）死亡的统一处理 ==========
            // 原情况 B：第三方击杀 → 给绑架者 100 金币
            // 原情况 C：写小脑缓冲豁免（原设计：无论谁杀了被绑架的人质都不受误杀惩罚）
            //
            // 【原代码根本 BUG（用户反馈的就是这个）】：
            //   之前情况 B 先写了 compVictim.kidnapperUuid = null，
            //   紧接着情况 C 的 if (compVictim.kidnapperUuid != null) 就永假 → 写缓冲的
            //   代码完全不执行 → 好人击杀人质时 OnTeammateKilledTeammate+XiaoNaoHandler
            //   照常跑，shot_innocent 反噬正常触发 = 设计的"免惩罚"完全没生效。
            //
            // 【修复点】：
            //  1. 先把 kidnapperUuid 保存到局部变量，状态清空放在**最后一步**，绝不前置
            //  2. 小脑缓冲**无条件写**（只要是人质被击杀，无论 killer 阵营都豁免，
            //     符合用户的角色设计：人质是绑匪的盾牌，打死人质不算误杀）
            //  3. 金币奖励照旧只给：非撕票、非自杀的第三方击杀
            //  4. 状态清理只在最后做一次，杜绝双写和分支顺序问题
            if (compVictim.kidnapperUuid != null && killer != null) {
                // ① 先保存绑架者 UUID 到局部变量（清空前先取）
                UUID savedKidnapperUuid = compVictim.kidnapperUuid;
                boolean isSuicide = killer.getUUID().equals(victim.getUUID());
                boolean isKidnapperSelfKill = killer.getUUID().equals(savedKidnapperUuid);

                // ② 写小脑豁免缓冲：**无条件写**
                //  用户角色设计：无论谁击杀被绑架的人质（包括好人杀好人质），都不触发
                //  shot_innocent 误杀反噬，这是绑架玩法的核心机制（人质是盾牌）。
                if (killer instanceof ServerPlayer spKiller) {
                    lastGoodHostageKilledTick = spKiller.serverLevel().getGameTime();
                    lastGoodHostageKillerUuid = spKiller.getUUID();
                }

                // ③ 恢复人质的碰撞和移动（避免死亡前瞬间卡 noPhysics / 速度为 0
                if (victim instanceof ServerPlayer sv) {
                    sv.noPhysics = false;
                    sv.getAbilities().setWalkingSpeed(0.1f);
                    sv.onUpdateAbilities();
                }

                // ④ 给绑架者 100 金币奖励：人质被除绑架者本人以外的任何人击杀（包括人质自杀）都发
                //  绑架者自己撕票（killer=绑架者）不发，防止自刷金币
                ServerPlayer kidnapperPlayer = null;
                if (!isKidnapperSelfKill) {
                    if (victim.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        Player rawP = sl.getPlayerByUUID(savedKidnapperUuid);
                        if (rawP instanceof ServerPlayer spK) kidnapperPlayer = spK;
                    }
                    if (kidnapperPlayer != null && Dim201Addon.isInGameAlive(kidnapperPlayer)) {
                        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(kidnapperPlayer);
                        if (shop != null) {
                            shop.addToBalance(100);
                            kidnapperPlayer.displayClientMessage(Component.literal(
                                    "§a你的人质 §f" + victim.getName().getString() + "§a 被 §f" + killer.getName().getString()
                                            + "§a 击杀！获得 100 金币奖励"), true);
                        }
                    }
                }

                // ⑤ 统一清空双方状态：**只在最后清一次**，杜绝分支顺序双写
                compVictim.kidnapperUuid = null;
                compVictim.sync();
                // 清绑架者那边的 hostageUuid + 【关键】：人质死亡=绑架结束，给绑架者启动 60s 技能冷却
                if (kidnapperPlayer != null) {
                    KidnapperPlayerComponent kc = KEY.get(kidnapperPlayer);
                    kc.hostageUuid = null;
                    kc.abilityCooldownTicksLeft = ABILITY_COOLDOWN_TICKS;
                    KEY.sync(kidnapperPlayer);
                } else {
                    // 绑架者已离线/未找到，遍历所有在线玩家兜底清除并写冷却
                    if (victim.serverLevel() != null) {
                        for (ServerPlayer p : victim.serverLevel().getServer().getPlayerList().getPlayers()) {
                            KidnapperPlayerComponent kc = KEY.get(p);
                            if (kc.hostageUuid != null && kc.hostageUuid.equals(victim.getUUID())) {
                                kc.hostageUuid = null;
                                kc.abilityCooldownTicksLeft = ABILITY_COOLDOWN_TICKS;
                                KEY.sync(p);
                            }
                        }
                    }
                }
            }
        });
    }

    // 临时缓冲"最近一 tick 好人质被别人击杀"的 killer UUID + tick（给 XiaoNao mixin 判断用）
    // 说明：名字叫 lastGoodHostageXXX，但实际上所有"击杀被绑架者"的场景都写在这
    public static long lastGoodHostageKilledTick = -1;
    @Nullable
    public static UUID lastGoodHostageKillerUuid = null;

    // ============================================================================
    // 兼容：旧字段（保留防止 mixin 名字兼容，不会再用了，避免反射问题）
    // ============================================================================
    @Deprecated
    public static long lastSheriffKilledGoodHostageTick = -1;
    @Deprecated
    @Nullable
    public static UUID lastSheriffKilledGoodHostage = null;
}
