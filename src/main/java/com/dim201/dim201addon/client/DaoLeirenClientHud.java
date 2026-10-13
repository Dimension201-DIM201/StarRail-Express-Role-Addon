package com.dim201.dim201addon.client;

import com.dim201.dim201addon.DaoLeirenPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.role.DaoLeirenRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;

/**
 * 刀系蕾忍 客户端 HUD：
 *   - 蕾克拉条（0-120，岚刀橙/燃烧刀红）
 *   - 当前攻击模式（岚刀/燃烧刀）
 *   - 模式切换冷却（5s）
 *   - 岚刀模式：对当前目标的连击进度（x/5）
 *   - 燃烧刀模式：蕾克拉耗尽倒计时估算
 *   - 打刀物品冷却状态
 *   - 技能键提示
 *
 * 仅在玩家为 DaoLeirenRole 时渲染，基于 DaoLeirenPlayerComponent 同步字段。
 */
public class DaoLeirenClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new DaoLeirenClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        boolean isDaoLeiren = false;
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld != null) {
            var role = gameWorld.getRole(mc.player);
            if (role != null && role.equals(DaoLeirenRole.DAO_LEIREN)) isDaoLeiren = true;
        }
        if (!isDaoLeiren) return;

        DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(mc.player);
        if (comp == null) return;

        if (comp.isDead) {
            drawDeadPanel(graphics, comp);
        } else {
            drawAlivePanel(graphics, comp);
        }
    }

    /** 存活状态：显示蕾克拉条+模式+连击/切换冷却 */
    private void drawAlivePanel(GuiGraphics g, DaoLeirenPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 110;

        int panelBottom = y + 95;
        HudPanels.draw(g, x - 5, y - 5, x + 270, panelBottom, 0xFFD2691E);

        // —— 标题 + 当前模式 ——
        String modeColor = comp.attackMode == DaoLeirenPlayerComponent.AttackMode.RANDAO ? "§c" : "§e";
        String modeName = comp.attackMode == DaoLeirenPlayerComponent.AttackMode.RANDAO ? "燃烧刀模式" : "岚刀模式";
        g.drawString(mc.font, Component.literal("§6§l刀系蕾忍 §8| " + modeColor + "§l" + modeName), x, y, 0xFFFFFFFF);
        y += 14;

        // —— 蕾克拉条 ——
        int reika = comp.reika;
        int maxReika = DaoLeirenPlayerComponent.MAX_REIKA;
        int barColor = comp.attackMode == DaoLeirenPlayerComponent.AttackMode.RANDAO ? 0xFFFF4500 : 0xFFFFA500;
        g.drawString(mc.font, Component.literal("§f蕾克拉：§a" + reika + "§7/" + maxReika), x, y, 0xFFFFFFFF);
        y += 10;
        float reikaPct = (reika * 100.0f) / maxReika;
        drawBar(g, x, y, 200, 6, reikaPct, barColor);
        y += 14;

        // —— 模式切换冷却 ——
        if (comp.modeSwitchCooldown > 0) {
            int sec = (comp.modeSwitchCooldown + 19) / 20;
            g.drawString(mc.font, Component.literal("§7切换冷却：§c" + sec + " 秒"), x, y, 0xFFFFFFFF);
        } else {
            String abilityKey = NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
            String hint = comp.attackMode == DaoLeirenPlayerComponent.AttackMode.LANDAO
                    ? String.format("§a✔ 可切换至燃烧刀 §7(需60蕾克拉) §e[%s]", abilityKey)
                    : String.format("§a✔ 可切换回岚刀 §e[%s]", abilityKey);
            g.drawString(mc.font, Component.literal(hint), x, y, 0xFFFFFFFF);
        }
        y += 14;

        // —— 打刀冷却（客户端 ItemCooldowns） ——
        float katanaCdPct = 0.0f;
        try {
            katanaCdPct = mc.player.getCooldowns().getCooldownPercent(Dim201ModItems.KATANA, 0.0f);
        } catch (Throwable ignored) {}
        if (katanaCdPct > 0.001f) {
            int cdSec = Math.round(katanaCdPct * (comp.attackMode == DaoLeirenPlayerComponent.AttackMode.RANDAO ? 1.0f : 30.0f));
            g.drawString(mc.font, Component.literal("§7打刀冷却：§c" + cdSec + " 秒"), x, y, 0xFFFFFFFF);
        } else {
            g.drawString(mc.font, Component.literal("§a✔ 打刀就绪"), x, y, 0xFFFFFFFF);
        }
        y += 14;

        // —— 攻击模式特有提示 ——
        if (comp.attackMode == DaoLeirenPlayerComponent.AttackMode.LANDAO) {
            // 岚刀模式：连击5次击杀提示
            g.drawString(mc.font, Component.literal("§e岚刀模式 §7| §f连击5次击杀目标"), x, y, 0xFFFFFFFF);
        } else {
            // 燃烧刀：每秒消耗3点，估算剩余时间
            int secs = reika / DaoLeirenPlayerComponent.RANDAO_DRAIN_PER_SEC;
            g.drawString(mc.font, Component.literal("§c每秒消耗3蕾克拉 §7| §f预计剩余 §e" + secs + " 秒"), x, y, 0xFFFFFFFF);
        }
    }

    /** 死亡状态：舍利子复活等待倒计时 */
    private void drawDeadPanel(GuiGraphics g, DaoLeirenPlayerComponent comp) {
        int x = 10;
        int y = g.guiHeight() - 60;

        HudPanels.draw(g, x - 5, y - 5, x + 270, y + 45, 0xFFD2691E);
        g.drawString(mc.font, Component.literal("§c§l刀系蕾忍 §8| §7已陨落"), x, y, 0xFFFFFFFF);
        y += 14;

        // 死亡位置（客户端同步过来的）
        g.drawString(mc.font, Component.literal(String.format("§7死亡位置：§f(%.1f, %.1f, %.1f)", comp.deathX, comp.deathY, comp.deathZ)), x, y, 0xFFFFFFFF);
        y += 14;

        if (comp.sariraDropped) {
            g.drawString(mc.font, Component.literal("§a✔ 舍利子已掉落 §7| 等待好人拾取后右键复活"), x, y, 0xFFFFFFFF);
        } else {
            g.drawString(mc.font, Component.literal("§7等待舍利子掉落..."), x, y, 0xFFFFFFFF);
        }
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, int height, float percentFloat, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        graphics.fill(x, y, x + width, y + height, 0xFF333333);
        float clamped = Math.max(0.0f, Math.min(100.0f, percentFloat));
        int fill = (int) ((clamped / 100.0f) * width);
        graphics.fill(x, y, x + fill, y + height, color);
    }
}
