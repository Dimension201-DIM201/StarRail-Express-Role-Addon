package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import com.dim201.dim201addon.role.SeikibankiRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 赤蛮奇客户端 HUD（精简版）。
 *
 * <p>技能激活时不显示；冷却中在右下角显示倒计时（无背景框）。
 * 冷却数据源为 SRE 通用技能组件，但倒计时采用本地插值（参考武器大师），
 * 每帧实时递减，不受服务端同步频率限制。
 */
public class SeikibankiClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    /** 本地倒计时（tick），每帧用 deltaTracker 递减 */
    private float clientCountdown = -1;
    /** 上次服务端同步的冷却值，用于检测变化并重置本地倒计时 */
    private int lastSyncedCooldown = -1;

    public static void register() {
        HudRenderCallback.EVENT.register(new SeikibankiClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        if (!gameWorld.isRole(mc.player, SeikibankiRole.SEIKIBANKI)) return;

        SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.get(mc.player);
        if (comp == null || comp.skillActive) return;

        int syncedCooldown = SREAbilityPlayerComponent.KEY.get(mc.player).cooldown;

        // 服务端同步值变化时，重置本地倒计时
        if (syncedCooldown != lastSyncedCooldown) {
            clientCountdown = syncedCooldown;
            lastSyncedCooldown = syncedCooldown;
        }

        // 每帧用渲染 delta 实时递减
        float delta = deltaTracker.getRealtimeDeltaTicks();
        clientCountdown -= delta;
        if (clientCountdown < 0) clientCountdown = 0;

        if (clientCountdown <= 0) return;

        int secs = (int) Math.ceil(clientCountdown / 20.0f);
        Component text = Component.literal("§d「飞头」 §7冷却 §e" + secs + "s");

        int textWidth = mc.font.width(text);
        int x = graphics.guiWidth() - textWidth - 10;
        int y = graphics.guiHeight() - 20;
        // 右下角小面板背景 + 深粉顶边
        com.dim201.dim201addon.client.HudPanels.draw(
                graphics, x - 4, y - 3, graphics.guiWidth() - 6, y + 12, 0xFFFF1493);
        graphics.drawString(mc.font, text, x, y, 0xFFFFFFFF);
    }
}
