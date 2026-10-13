package com.dim201.dim201addon.client;

import com.dim201.dim201addon.packet.PucciDisguiseC2SPacket;
import com.dim201.dim201addon.packet.PucciDisguiseS2CPacket;
import com.dim201.dim201addon.role.PucciDisguiseRoleData;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 普奇神父背包界面客户端 UI（仅客户端加载）
 *
 * <p>两阶段变形选择：
 * <ol>
 *   <li>第一次点击玩家头像 → 记录为「被变形者」</li>
 *   <li>第二次点击另一个玩家头像 → 发送网络包，让第一个人变成第二个人的样子</li>
 * </ol>
 *
 * <p>皮肤替换由 PucciDisguiseSkinMixin 在皮肤渲染层拦截完成，
 * 数据存在被变形者自己身上（Morphling 同款架构），客户端 UI 不参与皮肤逻辑。
 */
public class PucciClientUI {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Dim201Addon-Pucci-Client");

    /** 第一阶段选择：谁被变形（SwapperPlayerWidget 同款两阶段模式） */
    public static UUID firstTargetSelected = null;

    /**
     * 皮肤替换缓存：key=被变形者UUID → value=外观目标UUID。
     * ConcurrentHashMap 保证线程安全（客户端渲染主线程 + 网络包线程）。
     *
     * <p>为什么用静态 Map 而不是 RoleData？
     * 普奇变的是别人——被变形者的角色没有绑定 setRoleData(PucciDisguiseRoleData::new)，
     * RoleData.getOrCreate 在非角色玩家上不会创建组件，客户端侧更是永远空壳。
     * S2C 广播包直接写静态 Map 是最可靠的方案。
     *
     * <p>对局重置时需调 {@link #resetAll()} 清空（在 ResetPlayerEvent 里调用）。
     */
    public static final Map<UUID, UUID> DISGUISE_CACHE = new ConcurrentHashMap<>();

    /**
     * 处理服务端广播的 S2C 包：更新 DISGUISE_CACHE。
     * appearanceTarget 非空 = 设置变形；appearanceTarget 为空 = 清除变形。
     */
    public static void handleS2CPacket(PucciDisguiseS2CPacket packet) {
        if (packet.appearanceTarget() != null) {
            DISGUISE_CACHE.put(packet.disguiseTarget(), packet.appearanceTarget());
        } else {
            DISGUISE_CACHE.remove(packet.disguiseTarget());
        }
    }

    /** 对局重置时清空所有变形记录。 */
    public static void resetAll() {
        DISGUISE_CACHE.clear();
    }

    // ========== 背包界面头像按钮构建 ==========

    /**
     * 在 LimitedInventoryScreen.init() TAIL 处被 Mixin 调用，注入两阶段选择头像按钮。
     *
     * <p>坐标公式完全对齐 SilencerPlayerWidget / SwapperPlayerWidget / ClownpiecePlayerWidget：
     * apart=36, perPage=8, centerY=(h-32)/2, y=centerY+80, startX=w/2-n*36/2+9
     */
    public static void buildInventoryWidgets(LimitedInventoryScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        // 第一阶段选择在打开新界面时自动清空，防止上一轮残留
        firstTargetSelected = null;

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
            PucciPlayerButton btn = new PucciPlayerButton(screen, x, y, info);
            addWidgetViaReflection(screen, btn);
        }
    }

    /**
     * 反射调用运行时注入的 addDrawableChild(Button)。
     * 同 ClownpieceClientUI.addWidgetViaReflection。
     */
    private static void addWidgetViaReflection(LimitedInventoryScreen screen, Button widget) {
        try {
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

    // ========== 普奇专用玩家头像按钮 ==========

    /**
     * 两阶段选择头像按钮。
     *
     * <p>同 SwapperPlayerWidget 的 playerChoiceOne 逻辑：
     * 第一次点击存 firstTargetSelected；第二次点击发 PucciDisguiseC2SPacket(uuid1, uuid2)。
     *
     * <p>视觉风格同 ClownpiecePlayerWidget：POISON 外圈 + PlayerFaceRenderer.draw +
     * 死亡/杀手同伙标签 + hover tooltip + 覆写 renderString。
     */
    private static class PucciPlayerButton extends Button {

        private final LimitedInventoryScreen screen;
        private final PlayerInfo targetInfo;
        private Component displayText = Component.empty();
        private final List<net.minecraft.util.FormattedCharSequence> cachedLines = new ArrayList<>();

        PucciPlayerButton(LimitedInventoryScreen screen, int x, int y, PlayerInfo targetInfo) {
            super(x, y, 16, 16,
                    Component.nullToEmpty(targetInfo.getProfile().getName()),
                    btn -> handleClick(targetInfo),
                    DEFAULT_NARRATION);
            this.screen = screen;
            this.targetInfo = targetInfo;
            initDisplayText();
        }

        private static void handleClick(PlayerInfo targetInfo) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 冷却中不允许操作（Swapper 同款）
            var abilityComp = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(mc.player);
            if (abilityComp != null && abilityComp.cooldown > 0) return;

            UUID clickedId = targetInfo.getProfile().getId();
            if (firstTargetSelected == null) {
                // 第一阶段：选中被变形者
                firstTargetSelected = clickedId;
            } else {
                // 第二阶段：发送变形请求
                if (!firstTargetSelected.equals(clickedId)) {
                    ClientPlayNetworking.send(
                            new PucciDisguiseC2SPacket(firstTargetSelected, clickedId));
                }
                firstTargetSelected = null;
            }
        }

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
                // ========== 冷却状态：暗色遮罩 + 红色秒数（Swapper/Voodoo 同款）==========
                context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
                context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
                PlayerFaceRenderer.draw(context, targetInfo.getSkin().texture(), this.getX(), this.getY(), 16);

                if (this.isHovered()) {
                    drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                }

                context.setColor(1f, 1f, 1f, 1f);
                context.drawString(mc.font, String.valueOf(abilityComp.cooldown / 20),
                        this.getX(), this.getY(), java.awt.Color.RED.getRGB(), true);
            }

            // 第一目标高亮（冷却中也能看到）
            if (targetInfo.getProfile().getId().equals(firstTargetSelected)) {
                context.fill(this.getX() - 1, this.getY() - 1, this.getX() + 17, this.getY() + 17, 0xFFFF0000);
            }

            // 头像下方标签
            renderDisplayText(context);
        }

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

        private void renderDisplayText(GuiGraphics context) {
            if (displayText == null || displayText.getString().isEmpty()) return;

            Font font = Minecraft.getInstance().font;
            int maxWidth = 50;
            int lineHeight = font.lineHeight + 1;
            int yOffset = 4;

            if (cachedLines.isEmpty()) {
                cachedLines.addAll(font.split(displayText, maxWidth));
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

        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
            // 空实现：Button 默认名字与自定义 displayText 冲突
        }
    }
}
