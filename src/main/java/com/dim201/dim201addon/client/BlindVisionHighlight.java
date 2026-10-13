package com.dim201.dim201addon.client;

import com.dim201.dim201addon.role.BlindRole;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 盲人「心之眼」透视（SRE 直觉管线）
 *
 * 8 格内的所有玩家对盲人玩家以高亮描边渲染。
 * 走 SRE 原生直觉渲染通路（isCurrentlyGlowing → getTeamColor），
 * 不开启直觉也能隔墙看到，与 {@link BrotherInstinctHighlight} 原理一致。
 */
public final class BlindVisionHighlight {

    private BlindVisionHighlight() {}

    /** 淡蓝色（light blue），与盲人「心之眼」主题一致 */
    private static final int VISION_COLOR = 0xFF87CEEB;

    public static void register() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
            if (shouldOutline(target)) {
                return TrueFalseAndCustomResult.custom(VISION_COLOR);
            }
            return TrueFalseAndCustomResult.pass();
        });
    }

    /**
     * 本地玩家为盲人时，8 格内的其他玩家全部透视
     */
    private static boolean shouldOutline(net.minecraft.world.entity.Entity target) {
        if (!(target instanceof Player targetPlayer)) return false;
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null) return false;
        if (!BlindRole.isBlindClient(self)) return false;
        if (self == target) return false;
        return self.distanceTo(targetPlayer) <= BlindRole.VISION_RANGE;
    }
}
