package com.dim201.dim201addon.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 角色 HUD 面板统一样式工具
 *
 * <p>所有角色信息面板统一为「半透明深色背景 + 角色色 2px 顶边」样式，
 * 集中在此处绘制，便于后续整体调整风格（SRP：样式与各角色内容解耦）。
 */
public final class HudPanels {

    private HudPanels() {}

    /** 半透明深色背景 */
    public static final int BG_COLOR = 0x88000000;
    /** 顶边高度（px） */
    public static final int ACCENT_HEIGHT = 2;

    /**
     * 绘制面板背景。
     *
     * @param left   左边界（含）
     * @param top    上边界（含）
     * @param right  右边界（不含）
     * @param bottom 下边界（不含）
     * @param accent 角色色（24 位 RGB 或带 alpha，自动补不透明 alpha）
     */
    public static void draw(GuiGraphics graphics, int left, int top, int right, int bottom, int accent) {
        graphics.fill(left, top, right, bottom, BG_COLOR);
        graphics.fill(left, top, right, top + ACCENT_HEIGHT, 0xFF000000 | (accent & 0x00FFFFFF));
    }
}
