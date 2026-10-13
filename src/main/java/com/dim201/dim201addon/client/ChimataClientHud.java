package com.dim201.dim201addon.client;

import com.dim201.dim201addon.role.ChimataRole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.world.entity.player.Player;

/**
 * 天弓千亦客户端逻辑。
 *
 * <p>仅注册杀手直觉高亮事件：让千亦在直觉中显示为名字的渐变色（彩虹），
 * 覆盖 SRE 默认的中立阵营橙色高亮。
 *
 * <p>原理：{@link RoleInstinctEvents#TARGET_HIGHLIGHT_EVENT} 在直觉管线中最先被处理，
 * 返回 {@link TrueFalseAndCustomResult#custom(Object)} 会立即采用该颜色，
 * 跳过后续的「中立→橙色」默认分支。颜色取自角色自身的 {@code color()}（彩虹渐变），
 * 与头顶名字颜色一致。
 */
public class ChimataClientHud {

    private ChimataClientHud() {
    }

    public static void register() {
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(ChimataRole.CHIMATA_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    // 直觉关闭时不干预，走 SRE 默认逻辑（无高亮）
                    if (!isInstinctEnabled) {
                        return TrueFalseAndCustomResult.pass();
                    }
                    // 只在玩家目标上生效
                    if (!(target instanceof Player targetPlayer)) {
                        return TrueFalseAndCustomResult.pass();
                    }
                    var targetRole = SREClient.gameComponent.getRole(targetPlayer);
                    if (targetRole == null) {
                        return TrueFalseAndCustomResult.pass();
                    }
                    // 用角色自身的 color()（彩虹渐变）作为直觉高亮色
                    return TrueFalseAndCustomResult.custom(targetRole.color());
                });
    }
}
