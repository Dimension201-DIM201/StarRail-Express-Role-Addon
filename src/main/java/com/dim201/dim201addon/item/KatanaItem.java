package com.dim201.dim201addon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 蕾忍专属打刀
 *
 * <p>两种攻击模式（按技能键切换）：
 * <ul>
 *   <li>岚刀模式：连续攻击5次击杀目标，有小脑惩罚</li>
 *   <li>燃烧刀模式：每刀直接击杀，消耗蕾克拉，身边出现火焰粒子</li>
 * </ul>
 *
 * <p>物品本身不携带攻击逻辑，实际伤害/击杀判定在 DaoLeirenRole 的 AttackEntityCallback 中处理。
 * Tooltip 通过 withBuiltinLore 设置到 DataComponents.LORE。
 */
public class KatanaItem extends SwordItem {

    public KatanaItem(Tiers tier, Properties properties) {
        super(tier, properties);
    }

    /** 为物品栈设置内置 lore（在角色分配时调用） */
    public static ItemStack withBuiltinLore(@NotNull ItemStack stack) {
        try {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7§o刀系蕾忍专属武器").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("§e默认：岚刀模式 §7- 连击5次击杀"));
            lore.add(Component.literal("§c技能键切换：燃烧刀模式 §7- 每刀必杀"));
            lore.add(Component.literal("§6移动积累蕾克拉，切换模式消耗60点"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        } catch (Throwable ignored) {}
        return stack;
    }
}
