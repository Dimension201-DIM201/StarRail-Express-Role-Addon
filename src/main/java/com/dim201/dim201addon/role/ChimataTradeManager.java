package com.dim201.dim201addon.role;

import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天弓千亦能力卡牌交易奖励与限购系统
 *
 * <p>原理：使用静态 Map 记录本局交易状态，对局结束时清空。
 * <ul>
 *   <li>{@link #BLANK_BUYERS}：已购买空白卡牌的玩家，每局限购1次</li>
 *   <li>{@link #TRADE_PARTNERS}：天弓千亦已交易过的对手玩家UUID集合，
 *       与每个不同玩家的第一次交易触发50金币奖励</li>
 * </ul>
 *
 * <p>线程安全：使用 ConcurrentHashMap 保证并发访问安全。
 */
public final class ChimataTradeManager {

    /** 已购买空白卡牌的玩家UUID（每局限购1次） */
    private static final ConcurrentHashMap<UUID, Boolean> BLANK_BUYERS = new ConcurrentHashMap<>();

    /**
     * 天弓千亦已交易过的对手玩家UUID集合
     * 原理：key=天弓千亦UUID，value=已交易过的对手UUID集合。
     * 与每个不同对手的第一次交易触发奖励，重复交易不触发。
     */
    private static final ConcurrentHashMap<UUID, Set<UUID>> TRADE_PARTNERS = new ConcurrentHashMap<>();

    /** 交易奖励金币数 */
    public static final int TRADE_REWARD = 50;

    private ChimataTradeManager() {}

    /**
     * 判断玩家是否可以购买空白卡牌（每局限购1次）
     */
    public static boolean canBuyBlank(ServerPlayer sp) {
        return !BLANK_BUYERS.containsKey(sp.getUUID());
    }

    /**
     * 标记玩家已购买空白卡牌
     */
    public static void markBlankBought(ServerPlayer sp) {
        BLANK_BUYERS.put(sp.getUUID(), Boolean.TRUE);
    }

    /**
     * 尝试给予天弓千亦交易奖励
     *
     * <p>原理：
     * <ul>
     *   <li>仅天弓千亦本人获得奖励，交易对手不获得</li>
     *   <li>与每个不同玩家的第一次交易触发奖励（key=天弓千亦UUID，对手不在集合中）</li>
     *   <li>重复与同一玩家交易不再触发</li>
     * </ul>
     *
     * @param chimata 天弓千亦玩家
     * @param partner 交易对手玩家
     * @return true 表示成功给予奖励
     */
    public static boolean tryRewardTrade(ServerPlayer chimata, ServerPlayer partner) {
        // 仅天弓千亦本人获得奖励
        if (!isChimata(chimata)) return false;
        // 不能与自己交易
        if (chimata.getUUID().equals(partner.getUUID())) return false;

        UUID chimataId = chimata.getUUID();
        Set<UUID> partners = TRADE_PARTNERS.computeIfAbsent(chimataId,
                k -> ConcurrentHashMap.newKeySet());

        // 原子性检查+添加：如果对手已在集合中，说明已交易过，不再奖励
        if (!partners.add(partner.getUUID())) return false;

        CardUtils.addGold(chimata, TRADE_REWARD);
        CardUtils.notify(chimata, "§d天弓千亦§r§7 · 与新玩家交易奖励 §6" + TRADE_REWARD + " §7金币！");
        return true;
    }

    /**
     * 判断玩家是否为天弓千亦
     */
    public static boolean isChimata(Player player) {
        if (player == null || player.level() == null) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null) return false;
        var role = gw.getRole(player);
        return role != null
                && role.identifier().toString().equals(ChimataRole.CHIMATA_ID.toString());
    }

    /**
     * 对局结束时清空所有状态
     */
    public static void reset() {
        BLANK_BUYERS.clear();
        TRADE_PARTNERS.clear();
    }
}
