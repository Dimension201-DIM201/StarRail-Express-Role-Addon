package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.ShionPlayerComponent;
import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
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
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.cursed.cca.CursedComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 依神紫菀（Shion Yorigami）
 * 中立阵营
 *
 * 描述：依神姐妹中的姐姐，给他人带来不幸的贫穷神。
 *       饥饿的你，不知道为什么就上了这趟列车…
 *
 * 被动① 不幸的源头：
 *  · 对局 90s 后生效，每隔 20~40s 不定时让 12 格内玩家随机损失 10~40 金币
 *
 * 被动② 厄运缠身：
 *  · 被他人击杀时，击杀者被诅咒修饰符「厄运」缠身，金币清零
 *
 * 主动技能（G） 乞讨：
 *  · 对面前 3 格玩家发起乞讨，对方打开碗的 UI（施舍/拒绝）
 *  · 施舍：给紫菀 10~25 随机金币，施舍者 20% 概率获得 1 层护盾
 *  · 拒绝：无事发生
 *  · 技能冷却 25 秒
 *
 * 获胜：商店购买「去吃饭」（150金币）
 *  · 50% 成功：紫菀完成目标，跟随其他阵营胜利，不影响结算
 *  · 50% 失败：损失 150 金币（消耗），可再次尝试
 */
public class ShionRole {

    public static final ResourceLocation SHION_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "shion");

    public static SRERole SHION;

    /** 去吃饭（商店购买）价格 */
    public static final int GO_EAT_PRICE = 50;
    /** 购买成功概率（0.15 = 15%） */
    public static final double GO_EAT_SUCCESS_RATE = 0.15;

    public static void register() {
        SHION = TMMRoles.registerRole(new ShionRoleImpl());
    }

    /**
     * 被击杀回调：给击杀者 + CURSED 修饰符 + 清空金币
     */
    public static void onShionKilledByKiller(ServerPlayer shion, ServerPlayer killer) {
        if (killer == null) return;

        // 1. 给击杀者 CURSED 修饰符（厄运缠身）
        CursedComponent cursed = CursedComponent.KEY.get(killer);
        cursed.setCursed(killer.getUUID());
        cursed.sync();
        WorldModifierComponent wmc = WorldModifierComponent.KEY.get(killer.level());
        wmc.addModifier(killer.getUUID(), SEModifiers.CURSED);

        // 2. 清空击杀者金币
        int gold = CardUtils.getGold(killer);
        CardUtils.addGold(killer, -gold);

        // 3. 通知
        killer.displayClientMessage(Component.literal("§c§l厄运缠身！ §4贫穷神的诅咒降临于你，钱包被掏空..."), false);
        killer.playNotifySound(SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.7f, 0.8f);

        shion.displayClientMessage(Component.literal("§5§l贫穷之神的诅咒 §f已施加到 §4" + killer.getName().getString() + " §f身上！"), false);
    }

    private static class ShionRoleImpl extends NormalRole {

        public ShionRoleImpl() {
            super(
                    SHION_ID,
                    0xFF00BFFF,              // 深天蓝/青蓝色（DeepSkyBlue，青蓝系）
                    false,                   // isInnocent：false（中立）
                    false,                   // canUseKiller：不能使用杀手能力
                    MoodType.REAL,           // 
                    400,                     // maxSprintTime：体力 400
                    true                     // hideScoreboard：true → 看不到时间
            );

            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setDefaultMax(1)                 // 每局最多 1 个
                .setCanBeRandomedByOtherRoles(true)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public @NotNull List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            // 使用自定义「破碗」而不是原版 Items.BOWL
            // 原理：SRE 对局流程中会主动清理背包里的空碗，导致紫菀初始碗立刻被清空；
            //       自定义物品 dim201addon:broken_bowl 不会被 SRE 的清碗逻辑识别，因此不会被清理。
            items.add(new ItemStack(Dim201ModItems.BROKEN_BOWL));
            return items;
        }

        @Override
        public @NotNull List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 「去吃饭」：150 金币，50% 成功跟随胜利，50% 失败
            ItemStack eat = new ItemStack(Items.COOKED_BEEF);
            eat.set(DataComponents.ITEM_NAME, Component.literal("§d§l去吃饭"));
            entries.add(new ShopEntry(eat, GO_EAT_PRICE, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!(player instanceof ServerPlayer sp)) return false;
                    ShionPlayerComponent comp = ModComponents.SHION.get(sp);
                    if (comp == null) return false;

                    SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
                    if (gw == null) return false;
                    if (!gw.isRole(sp, SHION)) {
                        sp.sendSystemMessage(Component.literal("§c只有紫菀才能买这个！"));
                        return false; // 非紫菀购买失败（不扣钱）
                    }
                    if (comp.hasWon) {
                        sp.sendSystemMessage(Component.literal("§a你已经吃得饱饱的了！"));
                        return false; // 已经胜利过
                    }

                    // 先扣钱（ShopEntry 外层会扣，但这里要在扣钱之前判定概率？
                    // ShopEntry 框架：onBuy return true 表示购买成功（会扣钱）；return false 失败（不扣）
                    // 所以我们直接在这里判定结果，始终扣钱（因为是购买行为），对玩家显示不同提示

                    // 扣钱检查（防止免费）
                    int gold = CardUtils.getGold(sp);
                    if (gold < GO_EAT_PRICE) {
                        sp.sendSystemMessage(Component.literal("§c钱不够，还是饿着吧..."));
                        return false;
                    }

                    // 15% 成功 / 85% 失败
                    boolean success = ThreadLocalRandom.current().nextDouble() < GO_EAT_SUCCESS_RATE;
                    if (success) {
                        comp.hasWon = true;
                        comp.sync();

                        // 标题 + 字幕
                        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                        sp.connection.send(new ClientboundSetTitleTextPacket(
                                Component.literal("§d§l吃饱啦！")
                        ));
                        sp.connection.send(new ClientboundSetSubtitleTextPacket(
                                Component.literal("§f这顿饭真值...（你已达成目标，跟随好人/杀手胜利）")
                        ));
                        sp.level().playSound(null, sp.blockPosition(),
                                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8f, 1.0f);
                        sp.level().playSound(null, sp.blockPosition(),
                                SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6f, 1.1f);

                        // 不触发 customWinnerWin：改为"跟随胜利"模式
                        // 之后的 AllowGameEnd 判定中，hasWon=true 的紫菀会被"视为淘汰"，
                        // 让 SRE 原胜负判定（平民/杀手胜利）照常走
                    } else {
                        sp.displayClientMessage(Component.literal("§c§l饭馊了... §f你没能吃到这顿饭，下次再试试？"), false);
                        sp.level().playSound(null, sp.blockPosition(),
                                SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
                    }

                    return true; // 始终扣钱
                }
            });

            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            // 主动技能：乞讨
            ShionPlayerComponent comp = ModComponents.SHION.get(player);
            if (comp == null) return false;
            if (comp.hasWon) {
                player.displayClientMessage(Component.literal("§7你已经吃饱了，无需再乞讨"), false);
                return false;
            }
            return comp.useBegAbility(player);
        }

        /** tick：推进被动倒计时和乞讨冷却 */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, SHION)) return;
            ShionPlayerComponent comp = ModComponents.SHION.get(player);
            comp.tick(player);
        }
    }
}
