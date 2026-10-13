package com.dim201.dim201addon.client;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.LoneWolfRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 独狼客户端 HUD。
 *
 * <p>右下角显示：
 * <ul>
 *   <li>无人机状态（待机/操控中/冷却）+ 剩余时间</li>
 *   <li>无人机探测到的周围玩家名列表</li>
 * </ul>
 */
public class LoneWolfClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    private float clientCountdown = -1;
    private int lastSyncedCooldown = -1;

    public static void register() {
        HudRenderCallback.EVENT.register(new LoneWolfClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return;
        if (!gameWorld.isRole(mc.player, LoneWolfRole.LONE_WOLF)) return;

        LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(mc.player);
        if (comp == null) return;

        long gameTime = mc.level.getGameTime();

        // ===== 状态行：清算时间优先，其次无人机状态 =====
        Component statusLine;
        if (comp.settlementActive) {
            statusLine = Component.literal("§4§l【清算时间】 §7全图透视 · 手枪冷却2s");
        } else if (comp.droneActive) {
            int remain = (int) Math.max(0, (comp.droneDestroyTick - gameTime) / 20);
            if (comp.controllingDrone) {
                statusLine = Component.literal("§4「无人机」 §f操控中 §e" + remain + "s");
            } else {
                statusLine = Component.literal("§4「无人机」 §7悬停中 §e" + remain + "s");
            }
        } else {
            int syncedCooldown = SREAbilityPlayerComponent.KEY.get(mc.player).cooldown;
            if (syncedCooldown != lastSyncedCooldown) {
                clientCountdown = syncedCooldown;
                lastSyncedCooldown = syncedCooldown;
            }
            float delta = deltaTracker.getRealtimeDeltaTicks();
            clientCountdown -= delta;
            if (clientCountdown < 0) clientCountdown = 0;

            if (clientCountdown > 0) {
                int secs = (int) Math.ceil(clientCountdown / 20.0f);
                statusLine = Component.literal("§4「无人机」 §7冷却 §e" + secs + "s");
            } else {
                statusLine = Component.literal("§4「无人机」 §a就绪 §8(" + LoneWolfComponent.DEPLOY_COST + " 金币)");
            }
        }

        // ===== 探测行：无人机周围 8 格玩家名（最多 3 个） =====
        Component markLine = null;
        if (!comp.detectedPlayerUuids.isEmpty()) {
            StringBuilder names = new StringBuilder();
            int shown = 0;
            for (java.util.UUID uuid : comp.detectedPlayerUuids) {
                if (shown >= 3) { names.append("§8…"); break; }
                var found = mc.level.getPlayerByUUID(uuid);
                if (found != null) {
                    if (shown > 0) names.append("§7, ");
                    names.append("§c").append(found.getDisplayName().getString());
                    shown++;
                }
            }
            if (shown > 0) {
                markLine = Component.literal("§7探测 §f" + names);
            }
        }

        // ===== 绘制 =====
        int maxWidth = mc.font.width(statusLine);
        if (markLine != null) maxWidth = Math.max(maxWidth, mc.font.width(markLine));

        int x = graphics.guiWidth() - maxWidth - 10;
        int y = graphics.guiHeight() - 20;
        if (markLine != null) y -= 14; // 为探测行腾空间

        int panelHeight = markLine != null ? 30 : 16;
        HudPanels.draw(graphics, x - 4, y - 3, graphics.guiWidth() - 6, y + panelHeight - 3, 0xFF8B0000);

        graphics.drawString(mc.font, statusLine, x, y, 0xFFFFFFFF);
        if (markLine != null) {
            graphics.drawString(mc.font, markLine, x, y + 14, 0xFFFFFFFF);
        }
    }
}
