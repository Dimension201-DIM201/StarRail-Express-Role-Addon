package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.PhantomThiefPlayerComponent;
import com.dim201.dim201addon.packet.PhantomThiefAnnounceC2SPacket;
import com.dim201.dim201addon.packet.PhantomThiefStealC2SPacket;
import com.dim201.dim201addon.role.PhantomThiefRole;
import dev.doctor4t.wathe.util.ShopEntry;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.event.client.CommonInstinctEvents;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
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
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.GKeyRoleSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 大怪盗客户端 UI（仅客户端加载，禁止服务端引用）。
 *
 * <ul>
 *   <li>预告信：背包界面玩家头像按钮（由 PhantomThiefInventoryMixin 注入）</li>
 *   <li>高亮追踪：CommonInstinctEvents 拦截——只把预告目标染成怪盗紫，
 *       其余玩家一律 no() 压制（实现「只能透视到追踪目标」）</li>
 *   <li>被看隐藏：TARGET_HIGHLIGHT_EVENT——杀手阵营观察者无法用直觉透视到大怪盗
 *       （同小透明 GHOST / 芙兰）</li>
 *   <li>HUD：预告倒计时 / 读条进度 / 胜利计数 / 收藏层数 / 道具状态</li>
 *   <li>G 键：妙手空空开始读条（发包，服务端校验）</li>
 * </ul>
 */
public class PhantomThiefClientUI {

    private static final Logger LOGGER = LoggerFactory.getLogger("Dim201Addon-PhantomThief");

