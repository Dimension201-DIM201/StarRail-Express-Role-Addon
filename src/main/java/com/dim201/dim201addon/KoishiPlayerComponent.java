package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 古明地恋玩家组件
 */
public class KoishiPlayerComponent implements RoleComponent {

    public static final ComponentKey<KoishiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "koishi"), KoishiPlayerComponent.class);

    private final Player player;

    // ==================== 状态系统 ====================
    public enum State {
        ID("本我"),
        SUPER_EGO("超我");
        
        public final String displayName;
        
        State(String displayName) {
            this.displayName = displayName;
        }
    }
    
    public State currentState = State.ID;
    public int stateTimer = STATE_DURATION;
    public static final int STATE_DURATION = 600; // 30秒 = 600 tick

    // ==================== 技能冷却 ====================
    public int skillCooldown = 0;
    public static final int SKILL_COOLDOWN = 1200; // 60秒 = 1200 tick

    // ==================== 技能执行中 ====================
    public boolean isTeleporting = false;
    public int teleportDelay = 0;

    // 记录上次tick的游戏时间
    private long lastTickGameTime = -1;

    // noellesroles 药水效果的 ResourceLocation
    private static final ResourceLocation ISOLATION_ID = ResourceLocation.parse("noellesroles:player_isolation");
    private static final ResourceLocation INVINCIBLE_ID = ResourceLocation.parse("noellesroles:invincible");

    public KoishiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        currentState = State.ID;
        stateTimer = STATE_DURATION;
        skillCooldown = 0;
        isTeleporting = false;
        teleportDelay = 0;
        lastTickGameTime = -1;
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            removeEffectById(sp, ISOLATION_ID);
            removeEffectById(sp, INVINCIBLE_ID);
        }
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    // ==================== 效果工具方法 ====================

    /**
     * 获取 Holder<MobEffect>（1.21 API）
     */
    private Holder<MobEffect> getEffectHolder(ResourceLocation effectId) {
        var optionalHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId);
        return optionalHolder.orElse(null);
    }

    /**
     * 通过 ResourceLocation 应用效果
     */
    private void applyEffectById(ServerPlayer sp, ResourceLocation effectId, int duration, int amplifier) {
        Holder<MobEffect> holder = getEffectHolder(effectId);
        if (holder != null) {
            sp.addEffect(new MobEffectInstance(
                holder,
                duration,
                amplifier,
                false,
                false,
                false
            ));
        }
    }

    /**
     * 通过 ResourceLocation 移除效果
     */
    private void removeEffectById(ServerPlayer sp, ResourceLocation effectId) {
        Holder<MobEffect> holder = getEffectHolder(effectId);
        if (holder != null) {
            sp.removeEffect(holder);
        }
    }

    /**
     * 检查是否有指定效果
     */
    private boolean hasEffectById(ServerPlayer sp, ResourceLocation effectId) {
        Holder<MobEffect> holder = getEffectHolder(effectId);
        if (holder != null) {
            return sp.hasEffect(holder);
        }
        return false;
    }

    // ==================== 状态切换 ====================

    private void switchToSuperEgo(ServerPlayer sp) {
        currentState = State.SUPER_EGO;
        stateTimer = STATE_DURATION;
        
        // 清除本我效果
        removeEffectById(sp, ISOLATION_ID);
        removeEffectById(sp, INVINCIBLE_ID);
        
        sendActionBar(sp, "§d§l超我状态 §7| §f30秒");
        sync();
    }

    private void switchToId(ServerPlayer sp) {
        currentState = State.ID;
        stateTimer = STATE_DURATION;
        
        // 应用本我效果
        applyIdEffects(sp);
        
        sendActionBar(sp, "§a§l本我状态 §7| §f30秒");
        sync();
    }

    /**
     * 应用本我状态药水效果
     */
    private void applyIdEffects(ServerPlayer sp) {
        // player_isolation 效果
        if (!hasEffectById(sp, ISOLATION_ID)) {
            applyEffectById(sp, ISOLATION_ID, 999999, 0);
        }
        
        // invincible 效果
        if (!hasEffectById(sp, INVINCIBLE_ID)) {
            applyEffectById(sp, INVINCIBLE_ID, 999999, 0);
        }
    }

    // ==================== 技能：梅莉小姐的电话 ====================

    public boolean useSkill(ServerPlayer player) {
        if (skillCooldown > 0) {
            int cd = (int) Math.ceil(skillCooldown / 20.0);
            sendActionBar(player, "§c§l技能冷却中 §f" + cd + "秒");
            return false;
        }

        if (isTeleporting) {
            sendActionBar(player, "§c§l技能正在执行中！");
            return false;
        }

        var server = player.getServer();
        if (server == null) return false;

        List<ServerPlayer> alivePlayers = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == player) continue;
            if (!p.isAlive()) continue;
            if (p.isSpectator() || p.isCreative()) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            alivePlayers.add(p);
        }

        if (alivePlayers.isEmpty()) {
            sendActionBar(player, "§c§l没有可用的传送目标！");
            return false;
        }

        isTeleporting = true;
        teleportDelay = 140;
        skillCooldown = SKILL_COOLDOWN;
        
        playTeleportSound(player);
        sendActionBar(player, "§d§l梅莉小姐的电话 §7| §f正在拨号...");
        sync();
        return true;
    }

    private void playTeleportSound(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            target.playNotifySound(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "koishi_teleport")
                ),
                SoundSource.PLAYERS,
                0.3f,
                1.0f
            );
        }
    }

    private void executeTeleport(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;

        List<ServerPlayer> alivePlayers = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == player) continue;
            if (!p.isAlive()) continue;
            if (p.isSpectator() || p.isCreative()) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            alivePlayers.add(p);
        }

        if (alivePlayers.isEmpty()) {
            sendActionBar(player, "§c§l传送失败：没有可用的目标");
            isTeleporting = false;
            sync();
            return;
        }

        Collections.shuffle(alivePlayers);

        for (ServerPlayer target : alivePlayers) {
            Vec3 targetPos = target.position();
            Vec3 targetLook = target.getLookAngle().normalize();
            Vec3 behindPos = targetPos.subtract(targetLook.scale(1.0));
            
            if (isValidStandPosition(player.serverLevel(), behindPos)) {
                player.teleportTo(behindPos.x, behindPos.y, behindPos.z);
                player.setYRot(target.getYRot() + 180);
                player.setXRot(target.getXRot());
                
                sendActionBar(player, "§d§l梅莉小姐的电话 §7| §f已传送到 " + target.getName().getString() + " 身后");
                
                player.serverLevel().playSound(
                    null,
                    behindPos.x, behindPos.y, behindPos.z,
                    net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
                );
                
                isTeleporting = false;
                sync();
                return;
            }
        }

        sendActionBar(player, "§c§l传送失败：没有安全的位置");
        isTeleporting = false;
        sync();
    }

    private boolean isValidStandPosition(ServerLevel level, Vec3 pos) {
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        int z = (int) Math.floor(pos.z);
        
        var footBlock = level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z));
        if (footBlock.isAir() || !footBlock.isSolid()) {
            return false;
        }
        
        for (int i = 0; i < 2; i++) {
            var block = level.getBlockState(new net.minecraft.core.BlockPos(x, y + i, z));
            if (!block.isAir() && block.isSolid()) {
                return false;
            }
        }
        
        return true;
    }

    // ==================== Tick 处理 ====================

    public void tick(ServerPlayer sp) {
        long currentTime = sp.serverLevel().getGameTime();
        if (currentTime == lastTickGameTime) {
            return;
        }
        lastTickGameTime = currentTime;

        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;

        // 确保本我状态有效果（每20tick检查一次）
        if (currentState == State.ID && player.tickCount % 20 == 0) {
            applyIdEffects(sp);
        }

        // 状态计时器
        if (stateTimer <= 0) {
            stateTimer = STATE_DURATION;
            if (currentState == State.ID) {
                switchToSuperEgo(sp);
            } else {
                switchToId(sp);
            }
            return;
        }

        stateTimer--;
        
        if (stateTimer <= 0) {
            if (currentState == State.ID) {
                switchToSuperEgo(sp);
            } else {
                switchToId(sp);
            }
        }

        // 技能冷却
        if (skillCooldown > 0) {
            skillCooldown--;
            if (skillCooldown % 20 == 0 || skillCooldown == 0) {
                sync();
            }
        }

        // 传送延迟
        if (isTeleporting && teleportDelay > 0) {
            teleportDelay--;
            if (teleportDelay <= 0) {
                executeTeleport(sp);
            }
        }

        // 每20 tick同步HUD
        if (player.tickCount % 20 == 0) {
            sync();
        }
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("currentState", currentState.ordinal());
        tag.putInt("stateTimer", stateTimer);
        tag.putInt("skillCooldown", skillCooldown);
        tag.putBoolean("isTeleporting", isTeleporting);
        tag.putInt("teleportDelay", teleportDelay);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("currentState")) {
            int stateOrdinal = tag.getInt("currentState");
            State[] states = State.values();
            if (stateOrdinal >= 0 && stateOrdinal < states.length) {
                currentState = states[stateOrdinal];
            }
        }
        
        if (tag.contains("stateTimer")) {
            int readTimer = tag.getInt("stateTimer");
            stateTimer = (readTimer > 0) ? readTimer : STATE_DURATION;
        } else {
            stateTimer = STATE_DURATION;
        }
        
        skillCooldown = tag.contains("skillCooldown") ? tag.getInt("skillCooldown") : 0;
        isTeleporting = tag.contains("isTeleporting") ? tag.getBoolean("isTeleporting") : false;
        teleportDelay = tag.contains("teleportDelay") ? tag.getInt("teleportDelay") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }
}