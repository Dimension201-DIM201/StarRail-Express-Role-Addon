package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.item.Dim201ModItems;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 天弓千亦 - 中立阵营
 *
 * <p>核心机制：
 * <ul>
 *   <li>中立阵营（isInnocent=false, canUseKiller=false），无胜利条件</li>
 *   <li>可购买所有能力卡牌（getShopEntries 返回5张卡牌）</li>
 *   <li>空白卡牌每局限购1次（通过 onBuy 回调检查）</li>
 *   <li>与每个不同玩家的第一次交易获得50金币奖励（由 ChimataTradeManager 管理）</li>
 * </ul>
 *
 * <p>原理：NormalRole 构造时传入 isInnocent=false, canUseKiller=false，
 * NormalRole 会自动调用 setNeutrals(true)，使角色成为中立阵营，
 * 不参与好人/杀手的胜利条件判定。
 */
public class ChimataRole {

    public static final ResourceLocation CHIMATA_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "chimata"
    );

    public static SRERole CHIMATA;

    public static void register() {
        CHIMATA = TMMRoles.registerRole(new ChimataRoleImpl());
    }

    /** 判断玩家是否为天弓千亦（供交易系统调用） */
    public static boolean isChimata(Player player) {
        return ChimataTradeManager.isChimata(player);
    }

    private static class ChimataRoleImpl extends NormalRole {

        public ChimataRoleImpl() {
            super(
                CHIMATA_ID,
                0xFFE6E6FA,          // 淡紫色（中立，仅构造时占位）
                false,               // isInnocent = false（非好人）
                false,               // canUseKiller = false（非杀手）
                MoodType.FAKE,       // 假心情（不启用 mood 系统）
                400,                 // 默认冲刺时间
                true                 // 显示剩余时间
            );
            this.setCanSeeTime(true)
                .setCanUseInstinct(false)
                .setDefaultMax(1);   // 每局最多1个天弓千亦
        }

        /**
         * 重写 color() 实现彩虹变色
         *
         * <p>原理：使用 {@link java.awt.Color#HSBtoRGB} 色彩空间，
         * 以当前系统时间毫秒数计算色相（hue），5秒一个完整彩虹循环。
         * 与 SRE 的 RainbowCreeperRole 实现一致。
         */
        @Override
        public int color() {
            long cycleMs = 5000; // 5秒一个完整彩虹
            float hue = (System.currentTimeMillis() % cycleMs) / (float) cycleMs;
            return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        }

        @Override
        public int getColor() {
            return color();
        }

        /**
         * 商店条目：所有能力卡牌
         *
         * <p>价格统一从 {@link Dim201ModItems#getCardPrice(Item)} 获取，
         * 新增卡牌只需在价格表中添加条目即可自动同步到商店与交易系统。
         *
         * <p>空白卡牌特殊处理：价格为0，但每局限购1次。
         *
         * <p>排序规则：按价格从低到高展示，价格相同的按注册名字典序排序。
         *
         * <p>原理：SRE 商店购买流程为「客户端发送 index → 服务端 getShopEntries().get(index)」，
         * 两端必须返回完全一致的 List 顺序。
         * 由于 {@code Dim201ModItems.ABILITY_CARDS} 是 {@code Set.of()}，
         * Java 9+ 的不可变 Set 使用随机化哈希 seed 防止哈希碰撞攻击，
         * 客户端与服务端是独立 JVM，{@code Set.of()} 的迭代顺序可能不同。
         * {@code Comparator.comparingInt} 是稳定排序，对价格相同的元素保留原 Set 顺序，
         * 导致两端 List 中相同价格卡牌的相对顺序可能不一致 → "购买A拿到B" 错位。
         * 通过添加次级排序（注册名字典序）确保所有 JVM 中顺序一致。
         */
        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 按价格升序排序，价格相同的按注册名路径字典序排序（保证跨 JVM 一致）
            List<Item> sortedCards = new ArrayList<>(Dim201ModItems.ABILITY_CARDS);
            Comparator<Item> byPrice = Comparator.comparingInt(Dim201ModItems::getCardPrice);
            Comparator<Item> byName = Comparator.comparing(
                    card -> BuiltInRegistries.ITEM.getKey(card).getPath());
            sortedCards.sort(byPrice.thenComparing(byName));

            for (Item card : sortedCards) {
                int price = Dim201ModItems.getCardPrice(card);

                if (card == Dim201ModItems.BLANK) {
                    // 空白卡牌：0金币，每局限购1次
                    entries.add(new ShopEntry(
                            card.getDefaultInstance(), price, ShopEntry.Type.TOOL
                    ) {
                        @Override
                        public boolean canBuy(@NotNull Player player) {
                            if (!(player instanceof ServerPlayer sp)) return false;
                            if (!ChimataTradeManager.canBuyBlank(sp)) return false;
                            return super.canBuy(player);
                        }

                        @Override
                        public boolean onBuy(@NotNull Player player) {
                            if (!(player instanceof ServerPlayer sp)) return false;
                            if (!ChimataTradeManager.canBuyBlank(sp)) {
                                sp.displayClientMessage(
                                        Component.literal("§c本局已购买过空白卡牌"), false);
                                return false;
                            }
                            // 先标记已购买，再给予物品（保证原子性）
                            ChimataTradeManager.markBlankBought(sp);
                            return super.onBuy(player);
                        }
                    });
                } else {
                    // 普通卡牌：按价格表出售
                    entries.add(new ShopEntry(
                            card.getDefaultInstance(), price, ShopEntry.Type.TOOL
                    ));
                }
            }

            return entries;
        }
    }
}
