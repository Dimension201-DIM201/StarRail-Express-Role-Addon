package com.dim201.dim201addon.client;

import com.dim201.dim201addon.DeepWoundPlayerComponent;
import com.dim201.dim201addon.LegionPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.LegionRole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.NoellesrolesClient;

/**
 * 军团 Legion 的客户端 HUD：
 *   - 若当前玩家是「军团 LEGION」：
 *       · 技能开启中 → 显示野性狂暴面板（剩余时间/速度等级/必杀进度/一击必杀就绪）
 *       · 冷却中（未开技能）→ 仅显示冷却倒计时小面板
 *   - 杀手直觉透视：深度伤口玩家显示橙色（0xFF8800）
 *
 * 注意：深度伤口仅用 ActionBar 提示，不再在此 HUD 绘制
 */
public class LegionClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    /** 深度伤口玩家在军团视角中的高亮色（橙色） */
    private static final int DEEP_WOUND_COLOR = 0xFF8800;

    public static void register() {
        HudRenderCallback.EVENT.register(new LegionClientHud());

        // 军团透视：深度伤口玩家显示橙色，其余玩家走默认（绿色）
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(LegionRole.LEGION_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (!isInstinctEnabled) return TrueFalseAndCustomResult.pass();
                    if (!(target instanceof Player tp)) return TrueFalseAndCustomResult.pass();
                    DeepWoundPlayerComponent dw = ModComponents.DEEP_WOUND.get(tp);
                    if (dw.isActive()) {
                        return TrueFalseAndCustomResult.custom(DEEP_WOUND_COLOR);
                    }
                    return TrueFalseAndCustomResult.pass();
                });
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        boolean isLegion = false;
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld != null) {
            var role = gameWorld.getRole(mc.player);
            if (role != null && role.equals(LegionRole.LEGION)) isLegion = true;
        }
        if (!isLegion) return;

        LegionPlayerComponent comp = ModComponents.LEGION.get(mc.player);
        if (comp == null) return;

        if (comp.skillActive) {
            drawLegionSkillPanel(graphics, comp);
        } else if (comp.cooldownTicks > 0) {
            drawLegionCooldownPanel(graphics, comp);
        } else {
            drawLegionReadyPanel(graphics, comp);
        }
    }

    private void drawLegionSkillPanel(GuiGraphics g, LegionPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 160;

        String title = String.format("§c§l军团 §8| §f野性狂暴");
        g.drawString(mc.font, Component.literal(title), x, y, 0xFFFFFFFF);
        y += 14;

        int secLeft = Math.max(0, comp.skillTicksLeft + 19) / 20;
        g.drawString(mc.font, Component.literal("§f剩余时间：§c" + secLeft + " 秒"), x, y, 0xFFFFFFFF);
        y += 12;

        int spLvl = Math.min(5, Math.max(1, comp.speedLevel));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < spLvl ? "§b▶" : "§7▶");
        String jollyHint = comp.tapeJollyPurchased ? " §8(朱莉·II)" : "";
        g.drawString(mc.font, Component.literal("§f速度等级：§b" + spLvl + "  " + sb + jollyHint), x, y, 0xFFFFFFFF);
        y += 12;

        int alive = countAliveClientEstimate();
        int need = Math.max(1, alive / 2);
        int hit = comp.hitPlayersThisSession.size();
        float pct = Math.min(100.0f, (hit * 100.0f) / Math.max(1, need));
        g.drawString(mc.font, Component.literal("§f处决进度：§e" + hit + "/" + need + " §f(" + String.format("%.0f", pct) + "%)"), x, y, 0xFFFFFFFF);
        y += 10;
        drawBar(g, x, y, 200, 6, pct, 0xFFE0A500);
        y += 12;

        String execText = comp.nextHitIsExecution
                ? "§c§l⚔ 下一击·处决就绪（命中深度伤口玩家即击杀）"
                : "§7进度达标后，蓄力击可处决深度伤口玩家";
        g.drawString(mc.font, Component.literal(execText), x, y, 0xFFFFFFFF);
        y += 14;

        // 动态技能键提示：按玩家实际绑定的键位显示
        String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String skillHint = String.format("§7技能键§e[%s]§7：开启/维持野性狂暴", abilityKey);
        g.drawString(mc.font, Component.literal(skillHint), x, y, 0xFFFFFFFF);
        y += 12;

        int shieldLayers = 0;
        try {
            if (mc.player != null) {
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(mc.player);
                if (armor != null) shieldLayers = armor.getArmor();
            }
        } catch (Throwable ignored) {}
        String shieldTxt = (comp.shieldGrantedThisSession ? "§a激活中 " : "§7") + "§f护盾：§6" + shieldLayers + " §f层";
        g.drawString(mc.font, Component.literal(shieldTxt), x, y, 0xFFFFFFFF);
        y += 12;

        StringBuilder tapeSb = new StringBuilder();
        tapeSb.append("§f混音带: ");
        tapeSb.append(comp.tapeFrankPurchased ? "§a[弗]" : "§7[弗]");
        tapeSb.append(comp.tapeJollyPurchased ? "§a[朱]" : "§7[朱]");
        tapeSb.append(comp.tapeSusiePurchased ? "§a[苏]" : "§7[苏]");
        tapeSb.append(comp.tapeJoeyPurchased  ? "§a[乔]" : "§7[乔]");
        tapeSb.append(comp.tapeSmokePurchased ? "§a[冒]" : "§7[冒]");
        g.drawString(mc.font, Component.literal(tapeSb.toString()), x, y, 0xFFFFFFFF);
    }

    private void drawLegionCooldownPanel(GuiGraphics g, LegionPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 80;
        int secLeft = (comp.cooldownTicks + 19) / 20;

        String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String title = String.format("§c§l军团 §8| §e野性狂暴 §c冷却中 §f：还剩 §c%d §f秒  §7[技能键%s]", secLeft, abilityKey);
        g.drawString(mc.font,
                Component.literal(title),
                x, y, 0xFFFFFFFF);
        y += 12;
        float pct = 100.0f - ((comp.cooldownTicks * 100.0f) /
                Math.max(1, (comp.cooldownTicks > 120 * 20 - 1) ? (120 * 20) : (60 * 20)));
        drawBar(g, x, y, 200, 5, pct, 0xFF5555FF);
        y += 12;
        StringBuilder tapeSb = new StringBuilder();
        tapeSb.append("§f混音带: ");
        tapeSb.append(comp.tapeFrankPurchased ? "§a[弗]" : "§7[弗]");
        tapeSb.append(comp.tapeJollyPurchased ? "§a[朱]" : "§7[朱]");
        tapeSb.append(comp.tapeSusiePurchased ? "§a[苏]" : "§7[苏]");
        tapeSb.append(comp.tapeJoeyPurchased  ? "§a[乔]" : "§7[乔]");
        tapeSb.append(comp.tapeSmokePurchased ? "§a[冒]" : "§7[冒]");
        g.drawString(mc.font, Component.literal(tapeSb.toString()), x, y, 0xFFFFFFFF);
    }

    private void drawLegionReadyPanel(GuiGraphics g, LegionPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 70;

        String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String readyTitle = String.format("§c§l军团 §8| §a野性狂暴 §a§l✔ 已就绪 §f（按§e[%s]§f开启）", abilityKey);
        g.drawString(mc.font,
                Component.literal(readyTitle),
                x, y, 0xFFFFFFFF);
        y += 12;
        StringBuilder tapeSb = new StringBuilder();
        tapeSb.append("§f混音带: ");
        tapeSb.append(comp.tapeFrankPurchased ? "§a[弗]" : "§7[弗]");
        tapeSb.append(comp.tapeJollyPurchased ? "§a[朱]" : "§7[朱]");
        tapeSb.append(comp.tapeSusiePurchased ? "§a[苏]" : "§7[苏]");
        tapeSb.append(comp.tapeJoeyPurchased  ? "§a[乔]" : "§7[乔]");
        tapeSb.append(comp.tapeSmokePurchased ? "§a[冒]" : "§7[冒]");
        g.drawString(mc.font, Component.literal(tapeSb.toString()), x, y, 0xFFFFFFFF);
    }

    private static int countAliveClientEstimate() {
        if (mc.level == null) return 8;
        return Math.max(1, mc.level.players().size());
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, int height, float percentFloat, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        graphics.fill(x, y, x + width, y + height, 0xFF333333);
        float clamped = Math.max(0.0f, Math.min(100.0f, percentFloat));
        int fill = (int) ((clamped / 100.0f) * width);
        graphics.fill(x, y, x + fill, y + height, color);
    }
}
