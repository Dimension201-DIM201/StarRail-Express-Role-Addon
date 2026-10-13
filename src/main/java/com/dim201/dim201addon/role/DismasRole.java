package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.DismasPlayerComponent;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 强盗 - 警长职业（纯服务端逻辑）
 *
 * <p>背景：曾劫掠旅者马车却只带回永远伴随的阴影，乘坐列车寻求救赎。
 *
 * <p>被动1「信念考验」：任务恢复理智减半；每具尸体首次见到降理智；理智低于 50% 判定
 * 2/3 折磨 / 1/3 美德（见 {@link DismasPlayerComponent}）。
 * <p>被动2「不是今天！」：20% 死门抗性，概率免疫致命伤害，触发后消耗。
 * <p>主动技能「战斗技巧」（G 键，30 秒冷却）：手持刀向前冲刺；
 * 技能不在冷却时，霰弹枪开火后自动向后滑步。
 * <p>商店：刀 / 短霰弹枪（首购后 5 折）、回形针（每局限购 1 次）。
 */
public class DismasRole {

    public static final ResourceLocation DISMAS_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "dismas"
    );

    public static SRERole DISMAS;

    /** 强盗专属商品价格（不跟随 SRE 全局配置） */
    public static final int KNIFE_PRICE = 50;
    public static final int SHOTGUN_PRICE = 150;
    /** 霰弹枪首购后折扣（%，镜像 SRE 杀手刀的 50%） */
    public static final int SHOTGUN_DISCOUNT = 50;

    public static void register() {
        DISMAS = TMMRoles.registerRole(new DismasRoleImpl());
        org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(DISMAS_ID, 1);

        // 被动2「不是今天！」：死亡前判定死门抗性（覆盖有无击杀者两条死亡链路）
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> tryNotToday(player));
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> tryNotToday(player));

        // 真正死亡时掉落一把左轮手枪（供队友拾取武装自己）；
        // 「不是今天」取消死亡不会触发本事件，因此抗性保命不掉枪。
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning() || !gw.isRole(sp, DISMAS)) return;
            sp.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
        });

        // 主动技能第二触发点：霰弹枪开火后自动向后滑步
        // （霰弹枪是独立蓄力武器，不走 GunShootPayload，OnRevolverUsed 对它不触发，
        //   由 ShortShotgunItemMixin 在开火音效后回调 onShotgunFired）

        // 对局重置：清空全部运行时状态（CCA clear() 之外的双保险）
        org.agmas.harpymodloader.events.ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer sp) {
                ModComponents.DISMAS.get(sp).init();
            }
        });
    }

    /**
     * 「不是今天！」死亡拦截。
     * 判定成功 → 免疫本次死亡并恢复少量血量；优先消耗基础 20%（坚定层保留）。
     * 判定失败不消耗抗性。
     *
     * @return false 取消死亡
     */
    private static boolean tryNotToday(Player player) {
        if (!(player instanceof ServerPlayer sp)) return true;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(sp, DISMAS)) return true;

        DismasPlayerComponent comp = ModComponents.DISMAS.get(sp);
        double resistance = comp.getDeathResistance();
        if (resistance <= 0) return true;
        if (sp.getRandom().nextDouble() >= resistance) return true; // 判定失败，不消耗

        if (comp.hasBaseResistance) {
            comp.hasBaseResistance = false; // 触发后减少 20% 死门抗性
        }
        sp.setHealth(DismasPlayerComponent.NOT_TODAY_HEALTH);
        sp.fallDistance = 0;
        sp.displayClientMessage(Component.literal("§6§l「不是今天！」"), false);
        return false;
    }

    /**
     * 霰弹枪开火回调（由 {@code ShortShotgunItemMixin} 在开火音效后触发）。
     * 技能不在冷却时自动向后滑步，消耗同一次冷却。
     */
    public static void onShotgunFired(ServerPlayer player) {
        if (!player.getMainHandItem().is(ModItems.SHORT_SHOTGUN)) return;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning() || !gw.isRole(player, DISMAS)) return;

        DismasPlayerComponent comp = ModComponents.DISMAS.get(player);
        if (comp.skillCooldown > 0) return; // 仅在技能不在冷却时触发
        comp.tryDash(player, false);
    }

    private static class DismasRoleImpl extends NormalRole {

        public DismasRoleImpl() {
            // 警长阵营：好人 + 真实理智系统（MoodType.REAL，被动1 的前提）+ 平民疾跑上限
            // 颜色：淡灰色（与 noellesroles 保安 Color(170,170,170) 同款）
            super(DISMAS_ID, 0xAAAAAA, true, false, SRERole.MoodType.REAL, 400, false);
            this.setVigilanteTeam(true);     // 警长阵营
        }

        /**
         * 无法捡起左轮手枪（镜像鸣上悠）：迪斯马斯的武器是刀与霰弹枪，
         * 死亡掉落的左轮是留给队友的警长枪，他本人不得使用/拾取。
         * wathe 层谓词在 SRE 拾取门之后仍强制执行，精确封死左轮（不影响霰弹枪等）。
         */
        @Override
        public java.util.function.Predicate<Item> cantPickupItem(Player player) {
            return item -> item == TMMItems.REVOLVER;
        }

        /**
         * 强盗专属商店：
         * - 刀：复用 SRE 原版 KillerKnifeShopEntry（自带耐久模式 + 首购后 -50%）
         * - 短霰弹枪：首购后 -50%（DynamicShopComponent 价格修正）
         * - 回形针：每局限购 1 次（一次性撬锁器）
         */
        @Override
        public @NotNull List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            entries.add(new KillerKnifeShopEntry(KNIFE_PRICE));

            entries.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(),
                    SHOTGUN_PRICE, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    boolean success = super.onBuy(player);
                    if (success) {
                        applyFirstPurchaseDiscount(player, ModItems.SHORT_SHOTGUN);
                    }
                    return success;
                }
            });

            entries.add(new ShopEntry(ModItems.NOELL_PAPERCLIP.getDefaultInstance(),
                    SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    if (player instanceof ServerPlayer sp
                            && ModComponents.DISMAS.get(sp).lockpickBought) {
                        return false;
                    }
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    boolean success = super.onBuy(player);
                    if (success && player instanceof ServerPlayer sp) {
                        ModComponents.DISMAS.get(sp).lockpickBought = true;
                    }
                    return success;
                }
            });
            return entries;
        }

        /** 首购后给后续购买挂 -50% 折扣（镜像 SRE KillerKnifeShopEntry 的实现） */
        private static void applyFirstPurchaseDiscount(Player player, Item item) {
            DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (dynamicShop.getPurchaseCount(itemId) == 0) {
                dynamicShop.setPercentDiscount(itemId, SHOTGUN_DISCOUNT);
            }
            dynamicShop.recordPurchase(itemId);
        }

        /**
         * 信念考验：任务完成的理智恢复减半。
         * SRE 在调用本方法前已 addMood(MOOD_GAIN)，这里补扣削减部分。
         */
        @Override
        public void onFinishQuest(Player player, String quest) {
            super.onFinishQuest(player, quest);
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
            mood.addMood(-GameConstants.MOOD_GAIN * DismasPlayerComponent.TASK_MOOD_REDUCTION);
        }

        /** 主动技能「战斗技巧」：手持刀时按技能键向前冲刺 */
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (!player.getMainHandItem().is(TMMItems.KNIFE)) {
                player.displayClientMessage(Component.literal("§c手持刀才能使用战斗技巧"), true);
                return false;
            }
            DismasPlayerComponent comp = ModComponents.DISMAS.get(player);
            return comp.tryDash(player, true);
        }
    }
}
