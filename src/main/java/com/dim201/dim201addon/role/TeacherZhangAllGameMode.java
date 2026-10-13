package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.LegionPlayerComponent;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.TeacherZhangPlayerComponent;
import com.dim201.dim201addon.TeacherZhangRole;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.Noellesroles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全员张老师模式
 *
 * 与亡命徒模式（WTLooseEndsGameMode）并列的独立游戏模式：
 *  - 所有玩家分配张老师角色
 *  - 目标：累计走动 4000 格获胜（张老师原生机制）；或者最后一人存活获胜
 *  - 关闭情绪系统（hasMood=false），与亡命徒一致避免复杂心理
 *  - 不记录玩家统计（shouldRecordPlayerStats=false）：娱乐模式不影响排位
 *
 * 注册方式：在 Dim201Addon.onInitialize() 调用 SREGameModes.registerGameMode()
 * 把实例塞入 SREGameModes.GAME_MODES 静态 HashMap，SRE 的 MapVotingManager 和
 * StartCommand 会从同一个 Map 自动枚举，因此无需额外 Mixin 即可出现在投票/命令里。
 */
public class TeacherZhangAllGameMode extends GameMode {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "teacher_zhang_all");

    /** 与张老师角色保持一致的获胜距离，单位：格 */
    public static final double WIN_DISTANCE = TeacherZhangRole.WIN_DISTANCE;

    /** 每 30 秒（600 tick）给所有存活玩家发放一个风弹，提升娱乐性 */
    public static final int WIND_BOMB_INTERVAL_TICKS = 30 * 20;
    /** 风弹发放计数器：到达 WIND_BOMB_INTERVAL_TICKS 触发一次并归零 */
    private int curWindBombTick = 0;

    /** 对局开始 60 秒后给所有存活玩家发放开锁器+撬棍 */
    public static final int TOOLS_DROP_DELAY_TICKS = 60 * 20;
    /** 开锁器+撬棍发放计时器 */
    private int curToolDropTick = 0;
    /** 是否已发放过开锁器+撬棍（每局限发一次） */
    private boolean toolsDropped = false;

    /** 玩家起始位置（UUID -> [x, y, z]），用于死亡后原地复活 */
    public final Map<UUID, double[]> startPositions = new ConcurrentHashMap<>();

    /** 事件是否已注册 */
    private static boolean eventsRegistered = false;

    public TeacherZhangAllGameMode() {
        super(ID,
                10,     // 默认 10 分钟
                2);     // 最少 2 人可开
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public boolean isLooseEndMode() {
        return false;   // 返回 false 让客户端走标准角色渲染分支，使用张老师的欢迎/结算文本
    }

    @Override
    public boolean onlyOneWinner() {
        return true;    // 仍然只有一个获胜者，保证 looseEndWinner 正常持久化到 NBT
    }

    /** 全员张老师模式不开会议（娱乐模式） */
    @Override
    public boolean canHaveMeeting() {
        return false;
    }

    // 注：isPlayerWinning 是 SRE 4.4.0 新增的抽象方法，当前 JAR (4.3.0) 暂无。
    // SRE 更新后取消 @Override 注释即可。
    public boolean isPlayerWinning(ServerLevel world, ServerPlayer player, SRERole playerRole,
            SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent) {
        UUID winner = gameComponent.getLooseEndWinner();
        return winner != null && winner.equals(player.getUUID());
    }

    @Override
    public boolean hasMood() {
        return true;    // 启用情绪系统：让 HudMoodRenderer 渲染左上角角色标志（中立图标），任务刷新也依赖此
    }

    /** 所有玩家设置为张老师角色 */
    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gw) {
        for (ServerPlayer p : players) {
            gw.addRole(p, TeacherZhangRole.TEACHER_ZHANG);
        }
    }

    /** 发送欢迎包（告诉客户端本次模式/角色） */
    protected void sendWelcomePackets(List<ServerPlayer> players, SRERole role) {
        if (role == null) return;
        for (ServerPlayer p : players) {
            ServerPlayNetworking.send(p,
                    new AnnounceWelcomePayload(role.identifier().toString(), 0, 0));
        }
    }

    /** 触发张老师角色初始化事件（CCA 组件 init、技能冷却初始化等） */
    protected void assignModdedRole(List<ServerPlayer> players, SREGameWorldComponent gw) {
        for (ServerPlayer p : players) {
            SRERole role = gw.getRole(p);
            if (role != null) {
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(p, role);
            }
        }
    }

    /** 清空默认背包（张老师靠商店的碳酸饮料 / 冰激凌，不发武器） */
    protected void initPlayerItems(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            p.getInventory().clearContent();
        }
    }

    /** 每 30 秒给所有存活玩家发放一个风弹（原版 Items.WIND_CHARGE），用于骚扰/位移娱乐 */
    protected void giveWindBombsToAllPlayers(ServerLevel serverWorld) {
        ItemStack windBomb = new ItemStack(Items.WIND_CHARGE);
        // 添加描述：击中玩家时施加深度伤口
        windBomb.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.literal("§c击中玩家时施加深度伤口")
                )));
        for (Player p : serverWorld.players()) {
            if (!(p instanceof ServerPlayer sp)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) continue;
            // 直接复制实例避免共享同一 ItemStack
            sp.addItem(windBomb.copy());
        }
    }

    /** 对局开始 60 秒后给所有存活玩家发放开锁器+撬棍 */
    protected void giveToolsToAllPlayers(ServerLevel serverWorld) {
        // 从 trainmurdermystery 注册表获取开锁器和撬棍
        Item lockpick = BuiltInRegistries.ITEM.get(ResourceLocation.parse("trainmurdermystery:lockpick"));
        Item crowbar = BuiltInRegistries.ITEM.get(ResourceLocation.parse("trainmurdermystery:crowbar"));

        for (Player p : serverWorld.players()) {
            if (!(p instanceof ServerPlayer sp)) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) continue;

            if (lockpick != null && lockpick != Items.AIR) {
                sp.addItem(new ItemStack(lockpick));
            }
            if (crowbar != null && crowbar != Items.AIR) {
                sp.addItem(new ItemStack(crowbar));
            }
            sp.displayClientMessage(Component.literal("§e§l获得开锁器和撬棍！§7快去搞事情吧"), true);
        }
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gw,
                               List<ServerPlayer> players) {
        initRoles(players, gw);
        initPlayerItems(players);
        curWindBombTick = 0;                       // 重置风弹计数器
        curToolDropTick = 0;                        // 重置工具发放计时器
        toolsDropped = false;                       // 重置工具发放标记
        startPositions.clear();                     // 清空起始位置

        // 记录每个玩家的起始位置（用于死亡后原地复活）
        for (ServerPlayer p : players) {
            startPositions.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ()});
        }

        assignModdedRole(players, gw);           // 触发角色 init
        sendWelcomePackets(players, TeacherZhangRole.TEACHER_ZHANG);
    }

    /** 注册全局事件（在 Dim201Addon.onInitialize 中调用） */
    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        // —— 仅喝碳酸饮料触发心脏麻痹（自杀型 heart_attack）才复活为弗兰克 ——
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning()) return;
            if (!(gw.getGameMode() instanceof TeacherZhangAllGameMode mode)) return;

            // 死因必须是心脏麻痹，且击杀者是本人（碳酸饮料猝死：killPlayer(sp, sp, heart_attack)）。
            // 排除走赢家处决其他玩家的 heart_attack（击杀者是赢家而非本人）。
            if (deathReason == null || !"heart_attack".equals(deathReason.getPath())) return;
            if (killer != player) return;

            // 获取起始位置
            double[] startPos = mode.startPositions.get(sp.getUUID());
            if (startPos == null) {
                startPos = new double[]{sp.getX(), sp.getY(), sp.getZ()};
            }

            // 延迟到下一 tick 执行复活+换角色，避免与 SRE 死亡处理冲突
            final double[] finalStartPos = startPos;
            if (sp.getServer() != null) {
                sp.getServer().execute(() -> reviveAsLegionNoShop(sp, gw, finalStartPos));
            }
        });
    }

    /**
     * 将死亡玩家复活为无商店军团
     * 1. 移除旧角色（张老师或已死亡的军团）
     * 2. 分配无商店军团角色
     * 3. 在起始位置复活
     * 4. 技能冷却 30s（便于死亡后较快重新参战）
     * 5. 发送"欢迎登车 弗兰克"公告（参考秃鹫→杀手切换提示）
     */
    private static void reviveAsLegionNoShop(ServerPlayer sp, SREGameWorldComponent gw, double[] startPos) {
        if (!gw.isRunning()) return;
        // 玩家已存活则不处理（可能已被其他逻辑复活）
        if (GameUtils.isPlayerAliveAndSurvival(sp)) return;

        SRERole oldRole = gw.getRole(sp);

        // 移除旧角色
        if (oldRole != null) {
            ModdedRoleRemoved.EVENT.invoker().removeModdedRole(sp, oldRole);
        }
        gw.removeRole(sp);

        // 分配无商店军团
        gw.addRole(sp, LegionNoShopRole.LEGION_NO_SHOP);
        ModdedRoleAssigned.EVENT.invoker().assignModdedRole(sp, LegionNoShopRole.LEGION_NO_SHOP);

        // 在起始位置复活
        GameUtils.revivePlayer(sp, startPos[0], startPos[1], startPos[2]);
        sp.setHealth(sp.getMaxHealth());

        // 技能冷却 30s（无商店军团专用，便于死亡后较快重新参战）
        LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
        if (comp != null) {
            comp.cooldownTicks = 30 * 20;
            comp.sync();
        }

        // 发送"欢迎登车 弗兰克"公告（与秃鹫切换为杀手一致，通过 AnnounceWelcomePayload 触发客户端 UI 动画）
        ServerPlayNetworking.send(sp, new AnnounceWelcomePayload(
                LegionNoShopRole.LEGION_NO_SHOP_ID.toString(), gw.getAllKillerPlayers().size(), 0));

        // 额外文本提示
        sp.displayClientMessage(Component.literal("§c§l你已复活为弗兰克！§7技能冷却30秒，按G键开启野性狂暴"), false);
    }

    /**
     * 胜负判定主循环：
     * 1. 优先检查张老师"走 4000 格"胜利条件（TeacherZhangRole.triggerWin 已经会触发 RoleUtils.customWinnerWin
     *    但我们在服务端这里再做一次兜底，防止组件没 tick 到时漏判）
     * 2. 最后一人存活胜利（Loose End 风格）
     * 3. 时间耗尽：总距离最多者胜
     */
    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gw) {
        super.tickServerGameLoop(serverWorld, gw);

        // —— 每 30 秒给所有存活玩家发放一个风弹 ——
        // 使用 post-increment 计数器模式（与 SREEvilWarGameMode.curBalanceTick 一致）
        if (curWindBombTick++ >= WIND_BOMB_INTERVAL_TICKS) {
            giveWindBombsToAllPlayers(serverWorld);
            curWindBombTick = 0;
        }

        // —— 对局开始 60 秒后给所有存活玩家发放开锁器+撬棍（仅一次） ——
        if (!toolsDropped) {
            if (curToolDropTick++ >= TOOLS_DROP_DELAY_TICKS) {
                giveToolsToAllPlayers(serverWorld);
                toolsDropped = true;
            }
        }

        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        // —— 条件 1：有人达到 4000 格（检查张老师组件 totalDistance）——
        ServerPlayer distanceWinner = null;
        double maxDist = 0.0;
        int aliveCount = 0;
        ServerPlayer lastAlive = null;
        boolean hasTeacherZhangAlive = false;  // 场上是否有存活的张老师（未复活为弗兰克）

        for (ServerPlayer p : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) continue;
            aliveCount++;
            lastAlive = p;
            // 检查当前角色是否为张老师（死亡后会复活为弗兰克，role 会变化）
            SRERole role = gw.getRole(p);
            if (role != null && role.equals(TeacherZhangRole.TEACHER_ZHANG)) {
                hasTeacherZhangAlive = true;
            }
            TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(p);
            if (comp != null) {
                if (comp.totalDistance > maxDist) {
                    maxDist = comp.totalDistance;
                }
                if (comp.totalDistance >= WIN_DISTANCE && distanceWinner == null) {
                    distanceWinner = p;
                }
            }
        }

        if (distanceWinner != null) {
            // 走赢家：使用 WinStatus.CUSTOM + CustomWinnerID 走张老师自定义结算分支
            // （getEndText 会拼出 announcement.star.win.dim201addon.teacher_zhang 翻译键）
            gw.setLooseEndWinner(distanceWinner.getUUID());
            winStatus = GameUtils.WinStatus.CUSTOM;
            SREGameRoundEndComponent round = SREGameRoundEndComponent.KEY.get(serverWorld);
            round.CustomWinnerID = "dim201addon.teacher_zhang";
            round.CustomWinnerColor = 0xFF00E680;  // 与张老师角色色一致
            round.CustomWinnerPlayers.clear();
            round.CustomWinnerPlayers.add(distanceWinner.getUUID());

            // 走赢家获胜时，心脏麻痹杀死其他存活玩家（模仿 SREThiefWarGameMode 的实现）
            for (Player player : serverWorld.players()) {
                if (player != distanceWinner && !GameUtils.isPlayerEliminated(player)) {
                    GameUtils.killPlayer(player, true, distanceWinner, Noellesroles.id("heart_attack"), true);
                }
            }
        }

        // —— 条件 1.5：张老师全部死亡 → 杀手胜利 ——
        // 全员张老师模式下，所有玩家初始为张老师（中立），死亡后复活为弗兰克（杀手阵营）
        // 当场上没有张老师存活时，意味着所有初始张老师都已死亡复活为弗兰克，触发杀手胜利
        // 优先级高于「0 存活 → 无人生还」，避免复活延迟期间误判为无人生还
        if (winStatus == GameUtils.WinStatus.NONE && !hasTeacherZhangAlive) {
            winStatus = GameUtils.WinStatus.KILLERS;
        }

        // —— 条件 2：0 存活 → 无人生还（SRE 原生 NO_PLAYER）——
        if (winStatus == GameUtils.WinStatus.NONE && aliveCount <= 0) {
            winStatus = GameUtils.WinStatus.NO_PLAYER;
        }

        // —— 条件 3：剩 1 人 → 最后存活者胜利，同样走张老师自定义结算 ——
        if (winStatus == GameUtils.WinStatus.NONE && aliveCount == 1) {
            gw.setLooseEndWinner(lastAlive.getUUID());
            winStatus = GameUtils.WinStatus.CUSTOM;
            SREGameRoundEndComponent round = SREGameRoundEndComponent.KEY.get(serverWorld);
            round.CustomWinnerID = "dim201addon.teacher_zhang";
            round.CustomWinnerColor = 0xFF00E680;
            round.CustomWinnerPlayers.clear();
            round.CustomWinnerPlayers.add(lastAlive.getUUID());
        }

        // —— 条件 4：时间耗尽 → 距离最远者胜（否则 TIME=平民胜，和模式主题不符）——
        if (winStatus == GameUtils.WinStatus.NONE
                && !SREGameTimeComponent.KEY.get(serverWorld).hasTime()) {
            // 遍历找 maxDist 对应玩家
            for (Player p : serverWorld.players()) {
                if (!(p instanceof ServerPlayer sp)) continue;
                TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(sp);
                if (comp != null && Math.abs(comp.totalDistance - maxDist) < 0.001
                        && GameUtils.isPlayerAliveAndSurvival(sp)) {
                    gw.setLooseEndWinner(sp.getUUID());
                    SREGameRoundEndComponent round = SREGameRoundEndComponent.KEY.get(serverWorld);
                    round.CustomWinnerID = "dim201addon.teacher_zhang";
                    round.CustomWinnerColor = 0xFF00E680;
                    round.CustomWinnerPlayers.clear();
                    round.CustomWinnerPlayers.add(sp.getUUID());
                    winStatus = GameUtils.WinStatus.CUSTOM;
                    break;
                }
            }
        }

        if (winStatus != GameUtils.WinStatus.NONE
                && gw.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }
}
