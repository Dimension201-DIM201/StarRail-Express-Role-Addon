package com.dim201.dim201addon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 门穿透判定工具类——灵梦封魔针与独狼无人机共用。
 *
 * <p>原理：SRE/TMM 的门方块在开启时仍保留碰撞形状，
 * 飞行实体（射线/无人机）需要识别并跳过这些方块。
 */
public final class DoorPenetrationHelper {

    private DoorPenetrationHelper() {}

    /**
     * 判断该位置是否是"可穿透的门方块"。
     *
     * <p>双保险判定：
     * <ol>
     *   <li>门 BlockEntity：位置有 DoorBlockEntity → 直接穿</li>
     *   <li>命名空间+路径：SRE/TMM 注册的方块，路径含 door/gate/cabin/wagon/cart/room → 穿</li>
     * </ol>
     */
    public static boolean isDoorBlock(Level level, BlockPos pos, BlockState state) {
        // 1) 门 BE
        if (level.getBlockEntity(pos) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity) {
            return true;
        }
        // 2) 命名空间+路径
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        String ns = id.getNamespace();
        if ("starrailexpress".equals(ns) || "trainmurdermystery".equals(ns)) {
            String path = id.getPath();
            if (path != null) {
                String p = path.toLowerCase(java.util.Locale.ROOT);
                return p.contains("door")
                        || p.contains("gate")
                        || p.contains("cabin")
                        || p.contains("wagon")
                        || p.contains("cart")
                        || p.contains("room")
                        || p.contains("compartment")
                        || (p.contains("car") && (p.contains("door") || p.endsWith("_car") || p.startsWith("car_")));
            }
        }
        return false;
    }
}
