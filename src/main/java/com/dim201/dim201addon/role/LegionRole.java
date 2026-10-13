package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.LegionPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 军团 Legion（NormalRole 子类，阵营：杀手）
 */
public class LegionRole {

    public static final ResourceLocation LEGION_ID =
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "legion");
    public static SRERole LEGION;

    public static void register() {
        LEGION = TMMRoles.registerRole(new LegionRoleImpl());
        // 军团无配对 Occupation 角色，不调 addOccupationRole
    }

    public static void resetRoleState() { }

    public static void registerEvents() { }

    // ============================================================
    // 5 张「军团混音带」ShopEntry（用户新增）
    //  - 统一使用 Items.MUSIC_DISC_11（11号唱片）作为图标，价格都是 100 金币
    //  - onBuy 校验：已买过 → 拒绝（钱不退）；金币不够 → 拒绝；否则标志置 true + 扣钱成功
    // ============================================================
    public static class TapeFrankShopEntry extends ShopEntry {
        private static final int PRICE = 100;
        public static TapeFrankShopEntry create() {
            ItemStack icon = new ItemStack(Items.MUSIC_DISC_11);
            try {
                icon.set(DataComponents.ITEM_NAME, Component.literal("§f§l弗兰克的混音带 §8[§e100 金币§8]"));
                List<Component> loreList = new ArrayList<>();
                loreList.add(Component.literal("§7技能期间：手持军刀右键任意门即可永久撬开（同商店撬棍：开启后无法关闭）"));
                icon.set(DataComponents.LORE, new ItemLore(loreList));
            } catch (Throwable ignored) {}
            return new TapeFrankShopEntry(icon, PRICE, Type.TOOL);
        }
        public TapeFrankShopEntry(ItemStack stack, int price, Type type) { super(stack, price, type); }
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer sp)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
            if (comp == null) return false;
            if (comp.tapeFrankPurchased) {
                LegionPlayerComponent.LegionSendActionBar(sp, "§c购买失败！你已经买过「弗兰克的混音带」");
                return false;
            }
            try {
                io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp);
                if (shop != null && shop.balance < PRICE) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§c金币不足！需要 §6" + PRICE + " §f金币，你当前只有 §6" + shop.balance + " §f金币");
                    return false;
                }
            } catch (Throwable ignored) {}
            comp.tapeFrankPurchased = true;
            comp.sync();
            LegionPlayerComponent.LegionSendActionBar(sp, "§a购买成功！§f已装备「弗兰克的混音带」。野性狂暴技能期间，手持军刀右键任意门即可永久撬开（同商店撬棍，开启后无法关闭）");
            return true;
        }
    }

    public static class TapeJollyShopEntry extends ShopEntry {
        private static final int PRICE = 100;
        public static TapeJollyShopEntry create() {
            ItemStack icon = new ItemStack(Items.MUSIC_DISC_11);
            try {
                icon.set(DataComponents.ITEM_NAME, Component.literal("§f§l朱莉的混音带 §8[§e100 金币§8]"));
                List<Component> loreList = new ArrayList<>();
                loreList.add(Component.literal("§7技能期间：开启野性狂暴时初始速度为 速度 II（上限仍为 速度 V）"));
                icon.set(DataComponents.LORE, new ItemLore(loreList));
            } catch (Throwable ignored) {}
            return new TapeJollyShopEntry(icon, PRICE, Type.TOOL);
        }
        public TapeJollyShopEntry(ItemStack stack, int price, Type type) { super(stack, price, type); }
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer sp)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
            if (comp == null) return false;
            if (comp.tapeJollyPurchased) {
                LegionPlayerComponent.LegionSendActionBar(sp, "§c购买失败！你已经买过「朱莉的混音带」");
                return false;
            }
            try {
                io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp);
                if (shop != null && shop.balance < PRICE) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§c金币不足！需要 §6" + PRICE + " §f金币，你当前只有 §6" + shop.balance + " §f金币");
                    return false;
                }
            } catch (Throwable ignored) {}
            comp.tapeJollyPurchased = true;
            comp.sync();
            LegionPlayerComponent.LegionSendActionBar(sp, "§a购买成功！§f已装备「朱莉的混音带」。下一次开启野性狂暴，初始速度直接到达 速度 II");
            return true;
        }
    }

    public static class TapeSusieShopEntry extends ShopEntry {
        private static final int PRICE = 100;
        public static TapeSusieShopEntry create() {
            ItemStack icon = new ItemStack(Items.MUSIC_DISC_11);
            try {
                icon.set(DataComponents.ITEM_NAME, Component.literal("§f§l苏西的混音带 §8[§e100 金币§8]"));
                List<Component> loreList = new ArrayList<>();
                loreList.add(Component.literal("§7技能期间：每次击中玩家，使 16 格范围内所有玩家降低 20% 总 san 值（固定扣 20% 最大值）"));
                icon.set(DataComponents.LORE, new ItemLore(loreList));
            } catch (Throwable ignored) {}
            return new TapeSusieShopEntry(icon, PRICE, Type.TOOL);
        }
        public TapeSusieShopEntry(ItemStack stack, int price, Type type) { super(stack, price, type); }
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer sp)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
            if (comp == null) return false;
            if (comp.tapeSusiePurchased) {
                LegionPlayerComponent.LegionSendActionBar(sp, "§c购买失败！你已经买过「苏西的混音带」");
                return false;
            }
            try {
                io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp);
                if (shop != null && shop.balance < PRICE) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§c金币不足！需要 §6" + PRICE + " §f金币，你当前只有 §6" + shop.balance + " §f金币");
                    return false;
                }
            } catch (Throwable ignored) {}
            comp.tapeSusiePurchased = true;
            comp.sync();
            LegionPlayerComponent.LegionSendActionBar(sp, "§a购买成功！§f已装备「苏西的混音带」。技能期间每次击中，16 格范围内所有玩家降低 20% 总 san 值（满 san 玩家单次扣 20）");
            return true;
        }
    }

    public static class TapeJoeyShopEntry extends ShopEntry {
        private static final int PRICE = 100;
        public static TapeJoeyShopEntry create() {
            ItemStack icon = new ItemStack(Items.MUSIC_DISC_11);
            try {
                icon.set(DataComponents.ITEM_NAME, Component.literal("§f§l乔伊的混音带 §8[§e100 金币§8]"));
                List<Component> loreList = new ArrayList<>();
                loreList.add(Component.literal("§7技能期间：每次击中玩家，给予 16 格范围内所有玩家 3 秒 缓慢 I"));
                icon.set(DataComponents.LORE, new ItemLore(loreList));
            } catch (Throwable ignored) {}
            return new TapeJoeyShopEntry(icon, PRICE, Type.TOOL);
        }
        public TapeJoeyShopEntry(ItemStack stack, int price, Type type) { super(stack, price, type); }
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer sp)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
            if (comp == null) return false;
            if (comp.tapeJoeyPurchased) {
                LegionPlayerComponent.LegionSendActionBar(sp, "§c购买失败！你已经买过「乔伊的混音带」");
                return false;
            }
            try {
                io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp);
                if (shop != null && shop.balance < PRICE) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§c金币不足！需要 §6" + PRICE + " §f金币，你当前只有 §6" + shop.balance + " §f金币");
                    return false;
                }
            } catch (Throwable ignored) {}
            comp.tapeJoeyPurchased = true;
            comp.sync();
            LegionPlayerComponent.LegionSendActionBar(sp, "§a购买成功！§f已装备「乔伊的混音带」。技能期间每次击中，16 格范围内所有玩家获得 3 秒 缓慢 I");
            return true;
        }
    }

    public static class TapeSmokeShopEntry extends ShopEntry {
        private static final int PRICE = 100;
        public static TapeSmokeShopEntry create() {
            ItemStack icon = new ItemStack(Items.MUSIC_DISC_11);
            try {
                icon.set(DataComponents.ITEM_NAME, Component.literal("§f§l冒烟的混音带 §8[§e100 金币§8]"));
                List<Component> loreList = new ArrayList<>();
                loreList.add(Component.literal("§7技能期间：每次击中玩家，给予 16 格范围内所有玩家 1 秒失明"));
                icon.set(DataComponents.LORE, new ItemLore(loreList));
            } catch (Throwable ignored) {}
            return new TapeSmokeShopEntry(icon, PRICE, Type.TOOL);
        }
        public TapeSmokeShopEntry(ItemStack stack, int price, Type type) { super(stack, price, type); }
        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer sp)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
            if (comp == null) return false;
            if (comp.tapeSmokePurchased) {
                LegionPlayerComponent.LegionSendActionBar(sp, "§c购买失败！你已经买过「冒烟的混音带」");
                return false;
            }
            try {
                io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp);
                if (shop != null && shop.balance < PRICE) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§c金币不足！需要 §6" + PRICE + " §f金币，你当前只有 §6" + shop.balance + " §f金币");
                    return false;
                }
            } catch (Throwable ignored) {}
            comp.tapeSmokePurchased = true;
            comp.sync();
            LegionPlayerComponent.LegionSendActionBar(sp, "§a购买成功！§f已装备「冒烟的混音带」。技能期间每次击中，16 格范围内所有玩家获得 1 秒失明");
            return true;
        }
    }

    private static class LegionRoleImpl extends NormalRole {

        public LegionRoleImpl() {
            super(LEGION_ID,
                    0xFF7C0A02,
                    false,                        // isInnocent=false（杀手不无辜）
                    true,                         // canUseKiller=true（杀手阵营）
                    MoodType.FAKE,
                    -1,
                    true);
            this.setCanSeeTime(true)
                    .setCanUseInstinct(true)
                    .setDefaultMax(1)
                    .setCanBeRandomedByOtherRoles(true)
                    .setNeutrals(false);          // 杀手阵营，非中立
        }

        @Override
        public List<ItemStack> getDefaultItems() { return new ArrayList<>(); }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            Item knifeItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("trainmurdermystery:knife"));
            if (knifeItem != null && knifeItem != Items.AIR) {
                entries.add(new ShopEntry(new ItemStack(knifeItem), 130, ShopEntry.Type.TOOL));
            }
            Item lockpickItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("trainmurdermystery:lockpick"));
            if (lockpickItem != null && lockpickItem != Items.AIR) {
                entries.add(new ShopEntry(new ItemStack(lockpickItem), 80, ShopEntry.Type.TOOL));
            }
            // 军团专属：200 金币立即刷新技能冷却
            entries.add(LegionSkillRefreshShopEntry.create());
            // 军团 5 张 100 金币混音带（11号唱片图标）
            entries.add(TapeFrankShopEntry.create());
            entries.add(TapeJollyShopEntry.create());
            entries.add(TapeSusieShopEntry.create());
            entries.add(TapeJoeyShopEntry.create());
            entries.add(TapeSmokeShopEntry.create());
            // 停电 120 金币（SRE 原生关灯：购买后立即触发全图关灯，不给物品）
            entries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 120, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            return entries;
        }

        /**
         * 军团商店专属：消耗 200 金币立刻把野性狂暴冷却清零
         */
        public static class LegionSkillRefreshShopEntry extends ShopEntry {
            private static final int PRICE_COINS = 200;
            public LegionSkillRefreshShopEntry(ItemStack stack, int price, Type type) {
                super(stack, price, type);
            }
            public static LegionSkillRefreshShopEntry create() {
                ItemStack icon = new ItemStack(Items.PAPER);
                try {
                    icon.set(DataComponents.ITEM_NAME,
                            Component.literal("§c§l军团 §8| §6野性狂暴 §e冷却刷新 §8[§e" + PRICE_COINS + " 金币§8]"));
                } catch (Throwable ignored) {}
                return new LegionSkillRefreshShopEntry(icon, PRICE_COINS, Type.TOOL);
            }
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (!(player instanceof ServerPlayer sp)) return false;
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                if (comp == null) return false;
                if (comp.skillActive) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§c刷新失败！§f野性狂暴已经开启，请先结束技能后再刷新");
                    return false;
                }
                if (comp.cooldownTicks <= 0) {
                    LegionPlayerComponent.LegionSendActionBar(sp, "§a刷新失败！§f野性狂暴已经就绪，不需要花费金币刷新");
                    return false;
                }
                try {
                    io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                            io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp);
                    if (shop != null && shop.balance < PRICE_COINS) {
                        LegionPlayerComponent.LegionSendActionBar(sp, "§c金币不足！§f需要 §6" + PRICE_COINS + " §f金币才能刷新冷却，你当前只有 §6" + shop.balance + " §f金币");
                        return false;
                    }
                } catch (Throwable ignored) {}
                comp.cooldownTicks = 0;
                comp.sync();
                LegionPlayerComponent.LegionSendActionBar(sp, "§a刷新成功！§f已消耗 §6" + PRICE_COINS + " §f金币，野性狂暴 §a✔ 已就绪，按技能键立即开启");
                return true;
            }
        }

        @Override
        public boolean onAbilityUse(@NotNull ServerPlayer player) {
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null) return false;
            if (!gameWorld.getRole(player).equals(LEGION)) return false;
            LegionPlayerComponent comp = ModComponents.LEGION.get(player);
            if (comp == null) return false;
            comp.toggleSkill(player);
            return true;
        }

        /**
         * 军团技能期禁止「捡起任何攻击型武器 + 开锁器」
         */
        @Override
        public java.util.function.@NotNull Predicate<Item> cantPickupItem(@NotNull net.minecraft.world.entity.player.Player player) {
            return item -> {
                if (item == null) return false;
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                if (comp == null || !comp.skillActive) return false;
                return LegionPlayerComponent.isSkillTempStoreItem(item);
            };
        }
    }
}
