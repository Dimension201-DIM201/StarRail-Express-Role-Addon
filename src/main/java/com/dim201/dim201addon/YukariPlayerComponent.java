package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 八云紫玩家组件
 */
public class YukariPlayerComponent implements RoleComponent {

    public static final ComponentKey<YukariPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "yukari"), YukariPlayerComponent.class);

    private final Player player;

    // ==================== 技能系统 ====================
    public enum Skill {
        TOGGLE_INVISIBLE("进入/离开隙间"),
        USE_CONNECT("境界展开"),
        SHOOT_BOUNDARY_LINE("漂流物");
        
        public final String displayName;
        
        Skill(String displayName) {
            this.displayName = displayName;
        }
    }
    
    public Skill currentSkill = Skill.TOGGLE_INVISIBLE; // 当前选中的技能

    // ==================== 隐身状态 ====================
    public boolean isInvisible = false; // 常态显形，按技能键进入隙间
    public int toggleCooldown = 0; // 切换隐身CD（5秒 = 100 tick）

    /** 隙间状态附加的玩家剔除效果（参考古明地恋本我状态），进入隙间时随隐身一起施加 */
    private static final ResourceLocation PLAYER_ISOLATION_ID =
            ResourceLocation.parse("noellesroles:player_isolation");

    // ==================== 坐标系统 ====================
    public LinkedList<YukariCoordinate> coordinates = new LinkedList<>();
    public static final int MAX_COORDINATES = 10;

    // ==================== 连线系统 ====================
    public int connectCooldown = 0; // 连线CD（30秒 = 600 tick）
    public boolean isConnecting = false; // 是否正在连线
    public int connectDuration = 0; // 连线持续时间

    // ==================== 境界线系统 ====================
    public int boundaryLineCooldown = 0; // 境界线CD（30秒 = 600 tick）
    // 本轮境界线已命中目标（防止同一轮多次命中同一目标）
    public Set<UUID> boundaryLineHitTargets = new HashSet<>();

    // ==================== 神隐层数 ====================
    // 每个玩家的神隐层数：UUID -> 层数
    public Map<String, Integer> shenyInLayers = new ConcurrentHashMap<>();

    // ==================== 境界层数 ====================
    public int realmLevel = 0; // 境界层数（击杀玩家后增加）

    // ==================== 认真模式 ====================
    public boolean isSeriousMode = false; // 是否处于认真模式
    public int seriousModeDuration = 0; // 认真模式剩余时间（tick）
    public static final int SERIOUS_MODE_MAX_DURATION = 1200; // 60秒 = 1200 tick
    public int seriousModeBuyCooldown = 0; // 购买认真模式的冷却（240秒 = 4800 tick）
    public static final int SERIOUS_MODE_BUY_COOLDOWN = 4800; // 240秒

    // ==================== 废线购买冷却 ====================
    public int haisenBuyCooldown = 0; // 废线购买冷却（tick）
    public static final int HAISEN_BUY_COOLDOWN = 1200; // 60秒 = 1200 tick

    // ==================== 记录开局玩家总数（用于计算境界上限）====================
    public int startingPlayerCount = 0;

    // ==================== 角色获得后的tick计数（用于退场判断，替代getGameTime）====================
    public int ticksSinceAssigned = 0; // 获得角色后的tick数

    // 记录上次tick的游戏时间，防止同一tick被调用两次（修复CD快一倍的bug）
    private long lastTickGameTime = -1;

    public YukariPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        currentSkill = Skill.TOGGLE_INVISIBLE;
        isInvisible = false;
        toggleCooldown = 0;
        coordinates.clear();
        connectCooldown = 0;
        isConnecting = false;
        connectDuration = 0;
        boundaryLineCooldown = 0;
        boundaryLineHitTargets.clear();
        shenyInLayers.clear();
        realmLevel = 0;
        isSeriousMode = false;
        seriousModeDuration = 0;
        seriousModeBuyCooldown = 0;
        haisenBuyCooldown = 0;
        ticksSinceAssigned = 0; // 重置计数器
        lastTickGameTime = -1;
        
        // 记录开局玩家数量
        if (player instanceof ServerPlayer sp) {
            startingPlayerCount = sp.getServer().getPlayerList().getPlayers().size();
        }
        
        applyInvisibleState();
        sync();
    }

    @Override
    public void clear() {
        // 清除隐身相关效果
        if (player instanceof ServerPlayer sp) {
            sp.setInvisible(false);
            sp.removeEffect(MobEffects.INVISIBILITY);
            sp.removeEffect(MobEffects.BLINDNESS);
            sp.removeEffect(MobEffects.GLOWING);
            removeIsolationEffect(sp);
        }
        // 停止音乐
        stopSeriousModeMusic();
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    // ==================== 获取境界上限 ====================

    public int getRealmRequiredLevel() {
        return Math.max(1, startingPlayerCount / 2);
    }

    // ==================== 技能切换 ====================

    /**
     * 切换到下一个技能
     */
    public void switchSkill(ServerPlayer player) {
        Skill[] skills = Skill.values();
        int nextIndex = (currentSkill.ordinal() + 1) % skills.length;
        currentSkill = skills[nextIndex];
        sendActionBar(player, "§5§l切换技能 §7| §f" + currentSkill.displayName);
        sync();
    }

    // ==================== 隐身切换 ====================

    public boolean canUseToggle() {
        return toggleCooldown <= 0;
    }

    public void toggleInvisible(ServerPlayer player) {
        isInvisible = !isInvisible;
        toggleCooldown = isSeriousMode ? 40 : 100; // 认真模式2秒，正常5秒
        applyInvisibleState();
        
        if (isInvisible) {
            sendActionBar(player, "§5§l进入隙间 §7| §f视野受限");
        } else {
            sendActionBar(player, "§d§l显形 §7| §f恢复正常状态");
        }
        
        sync();
    }

    /**
     * 强制显形（用于连线/境界线时）
     */
    public void forceVisible(ServerPlayer player) {
        // 无论当前是否隐身，都设置切换CD
        toggleCooldown = isSeriousMode ? 40 : 200; // 认真模式2秒，正常10秒
        
        if (isInvisible) {
            isInvisible = false;
            applyInvisibleState();
            sendActionBar(player, "§5§l境界展开 §7| §f强制显形，切换冷却" + (isSeriousMode ? "2" : "10") + "秒");
        } else {
            // 已经是显形状态，只刷新CD
            sendActionBar(player, "§5§l境界展开 §7| §f切换冷却" + (isSeriousMode ? "2" : "10") + "秒");
        }
        
        sync();
    }

    public void applyInvisibleState() {
        if (player instanceof ServerPlayer sp) {
            sp.setInvisible(isInvisible);

            if (isInvisible) {
                // 进入隙间：隐身（让其他玩家看不到）
                sp.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    999999,
                    0,
                    false,
                    false,
                    false
                ));
                // 玩家剔除效果：彻底从其他玩家的视野与交互中消失（参考古明地恋本我状态）
                applyIsolationEffect(sp);
                // 视野受限：高级别失明
                sp.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    999999,
                    9, // 等级10（0-based index 9），最高级别失明
                    false,
                    false,
                    false
                ));
                // 移除发光
                sp.removeEffect(MobEffects.GLOWING);
            } else {
                // 显形：移除隐身、剔除效果和失明
                sp.removeEffect(MobEffects.INVISIBILITY);
                removeIsolationEffect(sp);
                sp.removeEffect(MobEffects.BLINDNESS);
                // 认真模式时添加发光
                if (isSeriousMode) {
                    sp.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING,
                        999999,
                        0,
                        false,
                        false,
                        false
                    ));
                }
            }
        }
    }

    /** 施加玩家剔除效果（noellesroles:player_isolation），效果不存在时静默跳过 */
    private void applyIsolationEffect(ServerPlayer sp) {
        BuiltInRegistries.MOB_EFFECT.getHolder(PLAYER_ISOLATION_ID)
                .ifPresent(holder -> sp.addEffect(new MobEffectInstance(
                        holder, 999999, 0, false, false, false)));
    }

    /** 移除玩家剔除效果 */
    private void removeIsolationEffect(ServerPlayer sp) {
        BuiltInRegistries.MOB_EFFECT.getHolder(PLAYER_ISOLATION_ID)
                .ifPresent(sp::removeEffect);
    }

    // ==================== 认真模式 ====================

    /**
     * 激活认真模式
     */
    public void activateSeriousMode(ServerPlayer player) {
        if (isSeriousMode) {
            sendActionBar(player, "§c§l已经在认真模式中了！");
            return;
        }
        
        if (seriousModeBuyCooldown > 0) {
            int cd = (int) Math.ceil(seriousModeBuyCooldown / 20.0);
            sendActionBar(player, "§c§l购买冷却中 §f" + cd + "秒");
            return;
        }
        
        isSeriousMode = true;
        seriousModeDuration = SERIOUS_MODE_MAX_DURATION; // 60秒
        seriousModeBuyCooldown = SERIOUS_MODE_BUY_COOLDOWN; // 240秒购买冷却
        
        // 强制显形
        if (isInvisible) {
            isInvisible = false;
            applyInvisibleState();
        }
        
        // 添加发光效果
        player.addEffect(new MobEffectInstance(
            MobEffects.GLOWING,
            999999,
            0,
            false,
            false,
            false
        ));
        
        // 给予1层护盾（使用SRE的护甲系统）
        SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(player);
        armorComp.giveArmor();
        
        // 重置所有技能冷却
        toggleCooldown = 0;
        connectCooldown = 0;
        boundaryLineCooldown = 0;
        
        // 播放音乐给所有人
        playSeriousModeMusic(player);
        
        sendActionBar(player, "§5§l是时候认真一下了！ §7| §f持续60秒，技能冷却大幅减少");
        sync();
    }

    /**
     * 播放认真模式音乐给所有玩家
     */
    private void playSeriousModeMusic(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        
        // 使用自定义声音事件播放音乐
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            target.playNotifySound(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "serious_mode")
                ),
                SoundSource.PLAYERS,
                0.2f, // 音量0.2
                1.0f
            );
        }
    }

    /**
     * 停止认真模式音乐
     */
    private void stopSeriousModeMusic() {
        if (!(player instanceof ServerPlayer sp)) return;
        var server = sp.getServer();
        if (server == null) return;
        
        // 停止音乐
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            target.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "serious_mode"),
                SoundSource.PLAYERS
            ));
        }
    }

    /**
     * 结束认真模式
     */
    private void endSeriousMode(ServerPlayer player) {
        isSeriousMode = false;
        seriousModeDuration = 0;
        
        // 移除发光
        player.removeEffect(MobEffects.GLOWING);
        
        // 停止音乐
        stopSeriousModeMusic();
        
        sendActionBar(player, "§5§l认真模式结束");
        sync();
    }

    // ==================== 坐标系统 ====================

    public void addCoordinate(ServerPlayer player, double x, double y, double z, String level) {
        // FIFO：如果达到上限，移除最早的
        if (coordinates.size() >= MAX_COORDINATES) {
            coordinates.pollFirst();
        }

        coordinates.addLast(new YukariCoordinate(x, y, z, level));
        sendActionBar(player, "§5§l设置坐标 §7| §f" + coordinates.size() + "/10");
        sync();
    }

    // ==================== 连线系统 ====================

    public boolean canUseConnect() {
        return connectCooldown <= 0 && !isConnecting && coordinates.size() >= 2;
    }

    public void useConnect(ServerPlayer player) {
        if (coordinates.size() < 2) {
            sendActionBar(player, "§c§l坐标不足！§f至少需要2个坐标");
            return;
        }
        
        // 强制显形
        forceVisible(player);
        
        isConnecting = true;
        connectDuration = 40; // 2秒 = 40 tick
        connectCooldown = isSeriousMode ? 40 : 600; // 认真模式2秒，正常30秒
        
        sendActionBar(player, "§5§l境界展开 §7| §f连线持续2秒");
        sync();
    }

    // ==================== 境界线系统 ====================

    public boolean canUseBoundaryLine() {
        return boundaryLineCooldown <= 0;
    }

    /**
     * 使用境界线：在身体左右各射出2条境界线
     */
    public void useBoundaryLine(ServerPlayer player) {
        if (!canUseBoundaryLine()) {
            int cd = (int) Math.ceil(boundaryLineCooldown / 20.0);
            sendActionBar(player, "§c§l漂流物冷却中 §f" + cd + "秒");
            return;
        }
        
        // 强制显形并设置切换CD
        forceVisible(player);
        
        boundaryLineCooldown = isSeriousMode ? 40 : 600; // 认真模式2秒，正常30秒
        
        // 清空本轮命中记录
        boundaryLineHitTargets.clear();
        
        // 射出4条境界线（左右各2条）
        shootBoundaryLines(player);
        
        sendActionBar(player, "§5§l漂流物 §7| §f4条漂流物已射出");
        sync();
    }

    /**
     * 射出境界线
     */
    private void shootBoundaryLines(ServerPlayer player) {
        var serverLevel = player.serverLevel();
        Vec3 pos = player.position().add(0, player.getEyeHeight() * 0.5, 0);
        Vec3 look = player.getLookAngle().normalize();
        
        // 计算左右方向（垂直于视线方向）
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up).normalize();
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        }
        
        // 左右各2条，偏移角度
        double[] offsets = {-0.3, -0.1, 0.1, 0.3}; // 左右偏移量
        
        for (double offset : offsets) {
            Vec3 direction = look.add(right.scale(offset)).normalize();
            shootSingleLine(serverLevel, pos, direction, player);
        }
    }

    /**
     * 射出单条境界线
     */
    private void shootSingleLine(net.minecraft.server.level.ServerLevel level, 
            Vec3 start, Vec3 direction, ServerPlayer shooter) {
        double maxDistance = 20.0; // 最大射程20格
        int steps = (int) (maxDistance * 2); // 每0.5格检查一次
        
        Vec3 current = start;
        
        for (int i = 0; i <= steps; i++) {
            current = start.add(direction.scale(i * 0.5));
            
            // 生成粒子效果
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.WITCH,
                current.x, current.y, current.z,
                1, 0, 0, 0, 0
            );
            
            // 检查是否命中玩家
            for (ServerPlayer target : level.getServer().getPlayerList().getPlayers()) {
                if (target == shooter) continue;
                if (!target.isAlive()) continue;
                if (isSpectator(target)) continue; // 跳过观察者模式玩家
                if (boundaryLineHitTargets.contains(target.getUUID())) continue; // 本轮已命中，跳过
                if (target.distanceToSqr(current.x, current.y, current.z) < 1.5) { // 命中判定范围
                    // 增加神隐层数
                    addShenyInLayer(target);
                    boundaryLineHitTargets.add(target.getUUID()); // 标记为已命中
                    break; // 一条线只命中一个目标
                }
            }
            
            // 检查是否撞到方块（减少碰撞判定：只在整数坐标点检查，且忽略可穿过方块）
            if (i > 0 && i % 2 == 0) { // 每1格检查一次方块（原来是每0.5格）
                var blockPos = net.minecraft.core.BlockPos.containing(current.x, current.y, current.z);
                var blockState = level.getBlockState(blockPos);
                
                if (!blockState.isAir()) {
                    // 检查是否是可穿过的方块（草、花、藤蔓、水等）
                    if (blockState.getCollisionShape(level, blockPos).isEmpty()) {
                        continue; // 可穿过，继续飞行
                    }
                    
                    // 检查是否是半透明/非固体方块（玻璃、树叶、栅栏等）
                    if (!blockState.isSolid()) {
                        continue; // 非固体方块，继续飞行
                    }
                    
                    // 只有碰到真正的固体方块才停止
                    break;
                }
            }
        }
    }

    // ==================== 神隐层数 ====================

    /**
     * 检查玩家是否为观察者模式（无法被神隐）
     */
    private boolean isSpectator(ServerPlayer target) {
        return target.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
            || target.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    public void addShenyInLayer(ServerPlayer target) {
        // 观察者模式玩家无法被神隐
        if (isSpectator(target)) return;

        var gameWorld = SREGameWorldComponent.KEY.get(target.level());
        if (gameWorld == null) return;

        // 博丽灵梦免疫神隐
        if (gameWorld.isRole(target, ReimuRole.REIMU)) {
            return;
        }

        // 杀手队友免疫神隐：神隐只作用于非杀手阵营目标
        SRERole targetRole = gameWorld.getRole(target);
        if (targetRole != null && gameWorld.isKillerTeamRole(targetRole)) {
            return;
        }

        String uuid = target.getUUID().toString();
        int current = shenyInLayers.getOrDefault(uuid, 0);
        current++;
        shenyInLayers.put(uuid, current);
        
        // 发送提示给目标
        target.displayClientMessage(
            Component.literal("§5§l神隐层数: §c" + current + "/5"), true);
        
        // 达到5层，死亡
        if (current >= 5) {
            // 清除层数
            shenyInLayers.remove(uuid);
            
            // 使用SRE死亡系统，神隐，无尸体无掉落
            killWithShenyIn(target);
            
            // 增加境界层数
            realmLevel++;
            
            // 提示Yukari
            if (player instanceof ServerPlayer sp) {
                sendActionBar(sp, "§5§l神隐成功！§f境界层数: " + realmLevel);
            }
        }
        
        sync();
    }

    private void killWithShenyIn(ServerPlayer target) {
        // 先清除掉落物
        target.getInventory().clearContent();
        
        // 使用SRE死亡系统
        io.wifi.starrailexpress.game.GameUtils.killPlayer(
            target, false, (ServerPlayer) player, YukariRole.DEATH_REASON_SHENYIN
        );
    }

    // ==================== 坐标清除（供博丽灵梦调用）====================

    /**
     * 清除所有坐标（供博丽灵梦的梦符结界使用）
     */
    public void clearCoordinates() {
        coordinates.clear();
        sync();
    }

    // ==================== Tick 处理（由角色的 serverTick 调用）====================

    /**
     * 每tick调用一次（由 YukariRoleImpl.serverTick 调用）
     */
    public void tick(ServerPlayer sp) {
        // 防止同一游戏tick被调用两次（修复CD快一倍的bug）
        long currentTime = sp.serverLevel().getGameTime();
        if (currentTime == lastTickGameTime) {
            return;
        }
        lastTickGameTime = currentTime;
        
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return;
        if (!gameWorld.isRole(player, YukariRole.YUKARI)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            // 如果处于认真模式且死亡，停止音乐
            if (isSeriousMode) {
                endSeriousMode(sp);
            }
            return;
        }

        // 自增获得角色后的tick计数（用于退场时间判断）
        ticksSinceAssigned++;

        // 认真模式购买冷却
        if (seriousModeBuyCooldown > 0) {
            seriousModeBuyCooldown--;
        }

        // 废线购买冷却
        if (haisenBuyCooldown > 0) {
            haisenBuyCooldown--;
            if (haisenBuyCooldown % 20 == 0 || haisenBuyCooldown == 0) {
                sync();
            }
        }

        // 认真模式倒计时
        if (isSeriousMode) {
            seriousModeDuration--;
            if (seriousModeDuration <= 0) {
                endSeriousMode(sp);
            } else if (seriousModeDuration % 20 == 0) {
                sync();
            }
        }

        // 切换CD
        if (toggleCooldown > 0) {
            toggleCooldown--;
            if (toggleCooldown % 20 == 0 || toggleCooldown == 0) sync();
        }

        // 连线CD
        if (connectCooldown > 0) {
            connectCooldown--;
            if (connectCooldown % 20 == 0 || connectCooldown == 0) sync();
        }

        // 境界线CD
        if (boundaryLineCooldown > 0) {
            boundaryLineCooldown--;
            if (boundaryLineCooldown % 20 == 0 || boundaryLineCooldown == 0) sync();
        }

        // 连线持续
        if (isConnecting) {
            connectDuration--;
            
            // 生成粒子效果
            spawnConnectParticles(sp);
            
            // 检测玩家在连线上
            checkPlayersOnLine(sp);
            
            if (connectDuration <= 0) {
                isConnecting = false;
                sendActionBar(sp, "§5§l连线结束");
                sync();
            }
        }
        
        // 每20 tick同步HUD
        if (player.tickCount % 20 == 0) {
            sync();
        }
    }

    /**
     * 生成连线粒子（坐标之间两两连线）
     */
    private void spawnConnectParticles(ServerPlayer player) {
        if (coordinates.size() < 2) return;
        
        var serverLevel = player.serverLevel();
        
        // 两两连线
        for (int i = 0; i < coordinates.size(); i++) {
            for (int j = i + 1; j < coordinates.size(); j++) {
                YukariCoordinate c1 = coordinates.get(i);
                YukariCoordinate c2 = coordinates.get(j);
                
                // 只连接同一维度的坐标
                if (!c1.level.equals(c2.level)) continue;
                if (!c1.level.equals(serverLevel.dimension().location().toString())) continue;
                
                // 生成紫色粒子连线
                spawnLineParticles(serverLevel, c1.x, c1.y, c1.z, c2.x, c2.y, c2.z);
            }
        }
    }

    private void spawnLineParticles(net.minecraft.server.level.ServerLevel level, 
            double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (distance * 2); // 每0.5格一个粒子
        
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            double z = z1 + dz * t;
            
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.WITCH, // 紫色粒子
                x, y, z,
                1, 0, 0, 0, 0
            );
        }
    }

    /**
     * 检测玩家是否在连线上
     */
    private void checkPlayersOnLine(ServerPlayer yukari) {
        var server = yukari.getServer();
        if (server == null) return;
        
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (target == yukari) continue; // 不能自己站在连线上增加层数
            if (!target.isAlive()) continue;
            if (isSpectator(target)) continue; // 跳过观察者模式玩家
            
            // 检查目标是否在任意连线上
            boolean onLine = false;
            for (int i = 0; i < coordinates.size() && !onLine; i++) {
                for (int j = i + 1; j < coordinates.size() && !onLine; j++) {
                    YukariCoordinate c1 = coordinates.get(i);
                    YukariCoordinate c2 = coordinates.get(j);
                    
                    if (!c1.level.equals(c2.level)) continue;
                    if (!c1.level.equals(target.level().dimension().location().toString())) continue;
                    
                    if (isPointOnLine(target.getX(), target.getY(), target.getZ(), 
                            c1.x, c1.y, c1.z, c2.x, c2.y, c2.z)) {
                        onLine = true;
                    }
                }
            }
            
            if (onLine && target.tickCount % 20 == 0) {
                addShenyInLayer(target);
            }
        }
    }

    /**
     * 判断点是否在线段附近（距离小于2格）
     */
    private boolean isPointOnLine(double px, double py, double pz,
            double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        
        double lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq == 0) {
            double distSq = (px - x1) * (px - x1) + (py - y1) * (py - y1) + (pz - z1) * (pz - z1);
            return distSq <= 4.0;
        }
        
        double t = ((px - x1) * dx + (py - y1) * dy + (pz - z1) * dz) / lenSq;
        t = Math.max(0, Math.min(1, t));
        
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        double closestZ = z1 + t * dz;
        
        double distSq = (px - closestX) * (px - closestX) 
                      + (py - closestY) * (py - closestY) 
                      + (pz - closestZ) * (pz - closestZ);
        
        return distSq <= 4.0;
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isInvisible", isInvisible);
        tag.putInt("toggleCooldown", toggleCooldown);
        tag.putInt("connectCooldown", connectCooldown);
        tag.putInt("boundaryLineCooldown", boundaryLineCooldown);
        tag.putInt("realmLevel", realmLevel);
        tag.putInt("startingPlayerCount", startingPlayerCount);
        tag.putInt("currentSkill", currentSkill.ordinal());
        tag.putBoolean("isSeriousMode", isSeriousMode);
        tag.putInt("seriousModeDuration", seriousModeDuration);
        tag.putInt("seriousModeBuyCooldown", seriousModeBuyCooldown);
        tag.putInt("haisenBuyCooldown", haisenBuyCooldown);
        tag.putInt("ticksSinceAssigned", ticksSinceAssigned);
        tag.putInt("coordinateCount", coordinates.size());

        // 坐标列表：使用有序 ListTag（不能用嵌套 CompoundTag + 数字键，
        // 那种写法经 CCA 同步往返会丢条目且打乱 FIFO 顺序）
        ListTag coordList = new ListTag();
        for (YukariCoordinate c : coordinates) {
            CompoundTag cTag = new CompoundTag();
            cTag.putDouble("x", c.x);
            cTag.putDouble("y", c.y);
            cTag.putDouble("z", c.z);
            cTag.putString("level", c.level);
            coordList.add(cTag);
        }
        tag.put("coordinates", coordList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        isInvisible = tag.getBoolean("isInvisible");
        toggleCooldown = tag.getInt("toggleCooldown");
        connectCooldown = tag.getInt("connectCooldown");
        boundaryLineCooldown = tag.contains("boundaryLineCooldown") ? tag.getInt("boundaryLineCooldown") : 0;
        realmLevel = tag.getInt("realmLevel");
        startingPlayerCount = tag.contains("startingPlayerCount") ? tag.getInt("startingPlayerCount") : 0;
        
        isSeriousMode = tag.contains("isSeriousMode") ? tag.getBoolean("isSeriousMode") : false;
        seriousModeDuration = tag.contains("seriousModeDuration") ? tag.getInt("seriousModeDuration") : 0;
        seriousModeBuyCooldown = tag.contains("seriousModeBuyCooldown") ? tag.getInt("seriousModeBuyCooldown") : 0;
        haisenBuyCooldown = tag.contains("haisenBuyCooldown") ? tag.getInt("haisenBuyCooldown") : 0;
        ticksSinceAssigned = tag.contains("ticksSinceAssigned") ? tag.getInt("ticksSinceAssigned") : 0;
        
        if (tag.contains("currentSkill")) {
            int skillOrdinal = tag.getInt("currentSkill");
            Skill[] skills = Skill.values();
            if (skillOrdinal >= 0 && skillOrdinal < skills.length) {
                currentSkill = skills[skillOrdinal];
            }
        }
        
        coordinates.clear();
        if (tag.contains("coordinates", CompoundTag.TAG_LIST)) {
            ListTag coordList = tag.getList("coordinates", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < coordList.size(); i++) {
                CompoundTag cTag = coordList.getCompound(i);
                coordinates.add(new YukariCoordinate(
                    cTag.getDouble("x"),
                    cTag.getDouble("y"),
                    cTag.getDouble("z"),
                    cTag.getString("level")
                ));
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }
}