package com.dim201.dim201addon.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 山姥的菜刀投掷实体
 *
 * <p>参考 SRE 的 {@code ThrowingKnifeEntity} 实现：
 * <ul>
 *   <li>继承 {@link AbstractArrow}（飞刀/箭矢通用物理逻辑）</li>
 *   <li>无重力（直线飞行），最大存活 8 秒（160 tick）</li>
 *   <li>击中玩家后调用 {@link GameUtils#forceKillPlayer} 强制击杀（无视护盾/死门豁免）</li>
 *   <li>与飞刀不同：每次使用消耗 1 张卡牌（由 YamabukiCleaverCard 控制）</li>
 * </ul>
 */
public class CleaverEntity extends AbstractArrow {

    /** 记录是否已命中目标（保证只击杀1名玩家） */
    private boolean hasHit = false;

    public CleaverEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public CleaverEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity shooter,
            Level level, ItemStack itemStack) {
        super(entityType, shooter, level, itemStack, null);
        this.setNoGravity(true);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && Math.random() < 0.25) {
            level().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
        // 8 秒后自动销毁（菜刀不需要飞得太远）
        if (this.tickCount > 20 * 8) {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        // 保证只击杀1名玩家：命中一次后标记 hasHit，后续命中忽略
        if (this.hasHit) return;
        if (getOwner() == null) return;

        if (entityHitResult.getEntity() instanceof ServerPlayer victim) {
            if (victim.getUUID().equals(getOwner().getUUID())) return;
            if (!(getOwner() instanceof ServerPlayer killer)) return;

            this.hasHit = true;

            Vec3 loc = entityHitResult.getLocation();
            ServerLevel sl = victim.serverLevel();
            // 命中粒子效果
            sl.sendParticles(ParticleTypes.CRIT, loc.x, loc.y + 1.25f, loc.z, 10, 0.3, 0.3, 0.3, 0.15);
            // 命中音效（所有玩家可听）
            sl.players().forEach(p -> sl.playSound(p, loc.x, loc.y, loc.z,
                    SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 1.0f));
            // 死亡原因：使用菜刀物品的注册 id，SRE 击杀记录会显示为"被山姥的菜刀击杀"
            ResourceLocation deathReason = com.dim201.dim201addon.Dim201Addon.id("cleaver_hit");
            // 好人用菜刀击杀好人 → 触发小脑惩罚（能力卡白名单外的死因需手动补）
            com.dim201.dim201addon.item.card.CardTeamKillPunisher.applyIfApplicable(victim, killer, deathReason);
            // forceKillPlayer：无视一切护盾/死亡豁免（调酒师护甲、弱甲、念动护盾、
            // AfterShieldAllowPlayerDeath 死门抗性等全部跳过），命中即必杀
            GameUtils.forceKillPlayer(victim, true, killer, deathReason);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return com.dim201.dim201addon.item.Dim201ModItems.YAMABUKI_CLEAVER.getDefaultInstance();
    }
}
