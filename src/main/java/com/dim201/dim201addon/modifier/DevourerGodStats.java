package com.dim201.dim201addon.modifier;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

/**
 * 神明吞噬者时刻 - 玩家状态快照
 *
 * <p>原理：镜像 SRE 难民（Refugee）的 PlayerStatsBeforeRefugee。
 * 当神明吞噬者复活时，记录所有存活玩家的状态（位置/背包/金币/心情/护盾）；
 * 当神明吞噬者时刻结束（死亡或超时）后，恢复所有玩家到快照状态。
 *
 * <p>记录字段：
 * <ul>
 *   <li>pos：玩家位置（傀儡位置优先）</li>
 *   <li>money：金币余额</li>
 *   <li>inventory：背包 NBT 快照</li>
 *   <li>rotation：玩家朝向（pitch, yaw）</li>
 *   <li>isAlive：是否存活</li>
 *   <li>mood：心情值</li>
 *   <li>shieldAmount：护盾层数</li>
 * </ul>
 */
public record DevourerGodStats(Vec3 pos, int money, ListTag inventory, Vec2 rotation, boolean isAlive,
                               float mood, int shieldAmount) {

    /**
     * 从玩家保存状态快照
     *
     * @param player  目标玩家
     * @param isAlive 是否存活
     * @return 状态快照 record
     */
    public static DevourerGodStats SaveFromPlayer(ServerPlayer player, boolean isAlive) {
        var inventory = player.getInventory();
        ListTag listTag = new ListTag();
        inventory.save(listTag);

        var shopComponent = SREPlayerShopComponent.KEY.get(player);
        var moodComponent = SREPlayerMoodComponent.KEY.get(player);
        int armorAmount = SREArmorPlayerComponent.KEY.get(player).getArmor();

        Vec3 pos = player.position();
        Level level = player.level();
        // 傀儡位置优先（与 PlayerStatsBeforeRefugee 一致）
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas != null) {
            var puppeteerBodyEntities = level.getEntitiesOfClass(PuppeteerBodyEntity.class, areas.getPlayArea());
            for (PuppeteerBodyEntity puppeteerBodyEntity : puppeteerBodyEntities) {
                if (puppeteerBodyEntity.getOwner() == player) {
                    pos = puppeteerBodyEntity.position();
                    break;
                }
            }
        }

        return new DevourerGodStats(pos,
                shopComponent.balance, listTag.copy(), player.getRotationVector(),
                isAlive, moodComponent.getMood(), armorAmount);
    }

    /**
     * 将状态快照恢复到玩家
     *
     * @param player 目标玩家
     * @param stats  状态快照
     */
    public static void LoadToPlayer(ServerPlayer player, DevourerGodStats stats) {
        if (stats == null) return;
        if (!stats.isAlive()) return;

        // 恢复背包
        player.getInventory().clearContent();
        player.getInventory().load(stats.inventory());

        // 清除运动状态
        player.setSwimming(false);
        player.stopRiding();
        player.stopFallFlying();
        player.stopSleeping();
        player.stopUsingItem();
        player.setShiftKeyDown(false);
        player.setCamera(player);

        // 恢复护盾
        SREArmorPlayerComponent armorComp = SREArmorPlayerComponent.KEY.get(player);
        armorComp.armor = stats.shieldAmount();

        // 恢复游戏模式（如果玩家已死亡则切回冒险模式）
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
            player.setGameMode(GameType.ADVENTURE);
        }

        // 恢复位置和朝向
        player.teleportTo(stats.pos().x, stats.pos().y, stats.pos().z);
        player.setPos(stats.pos());
        player.setXRot(stats.rotation().x);
        player.setYRot(stats.rotation().y);

        // 恢复金币和心情
        var shopComponent = SREPlayerShopComponent.KEY.get(player);
        var moodComponent = SREPlayerMoodComponent.KEY.get(player);
        shopComponent.balance = stats.money();
        moodComponent.setMood(stats.mood());
        shopComponent.sync();
        moodComponent.sync();
    }
}
