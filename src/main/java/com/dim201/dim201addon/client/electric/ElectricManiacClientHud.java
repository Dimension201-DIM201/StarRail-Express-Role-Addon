package com.dim201.dim201addon.client.electric;

import com.dim201.dim201addon.ElectricManiacPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.ElectricManiacRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 电击狂人技能 HUD
 *
 * <p>左下角显示：当前技能、两个技能的冷却/蓄力状态、自身静电剩余时间、
 * 静电方块占用数量。冷却与蓄力数据来自 CCA 同步的玩家 tickCount 口径字段。
 */
public class ElectricManiacClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    private static final int PANEL_WIDTH = 184;
    private static final int PANEL_HEIGHT = 86;
    /** 静电方块上限（与服务端 ElectricManiacManager.MAX_BLOCKS 保持一致） */
    private static final int MAX_BLOCKS = 16;

    public static void register() {
        HudRenderCallback.EVENT.register(new ElectricManiacClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;

        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(ElectricManiacRole.ELECTRIC_MANIAC)) return;

        ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - PANEL_HEIGHT - 10;

        // 半透明深色背景 + 深电蓝顶边（与角色主色一致）
        com.dim201.dim201addon.client.HudPanels.draw(
                graphics, x - 5, y - 5, x - 5 + PANEL_WIDTH, y - 5 + PANEL_HEIGHT, 0xFF003399);

        // 用世界游戏时间（与服务端 getGameTime 对齐），不能用 player.tickCount
        // （后者从玩家加入会话开始计数，与世界时间差值不固定，导致倒计时偏差）
        long now = mc.level.getGameTime();

        // 实际绑定的按键名（玩家在控制设置中改键后自动跟随）
        String abilityKey = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind
                .getTranslatedKeyMessage().getString();
        String switchKey = org.agmas.noellesroles.client.NoellesrolesClient.nextAbilityBind
                .getTranslatedKeyMessage().getString();

        // 标题
        graphics.drawString(mc.font, Component.literal("§b§l［电击狂人］ §8| §7静电掌控者"), x, y, 0xFFFFFFFF);
        y += 13;

        // 当前技能选择（▶ 标记当前技能）
        boolean skillCharge = comp.currentSkill == 0;
        String sel1 = skillCharge ? "§b▶ " : "§8  ";
        String sel2 = skillCharge ? "§8  " : "§c▶ ";
        graphics.drawString(mc.font,
                Component.literal(sel1 + "1.静电充能   " + sel2 + "2.静电爆炸 §7(§e" + switchKey + "§7)"),
                x, y, 0xFFFFFFFF);
        y += 12;

        // 技能1：静电充能冷却
        long chargeRemain = Math.max(0L, comp.chargeReadyAtTick - now);
        String skill1Text = chargeRemain > 0
                ? "§7静电充能: §8冷却 " + String.format("%.1f", chargeRemain / 20.0F) + "s"
                : "§7静电充能: §a✔ 就绪 §8(§e" + abilityKey + "§8)";
        graphics.drawString(mc.font, Component.literal(skill1Text), x, y, 0xFFFFFFFF);
        y += 12;

        // 技能2：蓄力中 > 冷却中 > 就绪
        long pendingRemain = Math.max(0L, comp.blastPendingUntilTick - now);
        long blastRemain = Math.max(0L, comp.blastReadyAtTick - now);
        String skill2Text;
        if (pendingRemain > 0) {
            skill2Text = "§c静电爆炸: §f定身蓄力 " + String.format("%.1f", pendingRemain / 20.0F) + "s";
        } else if (blastRemain > 0) {
            skill2Text = "§7静电爆炸: §8冷却 " + (blastRemain + 19) / 20 + "s";
        } else {
            skill2Text = "§7静电爆炸: §a✔ 就绪 §8(§e" + abilityKey + "§8·半径32)";
        }
        graphics.drawString(mc.font, Component.literal(skill2Text), x, y, 0xFFFFFFFF);
        y += 12;

        // 自身静电剩余时间
        long selfRemain = Math.max(0L, comp.electrifiedUntilTick - now);
        String selfText = comp.electrified && selfRemain > 0
                ? "§b⚡ 自身带电中: §f" + String.format("%.1f", selfRemain / 20.0F) + "s"
                : "§7⚡ 自身带电中: §8无";
        graphics.drawString(mc.font, Component.literal(selfText), x, y, 0xFFFFFFFF);
        y += 12;

        // 静电方块占用（客户端状态中即当前维度同步的方块数；单局仅一名电击狂人）
        int blockCount = ElectricManiacClientState.electrifiedBlocks.size();
        String countColor = blockCount >= MAX_BLOCKS ? "§c" : "§f";
        graphics.drawString(mc.font,
                Component.literal("§7静电方块: " + countColor + blockCount + "§8/§7" + MAX_BLOCKS),
                x, y, 0xFFFFFFFF);
    }
}
