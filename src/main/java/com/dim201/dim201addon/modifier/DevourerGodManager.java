package com.dim201.dim201addon.modifier;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.DevourerGodPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.DevourerGodRole;
import com.dim201.dim201addon.role.DevourerRole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.init.ModEffects;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 神明吞噬者时刻 - 核心管理器
 *
 * <p>原理：镜像 SRE 难民（Refugee）的死后复活机制，但有以下区别：
 * <ul>
 *   <li>只出现在吞噬者（DevourerRole）身上，吞噬者不会获得难民修饰符</li>
 *   <li>复活后变为「神明吞噬者」角色（DevourerGodRole），而非亡命徒 LOOSE_END</li>
 *   <li>全局效果中把 MobEffects.WEAVING 换成 ModEffects.DREAMCORE_FILTER</li>
 *   <li>自定义音频替换 StupidExpress.SOUND_REGUGEE</li>
 * </ul>
 *
 * <p>流程：
 * <ol>
 *   <li>吞噬者死亡时若持有 DEVOURER_GOD 修饰符 → 加入待复活列表（2 分钟后复活）</li>
 *   <li>复活时间到 → {@link #reviveAsGodDevourer}：
 *     传送至房间出生点、切换角色为神明吞噬者、清空背包、游戏时间+120s、
 *     全员黑屏、全局效果（DREAMCORE_FILTER/DARKNESS/GLOWING）、音效、标题通知、
 *     禁用技能、播放自定义音频、给予 1 层护盾、保存所有玩家状态</li>
 *   <li>150s 超时 → 击杀神明吞噬者</li>
 *   <li>神明吞噬者死亡 → {@link #onGodDevourerDeath}：恢复游戏时间、延迟 1 tick 恢复</li>
 *   <li>{@link #afterGodDevourerTryRestore}：启用技能、全员恢复状态、清空效果</li>
 * </ol>
 *
 * <p>线程安全：使用 ConcurrentHashMap + volatile 保证并发安全。
 */
public final class DevourerGodManager {

    /** 待复活列表：UUID → 复活数据 */
    private static final ConcurrentHashMap<UUID, DevourerGodData> pendingRevivals = new ConcurrentHashMap<>();
    /** 玩家状态快照：UUID → 快照 */
    private static final ConcurrentHashMap<UUID, DevourerGodStats> playersStats = new ConcurrentHashMap<>();
    /** 是否有进行中的神明吞噬者时刻 */
    private static volatile boolean isAnyRevivals = false;
    /** 延迟恢复标记（下一 tick 执行恢复） */
    private static volatile boolean isPendingRestore = false;
    /** 待恢复的玩家 */
    private static volatile ServerPlayer pendingWho = null;
    /** 神明吞噬者时刻开始前的游戏时间（用于恢复） */
    private static volatile int lastTime = 0;

    /** 复活延迟（tick）：2 分钟 = 2400 ticks */
    public static final int REVIVAL_DELAY_TICKS = 2400;
    /** 神明吞噬者时刻持续时长（tick）：150 秒 = 3000 ticks */
    public static final int DURATION_TICKS = 3000;
    /** DREAMCORE_FILTER 持续时间（tick）：150 秒 = 3000 ticks */
    public static final int DREAMCORE_FILTER_TICKS = 150 * 20;
    /** DARKNESS 持续时间（tick） */
    public static final int DARKNESS_TICKS = 40;
    /** GLOWING 持续时间（tick）：5 分钟 = 6000 ticks */
    public static final int GLOWING_TICKS = 5 * 60 * 20;
    /** 游戏时间增量（tick）：120 秒 = 2400 ticks */
    public static final int GAME_TIME_INCREMENT = 120 * 20;
    /** 倒计时提示间隔（tick） */
    public static final int COUNTDOWN_INTERVAL = 200;
    /** 周期扫描间隔（tick）：10 秒 = 200 ticks */
    public static final int SCAN_INTERVAL = 10 * 20;
    /** 扫描范围（格） */
    public static final double SCAN_RANGE = 32.0;
    /** 扫描发光持续时间（tick）：3 秒 = 60 ticks */
    public static final int SCAN_GLOW_TICKS = 3 * 20;

    private static boolean registered = false;

    private DevourerGodManager() {}

    /** 待复活数据 */
    public static class DevourerGodData {
        public final UUID uuid;
        public final long revivalTime;
        public volatile boolean isRevive;
        public volatile boolean isDead;

        public DevourerGodData(UUID uuid, long revivalTime, boolean isRevive) {
            this.uuid = uuid;
            this.revivalTime = revivalTime;
            this.isRevive = isRevive;
            this.isDead = false;
        }
    }

    /** 初始化：注册 ServerTickEvents 和 OnPlayerDeath 事件（幂等） */
    public static void init() {
        if (registered) return;
        registered = true;

        // 服务端 tick：处理复活计时、超时击杀、倒计时提示、延迟恢复
        ServerTickEvents.END_SERVER_TICK.register(DevourerGodManager::serverTick);

        // 玩家死亡事件：检查 DEVOURER_GOD 修饰符 / 神明吞噬者角色
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            var level = sp.serverLevel();
            var worldModifierComponent = WorldModifierComponent.KEY.get(level);
            var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (worldModifierComponent == null || gameWorldComponent == null) return;

            // 持有 DEVOURER_GOD 修饰符的玩家死亡 → 加入待复活列表
            if (worldModifierComponent.isModifier(sp, DevourerGodModifier.DEVOURER_GOD)) {
                addPendingRevival(sp.getUUID(), level);
            }

            // 神明吞噬者角色死亡 → 触发恢复流程
            var role = gameWorldComponent.getRole(sp);
            if (role != null && role.equals(DevourerGodRole.DEVOURER_GOD_ROLE)) {
                onGodDevourerDeath(sp, deathReason);
            }
        });

        // 拦截游戏结束：神明吞噬者存活时游戏不结束，杀光所有人时获胜
        AllowGameEnd.EVENT.register((serverWorld, winStatus, isLooseEndsMode) -> {
            // 只在游戏有结束倾向时介入
            if (winStatus == GameUtils.WinStatus.NONE || winStatus == GameUtils.WinStatus.NOT_MODIFY) {
                return GameUtils.WinStatus.NOT_MODIFY;
            }

            var gw = SREGameWorldComponent.KEY.get(serverWorld);
            if (gw == null || !gw.isRunning()) return GameUtils.WinStatus.NOT_MODIFY;

            // 检查场上是否有存活的神明吞噬者
            ServerPlayer devourerGod = null;
            for (ServerPlayer p : serverWorld.players()) {
                if (DevourerGodRole.isDevourerGod(p) && !GameUtils.isPlayerEliminated(p)) {
                    devourerGod = p;
                    break;
                }
            }

            if (devourerGod == null) {
                // 没有存活的神明吞噬者，不干预
                return GameUtils.WinStatus.NOT_MODIFY;
            }

            // 神明吞噬者存活，检查是否所有其他玩家都被淘汰
            boolean hasOtherAlive = false;
            for (ServerPlayer p : serverWorld.players()) {
                if (p == devourerGod) continue;
                if (!GameUtils.isPlayerEliminated(p)) {
                    hasOtherAlive = true;
                    break;
                }
            }

            if (!hasOtherAlive) {
                // 所有其他玩家被淘汰，神明吞噬者获胜
                gw.setLooseEndWinner(devourerGod.getUUID());
                var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                roundEnd.CustomWinnerID = "dim201addon.devourer_god";
                roundEnd.CustomWinnerColor = 0xFFAA00AA;
                roundEnd.CustomWinnerPlayers.clear();
                roundEnd.CustomWinnerPlayers.add(devourerGod.getUUID());
                return GameUtils.WinStatus.CUSTOM;
            }

            // 神明吞噬者存活但还有其他玩家，游戏继续
            return GameUtils.WinStatus.NONE;
        });
    }

    /** 添加待复活（2 分钟 = 2400 ticks 后复活） */
    public static void addPendingRevival(UUID uuid, ServerLevel level) {
        long revivalTime = level.getGameTime() + REVIVAL_DELAY_TICKS;
        pendingRevivals.put(uuid, new DevourerGodData(uuid, revivalTime, false));
    }

    /**
     * 直接触发神明吞噬者时刻（指令调用，跳过修饰符分配与死亡复活流程）。
     *
     * <p>原理：构造 revivalTime = 当前游戏时间的 DevourerGodData，
     * 直接调用 reviveAsGodDevourer 立即复活为神明吞噬者。
     *
     * @param player 目标玩家
     * @return 是否触发成功（游戏未运行或玩家不在线时返回 false）
     */
    public static boolean triggerDevourerGodMoment(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) return false;
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.serverLevel());
        if (gameWorldComponent == null || !gameWorldComponent.isRunning()) return false;

        ServerLevel level = player.serverLevel();
        // 构造 revivalTime = 当前时间，使 serverTick 立即触发复活
        DevourerGodData data = new DevourerGodData(player.getUUID(), level.getGameTime(), false);
        // 先加入 pendingRevivals，便于后续超时击杀与状态管理
        pendingRevivals.put(data.uuid, data);
        // 立即复活为神明吞噬者
        reviveAsGodDevourer(data, level);
        data.isRevive = true;
        isAnyRevivals = true;
        return true;
    }

    /** 服务端 tick 主逻辑 */
    private static void serverTick(MinecraftServer server) {
        if (pendingRevivals.isEmpty() && !isPendingRestore) return;

        long currentTime = -1;
        // 遍历所有待复活数据
        List<UUID> toRemove = new ArrayList<>();
        for (var data : pendingRevivals.values()) {
            if (data.isDead) {
                toRemove.add(data.uuid);
                continue;
            }
            // 懒加载当前时间（仅需时获取）
            if (currentTime < 0) {
                currentTime = getCurrentGameTime(server);
                if (currentTime < 0) continue;
            }
            ServerLevel level = findSreLevel(server);
            if (level == null) continue;

            // 到复活时间 → 复活为神明吞噬者
            if (!data.isRevive && currentTime >= data.revivalTime) {
                reviveAsGodDevourer(data, level);
                data.isRevive = true;
            }
            // 复活后超时（150s）→ 击杀神明吞噬者
            if (data.isRevive && !data.isDead && currentTime >= data.revivalTime + DURATION_TICKS) {
                data.isDead = true;
                killGodDevourer(server, data.uuid);
            }
            // 复活后每 10s 扫描周围 16 格，使被扫描到的玩家发光 3s
            if (data.isRevive && !data.isDead && currentTime >= 0
                    && currentTime % SCAN_INTERVAL == 0) {
                scanAndGlowNearbyPlayers(server, data.uuid);
            }
        }
        toRemove.forEach(pendingRevivals::remove);

        // 每 200 tick 发送倒计时提示
        if (currentTime >= 0 && currentTime % COUNTDOWN_INTERVAL == 0) {
            sendCountdownMessages(server, currentTime);
        }

        // 延迟恢复
        if (isPendingRestore) {
            isPendingRestore = false;
            afterGodDevourerTryRestore(pendingWho);
        }
    }

    /**
     * 扫描神明吞噬者周围 16 格内的玩家，使其发光 3 秒。
     *
     * <p>原理：获取神明吞噬者周围 SCAN_RANGE 范围内的所有存活玩家，
     * 排除神明吞噬者自身，给每个有效目标添加 GLOWING 效果。
     * 使用 {@link GameUtils#isPlayerAliveAndSurvival} 过滤死亡/观众态玩家。
     */
    private static void scanAndGlowNearbyPlayers(MinecraftServer server, UUID godUuid) {
        ServerPlayer godPlayer = server.getPlayerList().getPlayer(godUuid);
        if (godPlayer == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(godUuid)) continue; // 排除神明吞噬者自身
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue; // 跳过死亡/观众态
            if (p.distanceTo(godPlayer) <= SCAN_RANGE) {
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCAN_GLOW_TICKS, 0, false, false));
            }
        }
    }

    /** 复活为神明吞噬者 */
    public static void reviveAsGodDevourer(DevourerGodData data, ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.uuid);
        if (player == null) return;

        // 1. 传送到房间出生点
        var i = GameUtils.roomToPlayer.get(data.uuid);
        if (i == null) i = 1;
        var areasWorldComponent = AreasWorldComponent.KEY.get(level);
        var roomPosition = areasWorldComponent.getRoomPosition(i);
        if (roomPosition != null) {
            player.teleportTo(level, roomPosition.x, roomPosition.y, roomPosition.z,
                    player.getYRot(), player.getXRot());
        }

        // 2. 移除 DEVOURER_GOD 修饰符
        WorldModifierComponent wm = WorldModifierComponent.KEY.get(level);
        wm.removeModifier(player.getUUID(), DevourerGodModifier.DEVOURER_GOD, false);

        // 3. 切换角色为神明吞噬者（不记录旧角色，不统计场次）
        StupidRoleUtils.changeRole(player, DevourerGodRole.DEVOURER_GOD_ROLE, false, false);
        StupidRoleUtils.sendWelcomeAnnouncement(player);

        // 4. 清空背包，切换冒险模式，给予撬棍
        player.getInventory().clearContent();
        player.setGameMode(GameType.ADVENTURE);
        Item crowbar = BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("trainmurdermystery:crowbar"));
        if (crowbar != null && crowbar != net.minecraft.world.item.Items.AIR) {
            player.getInventory().add(new ItemStack(crowbar));
        }

        // 5. 游戏时间 +120 秒
        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(level);
        lastTime = gameTimeComponent.getTime();
        gameTimeComponent.setTime(gameTimeComponent.getTime() + GAME_TIME_INCREMENT);

        // 6. 全员黑屏
        SREWorldBlackoutComponent.KEY.get(level).triggerBlackout();

        // 7. 删除尸体实体
        removePlayerBody(level, data.uuid);

        // 8. 全局效果 + 音效 + 通知
        applyGlobalEffects(level, player);

        // 9. 禁用技能
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        gameWorldComponent.disableSkillsAndSync();

        // 10. 自定义音频由客户端背景氛围系统播放（MyBackgroundAmbience），
        //     无需服务端 playNotifySound，避免客户端加载/切换时错过音效包

        // 11. 给予初始 1 层护盾
        SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(player);
        armorComp.giveArmor();

        // 12. 激活天蓝色雾（同步给所有客户端，由 DevourerGodFogMixin 渲染）
        DevourerGodPlayerComponent godComp = ModComponents.DEVOURER_GOD.get(player);
        godComp.fogActive = true;
        godComp.sync();

        // 12. 保存所有玩家状态（仅首次复活时）
        if (!isAnyRevivals) {
            SavePlayersStats(level);
        }
        isAnyRevivals = true;

        // 13. 同步修饰符组件
        wm.sync();
    }

    /** 删除玩家尸体实体 */
    private static void removePlayerBody(ServerLevel level, UUID uuid) {
        List<Entity> bodiesToRemove = new ArrayList<>();
        for (var body : level.getAllEntities()) {
            if (body instanceof PlayerBodyEntity bodyEntity) {
                if (bodyEntity.getPlayerUuid().equals(uuid)) {
                    bodiesToRemove.add(body);
                    break;
                }
            }
        }
        bodiesToRemove.forEach(Entity::discard);
    }

    /** 应用全局效果（DREAMCORE_FILTER/DARKNESS/GLOWING）+ 音效 + 标题通知 */
    private static void applyGlobalEffects(ServerLevel level, ServerPlayer revivedPlayer) {
        for (final ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            // 标题/字幕
            SRENetworkMessageUtils.sendTitle(p,
                    Component.literal("神明吞噬者时刻").withStyle(ChatFormatting.DARK_PURPLE));
            SRENetworkMessageUtils.sendSubtitle(p,
                    Component.literal("还没完呢，小子！").withStyle(ChatFormatting.LIGHT_PURPLE));
            // 音效
            p.playNotifySound(SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            // 全局效果：DREAMCORE_FILTER（替换 WEAVING）、DARKNESS、GLOWING
            p.addEffect(new MobEffectInstance(ModEffects.DREAMCORE_FILTER, DREAMCORE_FILTER_TICKS, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0, false, false));
            p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);
            // 复活者发光 5 分钟
            if (p.getUUID().equals(revivedPlayer.getUUID())) {
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOWING_TICKS, 0, false, false));
            }
            // 提示消息
            p.displayClientMessage(
                    Component.literal("§5§l「吞噬神明」§r§d ").append(revivedPlayer.getName())
                            .append("§d 已觉醒为神明吞噬者！吞噬一切！"),
                    true);
        }
    }

    /** 播放自定义音频（devourer_god_music） */
    private static void playDevourerGodMusic(ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.playNotifySound(DevourerGodModifier.SOUND_DEVOURER_GOD, SoundSource.AMBIENT, 0.2f, 1.0f);
        }
    }

    /** 击杀超时的神明吞噬者 */
    private static void killGodDevourer(MinecraftServer server, UUID uuid) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(uuid) && GameUtils.isPlayerAliveAndSurvival(p)) {
                GameUtils.killPlayer(p, true, null,
                        ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "devourer_god_timeout"));
                break;
            }
        }
    }

    /** 神明吞噬者死亡时 */
    public static void onGodDevourerDeath(ServerPlayer sp, ResourceLocation deathReason) {
        // 1. 恢复游戏时间
        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(sp.level());
        gameTimeComponent.setTime(lastTime);

        // 2. 检查是否还有其他神明吞噬者存活
        var gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
        boolean hasOtherGod = sp.getServer().getPlayerList().getPlayers().stream().anyMatch(p -> {
            if (!GameUtils.isPlayerAliveAndSurvival(p) || p.getUUID().equals(sp.getUUID())) return false;
            var r = gameWorldComponent.getRole(p);
            return r != null && r.equals(DevourerGodRole.DEVOURER_GOD_ROLE);
        });
        if (hasOtherGod) return;

        // 3. 延迟 1 tick 恢复（避免在死亡事件中直接操作玩家状态）
        isPendingRestore = true;
        pendingWho = sp;
        sp.setGameMode(GameType.SPECTATOR);
    }

    /** 实际恢复逻辑 */
    public static void afterGodDevourerTryRestore(ServerPlayer sp) {
        if (sp == null) return;
        var gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorldComponent == null) return;

        // 检查是否还有其他神明吞噬者存活
        boolean hasOtherGod = sp.getServer().getPlayerList().getPlayers().stream().anyMatch(p -> {
            if (!GameUtils.isPlayerAliveAndSurvival(p) || p.getUUID().equals(sp.getUUID())) return false;
            var r = gameWorldComponent.getRole(p);
            return r != null && r.equals(DevourerGodRole.DEVOURER_GOD_ROLE);
        });
        if (hasOtherGod) return;

        sp.setGameMode(GameType.SPECTATOR);
        isAnyRevivals = false;

        // 标记当前玩家的待复活数据为已死亡
        for (var rev : pendingRevivals.values()) {
            if (rev.uuid.equals(sp.getUUID())) {
                rev.isDead = true;
            }
        }

        // 0. 关闭天蓝色雾（同步给所有客户端，移除雾渲染）
        DevourerGodPlayerComponent godComp = ModComponents.DEVOURER_GOD.get(sp);
        godComp.fogActive = false;
        godComp.sync();

        // 1. 启用技能
        gameWorldComponent.enableSkillsAndSync();

        // 2. 全局效果：ENDER_DRAGON_DEATH 音效、BLACK_MONITOR（40 ticks）、移除 DREAMCORE_FILTER
        for (final ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            SRENetworkMessageUtils.sendTitle(p,
                    Component.literal("神明陨落").withStyle(ChatFormatting.DARK_PURPLE));
            SRENetworkMessageUtils.sendSubtitle(p,
                    Component.literal("秩序已恢复，时间回溯").withStyle(ChatFormatting.LIGHT_PURPLE));
            ServerPlayNetworking.send(p, new RemoveStatusBarPayload("devourer_god"));
            p.playNotifySound(SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            p.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR, DARKNESS_TICKS, 0, false, false));
            if (p.hasEffect(ModEffects.DREAMCORE_FILTER)) {
                p.removeEffect(ModEffects.DREAMCORE_FILTER);
            }
            p.displayClientMessage(
                    Component.literal("§5§l神明吞噬者已陨落，时间回溯至觉醒前"),
                    true);
            // 3. 自定义音频由客户端背景氛围系统自动渐出停止（PlayPredicate 返回 false），无需服务端 StopSound
        }

        // 4. 恢复所有玩家状态
        LoadPlayersStats(sp.serverLevel());

        // 5. 清空状态
        playersStats.clear();
        pendingRevivals.clear();
    }

    /** 保存所有存活玩家状态 */
    public static void SavePlayersStats(ServerLevel level) {
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        playersStats.clear();
        for (var player : players) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                playersStats.put(player.getUUID(), DevourerGodStats.SaveFromPlayer(player, true));
            }
        }
    }

    /** 恢复玩家状态 */
    public static void LoadPlayersStats(ServerLevel level) {
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        // 收集所有尸体实体
        var bodies = new java.util.HashMap<UUID, PlayerBodyEntity>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof PlayerBodyEntity body) {
                bodies.put(body.getPlayerUuid(), body);
            }
        }
        for (var player : players) {
            // 跳过神明吞噬者角色（不恢复）
            var r = gameWorldComponent.getRole(player);
            if (r != null && r.equals(DevourerGodRole.DEVOURER_GOD_ROLE)) {
                continue;
            }
            var stats = playersStats.get(player.getUUID());
            if (stats != null) {
                DevourerGodStats.LoadToPlayer(player, stats);
                // 删除玩家尸体
                var body = bodies.get(player.getUUID());
                if (body != null) {
                    body.remove(Entity.RemovalReason.DISCARDED);
                }
                // 退出死亡/旁观者语音频道（时刻中死亡的玩家已恢复为存活状态）
                TrainVoicePlugin.resetPlayer(player.getUUID());
            }
        }
        gameWorldComponent.sync();
    }

    /** 倒计时提示（60s/30s/10s） */
    private static void sendCountdownMessages(MinecraftServer server, long currentTime) {
        for (var data : pendingRevivals.values()) {
            if (data.isRevive || data.isDead) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(data.uuid);
            if (player == null) continue;

            long ticksRemaining = data.revivalTime - currentTime;
            int secondsRemaining = (int) ((ticksRemaining + 19) / 20);
            if (secondsRemaining == 60 || secondsRemaining == 30 || secondsRemaining == 10) {
                player.sendSystemMessage(
                        Component.literal("§c「吞噬神明」§7 将在 " + secondsRemaining + " 秒后觉醒..."),
                        true);
            }
        }
    }

    /** 获取当前游戏时间（从 SRE 游戏世界所在 level） */
    private static long getCurrentGameTime(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            var gw = SREGameWorldComponent.KEY.get(level);
            if (gw != null && gw.isRunning()) {
                return level.getGameTime();
            }
        }
        return -1;
    }

    /** 查找 SRE 游戏世界所在 level */
    private static ServerLevel findSreLevel(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            var gw = SREGameWorldComponent.KEY.get(level);
            if (gw != null && gw.isRunning()) {
                return level;
            }
        }
        return null;
    }

    /** 停止音效 */
    public static void StopSound(ServerPlayer serverPlayer, ResourceLocation resourceLocation,
                                  SoundSource soundSource) {
        ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(resourceLocation, soundSource);
        serverPlayer.connection.send(packet);
    }

    /**
     * 清空所有状态（由 OnGameEnd 调用）
     *
     * @param level 游戏世界所在 level，用于遍历玩家关闭天蓝色雾
     */
    public static void reset(ServerLevel level) {
        // 关闭所有玩家的天蓝色雾（防止对局结束时雾残留）
        if (level != null) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                DevourerGodPlayerComponent godComp = ModComponents.DEVOURER_GOD.getNullable(p);
                if (godComp != null && godComp.fogActive) {
                    godComp.fogActive = false;
                    godComp.sync();
                }
            }
        }
        pendingRevivals.clear();
        playersStats.clear();
        isAnyRevivals = false;
        isPendingRestore = false;
        pendingWho = null;
        lastTime = 0;
    }

    /** 无参重载（向后兼容，不关闭雾） */
    public static void reset() {
        pendingRevivals.clear();
        playersStats.clear();
        isAnyRevivals = false;
        isPendingRestore = false;
        pendingWho = null;
        lastTime = 0;
    }
}
