package com.dim201.dim201addon.mixin.client;

import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * SRE 准星 HUD 玩家面板显示称号。
 *
 * <p>在 {@code RoleNameRenderer.getDisplayName(Player)} RETURN 处拦截，
 * prepend 称号到玩家名前面。
 *
 * <p>数据源：
 * <ol>
 *   <li>{@link RoleNameRenderer#displayTags} —— NameTagSyncPayload 同步</li>
 *   <li>本地玩家 CCA NameTagInventoryComponent —— shouldSyncWith 对自己返回 true</li>
 * </ol>
 */
@Mixin(RoleNameRenderer.class)
public abstract class RoleNameRendererMixin {

    @Inject(
            method = "getDisplayName(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void dim201$prependNametag(
            net.minecraft.world.entity.player.Player target,
            CallbackInfoReturnable<Component> cir) {

        String nametagStr = dim201$resolve(target);
        if (nametagStr == null || nametagStr.isBlank()) return;

        cir.setReturnValue(Component.literal(dim201$normalize(nametagStr) + " ").append(cir.getReturnValue()));
    }

    @Unique
    private static String dim201$resolve(net.minecraft.world.entity.player.Player target) {
        UUID uuid = target.getUUID();

        // 1) 全局同步 Map
        String fromMap = RoleNameRenderer.displayTags.get(uuid);
        if (fromMap != null && !fromMap.isBlank()) return fromMap;

        // 2) 本地玩家 fallback：自己的 CCA 是同步了的
        if (target instanceof net.minecraft.client.player.LocalPlayer) {
            var comp = net.exmo.sre.nametag.NameTagInventoryComponent.KEY.get(target);
            if (comp != null && comp.CurrentNameTag != null && !comp.CurrentNameTag.isBlank()) {
                return comp.CurrentNameTag;
            }
        }
        return null;
    }

    @Unique
    private static String dim201$normalize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                sb.append('§').append(raw.charAt(++i));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
