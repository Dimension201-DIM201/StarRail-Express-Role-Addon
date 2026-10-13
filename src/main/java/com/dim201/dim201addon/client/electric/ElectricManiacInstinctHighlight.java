package com.dim201.dim201addon.client.electric;

import com.dim201.dim201addon.role.ElectricManiacRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.world.entity.player.Player;

/**
 * 电击狂人杀手直觉范围限制（同款强盗）
 *
 * <p>原理：注册 {@link CommonInstinctEvents#ALIVE_COMMON_BEFORE_EVENT}，
 * 当本地玩家为电击狂人且直觉开启时：
 * <ul>
 *   <li>杀手队友：无距离限制（pass，走默认杀手直觉着色）</li>
 *   <li>触电高亮中的玩家：无视距离与阵营，强制亮黄色（全图高亮优先于范围限制）</li>
 *   <li>其他非队友：超过 10 格则 disallow（不显示高亮）</li>
 * </ul>
 *
 * <p>由于强盗的 10 格限制硬编码在 SRE 的 InstinctManager 中（不可修改），
 * 此处通过事件前置拦截实现相同效果。触电判定必须放在 disallow 之前，
 * 否则提前否决会导致 {@link ElectricManiacShockHighlight} 无法生效。
 */
public final class ElectricManiacInstinctHighlight {

    private static final double INSTINCT_RANGE = 10.0D;

    private ElectricManiacInstinctHighlight() {}

    public static void register() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
            if (!isInstinctEnabled) return TrueFalseAndCustomResult.pass();
            if (!ElectricManiacRole.isElectricManiacClient(self)) return TrueFalseAndCustomResult.pass();
            if (!(target instanceof Player targetPlayer)) return TrueFalseAndCustomResult.pass();
            if (self == targetPlayer) return TrueFalseAndCustomResult.pass();

            // 触电全图高亮优先：无论距离/阵营都强制显示亮黄色
            if (ElectricManiacClientState.shockedPlayersUntil.containsKey(targetPlayer.getUUID())) {
                return TrueFalseAndCustomResult.custom(ElectricManiacClientState.SHOCK_HIGHLIGHT_COLOR);
            }

            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(self.level());
            if (gw == null) return TrueFalseAndCustomResult.pass();

            SRERole targetRole = gw.getRole(targetPlayer);
            // 杀手队友无距离限制
            if (targetRole != null && gw.isKillerTeamRole(targetRole)) {
                return TrueFalseAndCustomResult.pass();
            }
            // 非队友超过 10 格不透视
            if (self.distanceTo(targetPlayer) > INSTINCT_RANGE) {
                return TrueFalseAndCustomResult.disallow();
            }
            return TrueFalseAndCustomResult.pass();
        });
    }
}
