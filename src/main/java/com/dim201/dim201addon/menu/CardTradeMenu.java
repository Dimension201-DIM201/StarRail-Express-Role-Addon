package com.dim201.dim201addon.menu;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.CardUtils;
import com.dim201.dim201addon.menu.ModMenus.TradeData;
import io.wifi.starrailexpress.content.gui.CustomInventoryMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 能力卡牌交易菜单
 *
 * <p>原理：两端 Menu 持有各自的 {@link SimpleContainer}，服务端通过原生
 * {@link AbstractContainerMenu#broadcastChanges}（每 tick 由 Player.tick 调用）
 * 自动检测交易槽变化并通过 ContainerSynchronizer 同步给两端客户端。
 *
 * <p>权限：
 * <ul>
 *   <li>发起方：交易槽可放入（限能力卡牌）、可取出；背包可操作</li>
 *   <li>接收方：交易槽只读；背包可操作；可点击购买/取消按钮</li>
 * </ul>
 *
 * <p>按钮通过原生 {@link #clickMenuButton} 机制，客户端→服务端自动发包。
 * 关闭通过 {@link #removed} 处理，任意一方关闭则退还物品并强制关闭另一方。
 *
 * <p>实现 {@link CustomInventoryMenu} 标记接口：
 * SRE 的 {@code AbstractContainerMenuMixin} 会在 {@code doClick} HEAD 处取消
 * 非白名单菜单的点击事件（含 QUICK_CRAFT 拖动），实现该接口使 SRE 放行本菜单。
 */
public class CardTradeMenu extends AbstractContainerMenu implements CustomInventoryMenu {

    /** 按钮 ID：购买 */
    public static final int BUTTON_BUY = 0;
    /** 按钮 ID：取消 */
    public static final int BUTTON_CANCEL = 1;

    private final boolean isInitiator;
    private final String partnerName;
    private final boolean buyerIsKiller;
    private final boolean sellerIsKiller;
    private final SimpleContainer tradeSlot;
    /** 服务端会话引用（客户端为 null） */
    private final CardTradeSession session;

    /** 客户端构造（ExtendedScreenHandlerType factory 调用） */
    public CardTradeMenu(int syncId, Inventory playerInventory, TradeData data) {
        this(syncId, playerInventory, data, null);
    }

    /** 服务端构造（ExtendedScreenHandlerFactory.createMenu 调用） */
    public CardTradeMenu(int syncId, Inventory playerInventory, TradeData data, CardTradeSession session) {
        super(ModMenus.CARD_TRADE, syncId);
        this.isInitiator = data.isInitiator();
        this.partnerName = data.partnerName();
        this.buyerIsKiller = data.buyerIsKiller();
        this.sellerIsKiller = data.sellerIsKiller();
        this.session = session;
        // 服务端用共享容器；客户端创建临时容器（由 ContainerSynchronizer 自动填充）
        this.tradeSlot = (session != null) ? session.tradeSlot : new SimpleContainer(1);

        initSlots(playerInventory);
        // 不在此处注册 listener：vanilla 的 Player.tick 每 tick 自动调用
        // broadcastChanges() 同步，手动触发会污染客户端 remoteSlots/remoteCarried 基线，
        // 导致拖动（QUICK_CRAFT）过程中客户端预测与服务端基线错乱，表现为无法拖动。
    }

    /** 初始化槽位：1交易槽 + 9快捷栏（不含主背包27格，因SRE对局中无法访问主背包） */
    private void initSlots(Inventory playerInventory) {
        // 交易槽（slot 0）
        if (isInitiator) {
            addSlot(new InitiatorTradeSlot(tradeSlot, 0, 80, 20));
        } else {
            addSlot(new ReadOnlyTradeSlot(tradeSlot, 0, 80, 20));
        }
        // 玩家快捷栏（slot 1-9，对应 Inventory.items 0-8）
        // 原主背包27格不暴露：SRE对局中玩家无法查看/使用主背包物品，
        // 若允许放入，会导致物品"消失"无法取出。
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 55));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index == 0) {
            // 交易槽 → 快捷栏（slot 1-9）
            if (!moveItemStackTo(original, 1, 10, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 快捷栏 → 交易槽（仅发起方）
            if (isInitiator) {
                if (!moveItemStackTo(original, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 按钮点击处理（原生机制，客户端自动发包）
     *
     * @param buttonId 0=购买, 1=取消
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (player.level().isClientSide) return true;
        if (session == null) return true;
        // 只有接收方能点按钮
        if (player != session.receiver) return false;

        if (buttonId == BUTTON_BUY) {
            confirmTrade();
        } else if (buttonId == BUTTON_CANCEL) {
            cancelTrade();
        }
        return true;
    }

    /**
     * 接收方点购买：检查金币→扣金币→给发起方→转移卡牌→天弓千亦奖励→关闭双方
     *
     * <p>价格0的卡牌（如空白）为免费交易，跳过金币检查与扣除。
     *
     * <p>杀手交易对称惩罚（与商店半价购买对称）：
     * <ul>
     *   <li>杀手买家：支付 50% 金币，卖家获得全价（差额凭空产生，与商店购买一致）</li>
     *   <li>杀手卖家：获得 50% 金币，买家支付全价（差额蒸发，避免套利）</li>
     * </ul>
     *
     * <p>提示规则：非杀手玩家只看到原价金额，不看到任何"降价"信息；
     * 杀手玩家能看到自己享受的半价/惩罚提示。
     */
    private void confirmTrade() {
        ItemStack stack = session.tradeSlot.getItem(0);
        if (stack.isEmpty()) {
            CardUtils.notify(session.receiver, "§c交易槽为空");
            return;
        }

        // 查询卡牌价格（非能力卡牌返回0，视为不可交易）
        if (!Dim201ModItems.isAbilityCard(stack)) {
            CardUtils.notify(session.receiver, "§c该物品不可交易");
            return;
        }
        int price = Dim201ModItems.getCardPrice(stack);

        // 查询双方角色（用于判断杀手身份）
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(session.initiator.level());
        var initiatorRole = (gameWorld != null) ? gameWorld.getRole(session.initiator) : null;
        var receiverRole = (gameWorld != null) ? gameWorld.getRole(session.receiver) : null;
        boolean sellerIsKiller = initiatorRole != null && initiatorRole.canUseKiller();
        boolean buyerIsKiller = receiverRole != null && receiverRole.canUseKiller();

        // 计算实际支付/获得金额
        // 杀手买家支付半价；杀手卖家获得半价；差额由对方/系统承担
        int buyerPays = buyerIsKiller ? Math.max(0, price / 2) : price;
        int sellerGain = sellerIsKiller ? Math.max(0, price / 2) : price;

        // 价格 > 0 时检查并扣除金币
        if (price > 0) {
            if (CardUtils.getGold(session.receiver) < buyerPays) {
                CardUtils.notify(session.receiver, "§c金币不足，需要 §6" + buyerPays + " §c金币");
                return;
            }
            CardUtils.spendGold(session.receiver, buyerPays);
            CardUtils.addGold(session.initiator, sellerGain);
        }

        // 移除交易槽物品并转移给接收方
        ItemStack traded = session.tradeSlot.removeItemNoUpdate(0);
        if (!session.receiver.getInventory().add(traded)) {
            session.receiver.drop(traded, false);
        }

        session.completed = true;

        // 交易成功提示（只有杀手能看到降价/惩罚，非杀手只看到原价）
        if (price > 0) {
            // 买家提示
            if (buyerIsKiller) {
                CardUtils.notify(session.receiver,
                        "§a购买成功！消耗 §6" + buyerPays + " §a金币 §7(杀手半价)");
            } else {
                CardUtils.notify(session.receiver, "§a购买成功！消耗 §6" + buyerPays + " §a金币");
            }
            // 卖家提示
            if (sellerIsKiller) {
                CardUtils.notify(session.initiator,
                        "§a交易完成！获得 §6" + sellerGain + " §a金币 §7(杀手50%惩罚)");
            } else {
                CardUtils.notify(session.initiator, "§a交易完成！获得 §6" + sellerGain + " §a金币");
            }
        } else {
            CardUtils.notify(session.receiver, "§a交易成功！");
            CardUtils.notify(session.initiator, "§a交易成功！");
        }

        // 天弓千亦交易奖励：与每个不同玩家的第一次交易触发50金币
        // 原理：双向尝试，只有天弓千亦本人且对手是"新对手"时才奖励
        com.dim201.dim201addon.role.ChimataTradeManager.tryRewardTrade(session.initiator, session.receiver);
        com.dim201.dim201addon.role.ChimataTradeManager.tryRewardTrade(session.receiver, session.initiator);

        session.initiator.closeContainer();
        session.receiver.closeContainer();
    }

    /** 接收方点取消：直接关闭双方（removed 会退还物品） */
    private void cancelTrade() {
        session.initiator.closeContainer();
        session.receiver.closeContainer();
    }

    /**
     * 菜单关闭处理
     * 原理：任意一方关闭时，若交易未完成则退还交易槽物品给发起方，
     * 并强制关闭另一方。用 session.closed 标志防止重复处理。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        if (session == null || session.closed) return;
        session.closed = true;

        if (!session.completed) {
            session.returnItemToInitiator();
        }
        // 强制关闭另一方
        ServerPlayer other = (player == session.initiator) ? session.receiver : session.initiator;
        other.closeContainer();
    }

    public boolean isInitiator() { return isInitiator; }
    public String getPartnerName() { return partnerName; }
    public boolean hasTradeItem() { return !tradeSlot.getItem(0).isEmpty(); }

    /** 当前玩家是否为杀手买家（享受半价购买） */
    public boolean isBuyerKiller() { return !isInitiator && buyerIsKiller; }

    /** 当前玩家是否为杀手卖家（获得50%金币惩罚） */
    public boolean isSellerKiller() { return isInitiator && sellerIsKiller; }

    /** 获取当前交易槽卡牌价格（供 Screen 显示） */
    public int getTradePrice() {
        return Dim201ModItems.getCardPrice(tradeSlot.getItem(0));
    }

    /** 发起方交易槽：只能放入能力卡牌 */
    private static class InitiatorTradeSlot extends Slot {
        public InitiatorTradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return Dim201ModItems.isAbilityCard(stack);
        }
    }

    /** 接收方交易槽：只读，不可放入/取出 */
    private static class ReadOnlyTradeSlot extends Slot {
        public ReadOnlyTradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
        @Override
        public boolean mayPickup(Player player) { return false; }
    }
}
