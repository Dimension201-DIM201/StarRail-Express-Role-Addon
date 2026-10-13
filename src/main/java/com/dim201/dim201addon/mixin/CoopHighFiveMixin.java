package com.dim201.dim201addon.mixin;

import com.cooptest.ChargedDapHandler;
import com.dim201.dim201addon.cooperation.BrotherSyncManager;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * 拦截 coopmod 蓄力击掌（G 键）：
 * <ul>
 *   <li>对局中只有东堂葵 / 虎杖悠仁能开始蓄力（onChargeStart）</li>
 *   <li>对局中只有东堂 × 虎杖组合才能执行击掌效果（executeDap）</li>
 *   <li>对局外不限制</li>
 * </ul>
 */
@Mixin(ChargedDapHandler.class)
public class CoopHighFiveMixin {

    /**
     * 拦截蓄力开始：对局中非东堂/虎杖玩家无法开始 G 键蓄力。
     * 取消后向客户端广播取消通知，避免本地动画/进度条卡住。
     */
    @Inject(method = "onChargeStart", at = @At("HEAD"), require = 0, cancellable = true)
    private static void dim201$blockNonBrotherCharge(ServerPlayer player, CallbackInfo ci) {
        var gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw != null && gw.isRunning()) {
            if (!TodoAoiRole.isTodo(player) && !ItadoriRole.isItadori(player)) {
                ci.cancel();
                cancelCharge(player);
            }
        }
    }

    /** 通知客户端重置蓄力状态 + 清除手臂动画 */
    private static void cancelCharge(ServerPlayer player) {
        try {
            // 1. 发送 ChargeSyncPayload(charging=false) 清除蓄力进度条
            Method method = ChargedDapHandler.class.getDeclaredMethod(
                    "broadcastChargeCancel", ServerPlayer.class);
            method.setAccessible(true);
            method.invoke(null, player);
        } catch (Exception ignored) {
        }
        try {
            // 2. 发送 AnimStateSyncPayload(NONE=0) 清除手臂蓄力动画
            var poseNetworking = Class.forName("com.cooptest.PoseNetworking");
            Method broadcast = poseNetworking.getDeclaredMethod(
                    "broadcastAnimState", ServerPlayer.class, int.class);
            broadcast.invoke(null, player, 0);
        } catch (Exception ignored) {
        }
    }

    /**
     * 拦截击掌效果执行：对局中双方不是东堂 × 虎杖组合时取消整个击掌效果。
     */
    @Inject(method = "executeDap", at = @At("HEAD"), require = 0, cancellable = true)
    private static void dim201$onBrotherDap(ServerPlayer p1, ServerPlayer p2,
                                            float charge1, float charge2,
                                            float fire1, float fire2,
                                            float syncQuality,
                                            long releaseTime1, long releaseTime2,
                                            CallbackInfo ci) {
        var gw = SREGameWorldComponent.KEY.get(p1.level());
        if (gw != null && gw.isRunning()) {
            boolean p1Todo = TodoAoiRole.isTodo(p1);
            boolean p2Todo = TodoAoiRole.isTodo(p2);
            boolean p1Ita = ItadoriRole.isItadori(p1);
            boolean p2Ita = ItadoriRole.isItadori(p2);
            boolean isBrotherPair = (p1Todo && p2Ita) || (p2Todo && p1Ita);
            if (!isBrotherPair) {
                ci.cancel();
                return;
            }
        }
        int tier = computeTier(charge1, charge2, fire1, fire2);
        BrotherSyncManager.onHighFive(p1, p2, tier);
    }

    private static int computeTier(float c1, float c2, float f1, float f2) {
        if (f1 >= 0.90f && f2 >= 0.90f) return 3;
        float avgCharge = (c1 + c2) * 0.5f;
        if (avgCharge >= 0.80f) return 3;
        if (avgCharge >= 0.50f) return 2;
        if (avgCharge >= 0.20f) return 1;
        return 0;
    }
}
