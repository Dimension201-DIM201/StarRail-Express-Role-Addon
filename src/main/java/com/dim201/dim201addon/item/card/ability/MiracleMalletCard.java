package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardEventBus;
import com.dim201.dim201addon.item.card.CardUtils;
import com.dim201.dim201addon.item.card.PassiveCardInterfaces;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 万宝槌：少名针妙丸的能力卡牌
 *
 * <p>携带激活型（被动）：放在背包中即生效，
 * 每次击杀玩家额外获得 50 金币奖励。
 *
 * <p>原理：实现 {@link PassiveCardInterfaces.OnKilledPlayerCard} 接口，
 * 在构造器中通过 {@link CardEventBus#register(Item)} 注册到事件总线。
 * SRE 的 {@code OnPlayerKilledPlayer.EVENT} 触发时，CardEventBus 会检查
 * 击杀者背包是否持有本卡 → 是则调用 {@link #onKilledPlayer} 发放额外 50 金币。
 *
 * <p>不消耗：与消耗型卡牌不同，本卡牌持续存在于背包中直至被交易或丢弃。
 */
public class MiracleMalletCard extends Item implements PassiveCardInterfaces.OnKilledPlayerCard {

    /** 每次击杀玩家额外奖励金币数 */
    public static final int KILL_REWARD = 50;

    public MiracleMalletCard() {
        super(CardUtils.cardProps(Rarity.UNCOMMON, 1));
        // 注册到携带生效卡牌事件总线（击杀事件）
        CardEventBus.register(this);
    }

    @Override
    public void onKilledPlayer(ServerPlayer killer, ServerPlayer victim, ResourceLocation deathReason) {
        // 发放额外击杀奖励金币
        CardUtils.addGold(killer, KILL_REWARD);
        CardUtils.notify(killer, "§d万宝槌§r§7 · 击杀奖励 §6" + KILL_REWARD + " §7金币");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7少名针妙丸的能力，击杀玩家额外获得金币奖励"));
        tooltip.add(Component.literal("§a§l[携带生效]§r §7背包中持有此卡时，每次击杀玩家额外获得§6" + KILL_REWARD + "§7金币"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8勇敢的小人战胜了巨大的鬼"));
        tooltip.add(Component.literal("§8鬼丢下的\"万宝槌\""));
        tooltip.add(Component.literal("§8可以实现任何愿望"));
    }
}
