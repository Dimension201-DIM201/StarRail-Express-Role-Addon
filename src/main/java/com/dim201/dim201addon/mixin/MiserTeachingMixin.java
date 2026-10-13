package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.item.card.ability.MiserTeachingCard;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 守财奴的教诲卡牌 - 任务完成奖励 Mixin
 *
 * <p>原理：注入 SRE 的 {@link RoleMethodDispatcher#callOnFinishQuest} 方法，
 * 当玩家完成任务时检查背包是否持有守财奴的教诲卡牌，
 * 有则额外给予 {@link MiserTeachingCard#QUEST_REWARD} 金币奖励。
 *
 * <p>实现与 SRE 的"任务大师"修饰符（TaskmasterGiveCoinsMixin）一致，
 * 仅触发条件从"修饰符"改为"持有卡牌"。
 *
 * <p>Mixin 规范遵循项目约束：
 * <ul>
 *   <li>注入方法为 private</li>
 *   <li>不使用 public static 方法</li>
 *   <li>注册到通用 mixins 数组（非客户端类）</li>
 * </ul>
 */
@Mixin(RoleMethodDispatcher.class)
public abstract class MiserTeachingMixin {

    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V",
            at = @At("TAIL"))
    private static void dim201$onFinishQuest(Player player, String quest, int taskStreak,
                                              boolean isParallelTask, CallbackInfo ci) {
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer sp)) return;

        // 检查玩家背包是否持有守财奴的教诲卡牌
        if (!MiserTeachingCard.hasCard(sp.getInventory().items)) return;

        // 额外给予25金币奖励
        com.dim201.dim201addon.item.card.CardUtils.addGold(sp, MiserTeachingCard.QUEST_REWARD);
        com.dim201.dim201addon.item.card.CardUtils.notify(sp,
                "§d守财奴的教诲§r§7 · 任务奖励 §6" + MiserTeachingCard.QUEST_REWARD + " §7金币");
    }
}
