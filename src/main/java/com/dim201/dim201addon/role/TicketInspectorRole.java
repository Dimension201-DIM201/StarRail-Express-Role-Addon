package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.item.Dim201ModItems;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 补票员 - 平民阵营
 *
 * 原理：
 * 1. 周围10格有人购买实际物品 → Mixin 拦截 tryBuy 成功回调，
 *    调用 TicketInspectorPlayerComponent.onPlayerBoughtNearby() 给范围内补票员发放发票物品
 * 2. 发票是实际物品（dim201addon:invoice），右键使用：扣除原价×150%金币 → 消耗发票 → 获得对应物品并揭晓
 * 3. 商店通过 getShopEntries() 直接返回「清空发票」选项（无需 Mixin 注入）
 * 4. 无初始物品
 */
public class TicketInspectorRole {

    public static final ResourceLocation TICKET_INSPECTOR_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "ticket_inspector"
    );

    public static SRERole TICKET_INSPECTOR;

    public static void register() {
        TICKET_INSPECTOR = TMMRoles.registerRole(new TicketInspectorRoleImpl());
    }

    private static class TicketInspectorRoleImpl extends NormalRole {

        public TicketInspectorRoleImpl() {
            super(
                TICKET_INSPECTOR_ID,
                0xFF32CD32,          // 酸橙绿
                true,                // isInnocent = true（平民阵营）
                false,               // canUseKiller = false
                MoodType.REAL,       // 真实心情
                400,                  // 默认冲刺时间
                false                // 能看到剩余时间
            );
            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setDefaultMax(1);   // 每局最多 1 个补票员
        }

        /**
         * 直接返回「清空发票」商店条目。
         * ShopEntry.onBuy(Player) 回调自带 player 参数，可直接清空背包发票，无需 Mixin。
         */
        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 清空发票按钮（0 金币）
            ItemStack clearBtn = new ItemStack(Items.BARRIER);
            clearBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("§c清空所有发票").withStyle(ChatFormatting.RED));
            List<Component> lores = new ArrayList<>();
            lores.add(Component.literal("花费：0 金币")
                .withStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.GRAY));
            lores.add(Component.literal("丢弃身上所有神秘发票")
                .withStyle(Style.EMPTY.withItalic(false)).withStyle(ChatFormatting.DARK_RED));
            clearBtn.set(DataComponents.LORE, new ItemLore(lores));

            entries.add(new ShopEntry(clearBtn, 0, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(@NotNull Player p) {
                    return true;
                }

                @Override
                public boolean onBuy(@NotNull Player p) {
                    int count = 0;
                    for (int i = 0; i < p.getInventory().items.size(); i++) {
                        ItemStack s = p.getInventory().items.get(i);
                        if (s != null && s.is(Dim201ModItems.INVOICE)) {
                            p.getInventory().items.set(i, ItemStack.EMPTY);
                            count++;
                        }
                    }
                    for (int i = 0; i < p.getInventory().offhand.size(); i++) {
                        ItemStack s = p.getInventory().offhand.get(i);
                        if (s != null && s.is(Dim201ModItems.INVOICE)) {
                            p.getInventory().offhand.set(i, ItemStack.EMPTY);
                            count++;
                        }
                    }
                    p.displayClientMessage(
                        Component.literal("§a已清空 " + count + " 张发票").withStyle(ChatFormatting.GREEN),
                        true
                    );
                    return true;
                }
            });

            return entries;
        }

        /** G 技能：汇报当前发票持有数 */
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            var comp = ModComponents.TICKET_INSPECTOR.get(player);
            int n = comp != null ? comp.getUnusedInvoiceCount() : 0;
            player.displayClientMessage(
                Component.literal("§a你手中持有 " + n + " 张发票。右键发票即可补票。")
                    .withStyle(ChatFormatting.WHITE),
                true
            );
            return true;
        }
    }
}
