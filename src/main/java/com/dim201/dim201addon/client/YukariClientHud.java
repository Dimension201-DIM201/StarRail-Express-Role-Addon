package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.YukariPlayerComponent;
import com.dim201.dim201addon.YukariRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.agmas.noellesroles.client.NoellesrolesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class YukariClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new YukariClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        // 检查是否是 Yukari 角色
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(YukariRole.YUKARI)) return;

        YukariPlayerComponent comp = ModComponents.YUKARI.get(mc.player);
        if (comp == null) return;

        // HUD 位置（屏幕右下角）
        int x = 10;
        int y = graphics.guiHeight() - 132;

        // 背景框
        HudPanels.draw(graphics, x - 5, y - 5, x + 140, y + 132, 0xFF800080);

        // 标题
        graphics.drawString(mc.font, Component.literal("§5§l境界操纵"), x, y, 0xFFAA00FF);

        y += 14;

        // 认真模式状态
        if (comp.isSeriousMode) {
            float seriousSeconds = comp.seriousModeDuration / 20.0f;
            String seriousText = "§4§l认真模式 §f" + String.format("%.1f", seriousSeconds) + "s";
            graphics.drawString(mc.font, Component.literal(seriousText), x, y, 0xFFFFFFFF);
            y += 12;
        }

        // 当前技能
        String skillText = "§f技能: §d" + comp.currentSkill.displayName;
        graphics.drawString(mc.font, Component.literal(skillText), x, y, 0xFFFFFFFF);

        y += 12;

        // 切换技能提示
        String switchText = "§7切换: §e" + NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage().getString() + " §7切换技能";
        graphics.drawString(mc.font, Component.literal(switchText), x, y, 0xFFFFFFFF);

        y += 12;

        // 境界层数（使用startingPlayerCount计算上限）
        int requiredLevel = Math.max(1, comp.startingPlayerCount / 2);
        String realmText = "§f境界: §d" + comp.realmLevel + "§7/§f" + requiredLevel;
        if (comp.realmLevel >= requiredLevel) {
            realmText += " §a✓";
        }
        graphics.drawString(mc.font, Component.literal(realmText), x, y, 0xFFFFFFFF);

        y += 12;

        // 隐身切换 CD
        String toggleText;
        if (comp.toggleCooldown > 0) {
            float seconds = comp.toggleCooldown / 20.0f;
            toggleText = "§c切换CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            toggleText = "§a切换CD: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(toggleText), x, y, 0xFFFFFFFF);

        y += 12;

        // 连线 CD
        String connectText;
        if (comp.connectCooldown > 0) {
            float seconds = comp.connectCooldown / 20.0f;
            connectText = "§c境界展开CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            connectText = "§a境界展开CD: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(connectText), x, y, 0xFFFFFFFF);

        y += 12;

        // 境界线 CD
        String boundaryText;
        if (comp.boundaryLineCooldown > 0) {
            float seconds = comp.boundaryLineCooldown / 20.0f;
            boundaryText = "§c漂流物CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            boundaryText = "§a漂流物CD: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(boundaryText), x, y, 0xFFFFFFFF);

        y += 12;

        // 坐标数量
        String coordText = "§f坐标: §d" + comp.coordinates.size() + "§7/§f10 §7(右键方块设置)";
        graphics.drawString(mc.font, Component.literal(coordText), x, y, 0xFFFFFFFF);

        y += 12;

        // 当前状态
        String stateText = comp.isInvisible ? "§5[隙间中]" : "§d[显形]";
        if (comp.isConnecting) {
            stateText += " §5[连线中]";
        }
        graphics.drawString(mc.font, Component.literal(stateText), x, y, 0xFFFFFFFF);
    }
}