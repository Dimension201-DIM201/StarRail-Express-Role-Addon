package com.dim201.dim201addon.client.screen;

import com.dim201.dim201addon.menu.BeggarMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 乞讨界面（客户端）
 * 中间显示一个「碗」图标，下方「施舍」「拒绝」两个按钮
 */
public class BeggarScreen extends AbstractContainerScreen<BeggarMenu> {

    public BeggarScreen(BeggarMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 130;
        this.inventoryLabelY = this.imageHeight - 28;
    }

    @Override
    protected void init() {
        super.init();

        // 中间：碗（Item 图标）
        // 碗不通过 slot（不给操作），在 renderBg 用 renderFakeItem 画出

        // 下方：施舍按钮（绿色，较大）
        addRenderableWidget(Button.builder(Component.literal("§a施舍"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BeggarMenu.BUTTON_DONATE)
        ).bounds(leftPos + 20, topPos + 78, 70, 22).build());

        // 下方：拒绝按钮（红色，较小）
        addRenderableWidget(Button.builder(Component.literal("§c拒绝"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BeggarMenu.BUTTON_REJECT)
        ).bounds(leftPos + 110, topPos + 78, 70, 22).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 深紫色背景（符合贫穷神氛围）
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2E1A3A);
        // 边框
        int border = 0xFF9B59B6;
        guiGraphics.fill(x, y, x + imageWidth, y + 1, border);
        guiGraphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, border);
        guiGraphics.fill(x, y, x + 1, y + imageHeight, border);
        guiGraphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, border);

        // 中间画一个碗（MC 原版物品）
        ItemStack bowl = new ItemStack(Items.BOWL);
        int bx = x + (imageWidth - 32) / 2;
        int by = y + 32;
        // 放大绘制碗：使用 renderFakeItem 画 32x32
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(bx, by, 0);
        guiGraphics.pose().scale(2.0F, 2.0F, 2.0F);
        guiGraphics.renderItem(bowl, 0, 0);
        guiGraphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 乞讨者名字
        String name = menu.getBeggarName();
        guiGraphics.drawCenteredString(font, "§d" + name + " §f向你乞讨...", imageWidth / 2, 6, 0xFFFFFF);
        // 小提示
        guiGraphics.drawCenteredString(font, "§7给钱有 10% 概率获得一层护盾", imageWidth / 2, 20, 0xAAAAAA);
    }
}
