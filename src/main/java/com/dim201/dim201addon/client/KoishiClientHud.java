package com.dim201.dim201addon.client;

import com.dim201.dim201addon.KoishiPlayerComponent;
import com.dim201.dim201addon.KoishiRole;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class KoishiClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new KoishiClientHud());

        // 被看事件：古明地恋不会被杀手直觉透视（与 SRE 小透明 GHOST、芙兰、大怪盗同款）。
        // 注册在「被看者」角色 ID 上：杀手阵营观察者且本人存活时，对 Koishi 返回 disallow()。
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(KoishiRole.KOISHI_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (SREClient.gameComponent != null
                            && SREClient.isPlayerAliveAndInSurvival()
                            && SREClient.gameComponent.isKillerTeam(viewer)) {
                        return TrueFalseAndCustomResult.disallow();
                    }
                    return TrueFalseAndCustomResult.pass();
                });
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(KoishiRole.KOISHI)) return;

        KoishiPlayerComponent comp = ModComponents.KOISHI.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 95;

        // 背景框
        HudPanels.draw(graphics, x - 5, y - 5, x + 150, y + 95, 0xFF7CFC00);

        // 标题
        String titleColor = comp.currentState == KoishiPlayerComponent.State.ID ? "§a" : "§d";
        graphics.drawString(mc.font, Component.literal(titleColor + "§l古明地恋"), x, y, 0xFFFFFFFF);

        y += 14;

        // 当前状态
        String stateText = "§f状态: " + titleColor + "§l" + comp.currentState.displayName;
        graphics.drawString(mc.font, Component.literal(stateText), x, y, 0xFFFFFFFF);

        y += 12;

        // 切换倒计时
        float stateSeconds = comp.stateTimer / 20.0f;
        String timerText = "§f切换倒计时: §e" + String.format("%.1f", stateSeconds) + "s";
        graphics.drawString(mc.font, Component.literal(timerText), x, y, 0xFFFFFFFF);

        y += 12;

        // 技能冷却
        String skillText;
        if (comp.isTeleporting) {
            float delaySeconds = comp.teleportDelay / 20.0f;
            skillText = "§d§l传送中... §f" + String.format("%.1f", delaySeconds) + "s";
        } else if (comp.skillCooldown > 0) {
            float cdSeconds = comp.skillCooldown / 20.0f;
            skillText = "§c技能CD: §f" + String.format("%.1f", cdSeconds) + "s";
        } else {
            skillText = "§a技能就绪 §7| §f梅莉小姐的电话";
        }
        graphics.drawString(mc.font, Component.literal(skillText), x, y, 0xFFFFFFFF);

        y += 12;

        // 当前状态说明
        String descText = comp.currentState == KoishiPlayerComponent.State.ID 
            ? "§7「无意识的妖怪」" 
            : "§7「超我觉醒」";
        graphics.drawString(mc.font, Component.literal(descText), x, y, 0xFFFFFFFF);
    }
}