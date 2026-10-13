package com.dim201.dim201addon;

import com.dim201.dim201addon.item.card.CardTeamKillPunisher;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 博丽灵梦玩家组件
 */
public class ReimuPlayerComponent implements RoleComponent {

    public static final ComponentKey<ReimuPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "reimu"), ReimuPlayerComponent.class);

    private final Player player;

    // ==================== 灵气系统 ====================
    public int reimuPower = 0; // 当前灵气层数
    public static final int MAX_REIMU_POWER = 5; // 灵气上限
    public double distanceTraveled = 0.0; // 累计移动距离
    private Vec3 lastPos = null; // 上次位置

    // ==================== 封魔针冷却 ====================
    public int sealingNeedleCooldown = 0; // 封魔针冷却（tick）
    public static final int SEALING_NEEDLE_COOLDOWN = 200; // 10秒 = 200 tick

    // ==================== 二重结界冷却 ====================
    public int dreamBarrierCooldown = 0; // 二重结界冷却（tick）
    public static final int DREAM_BARRIER_COOLDOWN = 2400; // 120秒 = 2400 tick

    // 记录上次tick的游戏时间
    private long lastTickGameTime = -1;

    public ReimuPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        reimuPower = 0;
        distanceTraveled = 0.0;
        lastPos = null;
        sealingNeedleCooldown = 0;
        dreamBarrierCooldown = 0;
        lastTickGameTime = -1;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    // ==================== 灵气获取 ====================

    /**
     * 每tick计算移动距离，每100格获得1层灵气
     * 灵气达到上限后不再累计步数
     */
    public void tick(ServerPlayer player) {
        // 防止同一游戏tick被调用两次
        long currentTime = player.serverLevel().getGameTime();
        if (currentTime == lastTickGameTime) {
            return;
        }
        lastTickGameTime = currentTime;

        if (!Dim201Addon.isInGameAlive(player)) return;

        // 封魔针冷却
        if (sealingNeedleCooldown > 0) {
            sealingNeedleCooldown--;
            if (sealingNeedleCooldown % 20 == 0 || sealingNeedleCooldown == 0) {
                sync();
            }
        }

        // 二重结界冷却
        if (dreamBarrierCooldown > 0) {
            dreamBarrierCooldown--;
            if (dreamBarrierCooldown % 20 == 0 || dreamBarrierCooldown == 0) {
                sync();
            }
        }

        // 灵气未达到上限时才计算移动距离
        if (reimuPower < MAX_REIMU_POWER) {
            Vec3 currentPos = player.position();
            if (lastPos != null) {
                double moved = currentPos.distanceTo(lastPos);
                distanceTraveled += moved;
                
                // 每100格获得1层灵气
                while (distanceTraveled >= 100.0 && reimuPower < MAX_REIMU_POWER) {
                    distanceTraveled -= 100.0;
                    reimuPower++;
                    sendActionBar(player, "§e§l灵气增加！§f当前: " + reimuPower + "/" + MAX_REIMU_POWER);
                }
                
                // 如果灵气达到上限，重置 distanceTraveled 为0，防止超过100%
                if (reimuPower >= MAX_REIMU_POWER) {
                    distanceTraveled = 0.0;
                }
            }
            lastPos = currentPos;
        }

        // 每20 tick同步HUD
        if (player.tickCount % 20 == 0) {
            sync();
        }
    }

    // ==================== 技能1：封魔针 ====================

    /**
     * 消耗1灵气，沿视线方向射出1根封魔针
     * 碰到玩家即死，误杀平民自己死亡
     * 10秒冷却
     * 【2026-08-11】 用户：改为空手右键即可触发，并且可以穿过所有门（门方块不挡道）
     */
    public boolean useSealingNeedle(ServerPlayer player) {
        if (sealingNeedleCooldown > 0) {
            int cd = (int) Math.ceil(sealingNeedleCooldown / 20.0);
            if (cd <= 0) cd = 1; // 冷却 <1s 时提示"1秒"（避免显示"0秒"迷惑用户）
            sendActionBar(player, "封魔针冷却中 - 剩 " + cd + " 秒");
            return false;
        }

        if (reimuPower < 1) {
            sendActionBar(player, "灵气不足！需要 1 层，当前 " + reimuPower + " 层（移动每 100 格 +1 层，上限 5）");
            return false;
        }

        reimuPower--;
        sealingNeedleCooldown = SEALING_NEEDLE_COOLDOWN; // 10秒冷却
        sendActionBar(player, "§c§l封魔针！§f剩余灵气: " + reimuPower + "/" + MAX_REIMU_POWER);
        sync();

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle().normalize();
        
        // 沿视线方向射出1根封魔针
        shootNeedle(player, eyePos, look);

        return true;
    }

    /**
     * 判断当前方块位置是否是"任意门"——如果是，则封魔针直接穿过不拦截（用户要求）
     * 注：描述中不要出现 SRE 字样；代码内部基于命名空间+路径双保险
     */
    private static boolean isDoorBlockShouldPenetrate(@NotNull net.minecraft.world.level.Level level,
                                                      @NotNull BlockPos blockPos,
                                                      @NotNull BlockState blockState) {
        return com.dim201.dim201addon.util.DoorPenetrationHelper.isDoorBlock(level, blockPos, blockState);
    }

    private void shootNeedle(ServerPlayer shooter, Vec3 start, Vec3 direction) {
        var server = shooter.getServer();
        if (server == null) return;
        var level = shooter.serverLevel();

        // ===== 发射音效 =====
        level.playSound(
            null, // null = 给所有玩家播放
            start.x, start.y, start.z,
            SoundEvents.ARROW_SHOOT, // 射箭音效，类似封魔针发射
            SoundSource.PLAYERS,
            1.0f, // 音量
            1.5f  // 音调（1.5 = 更高更尖锐）
        );

        double maxDistance = 30.0;
        double stepSize = 0.15; // 每0.15格检查一次（更精细的碰撞）
        int steps = (int) (maxDistance / stepSize);

        for (int i = 0; i <= steps; i++) {
            Vec3 current = start.add(direction.scale(i * stepSize));

            // 生成粒子效果（更明显：每步都生成）
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.WITCH, // 紫色魔法粒子
                current.x, current.y, current.z,
                1, // 数量1
                0, 0, 0, // 无扩散
                0 // 无速度
            );
            // 每3步额外生成一个红色粒子作为标记，让路径更明显
            if (i % 3 == 0) {
                level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.CRIT, // 红色暴击粒子
                    current.x, current.y, current.z,
                    2, // 数量2
                    0.02, 0.02, 0.02, // 微小扩散
                    0.01 // 微小速度
                );
            }

            // 检查是否命中玩家（使用碰撞箱检测）
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                if (target == shooter) continue;
                if (!target.isAlive()) continue;
                if (!Dim201Addon.isInGameAlive(target)) continue; // 跳过已出局/旁观/创造
                
                // 使用碰撞箱检测而非简单距离
                net.minecraft.world.phys.AABB targetBox = target.getBoundingBox().inflate(0.3);
                if (targetBox.contains(current)) {
                    // 命中！播放命中音效
                    level.playSound(
                        null,
                        current.x, current.y, current.z,
                        SoundEvents.PLAYER_HURT, // 命中音效
                        SoundSource.PLAYERS,
                        1.0f,
                        1.0f
                    );
                    onNeedleHit(shooter, target);
                    return;
                }
            }

            // 检查是否撞到方块（精确检测，跳过可穿过方块 + 用户新增：所有门都不拦截）
            var blockPos = BlockPos.containing(current.x, current.y, current.z);
            var blockState = level.getBlockState(blockPos);
            if (!blockState.isAir()) {
                // ① 先判"门"（用户要求穿门），只要我们认定是门，就直接继续飞
                if (isDoorBlockShouldPenetrate(level, blockPos, blockState)) {
                    continue;
                }
                // ② 再判"无碰撞箱方块"（草、花、告示牌等）
                if (blockState.getCollisionShape(level, blockPos).isEmpty()) {
                    continue; // 可穿过，继续飞行
                }
                // 撞到实体方块（非门），播放击中音效
                level.playSound(
                    null,
                    current.x, current.y, current.z,
                    SoundEvents.ARROW_HIT,
                    SoundSource.PLAYERS,
                    0.5f,
                    1.0f
                );
                break; // 停止
            }
        }
    }

    private void onNeedleHit(ServerPlayer shooter, ServerPlayer target) {
        // 清空背包（保留原行为）
        target.getInventory().clearContent();
        // 通用小脑惩罚：好人误杀好人时由 CardTeamKillPunisher 统一处理 shot_innocent + 警告字幕
        // （完全镜像 SRE 原生小脑逻辑：含游客/家族/中立/疯狂魔术师/复仇者/巫毒娃娃等豁免）
        CardTeamKillPunisher.applyIfApplicable(target, shooter, ReimuRole.DEATH_REASON_NEEDLE);
        // 击杀目标 — true = 保留尸体，shooter = 灵梦是击杀者，死因 = 封魔针
        io.wifi.starrailexpress.game.GameUtils.killPlayer(
            target, true, shooter, ReimuRole.DEATH_REASON_NEEDLE
        );
    }

    // ==================== 技能2：梦符「二重结界」 ====================

    /**
     * 消耗3灵气，清除八云紫的所有坐标并使八云紫强制显形
     * 120秒冷却
     * 【2026-08-11】用户：按下技能键只触发"二重结界"一个；封魔针改成空手右键发射
     */
    public boolean useDreamBarrier(ServerPlayer player) {
        // 检查冷却
        if (dreamBarrierCooldown > 0) {
            int cd = (int) Math.ceil(dreamBarrierCooldown / 20.0);
            sendActionBar(player, "§c§l梦符「二重结界」冷却中 §f" + cd + "秒");
            return false;
        }

        if (reimuPower < 3) {
            sendActionBar(player, "§c§l灵气不足！§f需要3层，当前" + reimuPower + "层");
            return false;
        }

        var server = player.getServer();
        if (server == null) return false;

        boolean foundYukari = false;

        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            var gameWorld = SREGameWorldComponent.KEY.get(target.level());
            if (gameWorld == null) continue;
            if (!gameWorld.isRole(target, YukariRole.YUKARI)) continue;
            if (!target.isAlive()) continue;

            foundYukari = true;

            // 清除八云紫的所有坐标
            YukariPlayerComponent yukariComp = ModComponents.YUKARI.get(target);
            yukariComp.coordinates.clear();

            // 强制显形
            if (yukariComp.isInvisible) {
                yukariComp.isInvisible = false;
                yukariComp.applyInvisibleState();
            }
            // 设置切换CD（10秒）
            yukariComp.toggleCooldown = 200;

            // 距离>16格 → 发光3秒（让灵梦能追踪八云紫位置）
            double dist = target.distanceTo(player);
            boolean farMarked = dist > 16.0;
            if (farMarked) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }

            yukariComp.sync();

            // 提示八云紫（合并为一条，避免 actionbar 互相覆盖）
            String tip = farMarked
                    ? "§4§l梦符「二重结界」！§f你的坐标被清除，因距离过远被发光标记3秒！"
                    : "§4§l梦符「二重结界」！§f你的坐标被清除了！";
            yukariComp.sendActionBar(target, tip);
        }

        if (foundYukari) {
            reimuPower -= 3;
            dreamBarrierCooldown = DREAM_BARRIER_COOLDOWN; // 120秒冷却
            sendActionBar(player, "§c§l梦符「二重结界」！§f清除了八云紫的坐标 §7[§e" + reimuPower + "§7/§e" + MAX_REIMU_POWER + "§7]");
        } else {
            sendActionBar(player, "§c§l没有检测到八云紫！");
            return false; // 不消耗灵气
        }

        sync();
        return true;
    }

    // ==================== NBT ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("reimuPower", reimuPower);
        tag.putDouble("distanceTraveled", distanceTraveled);
        // 2026-08-11 不再保存 currentSkill（因为只有二重结界一个按技能键触发的能力；封魔针是空手右键）
        tag.putInt("sealingNeedleCooldown", sealingNeedleCooldown);
        tag.putInt("dreamBarrierCooldown", dreamBarrierCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        reimuPower = tag.getInt("reimuPower");
        distanceTraveled = tag.getDouble("distanceTraveled");
        sealingNeedleCooldown = tag.contains("sealingNeedleCooldown") ? tag.getInt("sealingNeedleCooldown") : 0;
        dreamBarrierCooldown = tag.contains("dreamBarrierCooldown") ? tag.getInt("dreamBarrierCooldown") : 0;
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