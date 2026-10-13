package com.dim201.dim201addon.client.electric;

import com.dim201.dim201addon.role.ElectricManiacRole;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;

import java.awt.Color;

/**
 * 电击狂人静电方块渲染器
 *
 * <p>原理：注册 {@link WorldRenderEvents#AFTER_TRANSLUCENT}，
 * 对本地玩家为电击狂人时，将静电方块用透视线框高亮（参考任务方块透视）。
 *
 * <p>复用 SRE 的 {@link TaskBlockOverlayRenderer#renderBlockOverlay}，
 * 该方法使用 NO_DEPTH_TEST + VIEW_OFFSET_Z_LAYERING 实现透视效果。
 */
public final class ElectricManiacBlockRenderer {

    private ElectricManiacBlockRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!ElectricManiacRole.isElectricManiacClient(mc.player)) return;

            Color color = new Color(
                    (ElectricManiacClientState.BLOCK_HIGHLIGHT_COLOR >> 16) & 0xFF,
                    (ElectricManiacClientState.BLOCK_HIGHLIGHT_COLOR >> 8) & 0xFF,
                    ElectricManiacClientState.BLOCK_HIGHLIGHT_COLOR & 0xFF);

            for (BlockPos pos : ElectricManiacClientState.electrifiedBlocks) {
                if (!mc.level.isLoaded(pos)) continue;
                TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, color, 1.0f, true, 0f);
            }
        });
    }
}
