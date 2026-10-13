package com.dim201.dim201addon;

import com.dim201.dim201addon.role.GojoRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Random;

public class GojoPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<GojoPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "gojo"),
            GojoPlayerComponent.class);

    public static final ResourceLocation GOJO_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "gojo_sukuna_defeat");

    private static final Random RANDOM = new Random();

    // 3 个自定义 SoundEvent（在 sounds.json 中配置：gojobuy.ogg / gojowin.ogg / gojodeath.ogg）
    private static final SoundEvent SOUND_BUY = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "gojobuy"));
    private static final SoundEvent SOUND_WIN = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "gojowin"));
    private static final SoundEvent SOUND_DEATH = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "gojodeath"));

    // 永久药水效果时长：Minecraft 中 duration >= 32767 (0x7FFF) 会显示为 "∞"
    private static final int PERMANENT = Integer.MAX_VALUE;

    // pendingDuelResult 取值
    private static final int PENDING_NONE = 0;
    private static final int PENDING_WIN = 1;
    private static final int PENDING_DEATH = 2;

    // 对决结果高亮/播报/结算的阶段时间：90 秒高亮，80 秒时刻播报（剩余 10 秒），90 秒时刻正式结算
    private static final int HIGHLIGHT_TICKS = 90 * 20;     // 1800 tick = 90 秒
    private static final int ANNOUNCE_AT = 10 * 20;        // 剩余 10 秒 = 已经过 80 秒时播报

    // ========== 概率单位（×10，支持 0.5% 粒度）==========
    // 所有 winProbability/deathProbability/positiveProbability/negativeProbability 字段
    // 全部使用「整数×10」保存：数值 5 表示 0.5%，数值 25 表示 2.5%，数值 100 表示 10%。
    // HUD 文本/广播文本 /10 后格式化；抽桶 roll 取值 0-999（映射 0-99.9%），桶边界直接用原值比较。
    private static final int SCALE = 10;

    private final Player player;

    // 初始概率（×10）：死亡原 10% → 100；正面 35% → 350；负面 55% → 550；总和 1000 = 100.0%
    public int duelCount = 0;
    public int winProbability = 0;               // 0.0%
    public int deathProbability = 10 * SCALE;    // 10.0%（初始值保留原设定 10%）
    public int positiveProbability = 35 * SCALE; // 35.0%
    public int negativeProbability = 55 * SCALE; // 55.0%
    // 用户要求：前 2 次购买新宿车票是"安全垫"，不触发胜/死最终决战（只抽正/负面效果）；从第 3 次购票才正式可能胜利或死亡
    private static final int SAFE_DUEL_COUNT = 2;

    public boolean shieldActive = false;

    // 走马灯倒计时（tick），>0 表示4分钟后强制死亡
    public int doomTicksLeft = 0;

    // 新宿车票购买冷却 / 禁用倒计时（tick）。>0 期间，商店里车票变灰且点击购买会被拒绝。
    // 由负面效果"术式熔断"触发：30秒内无法购买新宿车票
    public int ticketCooldownTicks = 0;

    // 新宿车票「内置购买内置 5s 冷却」tick。每次购票成功后设为 100 tick（5s），
    // 防止快速连点购票按钮 / 快速刷票。与"术式熔断"冷却独立累加。
    private static final int PURCHASE_INTERNAL_CD_TICKS = 5 * 20;
    public int purchaseInternalCdTicks = 0;

    // 是否进入内置冷却中（>0 时不能购票）
    public boolean isPurchaseInternalCdActive() {
        return purchaseInternalCdTicks > 0;
    }

    // 延迟对决结果相关字段
    public int pendingDuelResult = PENDING_NONE;
    public int pendingDuelTicksLeft = 0;
    public boolean pendingAnnounced = false;

    public GojoPlayerComponent(Player player) {
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
        duelCount = 0;
        winProbability = 0;
        deathProbability = 10 * SCALE;
        positiveProbability = 35 * SCALE;
        negativeProbability = 55 * SCALE;
        shieldActive = false;
        doomTicksLeft = 0;
        pendingDuelResult = PENDING_NONE;
        pendingDuelTicksLeft = 0;
        pendingAnnounced = false;
        ticketCooldownTicks = 0;
        purchaseInternalCdTicks = 0;
        applyShieldIfNeeded();
        sync();
    }

    @Override
    public void clear() {
        duelCount = 0;
        winProbability = 0;
        deathProbability = 10 * SCALE;
        positiveProbability = 35 * SCALE;
        negativeProbability = 55 * SCALE;
        shieldActive = false;
        doomTicksLeft = 0;
        pendingDuelResult = PENDING_NONE;
        pendingDuelTicksLeft = 0;
        pendingAnnounced = false;
        ticketCooldownTicks = 0;
        purchaseInternalCdTicks = 0;
        // clear 与 init 不同：ResetPlayerEvent / ModdedRoleRemoved 都会对「非五条悟玩家」也调 clear，
        // 这里绝不能再调 applyShieldIfNeeded() / addArmor()，否则所有玩家开局都会白嫖一层护盾
        if (player instanceof ServerPlayer sp) {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(sp);
            // 保险：如果组件之前给这个人记了 shieldActive，尝试同步扣掉对应的那一层护盾
            // （避免 shield 状态与 shieldActive 不同步）
            if (shieldActive && armor.getArmor() > 0) {
                // 这里故意不 removeArmor，因为我们无法确认这层护盾是不是真的由 gojo 组件给的
                // 只把标志位置 false 就够；真正的"五条悟专属护盾"只在 ModdedRoleAssigned.init() 给
            }
        }
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    private void applyShieldIfNeeded() {
        if (!(player instanceof ServerPlayer sp)) return;
        // 注意：ModdedRoleAssigned.EVENT 触发 init() 时，可能玩家可能还没进入 Survival，所以放宽 alive 状态，因此这里不拦截 isPlayerAliveAndSurvival 以免开局时 alive=false
        if (!shieldActive) {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(sp);
            if (armor.getArmor() < 1) {
                armor.addArmor();
            }
            // 只要给了 1 层护盾（或者本身已存在就直接标记 shieldActive=true，HUD 就不会再显示"已消耗"）
            shieldActive = true;
        }
    }

    // 向服务器中所有玩家屏幕正上方中间发送广播员风格的大文案（全员可见）
    private void broadcastMsg(ServerLevel serverLevel, String text) {
        Component msg = Component.literal(text);
        for (ServerPlayer p : serverLevel.players()) {
            BroadcastCommand.BroadcastMessage(p, msg);
        }
    }

    // 兼容旧签名：传入单个玩家自动取其所在世界进行全服广播
    private void broadcastMsg(ServerPlayer sp, String text) {
        if (sp != null && sp.level() instanceof ServerLevel sl) {
            broadcastMsg(sl, text);
        }
    }

    // 给服务器里所有玩家单独播放一次同一个音效（volume 0.2f 时可认为"全局听到但不吵"）
    private void playGlobalSound(ServerLevel serverLevel, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : serverLevel.players()) {
            p.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    public void onDuelPurchased(ServerPlayer sp) {
        // 三重保险 ①：已进入对决倒计时未结算 → 拒绝再次买票（ShopEntry 已经拦截，但这里兜底）
        if (isDuelPending()) {
            int secs = (pendingDuelTicksLeft + 19) / 20;
            sendActionBar(sp, "§5§l「新宿决战」§f 进行中！还剩 §c" + secs + "秒 §f后才可再次购票");
            return;
        }
        // 三重保险 ②：5 秒内置购票冷却未结束 → 拒绝再次买票（ShopEntry 已经拦截，兜底）
        if (isPurchaseInternalCdActive()) {
            int secs = (purchaseInternalCdTicks + 19) / 20;
            sendActionBar(sp, "§7新宿车票 §f内置冷却中（剩余 §e" + secs + "秒 §f）");
            return;
        }
        // 三重保险 ③：术式熔断等"车票禁用期" → 拒绝再次买票
        if (ticketCooldownTicks > 0) {
            int secs = (ticketCooldownTicks + 19) / 20;
            sendActionBar(sp, "§c§l术式熔断！§f 新宿车票被禁止购买（剩余 §c" + secs + "秒 §f）");
            return;
        }

        ServerPlayer serverPlayer = sp;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 购买车票成功：立刻进入 5 秒内置冷却（放这里，后续 roll 不管是什么结果都会生效，防止极短时间内再点一次）
        purchaseInternalCdTicks = Math.max(purchaseInternalCdTicks, PURCHASE_INTERNAL_CD_TICKS);

        // 购买车票成功立刻播放车票音效（全员可听，音量 0.4f）
        playGlobalSound(serverLevel, SOUND_BUY, 0.4f, 1.0f);

        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0f, 1.2f);

        sendActionBar(serverPlayer, "§5§l「新宿决战」§f 与宿傩的最终对决开始...");

        // 通用对决开场广播（不提示 30s/结算，因为只有胜/死才会进入延迟结算；正/负面是即时生效）
        broadcastMsg(serverLevel,
                "§5§l═══════════════════════════════════\n"
                        + "§f     ［向南］五条悟   §cVS   §4两面宿傩\n"
                        + "§5§l═══════════════════════════════════\n"
                        + "§7虚式「茈」准备就绪，对决正式拉开帷幕！");

        // 抽桶：取值 0..999（精度 0.1%），与 ×10 的概率原值直接比较
        int roll = RANDOM.nextInt(100 * SCALE);

        int winMax = winProbability;
        int positiveMax = winMax + positiveProbability;
        int negativeMax = positiveMax + negativeProbability;

        // 用户新增：前 SAFE_DUEL_COUNT(2) 次购票是"安全垫"阶段，跳过胜利/死亡结算桶，只在正/负面结果中归一化抽一个
        //    为了避免"正面+负面不等于 1000"导致 roll 超范围，用一个单独的 0-999 重新归一化
        boolean safeStage = (duelCount < SAFE_DUEL_COUNT);
        if (safeStage) {
            int posRange = positiveProbability;
            int negRange = negativeProbability;
            int sum = Math.max(1, posRange + negRange);
            int roll2 = RANDOM.nextInt(sum);
            String info = (duelCount + 1) + "/" + SAFE_DUEL_COUNT;
            if (roll2 < posRange) {
                sendActionBar(serverPlayer, "§7新宿车票 · 第 §e" + info + " §7次（安全垫）：抽中正面效果");
                onDuelPositive(serverPlayer);
            } else {
                sendActionBar(serverPlayer, "§7新宿车票 · 第 §e" + info + " §7次（安全垫）：抽中负面效果");
                onDuelNegative(serverPlayer);
            }
            // 虽然安全垫不结算胜负，但仍然保留 increaseProbabilities()，让玩家第 3 次时 win/death 各 +5%（×10 单位=各+50）——保持一致的升级节奏
            increaseProbabilities();
            sync();
            return;
        }

        if (roll < winMax) {
            // 抽到胜利：进入 30 秒高亮，20 秒播报，30 秒才结算
            enterPendingDuel(serverPlayer, PENDING_WIN);
        } else if (roll < positiveMax) {
            onDuelPositive(serverPlayer);
        } else if (roll < negativeMax) {
            onDuelNegative(serverPlayer);
        } else {
            // 抽到死亡：同样进入 30 秒高亮，20 秒播报死亡，30 秒才真正击杀
            enterPendingDuel(serverPlayer, PENDING_DEATH);
        }

        increaseProbabilities();
        sync();
    }

    // 进入"延迟结算对决"状态：立刻高亮90秒，等待80秒播报/90秒结算
    private void enterPendingDuel(ServerPlayer sp, int resultKind) {
        pendingDuelResult = resultKind;
        pendingDuelTicksLeft = HIGHLIGHT_TICKS;  // 1800 tick = 90 秒
        pendingAnnounced = false;
        // 给五条悟加 90 秒发光（Glowing）效果，让所有人都能清楚看到他（高亮）
        sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, HIGHLIGHT_TICKS, 0, false, false, true));
        sendActionBar(sp, "§5§l「新宿决战」§f 倒计时开始：§c90秒 §f后揭晓结果并结算！存活到最后！");

        // ✦ 只有抽到胜或死（进入延迟结算）时，才在全员广播里提示「90秒后结算」 ✦
        ServerLevel serverLevel = sp.serverLevel();
        broadcastMsg(serverLevel, "§5§l「新宿决战」§f 90秒倒计时开始，80秒时揭晓结果，请务必存活到结算！");
    }

    // ============ 80 秒时刻：播报胜利文案和音频（只播报，不结算）============
    private void announcePendingWin(ServerLevel serverLevel) {
        playGlobalSound(serverLevel, SOUND_WIN, 0.4f, 1.0f);

        // 超长胜利广播文案
        broadcastMsg(serverLevel, "§6§l茈发动前的41秒内，§f新宿再次响起了五条悟的吟唱。\n"
                + "§6§l「九纲」「偏光」「乌与声明」「表里之间」。\n"
                + "§c宿傩 §f明白自己再也没有任何机会阻止 §9茈 §f的诞生了！\n"
                + "§5§l无限制的虚式 §f如同核爆一般在新宿亮起冲天的光芒，\n"
                + "魔虚罗的轮盘在茈中灰飞烟灭……\n"
                + "废墟之中，全力护住自己的 §c宿傩 §f无力地靠在残破的建筑上支撑着身体，\n"
                + "他的 §c左手 §f和 §c大腿 §f都被这一击吞噬殆尽。\n"
                + "同样伤痕累累的 §b五条悟 §f出现在宿傩面前，\n"
                + "宿傩立刻强迫自己不再倚靠墙壁，堂堂正正站在五条悟面前。\n"
                + "但是在 §9咒力同源 §f的影响下，五条悟所承受的伤害被大大削弱，\n"
                + "在 §a反转术式 §f的治疗下五条悟的身体再次恢复。\n"
                + "§7对五条悟来说，决定性的一击远距离「茈」只是其即兴创作。\n"
                + "§7结果魔虚罗被秒—— §6§l「看来完成的还算不错」。\n"
                + "§7谁能想到魔虚罗和宿傩发了疯阻止的只是五条悟刚刚开发的技能？\n"
                + "§7五条悟在茈发动前的短短41秒内见招拆招，\n"
                + "完成了数个几乎可以编入教科书的随机应变操作。\n"
                + "§e虎杖 §f呆呆的看着屏幕仍不可置信：\n"
                + "§7「也就是说……」\n"
                + "§a§l「没错，是五条悟赢了！」");

        // 特殊胜利结算标题（80s 时刻播报后还剩 10s，文案里仍为「10秒后正式结算」）
        broadcastMsg(serverLevel, "§6§l✦✦✦ 特殊胜利结算 ✦✦✦\n§6§l最强的战绩，铭刻于新宿！（10秒后正式结算）");
    }

    // ============ 80 秒时刻：播报死亡文案和音频（只播报，不击杀）============
    private void announcePendingDeath(ServerLevel serverLevel) {
        playGlobalSound(serverLevel, SOUND_DEATH, 0.4f, 1.0f);

        broadcastMsg(serverLevel, "§4§l✗✗✗ 新宿决战 失败 ✗✗✗\n"
                + "§c宿傩 §f凭借束缚，达成了对于 §9世界本身的斩击 §f！\n"
                + "§b五条悟 §f根本无法抵挡！");
        broadcastMsg(serverLevel, "§4§l也就是说……没错，是宿傩赢了！（10秒后五条悟将正式陨落）");
        broadcastMsg(serverLevel, "§7§o「没能让宿傩大人尽兴，真是抱歉啊……」");
    }

    // ============ 90 秒结束：真正执行胜利结算（无音频无播报，直接效果+独立胜利）============
    private void finalizePendingWin(ServerPlayer sp) {
        ServerLevel serverLevel = sp.serverLevel();
        sendMessage(sp, "§6§l✦ 胜利！§f 你击败了宿傩，展现了最强的实力！");
        serverLevel.playSound(null, sp.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);

        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(sp);
        for (int i = 0; i < 3; i++) armor.addArmor();

        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, PERMANENT, 2, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, PERMANENT, 1, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PERMANENT, 1, false, false, true));

        // 移除发光（避免结束后还高亮）
        sp.removeEffect(MobEffects.GLOWING);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (gameWorld != null && gameWorld.isRunning()) {
            RoleUtils.customWinnerWin(sp.serverLevel(), GojoRole.GOJO_ID.getPath(), 0xFF6FA8DC);
        }
    }

    // ============ 90 秒结束：真正执行死亡结算（80 秒时刻已播 gojodeath+文案，这里只击杀）============
    private void finalizePendingDeath(ServerPlayer sp) {
        sendMessage(sp, "§4§l✗ 战败 §f 你被宿傩的斩击击败，陨落于新宿街头");
        doomTicksLeft = 0;
        sp.removeEffect(MobEffects.GLOWING);
        GameUtils.forceKillPlayer(sp, true, null, GOJO_DEATH);
    }

    // ============================ 正面效果（5种，等权重，永久效果 + 全员广播）============================
    private void onDuelPositive(ServerPlayer sp) {
        ServerLevel serverLevel = sp.serverLevel();
        int type = RANDOM.nextInt(5);
        switch (type) {
            case 0 -> {
                // 风湿束缚 → 跳跃I（永久）
                sp.addEffect(new MobEffectInstance(MobEffects.JUMP, PERMANENT, 0, false, false, true));
                broadcastMsg(serverLevel, "§a§l✦ 束缚成立\n"
                        + "§b五条悟 §f立下 §7「这一战后必定患上不可治愈的风湿为代价，得到跳跃力的永久提升」 §f的束缚，\n"
                        + "以超强的跳跃能力躲开了 §c宿傩 §f针对腰部的斩击。\n"
                        + "§e§l效果：§a获得 跳跃提升 I（永久）");
                sendMessage(sp, "§a✦ 束缚 · 风湿代价 §f 跳跃提升 I（永久）");
            }
            case 1 -> {
                // 垃圾桶束缚 → 速度I（永久）
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PERMANENT, 0, false, false, true));
                broadcastMsg(serverLevel, "§a§l✦ 束缚成立\n"
                        + "§b五条悟 §f立下 §7「这一战后靠近垃圾桶，100%会被路过丢垃圾的人砸中为代价，得到速度的永久提升」 §f的束缚，\n"
                        + "闪掉了 §c宿傩 §f满含咒力的肘击。\n"
                        + "§e§l效果：§a获得 速度 I（永久）");
                sendMessage(sp, "§a✦ 束缚 · 垃圾桶代价 §f 速度 I（永久）");
            }
            case 2 -> {
                // 遗忘记忆束缚 → 隐身（永久）
                sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, PERMANENT, 0, false, false, true));
                broadcastMsg(serverLevel, "§a§l✦ 束缚成立\n"
                        + "§b五条悟 §f立下 §7「这一战后会遗忘最重要的记忆为代价，永久屏蔽宿傩的感知」 §f的束缚，\n"
                        + "以此寻找更多攻击的机会。\n"
                        + "§e§l效果：§a获得 永久 隐身");
                sendMessage(sp, "§a✦ 束缚 · 遗忘代价 §f 隐身（永久）");
            }
            case 3 -> {
                // 掌握咒力核心 → 额外 +10% 胜利概率（×10 单位 = +100）
                winProbability = Math.min(winProbability + 100, 80 * SCALE);
                recalcRemainingProbabilities();
                String cur = formatProb10(winProbability);
                broadcastMsg(serverLevel, "§6§l✦ 境界突破\n"
                        + "§b五条悟 §f在生死中掌握了 §9咒力的核心 §f！\n"
                        + "§e§l效果：§a胜利概率 +10%（当前 " + cur + "）");
                sendMessage(sp, "§6✦ 咒力核心 §f 胜利概率 +10%（当前：" + cur + "）");
            }
            case 4 -> {
                // 天赋消失束缚 → 铺垫茈 +5% 胜利概率（×10 单位 = +50）
                winProbability = Math.min(winProbability + 50, 80 * SCALE);
                recalcRemainingProbabilities();
                String cur = formatProb10(winProbability);
                broadcastMsg(serverLevel, "§a§l✦ 束缚成立\n"
                        + "§b五条悟 §f立下 §7「这一战后部分咒术师天赋消失为代价，获得此刻更强的咒力操纵能力」 §f的束缚，\n"
                        + "为接下来的 §5虚式·茈 §f发动铺垫。\n"
                        + "§e§l效果：§a胜利概率 +5%（当前 " + cur + "）");
                sendMessage(sp, "§a✦ 束缚 · 天赋代价 §f 胜利概率 +5%（当前：" + cur + "）");
            }
        }
        sync();
    }

    // ============================ 负面效果（7种，等权重，永久效果 + 全员广播）============================
    private void onDuelNegative(ServerPlayer sp) {
        ServerLevel serverLevel = sp.serverLevel();
        int type = RANDOM.nextInt(7);
        switch (type) {
            case 0 -> {
                // 眼睛被割伤 → 失明（永久）
                sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, PERMANENT, 0, false, false, true));
                broadcastMsg(serverLevel, "§c§l✧ 领域反噬\n"
                        + "§c宿傩 §f的斩击伴随着未知的束缚，割伤了 §b五条悟 §f的眼睛，无法治愈！\n"
                        + "§e§l效果：§c获得 失明（永久）");
                sendMessage(sp, "§c✧ 眼伤 §f 失明（永久）");
            }
            case 1 -> {
                // 连续黑闪穿透 → 缓慢+凋零（永久）
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PERMANENT, 1, false, false, true));
                sp.addEffect(new MobEffectInstance(MobEffects.WITHER, PERMANENT, 1, false, false, true));
                broadcastMsg(serverLevel, "§4§l✧ 黑闪连击\n"
                        + "§c宿傩 §f打出了连续的黑闪，伴随着未知的束缚成功穿透了 §9无下限术式 §f，攻击到了 §b五条悟 §f！\n"
                        + "§e§l效果：§c获得 缓慢 II（永久） + 凋零 II（永久）");
                sendMessage(sp, "§4✧ 穿透无下限 §f 缓慢 + 凋零（永久）");
            }
            case 2 -> {
                // 领域对撞 → 虚弱（永久） + 30秒内无法购买新宿车票（代替原本"技能冷却"）
                sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PERMANENT, 1, false, false, true));
                ticketCooldownTicks = Math.max(ticketCooldownTicks, 30 * 20);
                broadcastMsg(serverLevel, "§c§l✧ 术式熔断\n"
                        + "§b五条悟 §f与 §c宿傩 §f的领域对撞，导致了不可挽回的 §9术式熔断 §f！\n"
                        + "§e§l效果：§c获得 虚弱 II（永久） + 30秒内无法购买新宿车票");
                sendMessage(sp, "§c✧ 术式熔断 §f 虚弱（永久） + 30秒内无法购买新宿车票");
            }
            case 3 -> {
                // 魔虚罗偷袭重伤 → 定格10秒（多重超高 debuff 模拟）
                applyFreeze(sp, 10 * 20);
                broadcastMsg(serverLevel, "§4§l✧ 魔虚罗偷袭\n"
                        + "§b五条悟 §f应对 §c宿傩 §f之时，§d魔虚罗 §f趁虚而入，将五条悟打至重伤状态，§a反转术式 §f需要时间！\n"
                        + "§e§l效果：§c定格 10秒（高等级缓慢效果）");
                sendMessage(sp, "§4✧ 魔虚罗偷袭 §f 定格10秒，反转术式恢复中...");
            }
            case 4 -> {
                // 走马灯 → 4分钟后死亡（doomTicksLeft）
                doomTicksLeft = 4 * 60 * 20;
                broadcastMsg(serverLevel, "§4§l✧ 走马灯\n"
                        + "§b五条悟 §f被斩击斩出了 §4走马灯 §f……\n"
                        + "§c§l警告：§4四分钟后，你将陨落！\n"
                        + "§7若在此之前击败宿傩则可解除诅咒。");
                sendMessage(sp, "§4✧ 走马灯 §f 四分钟后死亡！快在之前击败宿傩！");
            }
            case 5 -> {
                // 宿傩掌握咒力核心 → +10% 死亡概率（×10 单位 = +100）
                deathProbability = Math.min(deathProbability + 100, 50 * SCALE);
                recalcRemainingProbabilities();
                String cur = formatProb10(deathProbability);
                broadcastMsg(serverLevel, "§4§l✧ 宿傩 境界突破\n"
                        + "§c宿傩 §f在战斗中掌握了 §9咒力的核心 §f！\n"
                        + "§e§l效果：§c死亡概率 +10%（当前 " + cur + "）");
                sendMessage(sp, "§4✧ 宿傩的成长 §f 死亡概率 +10%（当前：" + cur + "）");
            }
            case 6 -> {
                // 未知束缚无法闪避 → +5% 死亡概率（×10 单位 = +50）
                deathProbability = Math.min(deathProbability + 50, 50 * SCALE);
                recalcRemainingProbabilities();
                String cur = formatProb10(deathProbability);
                broadcastMsg(serverLevel, "§c§l✧ 束缚成立\n"
                        + "§c宿傩 §f以未知的束缚，创造出了无法闪避的斩击，§b五条悟 §f遭到了重创！\n"
                        + "§e§l效果：§c死亡概率 +5%（当前 " + cur + "）");
                sendMessage(sp, "§c✧ 束缚 · 无法闪避 §f 死亡概率 +5%（当前：" + cur + "）");
            }
        }
        sync();
    }

    private void applyFreeze(ServerPlayer sp, int ticks) {
        // 使用 4 种超高等级 debuff 组合模拟"定格10秒"
        // 缓慢 255：移动几乎为 0
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 254, false, false, true));
        // 挖掘疲劳 255：无法挖掘
        sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 254, false, false, true));
        // 跳跃弱化（使用 跳跃 效果 + 负值：级别 128 会让跳跃力 0）
        sp.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false, true));
        // 失明让视野黑掉，强化"定格"演出
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, true));
    }

    // ============================ 死亡（兼容走马灯触发：直接强制击杀，不会播放延迟阶段的播报）============================
    private void onDuelDeath(ServerPlayer sp) {
        // 清理 pending 状态，避免和新的对决冲突
        pendingDuelResult = PENDING_NONE;
        pendingDuelTicksLeft = 0;
        pendingAnnounced = false;
        finalizePendingDeath(sp);
    }

    // 每次购买车票：胜/死概率各 +2.5%（对应 ×10 单位 = +25），胜上限 80%，死上限 50%
    private void increaseProbabilities() {
        duelCount++;
        winProbability = Math.min(winProbability + 25, 80 * SCALE);
        deathProbability = Math.min(deathProbability + 25, 50 * SCALE);
        recalcRemainingProbabilities();
    }

    // 保证 胜 + 死 + 正 + 负 = 100%（=1000 个 ×10 单位），剩余部分按 35:55 重新分配到正面/负面
    private void recalcRemainingProbabilities() {
        int winDeath = winProbability + deathProbability;
        int total100 = 100 * SCALE;
        if (winDeath > total100) {
            int overflow = winDeath - total100;
            deathProbability -= overflow;
            winDeath = total100;
        }
        int remaining = total100 - winDeath;
        if (remaining <= 0) {
            positiveProbability = 0;
            negativeProbability = 0;
            return;
        }
        positiveProbability = (int) (remaining * (35.0 / 90.0));
        negativeProbability = remaining - positiveProbability;
    }

    /** 把「×10 单位的整数概率格式化成百分比文本：25 → "2.5%"；100 → "10%"；350 → "35%" */
    public static String formatProb10(int probX10) {
        if (probX10 % SCALE == 0) {
            return Integer.toString(probX10 / SCALE) + "%";
        }
        // 保留 1 位小数，不做多余的精度（25→2.5，15→1.5）
        int whole = probX10 / SCALE;
        int frac = probX10 % SCALE;
        return whole + "." + frac + "%";
    }

    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    public void sendMessage(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), false);
    }

    /**
     * 是否处于「新宿决战 30 秒倒计时」未结算阶段（胜或死 pending，还没到 0 结算）。
     * 此期间禁止再次购买新宿车票，防止玩家刷多次对决 / 叠状态。
     */
    public boolean isDuelPending() {
        return pendingDuelResult != PENDING_NONE && pendingDuelTicksLeft > 0;
    }

    @Override
    public void serverTick() {
        if (!isGojoActive()) return;
        if (!(player instanceof ServerPlayer sp)) return;

        // ① 先处理延迟对决的 30 秒倒计时（优先级最高：因为可能提前被击杀要取消播报/结算）
        if (pendingDuelResult != PENDING_NONE && pendingDuelTicksLeft > 0) {

            // 如果玩家中途死亡 / 变为观察者（非存活或非生存模式）：立即取消一切 pending（不播报、不结算）
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                sp.removeEffect(MobEffects.GLOWING);
                pendingDuelResult = PENDING_NONE;
                pendingDuelTicksLeft = 0;
                pendingAnnounced = false;
                sync();
            } else {

                // 先检查是否到达 20 秒播报点（还剩 10s == 200 tick）
                if (!pendingAnnounced && pendingDuelTicksLeft <= ANNOUNCE_AT) {
                    // 注意：播报之前再做一次确认：若此刻刚好玩家死了（观察者）就取消
                    if (GameUtils.isPlayerAliveAndSurvival(sp)) {
                        ServerLevel sl = sp.serverLevel();
                        if (pendingDuelResult == PENDING_WIN) {
                            announcePendingWin(sl);
                        } else if (pendingDuelResult == PENDING_DEATH) {
                            announcePendingDeath(sl);
                        }
                        pendingAnnounced = true;
                    }
                }

                pendingDuelTicksLeft--;

                // 倒计时为 0：正式结算
                if (pendingDuelTicksLeft <= 0) {
                    // 最终结算前最后一次检查存活：如已观察者则取消
                    if (GameUtils.isPlayerAliveAndSurvival(sp)) {
                        int result = pendingDuelResult;
                        // 先清零再执行，避免结算过程中自己再触发 tick 导致死循环
                        pendingDuelResult = PENDING_NONE;
                        pendingDuelTicksLeft = 0;
                        pendingAnnounced = false;
                        if (result == PENDING_WIN) {
                            finalizePendingWin(sp);
                        } else if (result == PENDING_DEATH) {
                            finalizePendingDeath(sp);
                        }
                    } else {
                        sp.removeEffect(MobEffects.GLOWING);
                        pendingDuelResult = PENDING_NONE;
                        pendingDuelTicksLeft = 0;
                        pendingAnnounced = false;
                    }
                    sync();
                }
            }
        }

        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;

        // ② 护盾状态补偿对齐（实际 armor 与组件 shieldActive 同步）
        //    init() / NBT 读档后可能出现「armor 已有 1 层但 shieldActive=false」的不一致，修掉它
        {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
            boolean actualHasShield = (armor.getArmor() >= 1);
            if (actualHasShield && !shieldActive) {
                shieldActive = true;
                sync();
            } else if (!actualHasShield && shieldActive) {
                // 被击破后同步一下
                shieldActive = false;
                sync();
            }
        }

        // ③ 无下限护盾是一次性的：仅在 init() 给一次，被击破后不再自动恢复（上面已处理过）
        if (doomTicksLeft > 0) {
            doomTicksLeft--;
            if (doomTicksLeft <= 0) {
                onDuelDeath(sp);
            }
            if (doomTicksLeft == 3600 || doomTicksLeft == 1800 || doomTicksLeft == 600
                    || doomTicksLeft == 300 || doomTicksLeft == 120 || doomTicksLeft == 60
                    || doomTicksLeft == 20) {
                int secs = doomTicksLeft / 20;
                sendActionBar(sp, "§4§l⚠ 走马灯 ⚠ §f 还有 §c" + secs + "秒 §f你将陨落！（击败宿傩可解除）");
            }
        }

        // ④ 新宿车票购买冷却倒计时（"术式熔断"的 30 秒禁用期）
        if (ticketCooldownTicks > 0) {
            int prevSec = (ticketCooldownTicks + 19) / 20;
            ticketCooldownTicks--;
            int curSec = Math.max(0, (ticketCooldownTicks + 19) / 20);
            if (curSec != prevSec && (curSec == 15 || curSec == 10 || curSec == 5
                    || curSec == 3 || curSec == 1)) {
                sendActionBar(sp, "§c§l术式熔断！§f 新宿车票还剩 §c" + curSec + "秒 §f后恢复购买");
            } else if (ticketCooldownTicks == 0) {
                sendActionBar(sp, "§a✔ 术式熔断解除，新宿车票已可重新购买！");
            }
            sync();
        }

        // ⑤ 新宿车票「5 秒内置购买冷却」倒计时（每次购票成功后自动进入，防连点刷票）
        if (purchaseInternalCdTicks > 0) {
            int prevSec = (purchaseInternalCdTicks + 19) / 20;
            purchaseInternalCdTicks--;
            int curSec = Math.max(0, (purchaseInternalCdTicks + 19) / 20);
            if (curSec != prevSec && (curSec == 4 || curSec == 3 || curSec == 2 || curSec == 1)) {
                sendActionBar(sp, "§7新宿车票内置冷却：还剩 §e" + curSec + "秒");
            }
            sync();
        }
    }

    private boolean isGojoActive() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        return gameWorld.isRole(player, GojoRole.GOJO);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        duelCount = tag.getInt("DuelCount");
        winProbability = tag.getInt("WinProb");
        deathProbability = tag.getInt("DeathProb");
        positiveProbability = tag.getInt("PosProb");
        negativeProbability = tag.getInt("NegProb");
        shieldActive = tag.getBoolean("ShieldActive");
        doomTicksLeft = tag.getInt("DoomTicks");
        pendingDuelResult = tag.getInt("PendingResult");
        pendingDuelTicksLeft = tag.getInt("PendingTicks");
        pendingAnnounced = tag.getBoolean("PendingAnn");
        ticketCooldownTicks = tag.getInt("TicketCd");
        purchaseInternalCdTicks = tag.getInt("PurchaseCd");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("DuelCount", duelCount);
        tag.putInt("WinProb", winProbability);
        tag.putInt("DeathProb", deathProbability);
        tag.putInt("PosProb", positiveProbability);
        tag.putInt("NegProb", negativeProbability);
        tag.putBoolean("ShieldActive", shieldActive);
        tag.putInt("DoomTicks", doomTicksLeft);
        tag.putInt("PendingResult", pendingDuelResult);
        tag.putInt("PendingTicks", pendingDuelTicksLeft);
        tag.putBoolean("PendingAnn", pendingAnnounced);
        tag.putInt("TicketCd", ticketCooldownTicks);
        tag.putInt("PurchaseCd", purchaseInternalCdTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }
}
