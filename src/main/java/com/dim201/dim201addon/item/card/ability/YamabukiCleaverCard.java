package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.entity.CleaverEntity;
import com.dim201.dim201addon.init.ModEntities;
import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 山姥的菜刀：坂田合欢的能力卡牌
 *
 * <p>右键消耗型：使用后立即消耗 1 张卡牌，向准星方向投掷一把菜刀，
 * 命中玩家无视护盾直接击杀，未命中继续飞行 8 秒后消失。
 *
 * <p>冷却：5 秒（100 tick），通过 ItemCooldowns 实现。
 *
 * <p>与飞刀（SRE ThrowingKnife）的差异：
 * <ul>
 *   <li>飞刀：按 Q 投掷，可多次使用（有库存），无使用数量上限</li>
 *   <li>菜刀：右键直接投掷，一次消耗 1 张卡牌，每张只能击杀 1 名玩家（实体层保证）</li>
 * </ul>
 *
 * <p>材质套用普通的刀（SRE 的 KNIFE 或原版 IRON_SWORD 等），无需额外贴图。
 */
public class YamabukiCleaverCard extends Item {

    /** 冷却 tick 数（5 秒） */
    public static final int COOLDOWN_TICKS = 5 * 20;

    public YamabukiCleaverCard() {
        super(CardUtils.cardProps(Rarity.RARE, 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 冷却中 → 不消耗（与其他冷却卡牌一致）
        if (user.getCooldowns().isOnCooldown(this)) {
            if (user instanceof ServerPlayer sp) CardUtils.notify(sp, "§c菜刀冷却中");
            return InteractionResultHolder.fail(stack);
        }
        // 仅在对局内可用（防止大厅刷）
        if (!world.isClientSide && user instanceof ServerPlayer sp) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                CardUtils.notify(sp, "§c只能在对局中使用");
                return InteractionResultHolder.fail(stack);
            }
            throwCleaver(sp, stack);
        }

        // 开始使用（右键动画虽然不蓄力，但保持语义正确）
        return net.minecraft.world.item.ItemUtils.startUsingInstantly(world, user, hand);
    }

    /**
     * 发射菜刀实体（服务端）
     *
     * <p>实现方式参考 SRE 飞刀：在 ModPacketsReciever 里发射 ThrowingKnifeEntity。
     * 差异：本卡右键直接在 use() 内发射，不走 Q 键/数据包逻辑，更简单可靠。
     */
    private void throwCleaver(ServerPlayer sp, ItemStack stack) {
        Level level = sp.level();
        CleaverEntity entity = new CleaverEntity(ModEntities.CLEAVER, sp, level, stack.copyWithCount(1));
        entity.setPos(sp.getEyePosition().add(0, -0.2, 0));
        // shootFromRotation：按玩家俯仰/偏航角度发射，速度 1.5（比飞刀快一点）
        entity.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0f, 1.5f, 1.0f);
        entity.setOwner(sp);
        level.addFreshEntity(entity);

        // 5 秒冷却
        sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        // 消耗 1 张卡牌（消耗前已发射实体，即便 shrink 失败实体也发出）
        stack.shrink(1);

        // 投掷音效（所有玩家可听）
        if (level instanceof ServerLevel sl) {
            sl.players().forEach(p -> sl.playSound(p, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.0f));
        }
        sp.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7坂田合欢的能力，投掷杀伤力极高的菜刀"));
        tooltip.add(Component.literal("§a§l[右键使用]§r §7向准星方向投掷菜刀，命中玩家直接击杀§c（无视护盾，使用后消耗）"));
        tooltip.add(Component.literal("§7冷却时间 §65 §7秒"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8魔法和火药，咒术与最新兵器。"));
        tooltip.add(Component.literal("§8武器形形色色，但果然刀具才是最棒的。"));
        tooltip.add(Component.literal("§8毕竟只是丢出去就能让大家感到恐惧。"));
    }
}
