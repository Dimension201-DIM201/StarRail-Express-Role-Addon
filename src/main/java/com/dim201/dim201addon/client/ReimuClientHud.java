package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.ReimuPlayerComponent;
import com.dim201.dim201addon.ReimuRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.agmas.noellesroles.client.NoellesrolesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ReimuClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new ReimuClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        // 检查是否是 Reimu 角色
        var gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null || !role.equals(ReimuRole.REIMU)) return;

        ReimuPlayerComponent comp = ModComponents.REIMU.get(mc.player);
        if (comp == null) return;

        // HUD 位置（屏幕右下）
        int x = 10;
        int y = graphics.guiHeight() - 110; // 高度增加以容纳新增行

        // 背景框
        HudPanels.draw(graphics, x - 5, y - 5, x + 200, y + 122, 0xFFFF0000);

        // 标题
        graphics.drawString(mc.font, Component.literal("§c§l博丽灵梦"), x, y, 0xFFFF0000);

        y += 14;

        // 灵气层数
        String powerText = "§f灵气: §e" + comp.reimuPower + "§7/§e" + ReimuPlayerComponent.MAX_REIMU_POWER;
        graphics.drawString(mc.font, Component.literal(powerText), x, y, 0xFFFFFFFF);

        y += 12;

        // 封魔针冷却
        String needleText;
        if (comp.sealingNeedleCooldown > 0) {
            float seconds = comp.sealingNeedleCooldown / 20.0f;
            needleText = "§c封魔针CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            needleText = "§a封魔针CD: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(needleText), x, y, 0xFFFFFFFF);

        y += 12;

        // 二重结界冷却
        String barrierText;
        if (comp.dreamBarrierCooldown > 0) {
            float seconds = comp.dreamBarrierCooldown / 20.0f;
            barrierText = "§c二重结界CD: §f" + String.format("%.1f", seconds) + "s";
        } else {
            barrierText = "§a二重结界CD: 就绪";
        }
        graphics.drawString(mc.font, Component.literal(barrierText), x, y, 0xFFFFFFFF);

        y += 12;

        // 移动距离进度（仅灵气未满时显示）
        if (comp.reimuPower < ReimuPlayerComponent.MAX_REIMU_POWER) {
            double progress = comp.distanceTraveled / 100.0;
            String distText = "§f移动: §e" + String.format("%.1f", progress * 100) + "%";
            graphics.drawString(mc.font, Component.literal(distText), x, y, 0xFFFFFFFF);
        } else {
            String distText = "§f移动: §a已满";
            graphics.drawString(mc.font, Component.literal(distText), x, y, 0xFFFFFFFF);
        }

        y += 12;

        // 操作提示（用户：灵梦不再有技能切换 —— 按下技能键 = 释放"二重结界"；封魔针改为手持初始物品「御币」右键发射，可穿过门）
        String abilityKey = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();
        String opHint = String.format("§7技能键§e[%s]§7：释放 §f梦符「二重结界」", abilityKey);
        graphics.drawString(mc.font, Component.literal(opHint), x, y, 0xFFFFFFFF);
        y += 12;
        String needleHint = "§7封魔针：§e手持御币右键§7即可射出（§a可穿过门§7）";
        graphics.drawString(mc.font, Component.literal(needleHint), x, y, 0xFFFFFFFF);

        y += 12;

        // 当前状态
        String stateText = "§c「博丽之巫女」";
        graphics.drawString(mc.font, Component.literal(stateText), x, y, 0xFFFFFFFF);
    }
}