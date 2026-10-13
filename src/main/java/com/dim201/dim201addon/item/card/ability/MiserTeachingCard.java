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
 * 守财奴的教诲：河城荷取的能力卡牌
 *
 * <p>携带激活型（被动）：放在背包中即生效，
 * 每次完成任务额外获得25金币奖励。
 *
 * <p>原理：通过 {@link com.dim201.dim201addon.mixin.MiserTeachingMixin} 注入
 * SRE 的 {@code RoleMethodDispatcher.callOnFinishQuest} 方法，
 * 任务完成时检查玩家背包是否持有本卡牌，有则额外给予25金币。
 * （与 SRE 的"任务大师"修饰符实现机制一致，参考 TaskmasterGiveCoinsMixin）
 *
 * <p>不消耗：与消耗型卡牌不同，本卡牌持续存在于背包中直至被交易或丢弃。
 */
public class MiserTeachingCard extends Item {

    /** 任务完成额外奖励金币数 */
    public static final int QUEST_REWARD = 15;

    public MiserTeachingCard() {
        super(CardUtils.cardProps(Rarity.UNCOMMON, 1));
    }

    /**
     * 检查物品堆中是否含有守财奴的教诲卡牌
     * 供 Mixin 调用
     */
    public static boolean hasCard(Iterable<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (stack.is(Dim201ModItems.MISER_TEACHING)) return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7河城荷取的能力，增加任务奖励"));
        tooltip.add(Component.literal("§a§l[携带生效]§r §7背包中持有此卡时，每次完成任务额外获得§6" + QUEST_REWARD + "§7金币"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8即“掉在地上的全都是自己的！”"));
        tooltip.add(Component.literal("§8既然河童能伸长手臂"));
        tooltip.add(Component.literal("§8那肯定是利用这一招来网罗万物的"));
    }
}
