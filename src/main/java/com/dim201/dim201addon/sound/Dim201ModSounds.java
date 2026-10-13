package com.dim201.dim201addon.sound;

import com.dim201.dim201addon.Dim201Addon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 自定义音效注册：小丑喇叭 clown
 * 资源位置：assets/dim201addon/sounds/clown.ogg（用户后续自己放音频文件）
 * sounds.json 里同步注册 "clown" 条目。
 */
public final class Dim201ModSounds {

    public static final SoundEvent CLOWN = register("clown");

    /** 蕾忍：切换到岚刀模式 */
    public static final SoundEvent DAO_LEIREN_SWITCH_LAN = register("dao_leiren.switch_lan");
    /** 蕾忍：切换到燃烧刀模式 */
    public static final SoundEvent DAO_LEIREN_SWITCH_RAN = register("dao_leiren.switch_ran");
    /** 蕾忍：复活 */
    public static final SoundEvent DAO_LEIREN_RESURRECT = register("dao_leiren.resurrect");

    /** 电击狂人：玩家触电时仅电击狂人本人听到的怪笑（sounds.json 聚合 4 个音效随机播放） */
    public static final SoundEvent ELECTRIC_SHOCK = register("electricmaniac.shock");

    /** 独狼：清算时间全场音乐（客户端 MyBackgroundAmbience 循环播放） */
    public static final SoundEvent LONE_WOLF_SETTLEMENT = register("lonewolf");

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        Dim201Addon.LOGGER.info("Dim201Addon sounds registered");
    }

    private Dim201ModSounds() {}
}
