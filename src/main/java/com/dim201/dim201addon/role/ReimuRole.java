package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import com.dim201.dim201addon.item.Dim201ModItems;

public class ReimuRole {

    public static final ResourceLocation REIMU_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "reimu"
    );
    
    public static SRERole REIMU;

    // 死亡原因：误伤平民
    public static final ResourceLocation DEATH_REASON_MISTAKE = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "reimu_mistake"
    );

    // 死亡原因：封魔针
    public static final ResourceLocation DEATH_REASON_NEEDLE = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "needle"
    );

    public static void register() {
        REIMU = TMMRoles.registerRole(new ReimuRoleImpl());

        // ---------- 博丽灵梦死亡：掉落左轮手枪（参考鸣上悠实现：OnPlayerDeath.EVENT） ----------
        // 注意：八云紫退场后灵梦变为义警，SRE 原生 NRDeathEvents 会清除义警初始左轮并掉落1把，
        //       这里通过 identifier 字符串检查（比 isRole 的 == 引用比较更可靠）避免额外掉落第2把
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null) return;
            SRERole currentRole = gameWorld.getRole(sp);
            if (currentRole == null) return;
            String roleId = currentRole.identifier().toString();
            // 已变为义警：NRDeathEvents 会处理左轮掉落，此处不额外掉落
            if (roleId.equals("starrailexpress:vigilante")) return;
            // 仅灵梦角色死亡时额外掉落1把左轮
            if (!roleId.equals(REIMU_ID.toString())) return;
            sp.drop(TMMItems.REVOLVER.getDefaultInstance(), false);
        });
    }

    private static class ReimuRoleImpl extends NormalRole {
        
        public ReimuRoleImpl() {
            super(
                REIMU_ID,
                0xFFFF0000, // 红色
                true,      // isInnocent = false（不是平民，是警长阵营）
                false,       // canUseKiller = true（有杀手能力）
                MoodType.REAL,  // 真实心情
                600,        // maxSprintTime = 600 tick（30秒有限体力）
                true        // hideScoreboard = true（看不到剩余时间）
            );
            
            this.setCanSeeTime(false)     // 看不到剩余时间
                .setCanUseInstinct(false)  // 无法使用直觉
                .setVigilanteTeam(true)
                .setDefaultMax(1)
                .setDefaultEnableChance(0)
                .setCanBeRandomedByOtherRoles(false)
                .setServerGameTickEvent(this::handleServerTick);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            // 给灵梦初始物品：1 个御币（手持右键射封魔针，替代空手释放方案）
            List<ItemStack> items = new ArrayList<>();
            items.add(new ItemStack(Dim201ModItems.REIMU_GOHEI));
            return items;
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            return new ArrayList<>(); // 博丽灵梦没有商店
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            ReimuPlayerComponent comp = ModComponents.REIMU.get(player);

            // 技能键 → 仍然是二重结界（Dream Barrier）
            // 封魔针 → 改成手持「御币」右键（物品重写 use() 稳定调用，避免 Fabric Callback 不稳定）
            return comp.useDreamBarrier(player);
        }
        
        private void handleServerTick(ServerPlayer player, SREGameWorldComponent gameWorld) {
            if (gameWorld == null || !gameWorld.isRole(player, REIMU)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                return;
            }

            ReimuPlayerComponent comp = ModComponents.REIMU.get(player);
            comp.tick(player);
        }
    }
    
    /**
     * 检查是否有存活的博丽灵梦
     */
    public static boolean isReimuAlive(net.minecraft.server.MinecraftServer server) {
        if (server == null) return false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var gameWorld = SREGameWorldComponent.KEY.get(p.level());
            if (gameWorld != null && gameWorld.isRole(p, REIMU)) {
                if (p.isAlive() && GameUtils.isPlayerAliveAndSurvival(p)) {
                    return true;
                }
            }
        }
        return false;
    }
}