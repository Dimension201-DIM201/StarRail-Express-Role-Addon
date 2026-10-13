package com.dim201.dim201addon;

import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 武器大师的玩家组件：管理四种虚拟零件的计数与获取机制。
 *
 * <p>零件类型：
 * <ul>
 *   <li>木质零件（wood）—— 每隔 30s 随机获取、完成任务获取</li>
 *   <li>金属零件（metal）—— 每隔 30s 随机获取、完成任务获取</li>
 *   <li>火药（gunpowder）—— 每隔 30s 随机获取、完成任务获取</li>
 *   <li>精密构件（precision）—— 仅通过撬棍破门获取</li>
 * </ul>
 *
 * <p>零件是虚拟数据，不注册为实际物品，通过 HUD 显示。
 *
 * <p>说明：
 *  - 不实现 ServerTickingComponent，改用 tick(ServerPlayer)，由 WeaponMasterRole.handleServerTick
 *    在 isRole(WEAPON_MASTER) 时调用（与 Saki/Reimu/Koishi 等组件保持一致，避免项目内两种 tick 范式混用；
 *    同时避免 CCA 框架对所有玩家每 tick 调用 serverTick 而误把零件发给非武器大师角色）
 */
public class WeaponMasterPlayerComponent implements AutoSyncedComponent {

    public static final int PART_TIMER_TICKS = 30 * 20;  // 30 秒
    private static final int MAX_PARTS = 99;  // 每种零件上限

    // 四种零件计数
    public int woodParts = 0;
    public int metalParts = 0;
    public int gunpowder = 0;
    public int precisionParts = 0;

    // 零件获取计时器
    public int partTimer = PART_TIMER_TICKS;

    // 上次随机获得的零件类型（0=木质 1=金属 2=火药，-1=尚未获得过），用于"不重复"机制
    private int lastGrantedType = -1;

    private final Player player;

    public WeaponMasterPlayerComponent(@NotNull Player player) {
        this.player = player;
    }

    // ===== 获取零件 =====

    /** 每 30 秒随机获得一种非精密零件 */
    private void tickPartTimer() {
        if (--partTimer <= 0) {
            partTimer = PART_TIMER_TICKS;
            grantRandomNonPrecision();
        }
    }

    /** 完成任务时调用：随机获得一种非精密零件 */
    public void onTaskComplete() {
        grantRandomNonPrecision();
    }

    /** 撬棍破门成功时调用：获得 1 个精密构件 */
    public void onDoorBroken() {
        if (precisionParts < MAX_PARTS) {
            precisionParts++;
            sync();
            player.displayClientMessage(Component.literal("§b§l[武器大师] §f获得精密构件 §b×1"), true);
        }
    }

    /**
     * 随机给予木质/金属/火药其中一种。
     * 不重复机制：排除上一次获得的类型，从剩余两种中随机（计时器与任务共用此记录）。
     */
    private void grantRandomNonPrecision() {
        int type;
        if (lastGrantedType < 0) {
            type = ThreadLocalRandom.current().nextInt(3);
        } else {
            type = ThreadLocalRandom.current().nextInt(2);
            if (type >= lastGrantedType) type++; // 跳过上次类型
        }
        lastGrantedType = type;
        String name;
        switch (type) {
            case 0 -> { if (woodParts < MAX_PARTS) { woodParts++; name = "木质零件"; } else return; }
            case 1 -> { if (metalParts < MAX_PARTS) { metalParts++; name = "金属零件"; } else return; }
            default -> { if (gunpowder < MAX_PARTS) { gunpowder++; name = "火药"; } else return; }
        }
        sync();
        player.displayClientMessage(Component.literal("§b§l[武器大师] §f获得 §e" + name + " §f×1"), true);
    }

    // ===== 消耗零件（商店购买时调用） =====

    /**
     * 检查是否有足够零件
     * @param wood 需要的木质零件数
     * @param metal 需要的金属零件数
     * @param gp 需要的火药数
     * @param prec 需要的精密构件数
     */
    public boolean hasEnough(int wood, int metal, int gp, int prec) {
        return woodParts >= wood && metalParts >= metal && gunpowder >= gp && precisionParts >= prec;
    }

    /**
     * 消耗零件（不检查，调用前应先 hasEnough）
     */
    public void consume(int wood, int metal, int gp, int prec) {
        woodParts -= wood;
        metalParts -= metal;
        gunpowder -= gp;
        precisionParts -= prec;
        sync();
    }

    // ===== 生命周期 =====

    public void clear() {
        woodParts = 0;
        metalParts = 0;
        gunpowder = 0;
        precisionParts = 0;
        partTimer = PART_TIMER_TICKS;
        lastGrantedType = -1;
        sync();
    }

    public void init() {
        clear();
    }

    // ===== CCA 序列化 =====

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        woodParts = tag.getInt("wood");
        metalParts = tag.getInt("metal");
        gunpowder = tag.getInt("gp");
        precisionParts = tag.getInt("prec");
        partTimer = tag.getInt("timer");
        lastGrantedType = tag.contains("last_type") ? tag.getInt("last_type") : -1;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("wood", woodParts);
        tag.putInt("metal", metalParts);
        tag.putInt("gp", gunpowder);
        tag.putInt("prec", precisionParts);
        tag.putInt("timer", partTimer);
        tag.putInt("last_type", lastGrantedType);
    }

    /**
     * 由 WeaponMasterRole.handleServerTick 在 isRole(WEAPON_MASTER) 判定通过后调用。
     * 仅武器大师角色才会推进零件计时器，避免被动零件误发给其他角色。
     */
    public void tick(ServerPlayer sp) {
        // 仅在对局进行中计时（角色回调入口已做 isRole 判断，此处只兜底对局状态）
        var gw = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(sp.level());
        if (gw == null || !gw.isRunning()) return;
        tickPartTimer();
        // partTimer 每 tick 递减但不会自动同步到客户端，
        // 若不 sync，客户端 comp.partTimer 保持初值不变，HUD 倒计时会卡在 0s。
        // 每秒（20 tick）同步一次，确保客户端实时看到递减，同时避免每 tick 同步浪费带宽。
        if (sp.tickCount % 20 == 0) {
            sync();
        }
    }

    public void sync() {
        ModComponents.WEAPON_MASTER.sync(player);
    }
}
