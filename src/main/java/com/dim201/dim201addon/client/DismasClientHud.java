package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.DismasPlayerComponent;
import com.dim201.dim201addon.role.DismasRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 强盗 HUD：左下角显示当前理智值（含判定线 50 警示）与战斗技巧冷却。
 *
 * <p>理智值来自 SRE 理智组件（自带同步）；技能冷却来自强盗组件（组件仅同步给本人）。
 */
public class DismasClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();
    /** 理智条宽度（像素） */
    private static final int BAR_WIDTH = 90;
    private static final int BAR_HEIGHT = 5;
    /** 理智判定线（与 DismasPlayerComponent.JUDGE_THRESHOLD 一致，用于警示变色） */
    private static final float JUDGE_THRESHOLD = 0.5f;

    public static void register() {
        HudRenderCallback.EVENT.register(new DismasClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(DismasRole.DISMAS)) return;
        DismasPlayerComponent comp = ModComponents.DISMAS.get(mc.player);
        if (comp == null) return;

        float mood = SREPlayerMoodComponent.KEY.get(mc.player).getMood();
        int sanPercent = Math.round(mood * 100f);

        int x = 10;
        int y = graphics.guiHeight() - 66;
        // 背景：高度扩展以容纳新增的 死门抗性 + 折磨/美德 两行
        HudPanels.draw(graphics, x - 5, y - 5, x + BAR_WIDTH + 34, y + 44, 0xFFAAAAAA);

        // 行1：理智条 + 数值（低于判定线变红警示折磨判定区）
        graphics.drawString(mc.font, Component.literal(
                sanPercent > JUDGE_THRESHOLD * 100 ? "§f理智" : "§c§l理智"), x, y, 0xFFFFFFFF);
        int barY = y + 10;
        graphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF3A3A3A);
        int sanColor = sanPercent > JUDGE_THRESHOLD * 100 ? 0xFF55C5FF : 0xFFFF5555;
        graphics.fill(x, barY, x + (int) (mood * BAR_WIDTH), barY + BAR_HEIGHT, sanColor);
        String sanText = (sanPercent > JUDGE_THRESHOLD * 100 ? "§f" : "§c§l") + sanPercent;
        graphics.drawString(mc.font, Component.literal(sanText), x + BAR_WIDTH + 4, barY - 4, 0xFFFFFFFF);

        // 行2：战斗技巧冷却
        int skillY = barY + BAR_HEIGHT + 4;
        String skillText;
        if (comp.skillCooldown > 0) {
            skillText = "§c战斗技巧: §f" + String.format("%.1f", comp.skillCooldown / 20.0f) + "s";
        } else {
            skillText = "§a战斗技巧: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(skillText), x, skillY, 0xFFFFFFFF);

        // 行3：死门抗性（基础 20% 未消耗时叠加 + 坚定层数 ×50%）
        // 例：基础未消耗 + 坚定 1 层 → 70%；基础已消耗 + 坚定 0 层 → 0%
        int resY = skillY + 10;
        int resPercent = (int) Math.round(comp.getDeathResistance() * 100);
        String resText = "§6死门抗性: §f" + resPercent + "%";
        graphics.drawString(mc.font, Component.literal(resText), x, resY, 0xFFFFFFFF);

        // 行4：折磨/美德当前生效效果（NONE 时不显示）
        int effectY = resY + 10;
        String effectText = null;
        switch (comp.phase) {
            case TORMENT -> effectText = "§4§l折磨·" + (comp.tormentType != null ? comp.tormentType.display : "?");
            case VIRTUE -> effectText = "§a§l美德·" + (comp.virtueType != null ? comp.virtueType.display : "?");
            case NONE -> { /* 空闲不显示 */ }
        }
        if (effectText != null) {
            graphics.drawString(mc.font, Component.literal(effectText), x, effectY, 0xFFFFFFFF);
        }
    }
}
