package com.dim201.dim201addon;

import com.dim201.dim201addon.entity.LoneWolfDroneEntity;
import com.dim201.dim201addon.mixin.CooldownInstanceAccessor;
import com.dim201.dim201addon.mixin.ItemCooldownsAccessor;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 独狼玩家组件 — 隐形无人机技能状态管理。
 *
 * <p>技能机制：
 * <ul>
 *   <li>按 G 消耗 100 金币部署隐形无人机，本体冻结，相机绑定无人机</li>
 *   <li>再按 G 在「操控无人机」和「操控本体」之间切换（无人机悬停不动）</li>
 *   <li>无人机自动探测周围 8 格存活玩家并持续高亮（仅独狼视角）</li>
 *   <li>无人机部署 60s 后自动销毁，销毁后技能进入 60s 冷却</li>
 *   <li>冷却走 {@link SREAbilityPlayerComponent}</li>
 * </ul>
 */
public class LoneWolfComponent implements RoleComponent {

    /** 无人机持续时间（60s） */
    public static final int DRONE_DURATION_TICKS = 60 * 20;
    /** 技能冷却（60s） */
    public static final int COOLDOWN_TICKS = 60 * 20;
    /** 部署费用 */
    public static final int DEPLOY_COST = 100;
    /** 无人机自动探测半径（格） */
    public static final double DETECT_RADIUS = 8.0;
    /** 清算时间触发门槛：开局人数下限 */
    public static final int SETTLEMENT_MIN_START_COUNT = 10;
    /** 清算时间对局倒计时（tick）：2 分钟 */
    public static final int SETTLEMENT_COUNTDOWN_TICKS = 2 * 60 * 20;
    /** 禁用效果续期时长 */
    private static final int BAN_RENEW_TICKS = 10;
    /** 本体允许漂移最大平方距离 */
    private static final double MAX_DRIFT_SQR = 1.5 * 1.5;

    private final Player player;

    // ===== 死亡标记（原有） =====
    public boolean isDead = false;

    // ===== 清算时间状态 =====
    /** 清算时间激活（独狼存活且存活人数≤开局1/5），无人机锁定、全图高亮、手枪冷却2s */
    public boolean settlementActive = false;

    // ===== 无人机状态 =====
    /** 无人机是否已部署 */
    public boolean droneActive = false;
    /** 当前是否正在操控无人机（false = 操控本体） */
    public boolean controllingDrone = false;
    /** 无人机实体网络 id，-1 表示无 */
    public int droneEntityId = -1;
    /** 无人机自动销毁的游戏时刻（仅服务端有效） */
    public long droneDestroyTick = 0;

    /** 客户端同步来的无人机坐标（探测锚点，比服务端实体位置更精确） */
    public double clientDroneX = 0, clientDroneY = 0, clientDroneZ = 0;

    // ===== 范围高亮状态 =====
    /** 无人机周围 8 格探测到的玩家 UUID 集合（无人机存活期间持续高亮） */
    public final Set<UUID> detectedPlayerUuids = new HashSet<>();
    /** 探测到的玩家名缓存（客户端无需加载实体即可显示 HUD） */
    public final java.util.Map<UUID, String> detectedPlayerNames = new java.util.concurrent.ConcurrentHashMap<>();

    // ===== 服务端锚点 =====
    private double anchorX, anchorY, anchorZ;

