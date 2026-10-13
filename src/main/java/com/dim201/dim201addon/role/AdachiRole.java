package com.dim201.dim201addon.role;

import com.dim201.dim201addon.AdachiPlayerComponent;
import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayerIdentifier;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdachiRole {

    public static final ResourceLocation ADACHI_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "adachi");
    public static final ResourceLocation BOREDOM_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "boredom");
    public static final ResourceLocation ADACHI_MAGATSU_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "adachi_magatsu");

    public static SRERole ADACHI;

    private static final int BORING_LIMIT = 2 * 60 * 20;
    private static final int SKILL1_COOLDOWN = 45 * 20;        // 祸津曼荼罗 CD
    private static final int SKILL2_COOLDOWN = 45 * 20;        // 灼热奋起 CD（新增）
    private static final int SKILL3_COOLDOWN = 120 * 20;       // 恶魔微笑 CD（沿用旧浓雾 120s）
    private static final int SKILL2_UNLOCK_TIME = 2 * 60 * 20; // 灼热奋起 2min 解锁
    private static final int SKILL3_UNLOCK_TIME = 3 * 60 * 20; // 恶魔微笑 3min 解锁（沿用旧浓雾解锁时间）
    private static final int EXECUTE_WINDOW_TICKS = 10 * 20;   // 恶魔微笑后 10s 斩杀窗口
    private static final int SHIELD_DURATION_TICKS = 2 * 20;   // 灼热奋起护盾 2s
    private static final int HEAT_RISER_PARTICLE_TICKS = 2 * 20; // 灼热奋起粒子持续 2s（跟随玩家）
    private static final int ROOT_DURATION = 5 * 20;

    private static boolean eventsRegistered = false;

    public static void register() {
        ADACHI = TMMRoles.registerRole(new AdachiRoleImpl());
        Harpymodloader.setRoleMaximum(ADACHI_ID, 1);
        registerEvents();
    }

    private static class AdachiRoleImpl extends NormalRole {

        public AdachiRoleImpl() {
            // isInnocent=false, canUseKiller=false → 独立中立（不吃平民/杀手胜负判定）
            super(ADACHI_ID, 0xFFFFD700, false, false, MoodType.FAKE, -1, true);
            this.setCanSeeTime(true)
                .setCanUseInstinct(true)
                .setInstinctNightVision(true)
                .setAbleToPickUpRevolver(true)
                .setDefaultMax(1)
                .setCanBeRandomedByOtherRoles(false)
                .setNeutrals(true)   // 中立阵营：计入 custom_winner_neturals，不计入平民和杀手
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            // 初始物品：假枪（noellesroles:fake_revolver），复活后才获得真刀
            Item fakeRevolver = BuiltInRegistries.ITEM.get(ResourceLocation.parse("noellesroles:fake_revolver"));
            if (fakeRevolver != null) items.add(new ItemStack(fakeRevolver));
            return items;
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            List<ShopEntry> entries = new ArrayList<>();
            Item revolverItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("trainmurdermystery:revolver"));
            if (revolverItem != null) entries.add(new ShopEntry(new ItemStack(revolverItem), 325, ShopEntry.Type.WEAPON));
            entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
            return entries;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            if (!isAliveAndPlaying(player)) return true;
            AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
            if (!comp.hasRevived) { comp.sendActionBar(player, "§c§l技能锁定！§f首次复活后解锁"); return true; }

            switch (comp.currentSkill) {
                case MAGATSU -> {
                    if (comp.skill1Cooldown > 0) { int cd = (int) Math.ceil(comp.skill1Cooldown / 20.0); comp.sendActionBar(player, "§c§l祸津曼荼罗冷却中 §f" + cd + "秒"); return true; }
                    Player target = findTargetInSight(player);
                    if (target == null || !target.isAlive()) { comp.sendActionBar(player, "§c§l没有目标！"); return true; }
                    if (target instanceof ServerPlayer sp && !GameUtils.isPlayerAliveAndSurvival(sp)) { comp.sendActionBar(player, "§c§l目标已死亡！"); return true; }
                    comp.skill1Cooldown = SKILL1_COOLDOWN;
                    useSkill1(player, target);
                    comp.sync();
                    return true;
                }
                case HEAT_RISER -> {
                    if (!comp.skill2Unlocked) { comp.sendActionBar(player, "§c§l灼热奋起未解锁！"); return true; }
                    if (comp.skill2Cooldown > 0) { int cd = (int) Math.ceil(comp.skill2Cooldown / 20.0); comp.sendActionBar(player, "§c§l灼热奋起冷却中 §f" + cd + "秒"); return true; }
                    comp.skill2Cooldown = SKILL2_COOLDOWN;
                    useSkill2(player);
                    comp.sync();
                    return true;
                }
                case DEVIL_SMILE -> {
                    if (!comp.skill3Unlocked) { comp.sendActionBar(player, "§c§l恶魔微笑未解锁！"); return true; }
                    if (comp.skill3Cooldown > 0) { int cd = (int) Math.ceil(comp.skill3Cooldown / 20.0); comp.sendActionBar(player, "§c§l恶魔微笑冷却中 §f" + cd + "秒"); return true; }
                    comp.skill3Cooldown = SKILL3_COOLDOWN;
                    comp.executeWindowTicks = EXECUTE_WINDOW_TICKS;
                    useSkill3(player);
                    comp.sync();
                    return true;
                }
            }
            return true;
        }

        @Override
        public void serverTick(ServerPlayer player) {}

        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, ADACHI)) return;
            AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
            if (comp == null) return;
            // 死亡/观察者模式：立即清除红雾并同步所有客户端（tick 内部也有清理，但这里是事件入口，
            // 避免 isAlive 前置 return 导致清理逻辑不被执行）
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                if (comp.redFogActive) {
                    comp.redFogActive = false;
                    comp.sync();
                }
                return;
            }
            tickAdachi(player, comp);
        }
    }

    public static void onSwitchSkill(ServerPlayer player) {
        AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
        if (comp == null) return;
        comp.switchSkill(player);
    }

    private static void tickAdachi(ServerPlayer player, AdachiPlayerComponent comp) {
        comp.tick(player);
        // 无聊爆发：复活后计时满2分钟未击杀 → 自杀（复活后hasRevived=true，无法再复活）
        if (comp.boringTimer >= BORING_LIMIT) {
            comp.boringTimer = 0; comp.dyingFromBoredom = true;
            GameUtils.forceKillPlayer(player, false, null, BOREDOM_DEATH);
            comp.dyingFromBoredom = false; return;
        }
        // 灼热奋起粒子持续 spawn（跟随玩家位置，每 tick 6 个粒子旋转）
        if (comp.heatRiserParticleTicks > 0) spawnTriColorParticles(player.serverLevel(), player);
        processRootedTargets(player, comp);
    }

    private static void useSkill1(ServerPlayer player, Player target) {
        ServerLevel level = player.serverLevel();
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ROOT_DURATION, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_DURATION, 127, false, false, true));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "adachi.skill1")), SoundSource.PLAYERS, 1.0f, 1.0f);
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
        mood.addMood(-0.3f);
        AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
        comp.rootedTargets.put(target.getUUID(), ROOT_DURATION);
        comp.sync();
    }

    /** 技能2 灼热奋起：播放 heatriser 音效、三色粒子（持续 2s 跟随玩家）、刷新武器冷却或获得 2s 护盾 */
    private static void useSkill2(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // 播放 heatriser 音效（sounds.json 已配置 3 个随机）
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "adachi.heatriser")),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        // 红绿紫三色粒子持续 2s 跟随玩家（不写在文本介绍里；实际 spawn 在 tickAdachi 每 tick 触发）
        AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
        comp.heatRiserParticleTicks = HEAT_RISER_PARTICLE_TICKS;

        // 收集身上所有武器的冷却
        List<Item> coolingWeapons = collectCoolingWeapons(player);
        if (coolingWeapons.isEmpty()) {
            // 没有武器在冷却中 → 获得 2s 护盾
            comp.grantShieldIfNeeded(player);
            comp.shieldTicksLeft = SHIELD_DURATION_TICKS;
            comp.sendActionBar(player, "§a§l灼热奋起！§f身上无武器冷却，获得 §a2s 护盾");
        } else {
            // 有武器在冷却中 → 刷新全部武器冷却
            for (Item weapon : coolingWeapons) player.getCooldowns().removeCooldown(weapon);
            comp.sendActionBar(player, "§a§l灼热奋起！§f已刷新 §6" + coolingWeapons.size() + " §f个武器冷却");
        }
    }

    /** 技能3 恶魔微笑：全员失明+SAN 固定 0.15（原浓雾逻辑改名） */
    private static void useSkill3(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // 沿用 adachi.skill2 音效事件名（避免重复造音效）
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "adachi.skill2")),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        for (ServerPlayer target : level.getPlayers(p -> GameUtils.isPlayerAliveAndSurvival(p))) {
            if (target == player) continue;
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 5 * 20, 0, false, false, true));
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(target);
            mood.setMood(0.15f);
        }
        AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
        comp.sendActionBar(player, "§6§l恶魔微笑！§f所有玩家进入低SAN状态，§c10s §f内可无视理智直接斩杀");
    }

    /** 收集玩家身上处于冷却中的武器（仅武器，不含工具/卡牌） */
    private static List<Item> collectCoolingWeapons(ServerPlayer player) {
        List<Item> out = new ArrayList<>();
        // 遍历主物品栏 + 副手，用 ArrayList.contains 去重（物品数量少，O(n) 足够）
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (out.contains(item)) continue;
            if (!isWeapon(item)) continue;
            if (player.getCooldowns().isOnCooldown(item)) out.add(item);
        }
        return out;
    }

    /** 判定物品是否为武器：SwordItem 子类（刀剑）或 ResourceLocation path 含 revolver/gun/knife/uchigatana/katana */
    private static boolean isWeapon(Item item) {
        if (item instanceof net.minecraft.world.item.SwordItem) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("revolver") || path.contains("gun")
                || path.contains("knife") || path.contains("uchigatana") || path.contains("katana");
    }

    /** 红绿紫三色粒子（跟随玩家环绕，每 tick 调用一次，6 个粒子旋转） */
    private static void spawnTriColorParticles(ServerLevel level, ServerPlayer player) {
        Vec3 center = player.position().add(0, 1.0, 0);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.5f);
        DustParticleOptions green = new DustParticleOptions(new Vector3f(0.0f, 1.0f, 0.0f), 1.5f);
        DustParticleOptions purple = new DustParticleOptions(new Vector3f(0.6f, 0.0f, 1.0f), 1.5f);
        DustParticleOptions[] colors = {red, green, purple};
        double r = 1.5;
        long time = level.getGameTime();
        // 6 个粒子均匀分布环形，角度随时间旋转产生流动效果
        for (int i = 0; i < 6; i++) {
            double angle = (2 * Math.PI * i) / 6 + (time * 0.15);
            double px = center.x + Math.cos(angle) * r;
            double py = center.y + Math.sin(time * 0.2 + i) * 0.3;
            double pz = center.z + Math.sin(angle) * r;
            level.sendParticles(colors[i % 3], px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    private static void processRootedTargets(ServerPlayer player, AdachiPlayerComponent comp) {
        ServerLevel level = player.serverLevel();
        Iterator<Map.Entry<UUID, Integer>> it = comp.rootedTargets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID targetId = entry.getKey(); int ticks = entry.getValue() - 1;
            Player target = findPlayerByUUID(level, targetId);
            if (target == null || !target.isAlive()) { it.remove(); continue; }
            if (target instanceof ServerPlayer sp && sp.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) { it.remove(); continue; }
            Vec3 center = target.position().add(0, 1.0, 0);
            if (ticks > 20) spawnCircleParticles(level, center, 1.2, 20, true);
            else spawnGatherParticles(level, center, 1.2 - (1.2 * (20 - ticks) / 20.0), 10);
            if (ticks <= 0) {
                it.remove();
                // 新逻辑：只有在恶魔微笑 10s 斩杀窗口内才直接斩杀（无视理智限制）
                if (comp.executeWindowTicks > 0 && target instanceof ServerPlayer targetServer) {
                    // leaveBody=true 留下尸体，不清除掉落物
                    GameUtils.killPlayer(targetServer, true, player, ADACHI_MAGATSU_DEATH);
                }
            } else entry.setValue(ticks);
        }
    }

    private static void spawnCircleParticles(ServerLevel level, Vec3 center, double radius, int count, boolean red) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.2f);
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count + (level.getGameTime() * 0.1);
            double px = center.x + Math.cos(angle) * radius;
            double py = center.y + 0.5 + Math.sin(level.getGameTime() * 0.2 + i * 0.5) * 0.3;
            double pz = center.z + Math.sin(angle) * radius;
            level.sendParticles(dust, px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    private static void spawnGatherParticles(ServerLevel level, Vec3 center, double currentRadius, int count) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.0f);
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double px = center.x + Math.cos(angle) * currentRadius;
            double py = center.y + 0.5;
            double pz = center.z + Math.sin(angle) * currentRadius;
            Vec3 toCenter = center.subtract(px, py, pz).normalize().scale(0.15);
            level.sendParticles(dust, px, py, pz, 1, toCenter.x, toCenter.y, toCenter.z, 0.1);
        }
    }

    private static Player findTargetInSight(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition(1.0f); Vec3 lookVec = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(10.0);
        Player best = null; double bestDot = 0.5;
        for (Player candidate : player.level().getEntitiesOfClass(Player.class, box)) {
            if (candidate == player || !candidate.isAlive()) continue;
            if (candidate instanceof ServerPlayer sp && !GameUtils.isPlayerAliveAndSurvival(sp)) continue;
            if (NarukamiRole.isNarukami(candidate)) continue;   // 鸣上悠不会成为祸津曼荼罗的目标
            Vec3 toTarget = candidate.position().add(0, candidate.getEyeHeight() * 0.5, 0).subtract(eyePos).normalize();
            double dot = toTarget.dot(lookVec); if (dot > bestDot) { bestDot = dot; best = candidate; }
        }
        return best;
    }

    private static ServerPlayer findPlayerByUUID(ServerLevel level, UUID uuid) {
        for (ServerPlayer p : level.getPlayers(player -> true)) if (p.getUUID().equals(uuid)) return p;
        return null;
    }

    private static boolean isAliveAndPlaying(ServerPlayer player) {
        if (player == null || !player.isAlive()) return false;
        return player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
    }

    private static boolean isAdachi(Player player) {
        if (player.level().isClientSide) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(ADACHI);
    }

    public static void registerEvents() {
        if (eventsRegistered) return;   // 幂等：防止重复注册事件
        eventsRegistered = true;

        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> tryRevive(player));
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> tryRevive(player));
        OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, reason) -> {
            if (killer != null && isAdachi(killer)) { AdachiPlayerComponent comp = ModComponents.ADACHI.get(killer); comp.boringTimer = 0; comp.sync(); }
        });

        // ========== 独立胜利机制（参考 NoellesRoles 小偷角色的 AllowGameEnd 用法） ==========
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode) return GameUtils.WinStatus.NOT_MODIFY;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorld == null || !gameWorld.isRunning()) return GameUtils.WinStatus.NOT_MODIFY;

            // 找存活的足立透 + 统计总存活人数
            ServerPlayer adachiPlayer = null;
            int aliveCount = 0;
            for (ServerPlayer p : serverLevel.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(p)) {
                    aliveCount++;
                    if (gameWorld.isRole(p, ADACHI)) adachiPlayer = p;
                }
            }
            // 足立透已死亡 → 不干预正常胜负判定
            if (adachiPlayer == null) return GameUtils.WinStatus.NOT_MODIFY;

            // 只剩足立透自己 → 独立胜利（CustomWinnerID = 角色ID的path，胜利统计自动匹配）
            if (aliveCount == 1) {
                SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverLevel);
                if (roundEnd != null) {
                    roundEnd.CustomWinnerID = ADACHI_ID.getPath();
                    roundEnd.CustomWinnerColor = 0xFFFFD700;
                    roundEnd.CustomWinnerTitle = Component.literal("§6§l足立透 获胜");
                    roundEnd.CustomWinnerSubtitle = Component.literal("§7「这个无聊透顶的世界，终于安静了」");
                }
                return GameUtils.WinStatus.CUSTOM;
            }

            // 足立透存活且已出现非超时胜负（杀手/平民/亡命徒等）→ 阻止结束，让对局继续
            // 对局时间耗尽（TIME）正常结束，不干预
            if (winStatus != GameUtils.WinStatus.NONE && winStatus != GameUtils.WinStatus.TIME) {
                return GameUtils.WinStatus.NONE;
            }

            return GameUtils.WinStatus.NOT_MODIFY;
        });
    }

    private static boolean tryRevive(Player player) {
        if (!isAdachi(player) || !(player instanceof ServerPlayer serverPlayer)) return true;
        AdachiPlayerComponent comp = ModComponents.ADACHI.get(serverPlayer);
        if (comp.hasRevived) return true;
        comp.hasRevived = true;
        comp.redFogActive = true;
        ServerLevel level = serverPlayer.serverLevel();
        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "adachi.death")), SoundSource.PLAYERS, 1.0f, 1.0f);
        // 使用对局开始时记录的初始位置复活
        if (comp.hasSpawnPos) serverPlayer.teleportTo(level, comp.spawnX, comp.spawnY, comp.spawnZ, comp.spawnYaw, comp.spawnPitch);
        else {
            AreasWorldComponent.PosWithOrientation spawn = AreasWorldComponent.KEY.get(level).getSpawnPos();
            if (spawn != null) serverPlayer.teleportTo(level, spawn.pos.x(), spawn.pos.y(), spawn.pos.z(), spawn.yaw, spawn.pitch);
        }
        serverPlayer.setHealth(serverPlayer.getMaxHealth());
        // 复活后获得一把刀（物品栏满则掉落在脚下）
        ItemStack knife = new ItemStack(TMMItems.KNIFE);
        if (!serverPlayer.getInventory().add(knife)) {
            serverPlayer.drop(knife, false);
        }
        comp.boringTimer = 0;
        level.setWeatherParameters(0, 120000, true, true);
        comp.sync();
        return false;
    }

    public static void resetRoleState() {}
}
