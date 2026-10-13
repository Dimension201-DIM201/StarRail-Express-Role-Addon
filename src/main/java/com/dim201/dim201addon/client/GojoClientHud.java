package com.dim201.dim201addon.client;

import com.dim201.dim201addon.GojoPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.GojoRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class GojoClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new GojoClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(GojoRole.GOJO)) return;

        GojoPlayerComponent comp = ModComponents.GOJO.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 90;

        HudPanels.draw(graphics, x - 5, y - 5, x + 200, y + 90, 0xFF6FA8DC);

        String titleColor = "§b";
        graphics.drawString(mc.font, Component.literal(titleColor + "§l［向南］五条悟 §8| §7最强咒术师"), x, y, 0xFFFFFFFF);
        y += 14;

        int duelCount = comp.duelCount;
        graphics.drawString(mc.font, Component.literal("§f宿傩对决次数: §5" + duelCount), x, y, 0xFFFFFFFF);
        y += 12;

        int winProb = comp.winProbability;
        int deathProb = comp.deathProbability;
        int posProb = comp.positiveProbability;
        int negProb = comp.negativeProbability;
        // 组件里保存的是「百分比 ×10」的整数，需要除以 10 显示（支持 0.5% 2.5% 等小数）
        String winProbText = GojoPlayerComponent.formatProb10(winProb);
        String deathProbText = GojoPlayerComponent.formatProb10(deathProb);
        String posProbText = GojoPlayerComponent.formatProb10(posProb);
        String negProbText = GojoPlayerComponent.formatProb10(negProb);

        int barWidth = 170;
        int barHeight = 6;

        // 胜利概率条
        graphics.drawString(mc.font, Component.literal("§a胜利概率: §f" + winProbText), x, y, 0xFFFFFFFF);
        y += 10;
        drawBar(graphics, x, y, barWidth, barHeight, winProb / 10.0f, 0xFF00FF00);
        y += 12;

        // 死亡概率条
        graphics.drawString(mc.font, Component.literal("§c死亡概率: §f" + deathProbText), x, y, 0xFFFFFFFF);
        y += 10;
        drawBar(graphics, x, y, barWidth, barHeight, deathProb / 10.0f, 0xFFFF0000);
        y += 12;

        // 其他概率提示
        graphics.drawString(mc.font, Component.literal("§7正面效果: §e" + posProbText + " §7| §7负面效果: §6" + negProbText), x, y, 0xFFFFFFFF);
        y += 12;

        // 无下限护盾状态（一次性，击破不自动再生）
        String shieldText = comp.shieldActive ? "§a✔ 无下限术式护盾生效中" : "§c✘ 护盾已消耗（一次性，不自动再生）";
        graphics.drawString(mc.font, Component.literal(shieldText), x, y, 0xFFFFFFFF);
        y += 12;

        // 六眼提示
        graphics.drawString(mc.font, Component.literal("§7§o六眼透视已开启 · 按直觉键查看玩家"), x, y, 0xFFFFFFFF);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, int height, float percentFloat, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        graphics.fill(x, y, x + width, y + height, 0xFF333333);
        float clamped = Math.max(0.0f, Math.min(100.0f, percentFloat));
        int fill = (int) ((clamped / 100.0f) * width);
        graphics.fill(x, y, x + fill, y + height, color);
    }
}
