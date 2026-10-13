package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.entity.LoneWolfDroneEntity;
import com.dim201.dim201addon.packet.LoneWolfDroneInputC2SPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 独狼无人机客户端控制器（参考赤蛮奇飞头远程驾驶）。
 *
 * <p>职责：
 * <ul>
 *   <li>操控无人机时把相机绑定到无人机实体，切回本体时恢复</li>
 *   <li>每 tick 采集按键位掩码 + 左键发射信号，发送 C2S 包</li>
 * </ul>
 */
public final class LoneWolfDroneController {

    private LoneWolfDroneController() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LoneWolfDroneController::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(client.player);
        if (comp == null) return;

        boolean controlling = comp.droneActive && comp.controllingDrone;
        int droneId = comp.droneEntityId;
        Entity drone = controlling && droneId >= 0 ? client.level.getEntity(droneId) : null;

        if (drone instanceof LoneWolfDroneEntity droneEntity) {
            // 绑定相机到无人机
            if (client.getCameraEntity() != droneEntity) {
                client.setCameraEntity(droneEntity);
            }

            float yaw = client.player.getYRot();
            float pitch = client.player.getXRot();

            // 同步朝向到无人机
            droneEntity.setYRot(yaw);
            droneEntity.yRotO = yaw;
            droneEntity.setXRot(pitch);
            droneEntity.xRotO = pitch;
            droneEntity.setYBodyRot(yaw);
            droneEntity.setYHeadRot(yaw);

            // 采集移动按键（探测高亮由服务端自动完成，无需左键）
            int bits = 0;
            if (client.options.keyUp.isDown()) bits |= LoneWolfDroneInputC2SPacket.BIT_FORWARD;
            if (client.options.keyDown.isDown()) bits |= LoneWolfDroneInputC2SPacket.BIT_BACK;
            if (client.options.keyLeft.isDown()) bits |= LoneWolfDroneInputC2SPacket.BIT_LEFT;
            if (client.options.keyRight.isDown()) bits |= LoneWolfDroneInputC2SPacket.BIT_RIGHT;
            if (client.options.keyJump.isDown()) bits |= LoneWolfDroneInputC2SPacket.BIT_UP;
            if (client.options.keyShift.isDown()) bits |= LoneWolfDroneInputC2SPacket.BIT_DOWN;

            // 客户端预测：与服务端 aiStep 相同的手动步进逻辑，
            // 遇门穿过、遇墙停止。同步包（10Hz）做最终校正。
            net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
            net.minecraft.world.phys.Vec3 right = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, yaw + 90.0F);
            net.minecraft.world.phys.Vec3 vel = net.minecraft.world.phys.Vec3.ZERO;
            if ((bits & LoneWolfDroneInputC2SPacket.BIT_FORWARD) != 0) vel = vel.add(look);
            if ((bits & LoneWolfDroneInputC2SPacket.BIT_BACK) != 0) vel = vel.subtract(look);
            if ((bits & LoneWolfDroneInputC2SPacket.BIT_LEFT) != 0) vel = vel.subtract(right);
            if ((bits & LoneWolfDroneInputC2SPacket.BIT_RIGHT) != 0) vel = vel.add(right);
            if ((bits & LoneWolfDroneInputC2SPacket.BIT_UP) != 0) vel = vel.add(0, 1, 0);
            if ((bits & LoneWolfDroneInputC2SPacket.BIT_DOWN) != 0) vel = vel.add(0, -1, 0);
            if (vel.lengthSqr() > 1.0E-6) {
                vel = vel.normalize().scale(0.44);
            }

            // 手动步进（与服务端 aiStep 一致）
            if (vel.lengthSqr() > 1.0E-6) {
                double totalDist = vel.length();
                net.minecraft.world.phys.Vec3 dir = vel.normalize();
                double stepSize = 0.1;
                int steps = (int) Math.ceil(totalDist / stepSize);
                for (int i = 0; i < steps; i++) {
                    double step = Math.min(stepSize, totalDist - i * stepSize);
                    net.minecraft.world.phys.Vec3 next = droneEntity.position().add(dir.scale(step));
                    var entityBox = droneEntity.getBoundingBox().move(next.subtract(droneEntity.position()));
                    var shapeBox = net.minecraft.world.phys.shapes.Shapes.create(entityBox);
                    var minPos = net.minecraft.core.BlockPos.containing(entityBox.minX, entityBox.minY, entityBox.minZ);
                    var maxPos = net.minecraft.core.BlockPos.containing(entityBox.maxX, entityBox.maxY, entityBox.maxZ);
                    boolean blocked = false;
                    for (var pos : net.minecraft.core.BlockPos.betweenClosed(minPos, maxPos)) {
                        var state = droneEntity.level().getBlockState(pos);
                        if (state.isAir()) continue;
                        if (com.dim201.dim201addon.util.DoorPenetrationHelper.isDoorBlock(droneEntity.level(), pos, state)) continue;
                        var collideShape = state.getCollisionShape(droneEntity.level(), pos);
                        if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(collideShape, shapeBox,
                                net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) break;
                    droneEntity.setPos(next.x, next.y, next.z);
                }
            }

            ClientPlayNetworking.send(new LoneWolfDroneInputC2SPacket(
                    bits, yaw, pitch, false,
                    droneEntity.getX(), droneEntity.getY(), droneEntity.getZ()));
        } else {
            // 非操控状态：相机绑回本体
            if (client.getCameraEntity() != client.player) {
                client.setCameraEntity(client.player);
            }
        }
    }
}
