package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.WeaponMasterPlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器大师（Weapon Master）：杀手阵营。
 *
 * <p>核心机制：通过虚拟零件制作武器。
 * <ul>
 *   <li>每 30 秒 + 完成任务时随机获得木质/金属/火药（不与上次重复）</li>
 *   <li>撬棍破门获得精密构件</li>
 *   <li>商店物品用零件兑换（价格显示在物品名中），左轮手枪额外需要 130 金币</li>
 *   <li>初始物品：撬棍</li>
 * </ul>
 *
 * <p>商店配方：
 * <pre>
 *   苦无       : 1 金属
 *   开锁器     : 1 木质 + 1 金属
 *   刀         : 1 木质 + 1 金属
 *   手榴弹     : 1 金属 + 2 火药 + 1 精密
 *   手铐       : 1 金属
 *   左轮手枪   : 130 金币 + 1 木质 + 1 火药 + 1 精密
 *   假左轮手枪 : 1 木质
 * </pre>
 */
public class WeaponMasterRole {

    public static final ResourceLocation WEAPON_MASTER_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "weapon_master");

    public static SRERole WEAPON_MASTER;

    /** 角色代表色：古铜色（深棕金属感，契合锻造主题） */
    public static final int ROLE_COLOR = 0x8B4513;

    public static void register() {
        WEAPON_MASTER = TMMRoles.registerRole(new WeaponMasterRoleImpl());
    }

    /**
     * 撬棍破门时给予精密构件（由 Dim201Addon 的 UseBlockCallback 监听调用）
     */
    public static void onCrowbarDoorBroken(ServerPlayer sp) {
        var gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRole(sp, WEAPON_MASTER)) return;
        ModComponents.WEAPON_MASTER.get(sp).onDoorBroken();
    }

    // ===== 零件消耗型商店条目 =====

    /**
     * 消耗零件购买武器的商店条目：
     * canBuy 检查零件是否足够，onBuy 放入物品成功后才消耗零件。
     * 价格为 0 时纯零件购买；价格 > 0 时金币 + 零件混合购买
     * （金币检查/扣除由购买流程的 hasEnoughCurrency/spendCurrency 处理）。
     */
    private static class PartShopEntry extends ShopEntry {
        private final int needWood, needMetal, needGP, needPrec;
        private final String partDesc;

        /** 纯零件购买（0 金币） */
        PartShopEntry(ItemStack stack, String partDesc,
                      int wood, int metal, int gp, int prec) {
            this(stack, partDesc, wood, metal, gp, prec, 0);
        }

        /** 金币 + 零件混合购买 */
        PartShopEntry(ItemStack stack, String partDesc,
                      int wood, int metal, int gp, int prec, int price) {
            super(stack, price, Type.TOOL);
            this.needWood = wood;
            this.needMetal = metal;
            this.needGP = gp;
            this.needPrec = prec;
            this.partDesc = partDesc;
            // 通过 this.stack() 获取父类实际持有的栈（可能为副本），
            // 对其设置 CUSTOM_NAME，确保商店界面显示的物品名被正确修改。
            // 保留原版可翻译组件（客户端显示中文名），追加材料消耗信息。
            ItemStack inner = this.stack();
            inner.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    inner.getHoverName().copy()
                            .append(Component.literal(" §8(" + partDesc + ")")));
        }

        @Override
        public boolean canBuy(@NotNull Player player) {
            if (this.stack().isEmpty()) return false;
            WeaponMasterPlayerComponent comp = ModComponents.WEAPON_MASTER.get(player);
            if (!comp.hasEnough(needWood, needMetal, needGP, needPrec)) {
                player.displayClientMessage(
                        Component.literal("§c零件不足！需要: " + partDesc), true);
                return false;
            }
            return true;
        }

        @Override
        public boolean onBuy(@NotNull Player player) {
            WeaponMasterPlayerComponent comp = ModComponents.WEAPON_MASTER.get(player);
            if (!comp.hasEnough(needWood, needMetal, needGP, needPrec)) return false;
            // 先放物品（super.onBuy = 插入空槽），成功后才扣零件；
            // 返回 false 时外层 spendCurrency 也不会执行，金币与零件都不扣，状态一致
            if (!super.onBuy(player)) return false;
            comp.consume(needWood, needMetal, needGP, needPrec);
            return true;
        }
    }

    private static class WeaponMasterRoleImpl extends NormalRole {

        public WeaponMasterRoleImpl() {
            super(WEAPON_MASTER_ID,
                    ROLE_COLOR,
                    false,             // isInnocent = false（杀手）
                    true,              // canUseKiller = true
                    MoodType.FAKE,     // 假心情
                    -1,                // 默认冲刺时间
                    true);             // 能看到时间
            this.setCanSeeTime(true)
                    .setCanUseInstinct(true)
                    .setCanBeRandomedByOtherRoles(true)
                    .setServerGameTickEvent(this::handleServerTick);
        }

        /**
         * 仅武器大师角色才推进零件计时器。
         * WeaponMasterPlayerComponent 不实现 ServerTickingComponent（避免 CCA 对所有玩家每 tick 调用，
         * 导致非武器大师角色也获取零件的 bug），改由角色回调在 isRole 判定通过后调用 comp.tick(sp)。
         * 参考 SakiRole.handleServerTick。
         */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, WEAPON_MASTER)) return;
            if (!Dim201Addon.isInGameAlive(player)) return;
            ModComponents.WEAPON_MASTER.get(player).tick(player);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            // 初始物品：撬棍
            items.add(new ItemStack(TMMItems.CROWBAR));
            return items;
        }

        @Override
        public void onFinishQuest(Player player, String quest) {
            super.onFinishQuest(player, quest);
            // 完成任务时随机获得一个非精密零件
            ModComponents.WEAPON_MASTER.get(player).onTaskComplete();
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 苦无：1 金属
            entries.add(new PartShopEntry(
                    ModItems.NINJA_KNIFE.getDefaultInstance(),
                    "§61 金属", 0, 1, 0, 0));

            // 开锁器：1 木质 + 1 金属
            entries.add(new PartShopEntry(
                    TMMItems.LOCKPICK.getDefaultInstance(),
                    "§61 木质 + 1 金属", 1, 1, 0, 0));

            // 刀：1 木质 + 1 金属
            entries.add(new PartShopEntry(
                    TMMItems.KNIFE.getDefaultInstance(),
                    "§61 木质 + 1 金属", 1, 1, 0, 0));

            // 手榴弹：1 金属 + 2 火药 + 1 精密
            entries.add(new PartShopEntry(
                    TMMItems.GRENADE.getDefaultInstance(),
                    "§61 金属 + 2 火药 + 1 精密", 0, 1, 2, 1));

            // 手铐：1 金属
            entries.add(new PartShopEntry(
                    ModItems.HANDCUFFS.getDefaultInstance(),
                    "§61 金属", 0, 1, 0, 0));

            // 左轮手枪：130 金币 + 1 木质 + 1 火药 + 1 精密（金币+零件混合购买）
            entries.add(new PartShopEntry(
                    TMMItems.REVOLVER.getDefaultInstance(),
                    "§61 木质 + 1 火药 + 1 精密", 1, 0, 1, 1, 130));

            // 假左轮手枪：1 木质
            entries.add(new PartShopEntry(
                    ModItems.FAKE_REVOLVER.getDefaultInstance(),
                    "§61 木质", 1, 0, 0, 0));

            // 停电 300 金币（SRE 原生关灯，购买后立即触发，不给物品）
            entries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 300, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });

            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            player.displayClientMessage(Component.literal(
                    "§b[武器大师] §7通过零件制作武器，在商店中查看配方"), true);
            return true;
        }
    }
}
