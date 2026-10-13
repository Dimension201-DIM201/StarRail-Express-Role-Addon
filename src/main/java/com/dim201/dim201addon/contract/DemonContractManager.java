package com.dim201.dim201addon.contract;

import com.dim201.dim201addon.DemonContractorComponent;
import com.dim201.dim201addon.menu.DemonContractMenu;
import com.dim201.dim201addon.menu.ModMenus;
import com.dim201.dim201addon.packet.DemonContractResultS2CPacket;
import com.dim201.dim201addon.role.DemonContractorRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 恶魔契约核心管理器
 *
 * <p>职责：尸体引导（3 秒、可被打断）、契约 UI 会话、掷骰与代价抽取、
 * 力量/代价结算、阈值死亡、不灭复活、悬赏金币、跨局清理。
 *
 * <p>所有可变状态使用并发集合，玩家重登/换局可安全清理。
 */
public final class DemonContractManager {

    private DemonContractManager() {}

    /** 契约死亡原因（无击杀者，走 SRE killPlayer 管线） */
    public static final ResourceLocation DEMON_CONTRACT_REASON =
            ResourceLocation.fromNamespaceAndPath("dim201addon", "demon_contract");

    /** 每具尸体只能契约一次；死亡超过 2 分钟的尸体不可契约；每场最多 3 次 */
    private static final long BODY_FRESH_TICKS = 2 * 60 * 20L;
    private static final int MAX_CONTRACTS = 3;
    /** 引导时长（tick）与允许位移（格） */
    private static final long CHANNEL_TICKS = 3 * 20L;
    /** 冲刺水平位移距离（格） */
    private static final double DASH_DISTANCE = 5.0;
    private static final double CHANNEL_MOVE_LIMIT = 2.5D;
    /** 揭晓后到处决的延迟（让玩家看完代价） */
    private static final long DEATH_DELAY_TICKS = 60L;

    /** 引导状态 */
    private record Channel(UUID bodyUuid, double x, double y, double z, long deadline) {}

    /** 恶魔报价：本局对玩家固定的力量抽取结果 */
    private record Offer(List<ContractPower> powers) {}

    private static final Map<UUID, Channel> CHANNELING = new ConcurrentHashMap<>();
    /** 已打开契约 UI 的玩家 → 尸体 UUID */
    private static final Map<UUID, UUID> SESSIONS = new ConcurrentHashMap<>();
    /** UI 中勾选的力量（服务端权威） */
    private static final Map<UUID, EnumSet<ContractPower>> SELECTED = new ConcurrentHashMap<>();
    /** 恶魔报价：玩家 → 力量抽取结果。本局对玩家固定，不随尸体变化，签订契约后才重新抽取 */
    private static final Map<UUID, Offer> OFFERS = new ConcurrentHashMap<>();
    /** 已被契约消耗的尸体 UUID */
    private static final Set<UUID> USED_BODIES = ConcurrentHashMap.newKeySet();
    /** 死于恶魔契约的玩家 UUID（其尸体不可再被契约） */
    private static final Set<UUID> DEMON_DEAD = ConcurrentHashMap.newKeySet();
    /** 已判定死亡、等待处决的玩家 UUID → 处决时刻 */
    private static final Map<UUID, Long> PENDING_DEATH = new ConcurrentHashMap<>();

    private static boolean registered = false;

    // ==================== 事件注册 ====================

