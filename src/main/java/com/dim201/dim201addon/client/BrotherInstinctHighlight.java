package com.dim201.dim201addon.client;

import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 东堂 × 虎杖互相透视高亮（SRE 直觉管线）。
 *
 * <p>原理：注册 {@link CommonInstinctEvents#ALIVE_COMMON_BEFORE_EVENT} 监听器。
 * SRE 的 {@code InstinctManager.getInstinctHighlight} 在判定「直觉是否开启」之前
 * 就会先触发该事件；只要返回 {@link TrueFalseAndCustomResult#custom(Object)}，
 * SRE 就会用原生直觉渲染管线（{@code MinecraftClientMixin.shouldEntityAppearGlowing}
 * + {@code WorldRendererMixin.getTeamColor}）把该实体描边渲染出来——
 * 即使本地玩家没有开启杀手直觉，也能隔墙看到搭档。
 *
 * <p>这不是原版发光药水效果（不产生绿色粒子 / 不修改 entity 状态），
 * 完全复用 SRE 的直觉透视渲染通路。
 */
public final class BrotherInstinctHighlight {

    private BrotherInstinctHighlight() {}

    public static void register() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
            if (BrotherClientState.shouldOutline(target)) {
                return TrueFalseAndCustomResult.custom(BrotherClientState.OUTLINE_COLOR);
            }
            return TrueFalseAndCustomResult.pass();
        });
    }
}