    public LoneWolfComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) { return this.player == sp; }

    @Override
    public void init() {
        teardownDrone();
        resetState();
        sync();
    }

    @Override
    public void clear() {
        teardownDrone();
        resetState();
        sync();
    }

    private void teardownDrone() {
        if (!droneActive) return;
        if (player instanceof ServerPlayer sp) {
            discardDrone(sp);
            sp.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void resetState() {
        isDead = false;
        settlementActive = false;
        droneActive = false;
        controllingDrone = false;
        droneEntityId = -1;
        droneDestroyTick = 0;
        detectedPlayerUuids.clear();
        anchorX = anchorY = anchorZ = 0;
    }

    public void sync() {
        ModComponents.LONE_WOLF.sync(this.player);
    }

    // ====================== 技能控制 ======================

    /** 部署无人机（消耗 100 金币） */
    public void deployDrone(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) {
            sp.displayClientMessage(Component.literal("§7当前无法使用「无人机」"), true);
            return;
        }
        if (!sp.isAlive()) return;

        // 清算时间期间技能锁定
        if (settlementActive) {
            sp.displayClientMessage(Component.literal(
                    "§c【清算时间】期间无法部署无人机"), true);
            return;
        }

        int cooldown = SREAbilityPlayerComponent.KEY.get(sp).cooldown;
        if (cooldown > 0) {
            sp.displayClientMessage(Component.literal(
                    "§c「无人机」冷却中 §7剩余 §e" + (cooldown / 20) + "s"), true);
            return;
        }
        if (droneActive) return;

        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < DEPLOY_COST) {
            sp.displayClientMessage(Component.literal(
                    "§c金币不足，部署无人机需要 " + DEPLOY_COST + " 金币"), true);
            return;
        }

        anchorX = sp.getX();
        anchorY = sp.getY();
        anchorZ = sp.getZ();

        LoneWolfDroneEntity drone = new LoneWolfDroneEntity(
                sp.serverLevel(), sp, sp.getX(), sp.getEyeY(), sp.getZ());
        drone.setYRot(sp.getYRot());
        drone.setXRot(sp.getXRot());
        sp.serverLevel().addFreshEntity(drone);

        // 实体生成成功后才扣费，避免生成异常导致白扣金币
        shop.balance -= DEPLOY_COST;
        shop.sync();

        droneEntityId = drone.getId();
        droneActive = true;
        controllingDrone = true;
        droneDestroyTick = sp.level().getGameTime() + DRONE_DURATION_TICKS;

        sp.displayClientMessage(Component.literal(
                "§4§l「无人机」 §7已部署 §8(-" + DEPLOY_COST + " 金币)"), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
        sync();
    }

    /** 切换操控目标（无人机 ↔ 本体） */
    public void toggleControl(ServerPlayer sp) {
        if (!droneActive) return;
        controllingDrone = !controllingDrone;

        if (controllingDrone) {
            // 切换回无人机：重新锚定本体位置
            anchorX = sp.getX();
            anchorY = sp.getY();
            anchorZ = sp.getZ();
            sp.displayClientMessage(Component.literal("§4§l「无人机」 §7已切换到无人机视角"), true);
        } else {
            sp.displayClientMessage(Component.literal("§4§l「无人机」 §7已切换回本体"), true);
        }
        sync();
    }

    /** 销毁无人机：超时/死亡/清算触发/对局结束 */
    public void destroyDrone(ServerPlayer sp, boolean startCooldown) {
        if (!droneActive) return;
        discardDrone(sp);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0;
        droneActive = false;
        controllingDrone = false;
        droneEntityId = -1;
        droneDestroyTick = 0;
        detectedPlayerUuids.clear();

        if (startCooldown) {
            SREAbilityPlayerComponent.KEY.get(sp).setCooldown(COOLDOWN_TICKS);
        }
        sync();
    }

    /** 本体死亡清理（幂等：无变更时不发同步包，非独狼死亡调用为无害空操作） */
    public void cleanupOnDeath(ServerPlayer sp) {
        boolean hadDrone = droneActive;
        boolean hadDetected = !detectedPlayerUuids.isEmpty();
        boolean wasSettlement = settlementActive;

        if (hadDrone) {
            discardDrone(sp);
            droneActive = false;
            controllingDrone = false;
            droneEntityId = -1;
            droneDestroyTick = 0;
        }
        settlementActive = false;
        detectedPlayerUuids.clear();

        if (hadDrone || hadDetected || wasSettlement) sync();
    }

    // ====================== 清算时间 ======================

    /**
     * 进入【清算时间】：
     * 独狼存活且开局人数≥10、存活人数≤开局1/5 时触发。
     * 清理所有无人机（操控中立刻回归本体）并锁定技能，
     * 全图玩家对独狼高亮，手枪冷却降为 2s（每 tick 钳制，见 {@link #clampRevolverCooldown}）。
     */
    public void activateSettlement(ServerPlayer sp) {
        if (settlementActive) return;
        settlementActive = true;

        // 清理无人机（正在操控则立刻回归本体）
        destroyDrone(sp, false);

        // 对局倒计时调整为 2 分钟（setTime 内部会 sync 到客户端）
        SREGameTimeComponent timeComp = SREGameTimeComponent.KEY.get(sp.serverLevel());
        if (timeComp != null) {
            timeComp.setTime(SETTLEMENT_COUNTDOWN_TICKS);
        }

        // 全员 Title 播报（与神明吞噬者时刻一致的全局提示方式）
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            SRENetworkMessageUtils.sendTitle(p,
                    Component.literal("清算时间").withStyle(ChatFormatting.DARK_RED));
            SRENetworkMessageUtils.sendSubtitle(p,
                    Component.literal("狼嗅到了血味……").withStyle(ChatFormatting.RED));
        }

        sp.displayClientMessage(Component.literal(
                "§4§l【清算时间】 §7狼嗅到了血味——全图尽收眼底，手枪冷却缩短"), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.6f, 1.5f);
        sync();
    }

    /** 每 20 tick 检查一次清算时间触发条件 */
    private void checkSettlementTrigger(ServerPlayer sp, SREGameWorldComponent gw) {
        int startCount = gw.getStartingPlayerCount();
        if (startCount < SETTLEMENT_MIN_START_COUNT) return;

        int aliveCount = 0;
        for (ServerPlayer p : sp.serverLevel().players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p)) aliveCount++;
        }
        // 存活人数 ≤ 开局人数 / 5
        if (aliveCount <= startCount / 5) {
            activateSettlement(sp);
        }
    }

    /**
     * 清算时间手枪冷却钳制：每 tick 读取制式左轮的冷却表，剩余冷却
     * 超过 2s 则重写为 2s。
     *
     * <p>不走 OnRevolverUsed 事件——事件时序不可靠（制式左轮可能不触发），
     * 每 tick 钳制只依赖冷却表状态，与开火路径解耦，必然生效。
     */
    private void clampRevolverCooldown(ServerPlayer sp) {
        Item revolver = BuiltInRegistries.ITEM.get(LoneWolfRole.STANDARD_REVOLVER);
        if (revolver == null || revolver == Items.AIR) return;

        ItemCooldowns cds = sp.getCooldowns();
        Object inst = ((ItemCooldownsAccessor) cds).dim201addon$cooldowns().get(revolver);
        if (!(inst instanceof CooldownInstanceAccessor acc)) return;

        int now = ((ItemCooldownsAccessor) cds).dim201addon$tickCount();
        int remain = acc.dim201addon$endTime() - now;
        if (remain > LoneWolfRole.SETTLEMENT_REVOLVER_COOLDOWN_TICKS) {
            cds.addCooldown(revolver, LoneWolfRole.SETTLEMENT_REVOLVER_COOLDOWN_TICKS);
        }
    }

    // ====================== 每 tick ======================

    public void tick(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;

        long now = sp.level().getGameTime();

        // 清算时间触发检查（每秒）
        if (!settlementActive && now % 20 == 0) {
            checkSettlementTrigger(sp, gw);
        }

        // 清算时间：每 tick 钳制制式左轮冷却 ≤ 2s
        if (settlementActive) {
            clampRevolverCooldown(sp);
        }

        if (!droneActive) return;

        // 无人机超时自动销毁
        if (now >= droneDestroyTick) {
            sp.displayClientMessage(Component.literal(
                    "§4§l「无人机」 §7电量耗尽，自动销毁"), false);
            destroyDrone(sp, true);
            return;
        }

        // 无人机实体有效性校验（每秒）：被外部移除时安全销毁
        if (now % 20 == 0 && findDrone(sp) == null) {
            destroyDrone(sp, true);
            return;
        }

        // 无人机范围探测：每 5 tick 刷新周围 8 格存活玩家高亮集合
        if (now % 5 == 0) {
            updateDetectedPlayers(sp);
        }

        // 操控无人机时冻结本体
        if (controllingDrone) {
            sp.setDeltaMovement(Vec3.ZERO);
            sp.fallDistance = 0;
            if (sp.distanceToSqr(anchorX, anchorY, anchorZ) > MAX_DRIFT_SQR) {
                sp.teleportTo(anchorX, anchorY, anchorZ);
            }
            sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, BAN_RENEW_TICKS, 0, true, false, false));
            sp.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, BAN_RENEW_TICKS, 0, true, false, false));
        }
    }

    // ====================== 输入包入口 ======================

    /** 收到客户端驾驶输入包（仅移动，探测高亮由服务端 tick 自动完成） */
    public void handleDroneInput(ServerPlayer sp, int bits, float yaw, float pitch, boolean fire,
                                 double x, double y, double z) {
        if (!droneActive || !controllingDrone) return;
        // 缓存客户端无人机坐标（探测锚点以客户端为准）
        clientDroneX = x;
        clientDroneY = y;
        clientDroneZ = z;
        LoneWolfDroneEntity drone = findDrone(sp);
        if (drone == null) return;
        drone.drive(bits, yaw, pitch);
    }

    /**
     * 无人机范围探测：扫描周围 8 格存活玩家，加入/移出高亮集合。
     *
     * <p>集合内容仅在发生变化时同步（detectedPlayerUuids 引用不变、内容比对），
     * 探测半径小且每 5 tick 一次，开销可忽略。
     */
    private void updateDetectedPlayers(ServerPlayer sp) {
        // 以客户端同步坐标为探测锚点（比服务端实体位置更精确，避免穿门等同步偏差）
        double cx = clientDroneX, cy = clientDroneY, cz = clientDroneZ;

        Set<UUID> nowDetected = new HashSet<>();
        for (Player p : sp.level().players()) {
            if (!(p instanceof ServerPlayer target) || target == sp) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target)) continue;
            double dx = target.getX() - cx, dy = target.getY() - cy, dz = target.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz > DETECT_RADIUS * DETECT_RADIUS) continue;
            nowDetected.add(target.getUUID());
        }

        // 发现新目标：提示独狼
        for (UUID uuid : nowDetected) {
            if (!detectedPlayerUuids.contains(uuid)) {
                Player found = sp.level().getPlayerByUUID(uuid);
                if (found != null) {
                    sp.displayClientMessage(Component.literal(
                            "§4§l「无人机」 §7发现目标：§c" + found.getDisplayName().getString()), true);
                }
            }
        }

        if (!nowDetected.equals(detectedPlayerUuids)) {
            detectedPlayerUuids.clear();
            detectedPlayerUuids.addAll(nowDetected);
            sync();
        }
    }

    // ====================== 实体查找/清理 ======================

    @Nullable
    private LoneWolfDroneEntity findDrone(ServerPlayer sp) {
        if (droneEntityId < 0) return null;
        Entity ent = sp.level().getEntity(droneEntityId);
        return ent instanceof LoneWolfDroneEntity drone ? drone : null;
    }

    private void discardDrone(ServerPlayer sp) {
        LoneWolfDroneEntity drone = findDrone(sp);
        if (drone != null && !drone.isRemoved()) {
            drone.discard();
        }
    }

    // ====================== 序列化 ======================

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        isDead = tag.getBoolean("is_dead");
        // 技能态不落盘
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("is_dead", isDead);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        isDead = tag.getBoolean("is_dead");
        settlementActive = tag.getBoolean("SettlementActive");
        droneActive = tag.getBoolean("DroneActive");
        controllingDrone = tag.getBoolean("ControllingDrone");
        droneEntityId = tag.getInt("DroneId");
        droneDestroyTick = tag.getLong("DroneDestroyTick");

        detectedPlayerUuids.clear();
        net.minecraft.nbt.ListTag list = tag.getList("DetectedUuids", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            detectedPlayerUuids.add(net.minecraft.nbt.NbtUtils.loadUUID(list.get(i)));
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("is_dead", isDead);
        tag.putBoolean("SettlementActive", settlementActive);
        tag.putBoolean("DroneActive", droneActive);
        tag.putBoolean("ControllingDrone", controllingDrone);
        tag.putInt("DroneId", droneEntityId);
        tag.putLong("DroneDestroyTick", droneDestroyTick);

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (UUID uuid : detectedPlayerUuids) {
            list.add(net.minecraft.nbt.NbtUtils.createUUID(uuid));
        }
        tag.put("DetectedUuids", list);
    }
}
