package com.dim201.dim201addon.entity;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import com.dim201.dim201addon.init.ModEntities;
import com.dim201.dim201addon.packet.SeikibankiFlyInputC2SPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * 赤蛮奇「飞头」技能的头颅实体。
 *
 * <p>设计参考操纵师（Manipulator）的远程驾驶模式：
 * <ul>
 *   <li>玩家本体冻结在原地，相机通过 {@code Minecraft.setCameraEntity} 绑定到本实体</li>
 *   <li>客户端每 tick 发送按键输入包，服务端 {@link #drive} 权威驱动头颅飞行（带碰撞，不会穿墙）</li>
 *   <li>无重力、无 AI、0.5×0.5 碰撞箱；飞行速度约为原版飞行的 1/3</li>
 *   <li>距技能锚点超过 20 格时截断回边缘</li>
 *   <li>被攻击（任意伤害源）→ 通知主人组件触发「击落回归」（60s 失明 + 冷却）</li>
 * </ul>
 */
public class SeikibankiHeadEntity extends PathfinderMob {

    /** 主人 UUID（同步给客户端，渲染时取主人皮肤） */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(SeikibankiHeadEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 头颅朝向（yaw/pitch），用 DataAccessor 同步，避免 LivingEntity 的 MoveEntityPacket 不同步 pitch */
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(SeikibankiHeadEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(SeikibankiHeadEntity.class, EntityDataSerializers.FLOAT);

    /** 飞行速度：原版创造飞行约 0.27 格/tick，取 2/3 */
    private static final double FLY_SPEED = 0.18;

    /** 0.5×0.5 碰撞箱，眼睛高度 0.5（相机在头颅顶部，避免第一人称被模型遮挡） */
    private static final EntityDimensions HEAD_DIMENSIONS =
            EntityDimensions.scalable(0.5F, 0.5F).withEyeHeight(0.5F);

    /** 距离锚点（仅服务端，用于距离截断） */
    private double anchorX, anchorY, anchorZ;

    public SeikibankiHeadEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setSilent(true);
        this.setPersistenceRequired();
        this.noPhysics = false;
    }

    public SeikibankiHeadEntity(Level level, ServerPlayer owner, double ax, double ay, double az) {
        this(ModEntities.SEIKIBANKI_HEAD, level);
        this.setOwnerUUID(owner.getUUID());
        // 关键：必须显式 setPos，否则实体位置默认 (0,0,0) 落在未加载 chunk
        // 导致 ServerLevel 不调用 entity.tick()，1 秒后组件 tick findHead==null 触发冷却
        this.setPos(ax, ay, az);
        this.anchorX = ax;
        this.anchorY = ay;
        this.anchorZ = az;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_YAW, 0.0F);
        builder.define(DATA_PITCH, 0.0F);
    }

    public float getSyncedYaw() {
        return this.entityData.get(DATA_YAW);
    }

    public float getSyncedPitch() {
        return this.entityData.get(DATA_PITCH);
    }

    @Override
    protected void registerGoals() {
        // 无 AI：移动完全由输入包驱动
    }

    public void setOwnerUUID(UUID uuid) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(DATA_OWNER);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return HEAD_DIMENSIONS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** 覆盖窒息检测：头颅无肺，不会因卡入方块而窒息 */
    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /**
     * 覆盖 LivingEntity.turn()：原版 LivingEntity 在非鞘翅飞行时不处理旋转，
     * 导致 setCameraEntity(head) 后鼠标无法转动视角。
     * 直接实现 Entity.turn() 的逻辑（Java 不支持 super.super 调用）。
     */
    @Override
    public void turn(double yaw, double pitch) {
        this.setYRot(this.getYRot() + (float) yaw);
        this.setXRot(net.minecraft.util.Mth.clamp(this.getXRot() + (float) pitch, -90.0F, 90.0F));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    /** 不随存档保存：技能态不落盘，重登/重启后自然消失 */
    @Override
    public boolean save(CompoundTag tag) {
        return false;
    }

    /**
     * 由输入包驱动飞行（服务端权威，客户端也做 yaw/pitch 预测）。
     *
     * @param bits  按键位掩码，见 {@link SeikibankiFlyInputC2SPacket}
     * @param yaw   玩家视角偏航角
     * @param pitch 玩家视角俯仰角
     */
    public void drive(int bits, float yaw, float pitch) {
        // 服务端 yaw/pitch 跟随客户端鼠标：既设置实体字段，也通过 DataAccessor 同步给所有观察者
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setXRot(pitch);
        this.xRotO = pitch;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
        this.entityData.set(DATA_YAW, yaw);
        this.entityData.set(DATA_PITCH, pitch);

        // W/S 沿视线方向（含俯仰），A/D 水平平移，空格上升 / Shift 下降
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        // 右方向：yaw+90°（yaw=0 朝南时右=西=-X）
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);

        Vec3 vel = Vec3.ZERO;
        if ((bits & SeikibankiFlyInputC2SPacket.BIT_FORWARD) != 0) vel = vel.add(look);
        if ((bits & SeikibankiFlyInputC2SPacket.BIT_BACK) != 0) vel = vel.subtract(look);
        if ((bits & SeikibankiFlyInputC2SPacket.BIT_LEFT) != 0) vel = vel.subtract(right);
        if ((bits & SeikibankiFlyInputC2SPacket.BIT_RIGHT) != 0) vel = vel.add(right);
        if ((bits & SeikibankiFlyInputC2SPacket.BIT_UP) != 0) vel = vel.add(0, 1, 0);
        if ((bits & SeikibankiFlyInputC2SPacket.BIT_DOWN) != 0) vel = vel.add(0, -1, 0);

        if (vel.lengthSqr() > 1.0E-6) {
            vel = vel.normalize().scale(FLY_SPEED);
        }

        // 距离限制：超 20 格时去掉远离本体的速度分量，保留切线方向和靠近方向
        double dx = this.getX() - anchorX;
        double dy = this.getY() - anchorY;
        double dz = this.getZ() - anchorZ;
        double distSqr = dx * dx + dy * dy + dz * dz;
        double max = SeikibankiPlayerComponent.MAX_DISTANCE;
        if (distSqr > max * max && vel.lengthSqr() > 1.0E-6) {
            Vec3 away = new Vec3(dx, dy, dz).normalize();
            double proj = vel.dot(away);
            if (proj > 0) {
                // 去掉远离分量，保留切线方向（可沿圆周移动）
                vel = vel.subtract(away.scale(proj));
            }
        }

        // 设置速度，由 aiStep 中的 move() 执行实际移动（原版机制，ServerEntity 可正确同步位置）
        this.setDeltaMovement(vel);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        // 环境伤害（撞墙/窒息/掉落等）不触发击落回归，也不扣血
        if (source.getEntity() == null) return false;
        // 被实体攻击 → 主人技能「击落回归」
        this.getOwnerUUID().ifPresent(uuid -> {
            Player owner = this.level().getPlayerByUUID(uuid);
            if (owner instanceof ServerPlayer sp) {
                SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.getNullable(sp);
                if (comp != null) {
                    comp.recallByAttack(sp);
                }
            }
        });
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (this.isRemoved()) return;

        // 主人离线/死亡/非当前维度 → 自毁（正常收回由组件负责）
        this.getOwnerUUID().ifPresentOrElse(uuid -> {
            Player owner = this.level().getPlayerByUUID(uuid);
            boolean isSp = owner instanceof ServerPlayer;
            boolean alive = isSp && ((ServerPlayer) owner).isAlive();
            boolean sameLevel = isSp && ((ServerPlayer) owner).serverLevel() == this.level();
            var comp = isSp ? ModComponents.SHIKIBANKI.getNullable((ServerPlayer) owner) : null;
            boolean skillActive = comp != null && comp.skillActive;
            boolean valid = isSp && alive && sameLevel && comp != null && skillActive;
            if (!valid) {
                this.discard();
                return;
            }
            // 粒子改由客户端渲染器生成（用渲染插值位置，避免与模型错开）
        }, () -> this.discard());

        // 无重力/无 AI 下再清零一次速度，防止水/爆炸等外力产生位移
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 用 travel 执行实际移动（由 drive 设置的 deltaMovement 驱动）。
     * 不直接重写为空，否则 ServerEntity 无法通过原版移动机制检测位置变化。
     */
    @Override
    public void aiStep() {
        // travel() 对 noAi 空中实体不产生位移，直接用 move（原版 mob 移动方式）
        this.move(MoverType.SELF, this.getDeltaMovement());
    }
}
