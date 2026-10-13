package com.dim201.dim201addon.modifier;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

/**
 * shuang_yue_DDD 修饰符
 *
 * <p>效果：死亡时发出惨叫（播放自定义音效）。
 *
 * <p>原理：修饰符系统只管注册和持有，死亡回调通过 SRE 的 {@link OnPlayerDeath#EVENT} 注册，
 * 检查死者是否持有此修饰符，有则在死亡位置播放音效。
 *
 * <p>为什么不用 SREModifier 的 serverTickEvent：死亡事件是一次性的，tick 回调需要自己计时，
 * 不如直接注册 OnPlayerDeath 事件简单。
 */
public final class ShuangyueDDDModifier {

    /** 修饰符（与张老师同色 0xFF00E680，默认随机分配） */
    public static final SREModifier SHUANGYUE_DDD = HMLModifiers.registerModifier(new SREModifier(
            Dim201Addon.id("shuang_yue_ddd"),
            0xFF00E680,
            null, null,
            false, false
    )).setCanSetSpawnInfoInConfig(false);

    /** 死亡惨叫音效（固定范围 16 格） */
    public static final SoundEvent SOUND_SYDEATH = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            Dim201Addon.id("sydeath"),
            SoundEvent.createFixedRangeEvent(Dim201Addon.id("sydeath"), 16.0f)
    );

    private static boolean registered = false;

    private ShuangyueDDDModifier() {}

    public static void init() {
        if (registered) return;
        registered = true;

        // 死亡事件：持有修饰符的玩家死亡时播放惨叫
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            var level = sp.serverLevel();

            WorldModifierComponent wmc = WorldModifierComponent.KEY.get(level);
            if (wmc == null) return;
            if (!wmc.isModifier(sp, SHUANGYUE_DDD)) return;

            // 在死亡位置播放，范围 16 格（SOUND_SYDEATH 的 fixed range）
            level.playSound(
                    null,                    // null = 所有玩家能听到（受距离限制）
                    sp.blockPosition(),      // 位置
                    SOUND_SYDEATH,           // 音效事件
                    SoundSource.PLAYERS,     // 声线
                    0.4f,                    // 音量
                    1.0f                     // 音调
            );
        });
    }
}
