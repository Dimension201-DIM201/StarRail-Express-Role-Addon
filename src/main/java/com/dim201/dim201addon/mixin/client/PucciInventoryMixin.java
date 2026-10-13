package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.client.PucciClientUI;
import com.dim201.dim201addon.role.PucciRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 SRE 背包界面 init() 末尾注入普奇神父的两阶段变形选择头像按钮。
 *
 * <p>和 ClownpieceInventoryMixin 完全同模式：
 * <ul>
 *   <li>@Mixin(LimitedInventoryScreen.class) @Inject(method="init", at=@At("TAIL"))</li>
 *   <li>用 identifier 比较角色身份（防止 SRERole.equals 引用比较问题）</li>
 *   <li>调用 PucciClientUI.buildInventoryWidgets(screen) 构建按钮</li>
 *   <li>通过反射调用运行时注入的 addDrawableChild 方法添加控件</li>
 * </ul>
 */
@Mixin(LimitedInventoryScreen.class)
public class PucciInventoryMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void dim201$pucciInjectPlayerButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var gameWorld = SREGameWorldComponent.KEY.getNullable(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null) return;

        if (!role.identifier().equals(PucciRole.PUCCI_ID)) return;

        LimitedInventoryScreen screen = (LimitedInventoryScreen) (Object) this;
        PucciClientUI.buildInventoryWidgets(screen);
    }
}
