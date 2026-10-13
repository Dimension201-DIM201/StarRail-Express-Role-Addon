package com.dim201.dim201addon.mixin.client;

import com.cooptest.client.ChargedDapClientHandler;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 客户端根源拦截蓄力击掌：
 * 对局中（本地玩家有角色）且不是东堂/虎杖时，在每 tick 开头将 wasKeyPressed 置 true，
 * 使整个按键按下分支（蓄力状态、网络包、手臂动画）全部跳过。
 * 对局外（getRole 返回 null）不做任何限制。
 */
@Mixin(ChargedDapClientHandler.class)
public class ChargedDapBlockMixin {

    /** 反射字段缓存（嵌套类不会被合并进目标类） */
    private static final class FieldHolder {
        static final Field WAS_KEY_PRESSED;
        static {
            Field f = null;
            try {
                f = ChargedDapClientHandler.class.getDeclaredField("wasKeyPressed");
                f.setAccessible(true);
            } catch (Exception ignored) {
            }
            WAS_KEY_PRESSED = f;
        }
    }

    /** 对局中且非东堂/虎杖 → true；对局外或搭档 → false */
    private static boolean dim201$shouldBlock(Minecraft client) {
        if (client.player == null) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(client.player.level());
        if (gw == null) return false;
        SRERole role = gw.getRole(client.player);
        if (role == null) return false; // 对局外
        return !gw.isRole(client.player, TodoAoiRole.TODO_AOI)
                && !gw.isRole(client.player, ItadoriRole.ITADORI);
    }

    @Inject(method = "lambda$register$22", at = @At("HEAD"), require = 0, remap = false)
    private static void dim201$blockNonBrotherCharge(Minecraft client, CallbackInfo ci) {
        if (!dim201$shouldBlock(client)) return;
        try {
            if (FieldHolder.WAS_KEY_PRESSED != null) {
                FieldHolder.WAS_KEY_PRESSED.setBoolean(null, true);
            }
        } catch (Exception ignored) {
        }
    }
}
