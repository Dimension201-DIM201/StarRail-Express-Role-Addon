package com.dim201.dim201addon;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 军团 Legion 的「野性狂暴」技能组件（作用于军团玩家本人）
 *
 * 规则：
 *  1. 技能开/关（onAbilityUse 触发；技能已开时提前关；关的时候给缓慢V+清军刀）
 *  2. 技能冷却：60s（任何解除方式结束后进入 60s 冷却；冷却中不可开）
 *     开局额外有 120s 初始冷却（ModdedRoleAssigned 后写入 cooldown=120s）
 *  3. 给一把 noellesroles:sp_knife 放到 0 号槽；0 号槽每 tick 强制 selected=0，防止切换
 *  4. 技能基础持续 15s；每次军刀蓄力命中玩家刷新 15s
 *  5. 技能开启给速度 I；每击中新玩家速度+1（最高 IV）
 *  6. 击中伤口玩家 → 立即结束（走 endSkill(giveSlowness=true, removeKnife=true）
 *  7. 击中累计 ≥ 当前存活人数/2 后下一击必杀；必杀完立即结束
 *  8. 任何结束方式：3s 缓慢V
 *
 *  冷却字段 cooldownTicks 每 tick --；skillTicksLeft 技能激活时每 tick --
 *  9. 初始化/清除：Dim201Addon 角色分配/移除/重置时调用 init/clear
 */
public class LegionPlayerComponent implements ServerTickingComponent, AutoSyncedComponent {

    public static final SoundEvent SOUND_SKILL_START = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "legionskill"));

    private static final int DEFAULT_DURATION_TICKS = 15 * 20;
    private static final int SLOWNESS_END_TICKS = 3 * 20;
    private static final int COOLDOWN_TICKS = 60 * 20;
    private static final int STARTING_COOLDOWN_TICKS = 120 * 20;
    private static final int MAX_SPEED_LEVEL = 5; // 用户要求：最高加速改为速度 V
    /**
     * 军团技能期给玩家的刀：noellesroles:sp_knife（军刀），用户明确要求「一定要用军刀，因为军刀击杀没有冷却」。
     * 命中逻辑仍然走 SRE 原生的「stab 命中 → GameUtils.killPlayer → OnKillPlayerTriggered 拦截 → 施加伤口」，
     * 所以不管是哪种刀，只要 SRE 认为是 stab 命中并触发 killPlayer，我们都能接住（OnKillPlayerTriggered 是全局事件，和刀的子类无关）。
     */
    private static final Item KNIFE_ITEM = ModItems.SP_KNIFE;
    /** 攻击节流：同一玩家对同一目标两次施加伤口至少间隔 10 tick（避免 Minecraft 挥刀动画连发多次触发） */
    private static final int KNIFE_HIT_COOLDOWN_TICKS = 10;
    /** 上次对 [目标UUID] 施加伤口的 tick 时间（服务器 tick，按 ServerLevel#getGameTime）；只记录最近一次目标 */
    private UUID lastHitTargetUuid = null;
    private long lastHitGameTime = -1L;
    /** 普通刀（商店的 trainmurdermystery:knife）物品：启动时懒加载一次 */
    private static Item COMMON_KNIFE_ITEM_CACHE = null;

    /** ThreadLocal：允许击杀（下一击必杀 & 深度伤口时间到期的杀） */
    public static final ThreadLocal<Boolean> EXECUTING_KILL_NOW = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final Player player;

    public boolean skillActive = false;
    public int skillTicksLeft = 0;
    public int cooldownTicks = 0;          // 技能冷却（0表示没冷却，>0 时 onAbilityUse 拒绝开启
    public int speedLevel = 0;
    public final Set<UUID> hitPlayersThisSession = new HashSet<>();
    public boolean nextHitIsExecution = false;
    /** 军团的 5 张"混音带"商店效果：本局内永久生效（切角色/重置时 init/clear 清掉） */
    public boolean tapeFrankPurchased = false;   // 弗兰克：技能期军刀可瞬间破坏 SRE 门
    public boolean tapeJollyPurchased = false;   // 朱莉：技能初始速度从 I→II（上限仍 V）
    public boolean tapeSusiePurchased = false;   // 苏西：击中玩家时 36 格范围内所有玩家降低 20% san
    public boolean tapeJoeyPurchased  = false;   // 乔伊：击中玩家时 36 格范围内所有玩家给予 缓慢 I（60 tick 3s）
    public boolean tapeSmokePurchased = false;   // 冒烟：击中玩家时 36 格范围内所有玩家给予 3 秒失明
    /** 「野性狂暴期间」临时给军团玩家的一层 SRE 原生护盾（达到处决进度就给，结束就扣） */
    public boolean shieldGrantedThisSession = false;
    public int lastSreArmorRef = -1;
    /** 野性狂暴期间锁定的热栏槽位（不再强制 0 号）：开技能时把军刀放到哪个热栏空位，就锁定哪个槽 */
    public int lockedHotbarSlot = 0;
    /** 热栏同步节流：上次全量 broadcastChanges 的 tickCount，用于方案A节流 */
    private int lastSlotSyncTick = -100;
    /**
     * 技能期临时保存玩家「热栏 0~8 除了锁定槽之外」的其他攻击型武器。
     * 重要说明：
     *   TMM/SRE 对局中玩家**根本打不开 E 键大背包**，只能看到 9 格快捷栏。
     *   如果上一版把武器搬到大背包 9~35 就等于永久扣了玩家的武器（永远拿不回来）。
     *   现在改为：攻击型武器先存 tempStoredHotbarWeapons<原槽号, ItemStack>，技能结束时**原槽号原封不动归还**；
     *   非攻击型物品（医疗包、钥匙、道具、食物、防御药剂等）依然放在热栏，不碰（因为它们不影响「锁定军刀才能打人」的规则）。
     */
    public final Map<Integer, ItemStack> tempStoredHotbarWeapons = new HashMap<>();

    public LegionPlayerComponent(@NotNull Player player) {
        this.player = player;
    }

    // ============================================================
    // 生命周期 & 持久化
    // ============================================================
    public void init() {
        skillActive = false;
        skillTicksLeft = 0;
        cooldownTicks = STARTING_COOLDOWN_TICKS; // 开局冷却 120s
        speedLevel = 0;
        hitPlayersThisSession.clear();
        nextHitIsExecution = false;
        tapeFrankPurchased = false;
        tapeJollyPurchased = false;
        tapeSusiePurchased = false;
        tapeJoeyPurchased = false;
        tapeSmokePurchased = false;
        shieldGrantedThisSession = false;
        lastSreArmorRef = -1;
        lockedHotbarSlot = 0;
        tempStoredHotbarWeapons.clear();
        sync();
    }

    public void clear() {
        if (skillActive) {
            // 切角色/重置：结束技能（含慢刀清除缓慢V5 3s+清除，加冷却 60s 的逻辑由 给
            forceEndSkill(true, true, true);
        } else {
            if (player instanceof ServerPlayer sp) clearKnifeFromInventory(sp);
            // 角色被移除：冷却清 0
            cooldownTicks = 0;
            tempStoredHotbarWeapons.clear();
            // （如果上一局遗留了一层"军团处决护盾"，clear 这里统一兜底扣掉）
            consumeLegionShieldIfStillOn();
            initWithoutStartCooldown();
        }
    }

    private void initWithoutStartCooldown() {
        skillActive = false;
        skillTicksLeft = 0;
        speedLevel = 0;
        hitPlayersThisSession.clear();
        nextHitIsExecution = false;
        tapeFrankPurchased = false;
        tapeJollyPurchased = false;
        tapeSusiePurchased = false;
        tapeJoeyPurchased = false;
        tapeSmokePurchased = false;
        shieldGrantedThisSession = false;
        lastSreArmorRef = -1;
        lockedHotbarSlot = 0;
        tempStoredHotbarWeapons.clear();
        lastHitTargetUuid = null;
        lastHitGameTime = -1L;
        sync();
    }

    // ============================================================
    // 技能开 / 关
    // ============================================================
    public void toggleSkill(@NotNull ServerPlayer sp) {
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;
        if (!skillActive) {
            if (cooldownTicks > 0) {
                int secLeft = (cooldownTicks + 19) / 20;
                LegionSendActionBar(sp, "§e野性狂暴 §c冷却中§f：还剩 §c" + secLeft + " §f秒后可再次开启");
                return;
            }
            startSkill(sp);
        } else {
            // 技能期间手动关：进入 60s 冷却
            forceEndSkill(true, true, true);
        }
    }

    private void startSkill(@NotNull ServerPlayer sp) {
        // 【关键】只找热栏 0~8 空位放军刀（TMM/SRE 对局中玩家打不开大背包，放 9~35 = 给了也用不了，等于没给）
        int slot = findEmptySlotForKnife(sp);
        if (slot < 0) {
            LegionSendActionBar(sp, "§c开启野性狂暴失败！§f请至少清空一格快捷栏后再开启（需要 1 个热栏空位放军刀，不会占用你现有的物品）");
            return;
        }
        tempStoredHotbarWeapons.clear();
        skillActive = true;
        skillTicksLeft = DEFAULT_DURATION_TICKS;
        // 朱莉的混音带：技能期初始速度从速度 I 改成速度 II（上限仍然是 5 级 V）
        speedLevel = tapeJollyPurchased ? 2 : 1;
        hitPlayersThisSession.clear();
        nextHitIsExecution = false;
        // 既然只找热栏空位，slot 肯定是 0~8，直接锁定就行
        lockedHotbarSlot = slot;
        ensureMobEffects(sp);
        giveKnifeToSlot(sp, slot);
        sp.getInventory().selected = lockedHotbarSlot;
        // 【关键1】开技能瞬间：把热栏其他 8 格里的所有 SRE 武器「存起来」，不从背包移动，直接放到 tempStoredHotbarWeapons<原槽号, 物品>
        //     非武器物品（医疗包、钥匙、道具、食物、防御药剂）仍留在原热栏，不碰
        storeWeaponsFromHotbar(sp, lockedHotbarSlot);
        // 【关键2】强制同步热栏选中槽给客户端 + 同步整个背包内容（不然客户端改 selected 下 tick 就拉回去了）
        // 开技能瞬间重置节流计数器，确保首次 broadcastChanges 立即执行（不等 5 tick）
        lastSlotSyncTick = -100;
        syncHotbarSlot(sp, lockedHotbarSlot);
        sp.playNotifySound(SOUND_SKILL_START, SoundSource.PLAYERS, 0.4f, 1.0f);
        String jollyTxt = tapeJollyPurchased ? " §8(朱莉混音带·初始速度 II)" : "";
        LegionSendActionBar(sp, "§6§l野性狂暴 §f开启！§6 速度 " + toRoman(speedLevel) + " §f，快捷栏已锁定在军刀，右键蓄力击中玩家施加深度伤口" + jollyTxt);
        sync();
    }

    // ============================================================
    // 军团处决进度护盾（nextHitIsExecution=true 时加一层 SRE 原生护盾，结束时扣回）
    // ============================================================
    private void grantLegionShieldIfNeeded(@NotNull ServerPlayer sp) {
        if (shieldGrantedThisSession) return;
        try {
            io.wifi.starrailexpress.cca.SREArmorPlayerComponent armor =
                    io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp);
            if (armor == null) return;
            int before = armor.getArmor();
            armor.addArmor();
            int after = armor.getArmor();
            // 只在"护盾真的 +1 成功"时把标志打开，避免结束时误扣别人的护盾
            if (after > before) {
                shieldGrantedThisSession = true;
                lastSreArmorRef = after;
                LegionSendActionBar(sp, "§a护盾激活！§f因已达到处决进度，你获得了 §a1 层护盾 §f（技能结束时回收）");
            }
        } catch (Throwable ignored) {}
    }

    private void consumeLegionShieldIfStillOn() {
        if (!shieldGrantedThisSession) return;
        try {
            if (player instanceof ServerPlayer sp) {
                io.wifi.starrailexpress.cca.SREArmorPlayerComponent armor =
                        io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp);
                if (armor != null && armor.getArmor() > 0) {
                    armor.removeArmor(1);
                }
            }
        } catch (Throwable ignored) {} finally {
            shieldGrantedThisSession = false;
            lastSreArmorRef = -1;
        }
    }

    /**
     * 强制结束技能。
     * @param giveSlowness  是否给 3s 缓慢V
     * @param removeKnife   是否清除所有 SP_KNIFE（含背包/主手/副手）
     * @param applyCool60     是否进入 60s 冷却（手动关 & 正常技能期其他结束方式均要走 60s 冷却；但开局重置/切角色不进入）
     */
    public void forceEndSkill(boolean giveSlowness, boolean removeKnife, boolean applyCool60) {
        ServerPlayer sp = (player instanceof ServerPlayer) ? (ServerPlayer) player : null;
        // 技能结束先把"处决进度奖励护盾"收回来（用户要求：技能结束时清除）
        consumeLegionShieldIfStillOn();
        skillActive = false;
        skillTicksLeft = 0;
        speedLevel = 0;
        hitPlayersThisSession.clear();
        nextHitIsExecution = false;
        lockedHotbarSlot = 0;
        if (applyCool60) cooldownTicks = COOLDOWN_TICKS;
        if (sp != null) {
            stopLegionSkillSound(sp);
            sp.removeEffect(MobEffects.MOVEMENT_SPEED);
            if (giveSlowness) {
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_END_TICKS, 4, false, false, true));
            }
            if (removeKnife) clearKnifeFromInventory(sp);
            // 【关键】技能结束：把 tempStoredHotbarWeapons 存的原玩家武器「原槽号」归还给玩家
            //         归还规则：如果原槽号现在是空的就直接放（大概率，因为是我们刚清空的）；否则如果现在是军刀就先放去大背包（但 removeKnife 已经清军刀了）；再否则就近放其他热栏空位。
            restoreStoredHotbarWeapons(sp);
            tempStoredHotbarWeapons.clear();
            String coolTxt = applyCool60 ? (" §7(§e冷却 60 秒)") : "";
            LegionSendActionBar(sp, "§6§l野性狂暴 §c已结束§f，你获得了 §c3 秒 缓慢 V" + coolTxt);
        }
        sync();
    }

    /** 停止对该玩家播放 legionskill 音效（开启技能后，军团玩家自己死亡 / 任何方式结束技能都要停）*/
    public static void stopLegionSkillSound(@NotNull ServerPlayer sp) {
        try {
            sp.connection.send(new ClientboundStopSoundPacket(
                    ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "legionskill"),
                    SoundSource.PLAYERS
            ));
        } catch (Throwable ignored) { /* 停音失败不影响其他逻辑 */ }
    }

    // ============================================================
    // 军刀：给刀 + 锁定 + 清除（新版：不抢占玩家任何原有物品）
    // ============================================================

    /** 找一个空位放军刀：优先热栏 0~8，再大背包 9~35；没任何空位返回 -1 */
    /** 找一个空位放军刀：**只找热栏 0~8**（TMM/SRE 对局中玩家根本打不开大背包 9~35，放了也白给）；没任何空位返回 -1 */
    private static int findEmptySlotForKnife(@NotNull ServerPlayer sp) {
        for (int i = 0; i <= 8; i++) {
            if (sp.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static int findFirstEmptyHotbar(@NotNull ServerPlayer sp) {
        for (int i = 0; i <= 8; i++) {
            if (sp.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** 直接把新军刀放到 slot 那格（调用方必须先确认该格为空，避免覆盖原物品） */
    private static void giveKnifeToSlot(@NotNull ServerPlayer sp, int slot) {
        ItemStack knifeStack = new ItemStack(KNIFE_ITEM);
        // 给这把普通刀打「杀手标记 + 满血耐用度」（反编译 KillerKnifeDurability.applyFreshDurability 做的就是这件事）：
        //   - 标记后 KillerKnifeDurability.isMarkedKnife(stack) 判定通过，不会被 KnifeItem 拦在 KnifeStabPayload 之前
        //   - 满血耐用度 KillerKnifeDurability.isDepleted(stack) 返回 false，不会被「刀已耗尽」拦截直接提示
        try { KillerKnifeDurability.applyFreshDurability(knifeStack); } catch (Throwable ignored) {}
        sp.getInventory().setItem(slot, knifeStack);
    }

    private static void clearKnifeFromInventory(@NotNull ServerPlayer sp) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(KNIFE_ITEM)) sp.getInventory().setItem(i, ItemStack.EMPTY);
        }
        ItemStack off = sp.getInventory().offhand.get(0);
        if (!off.isEmpty() && off.is(KNIFE_ITEM)) sp.getInventory().offhand.set(0, ItemStack.EMPTY);
        // 清军刀后同步一下背包，避免客户端显示残留
        try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
    }

    /**
     * 判断某物品是否是「SRE / NoelleRoles 中的攻击型武器」
     * 技能期：军团玩家热栏里除了军刀（KNIFE_ITEM = SP_KNIFE）之外的所有攻击型武器都搬到大背包，避免切槽后用其他武器打人
     */
    public static boolean isCombatWeaponItem(@NotNull Item i) {
        // 军刀自己允许保留（但只在 lockedSlot 那格，其他格一样要搬）
        if (i == KNIFE_ITEM) return false;
        // --- SRE 本体武器（TMMItems，项目明确引用过）---
        if (i == TMMItems.KNIFE) return true;
        if (i == TMMItems.REVOLVER) return true;
        if (i == TMMItems.DERRINGER) return true;
        if (i == TMMItems.SNIPER_RIFLE) return true;
        if (i == TMMItems.BAT) return true;
        if (i == TMMItems.CROWBAR) return true;
        if (i == TMMItems.GRENADE) return true;
        // --- 其他可能的攻击型：用 MC 原生 registry name 模糊匹配（所有 SRE/NoelleRoles 武器的 id 里都会带这些关键词，稳定不会漏） ---
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(i);
            if (id == null) return false;
            String s = id.toString();
            if (s.contains(":knife") || s.contains("_knife")
                    || s.contains(":gun") || s.contains("_pistol") || s.contains("_revolver")
                    || s.contains("_sniper") || s.contains("_rifle") || s.contains("_shotgun")
                    || s.contains(":sword") || s.contains("_sword") || s.contains("_katana")
                    || s.contains(":bat") || s.contains("_bat")
                    || s.contains(":grenade") || s.contains("_grenade") || s.contains(":bomb") || s.contains("_bomb")
                    || s.contains("_shuriken") || s.contains("_dart") || s.contains("_blade")
                    || s.contains("_dagger") || s.contains("_machete") || s.contains("_sickle")
                    || s.contains("_crowbar") || s.contains("_drill") || s.contains("_taser")
                    || s.contains("_derringer") || s.contains("_revolver")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 军团技能期「需要从热栏暂时收起来，技能结束归还」的物品集合 = 所有攻击型武器 + 开锁器（用户要求）
     *   - 攻击型武器：防止玩家切其他刀/枪打人（技能期只能用军刀）
     *   - 开锁器（trainmurdermystery:lockpick）：用户明确要求，技能期暂时移除，结束归还
     */
    public static boolean isSkillTempStoreItem(@NotNull Item i) {
        if (i == KNIFE_ITEM) return false; // 军刀自己（技能期给的）不收
        if (isCombatWeaponItem(i)) return true;
        // --- 开锁器：id 匹配 trainmurdermystery:lockpick ---
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(i);
            if (id != null) {
                String s = id.toString();
                if ("trainmurdermystery:lockpick".equals(s) || s.endsWith(":lockpick") || s.contains("_lockpick")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 把热栏 0~8 除了 keepSlot 之外，所有「技能期要暂存的物品」（见 isSkillTempStoreItem）存到 this.tempStoredHotbarWeapons<原槽号, ItemStack> 里，
     * 再把热栏对应槽**清空**（非技能期暂存物品：医疗包、钥匙、道具、食物、防御药剂、地图等 → 不碰，继续留在原热栏给玩家用）。
     *
     * 注意：如果 keepSlot 之外的某个槽已经被存过（同一个玩家连续多次 tick 调），不重复覆盖，
     *       只把「新出现的武器」（玩家刚从地上捡的 / 从大背包点进来的）再补存，保证技能结束归还时不会把本来就有的东西吞掉。
     */
    private void storeWeaponsFromHotbar(@NotNull ServerPlayer sp, int keepSlot) {
        int keep = Math.max(0, Math.min(8, keepSlot));
        boolean needSync = false;
        for (int i = 0; i <= 8; i++) {
            if (i == keep) continue;
            ItemStack s = sp.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            if (!isSkillTempStoreItem(it)) continue; // 非武器+非开锁器：不碰
            // 已经存过这一格？就不覆盖，防止吞物品；但如果玩家又放了新的就清掉（技能期不让用）
            if (tempStoredHotbarWeapons.containsKey(i)) {
                // 已存过：直接清掉当前热栏的（玩家又放回来的）
                sp.getInventory().setItem(i, ItemStack.EMPTY);
                needSync = true;
                continue;
            }
            // 没存过：先把原物品存 temp map，再清空该热栏槽
            tempStoredHotbarWeapons.put(i, s.copy());
            sp.getInventory().setItem(i, ItemStack.EMPTY);
            needSync = true;
        }
        if (needSync) {
            try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 技能结束时，把 tempStoredHotbarWeapons 存的原玩家武器**原槽号**归还热栏。
     *  - 如果原槽号现在是空的 → 直接放（99% 情况，因为 store 时我们清空的就是该槽）
     *  - 如果原槽号被占了（极端情况）→ 找其他热栏空位；再没有就放玩家背包 9~35 的空位；
     *    再没有就不归还（掉地上也绝对不会，保证不丢地），并记日志留底。
     */
    private void restoreStoredHotbarWeapons(@NotNull ServerPlayer sp) {
        if (tempStoredHotbarWeapons.isEmpty()) return;
        boolean needSync = false;
        for (var entry : tempStoredHotbarWeapons.entrySet()) {
            int originalSlot = entry.getKey();
            ItemStack original = entry.getValue();
            if (original == null || original.isEmpty()) continue;
            // 先尝试原槽号（优先）
            if (originalSlot >= 0 && originalSlot <= 8 && sp.getInventory().getItem(originalSlot).isEmpty()) {
                sp.getInventory().setItem(originalSlot, original.copy());
                needSync = true;
                continue;
            }
            // 再尝试其他热栏空位
            int otherHotbar = -1;
            for (int i = 0; i <= 8; i++) {
                if (sp.getInventory().getItem(i).isEmpty()) { otherHotbar = i; break; }
            }
            if (otherHotbar >= 0) {
                sp.getInventory().setItem(otherHotbar, original.copy());
                needSync = true;
                continue;
            }
            // 再尝试大背包 9~35 空位（虽然 TMM 模式玩家打不开，但至少没丢）
            int bigBackpack = -1;
            for (int i = 9; i < 36; i++) {
                if (sp.getInventory().getItem(i).isEmpty()) { bigBackpack = i; break; }
            }
            if (bigBackpack >= 0) {
                sp.getInventory().setItem(bigBackpack, original.copy());
                needSync = true;
                continue;
            }
            // 再没有就不塞了（绝不丢地，宁可我们没还），写个 warn log
            try { Dim201Addon.LOGGER.warn("[Legion] 技能结束归还玩家武器时背包全满，未能归还 slot={} item={}", originalSlot, original.getItem()); } catch (Throwable ignored) {}
        }
        if (needSync) {
            try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 强制把服务端的「选中热栏槽」同步给客户端 + 把背包内容同步给客户端
     * （直接修改 inventory.selected 字段是服务端-only，客户端下一个 tick 会把用户鼠标选择的值重新写回覆盖，必须发包）
     * <p>方案A节流：SetCarriedItem 每 tick 发（2B 轻量），broadcastChanges 每 5 tick 发一次（差异包）。
     * 删除了全量 ClientboundContainerSetContentPacket——broadcastChanges 已能同步槽内容变化。
     */
    private void syncHotbarSlot(@NotNull ServerPlayer sp, int slot0to8) {
        int safe = Math.max(0, Math.min(8, slot0to8));
        if (sp.getInventory().selected != safe) sp.getInventory().selected = safe;
        try {
            // 1) SetCarriedItem 每 tick 发（~2B，轻量），确保客户端选中槽被立刻拉回
            sp.connection.send(new ClientboundSetCarriedItemPacket(safe));
        } catch (Throwable ignored) {}
        // 2) broadcastChanges 节流：每 5 tick（250ms）发一次差异包，替代原来的全量 ContainerSetContentPacket
        if (sp.tickCount - lastSlotSyncTick >= 5) {
            lastSlotSyncTick = sp.tickCount;
            try { sp.containerMenu.broadcastChanges(); } catch (Throwable ignored) {}
        }
    }

    // ============================================================
    // 速度效果 I~IV
    // ============================================================
    private void ensureMobEffects(@NotNull ServerPlayer sp) {
        if (speedLevel >= 1) {
            int amp = Math.min(MAX_SPEED_LEVEL, speedLevel) - 1;
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, amp, false, false, false));
        }
    }

    // ============================================================
    // Server tick：冷却倒计时+技能倒计时/锁定/效果/必杀更新
    // ============================================================
    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;

        if (cooldownTicks > 0) cooldownTicks--;

        if (!skillActive) {
            sync();
            return;
        }

        lockHotbarSlot(sp);
        ensureMobEffects(sp);

        skillTicksLeft--;
        if (skillTicksLeft <= 0) {
            // 时间耗尽：给缓慢V5 3s+清军刀+进入60s 冷却
            forceEndSkill(true, true, true);
            return;
        }

        updateExecutionFlag(sp);

        if ((skillTicksLeft % 20) == 0) {
            // 与 HUD 一致：向上取整（HUD 用 (skillTicksLeft + 19) / 20）
            int secs = (skillTicksLeft + 19) / 20;
            int alive = countAlivePlayers(sp.serverLevel());
            int need = Math.max(1, alive / 2);
            int hit = hitPlayersThisSession.size();
            // 与 HUD 一致：术语统一为「处决就绪」/「处决进度」
            String execText = nextHitIsExecution
                    ? "§c§l⚔ 处决就绪§f"
                    : "§7处决进度：§e" + hit + "/" + need + "§f";
            String cdTxt = (cooldownTicks > 0) ? (" §8| 冷却：§e" + (cooldownTicks + 19) / 20 + "s") : "";
            LegionSendActionBar(sp, String.format(
                    "§6§l野性狂暴 §f剩余 §6%d 秒§f | 速度 §b%d§f | %s%s",
                    secs, speedLevel, execText, cdTxt
            ));
        } else if (cooldownTicks > 0 && cooldownTicks % 20 == 0) {
            // 冷却秒数提示（不是每秒发：冷却中不发
        }

        sync();
    }

    private void lockHotbarSlot(@NotNull ServerPlayer sp) {
        // 1) 强制选中锁定的那格热栏
        int slot = Math.max(0, Math.min(8, lockedHotbarSlot));
        sp.getInventory().selected = slot;

        // 【关键】每 tick 重新存一遍热栏其他 8 格里出现的武器：
        //   玩家如果在"tick 中间"从地上捡了刀/枪 / 从大背包点击把枪/普通刀移回热栏（或快速切），
        //   这里下一个 tick 立刻把它们存进 tempStoredHotbarWeapons（未存过的原武器）或直接清掉（玩家又放回来的），配合切回军刀槽，杜绝用其他武器打人
        storeWeaponsFromHotbar(sp, slot);

        // 2) 若该格不是军刀 → 再补一把军刀（此时玩家可能把军刀消耗/挪走了）
        //    注意：若该格有别的物品，这里会先把它原样移到玩家背包其他热栏空位（TMM 只能看到 0~8），没其他热栏空位就先不补（绝不覆盖玩家原物品）
        ItemStack cur = sp.getInventory().getItem(slot);
        boolean needsSync = false;
        if (!cur.is(KNIFE_ITEM)) {
            if (!cur.isEmpty()) {
                // 尝试把该格用户自己的物品原样移到其他热栏空位（大背包玩家看不到，不考虑）
                int empty = findFirstEmptyHotbarExcept(sp, slot);
                if (empty < 0) empty = findFirstEmptySlotExcept(sp, slot); // 再找不到就放大背包
                if (empty >= 0) {
                    sp.getInventory().setItem(empty, cur.copy());
                    sp.getInventory().setItem(slot, ItemStack.EMPTY);
                    needsSync = true;
                } else {
                    // 真的没任何空位：物品留在原地，军刀先不补（宁可短暂缺刀也绝不覆盖玩家原物品）
                    syncHotbarSlot(sp, slot); // 就算不补刀也要强制把选中槽同步回去
                    return;
                }
            }
            ItemStack newKnife = new ItemStack(KNIFE_ITEM);
            try { KillerKnifeDurability.applyFreshDurability(newKnife); } catch (Throwable ignored) {}
            sp.getInventory().setItem(slot, newKnife);
            needsSync = true;
        }
        // 3) 每 tick 都给客户端发一次"选中槽必须是这个"，避免客户端通过按键 1-9 / 鼠标滚轮切到其他格（最关键的一行）
        //    发包本身流量很小（每个包 2 字节），每 tick 一次约 20/s，对带宽影响可忽略
        syncHotbarSlot(sp, slot);
        if (needsSync) {
            try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
        }
    }

    /** 在热栏 0~8 中找除了 exceptSlot 之外第一个空槽；找不到返回 -1（TMM 玩家只看得热栏，所以优先考虑） */
    private static int findFirstEmptyHotbarExcept(@NotNull ServerPlayer sp, int exceptSlot) {
        for (int i = 0; i <= 8; i++) {
            if (i == exceptSlot) continue;
            if (sp.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** 在玩家 36 格背包中找除了 exceptSlot 之外第一个空槽；没找到返回 -1 */
    private static int findFirstEmptySlotExcept(@NotNull ServerPlayer sp, int exceptSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == exceptSlot) continue;
            if (sp.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private void updateExecutionFlag(ServerPlayer sp) {
        int alive = countAlivePlayers(sp.serverLevel());
        int need = Math.max(1, alive / 2);
        int hit = hitPlayersThisSession.size();
        boolean before = nextHitIsExecution;
        nextHitIsExecution = (hit >= need);
        // 达标瞬间：给军团玩家加一层 SRE 原生护盾（仅本次技能期间存在，forceEndSkill/clear 会统一收回来）
        if (nextHitIsExecution && !before) {
            grantLegionShieldIfNeeded(sp);
        }
    }

    public static int countAlivePlayers(ServerLevel serverLevel) {
        int[] c = {0};
        serverLevel.getPlayers(p -> true).forEach(p -> { if (GameUtils.isPlayerAliveAndSurvival(p)) c[0]++; });
        return c[0];
    }

    // ============================================================
    // 刀类命中判定（军团技能期：军刀/普通刀攻击玩家都是同种效果）
    // ============================================================

    /** 判断 ItemStack 是否是军团认可的刀：技能期军刀(sp_knife) 和 商店普通刀(trainmurdermystery:knife) 是同种效果 */
    public static boolean isLegionKnifeStack(@NotNull ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(KNIFE_ITEM)) return true;
        Item common = getCommonKnifeItem();
        return common != null && common != Items.AIR && stack.is(common);
    }

    private static Item getCommonKnifeItem() {
        if (COMMON_KNIFE_ITEM_CACHE != null) return COMMON_KNIFE_ITEM_CACHE;
        try {
            COMMON_KNIFE_ITEM_CACHE = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("trainmurdermystery:knife")
            );
        } catch (Throwable ignored) { COMMON_KNIFE_ITEM_CACHE = Items.AIR; }
        return COMMON_KNIFE_ITEM_CACHE;
    }

    /**
     * 军团技能期「命中回调」的真正处理入口（SRE 全局 OnKillPlayerTriggered 拦截到「目标即将死」那一刻调用）
     *
     * @return 与 SRE KnifeStabPayload.onUseKnifeHit 语义一致：
     *         false 表示阻止 SRE 默认击杀（交给我们自己的深度伤口/必杀流程）；
     *         true  表示交给 SRE 默认处理（技能未激活时）。
     */
    public boolean onKnifeAttackPlayer(@NotNull ServerPlayer attacker, @NotNull ServerPlayer target) {
        if (!skillActive) return true;
        if (!GameUtils.isPlayerAliveAndSurvival(target)) return true;
        if (attacker.getUUID().equals(target.getUUID())) return true;

        // 攻击节流：对同一目标 10 tick 内只处理一次，避免挥刀动画连发多次叠加
        ServerLevel sl = attacker.serverLevel();
        long now = sl.getGameTime();
        if (lastHitTargetUuid != null && lastHitTargetUuid.equals(target.getUUID())) {
            if (now - lastHitGameTime < KNIFE_HIT_COOLDOWN_TICKS) {
                return false; // 冷却中：这次不处理，且阻止默认刀的击杀
            }
        }
        lastHitTargetUuid = target.getUUID();
        lastHitGameTime = now;

        // 刷新技能持续时间（每次命中 重置回 15s）
        skillTicksLeft = Math.max(skillTicksLeft, DEFAULT_DURATION_TICKS);

        // 先取 target 当前的深度伤口状态（施加之前）
        DeepWoundPlayerComponent dw = ModComponents.DEEP_WOUND.get(target);
        boolean wasWounded = dw.active;

        // 如果「累计击中达到存活数/2」+「本目标已处于深度伤口」→ 直接走一击必杀（用户新规则：一击必杀只对深度伤口玩家有效，对无伤口玩家就是普通施加）
        // 注意：这里用施加前的 wasWounded 判，施加后判断下一次的击杀
        updateExecutionFlag(attacker);
        boolean canExecuteThis = nextHitIsExecution && wasWounded;

        // 施加 / 刷新深度伤口（哪怕是「一击必杀」的目标，也要先刷一下伤口，之后再杀）
        dw.apply(attacker);
        dw.sync(target);

        // 击中已经带伤口的玩家 → 立即结束技能（一击必杀例外：那是有奖励的击杀，不是"误伤旧伤口"惩罚）
        if (wasWounded && !canExecuteThis) {
            LegionSendActionBar(attacker, "§c你击中了已处于深度伤口的玩家 → 野性狂暴强制结束！");
            forceEndSkill(true, true, true);
            return false;
        }

        // 速度 +1：用户要求改规则——「只要目标被击中这一刻处于『未处于深度伤口』就加速」
        //   - 施加伤口前 wasWounded=false → 这名玩家是本技能第一次给他上伤口（或他蹲够 10s 自己解了又被你再上）→ 加速
        //   - wasWounded=true 且 !canExecuteThis → 上面已经走了强制结束；剩下的 canExecuteThis 是处决，不叠加速
        if (!wasWounded && !canExecuteThis) {
            // 注意：为了避免玩家反复把同一个蹲解伤口的玩家拿来刷速度，
            //       这里保留 hitPlayersThisSession 唯一性（同一个玩家最多给一次速度）
            if (hitPlayersThisSession.add(target.getUUID())) {
                speedLevel = Math.min(MAX_SPEED_LEVEL, speedLevel + 1);
                ensureMobEffects(attacker);
            }
        }

        // 「一击必杀」仅对处于深度伤口状态的玩家生效（用户新规则）：直接处决、击杀后立即结束技能
        if (canExecuteThis) {
            ResourceLocation exeReason = ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "legion_execution");
            boolean prev = EXECUTING_KILL_NOW.get();
            try {
                EXECUTING_KILL_NOW.set(true);
                GameUtils.killPlayer(target, true, attacker, exeReason, true);
            } finally {
                EXECUTING_KILL_NOW.set(prev);
            }
            LegionSendActionBar(attacker, "§4§l处决成功！§c已击杀处于深度伤口的目标玩家，野性狂暴结束");
            forceEndSkill(true, true, true);
            return false;
        }

        // 普通命中（对无伤口玩家施加 / 刷新了伤口）：给个反馈，阻止默认一刀死
        String speedTxt = "速度 " + toRoman(speedLevel);

        // 苏西/乔伊/冒烟的混音带：击中目标的这一刻，给 36 格内所有玩家（含攻击者本人）加对应的群体效果
        applyHitAoeTapes(attacker);

        if (nextHitIsExecution) {
            // 累计数已达标：提示玩家「下一次击中带伤口玩家可以直接处决」
            LegionSendActionBar(attacker, "§6击中 §f" + target.getGameProfile().getName() + " §6→ 施加深度伤口 §a(" + speedTxt + ") §8| §4⚔ 下一击命中【深度伤口玩家】可处决");
        } else {
            LegionSendActionBar(attacker, "§6击中 §f" + target.getGameProfile().getName() + " §6→ 施加深度伤口 §a(" + speedTxt + ")");
        }
        sync();
        return false;
    }

    // ============================================================
    // 3 张"命中后 16 格范围"混音带的 AoE 处理
    // ============================================================
    private static final int TAPE_AOE_BLOCKS = 16;
    private static final int TAPE_AOE_TICKS_SLOW = 3 * 20;    // 乔伊：3 秒缓慢 I
    private static final int TAPE_AOE_TICKS_BLIND = 1 * 20;  // 冒烟：用户要求从 3 秒改为 1 秒失明
    private static final float TAPE_SAN_DRAIN_20_OF_TOTAL = 0.20f; // 苏西：每人降低 总 san 值的 20%（mood 最大 1.0 = 100%）

    private void applyHitAoeTapes(@NotNull ServerPlayer attacker) {
        if (!tapeSusiePurchased && !tapeJoeyPurchased && !tapeSmokePurchased) return;
        ServerLevel level = attacker.serverLevel();
        final UUID attackerUuid = attacker.getUUID(); // 用户：乔伊 / 冒烟（苏西也一并）AoE 排除自己，不让军团吃自己的 debuff
        List<ServerPlayer> affected = level.getPlayers(p ->
                GameUtils.isPlayerAliveAndSurvival(p)
                        && !p.getUUID().equals(attackerUuid)
                        && p.distanceTo(attacker) <= TAPE_AOE_BLOCKS
        );
        if (affected.isEmpty()) return;
        for (ServerPlayer p : affected) {
            try {
                if (tapeSusiePurchased) {
                    SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(p);
                    if (mood != null) {
                        float cur = mood.getMood();
                        // 用户要求"降低总 san 值的 20%"→ 绝对值扣减：mood 最大 1.0=100%，所以固定 -0.20，最小保留 0
                        float next = Math.max(0.0f, cur - TAPE_SAN_DRAIN_20_OF_TOTAL);
                        mood.setMood(next);
                    }
                }
                if (tapeJoeyPurchased) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TAPE_AOE_TICKS_SLOW, 0, false, false, true));
                }
                if (tapeSmokePurchased) {
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TAPE_AOE_TICKS_BLIND, 0, false, false, true));
                }
            } catch (Throwable ignored) {
                // 单个玩家的效果异常绝不影响其他玩家
            }
        }
    }

    private static String toRoman(int lvl) {
        return switch (lvl) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(lvl);
        };
    }

    public static void LegionSendActionBar(@NotNull ServerPlayer sp, String text) {
        sp.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }

    public void sync() {
        ModComponents.LEGION.sync(player);
    }

    // ============================================================
    // NBT
    // ============================================================
    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        skillActive = tag.getBoolean("LegionSkillActive");
        skillTicksLeft = tag.getInt("LegionSkillTicks");
        cooldownTicks = tag.getInt("LegionCd");
        speedLevel = tag.getInt("LegionSpeedLvl");
        nextHitIsExecution = tag.getBoolean("LegionNextExec");
        lockedHotbarSlot = tag.getInt("LegionLockSlot");
        tapeFrankPurchased = tag.getBoolean("LegTapeFrank");
        tapeJollyPurchased = tag.getBoolean("LegTapeJolly");
        tapeSusiePurchased = tag.getBoolean("LegTapeSusie");
        tapeJoeyPurchased  = tag.getBoolean("LegTapeJoey");
        tapeSmokePurchased = tag.getBoolean("LegTapeSmoke");
        shieldGrantedThisSession = false; // 护盾是技能期临时给的，跨读档绝不保留，避免复活/断线重连白嫖
        lastSreArmorRef = -1;
        hitPlayersThisSession.clear();
        if (tag.contains("LegionHit", ListTag.TAG_LIST)) {
            ListTag list = tag.getList("LegionHit", StringTag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try { hitPlayersThisSession.add(UUID.fromString(list.getString(i))); } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putBoolean("LegionSkillActive", skillActive);
        tag.putInt("LegionSkillTicks", skillTicksLeft);
        tag.putInt("LegionCd", cooldownTicks);
        tag.putInt("LegionSpeedLvl", speedLevel);
        tag.putBoolean("LegionNextExec", nextHitIsExecution);
        tag.putInt("LegionLockSlot", lockedHotbarSlot);
        tag.putBoolean("LegTapeFrank", tapeFrankPurchased);
        tag.putBoolean("LegTapeJolly", tapeJollyPurchased);
        tag.putBoolean("LegTapeSusie", tapeSusiePurchased);
        tag.putBoolean("LegTapeJoey",  tapeJoeyPurchased);
        tag.putBoolean("LegTapeSmoke", tapeSmokePurchased);
        ListTag list = new ListTag();
        for (UUID u : hitPlayersThisSession) list.add(StringTag.valueOf(u.toString()));
        tag.put("LegionHit", list);
    }
}
