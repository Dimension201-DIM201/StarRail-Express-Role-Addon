package com.dim201.dim201addon.item;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.ReimuPlayerComponent;
import com.dim201.dim201addon.ReimuRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 博丽灵梦专属初始物品：御币（Gohei）
 * 手持右键 → 发射封魔针（走原 ReimuPlayerComponent 灵气/冷却/门穿透逻辑）
 * 木棍作为材质替代（纹理/minecraft:item/stick）
 *
 * 设计原因（放弃空手右键，改用专属物品）：
 *   Fabric 的 UseBlockCallback / UseItemCallback 会被 MC 客户端多种情况吞掉（ADVENTURE、
 *   ping、SRE 其他 callback 顺序），导致空手右键表现不稳定。专属物品重写 use() 是
 *   100% 稳定的：不管游戏模式/准星/其他模组，只要玩家手持御币右键，MC 一定调用 use()。
 */
public class ReimuGoheiItem extends Item {

    public ReimuGoheiItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        // 1. 过滤非主手：副手拿着御币也不触发（防止双持）
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        // 2. 只在服务端执行
        if (world == null || world.isClientSide()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        // 3. 必须是灵梦角色（非灵梦拿着御币，不触发，静默 PASS）
        var gw = SREGameWorldComponent.KEY.get(world);
        if (gw == null || !gw.isRole(sp, ReimuRole.REIMU)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        // 4. 走封魔针原逻辑（灵气≥1 + 冷却=0 → 扣灵气扣冷却射针；否则发 ActionBar 原因）
        ReimuPlayerComponent comp = ModComponents.REIMU.get(sp);
        if (comp == null) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        boolean ok = comp.useSealingNeedle(sp);
        ItemStack handStack = player.getItemInHand(hand);
        if (ok) {
            return InteractionResultHolder.success(handStack);
        }
        // 冷却/灵气不足：useSealingNeedle 内部已发 ActionBar 原因 → 返回 FAIL 告诉 MC 已处理
        return InteractionResultHolder.fail(handStack);
    }
}
