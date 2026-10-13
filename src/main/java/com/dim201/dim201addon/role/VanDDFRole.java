package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VanDDFRole {

    public static final ResourceLocation VAN_DDF_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "van_ddf"
    );
    
    public static SRERole VAN_DDF;

    private static final ResourceLocation DEFENSE_VIAL = ResourceLocation.parse("trainmurdermystery:defense_vial");

    private static final Map<UUID, Integer> transformTicks = new HashMap<>();
    private static final Set<UUID> warnedPlayers = new HashSet<>();

    public static void register() {
        VAN_DDF = TMMRoles.registerRole(new VanDDFRoleImpl());
    }

    public static void registerTickHandler() {
        org.agmas.harpymodloader.events.ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerId = serverPlayer.getUUID();
                if (transformTicks.containsKey(playerId)) {
                    revertToVan(serverPlayer);
                    transformTicks.remove(playerId);
                    warnedPlayers.remove(playerId);
                }
            }
        });
    }

    /**
     * 在玩家周围生成爱心粒子
     */
    private static void spawnHeartParticles(ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        
        ServerLevel serverLevel = (ServerLevel) player.level();
        
        for (int i = 0; i < 4; i++) {
            double offsetX = (serverLevel.getRandom().nextDouble() - 0.5) * 1.5;
            double offsetY = serverLevel.getRandom().nextDouble() * 1.5;
            double offsetZ = (serverLevel.getRandom().nextDouble() - 0.5) * 1.5;
            
            serverLevel.sendParticles(
                ParticleTypes.HEART,
                x + offsetX,
                y + offsetY,
                z + offsetZ,
                1,
                0.1,
                0.1,
                0.1,
                0.02
            );
        }
    }

    private static void sendTenSecondWarning(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("§e§l10秒")
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§f变身即将结束！")
        ));
        
        player.level().playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.NOTE_BLOCK_PLING,
            SoundSource.PLAYERS,
            1.0f,
            1.5f
        );
    }

    public static void transformFromVan(ServerPlayer player) {
        transformTicks.remove(player.getUUID());
        warnedPlayers.remove(player.getUUID());
        
        // 先触发 Biollante 变身
        triggerBiollanteTransform(player);
        
        player.server.getCommands().performPrefixedCommand(
            player.server.createCommandSourceStack().withPermission(4),
            "changeRole " + player.getName().getString() + " dim201addon:van_ddf"
        );
        
        player.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SPEED,
            60 * 20,
            2,
            false,
            false,
            true
        ));
        
        Item vialItem = BuiltInRegistries.ITEM.get(DEFENSE_VIAL);
        if (vialItem != null) {
            player.getInventory().add(new ItemStack(vialItem));
        }
        
        VanSoundHandler.playTransformSound(player);
        
        transformTicks.put(player.getUUID(), 60 * 20);
        
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("§d§l深邃黑暗的幻想")
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§fTHE DEEP♂DARK FANTASY，持续60秒！")
        ));
    }

    public static void revertToVan(ServerPlayer player) {
        transformTicks.remove(player.getUUID());
        warnedPlayers.remove(player.getUUID());
        
        // 同时清理 Biollante
        triggerBiollanteRevert(player);
        
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return;
        var currentRole = gameWorld.getRole(player);
        if (currentRole == null || !currentRole.equals(VAN_DDF)) {
            return;
        }
        
        player.server.getCommands().performPrefixedCommand(
            player.server.createCommandSourceStack().withPermission(4),
            "changeRole " + player.getName().getString() + " dim201addon:van"
        );
        
        VanSoundHandler.stopTransformSound(player);
        
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("§a§l变身结束")
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§fDDF的力量散去了...")
        ));
    }

    /**
     * 强制结束变身（被击杀时调用）
     */
    public static void forceRevert(ServerPlayer player) {
        // 清理变身计时
        transformTicks.remove(player.getUUID());
        warnedPlayers.remove(player.getUUID());
        
        // 停止音乐
        VanSoundHandler.stopTransformSound(player);
        
        // 同时清理 Biollante
        triggerBiollanteRevert(player);
        
        // 变回 Van（不发送标题提示，因为玩家已死亡）
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            var currentRole = gameWorld.getRole(player);
            if (currentRole != null && currentRole.equals(VAN_DDF)) {
                player.server.getCommands().performPrefixedCommand(
                    player.server.createCommandSourceStack().withPermission(4),
                    "changeRole " + player.getName().getString() + " dim201addon:van"
                );
            }
        }
    }

    // 触发 Biollante 变身
    private static void triggerBiollanteTransform(ServerPlayer vanPlayer) {
        for (ServerPlayer targetPlayer : vanPlayer.server.getPlayerList().getPlayers()) {
            var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(targetPlayer.level());
            if (gameWorld == null) continue;
            
            var role = gameWorld.getRole(targetPlayer);
            if (role != null && role.equals(BiollanteRole.BIOLLANTE)
                && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                BiollanteRole.transformFromVan(vanPlayer, targetPlayer);
                break;
            }
        }
    }

    // 触发 Biollante 结束
    private static void triggerBiollanteRevert(ServerPlayer vanPlayer) {
        for (ServerPlayer targetPlayer : vanPlayer.server.getPlayerList().getPlayers()) {
            var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(targetPlayer.level());
            if (gameWorld == null) continue;
            
            var role = gameWorld.getRole(targetPlayer);
            if (role != null && role.equals(BiollanteRole.BIOLLANTE)
                && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                BiollanteRole.revertBiollante(targetPlayer);
                break;
            }
        }
    }

    private static class VanDDFRoleImpl extends NormalRole {
        
        public VanDDFRoleImpl() {
            super(
                VAN_DDF_ID,
                0xFFFF0000,
                false,
                true,
                MoodType.FAKE,
                -1,
                true
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setDefaultMax(0)
                .setDefaultEnableChance(0)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return List.of();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            return null;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            SREAbilityPlayerComponent comp = SREAbilityPlayerComponent.KEY.get(player);
            if (comp.cooldown > 0) {
                return true;
            }
            return false;
        }

        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, VAN_DDF)) {
                return;
            }

            Integer remainingTicks = transformTicks.get(player.getUUID());
            if (remainingTicks == null || remainingTicks <= 0) {
                return;
            }

            int nextTicks = remainingTicks - 1;
            if (player.isAlive() && nextTicks % 5 == 0) {
                spawnHeartParticles(player);
            }

            if (nextTicks == 10 * 20 && !warnedPlayers.contains(player.getUUID())) {
                if (player.isAlive()) {
                    sendTenSecondWarning(player);
                    warnedPlayers.add(player.getUUID());
                }
            }

            if (nextTicks <= 0) {
                if (player.isAlive()) {
                    revertToVan(player);
                }
                return;
            }

            transformTicks.put(player.getUUID(), nextTicks);
        }
    }
}