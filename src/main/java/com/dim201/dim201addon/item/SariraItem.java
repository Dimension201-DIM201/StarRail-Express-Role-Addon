package com.dim201.dim201addon.item;

import com.dim201.dim201addon.DaoLeirenPlayerComponent;
import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.role.DaoLeirenRole;
import com.dim201.dim201addon.sound.Dim201ModSounds;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 舍利子
 *
 * <p>蕾忍死亡后掉落（每局限1个，蕾忍限掉1次）。
 * <p>只能被好人阵营拾取（由 SariraPickupMixin 拦截拾取）。
 * <p>蕾忍死亡120秒后，手持舍利子右键可在死亡点复活蕾忍并播放音频。
 *
 * <p>NBT 存储：daoLeirenUUID / deathX / deathY / deathZ / deathDimension / deathGameTime
 */
public class SariraItem extends Item {

    /** 蕾忍死亡后需等待的复活时间（tick）= 120秒 × 20 */
    public static final long RESURRECT_DELAY_TICKS = 120 * 20L;

    public static final String TAG_DAO_LEIREN_UUID = "dao_leiren_uuid";
    public static final String TAG_DEATH_X = "death_x";
    public static final String TAG_DEATH_Y = "death_y";
    public static final String TAG_DEATH_Z = "death_z";
    public static final String TAG_DEATH_DIMENSION = "death_dimension";
    public static final String TAG_DEATH_GAME_TIME = "death_game_time";
    public static final String TAG_DAO_LEIREN_NAME = "dao_leiren_name";

    public SariraItem(Properties properties) {
        super(properties);
    }

    /** 创建一枚舍利子物品栈，写入蕾忍死亡信息 */
    public static ItemStack create(ServerPlayer daoLeiren, long deathGameTime) {
        ItemStack stack = new ItemStack(com.dim201.dim201addon.item.Dim201ModItems.SARIRA);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§d舍利子").withStyle(ChatFormatting.LIGHT_PURPLE));

        CompoundTag data = new CompoundTag();
        data.putUUID(TAG_DAO_LEIREN_UUID, daoLeiren.getUUID());
        data.putString(TAG_DAO_LEIREN_NAME, daoLeiren.getName().getString());
        data.putDouble(TAG_DEATH_X, daoLeiren.getX());
        data.putDouble(TAG_DEATH_Y, daoLeiren.getY());
        data.putDouble(TAG_DEATH_Z, daoLeiren.getZ());
        data.putString(TAG_DEATH_DIMENSION, daoLeiren.level().dimension().location().toString());
        data.putLong(TAG_DEATH_GAME_TIME, deathGameTime);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("§d§o蕾忍的遗物").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("§7仅好人可拾取"));
        tooltip.add(Component.literal("§7持有者死亡后会掉落在地，可转交他人"));
        tooltip.add(Component.literal("§7右键：在死亡点复活蕾忍（需等待120秒）"));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        // 读取舍利子数据
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            sp.displayClientMessage(Component.literal("§c无效的舍利子"), true);
            return InteractionResultHolder.fail(stack);
        }
        CompoundTag tag = cd.copyTag();
        if (!tag.hasUUID(TAG_DAO_LEIREN_UUID)) {
            sp.displayClientMessage(Component.literal("§c无效的舍利子"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 校验使用者为好人阵营
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) {
            sp.displayClientMessage(Component.literal("§c当前没有进行中的对局"), true);
            return InteractionResultHolder.fail(stack);
        }
        var userRole = gw.getRole(sp);
        if (userRole == null || !userRole.isInnocent()) {
            sp.displayClientMessage(Component.literal("§c只有好人阵营才能使用舍利子"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 查找蕾忍玩家
        UUID daoLeirenUuid = tag.getUUID(TAG_DAO_LEIREN_UUID);
        MinecraftServer server = sp.getServer();
        if (server == null) return InteractionResultHolder.fail(stack);
        ServerPlayer daoLeiren = server.getPlayerList().getPlayer(daoLeirenUuid);
        if (daoLeiren == null) {
            sp.displayClientMessage(Component.literal("§c蕾忍不在线"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 检查蕾忍是否确实处于死亡（旁观者）状态
        if (!daoLeiren.isSpectator() && daoLeiren.isAlive()) {
            sp.displayClientMessage(Component.literal("§c蕾忍仍然存活！"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 检查是否已过120秒
        long deathGameTime = tag.getLong(TAG_DEATH_GAME_TIME);
        long currentGameTime = sp.serverLevel().getGameTime();
        long elapsed = currentGameTime - deathGameTime;
        if (elapsed < RESURRECT_DELAY_TICKS) {
            long remainSec = (RESURRECT_DELAY_TICKS - elapsed + 19) / 20;
            sp.displayClientMessage(Component.literal("§c舍利子尚未充能，还需 " + remainSec + " 秒"), true);
            return InteractionResultHolder.fail(stack);
        }

        // ===== 复活蕾忍 =====
        double dx = tag.getDouble(TAG_DEATH_X);
        double dy = tag.getDouble(TAG_DEATH_Y);
        double dz = tag.getDouble(TAG_DEATH_Z);

        // 使用 SRE 原生复活方法：清除死亡惩罚、传送、设置冒险模式
        GameUtils.revivePlayer(daoLeiren, dx, dy, dz);
        // 恢复满血
        daoLeiren.setHealth(daoLeiren.getMaxHealth());
        // 清除负面效果
        daoLeirenRemoveNegativeEffects(daoLeiren);

        // 重置蕾忍组件状态（复活后重新开始）
        DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(daoLeiren);
        if (comp != null) {
            comp.onRevive();
            comp.sync();
        }

        // 播放复活音频：让所有玩家都能听见（无视距离）
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(Dim201ModSounds.DAO_LEIREN_RESURRECT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // 提示
        String daoLeirenName = tag.getString(TAG_DAO_LEIREN_NAME);
        sp.displayClientMessage(Component.literal("§d§l舍利子的光芒复活了 " + daoLeirenName + "！"), true);
        daoLeiren.displayClientMessage(Component.literal("§d§l你被舍利子复活了！"), true);

        // 广播给所有玩家
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == sp || p == daoLeiren) continue;
            p.displayClientMessage(Component.literal("§d§l" + daoLeirenName + " 从死亡中归来了！"), false);
        }

        // 消耗舍利子
        stack.shrink(1);
        if (stack.isEmpty()) {
            player.setItemInHand(usedHand, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
        try { sp.inventoryMenu.broadcastChanges(); } catch (Throwable ignored) {}

        LOGGER_info("舍利子复活蕾忍: " + daoLeirenName);
        return InteractionResultHolder.success(stack);
    }

    /** 清除蕾忍身上的负面效果 */
    private static void daoLeirenRemoveNegativeEffects(ServerPlayer player) {
        player.getActiveEffects().stream()
            .filter(e -> !e.getEffect().value().isBeneficial())
            .toList()
            .forEach(e -> player.removeEffect(e.getEffect()));
    }

    private static void LOGGER_info(String msg) {
        Dim201Addon.LOGGER.info("[DaoLeiren] " + msg);
    }
}
