package com.dim201.dim201addon.client;

import com.dim201.dim201addon.ModComponents;
import com.dim201.dim201addon.ReimuRole;
import com.dim201.dim201addon.YukariRole;
import com.dim201.dim201addon.modifier.DevourerGodModifier;
import com.dim201.dim201addon.packet.SwitchSkillC2SPacket;
import com.dim201.dim201addon.packet.SwitchDaoLeirenModeC2SPacket;
import com.dim201.dim201addon.packet.BrotherSkillC2SPacket;
import com.dim201.dim201addon.packet.BrotherSyncS2CPacket;
import com.dim201.dim201addon.packet.DevourerEatC2SPacket;
import com.dim201.dim201addon.packet.DevourerKillerEscapeC2SPacket;
import com.dim201.dim201addon.packet.YukariSetCoordinateC2SPacket;
import com.dim201.dim201addon.client.PucciClientUI;
import com.dim201.dim201addon.packet.PucciDisguiseS2CPacket;
import com.dim201.dim201addon.menu.ModMenus;
import com.dim201.dim201addon.client.renderer.CleaverRenderer;
import com.dim201.dim201addon.client.renderer.LoneWolfDroneRenderer;
import com.dim201.dim201addon.client.renderer.SeikibankiHeadRenderer;
import com.dim201.dim201addon.client.screen.BeggarScreen;
import com.dim201.dim201addon.client.screen.DemonContractScreen;
import com.dim201.dim201addon.client.screen.CardTradeScreen;
import com.dim201.dim201addon.init.ModEntities;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import io.wifi.starrailexpress.client.StatusBarHUD;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbience;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import com.dim201.dim201addon.role.DaoLeirenRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.DevourerRole;
import com.dim201.dim201addon.role.DevourerGodRole;
import com.dim201.dim201addon.role.WhiteSnakeRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.GKeyRoleSkill;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.init.ModEffects;

