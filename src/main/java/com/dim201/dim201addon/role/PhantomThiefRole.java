package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.PhantomThiefPlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 大怪盗 - 中立阵营（纯服务端逻辑，原型：怪盗基德/ Persona 5 怪盗团）
 *
 * <p>胜利条件：成功完成「玩家总数/2」次预告盗窃（购买谢幕触发独立胜利）。
 *
 * <p>核心规则：
 * <ul>
 *   <li>无法使用任何武器杀死其他玩家（伤害事件拦截）</li>
 *   <li>死亡时背包中所有偷来的物品（STOLEN_MARK）戏剧性散落一地</li>
 * </ul>
 *
 * <p>主动1「预告信」：背包界面点击玩家头像（客户端 Mixin 注入）选择目标，
 * 系统从其背包抽取一件非基础任务物品标记为目标宝物，90 秒预告期。
 * 期间大怪盗获得目标的高亮追踪（客户端透视，仅目标可见轮廓）+ 杀手直觉/夜视。
 *
 * <p>主动2「妙手空空」（G 键，无冷却）：接近目标 ≤ 3 格开始读条（收藏层数缩短），
 * 目标对视大怪盗即打断；成功则转移宝物 + 200 金币 + 胜利计数。
 *
 * <p>被动「华丽脱身」：成功 = 40% 移速 3s + 隐身 5s；被打断 = 仅移速。
 * <p>被动「收藏家之傲」：每完成一次盗窃 +1 层（移速 +5%、读条 -0.5s），最多 3 层。
 *
 * <p>商店：烟雾弹 50 / 开锁器 100 / 钩影手 150（耐久道具，可复购）/ 迷魂铃（限购 1）/
 * 替身人偶 400（免疫一次死亡）/ 谢幕（胜利条件达成后可领，独立胜利）。
 */
public class PhantomThiefRole {

