package com.dim201.dim201addon;

import java.util.Random;

public class VanSoundHandler {

    private static final Random RANDOM = new Random();
    
    private static final String[] TRANSFORM_SOUND_IDS = {
        "dim201addon:van_transform_1",
        "dim201addon:van_transform_2",
        "dim201addon:van_transform_3",
        "dim201addon:van_transform_4"
    };

    /**
     * 播放变身音频 - 使用 /playsound 命令给每个玩家在其自身位置播放
     */
    public static void playTransformSound(net.minecraft.server.level.ServerPlayer player) {
        String selectedId = TRANSFORM_SOUND_IDS[RANDOM.nextInt(TRANSFORM_SOUND_IDS.length)];
        
        Dim201Addon.LOGGER.info("[VanSoundHandler] Playing transform sound via command: {}", selectedId);
        
        // 给每个玩家单独执行 playsound，使用具体坐标
        for (net.minecraft.server.level.ServerPlayer targetPlayer : player.server.getPlayerList().getPlayers()) {
            double x = targetPlayer.getX();
            double y = targetPlayer.getY();
            double z = targetPlayer.getZ();
            
            // 使用玩家选择器和具体坐标
            String command = String.format(
                "execute as %s at %s run playsound %s master @s %.2f %.2f %.2f 0.4 1",
                targetPlayer.getGameProfile().getName(),
                targetPlayer.getGameProfile().getName(),
                selectedId,
                x, y, z
            );
            
            player.server.getCommands().performPrefixedCommand(
                player.server.createCommandSourceStack().withPermission(4),
                command
            );
        }
    }

    /**
     * 停止音频
     */
    public static void stopTransformSound(net.minecraft.server.level.ServerPlayer player) {
        for (String soundId : TRANSFORM_SOUND_IDS) {
            player.server.getCommands().performPrefixedCommand(
                player.server.createCommandSourceStack().withPermission(4),
                "stopsound @a * " + soundId
            );
        }
    }
}