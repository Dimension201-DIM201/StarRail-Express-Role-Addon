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

import com.dim201.dim201addon.Dim201Addon;

public class BiollanteRole {

    public static final ResourceLocation BIOLLANTE_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "biollante"
    );
    
    public static SRERole BIOLLANTE;

    private static final ResourceLocation REVOLVER = ResourceLocation.parse("trainmurdermystery:revolver");
    private static final ResourceLocation BAT = ResourceLocation.parse("trainmurdermystery:bat");
    private static final ResourceLocation DEFENSE_VIAL = ResourceLocation.parse("trainmurdermystery:defense_vial");

    // 追踪 Biollante 变身状态
    private static final Map<UUID, Integer> biollanteTransformTicks = new HashMap<>();
    private static final Set<UUID> biollanteWarnedPlayers = new HashSet<>();

    public static void register() {
        BIOLLANTE = TMMRoles.registerRole(new BiollanteRoleImpl());
    }

    public static void registerTickHandler() {
        // 游戏重置时清理
        org.agmas.harpymodloader.events.ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerId = serverPlayer.getUUID();
                if (biollanteTransformTicks.containsKey(playerId)) {
                    revertBiollante(serverPlayer);
                    biollanteTransformTicks.remove(playerId);
                    biollanteWarnedPlayers.remove(playerId);
                }
            }
        });
    }

    /**
     * 当 Van 购买变身时，Biollante 也变身（仅对存活+生存模式的魔男生效）
     */
    public static void transformFromVan(ServerPlayer vanPlayer, ServerPlayer biollantePlayer) {
        // 魔男已死亡（观察者/创造）→ 不触发变身
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(biollantePlayer)) return;
        
        biollanteTransformTicks.remove(biollantePlayer.getUUID());
        biollanteWarnedPlayers.remove(biollantePlayer.getUUID());
        
        // 给 Biollante 添加速度效果
        biollantePlayer.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SPEED,
            60 * 20,
            2,
            false,
            false,
            true
        ));
        
        // 给防御药水
        Item vialItem = BuiltInRegistries.ITEM.get(DEFENSE_VIAL);
        if (vialItem != null) {
            biollantePlayer.getInventory().add(new ItemStack(vialItem));
        }
        
        // 开始计时
        biollanteTransformTicks.put(biollantePlayer.getUUID(), 60 * 20);
        
        // 发送标题
        biollantePlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        biollantePlayer.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("§c§l解放，猎天使魔男形态！")
        ));
        biollantePlayer.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§fVan开启了DDF，找到并打败他！")
        ));
    }

    /**
     * Biollante 变身结束
     */
    public static void revertBiollante(ServerPlayer player) {
        UUID playerId = player.getUUID();
        biollanteTransformTicks.remove(playerId);
        biollanteWarnedPlayers.remove(playerId);
        
        // 发送标题
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("§a§l变身结束")
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§f猎天使魔男的力量散去了...")
        ));
    }

    /**
     * 生成爱心粒子
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
            Component.literal("§f力量即将消散！")
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

    private static class BiollanteRoleImpl extends NormalRole {
        
        public BiollanteRoleImpl() {
            super(
                BIOLLANTE_ID,
                0xFF228B22,  // 森林绿
                true,        // isInnocent = true（警长阵营）
                false,       // canUseKiller = false（不是杀手）
                MoodType.REAL,  // 真实心情
                400,          // maxSprintTime（默认）
                false        // canSeeTime = false（看不到剩余时间）
            );
            
            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setVigilanteTeam(true)
                .setDefaultMax(0)
                .setDefaultEnableChance(0)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            Item revolverItem = BuiltInRegistries.ITEM.get(REVOLVER);
            if (revolverItem != null) {
                items.add(new ItemStack(revolverItem));
            }
            return items;
        }

@Override
public List<ShopEntry> getShopEntries() {
    List<ShopEntry> entries = new ArrayList<>();
    
    // 添加球棒商店条目，价格 200
    Item batItem = BuiltInRegistries.ITEM.get(BAT);
    if (batItem != null) {
        entries.add(new ShopEntry(
            new ItemStack(batItem),           // 物品
            200,                                // 价格
            dev.doctor4t.wathe.util.ShopEntry.Type.WEAPON  // 类型
        ));
    }
    
    return entries;
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
            if (gameWorld == null || !gameWorld.isRole(player, BIOLLANTE)) {
                return;
            }

            // 玩家已死亡（观察者/创造模式）→ 直接清理变身计时，不再生成特效
            boolean alive = io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player);
            if (!alive) {
                UUID id = player.getUUID();
                if (biollanteTransformTicks.containsKey(id)) {
                    biollanteTransformTicks.remove(id);
                    biollanteWarnedPlayers.remove(id);
                }
                return;
            }

            Integer remainingTicks = biollanteTransformTicks.get(player.getUUID());
            if (remainingTicks == null || remainingTicks <= 0) {
                return;
            }

            int nextTicks = remainingTicks - 1;
            if (nextTicks % 5 == 0) {
                spawnHeartParticles(player);
            }

            if (nextTicks == 10 * 20 && !biollanteWarnedPlayers.contains(player.getUUID())) {
                sendTenSecondWarning(player);
                biollanteWarnedPlayers.add(player.getUUID());
            }

            if (nextTicks <= 0) {
                revertBiollante(player);
                return;
            }

            biollanteTransformTicks.put(player.getUUID(), nextTicks);
        }
    }
}