package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import com.dim201.dim201addon.item.card.UndergroundSunManager;
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

import java.util.List;

/**
 * 地底的太阳：灵乌路空的能力卡牌
 *
 * <p>右键消耗型：使用后进入 5 秒「核爆状态」：
 * <ul>
 *   <li>无敌 5 秒：死亡事件被拦截（UndergroundSunManager 注册 AfterShieldAllowPlayerDeath）</li>
 *   <li>每 0.5 秒一次爆炸：爆炸中心为玩家当前位置，半径 5 格内所有玩家直接击杀</li>
 *   <li>5 秒结束后自身死亡（自爆）：死亡原因 dim201addon:underground_sun_self</li>
 * </ul>
 *
 * <p>爆炸频率：5 秒 / 0.5 秒 = 10 次爆炸。
 *
 * <p>并发安全：使用 ConcurrentHashMap 存储「核爆中玩家」列表。
 */
public class UndergroundSunCard extends Item {

    public UndergroundSunCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 冷却 / 对局限制由 Manager 统一判断（防止重复开核爆）
        if (!world.isClientSide && user instanceof ServerPlayer sp) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                CardUtils.notify(sp, "§c只能在对局中使用");
                return InteractionResultHolder.fail(stack);
            }
            if (UndergroundSunManager.isNuking(sp)) {
                CardUtils.notify(sp, "§c已经在核爆中，不可重复使用");
                return InteractionResultHolder.fail(stack);
            }
            UndergroundSunManager.startNuke(sp);
            stack.shrink(1);
        }

        return net.minecraft.world.item.ItemUtils.startUsingInstantly(world, user, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7灵乌路空的能力，可以焚尽世间万物"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §c（使用后消耗）"));
        tooltip.add(Component.literal("§7使用后进入 5 秒无敌"));
        tooltip.add(Component.literal("§c每 0.5 秒一次爆炸，击杀 5 格内所有玩家"));
        tooltip.add(Component.literal("§c5 秒结束后自身死亡"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8来回躲闪回避弹幕只是耍帅罢了"));
        tooltip.add(Component.literal("§8对于有能之人，只要把一切都烧个干净就好。"));
    }
}
