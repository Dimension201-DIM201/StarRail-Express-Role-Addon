package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoyRole {

    public static final ResourceLocation BOY_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "boy"
    );
    
    public static SRERole BOY;

    private static final Map<UUID, Integer> boyTimers = new HashMap<>();

    public static void register() {
        BOY = TMMRoles.registerRole(new BoyRoleImpl());
        // 不需要额外设置，已经在构造函数中设置了
    }

    public static void registerTickHandler() {
        org.agmas.harpymodloader.events.ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerId = serverPlayer.getUUID();
                if (boyTimers.containsKey(playerId)) {
                    serverPlayer.server.getCommands().performPrefixedCommand(
                        serverPlayer.server.createCommandSourceStack().withPermission(4),
                        "gamemode spectator " + serverPlayer.getName().getString()
                    );
                    boyTimers.remove(playerId);
                }
            }
        });
    }

    public static void transformFromKill(ServerPlayer victim, ServerPlayer killer) {
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(victim.level());
        if (gameWorld == null) return;
        
        victim.server.getCommands().performPrefixedCommand(
            victim.server.createCommandSourceStack().withPermission(4),
            "changeRole " + victim.getName().getString() + " dim201addon:boy"
        );
        
        if (!victim.isAlive()) {
            victim.setHealth(20.0f);
        }
        
        victim.server.getCommands().performPrefixedCommand(
            victim.server.createCommandSourceStack().withPermission(4),
            "gamemode adventure " + victim.getName().getString()
        );
        
        victim.removeAllEffects();
        
        victim.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SPEED,
            30 * 20,
            0,
            false,
            false,
            true
        ));
        
        boyTimers.put(victim.getUUID(), 30 * 20);
        
        victim.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
        victim.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
            net.minecraft.network.chat.Component.literal("§c§l你变成了杀手")
        ));
        victim.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
            net.minecraft.network.chat.Component.literal("§f变成了平家boy，你还有30s的时间！")
        ));
        
        victim.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e你已被转化为 boy！在30秒内寻找生路..."));
    }

    private static class BoyRoleImpl extends NormalRole {
        
        public BoyRoleImpl() {
            super(
                BOY_ID,
                0xFF808080,
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
            return new ArrayList<>();
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
            if (gameWorld == null || !gameWorld.isRole(player, BOY)) {
                return;
            }

            Integer remainingTicks = boyTimers.get(player.getUUID());
            if (remainingTicks == null || remainingTicks <= 0) {
                return;
            }

            int nextTicks = remainingTicks - 1;
            if (nextTicks <= 0) {
                boyTimers.remove(player.getUUID());
                player.server.getCommands().performPrefixedCommand(
                    player.server.createCommandSourceStack().withPermission(4),
                    "gamemode spectator " + player.getName().getString()
                );

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c§l你的时间到了..."));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 50, 20));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    net.minecraft.network.chat.Component.literal("§c§l死亡")
                ));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    net.minecraft.network.chat.Component.literal("§f你的时间到了...")
                ));
            } else {
                boyTimers.put(player.getUUID(), nextTicks);
            }
        }
    }
}