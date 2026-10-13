package com.dim201.dim201addon;

import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.role.PhantomThiefRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 大怪盗玩家组件：承载「预告信」「妙手空空」「华丽脱身」「收藏家之傲」等全部运行时状态。
 *
 * <p>服务端权威逻辑；追踪目标 / 胜利计数等数据仅同步给本人（{@link #shouldSyncWith}），
 * 客户端据此渲染追踪目标的高亮轮廓（PhantomThiefClientUI 注册的透视事件）。
 * 状态随对局重置：CCA clear() 生命周期 + PhantomThiefRole 注册的 ResetPlayerEvent 双保险。
 */
public class PhantomThiefPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {

    // ==================== 状态机 ====================

    /** 阶段：空闲 / 预告追踪中 / 偷窃读条中 */
    public enum Phase { NONE, ANNOUNCING, CHANNELING }

    // ==================== 数值配置 ====================

    /** 预告有效期（90 秒） */
    public static final int ANNOUNCE_DURATION_TICKS = 90 * 20;
    /** 预告信冷却：得手或到期后 15 秒（复用 SRE 原生技能冷却，与普奇/克劳恩皮斯同款） */
    public static final int ANNOUNCE_COOLDOWN_TICKS = 15 * 20;
    /** 妙手空空的发动距离（格） */
    public static final double STEAL_RANGE = 3.0;
    /** 读条基础时长（3 秒） */
    public static final int CHANNEL_BASE_TICKS = 3 * 20;
    /** 每层收藏缩短的读条时间（0.5 秒） */
    public static final int CHANNEL_REDUCTION_PER_COLLECTION = 10;
    /** 读条下限（1.5 秒，3 层收藏时） */
    public static final int MIN_CHANNEL_TICKS = 30;
    /** 偷窃成功奖励金币 */
    public static final int STEAL_REWARD_GOLD = 200;
    /** 收藏最大层数 */
    public static final int MAX_COLLECTIONS = 3;
    /** 每层收藏的移速加成 */
    public static final double SPEED_PER_COLLECTION = 0.05;
    /** 读条被打断判定：目标视线与大怪盗方向的点积阈值（≈ 25° 锥角） */
    public static final double LOOK_AT_DOT_THRESHOLD = 0.9;
    /** 读条对视打断的有效视线距离（目标在此范围内回头看向大怪盗才会打断读条） */
    public static final double CHANNEL_MAX_RANGE = 4.0;
    /** 华丽脱身：加速时长（3 秒，速度 II = +40%） */
    public static final int ESCAPE_SPEED_TICKS = 3 * 20;
    /** 华丽脱身：隐身时长（5 秒，仅盗窃成功） */
    public static final int ESCAPE_INVIS_TICKS = 5 * 20;
    /** 收藏家之傲的属性修饰符 ID */
    public static final ResourceLocation COLLECTOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "collector_pride");
    /** 「戏剧性散落」的初速度散布 */
    public static final double SCATTER_POWER = 0.35;

    // ==================== 运行时状态 ====================

    private final Player player;

    public Phase phase = Phase.NONE;
    /** 当前预告目标（追踪/偷窃对象） */
    public UUID targetUuid = null;
    public String targetName = "";
    /** 预告的宝物（物品注册 id 与显示名，读条成功时优先偷它） */
    public ResourceLocation announcedItemId = null;
    public String announcedItemName = "";
    /** 预告剩余时间（tick） */
    public int announceTicks = 0;
    /** 读条剩余时间（tick） */
    public int channelTicks = 0;
    /** 胜利计数（完成的预告盗窃次数） */
    public int winCount = 0;
    /** 收藏层数（0~3） */
    public int collections = 0;
    /** 迷魂铃是否已购买（每局限购 1 次） */
    public boolean locketBought = false;
    /** 替身人偶是否生效（可免疫一次死亡） */
    public boolean dollActive = false;
    /** 胜利条件达成提示已发送（避免每 tick 重复刷） */
    public boolean winNotified = false;

    public PhantomThiefPlayerComponent(Player player) {
        this.player = player;
    }

    public void init() {
        phase = Phase.NONE;
        targetUuid = null;
        targetName = "";
        announcedItemId = null;
        announcedItemName = "";
        announceTicks = 0;
        channelTicks = 0;
        winCount = 0;
        collections = 0;
        locketBought = false;
        dollActive = false;
        winNotified = false;
        if (player instanceof ServerPlayer sp) {
            removeCollectorModifier(sp);
        }
    }

    public void clear() {
        init();
    }

    /** 仅服务端：把追踪/计数状态同步给本人客户端。 */
    public void sync() {
        if (!player.level().isClientSide) {
            ModComponents.PHANTOM_THIEF.sync(player);
        }
    }

    /** 只同步给本人：追踪目标对其他玩家保密（他人知道了会围捕大怪盗） */
    @Override
    public boolean shouldSyncWith(ServerPlayer observer) {
        return observer == player;
    }

    // ==================== 胜利条件 ====================

    /**
     * 达成独立胜利所需次数 = 玩家总数 / 3（至少 1）。
     * 原理：从 SRE 对局角色表取玩家总数（包含已淘汰者），避免把观战者/对局外玩家计入。
     */
    public int requiredCount() {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        int total = gw != null ? gw.getRoles().size() : player.level().players().size();
        return Math.max(1, total / 3);
    }

    public boolean hasReachedWin() {
        return winCount >= requiredCount();
    }

    // ==================== 预告信（主动1） ====================

    /**
     * 发送预告信（由服务端网络包处理器在完成角色/存活校验后调用）。
     * 从目标背包随机抽取一件非基础任务物品（优先武器类）标记为「目标宝物」。
     */
    public void startAnnouncement(ServerPlayer thief, ServerPlayer target) {
        // 先选宝物：无可用物品则失败
        ItemStack treasure = pickTreasure(target);
        if (treasure == null) {
            thief.displayClientMessage(Component.literal("§c目标身上没有值得下手的东西…"), true);
            return;
        }

        phase = Phase.ANNOUNCING;
        targetUuid = target.getUUID();
        targetName = target.getName().getString();
        announcedItemId = BuiltInRegistries.ITEM.getKey(treasure.getItem());
        announcedItemName = treasure.getHoverName().getString();
        announceTicks = ANNOUNCE_DURATION_TICKS;
        channelTicks = 0;

        // 目标收到醒目 title（仅该玩家可见）
        target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        target.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("§5§l大怪盗的预告信已送达")));
        target.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("§f目标：你的【§d" + announcedItemName + "§f】。预告将在 §590 §f秒后到期")));

        target.playNotifySound(SoundEvents.WANDERING_TRADER_DISAPPEARED, SoundSource.PLAYERS, 0.8f, 1.4f);
        thief.displayClientMessage(Component.literal(
                "§5§l[预告信] §f已送达 §d" + targetName + "§f，目标宝物：【§d"
                        + announcedItemName + "§f】。§7追踪已开始（90 秒）"), false);
        sync();
    }

    // ==================== 妙手空空（主动2，G 键） ====================

    /**
     * 尝试开始读条（预告期间接近目标 ≤ 3 格时按技能键）。
     */
    public boolean tryStartChannel(ServerPlayer thief) {
        if (phase == Phase.CHANNELING) return true; // 读条中重复按键忽略
        if (phase != Phase.ANNOUNCING) {
            thief.displayClientMessage(Component.literal("§c先通过背包中的玩家头像发送预告信"), true);
            return false;
        }
        ServerPlayer target = getTargetPlayer();
        if (target == null) {
            cancelAnnouncement("目标已消失，预告失效");
            return false;
        }
        if (thief.distanceTo(target) > STEAL_RANGE) {
            thief.displayClientMessage(Component.literal(
                    "§c距离目标太远（需 ≤ 3 格，当前 " + String.format("%.1f", thief.distanceTo(target)) + " 格）"), true);
            return false;
        }
        phase = Phase.CHANNELING;
        channelTicks = channelDuration();
        thief.displayClientMessage(Component.literal("§5§l[妙手空空] §f开始行窃…保持不要与目标对视！"), true);
        sync();
        return true;
    }

    /** 当前读条总时长（收藏层数缩减，下限 1.5 秒） */
    public int channelDuration() {
        return Math.max(MIN_CHANNEL_TICKS, CHANNEL_BASE_TICKS - collections * CHANNEL_REDUCTION_PER_COLLECTION);
    }

    // ==================== 服务端 tick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
        if (gw == null || !gw.isRunning()) return;
        if (!gw.isRole(player, PhantomThiefRole.PHANTOM_THIEF)) return;

        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            // 死亡/观战：预告失效（目标收到提示），静默清状态
            if (phase != Phase.NONE) {
                notifyTarget(Component.literal("§7预告信随大怪盗的退场而失效了…"), false);
            }
            resetAnnouncement();
            return;
        }

        switch (phase) {
            case ANNOUNCING -> tickAnnouncing(sp);
            case CHANNELING -> tickChanneling(sp);
            case NONE -> { }
        }

        // 胜利条件达成提示（一次）
        if (!winNotified && hasReachedWin()) {
            winNotified = true;
            sp.displayClientMessage(Component.literal(
                    "§6§l[谢幕] §f胜利条件已达成（§e" + winCount + "§f/§e" + requiredCount()
                            + "§f）！可在商店购买「谢幕」结束这场演出，或继续留在场上"), false);
            sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            sync();
        }

        // 每秒兜底同步（目标客户端的按钮禁用态 + 透视数据）
        if (player.tickCount % 20 == 0) {
            sync();
        }
    }

    /** 预告追踪阶段：倒计时 + 双方提示；目标死亡则预告直接结束 */
    private void tickAnnouncing(ServerPlayer sp) {
        announceTicks--;
        ServerPlayer target = getTargetPlayer();
        if (target == null) {
            cancelAnnouncement("目标已消失，预告失效");
            return;
        }
        // 目标死亡/离场：预告直接结束（不触发冷却，可立即物色新目标）
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            sp.displayClientMessage(Component.literal(
                    "§7目标已退场，预告结束。重新物色目标吧"), true);
            resetAnnouncement();
            sync();
            return;
        }
        if (announceTicks <= 0) {
            // 预告到期：双方提示
            target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 10));
            target.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("§a§l预告已到期")));
            target.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("§f宝物安然无恙")));
            notifyTarget(Component.literal("§a大怪盗的预告已到期，你保住了自己的宝物！"), true);
            sp.displayClientMessage(Component.literal("§7预告到期，这次失手了…重新物色目标吧"), true);
            resetAnnouncement();
            triggerAnnounceCooldown(sp);
            sync();
            return;
        }
        // 每 5 秒给双方 actionbar 提示
        if (announceTicks % 100 == 0) {
            int seconds = (announceTicks + 19) / 20;
            sp.displayClientMessage(Component.literal(
                    "§5预告追踪中：" + targetName + " 的【" + announcedItemName + "】，剩余 §f" + seconds + "§5 秒"), true);
            notifyTarget(Component.literal(
                    "§5大怪盗仍在觊觎你的【" + announcedItemName + "】（§f" + seconds + "§5 秒后到期）"), true);
        }
    }

    /** 读条阶段：目标听到翻纸声；对视打断；读完成功偷窃（读条开始后无需再贴近目标） */
    private void tickChanneling(ServerPlayer sp) {
        ServerPlayer target = getTargetPlayer();
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            cancelAnnouncement("目标已消失，预告失效");
            return;
        }

        double dist = sp.distanceTo(target);
        // 对视打断：目标在视线范围内且准星指向大怪盗（点积超过阈值即视为察觉）
        // 读条一旦开始就不再要求贴脸（大怪盗可拉开距离），仅目标回头看见才会失败
        if (dist <= CHANNEL_MAX_RANGE && isLookingAt(target, sp)) {
            interruptChannel(sp, target);
            return;
        }

        channelTicks--;
        // 读条音效：轻微翻纸声，仅目标本人能听到（不明显但留有线索）
        if (channelTicks % 10 == 0 && channelTicks > 0) {
            target.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.4f, 1.3f);
        }

        if (channelTicks <= 0) {
            completeSteal(sp, target);
        }
    }

    /** 目标准星是否指向 observer（视线锥判定） */
    private boolean isLookingAt(ServerPlayer watcher, ServerPlayer observed) {
        var toTarget = observed.getEyePosition().subtract(watcher.getEyePosition());
        double len = toTarget.length();
        if (len < 1.0e-4) return true;
        return watcher.getViewVector(1.0f).dot(toTarget.normalize()) > LOOK_AT_DOT_THRESHOLD;
    }

    /**
     * 读条被打断（对视）：华丽脱身触发移速加成（不隐身），
     * 预告继续有效（90 秒窗口内可再次尝试）。
     */
    private void interruptChannel(ServerPlayer sp, ServerPlayer target) {
        phase = Phase.ANNOUNCING;
        channelTicks = 0;
        applyEscapeBoost(sp);
        sp.displayClientMessage(Component.literal("§c§l被察觉了！§f偷窃被打断，快撤退！"), true);
        target.displayClientMessage(Component.literal(
                "§e你察觉到了一道视线——大怪盗正在偷窃！快环顾四周！"), true);
        target.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.9f, 1.6f);
        sync();
    }

    // ==================== 偷窃结算 ====================

    /** 读条成功：转移宝物 + 200 金币 + 华丽脱身 + 胜利计数/收藏层数 */
    private void completeSteal(ServerPlayer sp, ServerPlayer target) {
        ItemStack stolen = takeTreasure(sp, target);
        if (stolen == null) {
            // 目标身上已无可用物品（宝物被丢弃/用掉且无其他目标）：预告作废
            cancelAnnouncement("目标身上已经空空如也，预告作废");
            return;
        }

        // 标记为「偷来的」→ 大怪盗死亡时戏剧性散落
        stolen.set(Dim201ModItems.STOLEN_MARK, true);
        if (!SREItemUtils.insertStackInFreeSlot(sp, stolen)) {
            sp.drop(stolen, false); // 背包满时掉在脚边
        }

        // 金币奖励
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        shop.setBalance(shop.balance + STEAL_REWARD_GOLD);
        shop.sync();

        // 胜利计数 + 收藏层数
        winCount++;
        collections = Math.min(MAX_COLLECTIONS, collections + 1);
        applyCollectorModifier(sp);

        // 华丽脱身：40% 移速 3 秒 + 隐身 5 秒
        applyEscapeBoost(sp);
        sp.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY, ESCAPE_INVIS_TICKS, 0, true, false, false));

        sp.displayClientMessage(Component.literal(
                "§5§l[妙手空空] §f得手！【§d" + stolen.getHoverName().getString()
                        + "§f】 +§e" + STEAL_REWARD_GOLD + "§f 金币 §7（胜利 " + winCount
                        + "/" + requiredCount() + "）"), false);
        target.displayClientMessage(Component.literal(
                "§5你的【§d" + stolen.getHoverName().getString() + "§5】被大怪盗偷走了！"), false);
        target.playNotifySound(SoundEvents.VILLAGER_HURT, SoundSource.PLAYERS, 0.8f, 0.6f);

        resetAnnouncement();
        triggerAnnounceCooldown(sp);
        sync();
    }

    /** 预告信进入 15 秒冷却（得手/到期后）。复用 SRE 原生技能冷却，自动递减并同步客户端 */
    private void triggerAnnounceCooldown(ServerPlayer sp) {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(sp);
        if (ability != null) {
            ability.setCooldown(ANNOUNCE_COOLDOWN_TICKS);
        }
    }

    /**
     * 取走宝物：优先读条目标预告的那件（按注册 id 搜索），
     * 不在背包时退化为随机一件非保护物品；全部为空返回 null。
     */
    private ItemStack takeTreasure(ServerPlayer sp, ServerPlayer target) {
        Inventory inv = target.getInventory();
        if (announcedItemId != null) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(s.getItem()).equals(announcedItemId)) {
                    inv.setItem(i, ItemStack.EMPTY);
                    return s;
                }
            }
        }
        // 退化：随机偷一件（同预告时的筛选规则）
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && !isProtectedItem(inv.getItem(i))) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) return null;
        int slot = slots.get(sp.getRandom().nextInt(slots.size()));
        ItemStack s = inv.getItem(slot);
        inv.setItem(slot, ItemStack.EMPTY);
        return s;
    }

    // ==================== 预告目标物挑选 ====================

    /**
     * 从目标背包随机抽取一件「非基础任务物品」：优先武器类（刀、左轮等枪械），
     * 永不抽取钥匙与信件（开局自带物品）。
     */
    private ItemStack pickTreasure(ServerPlayer target) {
        Inventory inv = target.getInventory();
        List<Integer> weaponSlots = new ArrayList<>();
        List<Integer> normalSlots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || isProtectedItem(s)) continue;
            if (isWeapon(s)) weaponSlots.add(i);
            else normalSlots.add(i);
        }
        List<Integer> pool = !weaponSlots.isEmpty() ? weaponSlots : normalSlots;
        if (pool.isEmpty()) return null;
        return inv.getItem(pool.get(target.getRandom().nextInt(pool.size())));
    }

    /** 武器优先池：刀、枪械标签、短霰弹枪 */
    private static boolean isWeapon(ItemStack s) {
        return s.is(TMMItems.KNIFE) || s.is(TMMItemTags.GUNS) || s.is(ModItems.SHORT_SHOTGUN);
    }

    /** 保护物品：钥匙与信件（开局自带），镜像 SRE 阿蒙的窃取保护名单 */
    private static boolean isProtectedItem(ItemStack s) {
        return s.is(TMMItems.KEY) || s.is(TMMItems.IRON_DOOR_KEY) || s.is(TMMItems.LETTER)
                || s.is(ModItems.LETTER_ITEM) || s.is(ModItems.COURIER_MAIL) || s.is(ModItems.RECEIVED_MAIL);
    }

    // ==================== 被动与工具 ====================

    /** 华丽脱身的移速加成（速度 II = +40%，3 秒） */
    public void applyEscapeBoost(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, ESCAPE_SPEED_TICKS, 1, true, false, false));
    }

    /** 重算收藏家之傲的移速修饰符（每层 +5%） */
    public void applyCollectorModifier(ServerPlayer sp) {
        AttributeInstance attr = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        removeCollectorModifier(sp);
        if (collections > 0) {
            attr.addTransientModifier(new AttributeModifier(
                    COLLECTOR_MODIFIER_ID, SPEED_PER_COLLECTION * collections,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void removeCollectorModifier(ServerPlayer sp) {
        AttributeInstance attr = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(COLLECTOR_MODIFIER_ID);
        }
    }

    /**
     * 死亡时戏剧性散落全部偷来的物品（由 PhantomThiefRole 的 OnPlayerDeath 调用）。
     */
    public void scatterStolenItems(ServerPlayer sp) {
        var inv = sp.getInventory();
        boolean dropped = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.has(Dim201ModItems.STOLEN_MARK)) continue;
            inv.setItem(i, ItemStack.EMPTY);
            ItemEntity drop = new ItemEntity(sp.serverLevel(), sp.getX(), sp.getY() + 0.5, sp.getZ(), s);
            // 戏剧性：随机方向弹飞
            double angle = sp.getRandom().nextDouble() * Math.PI * 2;
            double power = 0.15 + sp.getRandom().nextDouble() * SCATTER_POWER;
            drop.setDeltaMovement(Math.cos(angle) * power, 0.3, Math.sin(angle) * power);
            drop.setPickUpDelay(20);
            sp.serverLevel().addFreshEntity(drop);
            dropped = true;
        }
        if (dropped) {
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.ITEM_FRAME_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
        }
    }

    /**
     * 替身人偶死亡豁免（由 PhantomThiefRole 的死亡事件调用）。
     *
     * @return true 已消耗人偶、取消死亡
     */
    public boolean tryDollSave(ServerPlayer sp) {
        if (!dollActive) return false;
        dollActive = false;
        sp.setHealth(PhantomThiefRole.DOLL_HEALTH);
        sp.fallDistance = 0;
        sp.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1f);
        sp.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
        sp.displayClientMessage(Component.literal("§e§l替身人偶替你承受了死亡！"), false);
        sync();
        return true;
    }

    // ==================== 内部工具 ====================

    private ServerPlayer getTargetPlayer() {
        if (targetUuid == null) return null;
        return player.level().getServer().getPlayerList().getPlayer(targetUuid);
    }

    private void notifyTarget(Component msg, boolean actionbar) {
        ServerPlayer target = getTargetPlayer();
        if (target != null) {
            target.displayClientMessage(msg, actionbar);
        }
    }

    /** 取消预告并解锁技能（不发到期演出） */
    private void cancelAnnouncement(String thiefMsg) {
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§c" + thiefMsg), true);
        }
        notifyTarget(Component.literal("§7大怪盗的预告失效了…"), true);
        resetAnnouncement();
        sync();
    }

    /** 清空预告状态并解锁技能（公开：角色死亡/对局失效等外部流程调用） */
    public void resetAnnouncement() {
        phase = Phase.NONE;
        targetUuid = null;
        targetName = "";
        announcedItemId = null;
        announcedItemName = "";
        announceTicks = 0;
        channelTicks = 0;
    }

    // ==================== NBT 持久化（Component 抽象方法） ====================

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        phase = Phase.values()[tag.getInt("phase")];
        targetUuid = tag.hasUUID("target") ? tag.getUUID("target") : null;
        targetName = tag.getString("target_name");
        announcedItemId = tag.contains("treasure_id")
                ? ResourceLocation.tryParse(tag.getString("treasure_id")) : null;
        announcedItemName = tag.getString("treasure_name");
        announceTicks = tag.getInt("announce_ticks");
        channelTicks = tag.getInt("channel_ticks");
        winCount = tag.getInt("win_count");
        collections = tag.getInt("collections");
        locketBought = tag.getBoolean("locket_bought");
        dollActive = tag.getBoolean("doll_active");
        winNotified = tag.getBoolean("win_notified");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("phase", phase.ordinal());
        if (targetUuid != null) {
            tag.putUUID("target", targetUuid);
        }
        tag.putString("target_name", targetName);
        if (announcedItemId != null) {
            tag.putString("treasure_id", announcedItemId.toString());
        }
        tag.putString("treasure_name", announcedItemName);
        tag.putInt("announce_ticks", announceTicks);
        tag.putInt("channel_ticks", channelTicks);
        tag.putInt("win_count", winCount);
        tag.putInt("collections", collections);
        tag.putBoolean("locket_bought", locketBought);
        tag.putBoolean("doll_active", dollActive);
        tag.putBoolean("win_notified", winNotified);
    }

    /** 内部工具：取物品注册 id */
    private static final class BuiltInIds {
        static ResourceLocation getKey(ItemStack stack) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        }
    }
}
