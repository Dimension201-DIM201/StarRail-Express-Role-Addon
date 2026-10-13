package com.dim201.dim201addon.item;

import com.dim201.dim201addon.BlindPlayerComponent;
import com.dim201.dim201addon.role.BlindRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 盲杖 - 盲人商店专属物品
 *
 * 右键消耗：移除自身失明，恢复视野 30 秒
 * 恢复期由 {@link BlindPlayerComponent} 的恢复标记控制，
 * 到期后盲人被动会立即重新施加失明（见 BlindRole）
 */
public class BlindStickItem extends Item {

    /** 视野恢复时长（30 秒 = 600 ticks） */
    public static final int RESTORE_TICKS = 30 * 20;

    public BlindStickItem(Properties props) {
        super(props.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);
        if (!BlindRole.isBlind(sp)) {
            return InteractionResultHolder.pass(stack);
        }

        // 写入恢复期标记（盲人 tick 由此跳过失明施加），并立即移除当前失明
        BlindPlayerComponent comp = BlindPlayerComponent.KEY.get(sp);
        comp.requestVisionRestore(level.getGameTime() + RESTORE_TICKS);
        sp.removeEffect(MobEffects.BLINDNESS);

        sp.displayClientMessage(
                Component.literal("§d盲杖§r §7· 视野恢复 §630 §7秒"),
                true);

        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7右键消耗：恢复视野 30 秒").withStyle(ChatFormatting.GRAY));
    }
}
