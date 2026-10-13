package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.client.screen.RoleImageRenderer;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在角色介绍界面右侧面板渲染完成后，叠加背景图片层。
 * <p>不单独占 Tab，只要选中对应角色就显示（切任何 Tab 都不消失）。
 */
@Mixin(RoleIntroduceScreen.class)
public class RoleIntroduceScreenMixin {

    @Shadow
    private Object selectedRole;

    @Shadow
    private int rightX;

    @Shadow
    private int rightW;

    // @Shadow 私有方法可直接写，不要求 public
    @Shadow
    private int getRightContentTop() { return 0; }

    @Shadow
    private int getRightContentHeight() { return 0; }

    private static final int PANEL_PAD = 6;
    private static final int SCROLL_W = 7;

    @Inject(method = "renderRightPanel", at = @At("TAIL"), remap = false)
    private void dim201$renderRoleImageOverlay(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        if (!(selectedRole instanceof SRERole role)) return;

        ResourceLocation id = role.identifier();
        if (!"dim201addon".equals(id.getNamespace())) return;

        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(
            "dim201addon", "textures/gui/role_intro/" + id.getPath() + ".png"
        );

        // 与 renderRightPanel 第 1978 行的 contentWidth 计算一致
        int contentX = rightX + PANEL_PAD;
        int contentWidth = rightW - PANEL_PAD * 2 - SCROLL_W - 2;
        int contentY = getRightContentTop();
        int contentH = getRightContentHeight();

        RoleImageRenderer.render(g, tex, contentX, contentY, contentWidth, contentH);
    }
}
