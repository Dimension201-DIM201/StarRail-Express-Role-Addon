package com.dim201.dim201addon.menu;

import com.dim201.dim201addon.ShionPlayerComponent;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 乞讨会话：由 ShionPlayerComponent.useBegAbility 创建，
 * 绑定一个乞讨者（紫菀）和一个被乞讨者（目标）。
 * 目标打开 BeggarMenu；目标点击施舍/拒绝 → 调用 ShionPlayerComponent.onDonate / onReject。
 */
public class ShionBeggarSession {

    private final ServerPlayer beggar;     // 紫菀
    private final ServerPlayer target;     // 被乞讨的玩家
    private boolean resolved = false;

    private ShionBeggarSession(ServerPlayer beggar, ServerPlayer target) {
        this.beggar = beggar;
        this.target = target;
    }

    /** 给目标打开乞讨菜单（参照 CardTradeSession 实现） */
    public static void openBeggarMenu(ServerPlayer beggar, ServerPlayer target) {
        ShionBeggarSession session = new ShionBeggarSession(beggar, target);

        target.openMenu(new ExtendedScreenHandlerFactory<BeggarMenu.BeggarData>() {
            @Override
            public Component getDisplayName() {
                return Component.literal("§5" + beggar.getName().getString() + " §f向你伸出了碗...");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
                return new BeggarMenu(syncId, playerInventory,
                        new BeggarMenu.BeggarData(beggar.getUUID(), beggar.getName().getString()),
                        session);
            }

            @Override
            public BeggarMenu.BeggarData getScreenOpeningData(ServerPlayer player) {
                return new BeggarMenu.BeggarData(beggar.getUUID(), beggar.getName().getString());
            }
        });
    }

    /** 目标点击了按钮（由 BeggarMenu.clickMenuButton 调用） */
    public void onTargetButton(ServerPlayer targetSp, int buttonId) {
        if (resolved) return;
        if (!targetSp.getUUID().equals(target.getUUID())) return;
        resolved = true;

        if (buttonId == BeggarMenu.BUTTON_DONATE) {
            ShionPlayerComponent.onDonate(beggar, target);
        } else if (buttonId == BeggarMenu.BUTTON_REJECT) {
            ShionPlayerComponent.onReject(beggar, target);
        }

        // 关闭菜单
        if (targetSp.connection != null) {
            targetSp.closeContainer();
        }
    }

    /** 目标主动关闭菜单 → 视为拒绝 */
    public void onClosed(ServerPlayer player) {
        if (resolved) return;
        resolved = true;
        if (player.getUUID().equals(target.getUUID())) {
            ShionPlayerComponent.onReject(beggar, target);
        }
    }
}
