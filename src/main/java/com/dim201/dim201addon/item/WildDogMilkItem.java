package com.dim201.dim201addon.item;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 野生狗奶：饮品
 * - 饮用动画：drink（和药水/牛奶一致）
 * - 饮用时长：32 tick（约 1.6 秒，与普通牛奶对齐）
 * - 贴图暂用 minecraft:glass_bottle（水瓶/牛奶瓶）替代
 *
 * <p>关键设计：注册时挂载零营养的 FoodProperties（见 Dim201ModItems），
 * 这让本物品走 {@code ItemUtils.finishUsingItem} → {@code Player.eat()} 路径。
 * SRE 的护盾/毒药 Mixin（PlayerEntityMixin.tmm$poisonedFoodEffect）注入在
 * Player.eat() 中，会处理 ARMORER/WEAK_ARMORER/POISONER 数据组件——
 * 即托盘倒在野生狗奶上的防御药剂、毒药、弱效护盾都会正常生效。
 *
 * <p>任务系统兼容：SRE 的「喝饮料」任务（Task.DRINK）通过 FoodItemMixin 拦截
 * {@code Item.finishUsingItem}，但只用 {@code instanceof} 硬编码识别
 * CocktailItem / PotionItem / HoneyBottleItem 三类，本物品继承自 Item
 * 不在白名单内。因此在此手动调用 {@link SREPlayerMoodComponent#drinkCocktail()}
 * 触发任务完成，与 CocktailItem 的实现方式一致。
 *
 * <p>用户要求：野生狗奶喝完不给任何药水效果，只保留「喝 + 消耗 + 统计」——
 * FoodProperties 不挂任何 effect，nutrition=0/saturation=0 不影响饱食度。
 */
public class WildDogMilkItem extends Item {

    public WildDogMilkItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (!world.isClientSide && user instanceof ServerPlayer sp) {
            // 关键：走 Player.eat() 路径，触发 SRE 的护盾/毒药 Mixin
            // （PlayerEntityMixin.tmm$poisonedFoodEffect 注入在 Player.eat() 内，
            //   处理 stack 上的 ARMORER / WEAK_ARMORER / POISONER 数据组件）
            // 不调用 super.finishUsingItem（默认实现是空），手动复刻 eat + 消耗逻辑。
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                sp.eat(world, stack, food);
            }
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            sp.awardStat(Stats.ITEM_USED.get(this));
            // 触发 SRE 「喝饮料」任务（DrinkTask）
            // 原理：SRE 的 FoodItemMixin 仅识别 PotionItem/HoneyBottleItem/CocktailItem，
            // 自定义饮品需手动调用 drinkCocktail() 才能完成任务。
            SREPlayerMoodComponent.KEY.get(sp).drinkCocktail();
            // 消耗 1 个（Player.eat 不会自动消耗，参考原版 PotionItem/HoneyBottleItem 都自己 consume）
            stack.consume(1, sp);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 32;   // 普通牛奶/水瓶饮用时长
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;  // 喝药水/牛奶/水的手持动画
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;  // 咕嘟喝水声
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(world, user, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.literal("§7粤西特供，保质期：永久！"));
    }

    /**
     * 保证每个野生狗奶 ItemStack 都有 tooltip 描述
     */
    public static ItemStack withBuiltinLore(ItemStack stack) {
        try {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7粤西特供，保质期：永久！"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        } catch (Throwable ignored) {}
        return stack;
    }
}
