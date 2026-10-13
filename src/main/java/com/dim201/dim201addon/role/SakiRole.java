package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SakiPlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 骊驹早鬼（Saki）
 * 杀手阵营
 * 商店：130刀 / 285左轮 / 200绳索 / 80开锁器 / 35撬棍 / 关灯（默认 200）
 * 技能：劲疾技「三角追击」
 */
public class SakiRole {

    public static final ResourceLocation SAKI_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "saki");

    public static SRERole SAKI;

    public static void register() {
        SAKI = TMMRoles.registerRole(new SakiRoleImpl());
    }

    private static class SakiRoleImpl extends NormalRole {

        public SakiRoleImpl() {
            super(
                    SAKI_ID,
                    0xFF555555,              // 深灰色（用户指定，DARK_GRAY = §8）
                    false,                   // isInnocent：false → 杀手
                    true,                    // canUseKiller：可使用杀手能力
                    MoodType.FAKE,           // 假心情
                    -1,                      // maxSprintTime：默认
                    false                    // hideScoreboard：能看时间
            );

            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setCanSeeTeammateKillerRole(true)
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public @NotNull List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public @NotNull List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 1. 刀 130（三级回退：noellesroles → starrailexpress → trainmurdermystery，不同 SRE 版本物品 id 不同）
            ItemStack knife = stackById("noellesroles:knife", "§c§l刀");
            if (knife.getItem() == Items.AIR) knife = stackById("starrailexpress:knife", "§c§l刀");
            if (knife.getItem() == Items.AIR) knife = stackById("trainmurdermystery:knife", "§c§l刀");
            if (knife.getItem() != Items.AIR) entries.add(new ShopEntry(knife, 130, ShopEntry.Type.WEAPON));

            // 2. 左轮手枪 285（三级回退）
            ItemStack revolver = stackById("noellesroles:revolver", "§c§l左轮手枪");
            if (revolver.getItem() == Items.AIR) revolver = stackById("starrailexpress:revolver", "§c§l左轮手枪");
            if (revolver.getItem() == Items.AIR) revolver = stackById("trainmurdermystery:revolver", "§c§l左轮手枪");
            if (revolver.getItem() != Items.AIR) entries.add(new ShopEntry(revolver, 285, ShopEntry.Type.WEAPON));

            // 3. 绳索 200（用户指定：从 150 提升至 200；三级回退，noellesroles:rope 是正确 id，放第一个）
            ItemStack rope = stackById("noellesroles:rope", "§7§l绳索");
            if (rope.getItem() == Items.AIR) rope = stackById("starrailexpress:rope", "§7§l绳索");
            if (rope.getItem() == Items.AIR) rope = stackById("trainmurdermystery:rope", "§7§l绳索");
            if (rope.getItem() != Items.AIR) entries.add(new ShopEntry(rope, 200, ShopEntry.Type.TOOL));

            // 4. 开锁器 80（三级回退）
            ItemStack lockpick = stackById("noellesroles:lockpick", "§7开锁器");
            if (lockpick.getItem() == Items.AIR) lockpick = stackById("starrailexpress:lockpick", "§7开锁器");
            if (lockpick.getItem() == Items.AIR) lockpick = stackById("trainmurdermystery:lockpick", "§7开锁器");
            if (lockpick.getItem() != Items.AIR) entries.add(new ShopEntry(lockpick, 80, ShopEntry.Type.TOOL));

            // 5. 撬棍 35（三级回退）
            ItemStack crowbar = stackById("noellesroles:crowbar", "§7撬棍");
            if (crowbar.getItem() == Items.AIR) crowbar = stackById("starrailexpress:crowbar", "§7撬棍");
            if (crowbar.getItem() == Items.AIR) crowbar = stackById("trainmurdermystery:crowbar", "§7撬棍");
            if (crowbar.getItem() != Items.AIR) entries.add(new ShopEntry(crowbar, 35, ShopEntry.Type.TOOL));

            // 6. 关灯 200（SRE 基础杀手商店标准选项：购买后立即触发全图关灯，不给物品，内置购买冷却）
            //    —— 对应 SRE 原生 RoleShopHandler 写法：匿名重写 onBuy → SREPlayerShopComponent.useBlackout(player)
            //    价格：SRE 更新日志 L167 明确「所有通用杀手商店的关灯价格均调到 200 金币」
            entries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 200, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });

            return entries;
        }

        private static void addWeaponShop(List<ShopEntry> entries, String id, String name, int price) {
            ItemStack stack = stackById(id, name);
            if (stack.getItem() != Items.AIR) {
                entries.add(new ShopEntry(stack, price, ShopEntry.Type.WEAPON));
            }
        }

        private static void addToolShop(List<ShopEntry> entries, String id, String name, int price) {
            ItemStack stack = stackById(id, name);
            if (stack.getItem() != Items.AIR) {
                entries.add(new ShopEntry(stack, price, ShopEntry.Type.TOOL));
            }
        }

        private static ItemStack stackById(String id, String name) {
            ResourceLocation rl = ResourceLocation.parse(id);
            ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
            if (s.getItem() != Items.AIR) {
                s.set(DataComponents.ITEM_NAME, Component.literal(name));
            }
            return s;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            SakiPlayerComponent comp = ModComponents.SAKI.get(player);
            if (comp == null) return false;
            return comp.useAbility(player);
        }

        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, SAKI)) return;
            if (!Dim201Addon.isInGameAlive(player)) return;
            SakiPlayerComponent comp = ModComponents.SAKI.get(player);
            comp.tick(player);
        }
    }
}
