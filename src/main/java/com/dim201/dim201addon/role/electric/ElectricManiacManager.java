package com.dim201.dim201addon.role.electric;

import com.dim201.dim201addon.ElectricManiacPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.packet.electric.ElectricManiacBlastS2CPacket;
import com.dim201.dim201addon.packet.electric.ElectricManiacBlocksS2CPacket;
import com.dim201.dim201addon.packet.electric.ElectricManiacShockS2CPacket;
import com.dim201.dim201addon.role.ElectricManiacRole;
import com.dim201.dim201addon.sound.Dim201ModSounds;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 电击狂人静电系统服务端管理器
 *
 * <p>核心机制：
 * <ul>
 *   <li>静电方块：电击狂人右键方块附着静电（上限 16 个，30 秒），
 *       玩家靠近 3 格内获得静电；电击狂人自身不受方块影响</li>
 *   <li>静电玩家：两个静电玩家距离 < 3 格 → 触电（尖叫 -20san 全图高亮 + 受伤动画）</li>
 *   <li>技能 1「静电充能」：按技能键使自身获得静电（8 秒，5 秒冷却）</li>
 *   <li>技能 2「静电爆炸」：先定身 2 秒，结束后以自身为中心发射半径 32 格扩散粒子，
 *       球形范围内所有玩家触电（30 秒冷却）</li>
 * </ul>
 */
public final class ElectricManiacManager {

    private ElectricManiacManager() {}

    /** 静电方块数量上限 */
    public static final int MAX_BLOCKS = 16;
    /** 方块静电持续时间（tick）：30 秒 */
    public static final int BLOCK_DURATION_TICKS = 600;
    /** 玩家静电持续时间（tick）：15 秒 */
    public static final int PLAYER_DURATION_TICKS = 300;
    /** 技能1 自身充电持续时间（tick）：8 秒 */
    public static final int SELF_CHARGE_DURATION_TICKS = 160;
    /** 技能1 冷却（tick）：5 秒 */
    public static final int CHARGE_COOLDOWN_TICKS = 100;
    /** 技能2 冷却（tick）：30 秒 */
    public static final int BLAST_COOLDOWN_TICKS = 600;
    /** 技能2 定身前摇（tick）：2 秒 */
    public static final int BLAST_ROOT_TICKS = 40;
    /** 技能2 爆炸半径（格）：32 */
    public static final double BLAST_RADIUS = 32.0D;
    /** 方块静电影响半径（格） */
    public static final double BLOCK_AURA_RANGE = 3.0D;
    /** 静电玩家相互触发距离（格） */
    public static final double SHOCK_RANGE = 3.0D;
    /** 触电后减少的心情值（20 san = 0.2 mood） */
    public static final float SHOCK_MOOD_LOSS = 0.2f;
    /** 触电后全图高亮持续时间（tick）：5 秒 */
    public static final int SHOCK_HIGHLIGHT_TICKS = 100;
    /** 触电后定身时间（tick）：1 秒（127 级减速） */
    public static final int SHOCK_ROOT_TICKS = 20;
    /** 电击狂人触电怪笑的最小间隔（tick）：5 秒，防止多人同时触电重复播放 */
    public static final int SHOCK_SOUND_COOLDOWN_TICKS = 100;

