package com.dim201.dim201addon;

import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.minecraft.server.level.ServerPlayer;

public class VanKillHandler {

    public static void register() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer serverVictim)) return;
            if (!(killer instanceof ServerPlayer serverKiller)) return;
            
            // 检查杀手是否是 VanDDF
            var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(killer.level());
            if (gameWorld == null) return;
            var killerRole = gameWorld.getRole(killer);
            
            if (killerRole != null && killerRole.equals(VanDDFRole.VAN_DDF)) {
                // 将受害者转化为 boy（会复活为冒险模式）
                BoyRole.transformFromKill(serverVictim, serverKiller);
            }
        });
    }
}