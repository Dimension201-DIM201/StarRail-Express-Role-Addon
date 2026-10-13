package com.dim201.dim201addon;

import com.dim201.dim201addon.role.DevourerGodRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;

/**
 * 神明吞噬者玩家组件
 *
 * <p>管理神明吞噬者（DevourerGodRole）的状态：
 * <ul>
 *   <li>吞噬技能：3s CD（60 ticks），无限使用，自动击杀身边 1.5 格内所有玩家</li>
 *   <li>初始 1 层护盾，每吞噬 2 人 +1 层护盾</li>
 *   <li>持续速度 III 效果（amplifier=2）</li>
 * </ul>
 *
 * <p>原理：与普通吞噬者（DevourerPlayerComponent）不同，神明吞噬者直接击杀目标（不使用 PelicanManager）。
 * 复用 {@link com.dim201.dim201addon.packet.DevourerEatC2SPacket} 网络包触发吞噬技能。
 */
public class DevourerGodPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DevourerGodPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "devourer_god"),
            DevourerGodPlayerComponent.class);

    /** 吞噬冷却（tick）：3 秒 = 60 ticks */
    public static final int COOLDOWN_TICKS = 60;
    /** 范围吞噬半径（格）：身边 1.5 格内的所有玩家 */
    public static final double DEVOUR_RANGE = 1.5D;
    /** 速度效果持续 tick（每 5 秒刷新一次，保证不中断） */
    public static final int SPEED_DURATION_TICKS = 100;
    /** 速度效果等级：amplifier=2（即速度 III） */
    public static final int SPEED_AMPLIFIER = 2;

    private final Player player;

    /** 吞噬计数 */
    public int devourCount = 0;
    /** 冷却剩余 tick（0 = 可用） */
    public int cooldownTicks = 0;
    /** 天蓝色雾是否激活（同步给所有客户端，由 DevourerGodFogMixin 渲染） */
    public boolean fogActive = false;

    public DevourerGodPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        devourCount = 0;
        cooldownTicks = 0;
        fogActive = false;
        sync();
    }

    @Override
    public void clear() {
        devourCount = 0;
        cooldownTicks = 0;
        fogActive = false;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        // 同步给所有玩家：天蓝色雾状态需对所有客户端可见
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return;
        if (!gameWorld.isRole(player, DevourerGodRole.DEVOURER_GOD_ROLE)) return;
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 冷却递减
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        // 持续给予速度 III 效果（每 5 秒刷新一次，amplifier=2 = 速度 III）
        if (player.tickCount % SPEED_DURATION_TICKS == 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    SPEED_DURATION_TICKS + 20, SPEED_AMPLIFIER,
                    false, false, false));
        }

        // 仅在冷却期间每秒同步一次（避免冷却为 0 时每 tick 同步导致流量浪费）
        if (cooldownTicks > 0 && cooldownTicks % 20 == 0) {
            sync();
        }
    }

    /**
     * 尝试范围吞噬：直接击杀所有有效目标
     *
     * <p>原理：冷却只校验一次；对范围内所有存活且非自己的玩家依次执行击杀，
     * 按累计吞噬总数统一结算护盾（每 2 人 +1 层），冷却/音效/提示只触发一次。
     *
     * @param targets 范围内的候选目标
     * @return 本次实际吞噬人数
     */
    public int tryDevourAll(List<ServerPlayer> targets) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        if (targets == null || targets.isEmpty()) return 0;

        // 冷却检查（3s）
        if (cooldownTicks > 0) {
            long remaining = (cooldownTicks + 19) / 20;
            sp.displayClientMessage(
                    Component.literal("§c技能冷却中，剩余 " + remaining + " 秒"),
                    true);
            return 0;
        }

        // 过滤有效目标：存活且非自己
        List<ServerPlayer> validTargets = targets.stream()
                .filter(t -> t != null
                        && !t.getUUID().equals(player.getUUID())
                        && GameUtils.isPlayerAliveAndSurvival(t))
                .toList();
        if (validTargets.isEmpty()) {
            sp.displayClientMessage(Component.literal("§c身边没有可吞噬的目标"), true);
            return 0;
        }

        int beforeCount = devourCount;

        // 执行范围吞噬：直接击杀（留尸体，killer=神明吞噬者，死因=DEATH_REASON_DEVOUR）
        for (ServerPlayer target : validTargets) {
            GameUtils.killPlayer(target, true, sp, DevourerGodRole.DEATH_REASON_DEVOUR);
            devourCount++;
        }
        int killed = devourCount - beforeCount;

        // 按累计总数计算本次新增护盾层数（每吞噬 2 人 +1 层）
        int addedShields = devourCount / 2 - beforeCount / 2;
        if (addedShields > 0) {
            SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(sp);
            for (int i = 0; i < addedShields; i++) {
                armorComp.addArmor();
            }
            sp.displayClientMessage(
                    Component.literal("§b§l吞噬之力！§r§a 护盾 +" + addedShields
                            + "（当前 " + armorComp.getArmor() + " 层）")
                            .withStyle(ChatFormatting.AQUA),
                    true);
        }

        // 设置冷却
        cooldownTicks = COOLDOWN_TICKS;

        // 音效 + 提示（凋零呼吸声）
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_AMBIENT, SoundSource.MASTER, 0.8F, 0.5F);

        sp.displayClientMessage(
                Component.literal("§5§l已吞噬 " + killed + " 名玩家！")
                        .append("§r§5（累计吞噬 " + devourCount + " 人）")
                        .withStyle(ChatFormatting.DARK_PURPLE),
                true);

        sync();
        return killed;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("DevourCount", devourCount);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putBoolean("FogActive", fogActive);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        devourCount = tag.getInt("DevourCount");
        cooldownTicks = tag.getInt("CooldownTicks");
        fogActive = tag.getBoolean("FogActive");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
