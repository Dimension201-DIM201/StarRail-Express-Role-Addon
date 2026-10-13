package com.dim201.dim201addon;

import com.dim201.dim201addon.item.card.CardUtils;
import com.dim201.dim201addon.menu.ShionBeggarSession;
import com.dim201.dim201addon.role.ShionRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 依神紫菀（Shion Yorigami）玩家组件
 * 中立阵营
 *
 * 被动：不幸的源头
 *  · 对局开始 90s 后，每隔不定时间（20~40s）让周围 12 格内的玩家损失随机 10~40 金币
 *
 * 主动技能：乞讨
 *  · 按技能键 → 对面前 3 格玩家发起乞讨（目标弹出「施舍/拒绝」UI）
 *  · 施舍：给紫菀 10~25 随机金币，施舍者有 20% 概率获得 1 层护盾
 *  · 拒绝：无事发生
 *
 * 获胜：商店购买「去吃饭」→ 50% 成功（跟随其他阵营胜利）、50% 失败（啥也不发生）
 */
public class ShionPlayerComponent implements RoleComponent {

    public static final ComponentKey<ShionPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "shion"),
            ShionPlayerComponent.class);

    // ===== 常量 =====
    /** 被动生效延迟：对局开始 90s 后（1800 tick） */
    public static final int PASSIVE_DELAY_TICKS = 90 * 20;
    /** 被动作用半径（格） */
    public static final double PASSIVE_RADIUS = 12.0;
    /** 被动单次扣金币范围：10~40 */
    public static final int PASSIVE_MIN_LOSS = 10;
    public static final int PASSIVE_MAX_LOSS = 40;
    /** 乞讨技能冷却：25s */
    public static final int BEG_COOLDOWN_TICKS = 25 * 20;
    /** 施舍者获得护盾的概率：10% */
    public static final double SHIELD_CHANCE = 0.1;
    /** 施舍给的最少/最多金币 */
    public static final int GIVE_MIN_GOLD = 10;
    public static final int GIVE_MAX_GOLD = 25;

    private final Player player;

    // ===== 组件状态 =====
    /** 乞讨技能冷却（tick） */
    public int begCooldownTicks = 0;
    /** 被动下一次触发的剩余 tick（初始=PASSIVE_DELAY_TICKS） */
    public int nextPassiveTicks = PASSIVE_DELAY_TICKS;
    /** 已获得护盾层数（仅记录提示用，真正护盾数值在 SREArmorPlayerComponent） */
    public int shieldStacks = 0;
    /** 是否已购买到「去吃饭」，完成了胜利目标（不再获得任务奖励） */
    public boolean hasWon = false;

    public ShionPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return this.player == sp;
    }

    @Override
    public void init() {
        begCooldownTicks = 0;
        nextPassiveTicks = PASSIVE_DELAY_TICKS;
        shieldStacks = 0;
        hasWon = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // ================= 被动：不幸的源头（每 tick 推进） =================

    public void tick(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null) return;
        if (!gw.isRole(sp, ShionRole.SHION)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;

        // —— 乞讨冷却倒计时 ——
        if (begCooldownTicks > 0) {
            begCooldownTicks--;
        }

        // —— 被动触发倒计时 ——
        if (nextPassiveTicks > 0) {
            nextPassiveTicks--;
            if (nextPassiveTicks == 0) {
                triggerPassive(sp);
                // 下一次触发：在 20~40s 之间的随机时刻
                int next = (ThreadLocalRandom.current().nextInt(20, 41)) * 20;
                nextPassiveTicks = next;
            }
        }
    }

    /** 被动触发：周围 12 格内玩家随机扣 10~40 金币 */
    private void triggerPassive(ServerPlayer shion) {
        List<ServerPlayer> players = shion.serverLevel().players();
        int victimCount = 0;
        for (ServerPlayer p : players) {
            if (p.getUUID().equals(shion.getUUID())) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (p.distanceTo(shion) > PASSIVE_RADIUS) continue;

            int loss = ThreadLocalRandom.current().nextInt(PASSIVE_MIN_LOSS, PASSIVE_MAX_LOSS + 1);
            int currentGold = CardUtils.getGold(p);
            int actual = Math.min(loss, currentGold);
            if (actual > 0) {
                CardUtils.addGold(p, -actual);
                victimCount++;
            }
        }

        if (victimCount > 0) {
            sendActionBar(shion, "§5§l不幸扩散 §f影响到了 §c" + victimCount + " §f名玩家的钱包！");
            shion.level().playSound(null, shion.blockPosition(),
                    SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.4f, 0.8f);
        }
    }

    // ================= 主动：乞讨 =================

    /**
     * 使用技能键：寻找面前 3 格内玩家并打开乞讨 UI
     */
    public boolean useBegAbility(ServerPlayer shion) {
        if (begCooldownTicks > 0) {
            int secs = (begCooldownTicks + 19) / 20;
            sendActionBar(shion, "§e乞讨 §f冷却中 §c" + secs + "s");
            return false;
        }

        // 找面前 3 格内的目标玩家（使用视线射线）
        ServerPlayer target = findTargetInFront(shion);
        if (target == null) {
            sendActionBar(shion, "§c附近没有可乞讨的玩家");
            return false;
        }

        // 打开对方的乞讨菜单（Menu 系统）
        ShionBeggarSession.openBeggarMenu(shion, target);

        begCooldownTicks = BEG_COOLDOWN_TICKS;
        sendActionBar(shion, "§f你向 §e" + target.getName().getString() + " §f发起了乞讨...");
        sync();
        return true;
    }

    /** 找面前 3 格内的目标玩家（用 pick + AABB 兜底） */
    private ServerPlayer findTargetInFront(ServerPlayer shion) {
        // 优先：视线 pick（MC 原生 crosshair target 只对非玩家有效）
        // 这里用 AABB：视线方向 3 格长方体
        var box = shion.getBoundingBox().inflate(3.0);
        ServerPlayer best = null;
        double bestDist = 999;
        for (ServerPlayer p : shion.serverLevel().players()) {
            if (p.getUUID().equals(shion.getUUID())) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            if (!box.contains(p.position())) continue;
            // 必须在面前 ±60°（点积 > 0.5）
            var dirTo = p.position().subtract(shion.position()).normalize();
            var look = shion.getLookAngle();
            double dot = dirTo.x * look.x + dirTo.z * look.z;
            if (dot < 0.5) continue;
            double d = p.distanceTo(shion);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // ================= 乞讨「施舍」回调（由 BeggarMenu 调用） =================

    /**
     * 当目标点击「施舍」后调用。
     *
     * @param beggar  紫菀（乞讨者）
     * @param giver   施舍者
     */
    public static void onDonate(ServerPlayer beggar, ServerPlayer giver) {
        int amount = ThreadLocalRandom.current().nextInt(GIVE_MIN_GOLD, GIVE_MAX_GOLD + 1);
        int actual = Math.min(amount, CardUtils.getGold(giver));

        if (actual > 0) {
            // 从施舍者扣钱，给紫菀加钱（正常获得金币）
            CardUtils.addGold(giver, -actual);
            CardUtils.addGold(beggar, actual);

            beggar.displayClientMessage(Component.literal("§a" + giver.getName().getString() + " §f施舍了 §e" + actual + " §f金币给你！"), false);
            giver.displayClientMessage(Component.literal("§f你向 §5紫菀 §f施舍了 §e" + actual + " §f金币，愿好运常伴~"), false);
        } else {
            beggar.displayClientMessage(Component.literal("§f" + giver.getName().getString() + " §c钱包空空如也..."), false);
            giver.displayClientMessage(Component.literal("§c你没有足够的金币可以施舍"), false);
        }

        // 10% 概率给施舍者 1 层护盾（可叠加）
        if (ThreadLocalRandom.current().nextDouble() < SHIELD_CHANCE) {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(giver);
            armor.addArmor();
            armor.sync();
            // 屏幕下方大文字通知
            giver.displayClientMessage(Component.literal("§a行善积德！获得 §b1 §a层护盾！"), false);
            // actionbar 热栏上方提示（用户要求）
            giver.displayClientMessage(Component.literal("§a§l行善积德！护盾 +1（当前 " + armor.getArmor() + " 层）"), true);
            giver.playNotifySound(SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    /** 当目标点击「拒绝」后调用 */
    public static void onReject(ServerPlayer beggar, ServerPlayer giver) {
        beggar.displayClientMessage(Component.literal("§c" + giver.getName().getString() + " §7拒绝了你的乞讨..."), false);
    }

    // ================= 辅助 =================

    private void sendActionBar(ServerPlayer p, String msg) {
        if (p == null) return;
        p.displayClientMessage(Component.literal(msg), true);
    }

    // ================= NBT =================

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        begCooldownTicks = tag.getInt("BegCooldown");
        nextPassiveTicks = tag.getInt("NextPassive");
        shieldStacks = tag.getInt("ShieldStacks");
        hasWon = tag.getBoolean("HasWon");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("BegCooldown", begCooldownTicks);
        tag.putInt("NextPassive", nextPassiveTicks);
        tag.putInt("ShieldStacks", shieldStacks);
        tag.putBoolean("HasWon", hasWon);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }
}
