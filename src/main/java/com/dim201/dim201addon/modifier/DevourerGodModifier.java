package com.dim201.dim201addon.modifier;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.role.DevourerRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

/**
 * 吞噬神明修饰符注册 + 事件处理
 *
 * <p>原理：镜像 SRE 难民（Refugee）修饰符，但只出现在吞噬者（DevourerRole）身上。
 *
 * <p>触发流程：
 * <ol>
 *   <li>SRE 角色分配器将 REFUGEE 修饰符分配给吞噬者时，
 *       {@link ModifierAssigned#EVENT} 拦截并替换为 DEVOURER_GOD</li>
 *   <li>吞噬者死亡时若持有 DEVOURER_GOD → 2 分钟后复活为神明吞噬者</li>
 * </ol>
 *
 * <p>自定义音频：devourer_god_music（替换 StupidExpress.SOUND_REGUGEE）
 */
public final class DevourerGodModifier {

    /** 吞噬神明修饰符（深紫色，隐藏，默认不随机分配） */
    public static final SREModifier DEVOURER_GOD = HMLModifiers.registerModifier(new SREModifier(
            Dim201Addon.id("devourer_god"),
            0xAA00AA,   // 深紫色（dark_purple）
            null, null,
            false, false
    )).setDefaultEnableChance(0).setHidden(true);

    /** 自定义音频：神明吞噬者时刻背景音乐（替换 StupidExpress.SOUND_REGUGEE） */
    public static final SoundEvent SOUND_DEVOURER_GOD = registerSound("devourer_god_music");

    private static boolean initialized = false;

    private DevourerGodModifier() {}

    /** 注册音效 */
    private static SoundEvent registerSound(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * 初始化：注册 ModifierAssigned 事件
     *
     * <p>原理：当 SRE 将 REFUGEE 修饰符分配给吞噬者时，拦截并替换为 DEVOURER_GOD。
     * 这样吞噬者不会触发难民（亡命徒）的复活机制，而是触发神明吞噬者的复活机制。
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        ModifierAssigned.EVENT.register((player, modifier) -> {
            // 只处理 REFUGEE 被分配的情况
            if (!modifier.equals(SEModifiers.REFUGEE)) return;
            if (!(player instanceof ServerPlayer sp)) return;

            var gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null) return;
            // 只对吞噬者生效
            if (!gameWorld.isRole(sp, DevourerRole.DEVOURER_ROLE)) return;

            // 吞噬者不获得难民，改为获得吞噬神明
            WorldModifierComponent wm = WorldModifierComponent.KEY.get(sp.level());
            wm.removeModifier(sp.getUUID(), SEModifiers.REFUGEE, false);
            wm.addModifier(sp.getUUID(), DEVOURER_GOD, false);
            // 递归触发 DEVOURER_GOD 的分配事件（让其他监听器感知到新修饰符）
            ModifierAssigned.EVENT.invoker().assignModifier(sp, DEVOURER_GOD);
            wm.sync();

            Dim201Addon.LOGGER.info("吞噬者 {} 的难民修饰符已替换为吞噬神明",
                    sp.getName().getString());
        });
    }
}
