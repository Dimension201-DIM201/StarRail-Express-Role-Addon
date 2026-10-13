package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.KidnapperPlayerComponent;
import com.dim201.dim201addon.role.KidnapperRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 绑架者 HUD（Kidnapper）
 *
 * 显示：
 *  - 未绑架时：提示「金币 > 50，按[技能键]绑架面前 3 格玩家（每秒扣 1 金币）」+ 当前金币数
 *  - 绑架中：被绑架者名字 + 剩余金币 + 每秒扣 1 金币提示 + 按[技能键]可主动释放
 *  - 被绑架的人质视角：显示「你被 X 绑架了，无法操作」
 */
public class KidnapperClientHud implements HudRenderCallback {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        HudRenderCallback.EVENT.register(new KidnapperClientHud());
    }

    @Override
    public void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        var gw = SREGameWorldComponent.KEY.get(mc.player.level());
        if (gw == null) return;

        KidnapperPlayerComponent comp = ModComponents.KIDNAPPER.get(mc.player);
        if (comp == null) return;
        var role = gw.getRole(mc.player);
        boolean isKidnapperRole = (role != null && role.equals(KidnapperRole.KIDNAPPER));
        boolean isHostage = (comp.kidnapperUuid != null); // 被绑了，不管你本来是什么角色

        // ④ 人质视角不画 HUD 框框，actionbar 提示已经在 serverTick 里每 10tick 发一次了
        if (isHostage && !isKidnapperRole) return;
        // 不是绑架者也不是人质 → 啥都不画
        if (!isKidnapperRole && !isHostage) return;

        int x = 10;
        int y = graphics.guiHeight() - 105;
        HudPanels.draw(graphics, x - 5, y - 5, x + 290, y + 60, 0xFF483D8B);

        String abilityKey = org.agmas.noellesroles.client.NoellesrolesClient.abilityBind.getTranslatedKeyMessage().getString();

        // ===== 绑架者视角 HUD =====
        graphics.drawString(mc.font, Component.literal("§l绑架者"), x, y, 0xFF483D8B);
        y += 12;

        // 当前金币（从 SREPlayerShopComponent 取）
        int coins = 0;
        try {
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(mc.player);
            if (shop != null) coins = shop.balance;
        } catch (Exception ignored) {}
        String coinStr = String.format("§7当前金币：§e%d", coins);
        graphics.drawString(mc.font, Component.literal(coinStr), x, y, 0xFFFFFFFF);
        y += 12;

        if (comp.hostageUuid == null) {
            // 未绑架
            // ===== 技能冷却显示（优先显示） =====
            if (comp.abilityCooldownTicksLeft > 0) {
                int secs = (comp.abilityCooldownTicksLeft + 19) / 20;
                graphics.drawString(mc.font,
                        Component.literal(String.format("§c技能冷却中：§f%d §c秒", secs)),
                        x, y, 0xFFFF5555);
                y += 12;
                graphics.drawString(mc.font,
                        Component.literal(String.format("§7金币≥50 且冷却结束后，按§e[%s]§7可绑架", abilityKey)),
                        x, y, 0xFFFFFFFF);
            } else if (coins >= 50) {
                graphics.drawString(mc.font,
                        Component.literal(String.format("§a就绪：按§e[%s]§a绑架面前 3 格的玩家", abilityKey)),
                        x, y, 0xFFFFFFFF);
                y += 12;
                graphics.drawString(mc.font,
                        Component.literal("§7绑架后每秒扣 1 金币，没钱自动释放；再按技能键可主动放"),
                        x, y, 0xFFFFFFFF);
            } else {
                graphics.drawString(mc.font,
                        Component.literal(String.format("§c金币不足（需要 ≥50），按§e[%s]§c无效", abilityKey)),
                        x, y, 0xFFFF5555);
                y += 12;
                graphics.drawString(mc.font,
                        Component.literal("§7去赚金币再来绑架"),
                        x, y, 0xFFFFFFFF);
            }
        } else {
            // 绑架中：显示人质名字 + 每秒扣 1 金币提示
            String hostageName = "未知玩家";
            try {
                UUID h = comp.hostageUuid;
                if (h != null && mc.level != null) {
                    for (Player p : mc.level.players()) {
                        if (p.getUUID().equals(h)) { hostageName = p.getName().getString(); break; }
                    }
                }
            } catch (Exception ignored) {}
            graphics.drawString(mc.font,
                    Component.literal(String.format("§c§l绑架中：§f%s", hostageName)),
                    x, y, 0xFFFF5555);
            y += 12;
            graphics.drawString(mc.font,
                    Component.literal(String.format("§7每秒扣 1 金币 · 按§e[%s]§7主动释放（释放后技能冷却 60 秒）", abilityKey)),
                    x, y, 0xFFFFFFFF);
            y += 12;
            if (coins <= 10) {
                graphics.drawString(mc.font,
                        Component.literal("§c⚠ 金币即将耗尽，人质会被自动释放！"),
                        x, y, 0xFFFF5555);
            } else {
                graphics.drawString(mc.font,
                        Component.literal("§7（金币耗尽自动释放；你死=撕票；人质被别人杀=你得100金币）"),
                        x, y, 0xFFFFFFFF);
            }
        }
    }
}
