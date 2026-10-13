package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 依神紫菀 - 不获得 SRE 原生任务完成金币（修饰符/卡牌奖励保留）
 *
 * <p>目标：只扣除 SRE {@link RoleMethodDispatcher#callOnFinishQuest} 方法内部通过
 * {@code shopComponent.addToBalance(...)} 发放的「平民奖励+连击奖励 / 杀手驱动奖励」。
 * 修饰符（任务大师 +25）、卡牌（守财奴教诲 +15）、角色 onFinishQuest 奖励均保留。
 *
 * <p>实现方式：
 * <pre>
 * HEAD → snap1 调用前金币，同时 snap2=snap1
 *   SRE 方法内部 addToBalance：每执行一次，用 @ModifyVariable 或 INVOKE 后置更新 snap2
 *   → 为了规避 Mixin 0.8.7 下 "No refMap loaded" 与 INVOKE target 写错过 0 匹配的问题，
 *     这里采用对 shopComponent.addToBalance 的 INVOKE（SRE 包内类 refMap 不敏感）
 *     在每次 addToBalance 后把"当前最新金币"写回 snap2。
 *   role.onFinishQuest / TAIL 注入的修饰符/卡牌奖励 不再更新 snap2。
 * TAIL → 扣 (snap2 - snap1)
 * </pre>
 *
 * <p>注：@Inject 取消不做，避免破坏 ProgressionDataManager.onRoundQuestFinished
 * 和 role.onFinishQuest（后续任务分配依赖这些执行）。
 */
@Mixin(RoleMethodDispatcher.class)
public abstract class ShionQuestGoldMixin {

    // snap1：callOnFinishQuest 调用前的金币（非紫菀为 null，整段跳过）
    private static final ThreadLocal<Integer> SNAP_BEFORE = new ThreadLocal<>();
    // snap2：SRE 原生 addToBalance 全部执行完后的金币
    //        初始等于 snap1；每次命中 shopComponent.addToBalance 后更新
    private static final ThreadLocal<Integer> SNAP_AFTER_QUEST_REWARD = new ThreadLocal<>();

    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V",
            at = @At("HEAD"))
    private static void dim201$shionQuestHead(Player player, String quest, int taskStreak,
                                              boolean isParallelTask, CallbackInfo ci) {
        if (player.level().isClientSide) {
            SNAP_BEFORE.set(null);
            SNAP_AFTER_QUEST_REWARD.set(null);
            return;
        }
        if (!(player instanceof ServerPlayer sp)) {
            SNAP_BEFORE.set(null);
            SNAP_AFTER_QUEST_REWARD.set(null);
            return;
        }
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw != null && gw.isRole(sp, com.dim201.dim201addon.role.ShionRole.SHION)) {
            int g = CardUtils.getGold(sp);
            SNAP_BEFORE.set(g);
            SNAP_AFTER_QUEST_REWARD.set(g); // 初始化为当前值
        } else {
            SNAP_BEFORE.set(null);
            SNAP_AFTER_QUEST_REWARD.set(null);
        }
    }

    // 每次 SRE 内部 addToBalance(平民/杀手任务奖励) 执行完后，更新 snap2 为最新金币
    // 这里选择的 target 是 SRE 自身 SREPlayerShopComponent.addToBalance(int)
    // 类全名来源于 sre_source src/main/java/io/wifi/starrailexpress/cca/SREPlayerShopComponent.java:89
    // SRE 包内类非 Minecraft 类 → 不依赖 refMap intermediary 映射
    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V",
            at = @At(value = "INVOKE",
                     target = "Lio/wifi/starrailexpress/cca/SREPlayerShopComponent;addToBalance(I)V",
                     shift = At.Shift.AFTER))
    private static void dim201$shionQuestAfterAddToBalance(Player player, String quest, int taskStreak,
                                                           boolean isParallelTask, CallbackInfo ci) {
        Integer before = SNAP_BEFORE.get();
        if (before == null) return;
        if (player instanceof ServerPlayer sp) {
            SNAP_AFTER_QUEST_REWARD.set(CardUtils.getGold(sp));
        }
    }

    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V",
            at = @At("TAIL"))
    private static void dim201$shionQuestTail(Player player, String quest, int taskStreak,
                                              boolean isParallelTask, CallbackInfo ci) {
        Integer before = SNAP_BEFORE.get();
        SNAP_BEFORE.remove();
        Integer afterBase = SNAP_AFTER_QUEST_REWARD.get();
        SNAP_AFTER_QUEST_REWARD.remove();
        if (before == null || afterBase == null) return;
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer sp)) return;
        // 只没收 SRE 原生任务奖励（afterBase - before），
        // 修饰符（任务大师+25）、卡牌（守财奴教诲+15）、角色 onFinishQuest 奖励均保留
        int diff = afterBase - before;
        if (diff > 0) {
            CardUtils.addGold(sp, -diff);
            CardUtils.notify(sp, "§5紫菀§7 · 贫穷神无法获取任务基础金币（没收 §c-" + diff + "§7）");
        }
    }
}
