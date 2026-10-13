package com.dim201.dim201addon;

import com.dim201.dim201addon.role.TeacherZhangAllGameMode;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TeacherZhangRole {

    public static final ResourceLocation TEACHER_ZHANG_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "teacher_zhang"
    );
    
    public static SRERole TEACHER_ZHANG;

    // 获胜所需移动距离（格）
    public static final double WIN_DISTANCE = 4000.0;

    public static void register() {
        TEACHER_ZHANG = TMMRoles.registerRole(new TeacherZhangRoleImpl());
    }

    /**
     * 张老师获胜
     */
    public static void triggerWin(ServerPlayer player) {
        // 发送胜利标题
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("§6§l你赢了！")
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("§f你成功移动了 " + (int)WIN_DISTANCE + " 格！")
        ));

        // 播放胜利音效
        player.level().playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
            SoundSource.PLAYERS,
            1.0f,
            1.0f
        );

        // 全员张老师模式下，走赢家获胜时心脏麻痹杀死其他存活玩家
        // 必须在 customWinnerWin 之前执行，因为 customWinnerWin 会调用 stopGame 立即结束游戏
        var gameMode = SREGameWorldComponent.KEY.get(player.serverLevel()).getGameMode();
        if (gameMode != null && gameMode.identifier.equals(TeacherZhangAllGameMode.ID)) {
            for (Player p : player.serverLevel().players()) {
                if (p != player && !GameUtils.isPlayerEliminated(p)) {
                    GameUtils.killPlayer(p, true, player, Noellesroles.id("heart_attack"), true);
                }
            }
        }

        // 使用 RoleUtils 触发胜利
        RoleUtils.customWinnerWin(
            player.serverLevel(),
            "dim201addon.teacher_zhang",
            0xFF00E680
        );
    }

    private static class TeacherZhangRoleImpl extends NormalRole {
        
        public TeacherZhangRoleImpl() {
            super(
                TEACHER_ZHANG_ID,
                0xFF00E680,
                false,       // isInnocent = false（中立）
                false,       // canUseKiller = false（不是杀手）
                MoodType.REAL,  // 真实心情
                -1,          // maxSprintTime（默认）
                true         // canSeeTime = true（能看到剩余时间）
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setDefaultMax(1)  // 每局最多1个
                .setCanBeRandomedByOtherRoles(false)
                .setNeutrals(true);  // 中立角色
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            
            // 碳酸饮料 - 绿色药水
            ItemStack sodaStack = new ItemStack(Items.POTION);
            sodaStack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, 
                new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.LUCK));
            sodaStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, 
                Component.literal("§a碳酸饮料"));
            entries.add(new TeacherZhangSodaShopEntry(sodaStack, 100));
            
            // 巧克力冰激凌 - 棕色染料
            ItemStack iceCreamStack = new ItemStack(Items.BROWN_DYE);
            iceCreamStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, 
                Component.literal("§6巧克力冰激凌"));
            entries.add(new TeacherZhangIceCreamShopEntry(iceCreamStack, 100));
            
            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(player);
            if (comp == null) {
                return false;
            }
            return comp.useAbility();
        }
    }
    
    /**
     * 碳酸饮料商店条目
     */
    private static class TeacherZhangSodaShopEntry extends ShopEntry {
        
        public TeacherZhangSodaShopEntry(ItemStack stack, int price) {
            super(stack, price, ShopEntry.Type.WEAPON);
        }
        
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            
            TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(serverPlayer);
            StringBuilder actionBar = new StringBuilder();
            
            // 检查是否有冰激凌免疫
            if (comp.hasIceCreamImmunity) {
                comp.hasIceCreamImmunity = false;
                actionBar.append("§a§l本次免疫猝死§r | ");
                // 直接执行增益效果，不判定死亡
                applySodaEffect(serverPlayer, comp, actionBar);
                comp.sendActionBar(serverPlayer, actionBar.toString());
                comp.sync();
                return true;
            }
            
            // 计算死亡概率：第一次20%，每次增加20%
            int deathChance = (comp.sodaBuyCount + 1) * 20; // 20%, 40%, 60%, 80%, 100%
            int roll = serverPlayer.getRandom().nextInt(100);
            
            comp.sodaBuyCount++;
            
            if (roll < deathChance) {
                // 死亡！使用 SRE 的死亡系统，心脏骤停
                actionBar.append("§4§剧烈运动还喝碳酸饮料！你死了！§r | §c概率:").append(deathChance).append("% 骰子:").append(roll);
                comp.sendActionBar(serverPlayer, actionBar.toString());
                // 触发 SRE 死亡（心脏骤停），使用 Noellesroles.id()
                GameUtils.killPlayer(serverPlayer, true, serverPlayer, Noellesroles.id("heart_attack"));
                comp.sync();
                return true;
            }
            
            // 存活
            actionBar.append("§a§l存活！§r | §c概率:").append(deathChance).append("% 骰子:").append(roll).append(" | ");
            
            // 执行随机效果
            applySodaEffect(serverPlayer, comp, actionBar);
            
            // 提示下次死亡概率
            int nextDeathChance = (comp.sodaBuyCount + 1) * 20;
            if (nextDeathChance < 100) {
                actionBar.append(" | §7下次:").append(nextDeathChance).append("%");
            } else {
                actionBar.append(" | §c§o下次必死！");
            }
            
            comp.sendActionBar(serverPlayer, actionBar.toString());
            comp.sync();
            return true;
        }
        
        private void applySodaEffect(ServerPlayer player, TeacherZhangPlayerComponent comp, StringBuilder actionBar) {
            // 随机效果：无事发生、+300、+500、+800
            int effect = player.getRandom().nextInt(4); // 0-3
            switch (effect) {
                case 0 -> {
                    actionBar.append("§7无事发生");
                }
                case 1 -> {
                    comp.totalDistance += 300;
                    actionBar.append("§e+300格 ").append((int)comp.totalDistance).append("/").append((int)WIN_DISTANCE);
                }
                case 2 -> {
                    comp.totalDistance += 500;
                    actionBar.append("§e+500格 ").append((int)comp.totalDistance).append("/").append((int)WIN_DISTANCE);
                }
                case 3 -> {
                    comp.totalDistance += 800;
                    actionBar.append("§e+800格 ").append((int)comp.totalDistance).append("/").append((int)WIN_DISTANCE);
                }
            }
            comp.sync();
            
            // 检查是否获胜
            if (comp.totalDistance >= WIN_DISTANCE && !comp.hasWon) {
                comp.hasWon = true;
                triggerWin(player);
            }
        }
    }
    
    /**
     * 巧克力冰激凌商店条目
     */
    private static class TeacherZhangIceCreamShopEntry extends ShopEntry {
        
        public TeacherZhangIceCreamShopEntry(ItemStack stack, int price) {
            super(stack, price, ShopEntry.Type.WEAPON);
        }
        
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            
            TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(serverPlayer);
            
            if (comp.hasIceCreamImmunity) {
                comp.sendActionBar(serverPlayer, "§c§l已有冰激凌免疫！");
                return false; // 购买失败，不扣钱
            }
            
            comp.hasIceCreamImmunity = true;
            comp.sendActionBar(serverPlayer, "§6§l获得巧克力冰激凌！免疫一次碳酸饮料死亡！");
            comp.sync();
            
            return true;
        }
    }
}