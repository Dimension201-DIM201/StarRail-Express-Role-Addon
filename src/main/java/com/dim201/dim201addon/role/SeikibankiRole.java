package com.dim201.dim201addon.role;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.SeikibankiPlayerComponent;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 赤蛮奇（Seikibanki）
 * 平民阵营
 *
 * <p>背景：「葦がない。頭もない。身体中が見えない」——头颅可脱离身体自行探查的妖怪。
 *
 * <p>主动技能「飞头」（G 键，90 秒冷却）：
 * <ul>
 *   <li>激活时本体冻结在原地（可被攻击/击杀），意识进入飞出的头颅实体，相机绑定头颅</li>
 *   <li>WASD 飞行（W/S 沿视线方向，空格上升、Shift 下降），速度≈原版飞行 1/3，有碰撞不穿墙</li>
 *   <li>飞行路径留下白色粒子轨迹；头颅模型为主人本人皮肤</li>
 *   <li>头颅距本体达 20 格时被截断在边缘</li>
 *   <li>头颅被攻击时击落回归：60s 失明 + 90s 冷却；本体被击杀走正常死亡</li>
 *   <li>再按 G 主动收回：无失明，开始 90s 冷却</li>
 *   <li>飞头期间无法使用武器/道具/打开背包</li>
 *   <li>冷却 90s（1800 tick），技能结束后才开始计时</li>
 * </ul>
 */
public class SeikibankiRole {

    public static final ResourceLocation SEIKIBANKI_ID = ResourceLocation.fromNamespaceAndPath(
            Dim201Addon.MOD_ID, "seikibanki");

    public static SRERole SEIKIBANKI;

    public static void register() {
        SEIKIBANKI = TMMRoles.registerRole(new SeikibankiRoleImpl());

        // 对局重置：清空技能状态（CCA clear() 之外的双保险）
        ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer sp) {
                ModComponents.SHIKIBANKI.get(sp).init();
            }
        });

        // 本体死亡：重置技能状态（不进入冷却，技能随死亡结束）
        // 不论有没有击杀者两条死亡链路都覆盖
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning() || !gw.isRole(sp, SEIKIBANKI)) return;
            ModComponents.SHIKIBANKI.get(sp).cleanupOnDeath(sp);
        });
        OnPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRunning() || !gw.isRole(sp, SEIKIBANKI)) return;
            ModComponents.SHIKIBANKI.get(sp).cleanupOnDeath(sp);
        });
    }

    private static class SeikibankiRoleImpl extends NormalRole {

        public SeikibankiRoleImpl() {
            super(
                    SEIKIBANKI_ID,
                    0xFFFF1493,              // 深粉色（deep pink）：比 hot pink 更偏红
                    true,                    // isInnocent：true → 平民阵营
                    false,                   // canUseKiller：不能使用杀手能力
                    MoodType.REAL,           // 真实心情
                    400,                     // maxSprintTime：平民体力 400
                    true                     // hideScoreboard：true → 看不到时间（平民阵营）
            );

            this.setCanSeeTime(false)
                .setCanUseInstinct(false)
                .setDefaultMax(1)                 // 每局最多 1 个
                .setCanBeRandomedByOtherRoles(true)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public @NotNull List<ItemStack> getDefaultItems() {
            return new ArrayList<>();
        }

        @Override
        public @NotNull List<ShopEntry> getShopEntries() {
            return new ArrayList<>();
        }

        /**
         * 主动技能「飞头」：
         · skillActive=false 且不在冷却 → 本体冻结、释放头颅，激活技能
         · skillActive=true → 主动收回，意识归位，进入冷却
         */
        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(player.level());
            if (gw == null || !gw.isRunning() || !gw.isRole(player, SEIKIBANKI)) return false;

            SeikibankiPlayerComponent comp = ModComponents.SHIKIBANKI.get(player);
            if (comp.skillActive) {
                comp.recallSkill(player);
            } else {
                comp.startSkill(player);
            }
            return true;
        }

        /** 角色 tick：推进冷却倒计时（由 SRE 调用，仅对当前角色玩家触发） */
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, SEIKIBANKI)) return;
            ModComponents.SHIKIBANKI.get(player).tick(player);
        }
    }
}
