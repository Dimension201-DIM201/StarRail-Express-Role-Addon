package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.role.MadmanRole;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 取消疯子的二级无理智移动减速 Mixin
 *
 * <p>原理：SRE 在 {@code Player.getSpeed()} 的返回值上做心情速度修正——
 * mood 低于抑郁阈值时 {@code speed *= 0.8f}（高于愤怒阈值时 ×1.2）。
 * 疯子常驻 mood = 0.15f，会一直吃这个 0.8 减速。
 *
 * <p>实现方式上的坑：SRE 的修正是另一个 Mixin 的 {@code @ModifyReturnValue} 处理器，
 * 合并后处理器是 Player 类中的独立方法（名字随 SRE 构建版本可能变化，
 * 按处理器方法名注入曾导致 Critical injection failure），其内部调用点也不在 getSpeed 本体里，
 * 无法稳定地按方法名/调用点拦截。
 *
 * <p>因此这里采用与 SRE 内部结构完全解耦的方案：本 Mixin 后于 SRE 应用
 * （priority=900，数字越小越晚应用），拿到的 original 已包含 SRE 的全部修正；
 * 当持有者是疯子且其 mood 确实低于抑郁阈值时，SRE 必然乘过 0.8，
 * 直接除以 0.8f 精确抵消这一项。药水减速（含 127 级定身）、速度药水、
 * 绊线 ×0.1、上下颠倒等其他修正原样保留；镇静期间 mood 锁定为 0.5（不低于阈值），
 * 不会被错误加速。幻觉等其他低 san 表现也完全不受影响。
 */
@Mixin(value = Player.class, priority = 900)
public abstract class MadmanLowSanSlowMixin {

    /** SRE 低 san 减速系数，须与 SREPlayerMoodComponent 的心情速度修正保持一致 */
    private static final float SRE_LOW_MOOD_SPEED_FACTOR = 0.8f;

    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    private float dim201$madmanIgnoreLowSanSlow(float original) {
        Player self = (Player) (Object) this;
        if (original <= 0.0f) return original;

        // 双端安全：不依赖 gw.isRunning()（客户端同步时序下可能为 false），按角色标识判定
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(self.level());
        if (gw == null || MadmanRole.MADMAN == null) return original;
        SRERole role = gw.getRole(self);
        if (role == null || !role.identifier().toString()
                .equals(MadmanRole.MADMAN.identifier().toString())) {
            return original;
        }

        // 仅当低 san 减速确实生效时抵消；镇静（mood=0.5）等状态下不处理
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(self);
        if (mood == null || !mood.isLowerThanDepressed()) return original;

        return original / SRE_LOW_MOOD_SPEED_FACTOR;
    }
}
