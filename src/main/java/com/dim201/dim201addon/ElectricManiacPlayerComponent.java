package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 电击狂人玩家组件
 *
 * <p>跟踪玩家是否带有静电效果。
 * 静电状态由服务端 {@link com.dim201.dim201addon.role.electric.ElectricManiacManager} 管理，
 * 本组件仅负责状态存储与同步。
 */
public class ElectricManiacPlayerComponent implements RoleComponent {

    public static final ComponentKey<ElectricManiacPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "electric_maniac"),
            ElectricManiacPlayerComponent.class);

    private final Player player;

    /** 是否带有静电效果 */
    public boolean electrified = false;
    /** 静电到期时间（tick），-1 表示无 */
    public long electrifiedUntilTick = -1L;
    /** 当前技能：0=静电充能，1=静电爆炸 */
    public int currentSkill = 0;
    /** 技能1 冷却到期 tick（玩家 tickCount），-1 表示就绪 */
    public long chargeReadyAtTick = -1L;
    /** 技能2 冷却到期 tick（玩家 tickCount），-1 表示就绪 */
    public long blastReadyAtTick = -1L;
    /** 技能2 蓄力（定身）到期 tick，-1 表示未蓄力 */
    public long blastPendingUntilTick = -1L;

    public ElectricManiacPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        electrified = false;
        electrifiedUntilTick = -1L;
        currentSkill = 0;
        chargeReadyAtTick = -1L;
        blastReadyAtTick = -1L;
        blastPendingUntilTick = -1L;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        // 同步给所有玩家：其他玩家需要知道谁带有静电以便渲染
        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("Electrified", electrified);
        tag.putLong("ElectrifiedUntil", electrifiedUntilTick);
        tag.putInt("CurrentSkill", currentSkill);
        tag.putLong("ChargeReadyAt", chargeReadyAtTick);
        tag.putLong("BlastReadyAt", blastReadyAtTick);
        tag.putLong("BlastPendingUntil", blastPendingUntilTick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        electrified = tag.getBoolean("Electrified");
        electrifiedUntilTick = tag.getLong("ElectrifiedUntil");
        currentSkill = tag.getInt("CurrentSkill");
        chargeReadyAtTick = tag.getLong("ChargeReadyAt");
        blastReadyAtTick = tag.getLong("BlastReadyAt");
        blastPendingUntilTick = tag.getLong("BlastPendingUntil");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
