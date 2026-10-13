package com.dim201.dim201addon.client.contract;

import com.dim201.dim201addon.DemonContractorComponent;
import com.dim201.dim201addon.contract.ContractCost;
import com.dim201.dim201addon.contract.ContractPower;
import com.dim201.dim201addon.role.DemonContractorRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 恶魔契约的客户端透视规则（SRE 直觉管线，不依赖直觉键开启）：
 * <ol>
 *   <li>无声者：任何视角下都不显示高亮（最高优先级）</li>
 *   <li>恶魔悬赏：杀手眼中，被悬赏者以金色描边</li>
 *   <li>危险感知：契约者 15 格内所有玩家以淡青色描边</li>
 * </ol>
 */
public final class DemonContractInstinct {

    private DemonContractInstinct() {}

    /** 悬赏金（gold） */
    private static final int BOUNTY_COLOR = 0xFFFFD700;
    /** 危险感知淡青 */
    private static final int SENSE_COLOR = 0xFF88DDFF;
    private static final double SENSE_RANGE = 15.0D;

    public static void register() {
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, enabled) -> {
            if (!(target instanceof Player targetPlayer)) return TrueFalseAndCustomResult.pass();
            LocalPlayer me = Minecraft.getInstance().player;
            if (me == null || me == targetPlayer) return TrueFalseAndCustomResult.pass();

            DemonContractorComponent targetComp = DemonContractorComponent.KEY.getNullable(targetPlayer);
            DemonContractorComponent selfComp = DemonContractorComponent.KEY.getNullable(me);

            // 无声者：从一切高亮中消失
            if (targetComp != null && targetComp.hasPower(ContractPower.SILENCE)) {
                return TrueFalseAndCustomResult.disallow();
            }

            // 恶魔悬赏：仅杀手视角金色
            if (targetComp != null && targetComp.hasBrand(ContractCost.BOUNTY)) {
                SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(me.level());
                if (gw != null && gw.isKillerTeam(me)) {
                    return TrueFalseAndCustomResult.custom(BOUNTY_COLOR);
                }
            }

            // 危险感知：契约者 15 格内透视所有人
            if (selfComp != null
                    && DemonContractorRole.isContractorClient(me)
                    && selfComp.hasPower(ContractPower.DANGER_SENSE)
                    && me.distanceTo(targetPlayer) <= SENSE_RANGE) {
                return TrueFalseAndCustomResult.custom(SENSE_COLOR);
            }

            return TrueFalseAndCustomResult.pass();
        });
    }
}
