package com.dim201.dim201addon;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Random;
import java.util.UUID;

/**
 * 目标玩家身上的「深度伤口」组件（所有玩家都可能被军团施加，不限定角色）
 *
 * 时序：
 *   - 施加瞬间：active=true, woundTicksLeft=300(15s), totalCrouchTicks=0
 *   - 潜行时：不扣 woundTicksLeft，每 tick ++totalCrouchTicks，达到 200(10s) 立即解除
 *   - 非潜行：每 tick --woundTicksLeft，归零时 GameUtils.killPlayer（死因：深度伤口流血至死）
 *   - 周围大量红色粒子 DAMAGE_INDICATOR（每 tick 5 颗）
 *   - ActionBar 每 tick 显示倒计时 + 包扎进度
 */
public class DeepWoundPlayerComponent implements ServerTickingComponent, AutoSyncedComponent {

    public static final ResourceLocation DEATH_REASON_DEEP_WOUND =
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "deep_wound_bleed");

    private static final int DEFAULT_DURATION_TICKS = 15 * 20;
    private static final int REMOVE_CROUCH_TICKS = 10 * 20;
    private static final Random RANDOM = new Random();

    private final Player player;

    public boolean active = false;
    public int woundTicksLeft = 0;
    public int totalCrouchTicks = 0;
    /** 施加者 uuid（null 表示施加者离线），用于 SRE 击杀时把死亡归于军团 */
    public String applierUuidStr = null;

    public DeepWoundPlayerComponent(@NotNull Player player) {
        this.player = player;
    }

    public void apply(ServerPlayer applier) {
        active = true;
        woundTicksLeft = DEFAULT_DURATION_TICKS;
        totalCrouchTicks = 0;
        applierUuidStr = (applier != null) ? applier.getUUID().toString() : null;
    }

    public void clear() {
        active = false;
        woundTicksLeft = 0;
        totalCrouchTicks = 0;
        applierUuidStr = null;
    }

    public boolean isActive() {
        return active && woundTicksLeft > 0;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            if (active) {
                clear();
                sync(sp);
            }
            return;
        }
        if (!active) return;

        ServerLevel serverLevel = sp.serverLevel();

        // ======== 潜行暂停 / 累计潜行解除 ========
        boolean crouching = sp.isCrouching() || sp.isShiftKeyDown();
        if (crouching) {
            totalCrouchTicks++;
            if (totalCrouchTicks >= REMOVE_CROUCH_TICKS) {
                clear();
                sendActionBar(sp, "§a✔ 你成功包扎伤口，深度伤口已经解除！");
                sync(sp);
                return;
            }
        } else {
            woundTicksLeft--;
            if (woundTicksLeft <= 0) {
                // 自然时间到 → 击杀
                ServerPlayer killer = findApplier(serverLevel);
                ResourceLocation reason = DEATH_REASON_DEEP_WOUND;
                clear();
                sync(sp);
                // 允许：死因是深度伤口，不被拦截（OnKillPlayerTriggered 里放行）
                GameUtils.killPlayer(sp, true, killer, reason, true);
                return;
            }
        }

        // ======== 红色粒子（每 tick 5 颗）========
        for (int i = 0; i < 5; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 1.1;
            double oy = RANDOM.nextDouble() * 1.8;
            double oz = (RANDOM.nextDouble() - 0.5) * 1.1;
            double vx = (RANDOM.nextDouble() - 0.5) * 0.12;
            double vy = RANDOM.nextDouble() * 0.18;
            double vz = (RANDOM.nextDouble() - 0.5) * 0.12;
            serverLevel.sendParticles(
                    ParticleTypes.DAMAGE_INDICATOR,
                    sp.getX() + ox, sp.getY() + oy, sp.getZ() + oz,
                    1, vx, vy, vz, 0.0
            );
        }

        // ======== ActionBar ========
        int secs = (woundTicksLeft + 19) / 20;
        float crouchPct = Math.min(100.0f, (totalCrouchTicks * 100.0f) / REMOVE_CROUCH_TICKS);
        String bar = String.format(
                "§c⚠ 深度伤口 §f倒计时：§c%d 秒§f | 潜行包扎：§a%.0f%% / 100%%",
                secs, crouchPct
        );
        sendActionBar(sp, bar);

        sync(sp);
    }

    private ServerPlayer findApplier(ServerLevel serverLevel) {
        if (applierUuidStr == null) return null;
        try {
            UUID uuid = UUID.fromString(applierUuidStr);
            return serverLevel.getServer().getPlayerList().getPlayer(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void sendActionBar(ServerPlayer sp, String text) {
        sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    public void sync(net.minecraft.world.entity.player.Player holder) {
        ModComponents.DEEP_WOUND.sync(holder);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        active = tag.getBoolean("DWActive");
        woundTicksLeft = tag.getInt("DWTicks");
        totalCrouchTicks = tag.getInt("DWCrouch");
        if (tag.contains("DWApplier", StringTag.TAG_STRING)) {
            applierUuidStr = tag.getString("DWApplier");
        } else {
            applierUuidStr = null;
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putBoolean("DWActive", active);
        tag.putInt("DWTicks", woundTicksLeft);
        tag.putInt("DWCrouch", totalCrouchTicks);
        if (applierUuidStr != null) {
            tag.putString("DWApplier", applierUuidStr);
        }
    }
}
