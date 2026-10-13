package com.dim201.dim201addon.client;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.LoneWolfRole;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 独狼无人机高亮 — 无人机周围 8 格玩家对独狼持续显示红色描边。
 *
 * <p>通过 SRE 直觉管线实现，无需开启直觉键，常亮被动效果。
 */
public final class LoneWolfMarkInstinctHighlight {

    /** 无人机探测高亮色：橙红 */
    private static final int MARK_COLOR = 0xFFFF4500;
    /** 清算时间全图高亮色：血红 */
    private static final int SETTLEMENT_COLOR = 0xFFFF1A1A;

    private LoneWolfMarkInstinctHighlight() {}

    public static void register() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, enabled) -> {
            if (!(target instanceof Player targetPlayer)) return TrueFalseAndCustomResult.pass();
            LocalPlayer me = Minecraft.getInstance().player;
            if (me == null || me == targetPlayer) return TrueFalseAndCustomResult.pass();

            // 仅独狼视角
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(me.level());
            if (gw == null || !gw.isRole(me, LoneWolfRole.LONE_WOLF)) {
                return TrueFalseAndCustomResult.pass();
            }

            LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(me);
            if (comp == null) return TrueFalseAndCustomResult.pass();

            // 清算时间：全图玩家高亮
            if (comp.settlementActive) {
                return TrueFalseAndCustomResult.custom(SETTLEMENT_COLOR);
            }

            // 无人机范围探测：周围 8 格玩家高亮
            if (comp.detectedPlayerUuids.contains(targetPlayer.getUUID())) {
                return TrueFalseAndCustomResult.custom(MARK_COLOR);
            }

            return TrueFalseAndCustomResult.pass();
        });
    }
}
