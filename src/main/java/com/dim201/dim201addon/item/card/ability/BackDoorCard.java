package com.dim201.dim201addon.item.card.ability;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 背后之门：摩多罗隐岐奈的能力卡牌
 *
 * <p>携带生效类：放在背包中自动生效。
 * 玩家被「背后」的攻击者击杀时，消耗一张卡牌回避本次死亡。
 * 背后定义：攻击者位于受害者视线方向的后方 180° 范围内。
 *
 * <p>原理：SRE 对局中玩家无实际血量，伤害拦截无效。
 * 必须注册 SRE 的 {@link AfterShieldAllowPlayerDeathWithKiller} 事件
 * （在护盾检查后、死亡执行前触发），返回 false 即可取消死亡。
 * SRE 的 SpawnMixin 在调用 killPlayer 前已将玩家血量恢复为 20，
 * 因此取消死亡后玩家即为满血存活状态。
 *
 * <p>优先级机制：当玩家同时拥有「有钱能使鬼推磨」且金币充足时，
 * 优先让 BribeDeathCard 处理（它注册顺序在后，需要本卡让出机会）。
 */
public class BackDoorCard extends Item {

    /** 有钱能使鬼推磨的回避成本（需与 BribeDeathCard.BRIBE_COST 保持一致） */
    private static final int BRIBE_COST = 200;

    public BackDoorCard() {
        super(CardUtils.cardProps(Rarity.EPIC, 16));
        registerDeathPrevention();
    }

    /** 注册 SRE 死亡取消事件（仅有击杀者版本，背后定义需要攻击者） */
    private void registerDeathPrevention() {
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) ->
                tryAvoidDeath(player, killer, deathReason));
    }

    /**
     * 尝试回避背后攻击致死
     *
     * @return true = 允许死亡，false = 回避死亡
     */
    private boolean tryAvoidDeath(Player player, Player killer, ResourceLocation deathReason) {
        if (!(player instanceof ServerPlayer victim)) return true;
        if (killer == null) return true; // 无击杀者无法判定方向
        if (!CardUtils.hasInInventory(victim, this)) return true;

        // 优先级：同时拥有「有钱能使鬼推磨」且金币充足时，让 BribeDeathCard 处理
        // BribeDeathCard 注册顺序在后，本卡返回 true 让事件继续传播到它
        if (CardUtils.hasInInventory(victim, Dim201ModItems.BRIBE_DEATH)
                && CardUtils.getGold(victim) >= BRIBE_COST) {
            return true;
        }

        // 仅当来自背后的攻击才回避
        if (!isAttackFromBehind(victim, killer)) return true;

        CardUtils.consumeOneFromInventory(victim, this);
        CardUtils.notify(victim, "§d背后之门§r§7 抵挡了来自背后的致命一击！");
        return false; // 取消死亡
    }

    /**
     * 判定攻击者是否在受害者背后
     * 原理：受害者视线方向 → 「受害者→攻击者」方向的 XZ 点积
     * 点积 < 0 → 攻击者在后方 180° 范围内
     */
    private boolean isAttackFromBehind(ServerPlayer victim, LivingEntity attacker) {
        Vec3 look = victim.getLookAngle();
        Vec3 dir = attacker.position().subtract(victim.position());
        double dist = dir.horizontalDistance();
        if (dist < 0.01) return false; // 重叠无法判定
        // XZ 平面归一化后点积
        double dot = (look.x * dir.x + look.z * dir.z) / dist;
        return dot < 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7摩多罗隐岐奈的能力，可以打开背后之门"));
        tooltip.add(Component.literal("§a§l[被动触发]§r §7抵挡一次来自背后的致命攻击后消失"));
        tooltip.add(Component.literal("§6交易价格: " + Dim201ModItems.getCardPrice(this) + " 金币"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8对生物而言，背后既是弱点也是理想乡"));
        tooltip.add(Component.literal("§8在这前方，究竟是能托付背后的贵人"));
        tooltip.add(Component.literal("§8还是栖息于乐园中的天魔呢"));
    }
}
