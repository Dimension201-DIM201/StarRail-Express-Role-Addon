package com.dim201.dim201addon;

import com.dim201.dim201addon.cooperation.BrotherSyncManager;
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
 * 东堂葵玩家组件
 *
 * 主动技「不义游戏」与被动「兄弟情义」（虎杖 3 格内代替承受伤害）
 * 均由 {@link BrotherSyncManager} 统一管理，本组件只负责角色生命周期清理。
 */
public class TodoAoiPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<TodoAoiPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "todo_aoi"),
            TodoAoiPlayerComponent.class);

    private final Player player;

    public TodoAoiPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {}

    @Override
    public void clear() {
        BrotherSyncManager.resetFor(player.getUUID());
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void serverTick() {
        // 伤害转移由 BrotherSyncManager 的 LivingHurtCallback 处理
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
