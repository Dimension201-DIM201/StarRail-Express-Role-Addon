package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.GojoPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GojoRole {

    public static final ResourceLocation GOJO_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "gojo");

    public static SRERole GOJO;

    private static boolean eventsRegistered = false;

    public static void register() {
        GOJO = TMMRoles.registerRole(new GojoRoleImpl());
        Harpymodloader.setRoleMaximum(GOJO_ID, 1);
        SREArmorPlayerComponent.canSynced.add(entry -> entry.getKey().identifier().getPath().equals("gojo"));
        registerEvents();
    }

    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        // 五条悟独立胜利不由 AllowGameEnd 兜底触发，而是在「购买新宿车票 → 对决宿傩胜利」时立即宣布。
        // 因此这里不注册任何阻止对局结束的逻辑，对局时间耗尽或其他阵营胜负均按默认正常结束。
    }

    public static class ShinjukuTicketShopEntry extends ShopEntry {
        public ShinjukuTicketShopEntry(ItemStack stack, int price, ShopEntry.Type type) {
            super(stack, price, type);
        }

        @Override
        public boolean onBuy(@NotNull Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;

            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null || !gameWorld.isRole(player, GOJO)) {
                return super.onBuy(player);
            }

            if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) return false;

            GojoPlayerComponent comp = ModComponents.GOJO.get(serverPlayer);
            if (comp != null) {
                // 情况1：对决 90s 倒计时未结束 → 禁止再次购票（防止连续拉两次对决叠 pending）
                if (comp.isDuelPending()) {
                    int secs = (comp.pendingDuelTicksLeft + 19) / 20;
                    comp.sendActionBar(serverPlayer, "§5§l「新宿决战」§f 进行中！还剩 §c" + secs + "秒 §f后才可再次购票");
                    return false;
                }
                // 情况2：5 秒内置购票冷却中（防止快速连点 / 刷票）
                if (comp.isPurchaseInternalCdActive()) {
                    int secs = (comp.purchaseInternalCdTicks + 19) / 20;
                    comp.sendActionBar(serverPlayer, "§7新宿车票 §f内置冷却中（剩余 §e" + secs + "秒 §f）");
                    return false;
                }
                // 情况3：处于"无法购买车票"冷却时（术式熔断等负面）禁止消费
                if (comp.ticketCooldownTicks > 0) {
                    int secs = (comp.ticketCooldownTicks + 19) / 20;
                    comp.sendActionBar(serverPlayer, "§c§l术式熔断！§f 新宿车票被禁止购买（剩余 §c" + secs + "秒 §f）");
                    return false;
                }
                comp.onDuelPurchased(serverPlayer);
            }
            return true;
        }

        @Override
        public boolean canBuy(@NotNull Player player) {
            if (player instanceof ServerPlayer sp) {
                if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;
                GojoPlayerComponent comp = ModComponents.GOJO.get(sp);
                if (comp == null) return true;
                // 对决未结算 OR 5秒内置CD OR 票冷却 → 商店里车票灰化（不可点击购买）
                return !comp.isDuelPending()
                        && !comp.isPurchaseInternalCdActive()
                        && comp.ticketCooldownTicks <= 0;
            }
            return true;
        }
    }

    private static class GojoRoleImpl extends NormalRole {

        public GojoRoleImpl() {
            super(GOJO_ID,
                    0xFF6FA8DC,
                    false,
                    false,
                    MoodType.FAKE,
                    -1,
                    true);
            this.setCanSeeTime(true)
                    .setCanUseInstinct(true)
                    .setInstinctNightVision(true)
                    .setDefaultMax(1)
                    .setCanBeRandomedByOtherRoles(true)
                    .setNeutrals(true)
                    .setComponentKey(ModComponents.GOJO);
            // 注意：GojoPlayerComponent 实现了 ServerTickingComponent，CCA 会自动每 tick 调一次 serverTick()
            // 因此这里不再注册 setServerGameTickEvent 的重复调用，避免倒计时 2 倍速的 bug
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            Item paper = Items.PAPER;
            ItemStack ticketStack = new ItemStack(paper);
            ticketStack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    Component.literal("§5§l新宿车票 §8| §f前往最终决战"));
            entries.add(new ShinjukuTicketShopEntry(ticketStack, 50, ShopEntry.Type.TOOL));

            Item lockpickItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("trainmurdermystery:lockpick"));
            if (lockpickItem != null && lockpickItem != Items.AIR) {
                entries.add(new ShopEntry(new ItemStack(lockpickItem), 80, ShopEntry.Type.TOOL));
            }

            return entries;
        }

        // 五条悟没有任何主动技能：不覆写 onAbilityUse，框架默认 return false → 技能按钮不会生效
    }

    public static void resetRoleState() {
    }

    public static boolean isGojo(Player player) {
        if (player.level().isClientSide) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(GOJO);
    }
}
