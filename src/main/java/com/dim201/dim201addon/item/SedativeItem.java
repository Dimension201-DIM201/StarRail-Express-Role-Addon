package com.dim201.dim201addon.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 镇静剂 - 右键直接使用：获得 30 秒镇静效果（心情值锁定为 50%），疯子使用时持续时间减半（15 秒）
 *
 * <p>使用方式：右键立即触发，无饮用动画，无引用延迟。
 * <p>贴图：使用原版药水瓶模型 + 红色 PotionContents（瞬间伤害药水的液体染色）。
 * <p>第三人称：其他玩家看不到持有者手中的瓶子（客户端 Mixin 隐藏右臂），
 * 第一人称自己仍可见，参考 SRE 空包弹的"隐蔽使用"思路。
 * <p>对疯子同样有效：SedativeManager 在服务端 tick 末尾覆盖心情值，
 * 且 MadmanRole 的 handleServerTick 会检测镇静状态跳过强制低心情。
 */
public class SedativeItem extends Item {

    public SedativeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        // 客户端直接返回成功以播放使用动作（不阻塞）
        if (world.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }

        // 服务端：立即触发镇静效果
        int durationTicks = SedativeManager.getDurationFor(sp);
        SedativeManager.sedate(sp, durationTicks);

        CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        sp.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, sp);

        // 使用音效：仅使用者本人可听见（Player.playSound 只发包给该玩家，不广播附近）
        sp.playSound(SoundEvents.GENERIC_DRINK, 1.0F, 1.0F);

        sp.displayClientMessage(
                Component.literal("§b镇静剂 §7· 心情已稳定（持续 "
                        + (durationTicks / 20) + " 秒）"),
                true);

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7右键使用：获得 30 秒镇静效果"));
        tooltip.add(Component.literal("§7心情值锁定为 50%，结束后恢复"));
        tooltip.add(Component.literal("§7疯子使用时持续时间减半（15 秒）"));
    }
}
