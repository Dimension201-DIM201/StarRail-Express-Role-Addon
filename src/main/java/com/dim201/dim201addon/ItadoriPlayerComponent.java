package com.dim201.dim201addon;

import com.dim201.dim201addon.cooperation.BrotherSyncManager;
import com.dim201.dim201addon.item.card.CardTeamKillPunisher;
import com.dim201.dim201addon.role.ItadoriRole;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.UUID;

/**
 * 虎杖悠仁玩家组件
 *
 * 逕庭拳：空手连击同一目标，默认 5 次击杀；不义游戏响应后的 10 秒专注期内降为 2 次。
 * 连击没有 0.5 秒间隔限制（由 {@link ItadoriRole#registerEvents()} 的攻击事件直接计数）。
 * 好人误杀好人时由 {@link CardTeamKillPunisher} 统一执行小脑惩罚。
 */
public class ItadoriPlayerComponent implements RoleComponent {

    public static final ComponentKey<ItadoriPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "itadori"),
            ItadoriPlayerComponent.class);

    /** 默认击杀所需连击次数 */
    public static final int KILL_HITS_NORMAL = 5;
    /** 专注状态下击杀所需连击次数 */
    public static final int KILL_HITS_FOCUS = 2;

    private final Player player;

    /** 当前连击目标 */
    private UUID comboTarget;
    /** 当前连击次数 */
    private int comboCount;

    public ItadoriPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        resetCombo();
    }

    @Override
    public void clear() {
        resetCombo();
        BrotherSyncManager.resetFor(player.getUUID());
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    /** 逕庭拳命中一次（仅服务端调用） */
    public void onPunchVictim(ServerPlayer victim) {
        if (!(player instanceof ServerPlayer sp)) return;

        boolean focus = BrotherSyncManager.isFocusActive(player.getUUID(), sp.level().getGameTime());
        int threshold = focus ? KILL_HITS_FOCUS : KILL_HITS_NORMAL;

        // 目标切换 → 连击清零
        if (!victim.getUUID().equals(comboTarget)) {
            comboTarget = victim.getUUID();
            comboCount = 0;
        }
        comboCount++;

        // 每拳尝试造成 1 点伤害；但保留原版受伤无敌帧（0.5s），不绕过
        // "无连击间隔"指的是计数不重置，而非每拳都破防
        victim.hurt(sp.damageSources().playerAttack(sp), 1.0F);

        if (comboCount >= threshold) {
            resetCombo();

            // 小脑惩罚必须在 killPlayer 之前判定（镜像岚刀/燃烧刀写法）
            // 无敌不能阻止小脑惩罚：临时移除不义游戏赋予的 INVINCIBLE，
            // 若未触发惩罚（非好人杀好人）则恢复
            MobEffectInstance invincible = sp.getEffect(ModEffects.INVINCIBLE);
            if (invincible != null) {
                sp.removeEffect(ModEffects.INVINCIBLE);
            }
            boolean punished = CardTeamKillPunisher.applyIfApplicable(victim, sp, ItadoriRole.DIVERGENT_FIST_DEATH);
            if (!punished && invincible != null) {
                sp.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE,
                        invincible.getDuration(), invincible.getAmplifier(), false, true));
            }
            if (victim.isAlive()) {
                GameUtils.killPlayer(victim, true, sp, ItadoriRole.DIVERGENT_FIST_DEATH);
            }

            sp.displayClientMessage(
                    Component.literal("§c§l逕庭拳！§f" + victim.getName().getString() + " 被击溃"),
                    true);

            // 专注期击杀 → Tier3 击掌特效
            if (focus) {
                BrotherSyncManager.playTier3Fx(sp.serverLevel(),
                        victim.position().add(0, 1.0, 0));
            }
        } else {
            ChatFormatting color = comboCount == threshold - 1 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            sp.displayClientMessage(
                    Component.literal("连击 " + comboCount + "/" + threshold
                            + " §7- " + victim.getName().getString()).withStyle(color),
                    true);
        }
    }

    private void resetCombo() {
        comboTarget = null;
        comboCount = 0;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}
