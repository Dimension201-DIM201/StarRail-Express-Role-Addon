package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import com.dim201.dim201addon.entity.SeikibankiHeadEntity;
import com.dim201.dim201addon.packet.SeikibankiFlyInputC2SPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 赤蛮奇飞头客户端控制器（参考操纵师远程驾驶模式）。
 *
 * <p>职责：
 * <ul>
 *   <li>技能激活时把相机绑定到头颅实体（原版 {@code setCameraEntity}），结束时绑回玩家</li>
 *   <li>每 tick 采集按键位掩码发送 {@link SeikibankiFlyInputC2SPacket}</li>
 * </ul>
 * 本体移动输入由 {@code SeikibankiKeyboardInputMixin} 清零。
 */
public final class SeikibankiFlyController {

    private SeikibankiFlyController() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SeikibankiFlyController::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.getNullable(client.player);
        if (comp == null) {
            return;
        }

        boolean active = comp.skillActive;
        int headId = comp.headEntityId;
        Entity head = active && headId >= 0 ? client.level.getEntity(headId) : null;
        if (head instanceof SeikibankiHeadEntity flyingHead) {
            // 绑定相机到头颅
            if (client.getCameraEntity() != flyingHead) {
                client.setCameraEntity(flyingHead);
            }

            // 鼠标通过 player.changeLookDirection 修改玩家 yaw，相机旋转跟随玩家 yaw
            float yaw = client.player.getYRot();
            float pitch = client.player.getXRot();

            // 同步朝向到头颅实体，使模型朝向与视角一致
            flyingHead.setYRot(yaw);
            flyingHead.yRotO = yaw;
            flyingHead.setXRot(pitch);
            flyingHead.xRotO = pitch;
            flyingHead.setYBodyRot(yaw);
            flyingHead.setYHeadRot(yaw);

            // 采集按键
            int bits = 0;
            if (client.options.keyUp.isDown()) bits |= SeikibankiFlyInputC2SPacket.BIT_FORWARD;
            if (client.options.keyDown.isDown()) bits |= SeikibankiFlyInputC2SPacket.BIT_BACK;
            if (client.options.keyLeft.isDown()) bits |= SeikibankiFlyInputC2SPacket.BIT_LEFT;
            if (client.options.keyRight.isDown()) bits |= SeikibankiFlyInputC2SPacket.BIT_RIGHT;
            if (client.options.keyJump.isDown()) bits |= SeikibankiFlyInputC2SPacket.BIT_UP;
            if (client.options.keyShift.isDown()) bits |= SeikibankiFlyInputC2SPacket.BIT_DOWN;

            ClientPlayNetworking.send(new SeikibankiFlyInputC2SPacket(bits, yaw, pitch));
        } else if (client.getCameraEntity() instanceof SeikibankiHeadEntity) {
            // 技能结束/头颅失效：只有当前相机是头颅时才绑回本体，
            // 避免覆盖独狼无人机等其他角色的相机绑定
            client.setCameraEntity(client.player);
        }
    }
}
