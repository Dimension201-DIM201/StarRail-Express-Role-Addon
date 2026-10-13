package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class YukariRole {

    public static final ResourceLocation YUKARI_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "yukari"
    );
    
    public static SRERole YUKARI;

    // 死亡原因：神隐
    public static final ResourceLocation DEATH_REASON_SHENYIN = Noellesroles.id("sheny_in");
    // 死亡原因：列车碾压（SRE本体）
    public static final ResourceLocation DEATH_REASON_TRAIN = ResourceLocation.parse("trainmurdermystery:train");

    public static void register() {
        YUKARI = TMMRoles.registerRole(new YukariRoleImpl());
    }

    /**
     * 使用废线技能：击杀所有玩家
     * 如果博丽灵梦存活，废线失败
     */
    public static void useHaisen(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        
        // 检查博丽灵梦是否存活
        if (ReimuRole.isReimuAlive(server)) {
            // 废线失败！
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                target.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("§c§l梦想天生！")
                ));
                target.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("§e§o博丽灵梦保护了你们！")
                ));
            }
            
            // 延迟2秒后播放不死图腾音效
            server.execute(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                    target.level().playSound(
                        null,
                        target.getX(), target.getY(), target.getZ(),
                        SoundEvents.TOTEM_USE,
                        SoundSource.PLAYERS,
                        1.0f,
                        1.0f
                    );
                }
            });
            
            // 提示八云紫
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld != null) {
                YukariPlayerComponent comp = ModComponents.YUKARI.get(player);
                comp.sendActionBar(player, "§4§l废线被博丽灵梦阻止了！");
            }
            
            return;
        }
        
        // 全屏Title警告所有人
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            target.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("§4§l废线「废弃车站下车之旅」")
            ));
            target.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§c§o八云紫打开了境界之门...")
            ));
            
            target.level().playSound(
                null,
                target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS,
                1.0f,
                0.5f
            );
        }
        
        // 延迟2秒后击杀所有其他玩家
        server.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                if (target == player) continue;
                if (!target.isAlive()) continue;
                // 跳过观察者模式玩家
                if (target.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                    || target.gameMode.getGameModeForPlayer() == GameType.CREATIVE) continue;
                
                // 使用SRE死亡系统，列车碾压
                io.wifi.starrailexpress.game.GameUtils.killPlayer(
                    target, true, player, DEATH_REASON_TRAIN
                );
            }
        });
    }

    private static class YukariRoleImpl extends NormalRole {
        
        public YukariRoleImpl() {
            super(
                YUKARI_ID,
                0xFF800080, // 紫色
                false,      // isInnocent = false（杀手）
                true,       // canUseKiller = true（杀手）
                MoodType.FAKE,  // 假心情
                -1,         // maxSprintTime（默认）
                true        // canSeeTime = true
            );
            
            this.setCanSeeTime(true)
                .setCanUseInstinct(true) // 八云紫始终可以使用直觉
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
                        
            // 废线「废弃车站下车之旅」— 0金币矿车
            ItemStack haisenStack = new ItemStack(Items.MINECART);
            haisenStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, 
                Component.literal("§4§l废线「废弃车站下车之旅」"));
            entries.add(new HaisenShopEntry(haisenStack, 0));
            
            // 一次性手枪 — 0金币，消耗1境界层数
            ItemStack revolverStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("noellesroles:once_revolver")
                )
            );
            if (revolverStack.getItem() != Items.AIR) {
                revolverStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§6§l一次性手枪 §7[1境界]"));
                entries.add(new RealmShopEntry(revolverStack, 0, 1));
            }
            
            // 刀 — 0金币，消耗2境界层数
            ItemStack knifeStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:knife")
                )
            );
            if (knifeStack.getItem() != Items.AIR) {
                knifeStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§c§l刀 §7[2境界]"));
                entries.add(new RealmShopEntry(knifeStack, 0, 2));
            }
            
            // 开锁器 — 80金币
            ItemStack lockpickStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:lockpick")
                )
            );
            if (lockpickStack.getItem() != Items.AIR) {
                lockpickStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§a§l开锁器"));
                entries.add(new ShopEntry(lockpickStack, 80, ShopEntry.Type.TOOL));
            }
            
            // 是时候认真一下了 — 400金币，末影之眼图标，不获得物品
            ItemStack seriousStack = new ItemStack(Items.ENDER_EYE);
            seriousStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.literal("§5§l是时候认真一下了"));
            entries.add(new SeriousModeShopEntry(seriousStack, 400));

            // 退场 — 仅在开局前60秒可购买，0金币
            ItemStack retireStack = new ItemStack(Items.ENDER_PEARL);
            retireStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.literal("§5§l退场"));
            entries.add(new RetireShopEntry(retireStack, 0));
            
            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            YukariPlayerComponent comp = ModComponents.YUKARI.get(player);

            // 切换技能由专用切换键处理；这里只负责释放当前技能
            switch (comp.currentSkill) {
                case TOGGLE_INVISIBLE:
                    if (comp.canUseToggle()) {
                        comp.toggleInvisible(player);
                        return true;
                    } else {
                        int cd = (int) Math.ceil(comp.toggleCooldown / 20.0);
                        comp.sendActionBar(player, "§c§l切换冷却中 §f" + cd + "秒");
                        return false;
                    }
                    
                case USE_CONNECT:
                    if (comp.canUseConnect()) {
                        comp.useConnect(player);
                        return true;
                    } else {
                        int cd = (int) Math.ceil(comp.connectCooldown / 20.0);
                        comp.sendActionBar(player, "§c§l连线冷却中 §f" + cd + "秒");
                        return false;
                    }
                    
                case SHOOT_BOUNDARY_LINE:
                    if (comp.canUseBoundaryLine()) {
                        comp.useBoundaryLine(player);
                        return true;
                    } else {
                        int cd = (int) Math.ceil(comp.boundaryLineCooldown / 20.0);
                        comp.sendActionBar(player, "§c§l漂流物冷却中 §f" + cd + "秒");
                        return false;
                    }
                    
                default:
                    return false;
            }
        }
        
        /**
         * 服务端每tick调用（SRE调用，每tick只调用一次）
         */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, YUKARI)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }

            YukariPlayerComponent comp = ModComponents.YUKARI.get(player);
            comp.tick(player);
        }
    }
    
    /**
     * 退场商店条目 — 仅在开局前60秒可购买
     * 购买后八云紫变为杀手，博丽灵梦变为义警
     */
    private static class RetireShopEntry extends ShopEntry {
        
        public RetireShopEntry(ItemStack stack, int price) {
            super(stack, price, ShopEntry.Type.TOOL);
        }
        
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            
            var server = serverPlayer.getServer();
            if (server == null) return false;
            
            YukariPlayerComponent comp = ModComponents.YUKARI.get(serverPlayer);
            
            // 使用 ticksSinceAssigned 判断，不受世界时间影响（60秒 = 1200 tick）
            if (comp.ticksSinceAssigned > 1200) {
                serverPlayer.displayClientMessage(
                    Component.literal("§c§l退场时间已过！§f只能在获得角色后60秒内退场"), true);
                return false; // 不扣钱
            }
            
            // 找到博丽灵梦
            ServerPlayer reimuPlayer = null;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                var gw = SREGameWorldComponent.KEY.get(p.level());
                if (gw != null && gw.isRole(p, ReimuRole.REIMU)) {
                    reimuPlayer = p;
                    break;
                }
            }
            
            if (reimuPlayer == null) {
                serverPlayer.displayClientMessage(
                    Component.literal("§c§l博丽灵梦不在游戏中！"), true);
                return false; // 不扣钱
            }
            
            // 通过服务端命令执行角色转换（SRE 官方命令：changeRole 玩家名 职业ID）
            // ⚠ 用户实测：指令名必须是 changeRole（驼峰 R 大写），全小写 changerole 不生效
            var cmdSrc = server.createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput();
            server.getCommands().performPrefixedCommand(cmdSrc,
                    "changeRole " + serverPlayer.getName().getString() + " starrailexpress:killer");
            server.getCommands().performPrefixedCommand(cmdSrc,
                    "changeRole " + reimuPlayer.getName().getString() + " starrailexpress:vigilante");
            
            // ===== 解除八云紫的隐身和失明 =====
            comp.isInvisible = false;
            comp.applyInvisibleState(); // 移除隐身效果和失明
            // 额外确保所有效果被清除
            serverPlayer.removeEffect(MobEffects.BLINDNESS);
            serverPlayer.removeEffect(MobEffects.INVISIBILITY);
            serverPlayer.setInvisible(false);
            
            // 同步状态
            comp.sync();
            
            // 给八云紫发送 Title
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("§5§l你已退场")
            ));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§f身份已转换为§c杀手")
            ));
            
            // 给博丽灵梦发送 Title
            reimuPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            reimuPlayer.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("§c§l八云紫已退场")
            ));
            reimuPlayer.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§f身份转换为§a义警")
            ));
            
            // 给所有其他玩家发送 Title 广播
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p == serverPlayer || p == reimuPlayer) continue;
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("§5§l八云紫§f选择了退场")
                ));
                p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("§c她和灵梦的职业变更为杀手和义警")
                ));
            }
            
            return true; // 扣钱（0金币）
        }
    }
    
    /**
     * 废线商店条目
     */
    private static class HaisenShopEntry extends ShopEntry {
        
        public HaisenShopEntry(ItemStack stack, int price) {
            super(stack, price, ShopEntry.Type.WEAPON);
        }
        
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            
            YukariPlayerComponent comp = ModComponents.YUKARI.get(serverPlayer);
            
            // 检查废线购买冷却
            if (comp.haisenBuyCooldown > 0) {
                int cd = (int) Math.ceil(comp.haisenBuyCooldown / 20.0);
                comp.sendActionBar(serverPlayer, "§c§l废线购买冷却中 §f" + cd + "秒");
                return false; // 购买失败，不扣钱
            }
            
            // 检查境界层数是否达到要求
            int requiredLevel = comp.getRealmRequiredLevel();
            
            if (comp.realmLevel < requiredLevel) {
                comp.sendActionBar(serverPlayer, "§c§l境界不足！§f" + comp.realmLevel + "/" + requiredLevel);
                return false; // 购买失败，不扣钱
            }
            
            // 设置购买冷却并扣除境界层数
            comp.haisenBuyCooldown = YukariPlayerComponent.HAISEN_BUY_COOLDOWN; // 60秒
            comp.realmLevel -= requiredLevel;
            comp.sync();
            
            // 使用废线
            YukariRole.useHaisen(serverPlayer);
            return true;
        }
    }
    
    /**
     * 境界商店条目 — 消耗境界层数购买
     */
    private static class RealmShopEntry extends ShopEntry {
        
        private final int realmCost; // 消耗的境界层数
        
        public RealmShopEntry(ItemStack stack, int price, int realmCost) {
            super(stack, price, ShopEntry.Type.WEAPON);
            this.realmCost = realmCost;
        }
        
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            
            YukariPlayerComponent comp = ModComponents.YUKARI.get(serverPlayer);
            
            // 检查境界层数是否足够
            if (comp.realmLevel < realmCost) {
                comp.sendActionBar(serverPlayer, 
                    "§c§l境界不足！§f需要" + realmCost + "层，当前" + comp.realmLevel + "层");
                return false; // 购买失败，不扣钱
            }
            
            // 扣除境界层数
            comp.realmLevel -= realmCost;
            comp.sendActionBar(serverPlayer, "§5§l购买成功 §7| §f剩余境界: " + comp.realmLevel);
            comp.sync();
            
            // 给予物品
            ItemStack giveStack = this.stack().copy();
            if (!serverPlayer.addItem(giveStack)) {
                serverPlayer.drop(giveStack, false);
            }
            
            return true;
        }
    }
    
    /**
     * 认真模式商店条目 — 400金币，不获得物品，仅开启认真模式
     * 使用 YukariPlayerComponent.seriousModeBuyCooldown 实现 4 分钟购买冷却
     * 冷却期间购买返回 false，不扣钱
     */
    private static class SeriousModeShopEntry extends ShopEntry {
        
        public SeriousModeShopEntry(ItemStack stack, int price) {
            super(stack, price, ShopEntry.Type.TOOL);
        }
        
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            
            YukariPlayerComponent comp = ModComponents.YUKARI.get(serverPlayer);
            
            // 检查是否已经在认真模式中
            if (comp.isSeriousMode) {
                comp.sendActionBar(serverPlayer, "§c§l已经在认真模式中了！");
                return false; // 不扣钱
            }
            
            // 检查购买冷却 — 冷却期间返回 false，SRE 不会扣钱
            if (comp.seriousModeBuyCooldown > 0) {
                int cdSeconds = (int) Math.ceil(comp.seriousModeBuyCooldown / 20.0);
                comp.sendActionBar(serverPlayer, "§c§l购买冷却中 §f" + cdSeconds + "秒");
                return false; // 冷却中，不扣钱
            }
            
            // 激活认真模式
            comp.activateSeriousMode(serverPlayer);
            
            // 如果 activateSeriousMode 因为某种原因没有成功开启，返回 false 不扣钱
            if (!comp.isSeriousMode) {
                return false;
            }
            
            return true; // 成功开启，扣钱
        }
    }
}