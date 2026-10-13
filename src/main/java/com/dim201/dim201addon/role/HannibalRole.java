package com.dim201.dim201addon;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HannibalRole {

    public static final ResourceLocation HANNIBAL_ID = ResourceLocation.fromNamespaceAndPath(
        Dim201Addon.MOD_ID, "hannibal"
    );
    
    public static SRERole HANNIBAL;
    
    // 平底锅物品
    private static final ResourceLocation PAN = ResourceLocation.parse("noellesroles:pan");

    // 记录已被汉尼拔处理过的尸体 UUID
    private static final Set<UUID> eatenBodies = new HashSet<>();

    public static void register() {
        HANNIBAL = TMMRoles.registerRole(new HannibalRoleImpl());
        Harpymodloader.setRoleMaximum(HANNIBAL_ID, 0);
    }

    public static boolean isBodyEaten(UUID bodyUUID) {
        return eatenBodies.contains(bodyUUID);
    }

    public static void markBodyEaten(UUID bodyUUID) {
        eatenBodies.add(bodyUUID);
    }

    /**
     * 处理汉尼拔与尸体互动（由 SRE 的技能按键触发）
     */
    public static boolean tryInteractWithBody(ServerPlayer player) {
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld == null) {
            return false;
        }

        var role = gameWorld.getRole(player);
        if (role == null || !role.equals(HANNIBAL)) {
            return false;
        }

        PlayerBodyEntity body = findBodyInSight(player);
        if (body == null) {
            sendActionBar(player, "§c无效目标 §7| §f请对准尸体");
            return false;
        }

        UUID bodyUUID = body.getUUID();
        if (isBodyEaten(bodyUUID)) {
            sendActionBar(player, "§c这具尸体已经被处理过了... §7| §f寻找其他尸体");
            return false;
        }

        markBodyEaten(bodyUUID);
        giveFoodStuff(player);
        sendActionBar(player, "§c§l获取食材 §7| §f你从尸体上获取了食材...");
        return true;
    }

    /**
     * 发送 ActionBar 提示给玩家
     */
    private static void sendActionBar(ServerPlayer player, String message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)));
    }

    /**
     * 查找准星指向的尸体实体
     */
    private static PlayerBodyEntity findBodyInSight(ServerPlayer player) {
        double reach = 5.0;
        var eyePos = player.getEyePosition(1.0f);
        var lookVec = player.getLookAngle();
        var endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);

        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);

        for (PlayerBodyEntity body : player.level().getEntitiesOfClass(PlayerBodyEntity.class, searchBox)) {
            Vec3 toEntity = body.position().subtract(eyePos).normalize();
            if (toEntity.dot(lookVec) > 0.5) {
                return body;
            }
        }

        return null;
    }

    /**
     * 给玩家添加食材
     */
    private static void giveFoodStuff(Player player) {
        for (var it : player.getInventory().items) {
            if (it.is(ModItems.FOOD_STUFF)) {
                int count = it.getCount();
                if (count >= 16) {
                    RoleUtils.insertStackInFreeSlot(player, ModItems.FOOD_STUFF.getDefaultInstance());
                    return;
                } else {
                    it.setCount(count + 1);
                    return;
                }
            }
        }
        RoleUtils.insertStackInFreeSlot(player, ModItems.FOOD_STUFF.getDefaultInstance());
    }

    private static class HannibalRoleImpl extends NormalRole {

        public HannibalRoleImpl() {
            super(
                HANNIBAL_ID,
                0xFF8B0000, // 深红色
                false,      // 杀手
                true,       // 可以使用杀手直觉
                MoodType.FAKE,
                -1,
                true
            );

            this.setCanSeeTime(true)
                .setCanUseInstinct(true);
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            List<ItemStack> items = new ArrayList<>();
            Item panItem = BuiltInRegistries.ITEM.get(PAN);
            if (panItem != null) {
                items.add(new ItemStack(panItem));
            }
            return items;
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            return null;
        }

        @Override
        public boolean onAbilityUse(ServerPlayer player) {
            return tryInteractWithBody(player);
        }

        /**
         * 允许汉尼拔丢弃烹饪后的食物（COOKED_FOOD）。
         *
         * <p>原理：SRE 的 {@link io.wifi.starrailexpress.util.ServerDropManager#onDrop}
         * 会调用 {@link SRERole#onDropItem}，返回 {@link InteractionResult#SUCCESS}
         * 表示允许丢弃，原版 Q 键丢物品流程照常进行。
         * 其他物品返回 {@link InteractionResult#PASS} 走 SRE 默认规则（对局内禁止丢弃）。
         */
        @Override
        public InteractionResult onDropItem(Player player, ItemStack item) {
            if (item.is(ModItems.COOKED_FOOD)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        @Override
        public void onFinishQuest(Player player, String quest) {
            // 汉尼拔做任务不给食材
        }
    }
}