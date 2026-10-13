package com.dim201.dim201addon.item.card;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.dim201.dim201addon.item.card.PassiveCardInterfaces.*;

/**
 * 携带生效卡牌事件总线
 *
 * <p>使用方式：
 * <ol>
 *   <li>卡牌类实现需要的接口（OnKilledPlayerCard / OnBeingAttackedCard ...）</li>
 *   <li>卡牌构造器中调用 CardEventBus.register(this) 注册</li>
 * </ol>
 *
 * <p>触发机制：
 * 本 Bus 在 Dim201Addon.onInitialize 中 init() 一次，注册 6 个全局 SRE/Fabric 事件监听器。
 * 每个事件只注册一个回调 → 内部遍历已注册卡牌 → 检查相关玩家背包是否含该卡 →
 * 若含卡，则调用卡牌实现的接口方法。
 *
 * <p>所有调用包在 try/catch 内，单卡异常不影响其它卡牌与对局。
 */
public final class CardEventBus {

    // ====== 注册表：接口分类存储 ======
    private static final CopyOnWriteArrayList<OnKilledPlayerCard> KILL_CARDS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<OnBeingAttackedCard> ATTACKED_CARDS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<OnPlayerDeathCard> DEATH_CARDS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<OnTickCard> TICK_CARDS = new CopyOnWriteArrayList<>();

    /** OnTickCard 节流：玩家 UUID → 上次触发 tick 计数（ServerLevel tick 数） */
    private static final ConcurrentHashMap<UUID, Long> TICK_LAST_TRIGGER = new ConcurrentHashMap<>();
    private static final long TICK_INTERVAL = 20L; // 1 秒一次

    private static boolean initialized = false;

    private CardEventBus() {}

    // ==================== 注册入口 ====================

    /** 注册一张携带生效卡牌（自动按接口分类） */
    public static synchronized void register(Item card) {
        if (card instanceof OnKilledPlayerCard c) KILL_CARDS.addIfAbsent(c);
        if (card instanceof OnBeingAttackedCard c) ATTACKED_CARDS.addIfAbsent(c);
        if (card instanceof OnPlayerDeathCard c) DEATH_CARDS.addIfAbsent(c);
        if (card instanceof OnTickCard c) TICK_CARDS.addIfAbsent(c);
    }

