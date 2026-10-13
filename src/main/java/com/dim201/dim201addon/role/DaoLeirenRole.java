package com.dim201.dim201addon.role;

import com.dim201.dim201addon.DaoLeirenPlayerComponent;
import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.KatanaItem;
import com.dim201.dim201addon.item.SariraItem;
import com.dim201.dim201addon.item.card.CardTeamKillPunisher;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.ShouldDropOnDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import org.agmas.harpymodloader.Harpymodloader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 刀系蕾忍 - 好人阵营
 *
 * <p>核心机制：
 * <ul>
 *   <li>开局自带打刀（本模组物品）</li>
 *   <li>移动积累蕾克拉（每1格+1，上限120）</li>
 *   <li>按技能键切换攻击模式（5秒冷却）</li>
 *   <li>岚刀模式（默认）：连击5次击杀，有小脑惩罚</li>
 *   <li>燃烧刀模式：消耗60蕾克拉，每秒消耗3点，每刀直接击杀，火焰粒子</li>
 *   <li>死亡掉落舍利子（限1次/局），好人拾取后120秒右键复活</li>
 *   <li>死亡时隐藏所有玩家身份</li>
 * </ul>
 */
public class DaoLeirenRole {

    public static final ResourceLocation DAO_LEIREN_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "dao_leiren");

    /** 打刀击杀死因 */
    public static final ResourceLocation DAO_LEIREN_KATANA_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "dao_leiren_katana");
    /** 误杀好人自身死因 */
    public static final ResourceLocation DAO_LEIREN_MISKILL_DEATH = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "dao_leiren_miskill");

    public static SRERole DAO_LEIREN;

    private static boolean eventsRegistered = false;

    public static void register() {
        DAO_LEIREN = TMMRoles.registerRole(new DaoLeirenRoleImpl());
        Harpymodloader.setRoleMaximum(DAO_LEIREN_ID, 1);
        registerEvents();
    }

    /** 判断玩家是否为蕾忍 */
    public static boolean isDaoLeiren(Player player) {
        if (player == null || player.level() == null) return false;
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null || !gameWorld.isRunning()) return false;
        SRERole role = gameWorld.getRole(player);
        return role != null && role.equals(DAO_LEIREN);
    }

    private static class DaoLeirenRoleImpl extends NormalRole {

        public DaoLeirenRoleImpl() {
            // isInnocent=true（好人阵营），canUseKiller=false
            super(DAO_LEIREN_ID, 0xFF8C00, true, false, MoodType.REAL, 600, true);
            this.setCanSeeTime(false)
                .setDefaultMax(1)
                .setVigilanteTeam(true)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
            // 不设置 setVigilanteTeam(true) → 进入 civilianPool（普通好人池）
            // 这样不占用稀少的警长（vigilante）名额，能正常在对局中刷新
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            ItemStack katana = new ItemStack(Dim201ModItems.KATANA);
            KatanaItem.withBuiltinLore(katana);
            items.add(katana);
            return items;
        }

        @Override
        public java.util.function.Predicate<net.minecraft.world.item.Item> cantPickupItem(Player player) {
            // 蕾忍不能拾取左轮（与鸣上悠一致）
            return item -> item == io.wifi.starrailexpress.index.TMMItems.REVOLVER;
        }

        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, DAO_LEIREN)) return;
            if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
            DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(player);
            if (comp == null) return;

            // 组件的 serverTick 处理蕾克拉积累、模式冷却、燃烧刀消耗
            // （由 ServerTickingComponent 自动调用，无需在此重复）

            // 燃烧刀模式下持续生成火焰粒子
            if (comp.attackMode == DaoLeirenPlayerComponent.AttackMode.RANDAO
                    && player.tickCount % 4 == 0) {
                spawnFlameParticles(player.serverLevel(), player);
            }
        }
    }

    // ==================== 事件注册 ====================

    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        // ---------- 允许持打刀攻击玩家 ----------
        AllowPlayerPunching.EVENT.register((player) -> {
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld == null) return false;
            var role = gameWorld.getRole(player);
            if (role == null || !role.equals(DAO_LEIREN)) return false;
            ItemStack mainHand = player.getMainHandItem();
            return mainHand.is(Dim201ModItems.KATANA);
        });

        // ---------- 打刀左键攻击：岚刀5连击 / 燃烧刀直接击杀 ----------
        AttackEntityCallback.EVENT.register((attacker, world, hand, entity, hitResult) -> {
            if (world.isClientSide) return InteractionResult.SUCCESS;

            if (!(attacker instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ItemStack held = sp.getItemInHand(hand);
            if (!held.is(Dim201ModItems.KATANA)) return InteractionResult.PASS;

            var gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld == null || !gameWorld.isRunning()) return InteractionResult.PASS;
            if (!gameWorld.isRole(sp, DAO_LEIREN)) return InteractionResult.PASS;
            if (!(entity instanceof ServerPlayer target)) return InteractionResult.PASS;
            if (!GameUtils.isPlayerAliveAndSurvival(target) || target.isSpectator()) return InteractionResult.PASS;

            // 冷却中取消攻击
            if (sp.getCooldowns().isOnCooldown(Dim201ModItems.KATANA)) {
                return InteractionResult.FAIL;
            }

            DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(sp);
            if (comp == null) return InteractionResult.PASS;

            if (comp.attackMode == DaoLeirenPlayerComponent.AttackMode.RANDAO) {
                // ===== 燃烧刀模式：每刀直接击杀 =====
                handleRandaoHit(sp, target, gameWorld, comp);
            } else {
                // ===== 岚刀模式：5连击击杀 =====
                handleLandaoHit(sp, target, gameWorld, comp);
            }

            comp.sync();
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        });

        // ---------- 蕾忍死亡：掉落舍利子 + 隐藏身份 ----------
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null || !gameWorld.isRole(sp, DAO_LEIREN)) return;

            DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(sp);
            if (comp == null) return;

            // 记录死亡位置和时间
            comp.onDeath(sp);

            // 掉落舍利子（每局限1次）
            if (!comp.sariraDropped) {
                comp.sariraDropped = true;
                ItemStack sarira = SariraItem.create(sp, comp.deathGameTime);
                // 使用 addFreshEntity 直接生成掉落物，避免 SRE drop 拦截
                ItemEntity itemEntity = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), sarira);
                itemEntity.setNoPickUpDelay();
                sp.level().addFreshEntity(itemEntity);
                Dim201Addon.LOGGER.info("[DaoLeiren] 舍利子已掉落于 {},{},{}",
                        sp.getX(), sp.getY(), sp.getZ());
            }

            comp.sync();
        });

        // ---------- 舍利子随持有者死亡掉落 ----------
        // SRE 死亡清背包时默认只掉落左轮/望远镜，舍利子不在白名单会随背包清空消失。
        // 注册原生 ShouldDropOnDeath 事件，让舍利子与左轮走同一掉落链路，
        // 持有者（好人）阵亡后舍利子掉落原地，其他好人可拾取继续复活蕾忍。
        ShouldDropOnDeath.EVENT.register(stack -> stack.is(Dim201ModItems.SARIRA));
    }

    // ==================== 岚刀模式攻击处理 ====================

    private static void handleLandaoHit(ServerPlayer sp, ServerPlayer target,
                                         SREGameWorldComponent gameWorld,
                                         DaoLeirenPlayerComponent comp) {
        int hits = comp.incrementKatanaHits(target.getUUID());

        if (hits < DaoLeirenPlayerComponent.LANDAO_KILL_HITS) {
            // 未达5次：造成1点伤害 + 提示
            comp.sendActionBar(sp, "§e命中 " + hits + "/" + DaoLeirenPlayerComponent.LANDAO_KILL_HITS
                    + " §7- " + target.getName().getString());
            target.hurt(sp.damageSources().playerAttack(sp), 1.0F);
        } else {
            // 第5次：击杀 + 打刀30秒冷却
            comp.clearKatanaHits(target.getUUID());
            sp.getCooldowns().addCooldown(Dim201ModItems.KATANA, DaoLeirenPlayerComponent.KATANA_COOLDOWN);

            target.hurt(sp.damageSources().playerAttack(sp), 1.0F);
            // 通用小脑惩罚：好人误杀好人时统一处理（含豁免条件 + 警告字幕 + shot_innocent）
            CardTeamKillPunisher.applyIfApplicable(target, sp, DAO_LEIREN_KATANA_DEATH);
            GameUtils.killPlayer(target, true, sp, DAO_LEIREN_KATANA_DEATH);

            comp.sendActionBar(sp, "§e§l岚刀斩杀！§f" + target.getName().getString());
        }
    }

    // ==================== 燃烧刀模式攻击处理 ====================

    private static void handleRandaoHit(ServerPlayer sp, ServerPlayer target,
                                         SREGameWorldComponent gameWorld,
                                         DaoLeirenPlayerComponent comp) {
        // 燃烧刀：直接击杀
        sp.getCooldowns().addCooldown(Dim201ModItems.KATANA, DaoLeirenPlayerComponent.RANDAO_HIT_COOLDOWN);

        // 火焰粒子爆发
        spawnFlameBurst(sp.serverLevel(), target);

        target.hurt(sp.damageSources().playerAttack(sp), 1.0F);
        // 通用小脑惩罚：好人误杀好人时统一处理（含豁免条件 + 警告字幕 + shot_innocent）
        CardTeamKillPunisher.applyIfApplicable(target, sp, DAO_LEIREN_KATANA_DEATH);
        GameUtils.killPlayer(target, true, sp, DAO_LEIREN_KATANA_DEATH);

        comp.sendActionBar(sp, "§c§l燃烧刀斩杀！§f" + target.getName().getString());
    }

    // ==================== 粒子效果 ====================

    /** 燃烧刀模式下持续生成火焰粒子（环绕玩家） */
    private static void spawnFlameParticles(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        // 环形火焰粒子
        for (int i = 0; i < 3; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = 0.8 + Math.random() * 0.4;
            level.sendParticles(ParticleTypes.FLAME,
                    x + Math.cos(angle) * radius, y + Math.random() * 0.5, z + Math.sin(angle) * radius,
                    1, 0, 0.05, 0, 0.01);
        }
        // 偶尔生成岩浆粒子和火心粒子
        if (player.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.3, 0.5, 0.3, 0);
            level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 2, 0.5, 0.5, 0.5, 0.01);
        }
    }

    /** 燃烧刀击杀时爆发大量火焰粒子 */
    private static void spawnFlameBurst(ServerLevel level, ServerPlayer target) {
        double x = target.getX();
        double y = target.getY() + 1.0;
        double z = target.getZ();
        // 大范围火焰爆发
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 30, 1.0, 1.0, 1.0, 0.15);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 10, 0.8, 0.8, 0.8, 0);
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 15, 1.0, 1.0, 1.0, 0.1);
        // 附近玩家也看到火焰
        AABB box = new AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 20, 1.5, 1.5, 1.5, 0.2);
    }

    public static void resetRoleState() {}
}
