package com.dim201.dim201addon.menu;

import com.dim201.dim201addon.menu.ModMenus.TradeData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 能力卡牌交易会话（服务端）
 *
 * <p>原理：持有共享的 {@link SimpleContainer}，两端 Menu 绑定同一容器，
 * 物品变化通过 ContainerSynchronizer 自动同步到两端客户端。
 *
 * <p>状态机：
 * <ul>
 *   <li>正常交易中：双方 Menu 打开，发起方可放卡牌，接收方可点按钮</li>
 *   <li>completed=true：交易已完成（接收方点购买），物品已转移</li>
 *   <li>closed=true：会话已关闭（任意一方关闭 Menu），防止重复处理</li>
 * </ul>
 */
public class CardTradeSession {

    /** 共享交易槽（1格），两端 Menu 通过 addListener 同步 */
    public final SimpleContainer tradeSlot = new SimpleContainer(1);

    public final ServerPlayer initiator;
    public final ServerPlayer receiver;

    /** 交易是否已完成（接收方点了购买） */
    public boolean completed = false;

    /** 会话是否已关闭（防止 removed 重复处理） */
    public boolean closed = false;

    public CardTradeSession(ServerPlayer initiator, ServerPlayer receiver) {
        this.initiator = initiator;
        this.receiver = receiver;
    }

    /**
     * 退还交易槽物品给发起方
     * 用于交易取消/关闭时，把卡牌放回发起方背包
     */
    public void returnItemToInitiator() {
        ItemStack stack = tradeSlot.removeItemNoUpdate(0);
        if (!stack.isEmpty() && !initiator.getInventory().add(stack)) {
            // 背包满则掉落
            initiator.drop(stack, false);
        }
    }

    /**
     * 为双方玩家打开交易菜单
     *
     * <p>原理：使用 Fabric 的 {@link ExtendedScreenHandlerFactory}，通过 getScreenOpeningData
     * 返回 TradeData（含双方杀手身份），客户端 MenuType factory 读取后创建对应 Menu。
     * 杀手身份在服务端查询后传给客户端，客户端据此显示"半价"/"50%惩罚"提示。
     */
    public void openBoth() {
        // 查询双方杀手身份（客户端无法查角色信息，必须服务端传入）
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(initiator.level());
        boolean sellerIsKiller;
        boolean buyerIsKiller;
        if (gameWorld != null) {
            var initRole = gameWorld.getRole(initiator);
            var recvRole = gameWorld.getRole(receiver);
            sellerIsKiller = initRole != null && initRole.canUseKiller();
            buyerIsKiller = recvRole != null && recvRole.canUseKiller();
        } else {
            sellerIsKiller = false;
            buyerIsKiller = false;
        }
        // 转为 final 供匿名内部类引用
        final boolean sellerKiller = sellerIsKiller;
        final boolean buyerKiller = buyerIsKiller;

        // 发起方：isInitiator=true，对方名=接收方
        initiator.openMenu(new ExtendedScreenHandlerFactory<TradeData>() {
            @Override
            public Component getDisplayName() {
                return Component.literal("卡牌交易");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
                return new CardTradeMenu(containerId, inv,
                        new TradeData(true, receiver.getGameProfile().getName(),
                                buyerKiller, sellerKiller),
                        CardTradeSession.this);
            }

            @Override
            public TradeData getScreenOpeningData(ServerPlayer player) {
                return new TradeData(true, receiver.getGameProfile().getName(),
                        buyerKiller, sellerKiller);
            }
        });

        // 接收方：isInitiator=false，对方名=发起方
        receiver.openMenu(new ExtendedScreenHandlerFactory<TradeData>() {
            @Override
            public Component getDisplayName() {
                return Component.literal("卡牌交易");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
                return new CardTradeMenu(containerId, inv,
                        new TradeData(false, initiator.getGameProfile().getName(),
                                buyerKiller, sellerKiller),
                        CardTradeSession.this);
            }

            @Override
            public TradeData getScreenOpeningData(ServerPlayer player) {
                return new TradeData(false, initiator.getGameProfile().getName(),
                        buyerKiller, sellerKiller);
            }
        });
    }
}
