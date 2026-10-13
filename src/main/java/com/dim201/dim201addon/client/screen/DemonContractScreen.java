package com.dim201.dim201addon.client.screen;

import com.dim201.dim201addon.DemonContractorComponent;
import com.dim201.dim201addon.contract.ContractPower;
import com.dim201.dim201addon.menu.DemonContractMenu;
import com.dim201.dim201addon.packet.DemonContractResultS2CPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumSet;
import java.util.List;

/**
 * 恶魔契约界面（客户端）
 *
 * <p>阶段一「索取」：恶魔随机抽取的力量按钮多选（代价全部显示 ???），底部低语随数量变色，
 * 签订按钮不可反悔。阶段二「献祭」：{@link #showResult} 切换为揭晓页，
 * 逐条展示代价与死亡结局，2.5 秒后自动关闭。
 */
public class DemonContractScreen extends AbstractContainerScreen<DemonContractMenu> {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 222;

    // 阶段一统一布局（坐标系原点为面板左上角；快捷栏槽位见 DemonContractMenu.SLOT_Y）
    private static final int POWER_START_Y = 38;
    private static final int POWER_H = 24;
    private static final int POWER_GAP = 4;
    private static final int WHISPER_Y = 154;
    private static final int SIGN_Y = 168;
    private static final int SIGN_H = 20;

    private final EnumSet<ContractPower> selected = EnumSet.noneOf(ContractPower.class);
    private DemonContractorComponent clientComp;

    // 阶段二数据
    private boolean resultPhase = false;
    private List<String> powerLines = List.of();
    private List<String> costLines = List.of();
    private List<String> whisperLines = List.of();
    private int totalPoints;
    private boolean dead;
    /** 揭晓页实际内容高度（内容可能超过 PANEL_H，背景随之向下延伸） */
    private int resultPanelH = PANEL_H;