    // ==================== 初始化：由 Dim201Addon.onInitialize 调用 ====================

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        registerKillEvent();
        registerDeathEvent();
        registerAttackEvent();
        registerTickEvent();
        registerCleanupEvents();
    }

    // ==================== 各事件注册 ====================

    /** 玩家击杀玩家 → 分发 OnKilledPlayerCard */
    private static void registerKillEvent() {
        OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
            if (!(killer instanceof ServerPlayer spKiller)) return;
            if (KILL_CARDS.isEmpty()) return;
            for (OnKilledPlayerCard card : KILL_CARDS) {
                try {
                    Item item = (Item) card;
                    if (CardUtils.countInInventory(spKiller, item) > 0) {
                        card.onKilledPlayer(spKiller, (ServerPlayer) victim, resolve(reason));
                    }
                } catch (Exception e) {
                    Dim201Addon.LOGGER.error("[CardEventBus] OnKilledPlayerCard 异常: {}", card, e);
                }
            }
        });
    }

    /** 玩家死亡 → 分发 OnPlayerDeathCard */
    private static void registerDeathEvent() {
        OnPlayerDeath.EVENT.register((deadPlayer, reason) -> {
            if (!(deadPlayer instanceof ServerPlayer spDead)) return;
            if (DEATH_CARDS.isEmpty()) return;
            ServerPlayer killer = null;
            // 尽力找到 killer（SRE 的 reason 内部结构不开放，暂留 null，用方可忽略参数）
            for (OnPlayerDeathCard card : DEATH_CARDS) {
                try {
                    Item item = (Item) card;
                    if (CardUtils.countInInventory(spDead, item) > 0) {
                        card.onPlayerDeath(spDead, killer, resolve(reason));
                    }
                } catch (Exception e) {
                    Dim201Addon.LOGGER.error("[CardEventBus] OnPlayerDeathCard 异常: {}", card, e);
                }
            }
        });
    }

    /** 玩家被攻击 → 分发 OnBeingAttackedCard（允许修改伤害） */
    private static void registerAttackEvent() {
        AttackEntityCallback.EVENT.register((attacker, world, hand, entity, hitResult) -> {
            if (world.isClientSide) return net.minecraft.world.InteractionResult.PASS;
            if (!(entity instanceof ServerPlayer victim) || victim.isSpectator()) return net.minecraft.world.InteractionResult.PASS;
            if (ATTACKED_CARDS.isEmpty()) return net.minecraft.world.InteractionResult.PASS;

            // 原始伤害取 attacker 基础伤害（AttackEntityCallback 不含 damage 值，此处仅触发效果类卡牌；
            // 精确伤害修改建议通过 Mixin 注入 LivingEntity.hurt 方法实现）
            float damage = 1.0f;
            LivingEntity attackerLiving = attacker instanceof LivingEntity ? (LivingEntity) attacker : null;

            for (OnBeingAttackedCard card : ATTACKED_CARDS) {
                try {
                    Item item = (Item) card;
                    if (CardUtils.countInInventory(victim, item) > 0) {
                        float result = card.onBeingAttacked(victim, attackerLiving, damage);
                        if (result <= 0) {
                            // 卡牌完全抵挡了这次攻击 → 取消攻击
                            return net.minecraft.world.InteractionResult.CONSUME;
                        }
                        damage = result;
                    }
                } catch (Exception e) {
                    Dim201Addon.LOGGER.error("[CardEventBus] OnBeingAttackedCard 异常: {}", card, e);
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }

    /** 每 20 tick（1 秒）对每个存活玩家分发 OnTickCard */
    private static void registerTickEvent() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (TICK_CARDS.isEmpty()) return;
            if (!(world instanceof ServerLevel sl)) return;
            long levelTick = sl.getGameTime();

            for (ServerPlayer p : sl.players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;

                Long last = TICK_LAST_TRIGGER.get(p.getUUID());
                if (last != null && levelTick - last < TICK_INTERVAL) continue;
                TICK_LAST_TRIGGER.put(p.getUUID(), levelTick);

                for (OnTickCard card : TICK_CARDS) {
                    try {
                        Item item = (Item) card;
                        ItemStack stack = findStack(p, item);
                        if (stack != null) card.onTick(p, stack);
                    } catch (Exception e) {
                        Dim201Addon.LOGGER.error("[CardEventBus] OnTickCard 异常: {}", card, e);
                    }
                }
            }
        });
    }

    /** 清理：玩家离开移除缓存；服务器级定期清理过期条目 */
    private static void registerCleanupEvents() {
        // 玩家断开连接
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID u = handler.getPlayer().getUUID();
            TICK_LAST_TRIGGER.remove(u);
        });

        // 每 60 秒清理无在线玩家的过期条目
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (server.overworld().getGameTime() % 1200L != 0L) return;
            List<UUID> valid = new ArrayList<>();
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) valid.add(sp.getUUID());
            TICK_LAST_TRIGGER.keySet().retainAll(valid);
        });
    }

    // ==================== 小工具 ====================

    /** 找一个玩家背包里属于 item 的非空 ItemStack（找不到返回 null） */
    @Nullable
    private static ItemStack findStack(ServerPlayer p, Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return s;
        for (ItemStack s : p.getInventory().armor) if (s.is(item)) return s;
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) return s;
        return null;
    }

    /** SRE 的 deathReason 在不同版本是 ResourceLocation/自定义对象，尽力转成 ResourceLocation */
    private static ResourceLocation resolve(Object reason) {
        if (reason instanceof ResourceLocation rl) return rl;
        // 默认返回占位 id（卡牌方可用 reason.toString() 做字符串比较）
        return ResourceLocation.withDefaultNamespace("unknown");
    }
}
