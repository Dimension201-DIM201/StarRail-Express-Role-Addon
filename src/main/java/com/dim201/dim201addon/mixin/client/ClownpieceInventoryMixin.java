package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.client.ClownpieceClientUI;
import com.dim201.dim201addon.role.ClownpieceRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 SRE 背包界面 init() 末尾注入克劳恩皮斯玩家头像按钮。
 *
 * <p>为什么用 Mixin 而不是 SRERole.addChild？
 * SRE 的 addChild consumer 只在 getShopEntries() 返回非空时才被调用，
 * 克劳恩皮斯不需要商店道具，所以用 Mixin 直接注入。
 *
 * <p>为什么不用 @Shadow addDrawableChild？
 * addDrawableChild 是 Noelle's Roles 通过 Mixin 注入到 LimitedInventoryScreen 的运行时方法，
 * Mixin 框架不允许 shadow 其他 Mixin 注入的成员。
 * 解决方案：在 ClownpieceClientUI 里用反射调用。
 */
@Mixin(LimitedInventoryScreen.class)
public class ClownpieceInventoryMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void dim201$clownpieceInjectPlayerButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var gameWorld = SREGameWorldComponent.KEY.getNullable(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null) return;

        // 用 identifier 比较，避免 SRERole.equals 身份比较问题
        if (!role.identifier().equals(ClownpieceRole.CLOWNPIECE_ID)) return;

        LimitedInventoryScreen screen = (LimitedInventoryScreen) (Object) this;
        ClownpieceClientUI.buildInventoryWidgets(screen);
    }
}

