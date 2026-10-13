package com.dim201.dim201addon;

import com.dim201.dim201addon.role.EmiyaRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class EmiyaPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<EmiyaPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "emiya"),
            EmiyaPlayerComponent.class);

    private final Player player;

    // 投影物品枚举
    public enum ProjectionItem {
        ONCE_REVOLVER("一次性手枪", "noellesroles:once_revolver", 30, false),
        CONDUCTOR_KEY("乘务员钥匙", "noellesroles:master_key_p", 30, false),
        IRON_DOOR_KEY("铁门钥匙", "trainmurdermystery:iron_door_key", 30, false),
        MASTER_KEY("万能钥匙", "noellesroles:master_key", 90, false),
        CROWBAR("撬棍", "trainmurdermystery:crowbar", 30, false),
        ENVELOPE("绳索", "noellesroles:rope", 30, false),
        EXERCISE_BOOK("习题集", "noellesroles:problem_set", 60, false),
        TIME_STOP_CLOCK("时停钟", "noellesroles:time_stop_clock", 240, true);

        public final String displayName;
        public final String itemId;
        public final int manaCost;
        public final boolean oncePerRound;

        ProjectionItem(String displayName, String itemId, int manaCost, boolean oncePerRound) {
            this.displayName = displayName;
            this.itemId = itemId;
            this.manaCost = manaCost;
            this.oncePerRound = oncePerRound;
        }

        public Item getItem() {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        }
    }

    // 魔力系统
    public static final int INITIAL_MANA = 30;
    public static final int MAX_MANA = 240;
    public static final int MANA_REGEN_INTERVAL = 20; // 20 tick = 1秒

    public int currentMana = INITIAL_MANA;
    private int manaRegenTimer = 0;

    // 当前投影物品
    public ProjectionItem currentProjection = ProjectionItem.ONCE_REVOLVER;

    // 时停钟是否已投影过（每局限一次）
    public boolean timeStopClockUsed = false;

    public EmiyaPlayerComponent(Player player) {
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
        currentMana = INITIAL_MANA;
        manaRegenTimer = 0;
        currentProjection = ProjectionItem.ONCE_REVOLVER;
        timeStopClockUsed = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    // 切换投影物品
    public void setProjection(ProjectionItem item, ServerPlayer player) {
        this.currentProjection = item;
        sendActionBar(player, "§b§l「投影魔术」 §7| §f已切换投影目标: §e" + item.displayName + " §7(消耗 " + item.manaCost + " 魔力)");
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.2F);
        sync();
    }

    // 使用投影魔术
    public boolean useProjection(ServerPlayer player) {
        if (!isEmiyaActive()) return false;

        ProjectionItem item = currentProjection;

        // 检查时停钟是否已使用过
        if (item.oncePerRound && timeStopClockUsed) {
            sendActionBar(player, "§c§l投影失败！§f时停钟每局限投影一次");
            return false;
        }

        // 检查魔力是否足够
        if (currentMana < item.manaCost) {
            sendActionBar(player, "§c§l魔力不足！§f需要 " + item.manaCost + " 点魔力，当前 " + currentMana);
            return false;
        }

        // 获取物品
        Item targetItem = item.getItem();
        if (targetItem == null || targetItem == Items.AIR) {
            sendActionBar(player, "§c§l投影失败！§f物品不存在");
            return false;
        }

        // 创建物品堆
        ItemStack stack = new ItemStack(targetItem);

        // 时停钟设置耐久为1（只能使用一次）
        if (item == ProjectionItem.TIME_STOP_CLOCK) {
            if (stack.isDamageableItem()) {
                stack.setDamageValue(stack.getMaxDamage() - 1);
            }
        }

        // 添加物品到玩家背包
        if (!player.getInventory().add(stack)) {
            // 背包满了就掉在地上
            player.drop(stack, false);
        }

        // 扣除魔力
        currentMana -= item.manaCost;

        // 标记时停钟已使用
        if (item.oncePerRound) {
            timeStopClockUsed = true;
        }

        // 播放音效和粒子效果
        if (player.serverLevel() != null) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.0F);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.2F, 1.5F);
        }

        sendActionBar(player, "§b§l「投影魔术」 §f成功投影 §e" + item.displayName + " §7(-" + item.manaCost + " 魔力)");
        player.inventoryMenu.broadcastChanges();
        sync();
        return true;
    }

    private boolean isEmiyaActive() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) return false;
        return gameWorld.isRole(player, EmiyaRole.EMIYA);
    }

    @Override
    public void serverTick() {
        if (!isEmiyaActive()) return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
        if (!(player instanceof ServerPlayer)) return;

        // 每秒恢复1点魔力
        manaRegenTimer++;
        if (manaRegenTimer >= MANA_REGEN_INTERVAL) {
            manaRegenTimer = 0;
            if (currentMana < MAX_MANA) {
                currentMana++;
                sync();
            }
        }
    }

    @Override
    public void clientTick() {
        if (!isEmiyaActive()) return;
        // 客户端魔力也自动增加，用于显示（虽然服务端才是权威，但客户端可以平滑显示）
    }

    public void sendActionBar(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    // ==================== NBT 序列化 ====================
    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        currentMana = tag.getInt("CurrentMana");
        if (tag.contains("CurrentProjection")) {
            try {
                currentProjection = ProjectionItem.valueOf(tag.getString("CurrentProjection"));
            } catch (IllegalArgumentException ignored) {
                currentProjection = ProjectionItem.ONCE_REVOLVER;
            }
        }
        timeStopClockUsed = tag.getBoolean("TimeStopClockUsed");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("CurrentMana", currentMana);
        tag.putString("CurrentProjection", currentProjection.name());
        tag.putBoolean("TimeStopClockUsed", timeStopClockUsed);
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
