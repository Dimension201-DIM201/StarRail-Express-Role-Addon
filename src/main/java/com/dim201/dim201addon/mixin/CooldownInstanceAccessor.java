package com.dim201.dim201addon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 冷却实例访问器：{@code ItemCooldowns.CooldownInstance} 是包私有 record，
 * 无法直接 import，用 targets 定位读取冷却起止游戏时刻。
 */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {

    @Accessor("startTime")
    int dim201addon$startTime();

    @Accessor("endTime")
    int dim201addon$endTime();
}
