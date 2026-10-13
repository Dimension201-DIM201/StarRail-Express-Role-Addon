package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.card.CardUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 献给神山的供物：八坂神奈子的能力卡牌
 *
 * <p>右键消耗类：按下右键消耗，将16格内的全部掉落物传送至使用者脚下。
 */
public class OfferingCard extends Item {

    private static final double RANGE = 16.0;

    public OfferingCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        ServerLevel sl = sp.serverLevel();
        Vec3 pos = sp.position();
        AABB box = sp.getBoundingBox().inflate(RANGE);

        List<ItemEntity> items = sl.getEntitiesOfClass(ItemEntity.class, box);
        int count = 0;
        for (ItemEntity item : items) {
            item.teleportTo(pos.x, pos.y, pos.z);
            count++;
        }

        if (count > 0) {
            CardUtils.notify(sp, "§d献给神山的供物§r§7 回收了 §6" + count + " §7件掉落物！");
            // 粒子 + 音效
            sl.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 30, 2, 2, 2, 0.05);
            sl.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 2.0f, 0.8f);
        } else {
            CardUtils.notify(sp, "§7附近没有掉落物");
        }

        // 消耗
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7八坂神奈子的能力，可以将献给大山的供品全部回收"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7将16格内的全部掉落物传送至使用者脚下，使用后消耗"));
        tooltip.add(Component.literal("§6交易价格: " + com.dim201.dim201addon.item.Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8\u201c献给大山的，便是属于我的！\u201d"));
        tooltip.add(Component.literal("§8啊啊，乍一听是多么自私的说法啊！"));
    }
}
