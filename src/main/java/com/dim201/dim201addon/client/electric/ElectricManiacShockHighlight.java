package com.dim201.dim201addon.client.electric;

import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.world.entity.player.Player;

/**
 * 触电全图高亮渲染
 *
 * <p>原理：注册 {@link CommonInstinctEvents#ALIVE_COMMON_BEFORE_EVENT}，
 * 对处于触电高亮状态的玩家返回亮黄色，使所有玩家都能看到（不依赖直觉开启）。
 */
public final class ElectricManiacShockHighlight {

    private ElectricManiacShockHighlight() {}

    public static void register() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
            if (!(target instanceof Player targetPlayer)) return TrueFalseAndCustomResult.pass();
            if (ElectricManiacClientState.shockedPlayersUntil.containsKey(targetPlayer.getUUID())) {
                return TrueFalseAndCustomResult.custom(ElectricManiacClientState.SHOCK_HIGHLIGHT_COLOR);
            }
            return TrueFalseAndCustomResult.pass();
        });
    }
}
