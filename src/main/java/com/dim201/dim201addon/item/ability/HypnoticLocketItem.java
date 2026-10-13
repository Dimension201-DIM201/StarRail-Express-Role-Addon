package com.dim201.dim201addon.item.ability;

import com.dim201.dim201addon.role.PhantomThiefRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 迷魂铃：大怪盗专属消耗道具（原版铃铛贴图）。
 *
 * <p>对准星命中的玩家（3 格内）使用：禁止移动 + 禁止转向 + 技能封印 2.5 秒
 * （均为 SRE 已有药水效果 MOVE_BANED / TURN_BANED / SKILL_BANED）。
 * 使用后消耗；每局限购 1 次（购买标记在 {@link com.dim201.dim201addon.PhantomThiefPlayerComponent#locketBought}）。
 */
public class HypnoticLocketItem extends Item {

    /** 效果时长（2.5 秒） */
    public static final int EFFECT_DURATION_TICKS = 50;
    /** 可命中的最大距离（格） */
    public static final double RANGE = 3.0;

    public HypnoticLocketItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(sp, PhantomThiefRole.PHANTOM_THIEF)) {
            sp.displayClientMessage(Component.literal("§c只有大怪盗才能使用迷魂铃"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 准星实体检测（只认存活玩家）
        Vec3 from = sp.getEyePosition();
        Vec3 to = from.add(sp.getLookAngle().scale(RANGE));
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                level, sp, from, to,
                sp.getBoundingBox().expandTowards(sp.getLookAngle().scale(RANGE)).inflate(1.0),
                e -> e instanceof ServerPlayer t && t != sp && GameUtils.isPlayerAliveAndSurvival(t));
        if (hit == null) {
            sp.displayClientMessage(Component.literal("§c需要对准一名玩家使用（" + (int) RANGE + " 格内）"), true);
            return InteractionResultHolder.fail(stack);
        }

        ServerPlayer target = (ServerPlayer) hit.getEntity();
        target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, EFFECT_DURATION_TICKS, 0, true, false, false));
        target.addEffect(ModEffects.of(ModEffects.TURN_BANED, EFFECT_DURATION_TICKS, 0, true, false, false));
        target.addEffect(ModEffects.of(ModEffects.SKILL_BANED, EFFECT_DURATION_TICKS, 0, true, false, false));

        // 铃声是公共的——出千也有声响，这是大怪盗的浪漫
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.9f, 1.5f);

        stack.shrink(1); // 使用后消耗
        sp.displayClientMessage(Component.literal(
                "§5§l[迷魂铃] §f铃声迷住了 §d" + target.getName().getString() + "§f！"), true);
        target.displayClientMessage(Component.literal("§5清脆的铃声让你的身体僵住了…"), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§5铃声即催眠——清脆，却让人动弹不得"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7对准 §e3§7 格内的玩家："));
        tooltip.add(Component.literal("§7禁止移动 + 禁止转向 + 技能封印 §e2.5§7 秒"));
        tooltip.add(Component.literal("§c使用后消耗，每局限购 1 次"));
    }
}