    public static void register() {
        if (registered) return;
        registered = true;

        // 悬赏：击杀被烙印赏金的玩家额外 +100 金币（事件返回值会被累加）
        OnGiveKillerBalance.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof Player p)) return 0;
            DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(p);
            return (comp != null && comp.hasBrand(ContractCost.BOUNTY)) ? 100 : 0;
        });

        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) ->
                tryImmortalRevive(player, deathReason));
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) ->
                tryImmortalRevive(player, deathReason));

        // 对局结束清理（正常结束路径）
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register((level, gameWorld) -> clearGame(level));
        // 对局开始也清理一次：中途退出的玩家在 OnGameEnd 时已不在线，其 CCA 组件
        // 随 playerdata 落盘无法被遍历到；新局开局时所有参与者均在线，此处完成兜底重置
        io.wifi.starrailexpress.event.OnGameStarted.EVENT.register(DemonContractManager::clearGame);
        // 登录兜底：无对局运行时（赛后/大厅回归）清除可能落盘残留的组件与属性修饰符
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer sp = handler.getPlayer();
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.getNullable(sp.serverLevel());
            if (gw != null && gw.isRunning()) return;
            DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(sp);
            if (comp != null) {
                comp.clear();
                DemonContractorComponent.KEY.sync(sp);
            }
            ContractEffects.removePermanent(sp);
        });
    }

    // ==================== 引导 ====================

    /** 右键尸体开始契约引导（校验契约次数/尸体状态后进入 3 秒仪式） */
    public static void beginChanneling(ServerPlayer sp, PlayerBodyEntity body) {
        DemonContractorComponent comp = DemonContractorComponent.KEY.get(sp);
        if (comp.getContractCount() >= MAX_CONTRACTS) {
            actionbar(sp, "§8§o恶魔低语：§7你与我的契约已经足够多了");
            return;
        }
        if (USED_BODIES.contains(body.getUUID())) {
            actionbar(sp, "§c这具尸体的灵魂已经被取走了");
            return;
        }
        if (body.tickCount > BODY_FRESH_TICKS) {
            actionbar(sp, "§8§o尸体的灵魂早已散去，恶魔对此毫无兴趣");
            return;
        }
        UUID deadId = body.getPlayerUuid();
        if (deadId != null && DEMON_DEAD.contains(deadId)) {
            actionbar(sp, "§8§o这具灵魂已属于恶魔，无法再次交易");
            return;
        }
        long now = sp.level().getGameTime();
        CHANNELING.put(sp.getUUID(), new Channel(body.getUUID(),
                sp.getX(), sp.getY(), sp.getZ(), now + CHANNEL_TICKS));
        actionbar(sp, "§8§o正在与恶魔交涉…保持不动 3 秒");
    }

    /** 角色每 tick 调用：引导判定 + 处决 + 效果维持 */
    public static void handleTick(ServerPlayer sp) {
        long now = sp.level().getGameTime();

        // 引导
        Channel ch = CHANNELING.get(sp.getUUID());
        if (ch != null) {
            boolean interrupted = sp.hurtTime > 0
                    || sp.distanceToSqr(ch.x(), ch.y(), ch.z()) > CHANNEL_MOVE_LIMIT * CHANNEL_MOVE_LIMIT
                    || findBody(sp.serverLevel(), ch.bodyUuid()) == null;
            if (interrupted) {
                CHANNELING.remove(sp.getUUID());
                actionbar(sp, "§c契约仪式被打断了");
            } else if (now >= ch.deadline()) {
                CHANNELING.remove(sp.getUUID());
                openContractMenu(sp, ch.bodyUuid());
            }
        }

        // 处决
        Long deathAt = PENDING_DEATH.get(sp.getUUID());
        if (deathAt != null && now >= deathAt) {
            PENDING_DEATH.remove(sp.getUUID());
            GameUtils.killPlayer(sp, true, null, DEMON_CONTRACT_REASON);
            return;
        }

        // 力量/烙印持续效果
        DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(sp);
        if (comp != null && GameUtils.isPlayerAliveAndSurvival(sp)) {
            ContractEffects.maintainTick(sp, comp, now);
        }
    }

    /** 技能键：释放冲刺（拥有冲刺力量时；冷却中仅提示） */
    public static void useDash(ServerPlayer sp) {
        DemonContractorComponent comp = DemonContractorComponent.KEY.get(sp);
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;
        tryDash(sp, comp);
    }

    private static void tryDash(ServerPlayer sp, DemonContractorComponent comp) {
        if (!comp.hasPower(ContractPower.DASH)) return;
        long now = sp.level().getGameTime();
        if (now < comp.getDashReadyAt()) {
            actionbar(sp, "§7冲刺冷却中（§f"
                    + Math.max(0, (comp.getDashReadyAt() - now + 19) / 20) + " §7秒）");
            return;
        }
        ServerLevel level = sp.serverLevel();
        Vec3 look = sp.getLookAngle();
        // 水平投影归一化：冲刺距离恒定，不受俯仰角影响
        Vec3 horizontalLook = new Vec3(look.x, 0, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6) return; // 纯垂直视角不触发
        horizontalLook = horizontalLook.normalize();

        Vec3 start = sp.position();
        Vec3 target = start.add(horizontalLook.scale(DASH_DISTANCE));
        // 物理推进：setDeltaMovement + hurtMarked 让服务端真正接受位移
        Vec3 dash = horizontalLook.scale(DASH_DISTANCE * 0.28);
        sp.setDeltaMovement(sp.getDeltaMovement().add(dash.x, 0.08, dash.z));
        sp.hurtMarked = true;
        // 客户端 4 tick 平滑插值到目标位置
        sp.lerpTo(target.x, sp.getY(), target.z, sp.getYRot(), sp.getXRot(), 4);

        // 冲刺路径粒子（恶魔风格：黑烟）
        for (double d = 0; d <= DASH_DISTANCE; d += 0.4) {
            Vec3 p = start.add(horizontalLook.scale(d));
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.0, p.z,
                    2, 0.1, 0.1, 0.1, 0.01);
        }
        sp.fallDistance = 0;

        comp.setDashReadyAt(now + 60 * 20L);
        DemonContractorComponent.KEY.sync(sp);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_PEARL_THROW,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.6F);
        actionbar(sp, "§5§l恶魔冲刺！");
    }

    // ==================== 契约 UI 会话 ====================

    private static void openContractMenu(ServerPlayer sp, UUID bodyUuid) {
        DemonContractorComponent comp = DemonContractorComponent.KEY.get(sp);
        // 报价与玩家绑定：本局抽取一次后固定，无论对哪具尸体打开界面都是同一份列表（防刷池），
        // 仅在签订契约（消耗次数）后重新抽取——此时已拥有力量会从池中排除
        Offer offer = OFFERS.get(sp.getUUID());
        if (offer == null) {
            offer = new Offer(drawOfferedPowers(comp, sp.serverLevel().getRandom()));
            OFFERS.put(sp.getUUID(), offer);
        }
        List<ContractPower> offered = offer.powers();
        SESSIONS.put(sp.getUUID(), bodyUuid);
        SELECTED.put(sp.getUUID(), EnumSet.noneOf(ContractPower.class));
        sp.openMenu(new ExtendedScreenHandlerFactory<ModMenus.ContractData>() {
            @Override
            public Component getDisplayName() {
                return Component.literal("§8§l恶魔契约");
            }
            @Override
            public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new DemonContractMenu(syncId, inv, offered);
            }
            @Override
            public ModMenus.ContractData getScreenOpeningData(ServerPlayer player) {
                return new ModMenus.ContractData(
                        offered.stream().map(ContractPower::id).toList());
            }
        });
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** 恶魔从全部力量中随机抽取至多 4 项供本次契约选择（排除已拥有的力量） */
    private static List<ContractPower> drawOfferedPowers(DemonContractorComponent comp,
                                                         net.minecraft.util.RandomSource rng) {
        List<ContractPower> pool = new ArrayList<>(List.of(ContractPower.values()));
        pool.removeIf(comp::hasPower);
        shuffle(pool, rng);
        return pool.subList(0, Math.min(DemonContractMenu.MAX_PICKS, pool.size()));
    }

    /** 菜单关闭（玩家点掉界面 = 放弃本次契约，尸体不消耗） */
    public static void onMenuClosed(ServerPlayer sp) {
        SESSIONS.remove(sp.getUUID());
        SELECTED.remove(sp.getUUID());
    }

    /** 切换力量勾选（服务端权威校验） */
    public static void togglePower(ServerPlayer sp, ContractPower power) {
        if (!SESSIONS.containsKey(sp.getUUID())) return;
        DemonContractorComponent comp = DemonContractorComponent.KEY.get(sp);
        if (comp.hasPower(power)) return; // 已拥有的不可重复索取
        EnumSet<ContractPower> set = SELECTED.computeIfAbsent(sp.getUUID(),
                k -> EnumSet.noneOf(ContractPower.class));
        if (set.contains(power)) {
            set.remove(power);
        } else if (set.size() < DemonContractMenu.MAX_PICKS) {
            set.add(power);
        }
    }

    /** 签订契约：掷骰 → 抽代价 → 结算 */
    public static void submitContract(ServerPlayer sp) {
        UUID bodyUuid = SESSIONS.get(sp.getUUID());
        EnumSet<ContractPower> picked = SELECTED.get(sp.getUUID());
        // 会话丢失/空选：给出提示而非静默失败（客户端勾选状态本应已同步至此）
        if (bodyUuid == null || picked == null || picked.isEmpty()) {
            actionbar(sp, "§c契约需要至少索取一项力量");
            return;
        }

        ServerLevel level = sp.serverLevel();
        PlayerBodyEntity body = findBody(level, bodyUuid);
        // 打开 UI 期间尸体可能消失/超时，二次校验防作弊
        if (body == null || USED_BODIES.contains(bodyUuid) || body.tickCount > BODY_FRESH_TICKS) {
            actionbar(sp, "§c灵魂已散，契约未能成立");
            sp.closeContainer();
            return;
        }

        DemonContractorComponent comp = DemonContractorComponent.KEY.get(sp);
        long now = level.getGameTime();

        // 计算总代价：Σ隐藏价值 × 贪婪倍率 × 血誓 × 随机浮动
        int totalValue = picked.stream().mapToInt(ContractPower::value).sum();
        float greed = ContractEffects.GREED_MULTIPLIERS[Math.min(picked.size(), 4) - 1];
        if (comp.isBloodOathArmed()) {
            greed *= 1.5F;
            comp.consumeBloodOath();
        }
        float roll = 0.8F + level.getRandom().nextFloat() * 0.4F;
        int targetPoints = Math.round(totalValue * greed * roll);

        List<ContractCost> drawn = drawCosts(comp, targetPoints, level);

        List<String> powerLines = new ArrayList<>();
        for (ContractPower p : picked) powerLines.add(p.displayName());
        List<String> costLines = new ArrayList<>();
        for (ContractCost c : drawn) costLines.add(c.displayName());

        boolean dead = targetPoints >= ContractEffects.DEATH_THRESHOLD;
        List<String> whisperLines = List.of();

        if (dead) {
            // 付不起代价：什么都得不到，3 秒后处决（尸体仍消耗：灵魂已押给恶魔）
            DEMON_DEAD.add(sp.getUUID());
            PENDING_DEATH.put(sp.getUUID(), now + DEATH_DELAY_TICKS);
            actionbar(sp, "§4§l恶魔在对你微笑…");
        } else {
            // 结算力量
            for (ContractPower p : picked) {
                comp.addPower(p);
                ContractEffects.applyPowerInstant(sp, p);
            }
            // 结算代价
            for (ContractCost c : drawn) {
                if (c.kind() == ContractCost.Kind.PERMANENT) {
                    comp.addBrand(c);
                    ContractEffects.applyBrandInstant(sp, comp, c, now);
                } else {
                    ContractEffects.applyInstantCost(sp, c, now);
                }
            }
            // 死者低语：读取击杀者身份 + 名字一个字（并入揭晓页展示，对局中聊天栏不可见）
            if (picked.contains(ContractPower.WHISPER)) {
                whisperLines = revealWhisper(sp, body);
            }
            // 恶魔悬赏：向所有杀手广播目标与赏金
            if (drawn.contains(ContractCost.BOUNTY)) {
                broadcastBounty(level, sp);
            }
            comp.incrementContractCount();
            // 力量/烙印/次数变更，同步给客户端（HUD、直觉、契约界面依赖）
            DemonContractorComponent.KEY.sync(sp);
        }

        // 尸体消耗：黑烟后消失；报价随之作废（下一具尸体重新抽取）
        consumeBody(level, body);
        SESSIONS.remove(sp.getUUID());
        SELECTED.remove(sp.getUUID());
        OFFERS.remove(sp.getUUID());

        ServerPlayNetworking.send(sp, new DemonContractResultS2CPacket(
                powerLines, costLines, whisperLines, targetPoints, dead));
    }

    // ==================== 掷骰抽取代价 ====================

    /** 基于世界随机源的 Fisher-Yates 洗牌（RandomSource 不兼容 Collections.shuffle） */
    private static <T> void shuffle(List<T> list, net.minecraft.util.RandomSource rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /**
     * 凑点抽取：优先永久烙印，同组/已有不重复，即时池补足；
     * 实际抽到的总点数 ≤ target（玩家唯一的仁慈）。
     */
    private static List<ContractCost> drawCosts(DemonContractorComponent comp, int target,
                                                ServerLevel level) {
        List<ContractCost> result = new ArrayList<>();
        Set<String> usedGroups = new HashSet<>();
        int budget = target;

        List<ContractCost> permanentPool = new ArrayList<>(ContractCost.permanents());
        List<ContractCost> instantPool = new ArrayList<>(ContractCost.instants());
        shuffle(permanentPool, level.getRandom());
        shuffle(instantPool, level.getRandom());

        // 先抽永久烙印
        for (ContractCost c : permanentPool) {
            if (budget < 1) break;
            if (comp.hasBrand(c) || usedGroups.contains(c.group())) continue;
            if (c.points() <= budget) {
                result.add(c);
                usedGroups.add(c.group());
                budget -= c.points();
                if (c == ContractCost.BLOOD_OATH) comp.armBloodOath();
            }
        }
        // 剩余用即时代价补足
        for (ContractCost c : instantPool) {
            if (budget < 1) break;
            if (usedGroups.contains(c.group())) continue;
            if (c.points() <= budget) {
                result.add(c);
                usedGroups.add(c.group());
                budget -= c.points();
            }
        }
        return result;
    }

    // ==================== 死者低语 ====================

    /** 恶魔悬赏：向本局所有杀手广播目标头颅赏金 */
    private static void broadcastBounty(ServerLevel level, ServerPlayer target) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
        if (gw == null) return;
        Component msg = Component.literal(
                "§6§l恶魔悬赏 §8| §e" + target.getName().getString()
                        + " §7的头颅现在价值 §6100 金币");
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer killer && gw.isKillerTeam(killer)) {
                killer.sendSystemMessage(msg);
            }
        }
    }

    /** 死者低语：收集击杀者身份与名字碎片线索（展示在契约揭晓页，不使用聊天栏） */
    private static List<String> revealWhisper(ServerPlayer sp, PlayerBodyEntity body) {
        List<String> lines = new ArrayList<>();
        UUID killerUuid = body.getKillerUuid();
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (killerUuid == null || gw == null) {
            lines.add("§8§o这具尸体没有怨念，你什么也没读到");
            return lines;
        }
        var role = gw.getRole(killerUuid);
        Player killer = sp.level().getPlayerByUUID(killerUuid);
        if (role != null) {
            lines.add("§7凶手的身份是 §c" + role.getName().getString());
        }
        if (killer != null) {
            String fragment = pickNameFragment(killer.getName().getString(), sp.serverLevel());
            if (fragment != null && !fragment.isEmpty()) {
                lines.add("§7凶手的名字中带有 §e『" + fragment + "』§7 字");
            }
        }
        return lines;
    }

    /** 从玩家名随机取一个字（参考大侦探 GreatDetectiveRole.pickNameFragment） */
    private static String pickNameFragment(String name, ServerLevel level) {
        if (name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;
        if (name.length() <= 1) return name;
        int start = level.getRandom().nextInt(name.length());
        return name.substring(start, start + 1);
    }

    // ==================== 不灭复活 ====================

    private static boolean tryImmortalRevive(Player player, ResourceLocation deathReason) {
        if (!(player instanceof ServerPlayer sp)) return true;
        if (DEMON_CONTRACT_REASON.equals(deathReason)) return true; // 契约之死不可复活
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) return true;
        if (!gw.isRole(sp, DemonContractorRole.DEMON_CONTRACTOR)) return true;
        DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(sp);
        if (comp == null || !comp.hasPower(ContractPower.IMMORTAL)) return true;

        // 原地满血复活，消耗一次性力量
        comp.getPowers().remove(ContractPower.IMMORTAL);
        DemonContractorComponent.KEY.sync(sp);
        sp.setHealth(sp.getMaxHealth());
        sp.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        sp.sendSystemMessage(Component.literal("§6§l不灭！§7恶魔将你从死亡线上拖了回来"));
        return false;
    }

    // ==================== 工具 ====================

    private static void consumeBody(ServerLevel level, PlayerBodyEntity body) {
        USED_BODIES.add(body.getUUID());
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                body.getX(), body.getY() + 0.5, body.getZ(), 20, 0.3, 0.3, 0.3, 0.01);
        body.discard();
    }

    private static PlayerBodyEntity findBody(ServerLevel level, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        return (entity instanceof PlayerBodyEntity body) ? body : null;
    }

    private static void actionbar(ServerPlayer sp, String msg) {
        sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(msg)));
    }

    /** 对局结束清理：移除属性/发光，清空全部静态状态 */
    public static void clearGame(ServerLevel level) {
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp) {
                // 清空持久组件状态（力量/烙印/契约次数等），并同步给客户端
                DemonContractorComponent comp = DemonContractorComponent.KEY.getNullable(sp);
                if (comp != null) {
                    comp.clear();
                    DemonContractorComponent.KEY.sync(sp);
                }
                ContractEffects.removePermanent(sp);
                CHANNELING.remove(sp.getUUID());
                PENDING_DEATH.remove(sp.getUUID());
                SESSIONS.remove(sp.getUUID());
                SELECTED.remove(sp.getUUID());
                OFFERS.remove(sp.getUUID());
            }
        }
        USED_BODIES.clear();
        DEMON_DEAD.clear();
        OFFERS.clear();
    }
}
