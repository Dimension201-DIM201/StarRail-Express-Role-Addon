package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 不死鸟之尾：藤原妹红的能力卡牌
 *
 * <p>携带激活型（被动）：放在背包中即生效，
 * 死亡后在出生点复活，消耗此卡牌。每张卡牌仅能使用一次。
 *
 * <p>原理：通过 {@link com.dim201.dim201addon.item.card.PhoenixTailManager}
 * 注册 SRE 的 {@code AfterShieldAllowPlayerDeath} 和
 * {@code AfterShieldAllowPlayerDeathWithKiller} 事件，
 * 玩家死亡时检查背包是否持有本卡牌，有则传送到出生点并满血复活。
 * （参考足立透 AdachiRole.tryRevive 的实现方式）
 */
public class PhoenixTailCard extends Item {

    public PhoenixTailCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 1));
    }

    /**
     * 检查物品堆中是否含有不死鸟之尾卡牌
     * 供 PhoenixTailManager 调用
     */
    public static boolean hasCard(Iterable<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (stack.is(Dim201ModItems.PHOENIX_TAIL)) return true;
        }
        return false;
    }

    /**
     * 从物品栏中消耗一张不死鸟之尾卡牌
     */
    public static void consumeOne(Iterable<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (stack.is(Dim201ModItems.PHOENIX_TAIL)) {
                stack.shrink(1);
                return;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7藤原妹红的能力，不死之身"));
        tooltip.add(Component.literal("§a§l[携带生效]§r §7死亡后在出生点复活，消耗此卡牌（仅一次）"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8没有比这个更不看前提条件，更强大的能力了吧。"));
        tooltip.add(Component.literal("§8攻击……防御……回避……模式化……"));
        tooltip.add(Component.literal("§8在不死的生命看来，一切都只是游戏规则。"));
        tooltip.add(Component.literal("§8但是，费用极高。"));
    }
}