public class Dim201AddonClient implements ClientModInitializer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("dim201addon-client");

    private boolean wasSwitchKeyPressed = false;
    // 检测肚内杀手的反杀技能键按下
    private boolean wasAbilityKillerEscape = false;

    // ===== 神明吞噬者时刻进度条管理 =====
    // 进度条 ID 和显示参数
    private static final String GOD_BAR_ID = "devourer_god";
    private static final String GOD_BAR_NAME = "\u00a75神明吞噬者时刻";
    // 时刻总时长 150 秒 = 3000 ticks = 150000 ms
    private static final long GOD_DURATION_MS = 150_000L;
    // 进度条状态：是否已显示、本地开始时间
    private static boolean godBarActive = false;
    private static long godBarStartTime = 0L;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[dim201addon] 客户端初始化");

        //HUD
        YukariClientHud.register();
        // 八云紫坐标方块紫色高亮
        YukariCoordinateRenderer.register();
        ReimuClientHud.register();
        KoishiClientHud.register();
        WhiteSnakeClientHud.register();
        AdachiClientHud.register();
        NarukamiClientHud.register();
        EmiyaClientHud.register();
        ChimataClientHud.register();
        GojoClientHud.register();
        LegionClientHud.register();
        LegionNoShopClientHud.register();
        DaoLeirenClientHud.register();
        SakiClientHud.register();
        KidnapperClientHud.register();
        DismasClientHud.register();
        PhantomThiefClientHud.register();
        WeaponMasterClientHud.register();
        SeikibankiClientHud.register();
        TeacherZhangClientHud.register();

        // 神明吞噬者时刻背景音乐：使用客户端背景氛围系统（MyBackgroundAmbience），
        // 替代服务端 playNotifySound 一次性发包，避免客户端加载/传送时错过音效包。
        // 条件：场上存在神明吞噬者且其 fogActive 为 true（与雾渲染同步触发）
        AmbienceUtil.registerBackgroundAmbience(new MyBackgroundAmbience(
                DevourerGodModifier.SOUND_DEVOURER_GOD,
                SoundSource.AMBIENT,
                player -> {
                    var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isRunning()) return false;
                    for (var p : player.level().players()) {
                        if (gameWorld.isRole(p, DevourerGodRole.DEVOURER_GOD_ROLE)) {
                            var godComp = ModComponents.DEVOURER_GOD.getNullable(p);
                            if (godComp != null && godComp.fogActive) return true;
                        }
                    }
                    return false;
                },
                0.1f,  // 音量
                20,    // 渐入 ticks
                10     // 渐出 ticks
        ));

        // 卡牌交易界面注册
        MenuScreens.register(ModMenus.CARD_TRADE, CardTradeScreen::new);
        // 乞讨界面注册
        MenuScreens.register(ModMenus.BEGGAR, BeggarScreen::new);
        // 恶魔契约界面注册
        MenuScreens.register(ModMenus.DEMON_CONTRACT, DemonContractScreen::new);

        // 菜刀实体渲染注册（箭形渲染 + 放大 1.25x，套用飞刀贴图）
        EntityRendererRegistry.register(ModEntities.CLEAVER, CleaverRenderer::new);
        // 赤蛮奇飞头实体渲染（主人皮肤头颅模型）
        EntityRendererRegistry.register(ModEntities.SEIKIBANKI_HEAD, SeikibankiHeadRenderer::new);
        // 赤蛮奇飞头远程驾驶（相机绑定 + 输入转发）
        SeikibankiFlyController.register();
        // 赤蛮奇 HUD（冷却倒计时）
        SeikibankiClientHud.register();

        // 独狼无人机：实体渲染（隐形）、远程驾驶、HUD、标记高亮
        EntityRendererRegistry.register(ModEntities.LONE_WOLF_DRONE, LoneWolfDroneRenderer::new);
        LoneWolfDroneController.register();
        LoneWolfClientHud.register();
        LoneWolfMarkInstinctHighlight.register();

        // 独狼 G 键技能：部署/切换无人机
        // 原理：与吞噬者/蕾忍一致，GKeyRoleSkill 在最前面拦截 G 键并转发服务端，
        // 避免裸 onAbilityUse 被准星目标拦截导致技能失效
        GKeyRoleSkill.register(com.dim201.dim201addon.LoneWolfRole.LONE_WOLF, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new com.dim201.dim201addon.packet.LoneWolfSkillC2SPacket());
            return true;
        });

        // 独狼【清算时间】背景音乐：任一独狼进入清算时间后全场循环播放（音量 0.2）
        AmbienceUtil.registerBackgroundAmbience(new MyBackgroundAmbience(
                com.dim201.dim201addon.sound.Dim201ModSounds.LONE_WOLF_SETTLEMENT,
                SoundSource.AMBIENT,
                player -> {
                    var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isRunning()) return false;
                    for (var p : player.level().players()) {
                        // 存活检查：独狼死亡后立即停止音乐（服务端清理包之外的客户端兜底）
                        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(p)) continue;
                        if (gameWorld.isRole(p, com.dim201.dim201addon.LoneWolfRole.LONE_WOLF)) {
                            var comp = ModComponents.LONE_WOLF.getNullable(p);
                            if (comp != null && comp.settlementActive) return true;
                        }
                    }
                    return false;
                },
                0.2f,   // 音量
                20,     // 渐入 ticks
                20      // 渐出 ticks
        ));

        // 大怪盗：G 键读条 + 透视事件 + HUD（预告信头像按钮由 Mixin 注入背包界面）
        PhantomThiefClientUI.register();

        // 吞噬者 G 键技能：通过 SRE 的 GKeyRoleSkill 系统注册，
        // 按技能键时发送 DevourerEatC2SPacket，服务端决定吞噬或吐出
        GKeyRoleSkill.register(DevourerRole.DEVOURER_ROLE, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new DevourerEatC2SPacket());
            return true;
        });

        // 神明吞噬者 G 键技能：复用同一个 DevourerEatC2SPacket，
        // 服务端通过角色判定分流处理（神明吞噬者=直接击杀，3s CD）
        GKeyRoleSkill.register(DevourerGodRole.DEVOURER_GOD_ROLE, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new DevourerEatC2SPacket());
            return true;
        });

        // 电击狂人 G 键技能：自身充电（获得静电）
        GKeyRoleSkill.register(com.dim201.dim201addon.role.ElectricManiacRole.ELECTRIC_MANIAC, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new com.dim201.dim201addon.packet.electric.ElectricManiacChargeC2SPacket());
            return true;
        });

        // 电击狂人右键方块：附着静电（客户端发送 C2S，服务端处理）
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide && com.dim201.dim201addon.role.ElectricManiacRole.isElectricManiacClient(player)) {
                ClientPlayNetworking.send(new com.dim201.dim201addon.packet.electric.ElectricManiacBlockC2SPacket(
                        hitResult.getBlockPos()));
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // 八云紫右键方块：设置坐标（常驻交互，不再占用技能栏位）
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide) {
                var gw = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                if (gw != null && gw.isRole(player, com.dim201.dim201addon.YukariRole.YUKARI)) {
                    ClientPlayNetworking.send(new YukariSetCoordinateC2SPacket(hitResult.getBlockPos()));
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // 蕾忍 G 键技能：切换攻击模式（岚刀 ↔ 燃烧刀）
        // 原理：使用 GKeyRoleSkill 在最前面拦截 G 键，避免 onAbilityUse 被准星目标拦截
        GKeyRoleSkill.register(DaoLeirenRole.DAO_LEIREN, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new SwitchDaoLeirenModeC2SPacket());
            return true;
        });

        // 东堂葵 G 键：发动不义游戏（开启 5 秒响应窗口）
        GKeyRoleSkill.register(TodoAoiRole.TODO_AOI, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new BrotherSkillC2SPacket());
            return true;
        });
        // 虎杖悠仁 G 键：响应不义游戏（与东堂互换位置 + 10 秒专注）
        GKeyRoleSkill.register(ItadoriRole.ITADORI, true, (client, gameWorld) -> {
            ClientPlayNetworking.send(new BrotherSkillC2SPacket());
            return true;
        });

        // 兄弟默契状态同步：更新搭档 UUID / 能量 / 感知距离，供直觉高亮与 HUD 使用
        ClientPlayNetworking.registerGlobalReceiver(BrotherSyncS2CPacket.TYPE,
                (payload, context) -> context.client().execute(() -> BrotherClientState.handle(payload)));
        // 断开连接时清空客户端搭档状态，防止带入下一个服务器
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    BrotherClientState.reset();
                    // 断开服务器时清空电击狂人客户端状态，防止带入下次连接
                    com.dim201.dim201addon.client.electric.ElectricManiacClientState.reset();
                });

        // 东堂 × 虎杖互相透视：复用 SRE 直觉渲染管线（不开启直觉也能隔墙看到搭档）
        BrotherInstinctHighlight.register();
        // 兄弟默契 HUD：显示当前共享能量点数
        BrotherHud.register();
        // 盲人「心之眼」：8 格内玩家透视（SRE 直觉管线）
        BlindVisionHighlight.register();

        // 恶魔契约者：无声者屏蔽/悬赏金色/危险感知透视 + HUD
        com.dim201.dim201addon.client.contract.DemonContractInstinct.register();
        com.dim201.dim201addon.client.contract.DemonContractorHud.register();
        // 恶魔契约结算结果：切换当前契约界面为「献祭」揭晓页
        ClientPlayNetworking.registerGlobalReceiver(
                com.dim201.dim201addon.packet.DemonContractResultS2CPacket.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof DemonContractScreen screen) {
                        screen.showResult(payload);
                    }
                }));

        // 电击狂人：方块高亮 + 直觉范围限制 + 触电高亮 + 爆炸动画 + 技能HUD
        com.dim201.dim201addon.client.electric.ElectricManiacBlockRenderer.register();
        com.dim201.dim201addon.client.electric.ElectricManiacInstinctHighlight.register();
        com.dim201.dim201addon.client.electric.ElectricManiacShockHighlight.register();
        com.dim201.dim201addon.client.electric.ElectricManiacBlastAnimator.register();
        com.dim201.dim201addon.client.electric.ElectricManiacClientHud.register();

        // 电击狂人 S2C 包：静电方块列表同步
        ClientPlayNetworking.registerGlobalReceiver(
                com.dim201.dim201addon.packet.electric.ElectricManiacBlocksS2CPacket.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    com.dim201.dim201addon.client.electric.ElectricManiacClientState.electrifiedBlocks.clear();
                    com.dim201.dim201addon.client.electric.ElectricManiacClientState.electrifiedBlocks.addAll(payload.blocks());
                }));

        // SRE 对局结束广播：立即清空电击狂人全部视觉状态（方块/触电高亮/爆炸粒子）
        ClientPlayNetworking.registerGlobalReceiver(
                io.wifi.starrailexpress.network.OnGameFinishedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        com.dim201.dim201addon.client.electric.ElectricManiacClientState::reset));
        // 电击狂人 S2C 包：触电事件（记录到期游戏时间，由动画器统一清理）
        ClientPlayNetworking.registerGlobalReceiver(
                com.dim201.dim201addon.packet.electric.ElectricManiacShockS2CPacket.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level == null) return;
                    long until = context.client().level.getGameTime() + payload.durationTicks();
                    for (java.util.UUID uuid : payload.players()) {
                        com.dim201.dim201addon.client.electric.ElectricManiacClientState
                                .shockedPlayersUntil.put(uuid, until);
                    }
                }));
        // 电击狂人 S2C 包：静电爆炸粒子
        ClientPlayNetworking.registerGlobalReceiver(
                com.dim201.dim201addon.packet.electric.ElectricManiacBlastS2CPacket.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().level == null) return;
                    com.dim201.dim201addon.client.electric.ElectricManiacClientState.activeBlasts.add(
                            new com.dim201.dim201addon.client.electric.ElectricManiacClientState.BlastFx(
                                    payload.center(), context.client().level.getGameTime()));
                }));

        // 镇静剂不再注册颜色提供者：自定义 sedative.png 已烘焙红色药液，
        // 而 item/generated 的 layer0 全部图元带 tintindex 0，旧的 0xFF0000 染色会把
        // 蓝色玻璃瓶身压成黑色、整张图变成不可辨认的红色色块（表现为“贴图丢失”）

        // 普奇神父 S2C 广播包：收到后更新 DISGUISE_CACHE 静态 Map
        ClientPlayNetworking.registerGlobalReceiver(PucciDisguiseS2CPacket.TYPE,
                (payload, context) -> PucciClientUI.handleS2CPacket(payload));

        // 每 tick 检测按键
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            detectSkillSwitch(client);
            // 检测肚内杀手按技能键反杀吞噬者
            detectStashedKillerEscape(client);
            // 神明吞噬者时刻进度条管理
            updateGodMomentProgressBar(client);
        });

        LOGGER.info("[dim201addon] 客户端初始化完成");
    }

    private void detectSkillSwitch(Minecraft client) {
        if (client.player == null) {
            wasSwitchKeyPressed = false;
            return;
        }

        boolean isSwitchKeyPressed = NoellesrolesClient.nextAbilityBind.isDown();
        if (isSwitchKeyPressed && !wasSwitchKeyPressed) {
            var gameWorld = SREGameWorldComponent.KEY.get(client.player.level());
            if (gameWorld != null) {
                var role = gameWorld.getRole(client.player);
                if (role != null && (role.equals(YukariRole.YUKARI) || role.equals(ReimuRole.REIMU)
                        || role.equals(WhiteSnakeRole.WHITESNAKE)
                        || role.equals(com.dim201.dim201addon.role.ElectricManiacRole.ELECTRIC_MANIAC))) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new SwitchSkillC2SPacket());
                }
            }
        }
        wasSwitchKeyPressed = isSwitchKeyPressed;
    }

    /**
     * 肚内杀手（被吞噬者吞下）按 SRE 技能键反杀
     *
     * 客户端判据（近似，最终校验由服务端决定）：
     * 1. 玩家处于 SPECTATOR 模式 —— SRE 在 PelicanManager.stashPlayer 里把被吞玩家切为 SPECTATOR
     * 2. 身上有持续极长的 USED_BANED 效果（默认持续 Integer.MAX_VALUE ticks ≈ 3 年，远超正常技能禁用）
     * 3. 玩家角色 canUseKiller == true（杀手阵营）
     *
     * 满足以上 3 条时按 SRE 技能键就发送 DevourerKillerEscapeC2SPacket，
     * 服务端 killerTryEscape 内部再做权威校验（PelicanManager.isStashed + 吞噬者角色 + 30 秒窗口）。
     */
    private void detectStashedKillerEscape(Minecraft client) {
        if (client.player == null) {
            wasAbilityKillerEscape = false;
            return;
        }

        boolean abilityDown = NoellesrolesClient.abilityBind.isDown();
        if (abilityDown && !wasAbilityKillerEscape) {
            // ====== 1. spectator 模式 ======
            if (!client.player.isSpectator()) {
                // 正常存活玩家肯定不是被吞的
                wasAbilityKillerEscape = abilityDown;
                return;
            }

            // ====== 2. 身上有极长 USED_BANED（吞噬后默认加 Integer.MAX_VALUE 时长）======
            boolean hasLongUsedBaned = false;
            if (ModEffects.USED_BANED != null) {
                var effect = client.player.getEffect(ModEffects.USED_BANED);
                if (effect != null) {
                    // Integer.MAX_VALUE 约 3 年，这里以 > 1000000 tick（≈ 13 小时）做阈值
                    // 避免其他模组/道具短时间禁用物品被误判为被吞状态
                    hasLongUsedBaned = effect.getDuration() > 1_000_000;
                }
            }
            if (!hasLongUsedBaned) {
                wasAbilityKillerEscape = abilityDown;
                return;
            }

            // ====== 3. 玩家是杀手阵营角色（canUseKiller=true）======
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(client.player.level());
            if (gw == null) {
                wasAbilityKillerEscape = abilityDown;
                return;
            }
            SRERole role = gw.getRole(client.player);
            boolean isKiller = (role != null) && role.canUseKiller();
            if (!isKiller) {
                wasAbilityKillerEscape = abilityDown;
                return;
            }

            // 所有条件通过：发反杀包
            ClientPlayNetworking.send(new DevourerKillerEscapeC2SPacket());
        }
        wasAbilityKillerEscape = abilityDown;
    }

    /**
     * 神明吞噬者时刻进度条管理。
     *
     * <p>原理：客户端每 tick 检查场上是否存在神明吞噬者且 fogActive 为 true。
     * 条件满足时通过 {@link StatusBarHUD#addStatusBar} 显示进度条，
     * progressSupplier 用本地时间计算已过比例（0→1，150 秒满）。
     * 条件不满足时移除进度条。
     */
    private static void updateGodMomentProgressBar(Minecraft client) {
        boolean fogActive = false;
        if (client.level != null && client.player != null) {
            var gameWorld = SREGameWorldComponent.KEY.getNullable(client.level);
            if (gameWorld != null) {
                for (var p : client.level.players()) {
                    if (gameWorld.isRole(p, DevourerGodRole.DEVOURER_GOD_ROLE)) {
                        var godComp = ModComponents.DEVOURER_GOD.getNullable(p);
                        if (godComp != null && godComp.fogActive) {
                            fogActive = true;
                            break;
                        }
                    }
                }
            }
        }

        if (fogActive && !godBarActive) {
            // 进度条首次显示：记录本地开始时间
            godBarStartTime = System.currentTimeMillis();
            godBarActive = true;
            StatusBarHUD.getInstance().addStatusBar(
                    GOD_BAR_ID,
                    GOD_BAR_NAME,
                    () -> {
                        long elapsed = System.currentTimeMillis() - godBarStartTime;
                        return Math.min(1.0f, elapsed / (float) GOD_DURATION_MS);
                    }
            );
        } else if (!fogActive && godBarActive) {
            // 神明吞噬者时刻结束/死亡：移除进度条
            StatusBarHUD.getInstance().removeStatusBar(GOD_BAR_ID);
            godBarActive = false;
        }
    }

}