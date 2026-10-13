package com.dim201.dim201addon.client.screen;

import com.dim201.dim201addon.menu.CardTradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 能力卡牌交易界面（客户端）
 *
 * <p>布局（紧凑版，仅显示快捷栏）：
 * <ul>
 *   <li>顶部：标题 + 对方玩家名</li>
 *   <li>中部：交易槽（slot 0）+ 按钮区域（仅接收方）</li>
 *   <li>底部：玩家快捷栏（9 格，无 27 格主背包）</li>
 * </ul>
 *
 * <p>移除主背包的原因：SRE 对局中玩家只暴露快捷栏，放入主背包的物品无法访问。
 *
 * <p>按钮通过 {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#handleInventoryButtonClick}
 * 发送原生按钮点击包到服务端，触发 {@link CardTradeMenu#clickMenuButton}。
 */
public class CardTradeScreen extends AbstractContainerScreen<CardTradeMenu> {

    public CardTradeScreen(CardTradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // 高度增加 10px 以容纳杀手半价提示行
        this.imageHeight = 102;
        // 快捷栏顶部 Y = 55，上移 4px 留空隙
        this.inventoryLabelY = this.imageHeight - 27;
    }

    @Override
    protected void init() {
        super.init();
        // 仅接收方显示购买/取消按钮
        if (!menu.isInitiator()) {
            // 按钮放在交易槽下方
            addRenderableWidget(Button.builder(Component.literal("购买"), b ->
                    this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CardTradeMenu.BUTTON_BUY)
            ).bounds(leftPos + 28, topPos + 36, 55, 18).build());

            addRenderableWidget(Button.builder(Component.literal("取消"), b ->
                    this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CardTradeMenu.BUTTON_CANCEL)
            ).bounds(leftPos + 93, topPos + 36, 55, 18).build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // 深灰背景
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2B2B);
        // 浅灰边框
        int border = 0xFF6B6B6B;
        guiGraphics.fill(x, y, x + imageWidth, y + 1, border);
        guiGraphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, border);
        guiGraphics.fill(x, y, x + 1, y + imageHeight, border);
        guiGraphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, border);
        // 交易槽高亮区域
        guiGraphics.fill(x + 79, y + 19, x + 97, y + 37, 0xFF1A1A1A);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题
        guiGraphics.drawString(font, "卡牌交易", 8, 6, 0xFFFFFF, false);

        // 对方信息
        String role = menu.isInitiator() ? "出售给" : "购买自";
        guiGraphics.drawString(font, role + ": " + menu.getPartnerName(), 8, 18, 0xAAAAAA, false);

        // 价格信息
        if (menu.hasTradeItem()) {
            int price = menu.getTradePrice();
            int color = price > 0 ? 0xFFD700 : 0xFF5555;
            int y = 24;
            // 原价
            guiGraphics.drawString(font, "价格: " + price + " 金币", 100, y, color, false);
            // 杀手半价/惩罚提示
            if (price > 0) {
                if (menu.isBuyerKiller()) {
                    int half = Math.max(0, price / 2);
                    guiGraphics.drawString(font, "§c杀手半价: " + half + " 金币",
                            100, y + 10, 0xFF55FF, false);
                }
                if (menu.isSellerKiller()) {
                    int half = Math.max(0, price / 2);
                    guiGraphics.drawString(font, "§c杀手惩罚: " + half + " 金币",
                            100, y + 20, 0xFF55FF, false);
                }
            }
        }

        // 发起方提示（放在快捷栏上方的空白区域，因高度紧凑紧贴按钮下方）
        if (menu.isInitiator()) {
            String tip = menu.hasTradeItem() ? "等待对方确认..." : "放入卡牌";
            guiGraphics.drawString(font, tip, 58, 40, 0xAAAAAA, false);
        }

        // 快捷栏标签（原 playerInventoryTitle 显示为"物品栏"，改"快捷栏"更准确）
        guiGraphics.drawString(font, "快捷栏", 8, this.inventoryLabelY, 0xFFFFFF, false);
    }
}
