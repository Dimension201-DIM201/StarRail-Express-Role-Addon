package com.dim201.dim201addon.client;

import com.dim201.dim201addon.WhiteSnakePlayerComponent;
import com.dim201.dim201addon.role.WhiteSnakeRole;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.agmas.noellesroles.client.NoellesrolesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class WhiteSnakeClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final int NAMES_PER_ROW = 4;  // ← 每行显示4个名字

    /** 溶解目标直觉高亮颜色：淡蓝色 */
    private static final int DISSOLVE_TARGET_COLOR = 0xFF87CEEB;

    private static boolean instinctHighlightRegistered = false;

    public static void register() {
        HudRenderCallback.EVENT.register(new WhiteSnakeClientHud());

        // 注册低san直觉高亮（ma_chen_xu同款）
        if (!instinctHighlightRegistered) {
            instinctHighlightRegistered = true;
            CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
                if (!(target instanceof Player targetPlayer)) {
                    return TrueFalseAndCustomResult.pass();
                }
                var gameWorld = SREGameWorldComponent.KEY.get(self.level());
                if (gameWorld == null) {
                    return TrueFalseAndCustomResult.pass();
                }
                var role = gameWorld.getRole(self);
                if (role == null || !role.equals(WhiteSnakeRole.WHITESNAKE)) {
                    return TrueFalseAndCustomResult.pass();
                }
                if (targetPlayer == self) {
                    return TrueFalseAndCustomResult.pass();
                }
                if (targetPlayer.isSpectator()) {
                    return TrueFalseAndCustomResult.pass();
                }
                float mood = SREPlayerMoodComponent.KEY.get(targetPlayer).getMood();
                if (mood <= 0.15f) {
                    // 满足溶解条件的玩家在直觉中显示为淡蓝色
                    return TrueFalseAndCustomResult.custom(DISSOLVE_TARGET_COLOR);
                }
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
        if (role == null || !role.equals(WhiteSnakeRole.WHITESNAKE)) return;

        WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(mc.player);
        if (comp == null) return;

        List<String> lowSanPlayers = comp.getLowSanPlayerNames();

        int x = 10;
        int baseY = graphics.guiHeight() - 100;
        int y = baseY;

        // 计算行数
        int nameRows = (lowSanPlayers.size() + NAMES_PER_ROW - 1) / NAMES_PER_ROW;
        if (nameRows == 0) nameRows = 1; // "无" 占一行

        int boxHeight = 82 + (nameRows * 12);
        HudPanels.draw(graphics, x - 5, y - 5, x + 170, y + boxHeight, 0xFF808080);

        graphics.drawString(mc.font, Component.literal("§f§l「White Snake」"), x, y, 0xFFFFFFFF);
        y += 14;

        String skillText = "§f替身能力: §e「" + comp.currentSkill.displayName + "」";
        graphics.drawString(mc.font, Component.literal(skillText), x, y, 0xFFFFFFFF);
        y += 12;

        String switchText = "§7切换: §e" + NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage().getString() + " §7切换技能";
        graphics.drawString(mc.font, Component.literal(switchText), x, y, 0xFFFFFFFF);
        y += 12;

        String cdText;
        if (comp.abilityDissolveCooldown > 0) {
            float seconds = comp.abilityDissolveCooldown / 20.0f;
            cdText = "§c「能力溶解」CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            cdText = "§a「能力溶解」: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(cdText), x, y, 0xFFFFFFFF);
        y += 12;

        String spiritCdText;
        if (comp.spiritDissolveCooldown > 0) {
            float seconds = comp.spiritDissolveCooldown / 20.0f;
            spiritCdText = "§c「精神溶解」CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            spiritCdText = "§a「精神溶解」: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(spiritCdText), x, y, 0xFFFFFFFF);
        y += 12;

        graphics.drawString(mc.font, Component.literal("§f§l可溶解目标:"), x, y, 0xFFFFFFFF);
        y += 12;

        if (lowSanPlayers.isEmpty()) {
            graphics.drawString(mc.font, Component.literal("§7  无"), x, y, 0xFFFFFFFF);
        } else {
            // 每行显示4个名字
            for (int i = 0; i < lowSanPlayers.size(); i++) {
                int col = i % NAMES_PER_ROW;
                int rowStartX = x + col * 40; // 每列40像素宽
                
                if (col == 0 && i > 0) {
                    y += 12; // 换行
                }
                
                graphics.drawString(mc.font, Component.literal("§b" + lowSanPlayers.get(i)), rowStartX, y, 0xFFFFFFFF);
            }
        }
    }
}