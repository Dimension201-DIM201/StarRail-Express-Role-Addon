package com.dim201.dim201addon.item;

import com.dim201.dim201addon.item.card.CardUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameStarted;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 神秘礼盒 - 对局开始 1 分钟后可右键开启
 *
 * <p>开启后随机获得一张能力卡牌（不会开出空白卡牌 BLANK），
 * 价格越高的卡牌概率越低（按价格反比加权）。
 * 另有小概率（5%）获得 U 盘物品（后续会赋予机制，本次仅作为稀有奖品）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>SRP：物品本身只负责"开启/抽奖/发奖"，权重计算独立方法</li>
 *   <li>OCP：奖池和权重表在 {@link #buildRewardPool()} 构建，新增奖品只需扩展此方法</li>
 *   <li>线程安全：权重表构建在每次开启时即时计算（无共享可变状态）</li>
 * </ul>
 */
public class MysteryGiftBoxItem extends Item {

    /** 对局开始后多少 tick 才能开启（1 分钟 = 1200 tick） */
    private static final long UNLOCK_TICKS = 1200L;
    /** U 盘掉落概率（0.05 = 5%） */
    private static final float USB_DRIVE_CHANCE = 0.05F;

    /** 每个维度记录的开局时间（gameTime），对局开始时由 OnGameStarted 写入。
     *  使用 ConcurrentHashMap 保证多线程访问安全（主线程写、网络包读）。 */
    private static final Map<ServerLevel, Long> gameStartTimes = new ConcurrentHashMap<>();

    /** 注册 OnGameStarted 事件（在主类 onInitialize 调用一次） */
    public static void registerGameStartListener() {
        OnGameStarted.EVENT.register(level -> gameStartTimes.put(level, level.getGameTime()));
    }

    /** 对局结束时清理（避免跨局残留） */
    public static void clearGameStartTime(ServerLevel level) {
        gameStartTimes.remove(level);
    }

    public MysteryGiftBoxItem(Properties props) {
        super(props);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }

        // 对局开始时间校验：必须开局 1 分钟后才能开启
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) {
            sp.displayClientMessage(Component.literal("§c对局未进行中，无法开启礼盒！"), true);
            return InteractionResultHolder.fail(stack);
        }
        Long startTime = gameStartTimes.get(sp.level());
        if (startTime == null) {
            // 兜底：未记录开局时间（事件未触发？），用 0 作基准
            startTime = 0L;
        }
        long elapsed = sp.level().getGameTime() - startTime;
        if (elapsed < UNLOCK_TICKS) {
            long remaining = (UNLOCK_TICKS - elapsed + 19) / 20;
            sp.displayClientMessage(
                    Component.literal("§7神秘礼盒还未到开启时间，剩余 " + remaining + " 秒"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 抽奖
        ItemStack reward = drawReward(sp);
        if (reward.isEmpty()) {
            sp.displayClientMessage(Component.literal("§c抽奖失败：奖池为空"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 发放奖品（必须在消耗礼盒之前！）
        // 原因：getItemInHand 返回手持槽的直接引用，若先 consume 再发奖，
        // insertStackInFreeSlot 会把 count=0 的手持槽当作空槽放入奖励，
        // 随后 use 返回的空 stack 又覆盖该槽，导致奖励消失。
        CardUtils.giveItem(sp, reward);

        // 消耗礼盒（此时手持槽仍有礼盒，不会与奖励槽位冲突）
        stack.consume(1, sp);

        // 音效 + 提示（物品名用 translatable 组件，由客户端本地化渲染，避免服务端 getString 返回代码名）
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_LEVELUP, sp.getSoundSource(), 0.7F, 1.4F);
        sp.displayClientMessage(
                Component.literal("§d§l✦ 神秘礼盒 §7· 你获得了 §f")
                        .append(Component.translatable(reward.getDescriptionId())),
                true);

        return InteractionResultHolder.consume(stack);
    }

    /**
     * 抽取一份奖品：
     * <ol>
     *   <li>先掷骰判断是否中 U 盘（5% 概率）</li>
     *   <li>未中 U 盘则从能力卡牌池（不含 BLANK 空白卡）按价格反比加权随机抽取</li>
     * </ol>
     */
    private ItemStack drawReward(ServerPlayer sp) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 第 1 步：U 盘小概率
        if (rng.nextFloat() < USB_DRIVE_CHANCE) {
            return com.dim201.dim201addon.item.Dim201ModItems.USB_DRIVE.getDefaultInstance();
        }

        // 第 2 步：构建卡牌奖池（排除 BLANK 空白卡）
        Map<Item, Integer> pool = buildRewardPool();
        if (pool.isEmpty()) return ItemStack.EMPTY;

        // 价格越高权重越低：weight = 1000 / price（保底 1）
        long totalWeight = 0;
        for (Map.Entry<Item, Integer> e : pool.entrySet()) {
            int price = e.getValue();
            long w = price <= 0 ? 100 : Math.max(1, 1000L / price);
            totalWeight += w;
        }
        long pick = rng.nextLong(totalWeight);
        long acc = 0;
        for (Map.Entry<Item, Integer> e : pool.entrySet()) {
            int price = e.getValue();
            long w = price <= 0 ? 100 : Math.max(1, 1000L / price);
            acc += w;
            if (pick < acc) {
                return e.getKey().getDefaultInstance();
            }
        }
        // 理论不可达，兜底取第一个
        return pool.keySet().iterator().next().getDefaultInstance();
    }

    /**
     * 构建卡牌奖池：
     * <ul>
     *   <li>排除 BLANK（空白卡，天弓千亦专属，不应被开出）</li>
     *   <li>价格从 {@link com.dim201.dim201addon.item.Dim201ModItems#getCardPrice(Item)} 读取</li>
     * </ul>
     */
    private Map<Item, Integer> buildRewardPool() {
        java.util.Map<Item, Integer> pool = new java.util.HashMap<>();
        for (Item card : com.dim201.dim201addon.item.Dim201ModItems.ABILITY_CARDS) {
            if (card == com.dim201.dim201addon.item.Dim201ModItems.BLANK) continue;
            int price = com.dim201.dim201addon.item.Dim201ModItems.getCardPrice(card);
            pool.put(card, price);
        }
        return pool;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                 @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("§7右键开启：随机获得一张能力卡牌").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7对局开始 1 分钟后可用").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7价格越高的卡牌概率越低").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§b有小概率获得神秘物品 §fU盘").withStyle(ChatFormatting.GRAY));
    }
}
