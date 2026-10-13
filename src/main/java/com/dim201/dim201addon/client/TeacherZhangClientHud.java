package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.TeacherZhangPlayerComponent;
import com.dim201.dim201addon.TeacherZhangRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 张老师 HUD：显示当前步数（获胜进度）与进食状态。
 *
 * <p>内容：
 * <ul>
 *   <li>步数：已移动格数 / 获胜距离，附进度条（满进度变金色）</li>
 *   <li>进食状态：巧克力冰激凌免疫有无、碳酸饮料已喝次数与下次猝死概率</li>
 * </ul>
 *
 * <p>数据源为 {@link TeacherZhangPlayerComponent}（CCA 每 20 tick 同步 +
 * 客户端本地 tick 累计，显示平滑无跳变）。
 * 角色判定与兄弟默契 HUD 一致：直接 isRole，不依赖对局运行状态。
 */
public class TeacherZhangClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new TeacherZhangClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (mc.player == null || mc.options.hideGui) return;

        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gw == null) return;
        if (!gw.isRole(mc.player, TeacherZhangRole.TEACHER_ZHANG)) return;

        TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 88;

        // 面板背景 + 角色绿顶边
        HudPanels.draw(graphics, x - 5, y - 5, x + 200, y + 66, 0xFF00E680);

        // 标题
        graphics.drawString(mc.font, Component.literal("§a§l张老师 §8| §7你跑不过我你信吗"), x, y, 0xFFFFFFFF);
        y += 14;

        // 步数进度
        int steps = (int) comp.totalDistance;
        int win = (int) TeacherZhangRole.WIN_DISTANCE;
        graphics.drawString(mc.font,
                Component.literal("§f步数: §e" + steps + " §7/ §f" + win),
                x, y, 0xFFFFFFFF);
        y += 10;

        // 步数进度条（满进度变金色）
        float ratio = (float) Math.min(1.0D, comp.totalDistance / TeacherZhangRole.WIN_DISTANCE);
        int barWidth = 180;
        int barHeight = 6;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF333333);
        int fillColor = ratio >= 1.0F ? 0xFFFFD700 : 0xFF00E680;
        graphics.fill(x, y, x + (int) (barWidth * ratio), y + barHeight, fillColor);
        y += 12;

        // 进食状态：冰激凌免疫
        String immunityText = comp.hasIceCreamImmunity
                ? "§6冰激凌免疫 §a✔"
                : "§7无冰激凌免疫";
        graphics.drawString(mc.font,
                Component.literal("§f进食: " + immunityText),
                x, y, 0xFFFFFFFF);
        y += 12;

        // 进食状态：碳酸饮料次数与下次猝死概率（首瓶 20%，每瓶 +20%）
        int nextChance = (comp.sodaBuyCount + 1) * 20;
        String sodaText = comp.sodaBuyCount == 0
                ? "§7碳酸饮料: §f未喝 §7(下次 20% 猝死)"
                : "§f碳酸饮料: §e" + comp.sodaBuyCount + "瓶 §7(下次 " + nextChance + "% 猝死)";
        graphics.drawString(mc.font, Component.literal(sodaText), x, y, 0xFFFFFFFF);
    }
}
