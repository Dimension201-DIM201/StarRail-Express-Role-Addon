package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 有钱能使鬼推磨：四季映姬·夜摩仙那度的能力卡牌
 *
 * <p>携带生效类：放在背包中自动生效。
 * 玩家即将死亡时，若背包有此卡且金币≥200，
 * 则扣除 200 金币并消耗 1 张卡牌，回避本次死亡。
 *
 * <p>原理：注册 SRE 的 AfterShieldAllowPlayerDeath / AfterShieldAllowPlayerDeathWithKiller
 * 事件（在护盾检查后、死亡执行前触发），返回 false 即可取消死亡。
 * SRE 的 SpawnMixin 在调用 killPlayer 前已将玩家血量恢复为 20，
 * 因此取消死亡后玩家即为满血存活状态。
 */
public class BribeDeathCard extends Item {

    private static final int BRIBE_COST = 200;

    public BribeDeathCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 16));
        registerDeathPrevention();
    }

    /** 注册 SRE 死亡取消事件（无击杀者 + 有击杀者两种） */
    private void registerDeathPrevention() {
        // 无击杀者死亡（环境伤害等）
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) ->
                tryBribeDeath(player, deathReason));

        // 有击杀者死亡（被玩家击杀）
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) ->
                tryBribeDeath(player, deathReason));
    }

    /**
     * 尝试花钱回避死亡
     *
     * @return true = 允许死亡，false = 回避死亡
     */
    private boolean tryBribeDeath(Player player, ResourceLocation deathReason) {
        if (!(player instanceof ServerPlayer sp)) return true;
        if (!CardUtils.hasInInventory(sp, this)) return true;
        if (CardUtils.getGold(sp) < BRIBE_COST) return true;

        // 扣除金币 + 消耗卡牌
        CardUtils.spendGold(sp, BRIBE_COST);
        CardUtils.consumeOneFromInventory(sp, this);
        CardUtils.notify(sp, "§d有钱能使鬼推磨§r§7 · 耗费 §6" + BRIBE_COST + " §7金币回避了死亡！");
        return false; // 取消死亡
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7四季映姬·夜摩仙那度的能力，可以回避死亡"));
        tooltip.add(Component.literal("§a§l[被动触发]§r §7死亡时消耗" + BRIBE_COST + "金币与本卡牌，回避死亡"));
        tooltip.add(Component.literal("§6交易价格: " + com.dim201.dim201addon.item.Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8你生前做的任何事情我都一清二楚"));
        tooltip.add(Component.literal("§8……既然有这么多钱"));
        tooltip.add(Component.literal("§8说明你值得拥有从头开始人生的机会"));
    }
}