    public static final ResourceLocation PHANTOM_THIEF_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "phantom_thief"
    );

    public static SRERole PHANTOM_THIEF;

    /** 角色代表色：怪盗紫 */
    public static final int ROLE_COLOR = 0x9B59B6;

    /** 商店价格 */
    public static final int SMOKE_PRICE = 50;
    public static final int GRAPPLE_PRICE = 150;
    public static final int LOCKET_PRICE = 200;
    public static final int DOLL_PRICE = 400;
    public static final int LOCKPICK_PRICE = 100;
    /** 替身人偶触发后恢复的血量 */
    public static final float DOLL_HEALTH = 6.0f;

    public static void register() {
        PHANTOM_THIEF = TMMRoles.registerRole(new PhantomThiefRoleImpl());

        // ---------- 大怪盗无法使用任何武器杀死其他玩家 ----------
        // SRE 的刀/枪/棍/雷击杀都走 GameUtils.killPlayer 自定义死亡流程（不经过原版伤害事件），
        // 因此必须在 SRE 死亡判定事件拦截：击杀者是大怪盗 → 取消死亡（目标可被打伤但不会死）。
        // 参考林家（LinFamily）中立角色同款写法。SRE 原生刀击杀另由 canUseKiller=false 在角色层禁止。
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (killer == null) return true;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());
            if (gw == null || !gw.isRunning()) return true;
            if (gw.isRole(killer, PHANTOM_THIEF)) {
                killer.displayClientMessage(Component.literal(
                        "§5§l[大怪盗] §7你的手只适合取物，不适合染血——目标活了下来"), true);
                return false;
            }
            return true;
        });

        // ---------- 死亡：偷来的物品戏剧性散落 + 预告失效 ----------
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning() || !gw.isRole(sp, PHANTOM_THIEF)) return;
            PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(sp);
            comp.scatterStolenItems(sp);
            comp.resetAnnouncement();
            comp.sync();
        });

        // ---------- 替身人偶：免疫一次死亡（覆盖有无击杀者两条死亡链路） ----------
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> tryDollSave(player));
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> tryDollSave(player));

        // ---------- 对局重置 ----------
        org.agmas.harpymodloader.events.ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer sp) {
                ModComponents.PHANTOM_THIEF.get(sp).init();
            }
        });
    }

    /** 替身人偶死亡拦截：消耗人偶、取消死亡 */
    private static boolean tryDollSave(Player player) {
        if (!(player instanceof ServerPlayer sp)) return true;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(sp, PHANTOM_THIEF)) return true;
        return !ModComponents.PHANTOM_THIEF.get(sp).tryDollSave(sp);
    }

    /** 谢幕：大怪盗的独立胜利（镜像张老师 triggerWin） */
    public static void triggerWin(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("§5§l你赢了！")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§f没有什么是大怪盗偷不到的♪")));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);

        RoleUtils.customWinnerWin(player.serverLevel(), "dim201addon.phantom_thief", ROLE_COLOR);
    }

    // ==================== 角色定义 ====================

    private static class PhantomThiefRoleImpl extends NormalRole {

        public PhantomThiefRoleImpl() {
            super(PHANTOM_THIEF_ID, ROLE_COLOR,
                    false,      // isInnocent = false（中立）
                    false,      // canUseKiller = false（禁止 SRE 原生刀击杀）
                    SRERole.MoodType.FAKE,
                    -1,         // maxSprintTime 默认
                    false);
            this.setNeutrals(true)                     // 中立阵营
                    .setCanUseInstinct(true)           // 杀手直觉（客户端透视事件会把高亮限定为追踪目标）
                    .setInstinctNightVision(true)      // 夜视
                    .setDefaultMax(1)                  // 每局最多 1 名
                    .setCanBeRandomedByOtherRoles(true);
        }

        /**
         * 大怪盗专属商店：
         * - 烟雾弹：SRE 已有烟雾弹（SMOKE_PELLET）
         * - 钩影手：每局限 3 次使用（次数存组件，商店不限购次数但重复购买补充次数无意义 → 限购 1）
         * - 迷魂铃：消耗品，每局限购 1 次
         * - 替身人偶：购买立即生效（不入背包），免疫一次死亡，限购 1 次
         * - 谢幕：胜利条件达成后可领取，立即独立胜利
         */
        @Override
        public @NotNull List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();

            // 烟雾弹（复用捣蛋鬼专属烟雾弹：投掷形成烟雾区域，进入者失明）
            entries.add(new ShopEntry(ModItems.SMOKE_GRENADE.getDefaultInstance(),
                    SMOKE_PRICE, ShopEntry.Type.TOOL));

            // 开锁器（SRE 原生开锁器，撬锁开门）
            entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                    LOCKPICK_PRICE, ShopEntry.Type.TOOL));

            // 钩影手：普通耐久道具（3 点耐久，损坏报废），一局可反复购买
            entries.add(new ShopEntry(com.dim201.dim201addon.item.Dim201ModItems.PHANTOM_GRAPPLING_CLAW.getDefaultInstance(),
                    GRAPPLE_PRICE, ShopEntry.Type.TOOL));

            // 迷魂铃：每局限购 1 次
            entries.add(new ShopEntry(com.dim201.dim201addon.item.Dim201ModItems.PHANTOM_HYPNOTIC_LOCKET.getDefaultInstance(),
                    LOCKET_PRICE, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    if (player instanceof ServerPlayer sp
                            && ModComponents.PHANTOM_THIEF.get(sp).locketBought) {
                        return false;
                    }
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    boolean success = super.onBuy(player);
                    if (success && player instanceof ServerPlayer sp) {
                        ModComponents.PHANTOM_THIEF.get(sp).locketBought = true;
                    }
                    return success;
                }
            });

            // 替身人偶：虚拟物品（不死图腾仅作图标，购买即生效不入背包），限购 1 次
            ItemStack dollIcon = new ItemStack(Items.TOTEM_OF_UNDYING);
            dollIcon.set(DataComponents.ITEM_NAME,
                    Component.literal("§d§l替身人偶 §8[§6" + DOLL_PRICE + " 金币§8]"));
            dollIcon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("§7购买后立即生效，不占用背包"),
                    Component.literal("§7将替你免疫一次致命死亡"))));
            entries.add(new ShopEntry(dollIcon, DOLL_PRICE, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    if (player instanceof ServerPlayer sp
                            && ModComponents.PHANTOM_THIEF.get(sp).dollActive) {
                        return false;
                    }
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!(player instanceof ServerPlayer sp)) return false;
                    PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(sp);
                    if (comp.dollActive) return false;
                    // 虚拟物品：不调用 super（不发放不死图腾），仅置标志位；扣钱由商店框架处理
                    comp.dollActive = true;
                    comp.sync();
                    sp.displayClientMessage(Component.literal(
                            "§d§l[替身人偶] §f人偶已藏入你的阴影，将替你承受一次死亡"), true);
                    return true;
                }
            });

            // 谢幕：虚拟物品（末影珍珠仅作图标），胜利条件达成后可领，立即独立胜利
            ItemStack curtainIcon = new ItemStack(Items.ENDER_PEARL);
            curtainIcon.set(DataComponents.ITEM_NAME, Component.literal("§d§l谢幕"));
            curtainIcon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("§7完成目标预告盗窃次数后可购买"),
                    Component.literal("§7购买后立刻触发大怪盗独立胜利"))));
            entries.add(new ShopEntry(curtainIcon, 0, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    if (player instanceof ServerPlayer sp
                            && !ModComponents.PHANTOM_THIEF.get(sp).hasReachedWin()) {
                        return false;
                    }
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (player instanceof ServerPlayer sp
                            && ModComponents.PHANTOM_THIEF.get(sp).hasReachedWin()) {
                        triggerWin(sp);
                        return true;
                    }
                    return false;
                }
            });

            return entries;
        }

        // 「妙手空空」不走 onAbilityUse：与吞噬者/蕾忍一致，用客户端 GKeyRoleSkill
        // 拦截 G 键后发送 PhantomThiefStealC2SPacket，由服务端处理读条（避免被准星目标拦截）。
    }
}
