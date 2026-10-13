package com.dim201.dim201addon.item;

import com.dim201.dim201addon.sound.Dim201ModSounds;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 小丑喇叭
 *  - 物品描述（LORE）：「！？区区？！」—— 写死在 appendHoverText（或直接 set 到每个 ItemStack 的 DataComponents.LORE）
 *  - 右键使用：服务器端消耗 1 个，给「使用者为中心 半径 8 格方块距离内」所有存活玩家播放一次自定义 clown 音频
 *  - 不是 SRE 的「角色技能物品」，只是普通的消耗品，可以通过命令 /give 或 商店 给玩家
 */
public class ClownHornItem extends Item {

    private static final int RANGE_BLOCKS = 8;
    private static final int COOLDOWN_TICKS = 10 * 20; // 10 秒内置冷却（用户要求）

    public ClownHornItem(Item.Properties props) {
        super(props);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack); // 客户端只返回 success 让动画播放
        }
        // 冷却中：拒绝（不会再次触发，也不会消耗、不会全服刷屏 Title）
        if (player.getCooldowns().isOnCooldown(this)) {
            player.displayClientMessage(
                    Component.literal("§c小丑喇叭冷却中！§f请稍后再使用"),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }
        // 服务器端：消耗 1 个 + 给 8 格范围所有玩家放声音
        final double rangeSq = (double) RANGE_BLOCKS * RANGE_BLOCKS;
        AABB box = new AABB(
                player.getX() - RANGE_BLOCKS, player.getY() - RANGE_BLOCKS, player.getZ() - RANGE_BLOCKS,
                player.getX() + RANGE_BLOCKS, player.getY() + RANGE_BLOCKS, player.getZ() + RANGE_BLOCKS
        );
        List<Player> receivers = level.getEntitiesOfClass(Player.class, box, p ->
                p != null && p.isAlive() && !p.isSpectator() && p.distanceToSqr(player) <= rangeSq
        );
        if (GameUtils.isPlayerAliveAndSurvival(player) && !receivers.contains(player)) {
            receivers.add(player); // 使用者自己也能听到
        }
        for (Player r : receivers) {
            try {
                r.playNotifySound(Dim201ModSounds.CLOWN, SoundSource.PLAYERS, 1.25F, 1.0F);
            } catch (Throwable ignored) {
                // 单个玩家的 playNotifySound 失败不影响其他玩家
            }
        }
        // 用户：Title 无范围限制 —— 只要有人吹喇叭，服务器内所有在线玩家都能看到（声音仍只在 8 格内可听）
        if (player.getServer() != null) {
            net.minecraft.server.MinecraftServer server = player.getServer();
            Component titleMain = Component.literal("§c§l！？区区？！");
            for (ServerPlayer all : server.getPlayerList().getPlayers()) {
                if (all == null) continue;
                try {
                    all.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                    all.connection.send(new ClientboundSetTitleTextPacket(titleMain));
                } catch (Throwable ignored) {
                    // 单个玩家发包失败忽略
                }
            }
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1); // 生存/冒险模式下用一次减一个（创造下不消耗）
        }
        // 触发 10 秒内置冷却（用 MC 原生 ItemCooldowns：跨所有物品栏位共享该物品冷却）
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 保证每个小丑喇叭 ItemStack 都有 tooltip：「！？区区？！」——即使通过 /give 直接给玩家也能看到描述。
     */
    public static ItemStack withBuiltinLore(@NotNull ItemStack stack) {
        try {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§f！？区区？！"));
            lore.add(Component.literal("§7可在地图售货机中购买"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        } catch (Throwable ignored) {}
        return stack;
    }
}
