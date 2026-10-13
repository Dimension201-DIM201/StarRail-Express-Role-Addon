package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.PhantomThiefPlayerComponent;
import com.dim201.dim201addon.role.PhantomThiefRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 大怪盗 HUD：左下角显示预告追踪倒计时、妙手空空读条进度、
 * 胜利计数 / 收藏层数 / 钩影手次数 / 替身人偶状态。
 *
 * <p>数据来自 {@link PhantomThiefPlayerComponent}（CCA 仅同步给本人）。
 */
public class PhantomThiefClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 5;

    public static void register() {
        HudRenderCallback.EVENT.register(new PhantomThiefClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(PhantomThiefRole.PHANTOM_THIEF)) return;

        PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 72;

        // 面板背景 + 紫色顶边
        HudPanels.draw(graphics, x - 5, y - 5, x + 220, y + 68, 0xFF9B59B6);

        int lineY = y;

        // 行1：预告/读条状态
        if (comp.phase == PhantomThiefPlayerComponent.Phase.ANNOUNCING) {
            int seconds = Math.max(0, (comp.announceTicks + 19) / 20);
            graphics.drawString(mc.font, Component.literal(
                    "§d§l预告中 §7目标 §f" + comp.targetName), x, lineY, 0xFFFFFFFF);
            lineY += 10;
            graphics.drawString(mc.font, Component.literal(
                    "§7宝物 §d" + comp.announcedItemName + " §7剩余 §f" + seconds + "§7 秒"), x, lineY, 0xFFFFFFFF);
            lineY += 11;
            // 预告倒计时进度条
            drawBar(graphics, x, lineY,
                    1f - (float) comp.announceTicks / PhantomThiefPlayerComponent.ANNOUNCE_DURATION_TICKS,
                    0xFF9B59B6);
            lineY += BAR_HEIGHT + 4;
        } else if (comp.phase == PhantomThiefPlayerComponent.Phase.CHANNELING) {
            graphics.drawString(mc.font, Component.literal("§d§l妙手空空…"), x, lineY, 0xFFFFFFFF);
            lineY += 10;
            graphics.drawString(mc.font, Component.literal("§7正在行窃，切勿被目标的视线扫到！"), x, lineY, 0xFFFFFFFF);
            lineY += 11;
            // 读条进度条
            int total = comp.channelDuration();
            float progress = total > 0 ? 1f - (float) comp.channelTicks / total : 1f;
            drawBar(graphics, x, lineY, progress, 0xFFFFD700);
            lineY += BAR_HEIGHT + 4;
        } else {
            graphics.drawString(mc.font, Component.literal("§d§l大怪盗 §7未锁定目标"), x, lineY, 0xFFFFFFFF);
            lineY += 11;
            // 预告信冷却中显示倒计时，否则显示操作引导
            var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(mc.player);
            int cd = ability != null ? ability.cooldown : 0;
            if (cd > 0) {
                graphics.drawString(mc.font, Component.literal(
                        "§c预告信冷却中 §e" + ((cd + 19) / 20) + " §c秒"), x, lineY, 0xFFFFFFFF);
            } else {
                graphics.drawString(mc.font, Component.literal("§7打开背包点击玩家头像发送预告信"), x, lineY, 0xFFFFFFFF);
            }
            lineY += 11;
        }

        // 行末：胜利计数 / 道具
        int required = comp.requiredCount();
        String winText = comp.hasReachedWin()
                ? "§a§l胜利 " + comp.winCount + "§a/" + required + " §6可购买谢幕！"
                : "§d胜利 §f" + comp.winCount + "§7/" + required;
        graphics.drawString(mc.font, Component.literal(winText), x, lineY, 0xFFFFFFFF);
        lineY += 11;

        // 收藏家之傲层数单独一行
        String dots = "●".repeat(comp.collections) + "○".repeat(
                Math.max(0, PhantomThiefPlayerComponent.MAX_COLLECTIONS - comp.collections));
        graphics.drawString(mc.font, Component.literal("§5收藏 " + dots), x, lineY, 0xFFFFFFFF);
        lineY += 11;

        // 道具状态一行（钩影手耐久扫描背包物品；人偶读组件标志）
        String grappleText = "§8无";
        for (var stack : mc.player.getInventory().items) {
            if (stack.is(com.dim201.dim201addon.item.Dim201ModItems.PHANTOM_GRAPPLING_CLAW)) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                grappleText = "§f" + left;
                break;
            }
        }
        graphics.drawString(mc.font, Component.literal(
                "§7钩影手 " + grappleText
                        + " §7人偶 " + (comp.dollActive ? "§a有" : "§8无")), x, lineY, 0xFFFFFFFF);
    }

    /** 画一个进度条（progress 0~1） */
    private void drawBar(GuiGraphics graphics, int x, int y, float progress, int fillColor) {
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF3A3A3A);
        int filled = (int) (Math.max(0f, Math.min(1f, progress)) * BAR_WIDTH);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR_HEIGHT, fillColor);
        }
    }
}
