package com.dim201.dim201addon.client;

import com.dim201.dim201addon.EmiyaPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.EmiyaRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;

public class EmiyaClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new EmiyaClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(EmiyaRole.EMIYA)) return;

        EmiyaPlayerComponent comp = ModComponents.EMIYA.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 80;

        // 背景框
        HudPanels.draw(graphics, x - 5, y - 5, x + 210, y + 62, 0xFF00BFFF);

        String titleColor = "§b";
        graphics.drawString(mc.font, Component.literal(titleColor + "§l卫宫士郎 §8| §7正义的伙伴"), x, y, 0xFFFFFFFF);
        y += 14;

        // 魔力显示
        int currentMana = comp.currentMana;
        int maxMana = EmiyaPlayerComponent.MAX_MANA;
        String manaText = "§9魔力: §f" + currentMana + " / " + maxMana;
        graphics.drawString(mc.font, Component.literal(manaText), x, y, 0xFFFFFFFF);
        y += 12;

        // 魔力条
        int barWidth = 180;
        int barHeight = 6;
        int barX = x;
        int barY = y;
        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        int manaFill = (int) ((currentMana / (double) maxMana) * barWidth);
        graphics.fill(barX, barY, barX + manaFill, barY + barHeight, 0xFF5555FF);
        y += 12;

        // 当前投影物品
        EmiyaPlayerComponent.ProjectionItem proj = comp.currentProjection;
        String projName = proj.displayName;
        int manaCost = proj.manaCost;
        String canUseColor = currentMana >= manaCost ? "§a" : "§c";

        String projText = "§7投影物品: " + canUseColor + projName + " §7(消耗 §e" + manaCost + " §7魔力)";
        graphics.drawString(mc.font, Component.literal(projText), x, y, 0xFFFFFFFF);
        y += 12;

        // 时停钟使用状态
        if (proj == EmiyaPlayerComponent.ProjectionItem.TIME_STOP_CLOCK) {
            if (comp.timeStopClockUsed) {
                graphics.drawString(mc.font, Component.literal("§c⚠ 时停钟已在本局使用过"), x, y, 0xFFFFFFFF);
            } else {
                graphics.drawString(mc.font, Component.literal("§a✔ 时停钟可投影"), x, y, 0xFFFFFFFF);
            }
            y += 12;
        }

        // 动态按键提示：跟随玩家设置改变
        String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String switchKey = NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage().getString();
        String traceLine = String.format("§7Trace on!   技能键§e[%s]§7投影   §e[%s]§7切换物品", abilityKey, switchKey);
        graphics.drawString(mc.font, Component.literal(traceLine), x, y, 0xFFFFFFFF);
    }
}
