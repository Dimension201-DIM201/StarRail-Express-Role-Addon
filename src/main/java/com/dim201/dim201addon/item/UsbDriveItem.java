package com.dim201.dim201addon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * U 盘 - 神秘礼盒小概率奖品，后续会赋予机制。
 *
 * <p>当前版本仅为物品本体，无主动效果。玩家可拾取保留作为后续机制触发道具。
 */
public class UsbDriveItem extends Item {

    public UsbDriveItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                 @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("§7神秘物品，似乎隐藏着某种秘密...")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§c当前版本暂未开放使用").withStyle(ChatFormatting.RED));
    }
}
