package com.dim201.dim201addon.client;

import com.dim201.dim201addon.packet.BrotherSyncS2CPacket;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 兄弟默契客户端状态（由 S2C 包更新）。
 *
 * 供 {@link BrotherInstinctHighlight}（SRE 直觉事件）判断是否给搭档绘制
 * 透视描边，以及 {@link BrotherHud} 显示默契能量。
 */
public final class BrotherClientState {

    private BrotherClientState() {}

    /** 搭档描边颜色：兄弟金 */
    public static final int OUTLINE_COLOR = 0xFFFFD54F;

    private static volatile UUID partnerId;
    private static volatile int points;
    private static volatile boolean extended;

    public static void handle(BrotherSyncS2CPacket packet) {
        partnerId = packet.partnerId();
        points = packet.points();
        extended = packet.extended();
    }

    public static void reset() {
        partnerId = null;
        points = 0;
        extended = false;
    }

    public static int getPoints() {
        return points;
    }

    public static boolean isExtended() {
        return extended;
    }

    /**
     * 是否应对该实体绘制兄弟描边：
     * 本地玩家为东堂/虎杖 + 实体是在线搭档 + 距离 ≤ 感知范围（击掌后 30 格，否则 10 格）。
     * 只做距离判定，不要求视线，可以隔墙透视。
     */
    public static boolean shouldOutline(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return false;
        UUID partner = partnerId;
        if (partner == null || target == self || !(target instanceof Player)) return false;
        if (!target.getUUID().equals(partner) || !target.isAlive()) return false;

        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(mc.level);
        if (gw == null || !gw.isRunning()) return false;
        if (!TodoAoiRole.isTodo(self) && !ItadoriRole.isItadori(self)) return false;

        double range = extended
                ? TodoAoiRole.PERCEPTION_RANGE_EXTENDED
                : TodoAoiRole.PERCEPTION_RANGE;
        return self.distanceToSqr(target) <= range * range;
    }
}
