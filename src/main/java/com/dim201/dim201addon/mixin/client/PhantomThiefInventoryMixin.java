package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.client.PhantomThiefClientUI;
import com.dim201.dim201addon.role.PhantomThiefRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 SRE 背包界面 init() 末尾注入大怪盗玩家头像按钮（预告信选择目标）。
 * 与 ClownpieceInventoryMixin 同款方案：addDrawableChild 是运行时注入方法，
 * 由 PhantomThiefClientUI 反射调用。
 */
@Mixin(LimitedInventoryScreen.class)
public class PhantomThiefInventoryMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void dim201$phantomThiefInjectPlayerButtons(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var gameWorld = SREGameWorldComponent.KEY.getNullable(mc.player.level());
        if (gameWorld == null) return;
        var role = gameWorld.getRole(mc.player);
        if (role == null) return;

        // 用 identifier 比较，避免 SRERole.equals 身份比较问题
        if (!role.identifier().equals(PhantomThiefRole.PHANTOM_THIEF_ID)) return;

        LimitedInventoryScreen screen = (LimitedInventoryScreen) (Object) this;
        PhantomThiefClientUI.buildInventoryWidgets(screen);
    }
}
