package com.dim201.dim201addon.menu;

import com.dim201.dim201addon.contract.ContractPower;
import com.dim201.dim201addon.contract.DemonContractManager;
import io.wifi.starrailexpress.content.gui.CustomInventoryMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 恶魔契约菜单（阶段一：索取）
 *
 * <p>每次契约由服务端随机抽取 {@link #MAX_PICKS} 个力量供选择（{@code offered}），
 * 两端持有的列表一致。力量选择与签订全部走原生 {@link #clickMenuButton}：
 * <ul>
 *   <li>按钮 0~{@code offered.size()-1}：切换该力量的勾选</li>
 *   <li>按钮 {@link #BUTTON_SIGN}：签订契约（不可反悔，进入阶段二）</li>
 * </ul>
 *
 * <p>实现 {@link CustomInventoryMenu} 以通过 SRE 对局内的菜单点击白名单。
 */
public class DemonContractMenu extends AbstractContainerMenu implements CustomInventoryMenu {

    /** 签订契约按钮 */
    public static final int BUTTON_SIGN = 99;
    /** 单次契约最多索取的力量数（即恶魔抽取的数量上限） */
    public static final int MAX_PICKS = 4;

    /** 快捷栏槽位在面板内的坐标（须与 DemonContractScreen 的面板布局一致，槽位独占底部区域） */
    public static final int SLOT_X = 8;
    public static final int SLOT_Y = 196;

    /** 本次契约可供选择的力量（服务端抽取后经 ContractData 同步） */
    private final List<ContractPower> offered;
    /** 服务端构造标记（区分客户端实例） */
    private final boolean serverSide;

    /** 服务端构造：传入抽取好的力量池 */
    public DemonContractMenu(int syncId, Inventory playerInventory, List<ContractPower> offered) {
        this(syncId, playerInventory, offered, true);
    }

    /** 客户端工厂构造：从同步数据恢复力量池 */
    public DemonContractMenu(int syncId, Inventory playerInventory, ModMenus.ContractData data) {
        this(syncId, playerInventory,
                data.powerIds().stream().map(ContractPower::byId).filter(java.util.Objects::nonNull).toList(),
                false);
    }

    private DemonContractMenu(int syncId, Inventory playerInventory,
                              List<ContractPower> offered, boolean serverSide) {
        super(ModMenus.DEMON_CONTRACT, syncId);
        this.offered = List.copyOf(offered);
        this.serverSide = serverSide;
        // 仅暴露快捷栏（无交易槽），独占面板底部槽位区
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, SLOT_X + col * 18, SLOT_Y));
        }
    }

    /** 供客户端 Screen 渲染按钮 */
    public List<ContractPower> getOfferedPowers() {
        return offered;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (player.level().isClientSide) return true;
        if (!(player instanceof ServerPlayer sp)) return true;

        if (buttonId >= 0 && buttonId < offered.size()) {
            DemonContractManager.togglePower(sp, offered.get(buttonId));
            return true;
        }
        if (buttonId == BUTTON_SIGN) {
            DemonContractManager.submitContract(sp);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (serverSide && player instanceof ServerPlayer sp) {
            DemonContractManager.onMenuClosed(sp);
        }
    }
}
