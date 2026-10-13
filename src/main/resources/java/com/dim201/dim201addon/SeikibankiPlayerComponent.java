package com.dim201.dim201addon;

import com.dim201.dim201addon.entity.SeikibankiHeadEntity;
import com.dim201.dim201addon.role.SeikibankiRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 赤蛮奇玩家组件（远程驾驶飞头方案，参考操纵师 Manipulator）。
 *
 * <p>技能机制：
 * <ul>
 *   <li>启动：在玩家眼睛位置生成 {@link SeikibankiHeadEntity} 头颅实体，玩家本体冻结在原地。
 *       不再生成傀儡实体——玩家本体本身就是留在原地、可被攻击的「身体」</li>
 *   <li>操控：客户端 {@code setCameraEntity} 把视角绑定到头颅，每 tick 发送按键包，
 *       服务端权威驱动头颅飞行（无重力、速度≈原版飞行 1/3、有碰撞不穿墙、20 格距离截断）</li>
 *   <li>本体冻结：清零速度/摔落距离，漂移超过 1.5 格拉回锚点；
 *       持续施加 USED_BANED/INVENTORY_BANED 禁止使用物品/开背包，武器攻击由 Mixin 拦截</li>
 *   <li>头颅被攻击 → 击落回归：60s 失明 + 90s 冷却；主动再按 G 收回无失明</li>
 *   <li>本体在原地可被正常攻击/击杀（走 SRE 死亡流程），死亡即结束技能</li>
 *   <li>冷却走 {@link SREAbilityPlayerComponent}（自带服务端/客户端倒计时与同步）</li>
 * </ul>
 */
public class SeikibankiPlayerComponent implements RoleComponent {

