package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.WeaponMasterPlayerComponent;
import com.dim201.dim201addon.role.WeaponMasterRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 武器大师客户端 HUD：显示四种虚拟零件的数量和下次零件获取倒计时。
 *
 * <p>倒计时使用本地帧插值实时递减，不依赖服务端同步频率。
 */
public class WeaponMasterClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    /** 角色色（古铜色），与 WeaponMasterRole.ROLE_COLOR 一致 */
    private static final int TITLE_COLOR = 0x8B4513;

    /** 本地倒计时（tick），每帧用 deltaTracker 递减 */
    private float clientCountdown = -1;
    /** 上次服务端同步的 partTimer 值，用于检测变化并重置本地倒计时 */
    private int lastSyncedTimer = -1;

    public static void register() {
        HudRenderCallback.EVENT.register(new WeaponMasterClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;

        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(WeaponMasterRole.WEAPON_MASTER)) return;

        WeaponMasterPlayerComponent comp = ModComponents.WEAPON_MASTER.get(mc.player);

        int x = 4;
        int y = mc.getWindow().getGuiScaledHeight() - 55;

        // 面板背景 + 古铜色顶边（3 行 × 12px）
        HudPanels.draw(graphics, 0, y - 5, x + 230, y + 34, TITLE_COLOR);

        // 标题（古铜色）
        graphics.drawString(mc.font, Component.literal("§l[武器大师]"), x, y, TITLE_COLOR);
        y += 12;

        // 零件计数行
        String parts = String.format("§6木质 §f×%d  §7金属 §f×%d  §c火药 §f×%d  §d精密 §f×%d",
                comp.woodParts, comp.metalParts, comp.gunpowder, comp.precisionParts);
        graphics.drawString(mc.font, parts, x, y, 0xFFFFFF);
        y += 12;

        // 倒计时：检测服务端同步值变化，变化时重置本地倒计时
        if (comp.partTimer != lastSyncedTimer) {
            clientCountdown = comp.partTimer;
            lastSyncedTimer = comp.partTimer;
        }
        // 每帧用渲染 delta 递减（实时显示）
        float delta = deltaTracker.getRealtimeDeltaTicks();
        clientCountdown -= delta;
        if (clientCountdown < 0) clientCountdown = 0;

        int secs = Math.max(0, (int) Math.ceil(clientCountdown / 20.0f));
        graphics.drawString(mc.font,
                Component.literal("§a下次获取零件: " + secs + "s"),
                x, y, 0xFFFFFF);
    }
}
