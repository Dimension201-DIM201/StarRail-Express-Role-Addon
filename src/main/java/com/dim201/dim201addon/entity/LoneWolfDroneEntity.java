package com.dim201.dim201addon.entity;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.init.ModEntities;
import com.dim201.dim201addon.packet.LoneWolfDroneInputC2SPacket;
import com.dim201.dim201addon.util.DoorPenetrationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * 独狼「隐形无人机」实体。
 *
 * <p>参考赤蛮奇飞头的远程驾驶模式，但无人机：
 * <ul>
 *   <li>始终隐形（所有玩家不可见）</li>
 *   <li>0.3×0.3 碰撞箱，可碰撞方块但不穿墙</li>
 *   <li>不可被攻击（hurt 返回 false）</li>
 *   <li>无距离限制，60 秒后由组件自动销毁</li>
 *   <li>可发射射线标记玩家（左键），发射后自动销毁</li>
 * </ul>
 */
public class LoneWolfDroneEntity extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(LoneWolfDroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(LoneWolfDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(LoneWolfDroneEntity.class, EntityDataSerializers.FLOAT);

    /** 飞行速度：2 倍于原速，便于快速侦察 */
    private static final double FLY_SPEED = 0.44;

    private static final EntityDimensions DRONE_DIMENSIONS =
            EntityDimensions.scalable(0.3F, 0.3F).withEyeHeight(0.3F);

    public LoneWolfDroneEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setSilent(true);
        this.setPersistenceRequired();
        this.noPhysics = false;
        this.setInvisible(true);
    }

    public LoneWolfDroneEntity(Level level, ServerPlayer owner, double x, double y, double z) {
        this(ModEntities.LONE_WOLF_DRONE, level);
        this.setOwnerUUID(owner.getUUID());
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_YAW, 0.0F);
        builder.define(DATA_PITCH, 0.0F);
    }

    public float getSyncedYaw() { return this.entityData.get(DATA_YAW); }
    public float getSyncedPitch() { return this.entityData.get(DATA_PITCH); }

    @Override
    protected void registerGoals() {}

    public void setOwnerUUID(UUID uuid) { this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid)); }
    public Optional<UUID> getOwnerUUID() { return this.entityData.get(DATA_OWNER); }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) { return DRONE_DIMENSIONS; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    public boolean isInWall() { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    /** 始终隐形 */
    @Override
    public boolean isInvisible() { return true; }

    @Override
    public void turn(double yaw, double pitch) {
        this.setYRot(this.getYRot() + (float) yaw);
        this.setXRot(net.minecraft.util.Mth.clamp(this.getXRot() + (float) pitch, -90.0F, 90.0F));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    /** 不落盘（手动标记，配合 {@link #requiresCustomPersistence()}=false） */
    @Override
    public boolean save(CompoundTag tag) { return false; }

    /** 禁用 Mob 默认的持久化需求：防止区块卸载时无人机消失导致探测范围错乱 */
    @Override
    public boolean requiresCustomPersistence() { return false; }

    /** 网络包暂存：drive() 由网络线程调用，只暂存输入，tick 里统一处理 */
    private int pendingBits = 0;
    private float pendingYaw = 0;
    private float pendingPitch = 0;
    private boolean hasPendingInput = false;

    /** 网络线程入口：只暂存，不直接改状态（避免与主线程 move() 竞态） */
    public void drive(int bits, float yaw, float pitch) {
        this.pendingBits = bits;
        this.pendingYaw = yaw;
        this.pendingPitch = pitch;
        this.hasPendingInput = true;
    }

    /** 主线程 tick 中应用输入 */
    private void applyPendingInput() {
        if (!hasPendingInput) return;
        hasPendingInput = false;

        this.setYRot(pendingYaw);
        this.yRotO = pendingYaw;
        this.setXRot(pendingPitch);
        this.xRotO = pendingPitch;
        this.yBodyRot = pendingYaw;
        this.yBodyRotO = pendingYaw;
        this.yHeadRot = pendingYaw;
        this.yHeadRotO = pendingYaw;
        this.entityData.set(DATA_YAW, pendingYaw);
        this.entityData.set(DATA_PITCH, pendingPitch);

        Vec3 look = Vec3.directionFromRotation(pendingPitch, pendingYaw);
        Vec3 right = Vec3.directionFromRotation(0.0F, pendingYaw + 90.0F);

        Vec3 vel = Vec3.ZERO;
        if ((pendingBits & LoneWolfDroneInputC2SPacket.BIT_FORWARD) != 0) vel = vel.add(look);
        if ((pendingBits & LoneWolfDroneInputC2SPacket.BIT_BACK) != 0) vel = vel.subtract(look);
        if ((pendingBits & LoneWolfDroneInputC2SPacket.BIT_LEFT) != 0) vel = vel.subtract(right);
        if ((pendingBits & LoneWolfDroneInputC2SPacket.BIT_RIGHT) != 0) vel = vel.add(right);
        if ((pendingBits & LoneWolfDroneInputC2SPacket.BIT_UP) != 0) vel = vel.add(0, 1, 0);
        if ((pendingBits & LoneWolfDroneInputC2SPacket.BIT_DOWN) != 0) vel = vel.add(0, -1, 0);

        if (vel.lengthSqr() > 1.0E-6) {
            vel = vel.normalize().scale(FLY_SPEED);
        }
        this.setDeltaMovement(vel);
    }

    /** 隐形无人机不可被攻击 */
    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public void tick() {
        // 应用网络线程暂存的输入（必须在 super.tick() 之前，否则 aiStep 会读旧速度）
        if (!this.level().isClientSide) {
            applyPendingInput();
        }
        super.tick();
        if (this.level().isClientSide) return;
        if (this.isRemoved()) return;

        // 主人离线/死亡/非当前维度/技能关闭 → 自毁
        this.getOwnerUUID().ifPresentOrElse(uuid -> {
            Player owner = this.level().getPlayerByUUID(uuid);
            boolean valid = owner instanceof ServerPlayer sp && sp.isAlive()
                    && sp.serverLevel() == this.level();
            if (!valid) { this.discard(); return; }
            LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable((ServerPlayer) owner);
            if (comp == null || !comp.droneActive) { this.discard(); }
        }, () -> this.discard());

        // 速度清零放在 aiStep 之后（super.tick 内部已执行 move），
        // 防止网络包间隔期持续漂移
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void aiStep() {
        Vec3 vel = this.getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) return;

        // 手动步进移动：遇门穿过、遇墙停止（参考灵梦封魔针的射线步进逻辑）
        double totalDist = vel.length();
        Vec3 dir = vel.normalize();
        double stepSize = 0.1;
        int steps = (int) Math.ceil(totalDist / stepSize);

        for (int i = 0; i < steps; i++) {
            double step = Math.min(stepSize, totalDist - i * stepSize);
            Vec3 next = this.position().add(dir.scale(step));

            // 检查碰撞箱在新位置与方块碰撞形状的实际交集（非完整方块只阻挡重叠部分）
            net.minecraft.world.phys.AABB entityBox = this.getBoundingBox().move(next.subtract(this.position()));
            var shapeBox = net.minecraft.world.phys.shapes.Shapes.create(entityBox);
            var minPos = BlockPos.containing(entityBox.minX, entityBox.minY, entityBox.minZ);
            var maxPos = BlockPos.containing(entityBox.maxX, entityBox.maxY, entityBox.maxZ);

            boolean blocked = false;
            for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
                var state = this.level().getBlockState(pos);
                if (state.isAir()) continue;
                if (DoorPenetrationHelper.isDoorBlock(this.level(), pos, state)) continue;
                var collideShape = state.getCollisionShape(this.level(), pos);
                if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(collideShape, shapeBox,
                        net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                    blocked = true;
                    break;
                }
            }

            if (blocked) break;
            this.setPos(next.x, next.y, next.z);
        }

        this.setDeltaMovement(Vec3.ZERO);
    }
}
