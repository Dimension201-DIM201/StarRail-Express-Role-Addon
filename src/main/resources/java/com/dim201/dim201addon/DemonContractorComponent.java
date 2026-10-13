package com.dim201.dim201addon;

import com.dim201.dim201addon.contract.ContractCost;
import com.dim201.dim201addon.contract.ContractPower;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.EnumSet;
import java.util.Set;

/**
 * 恶魔契约者组件
 *
 * <p>承载契约状态：已获得的力量、永久烙印、契约次数、冲刺冷却、
 * 临时发光到期时间、清心 san 基准、利息/周期暴露计时、血誓标记。
 *
 * <p>状态由 {@code DemonContractManager} 读写，客户端只读同步副本
 * （HUD 与直觉高亮事件使用）。
 */
public class DemonContractorComponent implements RoleComponent {

    public static final ComponentKey<DemonContractorComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "demon_contractor"),
            DemonContractorComponent.class);

    private static final String TAG_POWERS = "Powers";
    private static final String TAG_BRANDS = "Brands";
    private static final String TAG_COUNT = "ContractCount";
    private static final String TAG_DASH = "DashReadyAt";
    private static final String TAG_GLOW = "GlowUntilAt";
    private static final String TAG_MOOD = "LastMood";
    private static final String TAG_INTEREST = "LastInterestAt";
    private static final String TAG_EXPOSURE = "LastExposureAt";
    private static final String TAG_FEVER = "LastFeverAt";
    private static final String TAG_OATH = "BloodOathArmed";

    private final Player player;

    private final EnumSet<ContractPower> powers = EnumSet.noneOf(ContractPower.class);
    private final EnumSet<ContractCost> brands = EnumSet.noneOf(ContractCost.class);
    private int contractCount = 0;
    private long dashReadyAtTick = -1L;
    private long glowUntilTick = -1L;
    private float lastMood = 1.0F;
    private long lastInterestAt = -1L;
    private long lastExposureAt = -1L;
    private long lastFeverAt = -1L;
    private boolean bloodOathArmed = false;

    public DemonContractorComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public void init() {}

    /** 对局结束：清空全部契约状态，不带入下一局 */
    @Override
    public void clear() {
        powers.clear();
        brands.clear();
        contractCount = 0;
        dashReadyAtTick = -1L;
        glowUntilTick = -1L;
        lastMood = 1.0F;
        lastInterestAt = -1L;
        lastExposureAt = -1L;
        lastFeverAt = -1L;
        bloodOathArmed = false;
    }

    // ---------- 力量 / 烙印 ----------

    public Set<ContractPower> getPowers() { return powers; }
    public Set<ContractCost> getBrands() { return brands; }

    public boolean hasPower(ContractPower power) { return powers.contains(power); }
    public boolean hasBrand(ContractCost cost) { return brands.contains(cost); }

    public void addPower(ContractPower power) { powers.add(power); }
    public void addBrand(ContractCost cost) { brands.add(cost); }

    public int getContractCount() { return contractCount; }
    public void incrementContractCount() { contractCount++; }

    // ---------- 冲刺 ----------

    public long getDashReadyAt() { return dashReadyAtTick; }
    public void setDashReadyAt(long tick) { this.dashReadyAtTick = tick; }

    // ---------- 临时发光 ----------

    public long getGlowUntil() { return glowUntilTick; }
    public void setGlowUntil(long tick) { this.glowUntilTick = tick; }

    // ---------- 清心 san 基准 ----------

    public float getLastMood() { return lastMood; }
    public void setLastMood(float mood) { this.lastMood = mood; }

    // ---------- 利息 / 周期暴露 ----------

    public long getLastInterestAt() { return lastInterestAt; }
    public void setLastInterestAt(long tick) { this.lastInterestAt = tick; }
    public long getLastExposureAt() { return lastExposureAt; }
    public void setLastExposureAt(long tick) { this.lastExposureAt = tick; }

    public long getLastFeverAt() { return lastFeverAt; }
    public void setLastFeverAt(long tick) { this.lastFeverAt = tick; }

    // ---------- 血誓 ----------

    public boolean isBloodOathArmed() { return bloodOathArmed; }
    public void armBloodOath() { this.bloodOathArmed = true; }
    public void consumeBloodOath() { this.bloodOathArmed = false; }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    // ---------- 同步 NBT（服务端 → 客户端） ----------

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.put(TAG_POWERS, writeEnum(powers));
        tag.put(TAG_BRANDS, writeEnum(brands));
        tag.putInt(TAG_COUNT, contractCount);
        tag.putLong(TAG_DASH, dashReadyAtTick);
        tag.putLong(TAG_GLOW, glowUntilTick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        powers.clear();
        brands.clear();
        readPowerList(tag.getList(TAG_POWERS, Tag.TAG_STRING));
        readBrandList(tag.getList(TAG_BRANDS, Tag.TAG_STRING));
        contractCount = tag.getInt(TAG_COUNT);
        dashReadyAtTick = tag.getLong(TAG_DASH);
        glowUntilTick = tag.getLong(TAG_GLOW);
    }

    // ---------- 持久化 NBT（重登恢复） ----------

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.put(TAG_POWERS, writeEnum(powers));
        tag.put(TAG_BRANDS, writeEnum(brands));
        tag.putInt(TAG_COUNT, contractCount);
        tag.putLong(TAG_DASH, dashReadyAtTick);
        tag.putLong(TAG_GLOW, glowUntilTick);
        tag.putFloat(TAG_MOOD, lastMood);
        tag.putLong(TAG_INTEREST, lastInterestAt);
        tag.putLong(TAG_EXPOSURE, lastExposureAt);
        tag.putLong(TAG_FEVER, lastFeverAt);
        tag.putBoolean(TAG_OATH, bloodOathArmed);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        powers.clear();
        brands.clear();
        readPowerList(tag.getList(TAG_POWERS, Tag.TAG_STRING));
        readBrandList(tag.getList(TAG_BRANDS, Tag.TAG_STRING));
        contractCount = tag.getInt(TAG_COUNT);
        dashReadyAtTick = tag.contains(TAG_DASH) ? tag.getLong(TAG_DASH) : -1L;
        glowUntilTick = tag.contains(TAG_GLOW) ? tag.getLong(TAG_GLOW) : -1L;
        lastMood = tag.contains(TAG_MOOD) ? tag.getFloat(TAG_MOOD) : 1.0F;
        lastInterestAt = tag.contains(TAG_INTEREST) ? tag.getLong(TAG_INTEREST) : -1L;
        lastExposureAt = tag.contains(TAG_EXPOSURE) ? tag.getLong(TAG_EXPOSURE) : -1L;
        lastFeverAt = tag.contains(TAG_FEVER) ? tag.getLong(TAG_FEVER) : -1L;
        bloodOathArmed = tag.getBoolean(TAG_OATH);
    }

    private static ListTag writeEnum(Set<?> set) {
        ListTag list = new ListTag();
        for (Object o : set) {
            // 统一用稳定字符串 id（不用枚举 name()），配合 byId 容错读取
            if (o instanceof ContractPower p) {
                list.add(StringTag.valueOf(p.id()));
            } else if (o instanceof ContractCost c) {
                list.add(StringTag.valueOf(c.id()));
            }
        }
        return list;
    }

    private void readPowerList(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            ContractPower p = ContractPower.byId(list.getString(i));
            if (p != null) powers.add(p);
        }
    }

    private void readBrandList(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            ContractCost c = ContractCost.byId(list.getString(i));
            if (c != null) brands.add(c);
        }
    }
}
