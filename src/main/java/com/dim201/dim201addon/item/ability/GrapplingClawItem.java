package com.dim201.dim201addon.item.ability;

import com.dim201.dim201addon.role.PhantomThiefRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 钩影手：大怪盗专属位移道具（拴绳贴图）。
 *
 * <p>右键发射钩索（最大 16 格，路径粒子）：
 * <ul>
 *   <li>命中方块：把大怪盗拉拽到该位置（带惯性速度）</li>
 *   <li>命中玩家：把目标拖到自己面前并施加 1 秒移动禁止（SRE MOVE_BANED）</li>
 * </ul>
 * <p>3 点耐久（命中一次耗 1 点，损坏后可在商店重新购买），每次使用 8 秒物品冷却。
 * 未命中（落空）不消耗耐久。
 */
public class GrapplingClawItem extends Item {

    public static final int MAX_USES = 3;
    public static final double RANGE = 16.0;
    public static final int COOLDOWN_TICKS = 8 * 20;
    /** 拉拽自己的初速度（格/tick），越大飞得越远越快 */
    public static final double PULL_POWER = 2.3;
    /** 拉自己时保证的向上初速度，确保能越过方块边缘落到目标上方 */
    public static final double PULL_UP_BIAS = 0.9;

    public GrapplingClawItem() {
        super(new Item.Properties().durability(MAX_USES).rarity(Rarity.EPIC));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!isValidUser(sp)) {
            return InteractionResultHolder.fail(stack);
        }

        if (sp.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        // 发射音效：无论命中与否都播放（甩钩的机械声）
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);

        Vec3 from = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        Vec3 to = from.add(look.scale(RANGE));

        // 先测方块距离，实体命中不能穿墙
        HitResult blockHit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double blockDist = blockHit.getType() != HitResult.Type.MISS
                ? blockHit.getLocation().distanceTo(from) : RANGE;

        // 实体优先（命中玩家 → 拖拽）
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, sp, from, to,
                sp.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
                e -> e instanceof ServerPlayer t && t != sp && GameUtils.isPlayerAliveAndSurvival(t));

        boolean consumed = false;
        if (entityHit != null && entityHit.getLocation().distanceTo(from) <= blockDist) {
            consumed = dragPlayer(sp, (ServerPlayer) entityHit.getEntity());
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            consumed = pullSelf(sp, (net.minecraft.world.phys.BlockHitResult) blockHit);
        } else {
            sp.displayClientMessage(Component.literal("§7钩索落空了（未命中不消耗耐久）"), true);
        }

        if (consumed) {
            // 命中才耗 1 点耐久；耐久耗尽物品报废（可在商店重新购买）
            int damage = stack.getDamageValue() + 1;
            if (damage >= stack.getMaxDamage()) {
                stack.shrink(1);
                sp.displayClientMessage(Component.literal(
                        "§c钩影手的索绳彻底磨损了……可在商店重新购买"), true);
            } else {
                stack.setDamageValue(damage);
            }
            sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** 命中玩家：拖到面前 + 1 秒移动禁止 */
    private boolean dragPlayer(ServerPlayer sp, ServerPlayer target) {
        // 只取水平朝向（去掉 Y 俯仰分量），避免目标被塞进地板/天花板
        Vec3 look = sp.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0e-6) {
            horizontal = new Vec3(sp.getDirection().getStepX(), 0, sp.getDirection().getStepZ());
        }
        horizontal = horizontal.normalize();
        // 落点：大怪盗正前方 1.5 格，Y 锁定与大怪盗同高（脚部），不会偏移到方块内部
        Vec3 front = sp.position().add(horizontal.scale(1.5));
        target.teleportTo(front.x, sp.getY(), front.z);
        target.setDeltaMovement(Vec3.ZERO);  // 清零残余速度，防止被惯性推入方块
        target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 20, 0, true, false, false));
        spawnPathParticles(sp.serverLevel(), sp.getEyePosition(), target.position().add(0, 1, 0));
        sp.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ARROW_HIT_PLAYER,
                SoundSource.PLAYERS, 1f, 0.8f);
        sp.displayClientMessage(Component.literal(
                "§5§l[钩影手] §f把 §d" + target.getName().getString() + "§f 拖到了面前！"), true);
        target.displayClientMessage(Component.literal("§5你被钩索猛地拽走了！"), true);
        return true;
    }

    /** 命中方块：把自己拉到目标方块的顶面上方（带惯性与向上弧线，越过边缘落到上面） */
    private boolean pullSelf(ServerPlayer sp, net.minecraft.world.phys.BlockHitResult blockHit) {
        // 落点：钩中顶面 → 命中点；钩中侧面/底面 → 目标方块顶面中心（保证拉到方块「上方」）
        net.minecraft.core.BlockPos blockPos = blockHit.getBlockPos();
        Vec3 land;
        if (blockHit.getDirection() == net.minecraft.core.Direction.UP) {
            land = blockHit.getLocation();
        } else {
            land = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);
        }

        // 从玩家当前位置指向落点的方向，给足初速度 + 强制向上分量，形成抛物线越过方块边缘
        Vec3 dir = land.subtract(sp.position());
        Vec3 vel = (dir.lengthSqr() < 1.0e-6)
                ? new Vec3(0, PULL_UP_BIAS, 0)
                : dir.normalize().scale(PULL_POWER);
        if (vel.y < PULL_UP_BIAS) {
            vel = new Vec3(vel.x, PULL_UP_BIAS, vel.z);
        }
        sp.setDeltaMovement(vel);
        sp.hurtMarked = true;   // 速度变化强制同步到客户端
        sp.fallDistance = 0;    // 钩索位移不摔伤
        spawnPathParticles(sp.serverLevel(), sp.getEyePosition(), land);
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.8f, 1.2f);
        sp.displayClientMessage(Component.literal("§5§l[钩影手] §f钩索回收，起飞！"), true);
        return true;
    }

    /** 钩索路径粒子 */
    private static void spawnPathParticles(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        int points = Math.max(6, (int) (dir.length() * 2));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(dir.scale((double) i / points));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** 仅大怪盗在对局内可用（被偷走的钩影手对他人无效） */
    private static boolean isValidUser(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(sp, PhantomThiefRole.PHANTOM_THIEF)) {
            sp.displayClientMessage(Component.literal("§c只有大怪盗才能使用钩影手"), true);
            return false;
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§5怪盗的得力道具，指哪儿拉哪儿"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7向准星射出钩索（§e16§7 格）"));
        tooltip.add(Component.literal("§7命中方块：把自己拉过去；命中玩家：拖到面前并禁足 1 秒"));
        tooltip.add(Component.literal("§7耐久 §e" + MAX_USES + " §7点（命中消耗、落空不耗），冷却 §68 §7秒"));
        tooltip.add(Component.literal("§8耐久耗尽后可在商店重新购买"));
    }
}