    public static final ComponentKey<SeikibankiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "seikibanki"),
            SeikibankiPlayerComponent.class);

    /** 头颅距锚点距离上限：20 格 */
    public static final double MAX_DISTANCE = 20.0;
    /** 技能冷却时长（90s = 1800 tick） */
    public static final int COOLDOWN_TICKS = 90 * 20;
    /** 击落失明时长（60s = 1200 tick） */
    public static final int BLINDNESS_TICKS = 60 * 20;
    /** 禁用效果续期时长（tick），每 tick 续 */
    private static final int BAN_RENEW_TICKS = 10;
    /** 本体允许离锚点的最大漂移平方距离（1.5 格） */
    private static final double MAX_DRIFT_SQR = 1.5 * 1.5;

    private final Player player;

    public boolean skillActive = false;
    /** 头颅实体的网络 id（同步给客户端用于查找实体/绑定相机），-1 表示无 */
    public int headEntityId = -1;

    /** 锚点（仅服务端）：本体冻结位置/距离限制中心 */
    private double anchorX, anchorY, anchorZ;

    public SeikibankiPlayerComponent(Player player) {
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
        // 对局重置/角色重分配可能发生在技能激活期间，必须先移除头颅并解除本体冻结
        teardownIfActive();
        resetState();
        sync();
    }

    @Override
    public void clear() {
        teardownIfActive();
        resetState();
        sync();
    }

    /** 技能激活中被重置：移除头颅、解除冻结（不触发冷却/失明） */
    private void teardownIfActive() {
        if (!skillActive) return;
        if (player instanceof ServerPlayer sp) {
            discardHead(sp);
            sp.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void resetState() {
        skillActive = false;
        headEntityId = -1;
        anchorX = anchorY = anchorZ = 0;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ====================== 技能控制 ======================

    /** 启动技能：本体冻结在原地，生成可远程驾驶的头颅实体 */
    public void startSkill(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRole(sp, SeikibankiRole.SEIKIBANKI)) return;
        if (!sp.isAlive()) return;
        int cooldown = SREAbilityPlayerComponent.KEY.get(sp).cooldown;
        if (cooldown > 0) {
            sp.displayClientMessage(Component.literal(
                    "§c「飞头」冷却中 §7剩余 §e" + (cooldown / 20) + "s"), true);
            return;
        }
        if (skillActive) return;

        anchorX = sp.getX();
        anchorY = sp.getY();
        anchorZ = sp.getZ();

        // 头颅从玩家眼睛位置出发
        SeikibankiHeadEntity head = new SeikibankiHeadEntity(
                sp.serverLevel(), sp, sp.getX(), sp.getEyeY(), sp.getZ());
        head.setYRot(sp.getYRot());
        head.setXRot(sp.getXRot());
        sp.serverLevel().addFreshEntity(head);
        headEntityId = head.getId();
        skillActive = true;

        sp.displayClientMessage(Component.literal("§d§l「飞头」 §7头颅已脱离，本体留在原地"), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6f, 1.6f);
        sync();
    }

    /** 主动收回（再按 G）：无失明，进入冷却 */
    public void recallSkill(ServerPlayer sp) {
        if (!skillActive) return;
        deactivate(sp);
        SREAbilityPlayerComponent.KEY.get(sp).setCooldown(COOLDOWN_TICKS);

        sp.displayClientMessage(Component.literal("§d§l「飞头」 §7主动收回，进入 90s 冷却"), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        sync();
    }

    /** 头颅被击落：60s 失明 + 90s 冷却（由 SeikibankiHeadEntity.hurt 调用） */
    public void recallByAttack(ServerPlayer sp) {
        if (!skillActive) return;
        deactivate(sp);
        SREAbilityPlayerComponent.KEY.get(sp).setCooldown(COOLDOWN_TICKS);
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0, false, true, true));

        sp.displayClientMessage(Component.literal("§c§l头颅被击落！ §7陷入 60s 失明，技能进入冷却"), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
        sync();
    }

    /** 结束技能公共收尾：移除头颅、解除本体冻结 */
    private void deactivate(ServerPlayer sp) {
        discardHead(sp);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0;
        skillActive = false;
        headEntityId = -1;
    }

    /** 本体死亡：移除头颅即可（死亡本身走 SRE 正常流程） */
    public void cleanupOnDeath(ServerPlayer sp) {
        if (!skillActive) return;
        discardHead(sp);
        skillActive = false;
        headEntityId = -1;
        sync();
    }

    // ====================== 每 tick ======================

    /**
     * 服务端每 tick：冻结本体、续期禁用效果、校验头颅有效性。
     */
    public void tick(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null) return;
        if (!gw.isRole(sp, SeikibankiRole.SEIKIBANKI)) return;
        // 死亡后由 OnPlayerDeath 事件处理，不再强制任何状态
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;
        if (!skillActive) return;

        // 头颅有效性校验（每秒）：被外部移除时安全收回，防止状态卡死
        if (sp.level().getGameTime() % 20 == 0 && findHead(sp) == null) {
            deactivate(sp);
            sp.displayClientMessage(Component.literal("§d§l「飞头」 §7头颅已消散，意识归位"), false);
            SREAbilityPlayerComponent.KEY.get(sp).setCooldown(COOLDOWN_TICKS);
            sync();
            return;
        }

        // ===== 冻结本体（参考操纵师）=====
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0;
        if (sp.distanceToSqr(anchorX, anchorY, anchorZ) > MAX_DRIFT_SQR) {
            sp.teleportTo(anchorX, anchorY, anchorZ);
        }

        // 禁止使用物品（刀/枪/道具）与打开背包；转视角不能禁（鼠标朝向要转发给头颅）
        sp.addEffect(new MobEffectInstance(ModEffects.USED_BANED, BAN_RENEW_TICKS, 0, true, false, false));
        sp.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, BAN_RENEW_TICKS, 0, true, false, false));
    }

    // ====================== 输入包入口 ======================

    /** 收到客户端驾驶输入包：转发给头颅实体（服务端权威驱动） */
    public void handleFlyInput(ServerPlayer sp, int bits, float yaw, float pitch) {
        if (!skillActive) return;
        SeikibankiHeadEntity head = findHead(sp);
        if (head != null) {
            head.drive(bits, yaw, pitch);
        }
    }

    // ====================== 实体查找/清理 ======================

    private SeikibankiHeadEntity findHead(ServerPlayer sp) {
        if (headEntityId < 0) return null;
        Entity ent = sp.level().getEntity(headEntityId);
        return ent instanceof SeikibankiHeadEntity head ? head : null;
    }

    private void discardHead(ServerPlayer sp) {
        SeikibankiHeadEntity head = findHead(sp);
        if (head != null && !head.isRemoved()) {
            head.discard();
        }
    }

    // ====================== 序列化 ======================

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 技能激活态不落盘：重登后从头开始（头颅实体 save=false 也不会残留）
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 冷却由 SREAbilityPlayerComponent 持久化，此处无需写入
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        skillActive = tag.getBoolean("SkillActive");
        headEntityId = tag.getInt("HeadId");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("SkillActive", skillActive);
        tag.putInt("HeadId", headEntityId);
    }
}
