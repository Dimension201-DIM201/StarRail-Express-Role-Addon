package com.dim201.dim201addon.client;

import com.dim201.dim201addon.DeepWoundPlayerComponent;
import com.dim201.dim201addon.LegionPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.LegionNoShopRole;
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
 * 无商店军团（弗兰克）的客户端 HUD：
 *   - 复用 LegionPlayerComponent 状态
 *   - 与普通军团 HUD 的区别：
 *       · 标题改为"弗兰克"，不显示混音带面板
 *       · 冷却上限按 30s 显示进度条（普通军团为 120s/60s）
 *   - 仅在玩家为 LegionNoShopRole 时渲染
 *
 * 原理：HUD 渲染基于客户端同步过来的 LegionPlayerComponent 字段
 *      （skillActive/cooldownTicks/skillTicksLeft/speedLevel/hitPlayersThisSession/nextHitIsExecution/shieldGrantedThisSession）
 */
public class LegionNoShopClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    /** 弗兰克专用冷却上限（与复活时设置的 30s 一致） */
    private static final int FRANK_COOLDOWN_TICKS = 30 * 20;

    /** 深度伤口玩家在军团视角中的高亮色（橙色），与普通军团一致 */
    private static final int DEEP_WOUND_COLOR = 0xFF8800;

    public static void register() {
        HudRenderCallback.EVENT.register(new LegionNoShopClientHud());

        // 无商店军团透视：深度伤口玩家显示橙色（与普通军团一致）
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(LegionNoShopRole.LEGION_NO_SHOP_ID,
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

        boolean isFrank = false;
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld != null) {
            var role = gameWorld.getRole(mc.player);
            if (role != null && role.equals(LegionNoShopRole.LEGION_NO_SHOP)) isFrank = true;
        }
        if (!isFrank) return;

        LegionPlayerComponent comp = ModComponents.LEGION.get(mc.player);
        if (comp == null) return;

        if (comp.skillActive) {
            drawFrankSkillPanel(graphics, comp);
        } else if (comp.cooldownTicks > 0) {
            drawFrankCooldownPanel(graphics, comp);
        } else {
            drawFrankReadyPanel(graphics, comp);
        }
    }

    /** 野性狂暴开启中的面板 */
    private void drawFrankSkillPanel(GuiGraphics g, LegionPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 140;

        String title = "§c§l弗兰克 §8| §f野性狂暴";
        g.drawString(mc.font, Component.literal(title), x, y, 0xFFFFFFFF);
        y += 14;

        int secLeft = Math.max(0, comp.skillTicksLeft + 19) / 20;
        g.drawString(mc.font, Component.literal("§f剩余时间：§c" + secLeft + " 秒"), x, y, 0xFFFFFFFF);
        y += 12;

        int spLvl = Math.min(5, Math.max(1, comp.speedLevel));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < spLvl ? "§b▶" : "§7▶");
        g.drawString(mc.font, Component.literal("§f速度等级：§b" + spLvl + "  " + sb), x, y, 0xFFFFFFFF);
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
                ? "§c§l⚔ 下一击· 处决就绪 (击中【深度伤口】玩家可直接击杀)"
                : "§7达到进度后，下一次击中『处于深度伤口』的玩家可直接处决";
        g.drawString(mc.font, Component.literal(execText), x, y, 0xFFFFFFFF);
        y += 14;

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
    }

    /** 冷却中面板 */
    private void drawFrankCooldownPanel(GuiGraphics g, LegionPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 60;
        int secLeft = (comp.cooldownTicks + 19) / 20;

        String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String title = String.format("§c§l弗兰克 §8| §e野性狂暴 §c冷却中 §f：还剩 §c%d §f秒  §7[技能键%s]", secLeft, abilityKey);
        g.drawString(mc.font, Component.literal(title), x, y, 0xFFFFFFFF);
        y += 12;
        float pct = 100.0f - ((comp.cooldownTicks * 100.0f) / FRANK_COOLDOWN_TICKS);
        drawBar(g, x, y, 200, 5, pct, 0xFF5555FF);
    }

    /** 就绪面板 */
    private void drawFrankReadyPanel(GuiGraphics g, LegionPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 50;

        String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String readyTitle = String.format("§c§l弗兰克 §8| §a野性狂暴 §a§l✔ 已就绪 §f（按§e[%s]§f开启）", abilityKey);
        g.drawString(mc.font, Component.literal(readyTitle), x, y, 0xFFFFFFFF);
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
