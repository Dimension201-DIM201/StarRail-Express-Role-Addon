package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.YukariCoordinate;
import com.dim201.dim201addon.YukariPlayerComponent;
import com.dim201.dim201addon.YukariRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;

import java.awt.Color;

/**
 * 八云紫坐标方块高亮渲染器
 *
 * <p>原理：注册 {@link WorldRenderEvents#AFTER_TRANSLUCENT}，本地玩家为八云紫时，
 * 将已设置的坐标方块以角色紫透视线框高亮（复用 SRE 任务方块透视管线，
 * 与电击狂人静电方块高亮同一机制）。坐标由 YukariPlayerComponent 的 CCA 同步下发，
 * 存储的是方块中心点（+0.5），用 {@link BlockPos#containing} 还原方块坐标；
 * 仅渲染当前维度且已加载的坐标。
 */
public final class YukariCoordinateRenderer {

    /** 角色紫（与 YukariRole 角色色 0xFF800080 一致） */
    private static final Color HIGHLIGHT_COLOR = new Color(0x80, 0x00, 0x80);

    private YukariCoordinateRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(mc.player.level());
            if (gameWorld == null || !gameWorld.isRole(mc.player, YukariRole.YUKARI)) return;

            YukariPlayerComponent comp = ModComponents.YUKARI.get(mc.player);
            if (comp == null) return;

            String currentDim = mc.level.dimension().location().toString();
            for (YukariCoordinate c : comp.coordinates) {
                if (!c.level.equals(currentDim)) continue;
                BlockPos pos = BlockPos.containing(c.x, c.y, c.z);
                if (!mc.level.isLoaded(pos)) continue;
                TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, HIGHLIGHT_COLOR, 1.0f, true, 0f);
            }
        });
    }
}
