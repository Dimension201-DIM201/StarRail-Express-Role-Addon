package com.dim201.dim201addon.client;

import com.dim201.dim201addon.packet.ClownpieceRefreshC2SPacket;
import dev.doctor4t.wathe.util.ShopEntry;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 克劳恩皮斯背包界面客户端 UI（仅客户端加载）
 *
 * <p>在 {@code Dim201AddonClient.onInitializeClient()} 中通过
 * {@link #bindInventoryWidgets()} 注册 Consumer 到 SRERole.addChild。
 *
 * <p>为什么独立成类？ClownpieceRole.java 在 common 目录，
 * 禁止引用任何 net.minecraft.client.* 类（否则服务端加载时 NoClassDefFoundError）。
 */
public class ClownpieceClientUI {

    private static final Logger LOGGER = LoggerFactory.getLogger("Dim201Addon-Clownpiece-Client");

    /**
     * 向背包界面注入玩家头像按钮列表
     *
     * <p>由 ClownpieceInventoryMixin 在 LimitedInventoryScreen.init() TAIL 处调用。
     * 通过反射调用运行时注入的 addDrawableChild 方法（Noelle's Roles Mixin 注入）。
     */
    public static void buildInventoryWidgets(LimitedInventoryScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        UUID selfId = mc.player.getUUID();
        List<PlayerInfo> players = new ArrayList<>();
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            if (!info.getProfile().getId().equals(selfId)) {
                players.add(info);
            }
        }

        int apart = 36;
        int perPage = 8;
        int centerY = (screen.height - 32) / 2;
        int y = centerY + 80;

        int maxDisplay = Math.min(players.size(), perPage);
        int startX = screen.width / 2 - maxDisplay * apart / 2 + 9;
        for (int i = 0; i < maxDisplay; i++) {
            PlayerInfo info = players.get(i);
            int x = startX + apart * i;
            ClownpiecePlayerButton btn = new ClownpiecePlayerButton(screen, x, y, info);
            addWidgetViaReflection(screen, btn);
        }
    }

    /**
     * 反射调用运行时存在的 addDrawableChild 方法添加控件。
     *
     * <p>经诊断确认运行时 LimitedInventoryScreen 上存在：
     * <pre>addDrawableChild(class_4185) public   // class_4185 = Button</pre>
     * 该方法由 Noelle's Roles 通过 Mixin 注入，编译时 SRE JAR 里没有，
     * 所以必须用反射在运行时调用。
     */
    private static void addWidgetViaReflection(LimitedInventoryScreen screen, Button widget) {
        try {
            // 在整个类层级找 public addDrawableChild(Button)
            for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals("addDrawableChild")) continue;
                    if (m.getParameterCount() != 1) continue;
                    if (!m.getParameterTypes()[0].isAssignableFrom(Button.class)) continue;
                    m.setAccessible(true);
                    m.invoke(screen, widget);
                    return;
                }
            }
            LOGGER.error("addDrawableChild not found on {}", screen.getClass().getName());
        } catch (Exception e) {
            LOGGER.error("Failed to add widget via reflection", e);
        }
    }

    // ========== 克劳恩皮斯专用玩家头像按钮 ==========

    /**
     * 克劳恩皮斯专用玩家头像按钮。
     *
     * <p>完全对齐 SilencerPlayerWidget（静语者）的视觉风格：
     * <ul>
     *   <li>头像下方 4px 居中的死亡 / 杀手同伙标签</li>
     *   <li>hover 时蓝色边框高亮 + 玩家名 tooltip</li>
     *   <li>覆写 renderString 防止 Button 默认名字和自定义 displayText 冲突</li>
     * </ul>
     */
    private static class ClownpiecePlayerButton extends Button {

        private final LimitedInventoryScreen screen;
        public final PlayerInfo targetInfo;
        private Component displayText = Component.empty();
        private List<net.minecraft.util.FormattedCharSequence> cachedLines = new ArrayList<>();

        ClownpiecePlayerButton(LimitedInventoryScreen screen, int x, int y, PlayerInfo targetInfo) {
            super(x, y, 16, 16,
                    Component.nullToEmpty(targetInfo.getProfile().getName()),
                    btn -> ClientPlayNetworking.send(
                            new ClownpieceRefreshC2SPacket(targetInfo.getProfile().getId())),
                    DEFAULT_NARRATION);
            this.screen = screen;
            this.targetInfo = targetInfo;
            initDisplayText();
        }

        /** 冷却中阻止点击（服务端会校验，但客户端也要即时反馈） */
        @Override
        public void onPress() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var abilityComp = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(mc.player);
                if (abilityComp != null && abilityComp.cooldown > 0) return;
            }
            super.onPress();
        }

        /**
         * 根据目标玩家状态设置头像下方的标签文字。
         */
        private void initDisplayText() {
            boolean dead = targetInfo.getGameMode() != GameType.ADVENTURE;
            if (dead) {
                setDisplayText(Component.translatable("hud.general.dead")
                        .withStyle(ChatFormatting.DARK_RED));
            } else if (SREClient.gameComponent != null) {
                SRERole role = SREClient.gameComponent.getRole(targetInfo.getProfile().getId());
                if (role != null && (role.isKillerTeam() || role.isNeutralForKiller())) {
                    setDisplayText(Component.translatable("hud.general.killer_friend")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            var abilityComp = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(mc.player);
            boolean onCooldown = abilityComp != null && abilityComp.cooldown > 0;

            // Button 默认背景框
            super.renderWidget(context, mouseX, mouseY, delta);

            if (!onCooldown) {
                // ========== 可用状态：正常显示 ==========
                context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
                PlayerFaceRenderer.draw(context, targetInfo.getSkin().texture(), this.getX(), this.getY(), 16);

                if (this.isHovered()) {
                    drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                    context.renderTooltip(mc.font,
                            Component.nullToEmpty(targetInfo.getProfile().getName()),
                            this.getX() - 4 - mc.font.width(targetInfo.getProfile().getName()) / 2,
                            this.getY() - 9);
                }
            } else {
                // ========== 冷却状态：暗色遮罩 + 红色秒数（和普奇神父同款）==========
                context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
                context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
                PlayerFaceRenderer.draw(context, targetInfo.getSkin().texture(), this.getX(), this.getY(), 16);
                context.setColor(1f, 1f, 1f, 1f);
                context.drawString(mc.font, String.valueOf(abilityComp.cooldown / 20),
                        this.getX(), this.getY(), java.awt.Color.RED.getRGB(), true);
            }

            // 头像下方死亡/杀手同伙标记
            renderDisplayText(context);
        }

        /**
         * 和 SilencerPlayerWidget 同款的 hover 蓝色边框高亮
         */
        private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
            int color = -1862287543;
            context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
            context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
            context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
        }

        private void setDisplayText(Component text) {
            this.displayText = text;
            this.cachedLines.clear();
        }

        /**
         * 头像下方 4px 居中渲染 displayText（死亡/杀手同伙标签）
         */
        private void renderDisplayText(GuiGraphics context) {
            if (displayText == null || displayText.getString().isEmpty()) return;

            Font font = Minecraft.getInstance().font;
            int maxWidth = 50;
            int lineHeight = font.lineHeight + 1;
            int yOffset = 4;

            if (cachedLines.isEmpty()) {
                cachedLines = font.split(displayText, maxWidth);
            }

            int startY = this.getY() + this.getHeight() + yOffset;

            for (int i = 0; i < cachedLines.size(); i++) {
                net.minecraft.util.FormattedCharSequence line = cachedLines.get(i);
                int lineWidth = font.width(line);
                int x = this.getX() + (this.getWidth() - lineWidth) / 2;
                int y = startY + (i * lineHeight);
                context.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);
                context.drawString(font, line, x, y, 0xFFFFFF, true);
            }
        }

        /**
         * 覆写为空，防止 Button 默认在头像下方渲染玩家名标签
         */
        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }
}
