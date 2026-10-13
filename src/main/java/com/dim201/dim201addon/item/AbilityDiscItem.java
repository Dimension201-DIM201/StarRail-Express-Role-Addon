package com.dim201.dim201addon.item;

import com.dim201.dim201addon.role.PucciRole;
import com.dim201.dim201addon.role.WhiteSnakeRole;
import com.dim201.dim201addon.WhiteSnakePlayerComponent;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AbilityDiscItem extends Item {

    public AbilityDiscItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        
        if (level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }
        if (!(user instanceof ServerPlayer serverUser)) {
            return InteractionResultHolder.pass(stack);
        }
        
        ServerPlayer target = findNearbyPlayer(serverUser, 3.0);
        if (target == null) {
            serverUser.displayClientMessage(Component.literal("§c§l附近3格内没有玩家！"), true);
            return InteractionResultHolder.pass(stack);
        }
        
        // ===== 新增：检查目标是否是白蛇或普奇 =====
        var gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld != null) {
            var targetRole = gameWorld.getRole(target);
            
            boolean isTargetWhiteSnake = targetRole != null && targetRole.equals(WhiteSnakeRole.WHITESNAKE);
            boolean isTargetPucci = targetRole != null && targetRole.equals(PucciRole.PUCCI);
            
            if (isTargetWhiteSnake || isTargetPucci) {
                // 目标是白蛇/普奇：把DISC交给对方，不改变职业
                return transferDiscToTeammate(serverUser, target, stack, hand);
            }
        }
        // ============================================
        
        // 原有逻辑：改变目标职业
        String roleId = parseRoleIdFromName(stack);
        if (roleId == null) {
            serverUser.displayClientMessage(Component.literal("§c§l无效的能力DISC！"), true);
            return InteractionResultHolder.pass(stack);
        }
        
        var server = serverUser.getServer();
        if (server != null) {
            var commandSource = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
            
            server.getCommands().performPrefixedCommand(
                commandSource,
                "changeRole " + target.getName().getString() + " " + roleId
            );
        }
        
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            Vec3 pos = target.position();
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, 
                pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.3, 0.3, 0.02);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, 
                pos.x, pos.y + 1.0, pos.z, 10, 0.2, 0.2, 0.2, 0.01);
        }
        
        serverUser.displayClientMessage(
            Component.literal("§5§l能力赋予！§f" + target.getName().getString() + " 已被赋予disc中的能力"), 
            true
        );
        
        stack.shrink(1);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 把DISC交给白蛇/普奇队友
     */
    private InteractionResultHolder<ItemStack> transferDiscToTeammate(ServerPlayer from, ServerPlayer to, ItemStack stack, InteractionHand hand) {
        // 复制一份给接收方
        ItemStack giveStack = stack.copy();
        giveStack.setCount(1);
        
        // 尝试放入接收方背包
        boolean added = to.getInventory().add(giveStack);
        
        if (!added) {
            // 背包满了，丢在地上
            to.drop(giveStack, false);
        }
        
        // 消耗交出方的DISC
        stack.shrink(1);
        if (stack.isEmpty()) {
            from.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY);
        }
        
        // 提示
        from.displayClientMessage(
            Component.literal("§a已将能力DISC交给 " + to.getName().getString()), true);
        to.displayClientMessage(
            Component.literal("§5§l「同伴」§f 交给你一个能力DISC"), false);
        
        // 播放音效
        from.level().playSound(null, from.blockPosition(), 
            net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 
            net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f);
        
        return InteractionResultHolder.success(stack);
    }

    // ... 其余方法不变 ...
    
    private String parseRoleIdFromName(ItemStack stack) {
        var nameComponent = stack.get(net.minecraft.core.component.DataComponents.ITEM_NAME);
        if (nameComponent == null) return null;
        
        String fullName = nameComponent.getString();
        int startIdx = fullName.indexOf("[WS:");
        if (startIdx == -1) return null;
        
        int endIdx = fullName.indexOf("]", startIdx);
        if (endIdx == -1) return null;
        
        return fullName.substring(startIdx + 4, endIdx);
    }

    private ServerPlayer findNearbyPlayer(ServerPlayer user, double range) {
        Vec3 pos = user.position();
        AABB box = new AABB(
            pos.x - range, pos.y - range, pos.z - range,
            pos.x + range, pos.y + range, pos.z + range
        );
        
        ServerPlayer closest = null;
        double closestDist = range;
        
        for (Player p : user.level().getEntitiesOfClass(Player.class, box)) {
            if (p == user) continue;
            if (!p.isAlive()) continue;
            
            double dist = pos.distanceTo(p.position());
            if (dist < closestDist) {
                closest = (ServerPlayer) p;
                closestDist = dist;
            }
        }
        
        return closest;
    }
}