    /** 追踪目标的高亮颜色（怪盗紫） */
    private static final int TRACK_COLOR = 0x9B59B6;

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        // G 键：妙手空空（与吞噬者/蕾忍同款：客户端拦截 → 发包 → 服务端处理）
        GKeyRoleSkill.register(PhantomThiefRole.PHANTOM_THIEF, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new PhantomThiefStealC2SPacket());
            return true;
        });

        // 透视事件：第一环拦截（对所有存活目标触发）
        // 本人是大怪盗 → 追踪目标返回怪盗紫，其他人返回 no() 压制默认高亮
        // 注意：仅在直觉开启时干预；直觉关闭必须 pass()，否则结果会在
        // InstinctManager 中被提前消费，导致没开直觉目标也发光。
        CommonInstinctEvents.ALIVE_COMMON_BEFORE_EVENT.register((self, target, isInstinctEnabled) -> {
            if (!isInstinctEnabled) {
                return TrueFalseAndCustomResult.pass();
            }
            if (!(target instanceof Player targetPlayer) || !(self instanceof Player selfPlayer)) {
                return TrueFalseAndCustomResult.pass();
            }
            var gameWorld = SREGameWorldComponent.KEY.get(selfPlayer.level());
            if (gameWorld == null || !gameWorld.isRole(selfPlayer, PhantomThiefRole.PHANTOM_THIEF)) {
                return TrueFalseAndCustomResult.pass();
            }
            PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(selfPlayer);
            if (comp.phase == PhantomThiefPlayerComponent.Phase.NONE || comp.targetUuid == null) {
                // 无预告：不透视为任何玩家
                return TrueFalseAndCustomResult.no();
            }
            if (targetPlayer.getUUID().equals(comp.targetUuid)) {
                return TrueFalseAndCustomResult.custom(TRACK_COLOR);
            }
            return TrueFalseAndCustomResult.no();
        });

        // 被看事件：大怪盗不会被杀手直觉透视（与 SRE 小透明 GHOST、芙兰同款）。
        // 注册在「被看者」角色 ID 上：当观察者（viewer）是杀手阵营且本人存活时，
        // 对大怪盗(target)的直觉高亮返回 disallow()，杀手透视看不到大怪盗。
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(PhantomThiefRole.PHANTOM_THIEF_ID,
                (client, viewer, target, isInstinctEnabled) -> {
                    if (SREClient.gameComponent != null
                            && SREClient.isPlayerAliveAndInSurvival()
                            && SREClient.gameComponent.isKillerTeam(viewer)) {
                        return TrueFalseAndCustomResult.disallow();
                    }
                    return TrueFalseAndCustomResult.pass();
                });

        LOGGER.info("大怪盗客户端 UI 注册完成");
    }

    // ==================== 背包界面头像按钮 ====================

    /**
     * 向背包界面注入玩家头像按钮（由 Mixin 在 init() TAIL 调用）。
     * 预告进行中（phase != NONE）时按钮置灰禁用。
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
            addWidgetViaReflection(screen, new AnnouncePlayerButton(x, y, info));
        }
    }

    /**
     * 反射调用运行时存在的 addDrawableChild（Noelle's Roles Mixin 注入，编译期不可见）。
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

    /**
     * 预告信玩家头像按钮：点击 → 发送 PhantomThiefAnnounceC2SPacket。
     * 预告进行中显示倒计时并禁用（服务端同样校验，客户端仅即时反馈）。
     */
    private static class AnnouncePlayerButton extends Button {

        private final PlayerInfo targetInfo;

        AnnouncePlayerButton(int x, int y, PlayerInfo targetInfo) {
            super(x, y, 16, 16,
                    Component.nullToEmpty(targetInfo.getProfile().getName()),
                    btn -> ClientPlayNetworking.send(
                            new PhantomThiefAnnounceC2SPacket(targetInfo.getProfile().getId())),
                    DEFAULT_NARRATION);
            this.targetInfo = targetInfo;
        }

        /** 预告进行中禁止点击 */
        @Override
        public void onPress() {
            if (isBusy()) return;
            super.onPress();
        }

        @Override
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            boolean busy = isBusy();

            // Button 默认背景框
            super.renderWidget(context, mouseX, mouseY, delta);

            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetInfo.getSkin().texture(), this.getX(), this.getY(), 16);

            if (busy) {
                // 预告进行中 / 冷却中：暗色遮罩 + 剩余秒数
                context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
                context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
                PlayerFaceRenderer.draw(context, targetInfo.getSkin().texture(), this.getX(), this.getY(), 16);
                context.setColor(1f, 1f, 1f, 1f);
                int seconds;
                if (isAnnouncing()) {
                    // 预告追踪剩余时间
                    PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(mc.player);
                    seconds = Math.max(0, (comp.announceTicks + 19) / 20);
                } else {
                    // 预告信冷却剩余时间（复用 SRE 原生技能冷却）
                    seconds = (announceCooldownTicks() + 19) / 20;
                }
                context.drawString(mc.font, String.valueOf(seconds),
                        this.getX(), this.getY(), java.awt.Color.RED.getRGB(), true);
            } else if (this.isHovered()) {
                drawHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(mc.font,
                        Component.literal("发送预告信 → ").append(Component.nullToEmpty(targetInfo.getProfile().getName()))
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        this.getX() - 4 - mc.font.width(targetInfo.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        }

        /** 预告进行中或预告信冷却中：禁止点击 */
        private boolean isBusy() {
            return isAnnouncing() || announceCooldownTicks() > 0;
        }

        /** 是否正处于预告追踪/读条阶段 */
        private boolean isAnnouncing() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(mc.player);
            return comp.phase != PhantomThiefPlayerComponent.Phase.NONE;
        }

        /** 预告信冷却剩余 tick（复用 SRE 原生技能冷却，与普奇/克劳恩皮斯同源） */
        private int announceCooldownTicks() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return 0;
            var ability = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(mc.player);
            return ability != null ? ability.cooldown : 0;
        }

        private void drawHighlight(GuiGraphics context, int x, int y, int z) {
            int color = -1862287543;
            context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
            context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
            context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
        }

        /** 覆写为空，防止 Button 默认渲染玩家名标签 */
        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }
}
