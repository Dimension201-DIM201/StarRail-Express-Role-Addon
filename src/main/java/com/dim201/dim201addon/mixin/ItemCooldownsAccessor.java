package com.dim201.dim201addon.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 冷却表访问器：读取 {@link ItemCooldowns} 内部冷却映射与游戏计时。
 *
 * <p>供独狼清算时间将制式左轮冷却钳制在 2s（每 tick 检查剩余冷却，
 * 与开火事件时序解耦，必然生效）。
 */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {

    @Accessor("cooldowns")
    Map<Item, Object> dim201addon$cooldowns();

    @Accessor("tickCount")
    int dim201addon$tickCount();
}
