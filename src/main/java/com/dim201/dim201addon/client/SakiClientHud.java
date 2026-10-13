package com.dim201.dim201addon.client;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SakiPlayerComponent;
import com.dim201.dim201addon.role.SakiRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 骊驹早鬼 HUD
 * 显示：技能中/冷却、速度等级（Ⅰ~Ⅴ）、技能剩余/冷却剩余、突进次数、第二次突进窗口剩余、G键提示
 */
public class SakiClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new SakiClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        var gw = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gw == null) return;
        var role = gw.getRole(mc.player);
        if (role == null || !role.equals(SakiRole.SAKI)) return;
        SakiPlayerComponent comp = ModComponents.SAKI.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 105;
        // 背景框（精简至 4 行显示 + 留空，高度 60）
        HudPanels.draw(graphics, x - 5, y - 5, x + 260, y + 60, 0xFF555555);

        // 标题：只留中文，去掉 Saki 后缀 → 深灰色（用户指定 §8 = 深灰）
        graphics.drawString(mc.font, Component.literal("§8§l骊驹早鬼"), x, y, 0xFF555555);
        y += 12;

        // 状态：技能中 / 冷却 / 就绪
        String abilityKey = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String state;
        if (comp.skillTicksLeft > 0) {
            float s = comp.skillTicksLeft / 20.0f;
            state = String.format("§e三角追击 §f剩余 §c%.1fs", s);
        } else if (comp.cooldownTicksLeft > 0) {
            float s = comp.cooldownTicksLeft / 20.0f;
            state = String.format("§7冷却 §f剩余 §c%.1fs", s);
        } else {
            state = String.format("§a就绪 §7按§e[%s]§a启动", abilityKey);
        }
        graphics.drawString(mc.font, Component.literal(state), x, y, 0xFFFFFFFF);
        y += 12;

        // 速度等级 + 突进次数 + 击杀（三合一，一行）
        String lv = roman(comp.speedLevel);
        String dashInfo = String.format("§f速度§e%s  §f突进§c%d/2  §f击杀§c%d/2",
                lv, comp.dashCount, comp.killCount);
        if (comp.secondDashWindowTicksLeft > 0) {
            dashInfo += String.format("  §7二次窗口§c%.1fs", comp.secondDashWindowTicksLeft / 20.0f);
        }
        graphics.drawString(mc.font, Component.literal(dashInfo), x, y, 0xFFFFFFFF);
        y += 12;

        // 动态按键提示（只保留一行，情况分支）
        String tip;
        if (comp.skillTicksLeft > 0) {
            if (comp.dashCount == 0) {
                tip = (comp.speedLevel >= 3)
                        ? String.format("§7按§e[%s]§7进行第一段突进", abilityKey)
                        : String.format("§7速度到§eⅢ §7后按§e[%s]§7突进", abilityKey);
            } else if (comp.dashCount == 1 && comp.secondDashWindowTicksLeft > 0) {
                tip = String.format("§a窗口开启：§7按§e[%s]§7进行第二段突进", abilityKey);
            } else {
                tip = "§7突进次数已用完";
            }
        } else if (comp.cooldownTicksLeft > 0) {
            tip = String.format("§7技能冷却中 · 结束后按§e[%s]§7启动", abilityKey);
        } else {
            tip = String.format("§7按§e[%s]§7启动技能 · 同一按键突进", abilityKey);
        }
        graphics.drawString(mc.font, Component.literal(tip), x, y, 0xFFFFFFFF);
    }

    private static String roman(int l) {
        return switch (l) {
            case 0 -> "0";
            case 1 -> "Ⅰ";
            case 2 -> "Ⅱ";
            case 3 -> "Ⅲ";
            case 4 -> "Ⅳ";
            default -> "Ⅴ";
        };
    }
}
