package com.dim201.dim201addon.init;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.entity.CleaverEntity;
import com.dim201.dim201addon.entity.LoneWolfDroneEntity;
import com.dim201.dim201addon.entity.SeikibankiHeadEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 自定义实体类型注册
 *
 * <p>「山姥的菜刀」投掷实体与赤蛮奇「飞头」头颅实体。
 */
public final class ModEntities {

    /** 山姥的菜刀投掷实体 */
    public static final EntityType<CleaverEntity> CLEAVER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Dim201Addon.id("cleaver"),
            EntityType.Builder.<CleaverEntity>of(CleaverEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(Dim201Addon.MOD_ID + ":cleaver")
    );

    /** 赤蛮奇「飞头」头颅实体：0.5×0.5，可被攻击、有碰撞、可绑定相机 */
    public static final EntityType<SeikibankiHeadEntity> SEIKIBANKI_HEAD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Dim201Addon.id("seikibanki_head"),
            EntityType.Builder.<SeikibankiHeadEntity>of(SeikibankiHeadEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build(Dim201Addon.MOD_ID + ":seikibanki_head")
    );

    /** 独狼「隐形无人机」实体：0.3×0.3，隐形、不可攻击、有碰撞
     * clientTrackingRange(16) 对应 256 格，覆盖最大飞行距离 */
    public static final EntityType<LoneWolfDroneEntity> LONE_WOLF_DRONE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Dim201Addon.id("lone_wolf_drone"),
            EntityType.Builder.<LoneWolfDroneEntity>of(LoneWolfDroneEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .fireImmune()
                    .build(Dim201Addon.MOD_ID + ":lone_wolf_drone")
    );

    private ModEntities() {}

    public static void init() {
        // 空方法，仅用于确保类被加载 → 静态初始化执行 Registry.register
        Dim201Addon.LOGGER.info("[ModEntities] Custom entity types registered");
    }
}
