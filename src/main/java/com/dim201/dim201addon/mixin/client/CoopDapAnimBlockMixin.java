package com.dim201.dim201addon.mixin.client;

import com.cooptest.client.CoopAnimationHandler;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 动画层兜底拦截：对局中非东堂/虎杖的本地玩家，
 * 直接阻止 dap 蓄力手臂动画播放（无论从哪条代码路径触发）。
 */
@Mixin(CoopAnimationHandler.class)
public class CoopDapAnimBlockMixin {

    private static boolean dim201$shouldBlock(Player player) {
        // 只拦截本地玩家自己的动画，其他玩家的动画来自网络状态同步，不受影响
        if (!(player instanceof net.minecraft.client.player.LocalPlayer)) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null) return false;
        SRERole role = gw.getRole(player);
        if (role == null) return false; // 对局外
        return !gw.isRole(player, TodoAoiRole.TODO_AOI)
                && !gw.isRole(player, ItadoriRole.ITADORI);
    }

    @Inject(method = "startDapCharge", at = @At("HEAD"), require = 0, remap = false, cancellable = true)
    private static void dim201$blockStart(Player player, CallbackInfo ci) {
        if (dim201$shouldBlock(player)) ci.cancel();
    }

    @Inject(method = "playDapChargeIdle", at = @At("HEAD"), require = 0, remap = false, cancellable = true)
    private static void dim201$blockIdle(Player player, CallbackInfo ci) {
        if (dim201$shouldBlock(player)) ci.cancel();
    }

    @Inject(method = "startFireDapCharge", at = @At("HEAD"), require = 0, remap = false, cancellable = true)
    private static void dim201$blockFire(Player player, CallbackInfo ci) {
        if (dim201$shouldBlock(player)) ci.cancel();
    }
}
