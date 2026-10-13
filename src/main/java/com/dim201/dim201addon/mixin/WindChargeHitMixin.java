package com.dim201.dim201addon.mixin;

import com.dim201.dim201addon.DeepWoundPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.TeacherZhangAllGameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截风弹爆炸事件（与 SRE WindChargeMixin 注入同一方法，Mojang 映射名 explode）。
 * 仅在全员张老师模式中，爆炸半径内的玩家被施加深度伤口效果。
 * 其他模式不受影响。
 */
@Mixin(WindCharge.class)
public class WindChargeHitMixin {

    @Inject(method = "explode", at = @At("HEAD"))
    private void dim201$onExplode(Vec3 vec3, CallbackInfo ci) {
        WindCharge windCharge = (WindCharge) (Object) this;
        var level = windCharge.level();

        // 只在服务端生效
        if (level.isClientSide) return;

        // 只在全员张老师模式中生效
        var gameMode = SREGameWorldComponent.KEY.get(level).getGameMode();
        if (gameMode == null || !gameMode.identifier.equals(TeacherZhangAllGameMode.ID)) return;

        // 获取风弹拥有者（施加者）
        Entity owner = windCharge.getOwner();
        ServerPlayer applier = owner instanceof ServerPlayer ? (ServerPlayer) owner : null;

        // 扫描爆炸点半径内的存活玩家（半径 3 格，略大于爆炸半径 1.2F）
        double radiusSq = 3.0 * 3.0;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (ServerPlayer target : serverLevel.players()) {
                if (target == applier) continue;  // 不对自己施加
                if (target.distanceToSqr(vec3) > radiusSq) continue;
                if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) continue;

                DeepWoundPlayerComponent dw = ModComponents.DEEP_WOUND.get(target);
                dw.apply(applier);
                dw.sync(target);
            }
        }
    }
}
