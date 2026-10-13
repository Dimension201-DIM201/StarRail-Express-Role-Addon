package com.dim201.dim201addon.role;

import com.dim201.dim201addon.item.Dim201ModItems;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * 杀手能力卡牌半价折扣管理器
 *
 * <p>原理：使用 SRE 的 {@link DynamicShopComponent} 动态价格系统，
 * 在杀手角色分配时为所有能力卡牌设置 50% 折扣（按物品 ID 为键）。
 * 该组件会自动同步到玩家客户端，使商店 UI 显示的折扣价与服务端实际扣费一致。
 *
 * <p>为什么用 DynamicShopComponent 而不是重写 ShopEntry：
 * <ul>
 *   <li>{@code SREPlayerShopComponent.tryBuy} 的扣费逻辑硬编码使用
 *       {@code DynamicShopComponent.effectivePrice(entry)}，无法在 ShopEntry 层面修改扣费金额</li>
 *   <li>ShopEntry.price 是 final 字段，且 {@code getShopEntries()} 在客户端/服务端都会调用，
 *       在 ShopEntry 层面根据玩家身份返回不同价格会导致两端 List 不一致（"购买A拿到B"错位）</li>
 *   <li>DynamicShopComponent 自动同步，UI 显示与实际扣费完全一致，零额外代码</li>
 * </ul>
 *
 * <p>折扣范围：所有 {@link Dim201ModItems#ABILITY_CARDS} 集合中的卡牌。
 * 由于能力卡牌仅在 {@link ChimataRole} 的商店中出售，折扣实际上只会在天弓千亦商店生效，
 * 不会影响杀手在其他商店的购买（其他商店不卖能力卡牌）。
 *
 * <p>折扣公式：{@code effective = max(0, round(basePrice * 0.5) - 0)} = basePrice / 2。
 * 价格为 0 的卡牌（如空白卡牌）折扣后仍为 0，不受影响。
 */
public final class KillerCardDiscountManager {

    /** 杀手折扣百分比：50% off */
    private static final int KILLER_DISCOUNT_PERCENT = 50;

    private KillerCardDiscountManager() {
    }

    /**
     * 如果玩家被分配的角色是杀手（{@code canUseKiller() == true}），
     * 为其设置所有能力卡牌的 50% 折扣。
     *
     * <p>在 {@code ModdedRoleAssigned.EVENT} 中调用（服务端）。
     *
     * @param player 被分配角色的玩家
     * @param role   分配的角色
     */
    public static void applyDiscountIfKiller(ServerPlayer player, SRERole role) {
        if (player == null || role == null) return;
        if (player.level().isClientSide) return;
        if (!role.canUseKiller()) return;

        DynamicShopComponent dyn = DynamicShopComponent.KEY.get(player);
        for (Item card : Dim201ModItems.ABILITY_CARDS) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(card);
            if (itemId != null) {
                dyn.setPercentDiscount(itemId, KILLER_DISCOUNT_PERCENT);
            }
        }
    }

    /**
     * 如果玩家被移除的角色是杀手，清除其所有能力卡牌折扣。
     *
     * <p>在 {@code ModdedRoleRemoved.EVENT} 中调用。
     * 防止玩家从杀手切换到非杀手角色后仍享受折扣。
     *
     * @param player 被移除角色的玩家
     * @param role   被移除的角色
     */
    public static void clearDiscountIfKiller(Player player, SRERole role) {
        if (player == null || role == null) return;
        if (player.level().isClientSide) return;
        if (!role.canUseKiller()) return;

        DynamicShopComponent dyn = DynamicShopComponent.KEY.get(player);
        for (Item card : Dim201ModItems.ABILITY_CARDS) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(card);
            if (itemId != null) {
                dyn.clearModifier(itemId);
            }
        }
    }
}