    public DemonContractScreen(DemonContractMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        if (resultPhase) {
            return; // 揭晓页不渲染按钮
        }
        this.clientComp = (minecraft.player != null) ? DemonContractorComponent.KEY.get(minecraft.player) : null;

        // 恶魔本次抽取的力量（服务端同步，全宽竖排）
        int btnW = PANEL_W - 16;
        List<ContractPower> offered = menu.getOfferedPowers();
        for (int i = 0; i < offered.size(); i++) {
            ContractPower power = offered.get(i);
            Button btn = Button.builder(buttonLabel(power), b -> onToggle(power))
                    .bounds(leftPos + 8, topPos + POWER_START_Y + i * (POWER_H + POWER_GAP),
                            btnW, POWER_H)
                    .tooltip(Tooltip.create(Component.literal(
                            power.description() + "\n§8代价：???")))
                    .build();
            addRenderableWidget(btn);
        }

        // 签订按钮（槽位区之上，与快捷栏完全分离）
        addRenderableWidget(Button.builder(Component.literal("§4§l签订契约（不可反悔）"), b -> {
            if (selected.isEmpty()) {
                // 空选不发包，直接给出提示，避免静默无反应
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.literal("§c恶魔需要你索取至少一项力量"), true);
                }
                return;
            }
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, DemonContractMenu.BUTTON_SIGN);
        }).bounds(leftPos + PANEL_W / 2 - 70, topPos + SIGN_Y, 140, SIGN_H).build());
    }

    private Component buttonLabel(ContractPower power) {
        String check = selected.contains(power) ? "§a[✔] " : "§8[  ] ";
        return Component.literal(check + "§d" + power.displayName());
    }

    private void onToggle(ContractPower power) {
        if (!selected.add(power)) selected.remove(power);
        // 服务端权威：勾选状态必须同步（按钮索引即 offered 池下标），否则签订时服务端读到空集
        int idx = menu.getOfferedPowers().indexOf(power);
        if (idx >= 0) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
        }
        rebuildWidgets(); // 刷新勾选状态与低语
    }

    /** S2C 结算结果到达：切换阶段二 */
    public void showResult(DemonContractResultS2CPacket pkt) {
        this.resultPhase = true;
        this.powerLines = pkt.powerLines();
        this.costLines = pkt.costLines();
        this.whisperLines = pkt.whisperLines();
        this.totalPoints = pkt.totalPoints();
        this.dead = pkt.dead();
        rebuildWidgets();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        int h = resultPhase ? resultPanelH : imageHeight;
        g.fill(x, y, x + imageWidth, y + h, 0xFF1A0D24);
        int border = dead ? 0xFF8B0000 : 0xFF6A0DAD;
        g.fill(x, y, x + imageWidth, y + 1, border);
        g.fill(x, y + h - 1, x + imageWidth, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + h, border);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (!resultPhase) {
            renderSelectPhase(g);
        } else {
            renderResultPhase(g);
        }
    }

    private void renderSelectPhase(GuiGraphics g) {
        g.drawCenteredString(font, "§5§l恶魔契约", imageWidth / 2, 8, 0xFFB266FF);
        g.drawCenteredString(font, "§7恶魔从中抽取了这些力量……（可多选）", imageWidth / 2, 22, 0xFFAAAAAA);

        // 底部低语
        String whisper = switch (selected.size()) {
            case 0 -> "§8恶魔沉默地看着你";
            case 1 -> "§8恶魔点了点头";
            case 2 -> "§7恶魔露出了微笑";
            case 3 -> "§6你的欲望令恶魔愉悦";
            default -> "§c§l贪婪，是最深的罪";
        };
        g.drawCenteredString(font, whisper, imageWidth / 2, WHISPER_Y, 0xFFFFFFFF);
        String remain = "§8剩余契约次数：§f" + Math.max(0,
                3 - (clientComp != null ? clientComp.getContractCount() : 0));
        g.drawString(font, remain, 8, WHISPER_Y, 0xFFFFFFFF);
    }

    private void renderResultPhase(GuiGraphics g) {
        int y = 14;
        g.drawCenteredString(font, "§5§l契约成立", imageWidth / 2, y, 0xFFB266FF);
        y += 22;

        if (!powerLines.isEmpty()) {
            g.drawString(font, "§a你获得了：", 16, y, 0xFFFFFFFF);
            y += 14;
            for (String line : powerLines) {
                g.drawString(font, "§d  ▪ " + line, 20, y, 0xFFFFFFFF);
                y += 13;
            }
            y += 6;
        }

        g.drawString(font, "§c为此你献出：", 16, y, 0xFFFFFFFF);
        y += 14;
        for (String line : costLines) {
            g.drawString(font, "§4  ▪ " + line, 20, y, 0xFFFFFFFF);
            y += 13;
        }
        y += 8;
        g.drawCenteredString(font, "§7总代价：§f" + totalPoints + " §7点",
                imageWidth / 2, y, 0xFFFFFFFF);
        y += 20;

        // 死者低语线索（仅选了 WHISPER 时存在）
        if (!whisperLines.isEmpty()) {
            g.drawString(font, "§8§o死者低语：", 16, y, 0xFFB266FF);
            y += 14;
            for (String line : whisperLines) {
                g.drawString(font, "§8  ▪ " + line, 20, y, 0xFFFFFFFF);
                y += 13;
            }
            y += 6;
        }

        if (dead) {
            g.drawCenteredString(font, "§4§l代价过重……", imageWidth / 2, y, 0xFFFF5555);
            y += 14;
            g.drawCenteredString(font, "§c你的灵魂被恶魔收走了", imageWidth / 2, y, 0xFFFF5555);
            y += 6;
        }

        // 背景高度跟随内容，底部留 18px 放关闭提示
        resultPanelH = Math.max(PANEL_H, y + 18);
        // 不自动关闭：保留揭晓信息供玩家反复查看，由玩家主动关闭
        g.drawCenteredString(font, "§8按 §fEsc §8或 §fE §8关闭",
                imageWidth / 2, resultPanelH - 12, 0xFF888888);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!resultPhase) this.renderTooltip(g, mouseX, mouseY);
    }
}
