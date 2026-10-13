package com.dim201.dim201addon;

import com.dim201.dim201addon.role.DevourerRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 吞噬者玩家组件
 *
 * 管理吞噬状态、冷却计时和金币扣除。
 * 吞噬机制复用 SRE 鹈鹕的 PelicanManager（stashPlayer / releasePlayer）。
 *
 * 冷却规则：吐出后才开始 120 秒冷却，吞噬不触发冷却。
 *
 * 杀手反杀机制：
 * - 如果被吞噬玩家是杀手阵营，进入肚内 30 秒后可按技能键开膛破肚反杀吞噬者
 * - 进入肚内时移除 SKILL_BANED（保留 CHAT_BAN/USED_BANED），确保杀手能放技能
 * - 如果本局只剩 1 个杀手，吞噬对杀手目标不生效（保护最后一名杀手不被直接吞掉导致杀手阵营无法胜利）
 */
public class DevourerPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<DevourerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "devourer"),
            DevourerPlayerComponent.class);

    /** 开膛破肚（杀手反杀吞噬者）的死因 ID */
    public static final ResourceLocation DEATH_REASON_DISEMBOWEL =
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "disembowel");

    /** 杀手肚内可反杀的等待时间（tick）= 30 秒 × 20 */
    public static final int KILLER_ESCAPE_TICKS = 30 * 20;

    /** 吞噬最大持续时间（tick）= 40 秒 × 20，超时自动吐出 */
    public static final int MAX_SWALLOW_TICKS = 40 * 20;

    private final Player player;

    /** 当前被吞噬的玩家 UUID（null 表示肚内无人） */
    public UUID swallowedUUID = null;
    public String swallowedName = "";
    /** 吞噬发生的时间戳（server level getGameTime()，tick），用于计算杀手 30 秒反杀窗口 */
    public long swallowedAtTick = 0L;
    /** 冷却剩余 tick（0 = 可用） */
    public int cooldownTicks = 0;

    public DevourerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        swallowedUUID = null;
        swallowedName = "";
        swallowedAtTick = 0L;
        cooldownTicks = 0;
        sync();
    }

    @Override
    public void clear() {
        // 角色被移除/对局重置时，释放肚内玩家
        releaseSwallowedSilent();
        swallowedUUID = null;
        swallowedName = "";
        swallowedAtTick = 0L;
        cooldownTicks = 0;
        sync();
    }

    /** 静默释放（不提示、不触发冷却），用于死亡/角色移除/对局重置 */
    private void releaseSwallowedSilent() {
        if (swallowedUUID == null) return;
        if (!(player instanceof ServerPlayer sp)) return;
        ServerPlayer target = sp.getServer().getPlayerList().getPlayer(swallowedUUID);
        if (target != null && PelicanManager.isStashed(target)) {
            PelicanManager.releasePlayer(target);
        }
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, DevourerRole.DEVOURER_ROLE)) return;
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) return;

        // 冷却递减
        int prev = cooldownTicks;
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        // 冷却变化时每秒同步一次
        if (prev != cooldownTicks && cooldownTicks % 20 == 0) {
            sync();
        }

        // 检查被吞噬玩家是否仍被 PelicanManager 跟踪
        if (swallowedUUID != null && !PelicanManager.isStashed(swallowedUUID)) {
            // 被吞噬玩家已因其他原因释放（如鹈鹕死亡逻辑、对局结束等）
            swallowedUUID = null;
            swallowedName = "";
            swallowedAtTick = 0L;
            sync();
        }

        // 吞噬超时：超过 40 秒自动吐出（与手动吐出相同，触发 120 秒冷却）
        if (swallowedUUID != null) {
            long elapsed = sp.level().getGameTime() - swallowedAtTick;
            if (elapsed >= MAX_SWALLOW_TICKS) {
                sp.displayClientMessage(
                        Component.literal("§c吞噬时间到，自动吐出 " + swallowedName)
                                .withStyle(ChatFormatting.RED),
                        true);
                releaseSwallowed();
            }
        }
    }

    /** 判定目标角色是否为杀手阵营（canUseKiller==true） */
    private static boolean isKillerRole(SREGameWorldComponent gw, Player p) {
        if (gw == null || p == null) return false;
        SRERole role = gw.getRole(p);
        return role != null && role.canUseKiller();
    }

    /**
     * 尝试吞噬目标玩家
     * @return true 表示吞噬成功
     */
    public boolean tryEat(ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (target == null) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(target)) return false;
        if (target.getUUID().equals(player.getUUID())) return false;

        // 已有吞噬目标 → 不允许再次吞噬
        if (swallowedUUID != null) {
            sp.displayClientMessage(
                    Component.literal("§c已吞噬玩家，请先吐出！"),
                    true);
            return false;
        }

        // 冷却检查
        if (cooldownTicks > 0) {
            long remaining = cooldownTicks / 20 + 1;
            sp.displayClientMessage(
                    Component.literal("§c技能冷却中，剩余 " + remaining + " 秒"),
                    true);
            return false;
        }

        // 金币检查
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < DevourerRole.EAT_COST) {
            sp.displayClientMessage(
                    Component.literal("§c金币不足，需要 " + DevourerRole.EAT_COST + " 金币"),
                    true);
            return false;
        }

        // 不能吞噬已被鹈鹕/吞噬者吞下的玩家
        if (PelicanManager.isStashed(target)) return false;

        // 判定目标是否为杀手阵营（用于吞噬后移除 SKILL_BANED 让其可反杀）
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        boolean targetIsKiller = isKillerRole(gw, target);

        // 执行吞噬：复用 SRE 鹈鹕的 stashPlayer 机制
        PelicanManager.stashPlayer(sp, target);

        // ====== 新增：如果被吞玩家是杀手，移除 SKILL_BANED 效果 ======
        // PelicanManager.stashPlayer 默认给被吞玩家加 3 个效果：SKILL_BANED / CHAT_BAN / USED_BANED
        // 移除 SKILL_BANED 后杀手可以按技能键触发 30 秒后的开膛破肚反杀；
        // 保留 CHAT_BAN / USED_BANED 防止杀手在肚内聊天或使用物品。
        if (targetIsKiller) {
            target.removeEffect(ModEffects.SKILL_BANED);
            // 肚内剩余时间提示：客户端可以通过同步数据显示倒计时
            target.displayClientMessage(
                    Component.literal("§4你被吞噬了！30 秒后按技能键可以开膛破肚反杀吞噬者")
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
        }

        // 扣除金币
        shop.addToBalance(-DevourerRole.EAT_COST);

        // 记录吞噬状态
        swallowedUUID = target.getUUID();
        swallowedName = target.getName().getString();
        swallowedAtTick = sp.level().getGameTime();   // 记录吞噬发生的 tick，用于 30 秒反杀窗口

        // 音效 + 提示
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.MASTER, 0.9F, 0.65F);

        sp.displayClientMessage(
                Component.literal("§a已吞噬 " + swallowedName + "！再次按技能键吐出")
                        .withStyle(ChatFormatting.GOLD),
                true);

        sync();
        return true;
    }

    /**
     * 吐出已吞噬的玩家，吐出后开始 120 秒冷却
     * @return true 表示吐出成功
     */
    public boolean releaseSwallowed() {
        if (!(player instanceof ServerPlayer sp)) return false;
        if (swallowedUUID == null) {
            sp.displayClientMessage(
                    Component.literal("§c肚内没有玩家"),
                    true);
            return false;
        }

        ServerPlayer target = sp.getServer().getPlayerList().getPlayer(swallowedUUID);
        if (target != null) {
            PelicanManager.releasePlayer(target);
        }

        sp.displayClientMessage(
                Component.literal("§a已吐出 " + swallowedName + "，冷却 120 秒")
                        .withStyle(ChatFormatting.GREEN),
                true);

        // 清除吞噬状态，开始冷却
        swallowedUUID = null;
        swallowedName = "";
        swallowedAtTick = 0L;
        cooldownTicks = DevourerRole.COOLDOWN_TICKS;

        sync();
        return true;
    }

    /**
     * 杀手在肚内按技能键反杀吞噬者（开膛破肚）
     * 由服务端收到 DevourerKillerEscapeC2SPacket 后调用。
     *
     * @param killer 调用者（被吞噬的杀手玩家，服务端已校验调用者确实被某个吞噬者吞掉）
     * @return true 表示成功杀死吞噬者并释放杀手
     */
    public static boolean killerTryEscape(ServerPlayer killer) {
        if (killer == null) return false;
        if (!PelicanManager.isStashed(killer)) return false;

        UUID killerId = killer.getUUID();
        UUID pelicanId = PelicanManager.getPelicanForStashed(killerId);
        if (pelicanId == null) return false;

        ServerPlayer devourer = killer.getServer().getPlayerList().getPlayer(pelicanId);
        if (devourer == null) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(devourer)) return false;

        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(killer.level());
        if (gw == null) return false;
        // 只处理吞噬者（PELICAN 走原生逻辑）
        if (!gw.isRole(devourer, DevourerRole.DEVOURER_ROLE)) return false;

        DevourerPlayerComponent comp = ModComponents.DEVOURER.get(devourer);
        if (comp == null) return false;
        if (!killerId.equals(comp.swallowedUUID)) return false;

        // 反杀前时间检查：必须在肚内待够 30 秒
        long elapsed = devourer.level().getGameTime() - comp.swallowedAtTick;
        if (elapsed < KILLER_ESCAPE_TICKS) {
            long remain = (KILLER_ESCAPE_TICKS - elapsed + 19) / 20;
            killer.displayClientMessage(
                    Component.literal("§c反杀冷却中，还需 " + remain + " 秒才能开膛破肚").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        // ===== 1. 先释放杀手（避免杀吞噬者时 PelicanManager 连带强制释放搞混）=====
        PelicanManager.releasePlayer(killer);

        // ===== 2. 杀手杀死吞噬者（死因 dim201addon:disembowel 开膛破肚）=====
        // spawnBody=true 会正常生成尸体，killer=杀手自己，会被 SRE 统计为击杀
        GameUtils.killPlayer(devourer, true, killer, DEATH_REASON_DISEMBOWEL);

        // 清除吞噬者组件状态（避免状态残留）
        comp.swallowedUUID = null;
        comp.swallowedName = "";
        comp.swallowedAtTick = 0L;
        comp.cooldownTicks = DevourerRole.COOLDOWN_TICKS;  // 虽然死了，但保险起见给个冷却
        comp.sync();

        // 给杀手发个提示
        killer.displayClientMessage(
                Component.literal("§a开膛破肚！你亲手杀死了吞噬者 ").append(devourer.getName().copy().withStyle(ChatFormatting.YELLOW))
                        .append("§a，成功逃脱！")
                        .withStyle(ChatFormatting.GREEN),
                true);

        return true;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (swallowedUUID != null) {
            tag.putUUID("SwallowedUUID", swallowedUUID);
            tag.putString("SwallowedName", swallowedName);
            tag.putLong("SwallowedAtTick", swallowedAtTick);
        }
        tag.putInt("CooldownTicks", cooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        swallowedUUID = tag.contains("SwallowedUUID") ? tag.getUUID("SwallowedUUID") : null;
        swallowedName = tag.contains("SwallowedName") ? tag.getString("SwallowedName") : "";
        swallowedAtTick = tag.contains("SwallowedAtTick") ? tag.getLong("SwallowedAtTick") : 0L;
        cooldownTicks = tag.getInt("CooldownTicks");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
