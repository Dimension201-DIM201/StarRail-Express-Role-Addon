package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 张老师玩家组件
 * 
 * 功能：
 * - 追踪移动距离，达到3000格时获胜
 * - 技能：按下技能键获得20秒速度V，冷却120秒
 * - 商店：碳酸饮料（随机效果+死亡概率）、巧克力冰激凌（免疫死亡）
 */
public class TeacherZhangPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<TeacherZhangPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "teacher_zhang"), TeacherZhangPlayerComponent.class);

    private final Player player;
    private SREGameWorldComponent gameWorldComponent = null;

    // ==================== 移动距离追踪 ====================
    
    public double totalDistance = 0.0;
    private double lastX = 0;
    private double lastZ = 0;
    private boolean positionInitialized = false;
    public boolean hasWon = false;

    // ==================== 技能系统 ====================
    
    /** 技能冷却时间（120秒 = 2400 tick） */
    public static final int ABILITY_COOLDOWN = 2400;
    /** 速度效果持续时间（20秒 = 400 tick） */
    public static final int SPEED_DURATION = 400;
    /** 速度效果等级（4 = Speed V） */
    public static final int SPEED_AMPLIFIER = 4;

    /** 技能冷却剩余 tick */
    public int cooldown = 0;
    /** 速度效果剩余 tick */
    public int speedTicks = 0;
    /** 是否正在疾跑中 */
    public boolean isSprinting = false;

    // ==================== 商店系统 ====================
    
    /** 碳酸饮料购买次数（用于计算死亡概率） */
    public int sodaBuyCount = 0;
    /** 是否有冰激凌免疫 */
    public boolean hasIceCreamImmunity = false;

    public TeacherZhangPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        // 移动距离
        this.totalDistance = 0.0;
        this.positionInitialized = false;
        this.hasWon = false;
        this.lastX = player.getX();
        this.lastZ = player.getZ();
        
        // 技能
        this.cooldown = 1200; // 开局60秒冷却
        this.speedTicks = 0;
        this.isSprinting = false;
        
        // 商店
        this.sodaBuyCount = 0;
        this.hasIceCreamImmunity = false;
        
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 发送 ActionBar 消息
     */
    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    // ==================== 技能方法 ====================

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        return cooldown <= 0 && !isSprinting;
    }

    /**
     * 使用疾跑技能
     */
    public boolean useAbility() {
        // 冷却中，提示剩余时间
        if (cooldown > 0) {
            if (player instanceof ServerPlayer serverPlayer) {
                int seconds = (int) Math.ceil(cooldown / 20.0);
                sendActionBar(serverPlayer, "§c§l技能冷却中 §f" + seconds + "秒");
            }
            return false;
        }

        // 正在冲刺中
        if (isSprinting) {
            if (player instanceof ServerPlayer serverPlayer) {
                int seconds = (int) Math.ceil(speedTicks / 20.0);
                sendActionBar(serverPlayer, "§e§l冲刺中 §f" + seconds + "秒剩余");
            }
            return false;
        }

        // 验证是张老师角色
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRole(player, TeacherZhangRole.TEACHER_ZHANG)) {
            return false;
        }

        // 应用速度效果（无粒子，不显示图标）
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                SPEED_DURATION,
                SPEED_AMPLIFIER,
                true,   // ambient
                false,  // showParticles
                false   // showIcon
        ));

        this.isSprinting = true;
        this.speedTicks = SPEED_DURATION;
        this.cooldown = ABILITY_COOLDOWN;

        if (player instanceof ServerPlayer serverPlayer) {
            sendActionBar(serverPlayer, "§a§l冲刺！§f20秒极速奔跑！");
        }

        this.sync();
        return true;
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 获取速度效果剩余时间（秒）
     */
    public float getSpeedSeconds() {
        return speedTicks / 20.0f;
    }

    // ==================== 移动检测 ====================

    private boolean checkIsGameRunning() {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        return gameWorldComponent != null 
            && gameWorldComponent.gameStatus == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    // ==================== Server Tick ====================

    @Override
    public void serverTick() {
        if (!checkIsGameRunning()) return;
        if (!(player instanceof ServerPlayer sp)) return;
        if (!gameWorldComponent.isRole(player, TeacherZhangRole.TEACHER_ZHANG)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // ---- 技能 tick 处理 ----
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 20 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        if (this.speedTicks > 0) {
            this.speedTicks--;
            if (this.speedTicks <= 0) {
                this.isSprinting = false;
                sendActionBar(sp, "§e冲刺结束");
                this.sync();
            }
        }

        // ---- 移动距离检测 ----
        if (hasWon) return;

        double currentX = player.getX();
        double currentZ = player.getZ();

        if (!positionInitialized) {
            lastX = currentX;
            lastZ = currentZ;
            positionInitialized = true;
            return;
        }

        double dx = currentX - lastX;
        double dz = currentZ - lastZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        // 防传送刷分：单次移动超过 1000 格判定为传送/技能位移，不累计
        if (distance > 1000.0) {
            lastX = currentX;
            lastZ = currentZ;
            return;
        }

        totalDistance += distance;

        lastX = currentX;
        lastZ = currentZ;

        // 检查获胜
        if (totalDistance >= TeacherZhangRole.WIN_DISTANCE && !this.hasWon) {
            hasWon = true;
            TeacherZhangRole.triggerWin(sp);
            this.sync();
            return;
        }

        // 每100格提示
        if ((int)(totalDistance / 100) > (int)((totalDistance - distance) / 100)) {
            int remaining = (int)(TeacherZhangRole.WIN_DISTANCE - totalDistance);
            sendActionBar(sp, "§e已移动 " + (int)totalDistance + " 格，还需 " + remaining + " 格获胜");
        }

        // 每20 tick同步
        if (player.tickCount % 20 == 0) {
            this.sync();
        }
    }

    // ==================== Client Tick ====================

    @Override
    public void clientTick() {
        if (!checkIsGameRunning()) return;
        if (!gameWorldComponent.isRole(player, TeacherZhangRole.TEACHER_ZHANG)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 技能倒计时
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.speedTicks > 0) {
            this.speedTicks--;
        }

        // 移动距离
        double currentX = player.getX();
        double currentZ = player.getZ();

        if (!positionInitialized) {
            lastX = currentX;
            lastZ = currentZ;
            positionInitialized = true;
            return;
        }

        double dx = currentX - lastX;
        double dz = currentZ - lastZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        // 防传送刷分：单次移动超过 1000 格判定为传送/技能位移，不累计
        if (distance > 1000.0) {
            lastX = currentX;
            lastZ = currentZ;
            return;
        }

        totalDistance += distance;
        lastX = currentX;
        lastZ = currentZ;
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putDouble("totalDistance", this.totalDistance);
        tag.putBoolean("hasWon", this.hasWon);
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("speedTicks", this.speedTicks);
        tag.putBoolean("isSprinting", this.isSprinting);
        tag.putInt("sodaBuyCount", this.sodaBuyCount);
        tag.putBoolean("hasIceCreamImmunity", this.hasIceCreamImmunity);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.totalDistance = tag.getDouble("totalDistance");
        this.hasWon = tag.getBoolean("hasWon");
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.speedTicks = tag.contains("speedTicks") ? tag.getInt("speedTicks") : 0;
        this.isSprinting = tag.contains("isSprinting") && tag.getBoolean("isSprinting");
        this.sodaBuyCount = tag.contains("sodaBuyCount") ? tag.getInt("sodaBuyCount") : 0;
        this.hasIceCreamImmunity = tag.contains("hasIceCreamImmunity") && tag.getBoolean("hasIceCreamImmunity");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}