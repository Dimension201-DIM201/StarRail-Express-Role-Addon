package com.dim201.dim201addon.client;

import com.dim201.dim201addon.cooperation.BrotherSyncManager;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 兄弟默契 HUD：显示当前默契能量点数（共享，上限 200）。
 *
 * <p>本地玩家为东堂葵或虎杖悠仁时始终显示（不依赖对局是否运行、不依赖能量点数），
 * 数据源为 {@link BrotherClientState}（由服务端 S2C 包同步）。
 */
public class BrotherHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new BrotherHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;

        // 直接用 isRole 判断角色，不卡 isRunning()，确保从角色分配到对局结束始终显示
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gw == null) return;
        boolean isTodo = gw.isRole(mc.player, TodoAoiRole.TODO_AOI);
        boolean isIta = gw.isRole(mc.player, ItadoriRole.ITADORI);
        if (!isTodo && !isIta) return;

        int points = BrotherClientState.getPoints();
        boolean extended = BrotherClientState.isExtended();

        // 标题
        Component title = Component.literal("§6兄弟默契");
        // 能量条文字：当前 / 上限
        Component energyText = Component.literal(
                "§e" + points + " §7/ §f" + BrotherSyncManager.MAX_POINTS);
        // 扩展感知指示
        Component rangeText = extended
                ? Component.literal("§a感知范围: 30格")
                : Component.literal("§7感知范围: 10格");

        int barWidth = 120;
        int barHeight = 8;
        int padding = 6;

        int titleWidth = mc.font.width(title);
        int energyWidth = mc.font.width(energyText);
        int rangeWidth = mc.font.width(rangeText);
        int boxWidth = Math.max(Math.max(titleWidth, energyWidth), Math.max(rangeWidth, barWidth)) + padding * 2;
        int boxHeight = mc.font.lineHeight * 2 + barHeight + padding * 3 + 4;

        // 右下角定位
        int x = graphics.guiWidth() - boxWidth - 10;
        int y = graphics.guiHeight() - boxHeight - 10;

        // 半透明背景
        HudPanels.draw(graphics, x, y, x + boxWidth, y + boxHeight, 0xFF7D3CB5);

        // 标题
        graphics.drawString(mc.font, title, x + padding, y + padding, 0xFFFFD54F);

        // 能量条
        int barX = x + padding;
        int barY = y + padding + mc.font.lineHeight + 2;
        // 背景条
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        // 填充条
        float ratio = Math.min(1.0F, (float) points / BrotherSyncManager.MAX_POINTS);
        int fillWidth = (int) (barWidth * ratio);
        int barColor = ratio >= 1.0F ? 0xFFFFD700 : 0xFFDAA520;
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);

        // 能量数值
        int textY = barY + barHeight + 2;
        graphics.drawString(mc.font, energyText, barX, textY, 0xFFFFFFFF);
        graphics.drawString(mc.font, rangeText, barX, textY + mc.font.lineHeight, 0xFFFFFFFF);
    }
}
