package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 鬼杰组长的恐吓：吉吊八千慧的能力卡牌
 *
 * <p>右键消耗类：使用后掠夺10格内每个玩家50金币，转移给使用者。
 *
 * <p>原理：
 * <ul>
 *   <li>以玩家为中心构建 AABB 盒子（10格范围）</li>
 *   <li>遍历范围内所有存活玩家（排除自己）</li>
 *   <li>每个玩家扣除50金币（通过 spendGold，不足则扣尽所有余额）</li>
 *   <li>累计金额一次性加给使用者（减少同步次数）</li>
 *   <li>扣除失败（余额0）的玩家跳过，不扣除卡牌也不算入收益</li>
 * </ul>
 */
public class IbisChieftainIntimidationCard extends Item {

    /** 掠夺范围（格） */
    private static final double RANGE = 10.0;
    /** 每个玩家被掠夺的金币数 */
    private static final int PLUNDER_AMOUNT = 50;

    public IbisChieftainIntimidationCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        // 构建以玩家为中心的 AABB 盒子（10格范围）
        AABB box = AABB.ofSize(sp.position(), RANGE * 2, RANGE * 2, RANGE * 2);

        int totalPlundered = 0;
        int victims = 0;

        // 遍历范围内所有玩家
        for (Player target : sp.level().getEntitiesOfClass(Player.class, box)) {
            if (target == sp) continue;
            if (!(target instanceof ServerPlayer targetSp)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(targetSp)) continue;

            // 扣除50金币（不足则扣尽所有余额）
            int targetBalance = CardUtils.getGold(targetSp);
            if (targetBalance <= 0) continue;

            int actualDeduct = Math.min(targetBalance, PLUNDER_AMOUNT);
            if (!CardUtils.spendGold(targetSp, actualDeduct)) continue;

            totalPlundered += actualDeduct;
            victims++;
        }

        // 没有可掠夺的玩家 → 不消耗卡牌
        if (victims == 0) {
            CardUtils.notify(sp, "§710格内没有可掠夺金币的玩家");
            return InteractionResultHolder.pass(stack);
        }

        // 一次性给使用者
        CardUtils.addGold(sp, totalPlundered);
        CardUtils.notify(sp, "§d鬼杰组长的恐吓§r§7 · 掠夺 §6" + victims
                + " §7名玩家，共获得 §6" + totalPlundered + " §7金币");

        // 消耗
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7吉吊八千慧的能力，恐吓周围的玩家"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7掠夺10格内每个玩家50金币，使用后消耗"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8以尽管彬彬有礼却让人生不出反抗念头的态度"));
        tooltip.add(Component.literal("§8从妖精手上再夺走一点小钱"));
        tooltip.add(Component.literal("§8购买能力卡牌时，什么是必要的？"));
        tooltip.add(Component.literal("§8人生中最重要的东西是什么，你应该能明白"));
    }
}
