package com.dim201.dim201addon;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 通用近战连击击杀处理器
 * 
 * 适用于 Biollante、Van、VanDDF、Boy 等角色
 * 空手攻击玩家指定次数后击杀
 * 
 * Biollante 特殊机制：连击打满击杀无辜者时同归于尽
 */
public class MeleeKillHandler {

    /**
     * 记录每个攻击者（UUID）当前的 [目标UUID, 连击次数]
     */
    public static final Map<UUID, PunchRecord> PUNCH_RECORDS = new HashMap<>();

    /**
     * 记录每个受害者上次被攻击的时间（服务器tick），用于攻击间隔
     */
    public static final Map<UUID, Long> LAST_HURT_TIME = new HashMap<>();

    /**
     * 攻击间隔（tick）：1秒 = 20 tick
     */
    public static final int ATTACK_COOLDOWN = 10;

    /**
     * 触发致死所需的连击次数
     */
    public static final int KILL_THRESHOLD = 5;

    /**
     * 注册需要连击击杀的角色列表
     */
    private static final Map<io.wifi.starrailexpress.api.SRERole, String> REGISTERED_ROLES = new HashMap<>();

    /**
     * 注册角色的连击击杀能力
     * 
     * @param role 角色
     * @param killReason 击杀原因（用于死亡提示）
     */
    public static void registerRole(io.wifi.starrailexpress.api.SRERole role, String killReason) {
        REGISTERED_ROLES.put(role, killReason);
    }

    public static void register() {
        // 注册空手拳击权限
        AllowPlayerPunching.EVENT.register((player) -> {
            if (player.hasEffect(ModEffects.SAFE_TIME)) {
                return false;
            }
            
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null) return false;
            
            var role = gameWorld.getRole(player);
            if (role == null) return false;
            
            // 检查是否是已注册的角色
            if (REGISTERED_ROLES.containsKey(role)) {
                // 必须空手（主手持空气）
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.isEmpty()) {
                    return true;
                }
            }
            return false;
        });

        // 监听实体受到伤害事件
        AttackEntityCallback.EVENT.register(MeleeKillHandler::onEntityDamaged);
    }

    /**
     * 当实体受到伤害时触发
     */
    public static InteractionResult onEntityDamaged(Player attacker, Level level, InteractionHand hand, Entity entity,
            EntityHitResult hitResult) {
        if (SRE.isLobby)
            return InteractionResult.PASS;
            
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (!GameUtils.isPlayerAliveAndSurvival(attacker))
            return InteractionResult.PASS;
            
        if (!(entity instanceof Player victim)) {
            return InteractionResult.PASS;
        }
        
        if (!GameUtils.isPlayerAliveAndSurvival(victim) || victim.isSpectator())
            return InteractionResult.PASS;
            
        // 检查攻击者是否是已注册的角色
        var attackerRole = gameWorldComponent.getRole(attacker);
        if (attackerRole == null || !REGISTERED_ROLES.containsKey(attackerRole))
            return InteractionResult.PASS;
            
        // 检查受害者是否有安全时间
        if (victim.getCooldowns().isOnCooldown(Items.CLOCK)) {
            return InteractionResult.PASS;
        }
        
        // 必须空手
        ItemStack mainHand = attacker.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return InteractionResult.PASS;
        }
        
        // 排除自伤
        if (attacker.getUUID().equals(entity.getUUID())) {
            return InteractionResult.PASS;
        }

        // ====== 客户端直接返回成功（显示攻击动画），但不处理逻辑 ======
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // ====== 服务器端处理 ======
        
        // ====== 攻击间隔检查（服务器端） ======
        long currentTime = level.getGameTime();
        Long lastHurt = LAST_HURT_TIME.get(victim.getUUID());
        if (lastHurt != null && (currentTime - lastHurt) < ATTACK_COOLDOWN) {
            // 冷却中，不处理这次攻击，但返回 SUCCESS 阻止原版攻击
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        }
        LAST_HURT_TIME.put(victim.getUUID(), currentTime);
        // ====== 攻击间隔检查结束 ======

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();
        String killReason = REGISTERED_ROLES.get(attackerRole);

        PunchRecord record = PUNCH_RECORDS.computeIfAbsent(attackerUUID, k -> new PunchRecord());

        // 如果目标换人，重置计数
        if (!victimUUID.equals(record.targetUUID)) {
            record.targetUUID = victimUUID;
            record.count = 0;
        }

        record.count++;

        int remaining = KILL_THRESHOLD - record.count;
        
        // 造成1点伤害（显示攻击效果）
        victim.hurt(attacker.damageSources().playerAttack(attacker), 1.0F);
        
        // 设置受害者的受伤无敌时间，防止其他伤害
        victim.invulnerableTime = ATTACK_COOLDOWN;

        if (record.count >= KILL_THRESHOLD) {
            // 达到阈值：准备击杀
            
            // ========== Biollante 特殊机制：击杀无辜者时同归于尽 ==========
            if (attackerRole.equals(BiollanteRole.BIOLLANTE)) {
                var victimRole = gameWorldComponent.getRole(victim);
                if (victimRole != null) {
                    // 检查目标是否是平民或警长阵营（无辜者）
                    boolean isInnocent = victimRole.isInnocent() || victimRole.isVigilanteTeam();
                    
                    if (isInnocent) {
                        // 误杀无辜者！双方同归于尽
                        PUNCH_RECORDS.remove(attackerUUID);
                        LAST_HURT_TIME.remove(victimUUID);
                        
                        // 先击杀平民（受害者）
                        GameUtils.killPlayer(victim, true, attacker, Noellesroles.id("biollante_friendly_fire_victim"));
                        
                        // 再自杀（攻击者）
                        GameUtils.killPlayer(attacker, true, null, Noellesroles.id("biollante_friendly_fire"));
                        
                        // 发送提示给攻击者
                        attacker.displayClientMessage(
                            Component.literal("§c§l你误杀了无辜者！正义的制裁降临了...")
                                .withStyle(ChatFormatting.RED), 
                            true
                        );
                        
                        // 发送提示给受害者
                        victim.displayClientMessage(
                            Component.literal("§c§l你被 Biollante 误杀了...")
                                .withStyle(ChatFormatting.RED), 
                            true
                        );
                        
                        return InteractionResult.SUCCESS_NO_ITEM_USED;
                    }
                }
            }
            // ========== Biollante 特殊机制结束 ==========

            // 正常击杀目标
            PUNCH_RECORDS.remove(attackerUUID);
            LAST_HURT_TIME.remove(victimUUID);

            GameUtils.killPlayer(victim, true, attacker, Noellesroles.id(killReason));

            sendActionBar(attacker,
                    Component.translatable("message.melee_kill.kill").withStyle(ChatFormatting.RED));

            return InteractionResult.SUCCESS_NO_ITEM_USED;
        } else {
            // 未达到阈值：提示剩余次数
            ChatFormatting color = remaining == 1 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            sendActionBar(attacker,
                    Component.translatable("message.melee_kill.combo", 
                            Component.literal("[" + record.count + "/" + KILL_THRESHOLD + "]").withStyle(ChatFormatting.WHITE))
                            .withStyle(color));
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        }
    }

    /**
     * 向玩家发送 Action Bar 消息
     */
    private static void sendActionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    /**
     * 存储单个攻击者的连击状态
     */
    public static class PunchRecord {
        public UUID targetUUID = null;
        public int count = 0;
    }
}