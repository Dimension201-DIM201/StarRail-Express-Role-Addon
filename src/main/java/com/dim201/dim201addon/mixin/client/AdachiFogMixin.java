package com.dim201.dim201addon.mixin.client;

import com.dim201.dim201addon.AdachiPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.AdachiRole;
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
 * 足立透复活红雾渲染 Mixin。
 *
 * 【双重覆盖原理】
 * 雾渲染分两个独立阶段，需要分别拦截：
 *
 * 1) 雾颜色：FogRenderer.setupColor(...) 内部通过 CubicSampler 采样天空/生物群系颜色，
 *    最后写入 RenderSystem.shaderFogColor[R,G,B,A] 这个私有静态字段（float[4]）。
 *    SRE 在 BackgroundRendererMixin 中用 @WrapOperation 替换内部的 gaussianSampleVec3
 *    采样结果来改颜色，但 setupColor 方法本身是一定会执行完毕的。
 *    因此此处 Mixin FogRenderer.setupColor 的 RETURN 点，在 SRE 改完颜色之后再覆盖。
 *
 * 2) 雾距离/形状：由 FogRenderer.setupFog(...) 写入 shaderFogStart/End/Shape。
 *    SRE 在 WorldRendererMixin 中用 @WrapOperation 拦截 LevelRenderer.renderLevel
 *    内对 setupFog 的调用，对局中直接设置参数并 return，跳过 setupFog 方法本体。
 *    因此不能 Mixin FogRenderer.setupFog（不会触发），必须在 renderLevel 中对
 *    setupFog 调用点做 INVOKE + AFTER，在 SRE 写完参数后再覆盖。
 */
@Mixin(LevelRenderer.class)
public class AdachiFogMixin {

    // RenderSystem.shaderFogColor 私有静态字段缓存（反射只找一次，避免每帧开销）
    @Unique
    private static Field DIM201$shaderFogColorField;
    @Unique
    private static boolean DIM201$fogColorFieldResolved = false;

    // 红雾 RGBA：高红、极低绿蓝 → 血色氛围
    @Unique
    private static final float[] DIM201$RED_FOG_COLOR = new float[]{0.62f, 0.04f, 0.04f, 1.0f};

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
            shift = At.Shift.AFTER))
    private void dim201$afterSetupFog(CallbackInfo ci) {
        if (!isAdachiRedFogActive()) return;
        RenderSystem.setShaderFogStart(0.0f);
        RenderSystem.setShaderFogEnd(72.0f);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        // 同时覆盖颜色（防止 setupColor 比 setupFog 先跑导致颜色被后续覆盖，这里兜底再设一次）
        dim201$applyRedFogColor();
    }

    /**
     * 在 FogRenderer.setupColor 的 RETURN 注入。
     * 由于 Mixin 已经声明为 LevelRenderer，无法再声明 FogRenderer，
     * 但我们需要的是 setupColor 返回后覆盖颜色。
     * 【实现方式】：在 renderLevel 中对 setupColor 的调用点做 INVOKE + AFTER。
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            shift = At.Shift.AFTER))
    private void dim201$afterSetupColor(CallbackInfo ci) {
        if (!isAdachiRedFogActive()) return;
        dim201$applyRedFogColor();
    }

    @Unique
    private static boolean isAdachiRedFogActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.getNullable(mc.level);
        if (gameComponent == null) return false;
        // 仅对局运行期间生效：大厅/非对局场景渲染红雾属于串场
        if (!gameComponent.isRunning()) return false;

        for (Player player : mc.level.players()) {
            if (!gameComponent.isRole(player, AdachiRole.ADACHI)) continue;
            AdachiPlayerComponent adachiComp = ModComponents.ADACHI.getNullable(player);
            if (adachiComp != null && adachiComp.isRedFogActive()) return true;
        }
        return false;
    }

    @Unique
    private static void dim201$applyRedFogColor() {
        try {
            if (!DIM201$fogColorFieldResolved) {
                DIM201$shaderFogColorField = RenderSystem.class.getDeclaredField("shaderFogColor");
                DIM201$shaderFogColorField.setAccessible(true);
                DIM201$fogColorFieldResolved = true;
            }
            if (DIM201$shaderFogColorField != null) {
                // shaderFogColor 是 static final float[]，final 只是引用 final，数组元素可变
                float[] color = (float[]) DIM201$shaderFogColorField.get(null);
                if (color != null && color.length >= 4) {
                    System.arraycopy(DIM201$RED_FOG_COLOR, 0, color, 0, 4);
                }
            }
        } catch (Throwable ignored) {
            // 反射失败不崩溃，最多雾不变红
        }
    }
}