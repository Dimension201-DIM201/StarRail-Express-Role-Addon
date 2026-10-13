package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.card.CardUtils;
import com.dim201.dim201addon.item.card.NoClipManager;
import io.wifi.starrailexpress.rules.CollisionRules;
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
 * 小石子帽子：古明地恋的能力卡牌
 *
 * <p>右键消耗类：按下右键消耗，持续 30 秒移除自身碰撞体积（玩家之间可互相穿透）。
 *
 * <p>原理：复用 SRE 的 {@link CollisionRules#cantCollide} 机制
 * （与"轻灵"修饰符实现一致）。卡牌生效期间向 cantCollide 注册 Predicate，
 * 匹配当前玩家UUID则返回 true，使 EntityMixin 的 canCollideWith 跳过碰撞判定。
 *
 * <p>状态管理：使用 {@link NoClipManager} 记录激活玩家UUID和到期时间，
 * Predicate 查询时判断是否在有效期内。
 */
public class KoishiHatCard extends Item {

    /** 无碰撞持续时间（30秒 = 600 tick） */
    private static final long DURATION_TICKS = 600L;

    public KoishiHatCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 16));
        registerCollisionRule();
    }

    /**
     * 注册碰撞规则
     * 原理：向 CollisionRules.cantCollide 添加 Predicate，
     * 当玩家处于无碰撞状态时返回 true，使 canCollideWith 判定跳过碰撞。
     */
    private void registerCollisionRule() {
        CollisionRules.cantCollide.add(player -> {
            if (player.level().isClientSide) return false;
            if (!(player instanceof ServerPlayer sp)) return false;
            return NoClipManager.isActive(sp.getUUID(),
                    sp.server.getTickCount());
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        // 激活无碰撞状态
        NoClipManager.activate(sp, DURATION_TICKS);
        CardUtils.notify(sp, "§d小石子帽子§r§7 · 移除碰撞体积，持续 §630 §7秒");

        // 消耗
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7古明地恋的能力，移除自身的碰撞体积"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7消耗1张，持续30秒，期间玩家之间可互相穿透"));
        tooltip.add(Component.literal("§6交易价格: " + com.dim201.dim201addon.item.Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8难道说没人注意到我？"));
        tooltip.add(Component.literal("§8这样啊，这下就方便了。"));
    }
}
