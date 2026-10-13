package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
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

import java.util.HashSet;
import java.util.List;

/**
 * 燃烧生命的火把：克劳恩皮丝的能力卡牌
 *
 * <p>右键消耗类：使用后刷新所有物品的冷却时间。
 *
 * <p>原理：通过 SRE access widener 暴露的 {@code ItemCooldowns.cooldowns} 字段，
 * 复制当前所有冷却中的物品 key 集合，逐一调用 {@code removeCooldown(Item)} 清除冷却。
 * （参考 SRE 的 {@code RepairGameSetup.resetGame} 实现方式）
 */
public class TorchOfLifeCard extends Item {

    public TorchOfLifeCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        // 遍历玩家背包、盔甲、副手所有物品，移除其冷却
        // 原理：ItemCooldowns.isOnCooldown/removeCooldown 是 public，
        // 通过遍历玩家持有的物品检测冷却状态，逐一移除。
        HashSet<net.minecraft.world.item.Item> coolingItems = new HashSet<>();
        collectCoolingItems(sp, coolingItems);

        if (coolingItems.isEmpty()) {
            CardUtils.notify(sp, "§7当前没有处于冷却中的物品");
            return InteractionResultHolder.pass(stack);
        }

        // 逐一清除冷却
        for (net.minecraft.world.item.Item item : coolingItems) {
            sp.getCooldowns().removeCooldown(item);
        }

        CardUtils.notify(sp, "§d燃烧生命的火把§r§7 · 已刷新 §6"
                + coolingItems.size() + " §7个物品的冷却");

        // 消耗
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 收集玩家所有持有物品中处于冷却状态的物品
     * 原理：遍历主物品栏、盔甲栏、副手，检查每个物品的冷却状态
     */
    private void collectCoolingItems(ServerPlayer sp, HashSet<net.minecraft.world.item.Item> out) {
        // 主物品栏
        for (ItemStack stack : sp.getInventory().items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.item.Item item = stack.getItem();
                if (sp.getCooldowns().isOnCooldown(item)) out.add(item);
            }
        }
        // 盔甲栏
        for (ItemStack stack : sp.getInventory().armor) {
            if (!stack.isEmpty()) {
                net.minecraft.world.item.Item item = stack.getItem();
                if (sp.getCooldowns().isOnCooldown(item)) out.add(item);
            }
        }
        // 副手
        for (ItemStack stack : sp.getInventory().offhand) {
            if (!stack.isEmpty()) {
                net.minecraft.world.item.Item item = stack.getItem();
                if (sp.getCooldowns().isOnCooldown(item)) out.add(item);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7克劳恩皮丝的能力，刷新物品冷却"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7刷新所有物品的冷却时间，使用后消耗"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8据说看着篝火人就会感到放松"));
        tooltip.add(Component.literal("§8但是不可以盯着她的火把看"));
        tooltip.add(Component.literal("§8看她的火把时肯定不会觉得平静舒畅"));
    }
}
