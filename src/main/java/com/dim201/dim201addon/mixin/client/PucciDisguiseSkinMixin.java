package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.client.PucciClientUI;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 拦截 PlayerRenderer.getTextureLocation，为被普奇神父变形的玩家替换皮肤纹理。
 *
 * <p><b>正确架构（S2C 广播 + 静态 Map）：</b>
 * <pre>
 * 服务端 handler 成功变形 → broadcast PucciDisguiseS2CPacket(被变形者UUID, 外观UUID)
 * 所有客户端收到 → PucciClientUI.handleS2CPacket → DISGUISE_CACHE.put(key, value)
 * 皮肤渲染时 → PucciClientUI.DISGUISE_CACHE.get(player.getUUID()) → 查到外观UUID → 替换
 * </pre>
 *
 * <p><b>为什么 Morphling 能用 RoleData，普奇不行？</b>
 * Morphling 变的是**自己**——Morphling 玩家身上本来就有 setRoleData 绑定的组件。
 * 普奇变的是**别人**——被变形者没有绑定 setRoleData(PucciDisguiseRoleData::new)，
 * RoleData.getOrCreate 在非角色玩家上不会创建组件，客户端侧组件永远是空壳。
 * 所以普奇必须用 S2C 广播绕过 RoleData 的 per-player 组件限制。
 */
@Mixin(PlayerRenderer.class)
public class PucciDisguiseSkinMixin {

    private static final ThreadLocal<Boolean> IS_IN_PUCCI_DISGUISE = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void dim201$pucciDisguiseSkin(AbstractClientPlayer player,
                                          CallbackInfoReturnable<ResourceLocation> cir) {
        if (IS_IN_PUCCI_DISGUISE.get()) return;
        try {
            IS_IN_PUCCI_DISGUISE.set(true);

            // 直接查静态 Map（S2C 广播维护的缓存）
            UUID appearanceUuid = PucciClientUI.DISGUISE_CACHE.get(player.getUUID());
            if (appearanceUuid == null) return;

            var playerInfo = ClientSkinCache.getCachedPlayerInfo(appearanceUuid);
            if (playerInfo == null) return;
            var skin = playerInfo.getSkin();
            if (skin == null) return;
            var texture = skin.texture();
            if (texture == null) return;

            cir.setReturnValue(texture);
            cir.cancel();
        } finally {
            IS_IN_PUCCI_DISGUISE.set(false);
        }
    }
}
