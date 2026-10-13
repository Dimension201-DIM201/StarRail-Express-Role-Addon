package com.dim201.dim201addon.mixin;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让被 PelicanManager 吞下的玩家在 isPlayerEliminated 判定中视为"未淘汰"
 *
 * 背景：
 * SRE 的 PelicanManager.stashPlayer 会把被吞玩家切到 SPECTATOR 模式，
 * 而 GameUtils.isPlayerEliminated 用 player.isSpectator() 判断淘汰，
 * 导致被吞的杀手会被误判为"已淘汰" → 胜负判定遍历所有杀手时认为全部淘汰 → 平民提前胜利。
 *
 * SRE 自己的 PelicanAliveCheckMixin 只处理了 isPlayerAliveAndSurvivalIgnoreShitSplit（返回 true=存活），
 * 漏了 isPlayerEliminated 的对称处理。本 Mixin 补上这个缺口，让 stashed 玩家返回 false（未淘汰）。
 *
 * 这对鹈鹕和吞噬者都生效（两者都用 PelicanManager.stashPlayer），是通用修复。
 */
@Mixin(GameUtils.class)
public class StashedPlayerNotEliminatedMixin {

    @Inject(method = "isPlayerEliminated(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void dim201$stashedNotEliminated(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (PelicanManager.isStashed(player)) {
            cir.setReturnValue(false);
        }
    }
}
