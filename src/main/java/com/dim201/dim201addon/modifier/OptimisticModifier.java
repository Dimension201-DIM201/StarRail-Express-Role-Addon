package com.dim201.dim201addon.modifier;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.Random;

/**
 * 乐观修饰符
 *
 * <p>效果：拥有此修饰符的玩家每 90~180 秒随机发出笑声，
 * 音效传播范围约 8 格。共 10 个笑声音效（laugh1~laugh10.ogg）随机播放。
 *
 * <p>原理：通过 {@link SREModifier#setServerTickEvent} 注册 tick 回调，
 * 修饰符系统自动遍历所有持有此修饰符的玩家调用回调。
 * 每个玩家维护独立的下次触发时间，避免多玩家同频笑声。
 */
public final class OptimisticModifier {

    /** 乐观修饰符（亮黄色，默认随机分配） */
    public static final SREModifier OPTIMISTIC = HMLModifiers.registerModifier(new SREModifier(
            Dim201Addon.id("optimistic"),
            0xFFDD00,   // 亮黄色
            null, null,
            false, false
    )).setServerGameTickEvent(OptimisticModifier::onServerTick);

    /**
     * 笑声事件（固定范围 8 格）。
     * sounds.json 里 laugh 会聚合 10 个 ogg，Minecraft 自动随机挑一个。
     */
    public static final SoundEvent SOUND_LAUGH = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            Dim201Addon.id("laugh"),
            SoundEvent.createFixedRangeEvent(Dim201Addon.id("laugh"), 8.0f)
    );

    /**
     * 下次笑声触发时间（tick）。
     * 用玩家 UUID 做 key，每个玩家独立计时。
     */
    private static final java.util.Map<java.util.UUID, Long> NEXT_LAUGH_TICK =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();

    /** 笑声触发间隔：90~180 秒（1800~3600 tick），比旧版 60~120 秒更低频 */
    private static final long MIN_INTERVAL = 90L * 20L;
    private static final long RANDOM_INTERVAL_SPAN = 90L * 20L;
    /** 笑声音量 */
    private static final float LAUGH_VOLUME = 0.4f;

    private static boolean gameEndRegistered = false;

    private OptimisticModifier() {}

    /**
     * 初始化（触发类加载，注册修饰符 + 声音事件 + 对局结束清理）。
     */
    public static void init() {
        if (gameEndRegistered) return;
        gameEndRegistered = true;
        // 对局结束清空计时表：SRE 世界 gameTime 跨局单调递增，
        // 残留时间戳会导致新局开局 currentTick >= 旧时间戳而立即播放笑声
        OnGameEnd.EVENT.register((level, gameWorld) -> NEXT_LAUGH_TICK.clear());
    }

    /**
     * 修饰符 serverTickEvent：每 tick 检查是否到点播放笑声。
     *
     * <p>为什么不用 ItemStack cooldown 或玩家 DataComponent：
     * 修饰符系统的 serverTickEvent 是"自带 tick 回调"的设计，
     * 直接注册回调比再建 CCA 组件更轻量。
     *
     * <p>tick 间隔说明：90~180 秒 = 1800~3600 tick。
     */
    private static void onServerTick(ServerPlayer player) {
        long currentTick = player.serverLevel().getGameTime();
        // 首次 tick：初始化下次触发时间为 90~180s 后，避免对局开始立即播放笑声
        NEXT_LAUGH_TICK.putIfAbsent(player.getUUID(),
                currentTick + MIN_INTERVAL + RANDOM.nextInt((int) RANDOM_INTERVAL_SPAN + 1));
        long nextTick = NEXT_LAUGH_TICK.get(player.getUUID());

        if (currentTick >= nextTick) {
            // 用 playSound 位置音效（传播范围 = range 参数，这里是 8 格）
            player.serverLevel().playSound(
                    null,                       // null = 所有玩家都能听到（但受距离限制）
                    player.blockPosition(),     // 音效来源位置
                    SOUND_LAUGH,                // sounds.json laugh → 随机选 1~10
                    SoundSource.PLAYERS,        // 玩家声线
                    LAUGH_VOLUME,               // 音量（降低 1/3）
                    0.9f + RANDOM.nextFloat() * 0.2f  // 音调微调（0.9~1.1），更自然
            );
            // 排下一次：90~180 秒后
            long delay = MIN_INTERVAL + RANDOM.nextInt((int) RANDOM_INTERVAL_SPAN + 1);
            NEXT_LAUGH_TICK.put(player.getUUID(), currentTick + delay);
        }
    }
}
