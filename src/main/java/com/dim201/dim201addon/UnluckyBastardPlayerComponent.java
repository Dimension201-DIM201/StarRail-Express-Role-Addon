package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import com.dim201.dim201addon.item.card.CardUtils;
import com.dim201.dim201addon.role.UnluckyBastardRole;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 倒霉蛋（Unlucky Bastard）玩家组件
 * 平民阵营
 *
 * 被动能力：
 *  · 身边 10 格内有人死亡 → 强制拿起一把带 30s 冷却的刀（被栽赃）
 *  · 30s 冷却结束时若玩家存活 → 获得 75 金币补偿，同时刀被回收
 *  · 被动能力每局只触发一次
 */
public class UnluckyBastardPlayerComponent implements RoleComponent {

    public static final ComponentKey<UnluckyBastardPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "unlucky_bastard"),
            UnluckyBastardPlayerComponent.class);

    /** 被动触发范围（格） */
    public static final double TRIGGER_RANGE = 10.0;
    /** 刀的冷却时长（30s = 600 tick） */
    public static final int KNIFE_COOLDOWN_TICKS = 30 * 20;
    /** 存活补偿金币 */
    public static final int SURVIVAL_REWARD_GOLD = 75;

    private final Player player;

    // ===== 状态（按 NBT 同名保存）=====
    /** 是否已触发过被动能力（每局仅一次） */
    public boolean hasTriggered = false;
    /** 刀的剩余冷却时间，>0 表示持有冷却中的刀 */
    public int knifeCooldownTicks = 0;
    /** 被动期间锁定的热栏槽位（0~8），-1 表示未锁定 */
    public int lockedHotbarSlot = -1;
    /** 热栏同步节流 */
    private int lastSlotSyncTick = -100;

    public UnluckyBastardPlayerComponent(Player player) {
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
        hasTriggered = false;
        knifeCooldownTicks = 0;
        lockedHotbarSlot = -1;
        sync();
    }

    @Override
    public void clear() {
        hasTriggered = false;
        knifeCooldownTicks = 0;
        lockedHotbarSlot = -1;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 尝试触发被动能力（10 格内有玩家死亡时调用）
     *
     * @param victim 死亡的玩家（死亡点作为距离判定点）
     * @return true 表示成功触发（满足所有条件）
     */
    public boolean tryTriggerPassive(ServerPlayer victim) {
        if (!(player instanceof ServerPlayer sp)) return false;
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null) return false;
        // 角色与存活校验
        if (!gw.isRole(sp, UnluckyBastardRole.UNLUCKY_BASTARD)) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return false;
        // 每局只触发一次
        if (hasTriggered) return false;
        // 距离判定（10 格内）
        if (sp.distanceTo(victim) > TRIGGER_RANGE) return false;

        // ===== 触发被动 =====
        hasTriggered = true;
        knifeCooldownTicks = KNIFE_COOLDOWN_TICKS;

        // 强制给一把刀（塞到主手或背包）
        giveKnife(sp);

        // 通知
        sendActionBar(sp, "§c§l诶诶诶！这刀是谁塞在我手里的！ §730s 内未死亡可获得 §a+75 §7金币");
        sp.displayClientMessage(Component.literal("§c有人死在了你附近，你捡起了一把沾满血的刀...别让别人看到！"), false);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);

        sync();
        return true;
    }

    /**
     * 每 tick 推进冷却。由角色 tick 事件调用。
     */
    public void tick(ServerPlayer sp) {
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null) return;
        if (!gw.isRole(sp, UnluckyBastardRole.UNLUCKY_BASTARD)) return;
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) return;

        if (knifeCooldownTicks > 0) {
            knifeCooldownTicks--;

            // 强制锁定热栏槽位（参考军团：每 tick 同步选中槽给客户端）
            if (lockedHotbarSlot >= 0) {
                lockHotbarSlot(sp);
            }

            // 冷却结束
            if (knifeCooldownTicks == 0) {
                // 回收刀
                removeKnife(sp);
                // 存活 → 给金币
                CardUtils.addGold(sp, SURVIVAL_REWARD_GOLD);
                sendActionBar(sp, "§a§l清白！ §f获得 §e+" + SURVIVAL_REWARD_GOLD + " §f金币补偿，刀已被回收");
                sp.displayClientMessage(Component.literal("§a幸好你没有死，还额外获得了 " + SURVIVAL_REWARD_GOLD + " 金币的补偿！"), false);
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
                sync();
                return;
            }

            // 每 5 秒提示一次剩余时间
            int secs = knifeCooldownTicks / 20;
            if (knifeCooldownTicks % 100 == 0 && secs > 0) {
                sendActionBar(sp, "§c「栽赃」冷却 §e" + secs + "s §7活下来就能拿金币！");
            }
        }
    }

    // ====================== 辅助：给刀 / 收刀 ======================

    /**
     * 给玩家一把冷却中的刀并锁定热栏槽位（参考军团机制）
     * - 找一个热栏空位放刀，锁定该槽，每 tick 强制选中
     * - 同时给刀添加 30s 物品冷却，防止倒霉蛋用刀攻击
     */
    private void giveKnife(ServerPlayer sp) {
        ItemStack knife = buildKnifeStack();
        // 找一个热栏空位（0~8），没有就用当前选中槽
        int slot = -1;
        for (int i = 0; i <= 8; i++) {
            if (sp.getInventory().getItem(i).isEmpty()) { slot = i; break; }
        }
        if (slot < 0) slot = sp.getInventory().selected;
        // 若目标槽有物品，先移到背包其他空位
        ItemStack cur = sp.getInventory().getItem(slot);
        if (!cur.isEmpty()) {
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                if (i == slot) continue;
                if (sp.getInventory().getItem(i).isEmpty()) {
                    sp.getInventory().setItem(i, cur.copy());
                    break;
                }
            }
        }
        sp.getInventory().setItem(slot, knife);
        lockedHotbarSlot = slot;
        sp.getInventory().selected = slot;
        // 添加 30s 物品冷却（覆盖所有同类物品），防止使用刀攻击
        Item knifeItem = knife.getItem();
        sp.getCooldowns().addCooldown(knifeItem, KNIFE_COOLDOWN_TICKS);
        // 立即同步给客户端
        lastSlotSyncTick = -100;
        syncHotbarSlot(sp, slot);
        try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
    }

    /** 回收刀：从背包移除第一把匹配的倒霉蛋标记刀，并解除槽位锁定 */
    private void removeKnife(ServerPlayer sp) {
        lockedHotbarSlot = -1;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack stack = sp.getInventory().getItem(i);
            if (isOurKnife(stack)) {
                sp.getInventory().setItem(i, ItemStack.EMPTY);
                break;
            }
        }
        // 检查副手
        ItemStack off = sp.getInventory().offhand.get(0);
        if (isOurKnife(off)) {
            sp.getInventory().offhand.set(0, ItemStack.EMPTY);
        }
        try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
    }

    /**
     * 每 tick 强制锁定热栏槽位（参考军团 LegionPlayerComponent）
     * - 若锁定槽位的刀被丢掉/换掉，重新补一把
     * - 发包强制客户端选中该槽
     */
    private void lockHotbarSlot(ServerPlayer sp) {
        int slot = Math.max(0, Math.min(8, lockedHotbarSlot));
        sp.getInventory().selected = slot;
        // 若该格不是我们的刀 → 补一把
        ItemStack cur = sp.getInventory().getItem(slot);
        if (!isOurKnife(cur)) {
            if (!cur.isEmpty()) {
                // 把原物品移走
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    if (i == slot) continue;
                    if (sp.getInventory().getItem(i).isEmpty()) {
                        sp.getInventory().setItem(i, cur.copy());
                        break;
                    }
                }
            }
            sp.getInventory().setItem(slot, buildKnifeStack());
            // 重新添加物品冷却（如果刀被丢了重补）
            ItemStack knife = sp.getInventory().getItem(slot);
            if (!knife.isEmpty()) {
                sp.getCooldowns().addCooldown(knife.getItem(), knifeCooldownTicks);
            }
            try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}
        }
        syncHotbarSlot(sp, slot);
    }

    /**
     * 强制把服务端的选中热栏槽同步给客户端（参考军团）
     * - SetCarriedItem 每 tick 发（2B 轻量）
     * - broadcastChanges 每 5 tick 发一次差异包
     */
    private void syncHotbarSlot(ServerPlayer sp, int slot0to8) {
        int safe = Math.max(0, Math.min(8, slot0to8));
        if (sp.getInventory().selected != safe) sp.getInventory().selected = safe;
        try {
            sp.connection.send(new ClientboundSetCarriedItemPacket(safe));
        } catch (Throwable ignored) {}
        if (sp.tickCount - lastSlotSyncTick >= 5) {
            lastSlotSyncTick = sp.tickCount;
            try { sp.containerMenu.broadcastChanges(); } catch (Throwable ignored) {}
        }
    }

    /** 判断一个物品是否是我们给出去的那把刀（通过自定义 tag 识别） */
    private boolean isOurKnife(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag custom = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag() : null;
        return custom != null && custom.getBoolean("UnluckyBastardFrame");
    }

    /** 构建一把带标记的刀（三级回退：noellesroles → starrailexpress → trainmurdermystery） */
    private ItemStack buildKnifeStack() {
        // 三级回退，先尝试 SRE 的 knife（与商店保持一致）
        String[] ids = {"noellesroles:knife", "starrailexpress:knife", "trainmurdermystery:knife"};
        ItemStack knife = ItemStack.EMPTY;
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.parse(id);
            ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
            if (s.getItem() != Items.AIR) {
                knife = s;
                break;
            }
        }
        // 兜底：铁剑（极端情况没有刀物品）
        if (knife.getItem() == Items.AIR) {
            knife = new ItemStack(Items.IRON_SWORD);
        }
        // 名称
        knife.set(DataComponents.ITEM_NAME, Component.literal("§c§l带血的刀"));
        // 自定义 tag 标记
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("UnluckyBastardFrame", true);
        knife.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // 不能掉落也不能丢弃（让它稳稳地在玩家身上，冷却结束自动消失）
        // 这里只做普通物品，由 removeKnife 负责清除
        return knife;
    }

    // ====================== 工具 ======================

    private void sendActionBar(ServerPlayer p, String msg) {
        if (p == null) return;
        p.displayClientMessage(Component.literal(msg), true);
    }

    // ====================== NBT ======================

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        hasTriggered = tag.getBoolean("HasTriggered");
        knifeCooldownTicks = tag.getInt("KnifeCooldown");
        lockedHotbarSlot = tag.getInt("LockedSlot");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("HasTriggered", hasTriggered);
        tag.putInt("KnifeCooldown", knifeCooldownTicks);
        tag.putInt("LockedSlot", lockedHotbarSlot);
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
