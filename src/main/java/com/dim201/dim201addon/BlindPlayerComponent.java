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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 盲人玩家组件
 *
 * 被动能力由 {@link com.dim201.dim201addon.role.BlindRole} 的
 * {@code setServerGameTickEvent} 处理（失明 + 透视范围管理）。
 *
 * <p>本组件维护「视野恢复期」状态：盲杖使用后在到期 gameTime 前跳过失明施加，
 * 使盲人临时恢复视野。角色 tick 读此状态，盲杖物品写此状态（读写分离）。
 */
public class BlindPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<BlindPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "blind"),
            BlindPlayerComponent.class);

    private static final String NBT_RESTORE_UNTIL = "VisionRestoreUntilTick";

    private final Player player;

    /** 视野恢复截止 gameTime（-1 = 非恢复期） */
    private long visionRestoreUntilTick = -1L;

    public BlindPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 申请视野恢复：到期前失明施加被跳过 */
    public void requestVisionRestore(long untilTick) {
        this.visionRestoreUntilTick = untilTick;
    }

    /** 当前是否处于视野恢复期 */
    public boolean isVisionRestored(long nowTick) {
        return visionRestoreUntilTick > nowTick;
    }

    /** 是否存在恢复标记（含已过期未清理的） */
    public boolean hasRestoreMark() {
        return visionRestoreUntilTick >= 0L;
    }

    /** 清除恢复标记（恢复期结束/对局结束时调用） */
    public void clearVisionRestore() {
        this.visionRestoreUntilTick = -1L;
    }

    @Override
    public void init() {}

    /** 对局结束清理，防止状态带入下一局 */
    @Override
    public void clear() {
        clearVisionRestore();
    }

    @Override
    public void serverTick() {}

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    /** 持久化：重登后恢复期继续有效 */
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong(NBT_RESTORE_UNTIL, visionRestoreUntilTick);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        visionRestoreUntilTick = tag.contains(NBT_RESTORE_UNTIL)
                ? tag.getLong(NBT_RESTORE_UNTIL) : -1L;
    }
}
