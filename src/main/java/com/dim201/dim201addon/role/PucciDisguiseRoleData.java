package com.dim201.dim201addon.role;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 普奇神父 RoleData —— 两种用途、同一类、挂在不同玩家身上：
 *
 * <p><b>挂在普奇自己身上（通过 setRoleData 自动创建）：</b>
 * 仅存 {@link #disguiseCooldownTicks} 冷却计时，disguiseTarget 永远为 null。
 * 皮肤 Mixin 查普奇自己时 disguiseTarget=null，不触发替换。
 *
 * <p><b>挂在被变形者身上（通过 RoleData.getOrCreate 动态创建）：</b>
 * 仅存 {@link #disguiseTarget}（外观目标 UUID），disguiseCooldownTicks 永远为 0。
 * 皮肤 Mixin 直接查被渲染玩家自己的 RoleData → disguiseTarget 非空就替换。
 *
 * <p>为什么不存 Map（之前的方案）？
 * 旧方案把所有变形映射存在普奇身上，皮肤 Mixin 在客户端要遍历所有玩家找普奇的 RoleData。
 * 但客户端上其他玩家的 RoleData 根本不存在（setRoleData 只给角色拥有者创建），
 * 导致 getAllDisguises() 永远返回空 Map → 皮肤替换永远不触发。
 *
 * <p>新方案完全对齐 MorphlingRoleData：每个被变形者自己持有 disguiseTarget，
 * 皮肤渲染器直接查被渲染玩家 → 数据和使用者在同一个实体上，架构天然自洽。
 */
public class PucciDisguiseRoleData extends SimpleRoleData {

    /**
     * 被变形者身上：外观目标 UUID（变成谁的皮肤）。
     * null = 无变形效果。
     */
    public UUID disguiseTarget = null;

    /**
     * 普奇自己身上：「认知妨碍」冷却剩余 tick。0 = 可用。
     * 被变形者身上永远为 0，无意义。
     */
    public int disguiseCooldownTicks = 0;

    /** 冷却常量：60 秒 */
    public static final int DISGUISE_COOLDOWN = 60 * 20;

    public PucciDisguiseRoleData(RoleDataContext context) {
        super(context);
    }

    /** 普奇专用：冷却检查 */
    public boolean isDisguiseReady() {
        return disguiseCooldownTicks <= 0;
    }

    /** 普奇专用：消耗冷却（冷却检查通过后调用） */
    public void startCooldown() {
        disguiseCooldownTicks = DISGUISE_COOLDOWN;
        sync();
    }

    /** 被变形者专用：设置外观目标并同步到所有客户端 */
    public void setDisguiseTarget(UUID appearanceTarget) {
        this.disguiseTarget = appearanceTarget;
        sync();
    }

    /** 被变形者专用：清除变形 */
    public void clearDisguise() {
        this.disguiseTarget = null;
        sync();
    }

    // ===== 冷却 tick 递减（普奇自己的实例有效，被变形者身上永远是 0） =====

    @Override
    public void serverTick() {
        if (disguiseCooldownTicks > 0) {
            disguiseCooldownTicks--;
        }
    }

    // ===== NBT 序列化 =====

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (disguiseTarget != null) {
            tag.putUUID("disguiseTarget", disguiseTarget);
        }
        tag.putInt("disguiseCooldown", disguiseCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        disguiseTarget = tag.hasUUID("disguiseTarget") ? tag.getUUID("disguiseTarget") : null;
        disguiseCooldownTicks = tag.getInt("disguiseCooldown");
    }

    @Override
    public void init() {
        disguiseTarget = null;
        disguiseCooldownTicks = 0;
    }

    @Override
    public void clear() {
        disguiseTarget = null;
        disguiseCooldownTicks = 0;
    }
}
