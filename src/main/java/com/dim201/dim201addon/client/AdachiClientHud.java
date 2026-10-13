package com.dim201.dim201addon.client;

import com.dim201.dim201addon.AdachiPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.AdachiRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.agmas.noellesroles.client.NoellesrolesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.dim201.dim201addon.packet.SwitchSkillC2SPacket;

public class AdachiClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final int BORING_LIMIT = 2 * 60 * 20;
    private static final int SKILL2_UNLOCK_TIME = 2 * 60 * 20;  // 灼热奋起 2min 解锁
    private static final int SKILL3_UNLOCK_TIME = 3 * 60 * 20;  // 恶魔微笑 3min 解锁

    private static boolean switchHandlerRegistered = false;
    private static boolean wasSwitchKeyDown = false;
    private static boolean instinctHighlightRegistered = false;

    public static void register() {
        HudRenderCallback.EVENT.register(new AdachiClientHud());

        if (!switchHandlerRegistered) {
            switchHandlerRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.player == null) return;
                if (Minecraft.getInstance().screen != null) return;
                boolean isDown = NoellesrolesClient.nextAbilityBind.isDown();
                // 边沿检测：轻按一下切换一次，长按不连发
                if (isDown && !wasSwitchKeyDown) {
                    var gameWorld = SREGameWorldComponent.KEY.get(client.player.level());
                    if (gameWorld == null) { wasSwitchKeyDown = isDown; return; }
                    var role = gameWorld.getRole(client.player);
                    if (role == null || !role.equals(AdachiRole.ADACHI)) { wasSwitchKeyDown = isDown; return; }
                    ClientPlayNetworking.send(new SwitchSkillC2SPacket());
                }
                wasSwitchKeyDown = isDown;
            });
        }

        if (!instinctHighlightRegistered) {
            instinctHighlightRegistered = true;
            CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
                if (!(target instanceof Player targetPlayer)) return TrueFalseAndCustomResult.pass();
                var gameWorld = SREGameWorldComponent.KEY.get(self.level());
                if (gameWorld == null) return TrueFalseAndCustomResult.pass();
                var role = gameWorld.getRole(self);
                if (role == null || !role.equals(AdachiRole.ADACHI)) return TrueFalseAndCustomResult.pass();
                if (targetPlayer == self || targetPlayer.isSpectator()) return TrueFalseAndCustomResult.pass();
                float mood = SREPlayerMoodComponent.KEY.get(targetPlayer).getMood();
                if (mood <= 0.15f) return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                return TrueFalseAndCustomResult.pass();
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
        if (role == null || !role.equals(AdachiRole.ADACHI)) return;
        AdachiPlayerComponent comp = ModComponents.ADACHI.get(mc.player);
        if (comp == null) return;

        int x = 10; int baseY = graphics.guiHeight() - 150; int y = baseY;

        // 面板背景 + 金色顶边（标题 14px + 8 行 × 12px）
        HudPanels.draw(graphics, x - 5, baseY - 5, x + 250, baseY + 120, 0xFFFFD700);

        String titleColor = comp.hasRevived ? "§6" : "§7";
        graphics.drawString(mc.font, Component.literal(titleColor + "§l足立透 §8| §7中立·独自获胜"), x, y, 0xFFFFFFFF); y += 14;

        String skillText = comp.hasRevived ? "§f技能: §e「" + comp.currentSkill.displayName + "」" : "§f技能: §c锁定（需复活）";
        graphics.drawString(mc.font, Component.literal(skillText), x, y, 0xFFFFFFFF); y += 12;

        String switchText = comp.hasRevived ? "§7切换: §e" + NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage().getString() + " §7切换技能" : "§7切换: §c锁定（需复活）";
        graphics.drawString(mc.font, Component.literal(switchText), x, y, 0xFFFFFFFF); y += 12;

        // 无聊计时：复活前未启动，复活后才开始倒计时
        String boringText;
        if (!comp.hasRevived) {
            boringText = "§7无聊: 未启动（复活后计时）";
        } else {
            int boringRemaining = Math.max(0, BORING_LIMIT - comp.boringTimer);
            float boringSeconds = boringRemaining / 20.0f;
            float boringPercent = (float) comp.boringTimer / BORING_LIMIT;
            String boringPrefix;
            if (boringRemaining <= 0) boringPrefix = "§4§l";
            else if (boringPercent >= 0.8) boringPrefix = "§c";
            else if (boringPercent >= 0.5) boringPrefix = "§e";
            else boringPrefix = "§f";
            boringText = boringRemaining <= 0 ? boringPrefix + "无聊爆发" : boringPrefix + "无聊: §e" + String.format("%.1f", boringSeconds) + "s";
        }
        graphics.drawString(mc.font, Component.literal(boringText), x, y, 0xFFFFFFFF); y += 12;

        float aliveSeconds = comp.aliveTime / 20.0f;
        String aliveText = "§f存活: §e" + String.format("%.1f", aliveSeconds) + "s";
        if (comp.hasRevived) aliveText += " §7(已定格)";
        graphics.drawString(mc.font, Component.literal(aliveText), x, y, 0xFFFFFFFF); y += 12;

        String reviveText = comp.hasRevived ? "§c已复活" : "§a复活未使用 §7| §f首次死亡触发";
        graphics.drawString(mc.font, Component.literal(reviveText), x, y, 0xFFFFFFFF); y += 12;

        String skill1Text;
        if (!comp.hasRevived) skill1Text = "§c祸津曼荼罗: 锁定（需复活）";
        else if (comp.skill1Cooldown > 0) skill1Text = "§c祸津曼荼罗CD: §f" + String.format("%.1f", comp.skill1Cooldown / 20.0f) + "s";
        else skill1Text = "§a祸津曼荼罗: 就绪";
        graphics.drawString(mc.font, Component.literal(skill1Text), x, y, 0xFFFFFFFF); y += 12;

        // 灼热奋起：复活前显示解锁进度（2min 解锁），复活后显示状态
        String skill2Text;
        if (!comp.hasRevived) {
            if (comp.skill2Unlocked) skill2Text = "§7灼热奋起: §a已解锁 §7(复活后可用)";
            else {
                float need = (SKILL2_UNLOCK_TIME - comp.aliveTime) / 20.0f;
                skill2Text = "§7灼热奋起: §e" + String.format("%.1f", Math.max(0, need)) + "s 后解锁 §7(复活后可用)";
            }
        }
        else if (!comp.skill2Unlocked) skill2Text = "§7灼热奋起: §c未解锁";
        else if (comp.skill2Cooldown > 0) skill2Text = "§c灼热奋起CD: §f" + String.format("%.1f", comp.skill2Cooldown / 20.0f) + "s";
        else skill2Text = "§a灼热奋起: 就绪";
        graphics.drawString(mc.font, Component.literal(skill2Text), x, y, 0xFFFFFFFF); y += 12;

        // 恶魔微笑：复活前显示解锁进度（3min 解锁），复活后显示状态；激活后追加斩杀窗口倒计时
        String skill3Text;
        if (!comp.hasRevived) {
            if (comp.skill3Unlocked) skill3Text = "§7恶魔微笑: §a已解锁 §7(复活后可用)";
            else {
                float need3 = (SKILL3_UNLOCK_TIME - comp.aliveTime) / 20.0f;
                skill3Text = "§7恶魔微笑: §e" + String.format("%.1f", Math.max(0, need3)) + "s 后解锁 §7(复活后可用)";
            }
        }
        else if (!comp.skill3Unlocked) skill3Text = "§7恶魔微笑: §c未解锁";
        else if (comp.skill3Cooldown > 0) skill3Text = "§c恶魔微笑CD: §f" + String.format("%.1f", comp.skill3Cooldown / 20.0f) + "s";
        else skill3Text = "§a恶魔微笑: 就绪";
        // 恶魔微笑激活后显示斩杀窗口剩余秒数
        if (comp.executeWindowTicks > 0) skill3Text += " §d| §c斩杀窗口 " + String.format("%.1f", comp.executeWindowTicks / 20.0f) + "s";
        graphics.drawString(mc.font, Component.literal(skill3Text), x, y, 0xFFFFFFFF); y += 12;

        String stateText = comp.hasRevived ? "§6§l「这个世界的真相」" : "§7「无聊透顶的世界」";
        graphics.drawString(mc.font, Component.literal(stateText), x, y, 0xFFFFFFFF);
    }
}
