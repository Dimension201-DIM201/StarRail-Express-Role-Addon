package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.NarukamiPlayerComponent;
import com.dim201.dim201addon.packet.SwitchSkillC2SPacket;
import com.dim201.dim201addon.role.NarukamiRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;

public class NarukamiClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    private static boolean switchHandlerRegistered = false;
    private static boolean wasSwitchKeyDown = false;

    public static void register() {
        HudRenderCallback.EVENT.register(new NarukamiClientHud());

        if (!switchHandlerRegistered) {
            switchHandlerRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.player == null) return;
                if (Minecraft.getInstance().screen != null) return;
                boolean isDown = NoellesrolesClient.nextAbilityBind.isDown();
                // 边沿检测：轻按一下切换一次
                if (isDown && !wasSwitchKeyDown) {
                    var gameWorld = SREGameWorldComponent.KEY.get(client.player.level());
                    if (gameWorld == null) { wasSwitchKeyDown = isDown; return; }
                    var role = gameWorld.getRole(client.player);
                    if (role == null || !role.equals(NarukamiRole.NARUKAMI)) { wasSwitchKeyDown = isDown; return; }
                    ClientPlayNetworking.send(new SwitchSkillC2SPacket());
                }
                wasSwitchKeyDown = isDown;
            });
        }
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(NarukamiRole.NARUKAMI)) return;
        NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(mc.player);
        if (comp == null) return;

        int x = 10;
        int y = graphics.guiHeight() - 120;

        // 面板背景 + 金色顶边（标题 14px + 6 行 × 12px）
        HudPanels.draw(graphics, x - 5, y - 5, x + 220, y + 92, 0xFFFFD700);

        // 标题（觉醒后金色）
        String titleColor = comp.awakened ? "§6" : "§e";
        graphics.drawString(mc.font, Component.literal(titleColor + "§l鸣上悠 §8| §7自称搜查队"), x, y, 0xFFFFFFFF);
        y += 14;

        // 搜查点数
        graphics.drawString(mc.font, Component.literal("§f搜查点数: §e" + comp.investigationPoints), x, y, 0xFFFFFFFF);
        y += 12;

        // 当前技能 + 切换提示
        boolean hasAnySkill = comp.investigationPoints >= 1;
        String skillText = hasAnySkill
                ? "§f技能: §e「" + comp.currentSkill.displayName + "」"
                : "§f技能: §c未解锁 §7(需1点搜查)";
        graphics.drawString(mc.font, Component.literal(skillText), x, y, 0xFFFFFFFF);
        y += 12;

        String switchText = hasAnySkill
                ? "§7切换: §e" + NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage().getString() + " §7切换技能"
                : "§7切换: §c锁定";
        graphics.drawString(mc.font, Component.literal(switchText), x, y, 0xFFFFFFFF);
        y += 12;

        // 技能状态行
        graphics.drawString(mc.font, Component.literal(skillLine(comp, NarukamiPlayerComponent.Skill.ZIODYNE, comp.skill1Cooldown)), x, y, 0xFFFFFFFF);
        y += 12;
        graphics.drawString(mc.font, Component.literal(skillLine(comp, NarukamiPlayerComponent.Skill.DASH, comp.skill2Cooldown)), x, y, 0xFFFFFFFF);
        y += 12;
        // 十文字斩行：就绪且护盾持有时额外显示护盾标记
        String crossSlashLine = skillLine(comp, NarukamiPlayerComponent.Skill.CROSS_SLASH, comp.skill3Cooldown);
        if (comp.crossSlashUnlocked && comp.skill3Cooldown <= 0 && comp.crossSlashShieldGranted) {
            crossSlashLine += " §b[护盾]";
        }
        graphics.drawString(mc.font, Component.literal(crossSlashLine), x, y, 0xFFFFFFFF);
        y += 12;

        // 觉醒状态
        if (comp.awakened) {
            graphics.drawString(mc.font, Component.literal("§6§l「伊耶那岐大神」 §7足立透已现形"), x, y, 0xFFFFFFFF);
        } else {
            graphics.drawString(mc.font, Component.literal("§7觉醒: 足立透复活 + 点数≥总人数/2"), x, y, 0xFFFFFFFF);
        }
    }

    private String skillLine(NarukamiPlayerComponent comp, NarukamiPlayerComponent.Skill skill, int cooldown) {
        if (!comp.isSkillUnlocked(skill)) {
            int required = comp.getRequiredPointsForSkill(skill);
            return "§7" + skill.displayName + ": §c未解锁 §7(需" + required + "点)";
        }
        if (cooldown > 0) {
            return "§c" + skill.displayName + "CD: §f" + String.format("%.1f", cooldown / 20.0f) + "s";
        }
        return "§a" + skill.displayName + ": 就绪";
    }
}
