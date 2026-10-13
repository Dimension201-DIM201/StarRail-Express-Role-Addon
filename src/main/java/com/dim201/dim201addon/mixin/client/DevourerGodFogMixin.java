package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.DevourerGodPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.DevourerGodRole;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 神明吞噬者时刻天蓝色雾渲染 Mixin。
 *
 * <p>原理：完全镜像 {@link AdachiFogMixin} 的红雾实现，仅将雾色改为天蓝色，
 * 触发条件改为「场上存在神明吞噬者且其 fogActive 为 true」。
 *
 * <p>双重覆盖原理（同 AdachiFogMixin）：
 * <ol>
 *   <li>雾颜色：在 FogRenderer.setupColor 调用后覆盖 RenderSystem.shaderFogColor</li>
 *   <li>雾距离/形状：在 FogRenderer.setupFog 调用后覆盖 start/end/shape</li>
 * </ol>
 */
@Mixin(LevelRenderer.class)
public class DevourerGodFogMixin {

    // RenderSystem.shaderFogColor 私有静态字段缓存（反射只找一次，避免每帧开销）
    @Unique
    private static Field DIM201$GodFogColorField;
    @Unique
    private static boolean DIM201$godFogColorFieldResolved = false;

    // 天蓝色雾 RGBA：中蓝、中绿、低红 → 神明降临氛围
    @Unique
    private static final float[] DIM201$SKY_BLUE_FOG_COLOR = new float[]{0.35f, 0.65f, 0.85f, 1.0f};

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
            shift = At.Shift.AFTER))
    private void dim201$godAfterSetupFog(CallbackInfo ci) {
        if (!isGodFogActive()) return;
        RenderSystem.setShaderFogStart(0.0f);
        RenderSystem.setShaderFogEnd(72.0f);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        // 同时覆盖颜色（防止 setupColor 比 setupFog 先跑导致颜色被后续覆盖，这里兜底再设一次）
        dim201$applySkyBlueFogColor();
    }

    /**
     * 在 FogRenderer.setupColor 调用后覆盖雾颜色。
     * 与 AdachiFogMixin 同理，在 renderLevel 中对 setupColor 的调用点做 INVOKE + AFTER。
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            shift = At.Shift.AFTER))
    private void dim201$godAfterSetupColor(CallbackInfo ci) {
        if (!isGodFogActive()) return;
        dim201$applySkyBlueFogColor();
    }

    @Unique
    private static boolean isGodFogActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.getNullable(mc.level);
        if (gameComponent == null) return false;
        // 仅对局运行期间生效：大厅/非对局场景渲染神雾属于串场
        if (!gameComponent.isRunning()) return false;

        for (Player player : mc.level.players()) {
            if (!gameComponent.isRole(player, DevourerGodRole.DEVOURER_GOD_ROLE)) continue;
            DevourerGodPlayerComponent godComp = ModComponents.DEVOURER_GOD.getNullable(player);
            if (godComp != null && godComp.fogActive) return true;
        }
        return false;
    }

    @Unique
    private static void dim201$applySkyBlueFogColor() {
        try {
            if (!DIM201$godFogColorFieldResolved) {
                DIM201$GodFogColorField = RenderSystem.class.getDeclaredField("shaderFogColor");
                DIM201$GodFogColorField.setAccessible(true);
                DIM201$godFogColorFieldResolved = true;
            }
            if (DIM201$GodFogColorField != null) {
                // shaderFogColor 是 static final float[]，final 只是引用 final，数组元素可变
                float[] color = (float[]) DIM201$GodFogColorField.get(null);
                if (color != null && color.length >= 4) {
                    System.arraycopy(DIM201$SKY_BLUE_FOG_COLOR, 0, color, 0, 4);
                }
            }
        } catch (Throwable ignored) {
            // 反射失败不崩溃，最多雾不变色
        }
    }
}
