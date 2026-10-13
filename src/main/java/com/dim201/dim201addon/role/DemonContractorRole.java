package com.dim201.dim201addon.role;

import com.dim201.dim201addon.DemonContractorComponent;
import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.contract.ContractPower;
import com.dim201.dim201addon.contract.DemonContractManager;
import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 恶魔契约者 - 平民阵营
 *
 * <p>核心机制：右键点击尸体引导 3 秒签订恶魔契约。
 * 契约时先索取力量（可多选，代价隐藏），签订后代价不可反悔地揭晓；
 * 总代价达到 10 点直接死于契约。每场最多契约 3 具尸体。
 *
 * <p>获得「冲刺」力量后，按技能键释放冲刺。
 */
public class DemonContractorRole {

    public static final ResourceLocation DEMON_CONTRACTOR_ID =
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "demon_contractor");

    public static SRERole DEMON_CONTRACTOR;

    public static void register() {
        DEMON_CONTRACTOR = TMMRoles.registerRole(new DemonContractorImpl());
    }

    /** 通用判定（服务端/客户端） */
    public static boolean isContractor(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        return gw != null && gw.isRole(player, DEMON_CONTRACTOR);
    }

    /** 客户端判定（不依赖 isRunning，避免同步时序问题） */
    public static boolean isContractorClient(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        return gw != null && gw.isRole(player, DEMON_CONTRACTOR);
    }

    private static class DemonContractorImpl extends NormalRole {

        DemonContractorImpl() {
            super(
                    DEMON_CONTRACTOR_ID,
                    0xFF3B0A4A,           // 暗紫色
                    true,                 // 平民阵营
                    false,                // 不能使用杀手直觉
                    MoodType.REAL,
                    400,
                    false
            );
            this.setCanSeeTime(true)
                    .setCanUseInstinct(false)
                    .setServerGameTickEvent(this::handleServerTick);
        }

        /** 技能键：释放冲刺（契约改由右键尸体触发，避免按键冲突） */
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            DemonContractManager.useDash(player);
            return true;
        }

        /** 右键尸体：开始契约引导（服务端钩子，消费事件阻止继续传递） */
        @Override
        public InteractionResult rightClickEntity(Player player, Entity victim) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!(victim instanceof PlayerBodyEntity body)) return InteractionResult.PASS;
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) return InteractionResult.PASS;
            DemonContractManager.beginChanneling(sp, body);
            return InteractionResult.SUCCESS;
        }

        /** 每 tick：引导/处决/力量与烙印持续效果 */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, DEMON_CONTRACTOR)) return;
            DemonContractManager.handleTick(player);
        }

        /** 财富之手：完成任务额外获得 15 金币 */
        @Override
        public void onFinishQuest(Player player, String quest) {
            super.onFinishQuest(player, quest);
            if (!(player instanceof ServerPlayer sp)) return;
            DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(sp);
            if (comp != null && comp.hasPower(ContractPower.FORTUNE)) {
                CardUtils.addGold(sp, 15);
                CardUtils.notify(sp, "§6财富之手：§7任务额外收益 §615 金币");
            }
        }
    }
}
