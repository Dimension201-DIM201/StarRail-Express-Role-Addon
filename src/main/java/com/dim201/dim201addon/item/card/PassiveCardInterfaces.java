package com.dim201.dim201addon.item.card;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 携带生效卡牌接口集合（接口隔离原则 ISP）
 *
 * <p>每张携带生效卡牌实现自己需要的接口即可，不强制实现所有方法。
 * 构造时通过 CardEventBus.register(this) 注册到事件总线。
 * CardEventBus 会在全局触发事件时，先检查相关玩家背包是否含该卡 → 是则调用对应接口方法。
 *
 * <p>示例：
 * <pre>
 * public class MyCard extends Item implements OnKilledPlayerCard {
 *     public MyCard(Properties props) {
 *         super(props);
 *         CardEventBus.register(this);   // 注册到事件总线
 *     }
 *     public void onKilledPlayer(ServerPlayer killer, ServerPlayer victim, ResourceLocation deathReason) { ... }
 * }
 * </pre>
 */
public final class PassiveCardInterfaces {

    private PassiveCardInterfaces() {}

    /** 携带卡牌的玩家击杀他人时触发 */
    public interface OnKilledPlayerCard {
        void onKilledPlayer(ServerPlayer killer, ServerPlayer victim,
                            net.minecraft.resources.ResourceLocation deathReason);
    }

    /** 携带卡牌的玩家被攻击时触发（允许修改伤害值） */
    public interface OnBeingAttackedCard {
        /**
         * @param victim        被攻击者（携带者），非空
         * @param attacker      攻击者，可能为 null（环境伤害）
         * @param damageAmount  原始伤害
         * @return 最终伤害（0 或负数 → 不受伤）
         */
        float onBeingAttacked(ServerPlayer victim, @Nullable LivingEntity attacker, float damageAmount);
    }

    /** 携带卡牌的玩家死亡时触发 */
    public interface OnPlayerDeathCard {
        /**
         * @param dead        死者（携带者），非空
         * @param killer      击杀者，可能为 null（环境死亡）
         * @param deathReason 死因 ID
         */
        void onPlayerDeath(ServerPlayer dead, @Nullable ServerPlayer killer,
                           net.minecraft.resources.ResourceLocation deathReason);
    }

    /** 携带卡牌的玩家每秒 tick（20 tick 节流）一次时触发 */
    public interface OnTickCard {
        /**
         * @param player    携带者（存活）
         * @param cardStack 该卡的一个代表 ItemStack（同一张卡多张也只调用一次）
         */
        void onTick(ServerPlayer player, ItemStack cardStack);
    }
}

