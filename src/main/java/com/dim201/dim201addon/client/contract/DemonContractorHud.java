package com.dim201.dim201addon.client.contract;

import com.dim201.dim201addon.DemonContractorComponent;
import com.dim201.dim201addon.contract.ContractPower;
import com.dim201.dim201addon.role.DemonContractorRole;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 恶魔契约者 HUD（纯文字，无背景面板）
 *
 * 显示：剩余契约次数；获得冲刺力量后显示其冷却。
 * 仅本地玩家为契约者时渲染，固定于屏幕右下角，文字右对齐、自底向上排列。
 */
public final class DemonContractorHud {

    private static final int MAX_CONTRACTS = 3;
    private static final int COLOR = 0xFFB266FF;
    /** 距屏幕右/下边缘的内边距 */
    private static final int MARGIN = 6;
    private static final int LINE_H = 12;

    private DemonContractorHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        if (!DemonContractorRole.isContractorClient(mc.player)) return;
        DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(mc.player);
        if (comp == null) return;

        // 组装显示行（标题在最上，冲刺行在最下）
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal("§5§l恶魔契约者"));
        int left = Math.max(0, MAX_CONTRACTS - comp.getContractCount());
        lines.add(Component.literal("§7剩余契约: §f" + left + "/" + MAX_CONTRACTS));
        if (comp.hasPower(ContractPower.DASH)) {
            long now = mc.level.getGameTime();
            if (now >= comp.getDashReadyAt()) {
                lines.add(Component.literal("§a冲刺就绪（按技能键）"));
            } else {
                long sec = Math.max(0, (comp.getDashReadyAt() - now + 19) / 20);
                lines.add(Component.literal("§7冲刺冷却: §f" + sec + " 秒"));
            }
        }

        // 右下角：每行右对齐，最后一行贴近底边，向上堆叠
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int y = screenH - MARGIN - mc.font.lineHeight;
        for (int i = lines.size() - 1; i >= 0; i--) {
            Component line = lines.get(i);
            int x = screenW - MARGIN - mc.font.width(line);
            int color = i == 0 ? COLOR : 0xFFFFFFFF;
            graphics.drawString(mc.font, line, x, y, color);
            y -= LINE_H;
        }
    }
}
