package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.role.MadmanRole;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阻止疯子获得正向 san 值回复 Mixin
 *
 * <p>原理：SRE 在玩家完成任务时通过 {@link SREPlayerMoodComponent#addMood(float)}
 * 给予正向 moodGain（如 {@code GameConstants.MOOD_GAIN}）。疯子每 tick 强制
 * {@code setMood(0.15f)} 只在 {@code mood >= 0.2f} 时执行，任务完成的 moodGain
 * 会让 mood 瞬间突破 0.2 阈值，导致二级无理智（mood &lt; 0.2）状态在该 tick 内
 * 一瞬间失效（幻觉物品渲染恢复正常、低 san 视觉效果中断）。
 *
 * <p>本 Mixin 在 HEAD 拦截 addMood：
 * <ul>
 *   <li>持有者是疯子且 {@code value > 0}（回复 san）→ cancel，mood 维持 0.15f</li>
 *   <li>{@code value <= 0}（如触电 -0.2f、诅咒 -rate 等）→ 不拦截，正常生效
 *       （疯子下一 tick 也会被 setMood(0.15f) 拉回）</li>
 * </ul>
 *
 * <p>镇静剂生效时由 {@code SedativeManager} 独立锁定为 0.5f，不经过 addMood，
 * 不受本 Mixin 影响。
 */
@Mixin(SREPlayerMoodComponent.class)
public abstract class MadmanMoodGainCancelMixin {

    @Inject(method = "addMood(F)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void dim201$cancelMoodGainIfMadman(float value, CallbackInfo ci) {
        // 只拦截正向 addMood（回复 san 值）；负向（减少 san 值）保留
        if (value <= 0f) return;
        SREPlayerMoodComponent self = (SREPlayerMoodComponent) (Object) this;
        Player player = self.getPlayer();
        if (player == null) return;
        if (MadmanRole.isMadman(player)) {
            ci.cancel();
        }
    }
}
