package com.dim201.dim201addon.menu;

import com.dim201.dim201addon.Dim201Addon;
import io.wifi.starrailexpress.content.gui.CustomInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * 乞讨菜单：紫菀发起乞讨 → 目标玩家打开这个菜单
 * 中间显示一个「碗」图标；下方两个按钮：施舍（ID 0）、拒绝（ID 1）
 *
 * <p>按钮交互使用原生 clickMenuButton 机制，客户端点击按钮后服务端
 * 会收到 clickMenuButton(buttonId) 回调，无需额外网络包。
 */
public class BeggarMenu extends AbstractContainerMenu implements CustomInventoryMenu {

    public static final int BUTTON_DONATE = 0;
    public static final int BUTTON_REJECT = 1;

    /** 乞讨者 UUID（即紫菀） */
    private final UUID beggarUuid;
    private final String beggarName;

    /** 服务端会话引用（客户端为 null） */
    private final ShionBeggarSession session;

    /** 客户端构造（由 ExtendedScreenHandlerType factory 调用） */
    public BeggarMenu(int syncId, Inventory playerInventory, BeggarData data) {
        this(syncId, playerInventory, data, null);
    }

    /** 服务端构造（由 openBeggarMenu → createMenu 调用） */
    public BeggarMenu(int syncId, Inventory playerInventory, BeggarData data, ShionBeggarSession session) {
        super(ModMenus.BEGGAR, syncId);
        this.beggarUuid = data.beggarUuid();
        this.beggarName = data.beggarName();
        this.session = session;
        // 菜单不放槽位（只有按钮），暴露玩家快捷栏避免警告
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 108));
        }
    }

    public UUID getBeggarUuid() { return beggarUuid; }
    public String getBeggarName() { return beggarName; }

    /** 客户端按钮点击 → 服务端通过 clickMenuButton 自动调用 */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (session == null) return false; // 客户端不处理
        if (!(player instanceof net.minecraft.server.level.ServerPlayer targetSp)) return false;

        session.onTargetButton(targetSp, buttonId);
        return true;
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
        if (session != null) {
            session.onClosed((net.minecraft.server.level.ServerPlayer) player);
        }
    }

    // =============== 菜单数据（服务端→客户端） ===============

    public record BeggarData(UUID beggarUuid, String beggarName) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BeggarData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, d) -> {
                            buf.writeUUID(d.beggarUuid);
                            buf.writeUtf(d.beggarName);
                        },
                        buf -> new BeggarData(buf.readUUID(), buf.readUtf())
                );
    }
}