    /** 技能1 冷却时间记录 */
    private static final Map<UUID, Long> chargeCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    /** 技能2 冷却时间记录 */
    private static final Map<UUID, Long> blastCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 静电方块：维度|位置组合 key → 到期 tick
     * 使用 LinkedHashMap 保证插入顺序，超过 {@link #MAX_BLOCKS} 时淘汰最早放置的方块。
     * 所有访问均在服务端主线程（网络包已 execute 到主线程）。
     */
    private static final Map<String, Long> electrifiedBlocks =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** 全图高亮中的玩家：UUID → 到期 tick（玩家 tickCount） */
    private static final Map<UUID, Long> highlightedPlayers = new java.util.concurrent.ConcurrentHashMap<>();

    /** 待引爆的静电爆炸：UUID → 引爆 tick（玩家 tickCount） */
    private static final Map<UUID, Long> pendingBlasts = new java.util.concurrent.ConcurrentHashMap<>();

    /** 各电击狂人上次听到触电怪笑的 gameTime：按聆听者 UUID 独立 5 秒冷却 */
    private static final Map<UUID, Long> lastShockSoundTick = new java.util.concurrent.ConcurrentHashMap<>();

    private static String blockKey(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.asLong();
    }

    /** 施加静电方块（超过上限时淘汰最早的方块） */
    public static void placeStaticBlock(Level level, BlockPos pos) {
        String key = blockKey(level.dimension(), pos);
        // 已存在则先移除再放入，刷新其在 LRU 队列中的位置
        electrifiedBlocks.remove(key);
        electrifiedBlocks.put(key, level.getGameTime() + BLOCK_DURATION_TICKS);
        // 超出上限：淘汰最早放置的方块
        while (electrifiedBlocks.size() > MAX_BLOCKS) {
            String oldest = electrifiedBlocks.keySet().iterator().next();
            electrifiedBlocks.remove(oldest);
        }
        syncBlocks(level);
    }

    /** 切换技能（0↔1） */
    public static void switchSkill(ServerPlayer player) {
        ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(player);
        comp.currentSkill = comp.currentSkill == 0 ? 1 : 0;
        comp.sync();
        player.displayClientMessage(
                Component.literal(comp.currentSkill == 0
                        ? "§b当前技能：§f静电充能"
                        : "§b当前技能：§c静电爆炸"),
                true);
    }

    /** 使用技能（G 键）：按当前技能分流 */
    public static void useSkill(ServerPlayer player) {
        ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(player);
        if (comp.currentSkill == 1) {
            triggerBlast(player);
        } else {
            chargeSelf(player);
        }
    }

    /** 技能1：自身充电 */
    public static void chargeSelf(ServerPlayer player) {
        long now = player.tickCount;
        Long cdEnd = chargeCooldown.get(player.getUUID());
        if (cdEnd != null && now < cdEnd) {
            player.displayClientMessage(Component.literal("§c技能冷却中"), true);
            return;
        }
        chargeCooldown.put(player.getUUID(), now + CHARGE_COOLDOWN_TICKS);

        ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(player);
        comp.electrified = true;
        comp.electrifiedUntilTick = now + SELF_CHARGE_DURATION_TICKS;
        comp.chargeReadyAtTick = now + CHARGE_COOLDOWN_TICKS;
        comp.sync();

        player.displayClientMessage(
                Component.literal("§b⚡ 静电已充能（持续 8 秒）"), true);
    }

    /**
     * 技能2：静电爆炸
     *
     * <p>流程：先施加 2 秒 127 级减速（定身），2 秒后在 {@link #serverTick} 中
     * 引爆——广播粒子包 + 球形 32 格内所有玩家触电。
     */
    public static void triggerBlast(ServerPlayer player) {
        long now = player.tickCount;
        if (pendingBlasts.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.literal("§c静电爆炸蓄力中！"), true);
            return;
        }
        Long cdEnd = blastCooldown.get(player.getUUID());
        if (cdEnd != null && now < cdEnd) {
            long remaining = (cdEnd - now + 19) / 20;
            player.displayClientMessage(
                    Component.literal("§c静电爆炸冷却中，剩余 " + remaining + " 秒"), true);
            return;
        }
        blastCooldown.put(player.getUUID(), now + BLAST_COOLDOWN_TICKS);

        // 定身 2 秒：127 级减速（参考足立透定身实现）
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, BLAST_ROOT_TICKS, 127, false, false, true));

        pendingBlasts.put(player.getUUID(), now + BLAST_ROOT_TICKS);

        // 镜像到组件供 HUD 显示冷却与蓄力进度
        ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(player);
        comp.blastReadyAtTick = now + BLAST_COOLDOWN_TICKS;
        comp.blastPendingUntilTick = now + BLAST_ROOT_TICKS;
        comp.sync();

        player.displayClientMessage(
                Component.literal("§c§l⚡ 静电爆炸蓄力中（2 秒后引爆）！"), true);
    }

    /** 服务端每 tick 处理 */
    public static void serverTick(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();

        // 清理过期方块
        electrifiedBlocks.entrySet().removeIf(e -> e.getValue() < now);

        // 清理过期高亮
        highlightedPlayers.entrySet().removeIf(e -> e.getValue() < now);

        // 处理待引爆的静电爆炸
        var blastIt = pendingBlasts.entrySet().iterator();
        while (blastIt.hasNext()) {
            var entry = blastIt.next();
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster == null || !GameUtils.isPlayerAliveAndSurvival(caster)) {
                blastIt.remove();
                // 蓄力者死亡：清除蓄力显示（离线时组件随重置事件清理）
                if (caster != null) {
                    ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(caster);
                    if (comp.blastPendingUntilTick != -1L) {
                        comp.blastPendingUntilTick = -1L;
                        comp.sync();
                    }
                }
                continue;
            }
            if (caster.tickCount >= entry.getValue()) {
                detonateBlast(caster);
                blastIt.remove();
            }
        }

        // 遍历所有在线玩家
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;

            ElectricManiacPlayerComponent comp = ModComponents.ELECTRIC_MANIAC.get(player);
            boolean isElectricManiac = isElectricManiac(player);

            // 检查静电方块影响（电击狂人自身免疫；已带电者跳过，避免多方块重复施加/刷新时长）
            if (!isElectricManiac && !comp.electrified) {
                String dimStr = player.level().dimension().location().toString();
                synchronized (electrifiedBlocks) {
                    for (var blockEntry : electrifiedBlocks.entrySet()) {
                        String key = blockEntry.getKey();
                        String keyDim = key.substring(0, key.indexOf('|'));
                        if (!keyDim.equals(dimStr)) continue;
                        long posLong = Long.parseLong(key.substring(key.indexOf('|') + 1));
                        BlockPos pos = BlockPos.of(posLong);
                        if (player.distanceToSqr(pos.getCenter()) <= BLOCK_AURA_RANGE * BLOCK_AURA_RANGE) {
                            comp.electrified = true;
                            comp.electrifiedUntilTick = player.tickCount + PLAYER_DURATION_TICKS;
                            comp.sync();
                            break;
                        }
                    }
                }
            }

            // 静电过期检查
            if (comp.electrified && comp.electrifiedUntilTick > 0
                    && player.tickCount > comp.electrifiedUntilTick) {
                comp.electrified = false;
                comp.electrifiedUntilTick = -1L;
                comp.sync();
            }
        }

        // 检查静电玩家相互触发
        List<ServerPlayer> electrifiedPlayers = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            ElectricManiacPlayerComponent c = ModComponents.ELECTRIC_MANIAC.get(p);
            if (c.electrified) electrifiedPlayers.add(p);
        }

        for (int i = 0; i < electrifiedPlayers.size(); i++) {
            for (int j = i + 1; j < electrifiedPlayers.size(); j++) {
                ServerPlayer a = electrifiedPlayers.get(i);
                ServerPlayer b = electrifiedPlayers.get(j);
                if (a.distanceTo(b) < SHOCK_RANGE) {
                    triggerContactShock(a, b);
                }
            }
        }

        // 周期性同步方块（每 2 秒）
        if (now % 40 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                syncBlocks(level);
            }
        }
    }

    /** 引爆静电爆炸：粒子特效 + 球形 32 格内所有玩家触电 */
    private static void detonateBlast(ServerPlayer caster) {
        Vec3 center = caster.position().add(0, 1.0, 0);

        // 清除蓄力状态显示
        ElectricManiacPlayerComponent casterComp = ModComponents.ELECTRIC_MANIAC.get(caster);
        casterComp.blastPendingUntilTick = -1L;
        casterComp.sync();

        // 广播粒子包（同维度玩家）
        ElectricManiacBlastS2CPacket packet = new ElectricManiacBlastS2CPacket(center);
        for (ServerPlayer viewer : PlayerLookup.world((ServerLevel) caster.level())) {
            ServerPlayNetworking.send(viewer, packet);
        }

        // 爆炸音效
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.2F);

        // 球形 32 格内所有存活玩家触电
        List<UUID> shockedUuids = new ArrayList<>();
        double radiusSqr = BLAST_RADIUS * BLAST_RADIUS;
        for (var target : caster.serverLevel().players()) {
            if (target instanceof ServerPlayer serverTarget
                    && GameUtils.isPlayerAliveAndSurvival(serverTarget)
                    && serverTarget.distanceToSqr(center) <= radiusSqr) {
                shockTarget(serverTarget);
                shockedUuids.add(serverTarget.getUUID());
            }
        }

        // 广播触电高亮包（全图所有玩家都能看到高亮）
        if (!shockedUuids.isEmpty()) {
            ElectricManiacShockS2CPacket shockPacket =
                    new ElectricManiacShockS2CPacket(shockedUuids, SHOCK_HIGHLIGHT_TICKS);
            for (ServerPlayer viewer : PlayerLookup.all(caster.getServer())) {
                ServerPlayNetworking.send(viewer, shockPacket);
            }
        }
    }

    /** 两个静电玩家相互接触触发的触电（消耗双方静电） */
    private static void triggerContactShock(ServerPlayer a, ServerPlayer b) {
        // 任一方已在高亮中则防重复触发
        if (highlightedPlayers.containsKey(a.getUUID())
                || highlightedPlayers.containsKey(b.getUUID())) {
            return;
        }

        shockTarget(a);
        shockTarget(b);

        // 清除双方静电（接触触电后消耗）
        ElectricManiacPlayerComponent compA = ModComponents.ELECTRIC_MANIAC.get(a);
        ElectricManiacPlayerComponent compB = ModComponents.ELECTRIC_MANIAC.get(b);
        compA.electrified = false;
        compA.electrifiedUntilTick = -1L;
        compA.sync();
        compB.electrified = false;
        compB.electrifiedUntilTick = -1L;
        compB.sync();

        // 广播触电高亮包
        ElectricManiacShockS2CPacket packet = new ElectricManiacShockS2CPacket(
                List.of(a.getUUID(), b.getUUID()), SHOCK_HIGHLIGHT_TICKS);
        for (ServerPlayer viewer : PlayerLookup.all(a.getServer())) {
            ServerPlayNetworking.send(viewer, packet);
        }
    }

    /** 对单个玩家施加触电效果：减 20 san + 受伤动画 + 尖叫 + 定身 + 电流粒子 + 标记高亮 */
    private static void shockTarget(ServerPlayer target) {
        long gameTime = target.getServer().overworld().getGameTime();
        highlightedPlayers.put(target.getUUID(), gameTime + SHOCK_HIGHLIGHT_TICKS);

        // 减少 20 san 值
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
        mood.addMood(-SHOCK_MOOD_LOSS);

        // 受伤动画（SRE 玩家无限血量，仅播放动画不掉血）
        target.animateHurt(0);

        // 定身 1 秒：127 级减速使玩家完全无法移动（与静电爆炸前摇同方案）
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, SHOCK_ROOT_TICKS, 127, false, false, true));

        // 一瞬间电流粒子：以玩家身体盒为范围爆发一次（sendParticles 自动广播给周围玩家）
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    30,
                    0.45D, target.getBbHeight() * 0.45D, 0.45D,
                    0.0D);
        }

        // 尖叫音效（村民受伤声近似尖叫，触电位置附近玩家可闻）
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.VILLAGER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);

        // 给所有电击狂人私播怪笑（每人 5 秒冷却，防多目标/多人触电重复）
        playShockLaugh(target);
    }

    /**
     * 向所有在线电击狂人私播触电怪笑。
     *
     * <p>原理：用 {@link ClientboundSoundPacket} 只发给电击狂人的客户端连接，
     * 其他玩家听不到；声源坐标取电击狂人自身位置，保证无论触电者多远
     * （静电爆炸半径 32 格）都满音量可闻。具体播 4 个音效中的哪一个由
     * sounds.json 的多 sound 聚合机制随机决定。
     *
     * <p>冷却按聆听者 UUID 独立记录 gameTime：静电爆炸一次可命中多名玩家，
     * shockTarget 会在同一 tick 被调用多次，5 秒冷却保证每个电击狂人最多听到一次。
     */
    private static void playShockLaugh(ServerPlayer shockedTarget) {
        var server = shockedTarget.getServer();
        if (server == null) return;
        long now = server.overworld().getGameTime();

        for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
            if (!isElectricManiac(listener)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(listener)) continue;

            Long last = lastShockSoundTick.get(listener.getUUID());
            if (last != null && now - last < SHOCK_SOUND_COOLDOWN_TICKS) continue;
            lastShockSoundTick.put(listener.getUUID(), now);

            listener.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(Dim201ModSounds.ELECTRIC_SHOCK),
                    SoundSource.PLAYERS,
                    listener.getX(), listener.getY(), listener.getZ(),
                    1.0F,
                    0.95F + listener.getRandom().nextFloat() * 0.1F,
                    listener.getRandom().nextLong()));
        }
    }

    /** 同步静电方块列表给指定维度的所有客户端 */
    private static void syncBlocks(Level level) {
        List<BlockPos> blocks = new ArrayList<>();
        String dimStr = level.dimension().location().toString();
        synchronized (electrifiedBlocks) {
            for (String key : electrifiedBlocks.keySet()) {
                String keyDim = key.substring(0, key.indexOf('|'));
                if (!keyDim.equals(dimStr)) continue;
                long posLong = Long.parseLong(key.substring(key.indexOf('|') + 1));
                blocks.add(BlockPos.of(posLong));
            }
        }
        ElectricManiacBlocksS2CPacket packet = new ElectricManiacBlocksS2CPacket(blocks);
        for (ServerPlayer viewer : PlayerLookup.world((ServerLevel) level)) {
            ServerPlayNetworking.send(viewer, packet);
        }
    }

    /** 判断玩家是否为电击狂人 */
    public static boolean isElectricManiac(ServerPlayer player) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null) return false;
        return gw.isRole(player, ElectricManiacRole.ELECTRIC_MANIAC);
    }

    /**
     * 对局结束清理：清空全部服务端状态，并向所有在线客户端广播空方块列表，
     * 使静电方块透视高亮立即消失（触电高亮/爆炸动画持续时间极短，由客户端自行到期）。
     */
    public static void clearAll(net.minecraft.server.MinecraftServer server) {
        electrifiedBlocks.clear();
        highlightedPlayers.clear();
        chargeCooldown.clear();
        blastCooldown.clear();
        pendingBlasts.clear();
        lastShockSoundTick.clear();

        ElectricManiacBlocksS2CPacket emptyPacket = new ElectricManiacBlocksS2CPacket(List.of());
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer viewer : PlayerLookup.world(level)) {
                ServerPlayNetworking.send(viewer, emptyPacket);
            }
        }
    }
}
