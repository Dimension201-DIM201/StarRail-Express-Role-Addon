package com.dim201.dim201addon;

import com.dim201.dim201addon.cooperation.BrotherSyncManager;
import com.dim201.dim201addon.packet.SwitchSkillC2SPacket;
import com.dim201.dim201addon.packet.SwitchDaoLeirenModeC2SPacket;
import com.dim201.dim201addon.packet.DevourerEatC2SPacket;
import com.dim201.dim201addon.packet.DevourerKillerEscapeC2SPacket;
import com.dim201.dim201addon.packet.ClownpieceRefreshC2SPacket;
import com.dim201.dim201addon.packet.PhantomThiefAnnounceC2SPacket;
import com.dim201.dim201addon.packet.PhantomThiefStealC2SPacket;
import com.dim201.dim201addon.packet.PucciDisguiseC2SPacket;
import com.dim201.dim201addon.packet.PucciDisguiseS2CPacket;
import com.dim201.dim201addon.packet.LoneWolfDroneInputC2SPacket;
import com.dim201.dim201addon.packet.LoneWolfSkillC2SPacket;
import com.dim201.dim201addon.packet.SeikibankiFlyInputC2SPacket;
import com.dim201.dim201addon.packet.YukariSetCoordinateC2SPacket;
import com.dim201.dim201addon.item.Dim201ModItems;
import com.dim201.dim201addon.item.card.PhoenixTailManager;
import com.dim201.dim201addon.item.card.ability.BlankCard;
import com.dim201.dim201addon.command.DevourerGodCommand;
import com.dim201.dim201addon.command.LoneWolfSettlementCommand;
import com.dim201.dim201addon.modifier.DevourerGodManager;
import com.dim201.dim201addon.modifier.DevourerGodModifier;
import com.dim201.dim201addon.modifier.OptimisticModifier;
import com.dim201.dim201addon.modifier.ShuangyueDDDModifier;
import com.dim201.dim201addon.menu.CardTradeSession;
import com.dim201.dim201addon.menu.ModMenus;
import com.dim201.dim201addon.role.AdachiRole;
import com.dim201.dim201addon.role.DaoLeirenRole;
import com.dim201.dim201addon.role.ClownpieceRole;
import com.dim201.dim201addon.role.ChimataRole;
import com.dim201.dim201addon.role.PhantomThiefRole;
import com.dim201.dim201addon.role.WeaponMasterRole;
import com.dim201.dim201addon.WeaponMasterPlayerComponent;
import com.dim201.dim201addon.ElectricManiacPlayerComponent;
import com.dim201.dim201addon.role.ChimataTradeManager;
import com.dim201.dim201addon.role.KillerCardDiscountManager;
import com.dim201.dim201addon.role.TeacherZhangAllGameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.index.TMMItems;
import com.dim201.dim201addon.role.NarukamiRole;
import com.dim201.dim201addon.role.SeikibankiRole;
import com.dim201.dim201addon.role.TodoAoiRole;
import com.dim201.dim201addon.role.BlindRole;
import com.dim201.dim201addon.role.ElectricManiacRole;
import com.dim201.dim201addon.role.ItadoriRole;
import com.dim201.dim201addon.role.WhiteSnakeRole;
import com.dim201.dim201addon.role.PucciRole;
import com.dim201.dim201addon.role.PucciDisguiseRoleData;
import io.wifi.starrailexpress.api.data.RoleData;
import com.dim201.dim201addon.role.EmiyaRole;
import com.dim201.dim201addon.role.GojoRole;
import com.dim201.dim201addon.role.LegionRole;
import com.dim201.dim201addon.role.LegionNoShopRole;
import com.dim201.dim201addon.role.SakiRole;
import com.dim201.dim201addon.role.KidnapperRole;
import com.dim201.dim201addon.role.TicketInspectorRole;
import com.dim201.dim201addon.role.DevourerRole;
import com.dim201.dim201addon.role.DevourerGodRole;
import com.dim201.dim201addon.role.UnluckyBastardRole;
import com.dim201.dim201addon.role.ShionRole;
import com.dim201.dim201addon.role.MadmanRole;
import com.dim201.dim201addon.role.DismasRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Dim201Addon implements ModInitializer {

    public static final String MOD_ID = "dim201addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    // 防止绑定死亡递归处理
    private static boolean isProcessingLinkedDeath = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Dim201Addon 初始化完成");

        Dim201ModItems.init();
        // 镇静剂注册到 SRE 的 INVISIBLE_ITEMS 列表：
        // 第三人称下其他玩家看不到持有者手中的镇静剂瓶子（参考 SRE 空包弹/烟雾弹做法），
        // 第一人称本地玩家由 ItemInHandRenderer 独立渲染，自己仍可见。
        TMMItems.INVISIBLE_ITEMS.add(Dim201ModItems.SEDATIVE);
        com.dim201.dim201addon.init.ModEntities.init();
        // Mob 基类实体属性必须在通用入口注册（客户端注册会导致 field_23711 NPE）
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                com.dim201.dim201addon.init.ModEntities.SEIKIBANKI_HEAD,
                net.minecraft.world.entity.PathfinderMob.createMobAttributes().build());
        // 独狼隐形无人机同为 Mob 基类：缺属性注册会在 addFreshEntity 时抛异常，
        // 且 deployDrone 先扣金币后生成实体，异常会导致「扣费但无任何效果」
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                com.dim201.dim201addon.init.ModEntities.LONE_WOLF_DRONE,
                net.minecraft.world.entity.PathfinderMob.createMobAttributes().build());
        com.dim201.dim201addon.sound.Dim201ModSounds.init();
        com.dim201.dim201addon.item.card.CardEventBus.init();
        com.dim201.dim201addon.item.card.PhoenixTailManager.register();
        com.dim201.dim201addon.item.card.UndergroundSunManager.register();
        com.dim201.dim201addon.contract.DemonContractManager.register();
        ModMenus.init();

        registerPayloadTypes();

        // 以下角色待重做，暂时注释掉
        // BoyRole.register();
        // VanDDFRole.register();
        // VanRole.register();
        // BiollanteRole.register();
        TeacherZhangRole.register();
        CorruptCopRole.register();       
        LoneWolfRole.register();
        RookieCopRole.register();
        HannibalRole.register();

        YukariRole.register();
        ReimuRole.register();
        KoishiRole.register();
        WhiteSnakeRole.register();
        PucciRole.register();
        AdachiRole.register();
        NarukamiRole.register();
        EmiyaRole.register();
        GojoRole.register();
        LegionRole.register();
        SakiRole.register();
        KidnapperRole.register();
        TicketInspectorRole.register();
        MadmanRole.register();
        DevourerRole.register();
        DevourerGodRole.register();
        UnluckyBastardRole.register();
        ShionRole.register();
        DaoLeirenRole.register();
        ClownpieceRole.register();
        DismasRole.register();
        LegionNoShopRole.register();
        ChimataRole.register();
        PhantomThiefRole.register();
        WeaponMasterRole.register();
        SeikibankiRole.register();
        TodoAoiRole.register();
        ItadoriRole.register();
        BlindRole.register();
        org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(BlindRole.BLIND_ID, 1);
        // 电击狂人：杀手阵营，静电机制
        ElectricManiacRole.register();
        org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(ElectricManiacRole.ELECTRIC_MANIAC_ID, 1);
        // 恶魔契约者：平民阵营，尸体契约索取力量/献祭代价
        com.dim201.dim201addon.role.DemonContractorRole.register();
        org.agmas.harpymodloader.Harpymodloader.setRoleMaximum(
                com.dim201.dim201addon.role.DemonContractorRole.DEMON_CONTRACTOR_ID, 1);
        // 虎杖悠仁（警长）× 东堂葵（平民）绑定生成：仅单向注册 虎杖→东堂。
        // 原理：addOccupationRole 是单向关联；虎杖正常进入警长抽取池（占警长名额，受配额约束），
        // 抽中后展开阶段追加东堂（tryRemoveARole 移除一个平民同阵营腾位，不改变任何阵营配额）。
        // 东堂自身 ROLE_MAX=0 + enableChance=0 永不独立入池，因此无需反向注册；
        // 双向注册会在东堂被独立抽中时反向强塞虎杖，腾不出警长位时 tryLevel=3 移除任意角色，
        // 导致警长数量突破配置上限（对比足立→鸣上同为单向范式）。
        org.agmas.harpymodloader.Harpymodloader.addOccupationRole(ItadoriRole.ITADORI, TodoAoiRole.TODO_AOI);
        com.dim201.dim201addon.cooperation.BrotherSyncManager.register();
        TeacherZhangAllGameMode.registerEvents();

        // ===== 注册全员张老师模式 =====
        // 原理：SREGameModes.GAME_MODES 是全局 HashMap，
        // MapVotingManager（投票系统）和 StartCommand（/start 指令）都从这里枚举所有游戏模式，
        // 注册后即可在投票/指令中出现，无需额外 Mixin。
        SREGameModes.registerGameMode(new TeacherZhangAllGameMode());
        LOGGER.info("全员张老师模式注册完成");

        // Harpymodloader.addOccupationRole(VanRole.VAN, BiollanteRole.BIOLLANTE); // 待重做
        Harpymodloader.addOccupationRole(YukariRole.YUKARI, ReimuRole.REIMU);
        Harpymodloader.addOccupationRole(WhiteSnakeRole.WHITESNAKE, PucciRole.PUCCI);
        Harpymodloader.addOccupationRole(AdachiRole.ADACHI, NarukamiRole.NARUKAMI);
        
        // 白蛇和足立透双向互斥
        WhiteSnakeRole.WHITESNAKE.addTwoWayOpposingRole(AdachiRole.ADACHI);
        // 足立透和虎杖悠仁双向互斥（同为高信息量的独立机制角色，避免同局叠加）
        AdachiRole.ADACHI.addTwoWayOpposingRole(ItadoriRole.ITADORI);

        // 吞噬者与 SRE 原生鹈鹕双向互斥：两者都基于"吞人/藏人"机制（PelicanManager.stashPlayer），
        // 同局并存会让被吞玩家被两套吞噬状态同时管理导致视角/释放逻辑混乱，因此禁止同局生成。
        // addTwoWayOpposingRole 会互把对方加入 opposingRoles 集合，SRE 角色分配器会跳过与已分配角色互斥的候选。
        DevourerRole.DEVOURER_ROLE.addTwoWayOpposingRole(ModRoles.PELICAN);

        // 注册白蛇和普奇神父支持 SRE 护盾同步
        SREArmorPlayerComponent.canSynced.add(entry -> entry.getKey().identifier().getPath().equals("whitesnake"));
        SREArmorPlayerComponent.canSynced.add(entry -> entry.getKey().identifier().getPath().equals("pucci"));
        SREArmorPlayerComponent.canSynced.add(entry -> entry.getKey().identifier().getPath().equals("devourer_god"));

        // BoyRole.registerTickHandler(); // 待重做
        // VanDDFRole.registerTickHandler(); // 待重做
        // BiollanteRole.registerTickHandler(); // 待重做

        LoneWolfRole.registerEvents();
        // VanKillHandler.register(); // 待重做
        AdachiRole.registerEvents();

        registerMeleeKillHandler();
        LegionRole.registerEvents();

        registerSwitchSkillPacketHandler();
        registerDevourerPacketHandler();
        registerDevourerKillerEscapePacketHandler();
        registerSwitchDaoLeirenModePacketHandler();
        registerClownpieceRefreshPacketHandler();
        registerPucciDisguisePacketHandler();
        registerPhantomThiefPacketHandler();
        registerSeikibankiFlyInputHandler();
        registerLoneWolfDroneInputHandler();
        registerLoneWolfSkillHandler();
        registerBrotherSkillPacketHandler();

        registerLinkedDeathEvents();
        registerLifecycleEvents();

        // 用户 2026-08-11：灵梦封魔针改为空手右键即可发射（不需要任何物品）
        registerReimuSealingNeedleFireFromEmptyHand();

        // 绑架者静态全局事件（死亡联动 + 操作禁用）
        KidnapperPlayerComponent.registerStaticEvents();
        registerKidnapperHostageActionBlock();

        // 能力卡牌交易：手持卡牌左键点击玩家发起交易
        registerCardTradeHandler();

        // 对局结束时重置天弓千亦交易状态 + 无碰撞状态
        OnGameEnd.EVENT.register((level, comp) -> {
            ChimataTradeManager.reset();
            com.dim201.dim201addon.item.card.NoClipManager.reset();
            // 重置神明吞噬者时刻状态（同时关闭天蓝色雾）
            DevourerGodManager.reset(level);
            // 清空东堂×虎杖默契状态（兜底，防止 ResetPlayerEvent 未覆盖到的玩家）
            BrotherSyncManager.resetAll();
            // 清空镇静状态
            com.dim201.dim201addon.item.SedativeManager.clearAll();
            // 清空电击狂人静电状态（同时通知客户端清除方块高亮）
            com.dim201.dim201addon.role.electric.ElectricManiacManager.clearAll(level.getServer());
            // 清空神秘礼盒开局时间记录
            com.dim201.dim201addon.item.MysteryGiftBoxItem.clearGameStartTime(level);
        });

        // 对局开始时记录开局 tick（供神秘礼盒 1 分钟冷却判定）
        com.dim201.dim201addon.item.MysteryGiftBoxItem.registerGameStartListener();

        // ===== 指令注册 =====
        // /dim201:devourergod <player> — 直接触发神明吞噬者时刻
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DevourerGodCommand.register(dispatcher));
        // /dim201:lonewolfsettlement <player> — 直接触发独狼清算时间
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LoneWolfSettlementCommand.register(dispatcher));

        // ===== 吞噬神明修饰符注册 =====
        // 原理：DEVOURER_GOD 修饰符需在 SEModifiers.init() 之后注册（HMLModifiers.MODIFIERS 列表），
        // ModifierAssigned 事件监听吞噬者获得 REFUGEE 时替换为 DEVOURER_GOD。
        DevourerGodModifier.init();
        // 神明吞噬者时刻管理器：注册 ServerTickEvents + OnPlayerDeath
        DevourerGodManager.init();
        LOGGER.info("吞噬神明修饰符 + 管理器初始化完成");

        // 镇静剂状态管理：每 tick 末尾锁定镇静玩家的心情值
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK
                .register(server -> com.dim201.dim201addon.item.SedativeManager.tickAll(server));

        // 电击狂人静电系统：每 tick 处理方块影响和玩家触电
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK
                .register(server -> com.dim201.dim201addon.role.electric.ElectricManiacManager.serverTick(server));

        // ===== 乐观修饰符 =====
        OptimisticModifier.init();

        // ===== shuang_yue_DDD 修饰符 =====
        ShuangyueDDDModifier.init();

        // 通行证/皮肤/称号系统均已拆分至独立模组 dim201fix
    }

    /**
     * 能力卡牌交易触发
     *
     * <p>原理：监听 {@link AttackEntityCallback}，当玩家手持能力卡牌左键点击
     * 另一名玩家时，创建 {@link CardTradeSession} 并打开双方交易菜单。
     *
     * <p>校验条件：
     * <ul>
     *   <li>主手/副手持有能力卡牌</li>
     *   <li>目标为存活玩家</li>
     *   <li>不是自己</li>
     *   <li>双方都在对局中且存活</li>
     *   <li>距离 ≤ 5 格</li>
     * </ul>
     */
    private void registerCardTradeHandler() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer initiator)) return InteractionResult.PASS;

            // 必须手持能力卡牌
            ItemStack held = initiator.getItemInHand(hand);
            if (!Dim201ModItems.isAbilityCard(held)) return InteractionResult.PASS;

            // 目标必须是玩家
            if (!(entity instanceof ServerPlayer receiver)) return InteractionResult.PASS;

            // 不能和自己交易
            if (initiator.getUUID().equals(receiver.getUUID())) return InteractionResult.PASS;

            // 双方必须在对局中且存活
            if (!GameUtils.isPlayerAliveAndSurvival(initiator)
                    || !GameUtils.isPlayerAliveAndSurvival(receiver)) {
                return InteractionResult.PASS;
            }

            // 距离限制（5格内）
            if (initiator.distanceTo(receiver) > 5.0) {
                initiator.displayClientMessage(Component.literal("§c距离太远，无法交易"), true);
                return InteractionResult.FAIL;
            }

            // 发起交易
            CardTradeSession session = new CardTradeSession(initiator, receiver);
            session.openBoth();
            return InteractionResult.CONSUME;
        });
    }

    private void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(SwitchSkillC2SPacket.ID, SwitchSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SwitchSkillC2SPacket.ID, SwitchSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DevourerEatC2SPacket.TYPE, DevourerEatC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DevourerKillerEscapeC2SPacket.TYPE, DevourerKillerEscapeC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SwitchDaoLeirenModeC2SPacket.ID, SwitchDaoLeirenModeC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SwitchDaoLeirenModeC2SPacket.ID, SwitchDaoLeirenModeC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ClownpieceRefreshC2SPacket.TYPE, ClownpieceRefreshC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PucciDisguiseC2SPacket.TYPE, PucciDisguiseC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(PucciDisguiseS2CPacket.TYPE, PucciDisguiseS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PhantomThiefAnnounceC2SPacket.TYPE, PhantomThiefAnnounceC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PhantomThiefStealC2SPacket.TYPE, PhantomThiefStealC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeikibankiFlyInputC2SPacket.TYPE, SeikibankiFlyInputC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(LoneWolfDroneInputC2SPacket.TYPE, LoneWolfDroneInputC2SPacket.CODEC);
        // 独狼技能键（部署/切换无人机）
        PayloadTypeRegistry.playC2S().register(LoneWolfSkillC2SPacket.TYPE, LoneWolfSkillC2SPacket.CODEC);
        // 东堂 × 虎杖合作技
        PayloadTypeRegistry.playC2S().register(com.dim201.dim201addon.packet.BrotherSkillC2SPacket.TYPE,
                com.dim201.dim201addon.packet.BrotherSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(com.dim201.dim201addon.packet.BrotherSyncS2CPacket.TYPE,
                com.dim201.dim201addon.packet.BrotherSyncS2CPacket.CODEC);
        // 电击狂人
        PayloadTypeRegistry.playC2S().register(com.dim201.dim201addon.packet.electric.ElectricManiacChargeC2SPacket.TYPE,
                com.dim201.dim201addon.packet.electric.ElectricManiacChargeC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(com.dim201.dim201addon.packet.electric.ElectricManiacBlockC2SPacket.TYPE,
                com.dim201.dim201addon.packet.electric.ElectricManiacBlockC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(com.dim201.dim201addon.packet.electric.ElectricManiacBlocksS2CPacket.TYPE,
                com.dim201.dim201addon.packet.electric.ElectricManiacBlocksS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(com.dim201.dim201addon.packet.electric.ElectricManiacShockS2CPacket.TYPE,
                com.dim201.dim201addon.packet.electric.ElectricManiacShockS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(com.dim201.dim201addon.packet.electric.ElectricManiacBlastS2CPacket.TYPE,
                com.dim201.dim201addon.packet.electric.ElectricManiacBlastS2CPacket.CODEC);
        // 八云紫右键方块设置坐标
        PayloadTypeRegistry.playC2S().register(YukariSetCoordinateC2SPacket.TYPE,
                YukariSetCoordinateC2SPacket.CODEC);
        // 恶魔契约结算结果
        PayloadTypeRegistry.playS2C().register(com.dim201.dim201addon.packet.DemonContractResultS2CPacket.TYPE,
                com.dim201.dim201addon.packet.DemonContractResultS2CPacket.CODEC);

        LOGGER.info("网络包 Payload 类型注册完成");
    }

    /** 赤蛮奇飞头驾驶输入：服务端校验技能状态后权威驱动头颅实体 */
    private void registerSeikibankiFlyInputHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            SeikibankiFlyInputC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.SHIKIBANKI.getNullable(player);
                    if (comp != null) {
                        comp.handleFlyInput(player, payload.movementBits(), payload.yaw(), payload.pitch());
                    }
                });
            }
        );
    }

    /** 独狼无人机驾驶输入：移动 + 射线发射 */
    private void registerLoneWolfDroneInputHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            LoneWolfDroneInputC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.LONE_WOLF.getNullable(player);
                    if (comp != null) {
                        comp.handleDroneInput(player, payload.movementBits(), payload.yaw(), payload.pitch(), payload.fire(),
                                payload.x(), payload.y(), payload.z());
                    }
                });
            }
        );
    }

    /** 独狼技能键：未部署 → 部署无人机；已部署 → 切换操控目标 */
    private void registerLoneWolfSkillHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            LoneWolfSkillC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    var comp = ModComponents.LONE_WOLF.getNullable(player);
                    if (comp == null) return;
                    if (comp.droneActive) {
                        comp.toggleControl(player);
                    } else {
                        comp.deployDrone(player);
                    }
                });
            }
        );
    }

    private void registerMeleeKillHandler() {
        // MeleeKillHandler.registerRole(BoyRole.BOY, "boy_melee_kill"); // 待重做
        // MeleeKillHandler.registerRole(VanDDFRole.VAN_DDF, "van_ddf_melee_kill"); // 待重做
        // MeleeKillHandler.registerRole(BiollanteRole.BIOLLANTE, "biollante_melee_kill"); // 待重做
        MeleeKillHandler.register();
        LOGGER.info("近战连击击杀处理器注册完成");
    }

    /**
     * 东堂 × 虎杖合作技按键：同一个 C2S 包按发送者角色分流
     * （东堂开启不义游戏窗口 / 虎杖响应互换）。
     */
    private void registerBrotherSkillPacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            com.dim201.dim201addon.packet.BrotherSkillC2SPacket.TYPE,
            (payload, context) -> context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.isSpectator()) return;
                if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                    || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                    return;
                }
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                if (gameWorld == null || !gameWorld.isSkillAvailable) return;

                if (gameWorld.isRole(player, TodoAoiRole.TODO_AOI)) {
                    com.dim201.dim201addon.cooperation.BrotherSyncManager.onTodoPress(player);
                } else if (gameWorld.isRole(player, ItadoriRole.ITADORI)) {
                    com.dim201.dim201addon.cooperation.BrotherSyncManager.onItadoriPress(player);
                }
            })
        );
    }

    private void registerSwitchSkillPacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            SwitchSkillC2SPacket.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    var gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null) return;

                    var role = gameWorld.getRole(player);
                    if (role == null) return;

                    if (role.equals(YukariRole.YUKARI)) {
                        YukariPlayerComponent comp = ModComponents.YUKARI.get(player);
                        if (comp != null) {
                            comp.switchSkill(player);
                        }
                    } else if (role.equals(ReimuRole.REIMU)) {
                        return;
                    } else if (role.equals(WhiteSnakeRole.WHITESNAKE)) {
                        WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
                        if (comp != null) {
                            comp.switchSkill(player);
                        }
                    } else if (role.equals(AdachiRole.ADACHI)) {  // ← 新增
                        AdachiRole.onSwitchSkill(player);
                    } else if (role.equals(NarukamiRole.NARUKAMI)) {
                        NarukamiRole.onSwitchSkill(player);
                    } else if (role.equals(ElectricManiacRole.ELECTRIC_MANIAC)) {
                        com.dim201.dim201addon.role.electric.ElectricManiacManager.switchSkill(player);
                    }
                });
            }
        );
    }

    private void registerDevourerPacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            DevourerEatC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;

                    // 技能禁用效果检查（SKILL_BANED/SKILL_FREEZED 对所有角色生效）
                    if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        return;
                    }

                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null) return;

                    // 神明吞噬者：豁免全局技能禁用（isSkillAvailable），
                    // 因为其自身时刻触发的 disableSkillsAndSync 是为禁用其他玩家技能，
                    // 神明吞噬者自身应可正常使用吞噬技能
                    if (gameWorld.isRole(player, DevourerGodRole.DEVOURER_GOD_ROLE)) {
                        DevourerGodPlayerComponent godComp = ModComponents.DEVOURER_GOD.get(player);
                        // 收集身边 1.5 格内所有存活玩家（近身范围吞噬，无需视线判定）
                        double rangeSqr = DevourerGodPlayerComponent.DEVOUR_RANGE
                                * DevourerGodPlayerComponent.DEVOUR_RANGE;
                        List<ServerPlayer> targets = new ArrayList<>();
                        for (ServerPlayer p : player.serverLevel()
                            .getPlayers(p -> p != player && GameUtils.isPlayerAliveAndSurvival(p))) {
                            if (player.distanceToSqr(p) <= rangeSqr) {
                                targets.add(p);
                            }
                        }
                        godComp.tryDevourAll(targets);
                        return;
                    }

                    // 普通吞噬者及其他角色：受全局技能禁用约束
                    if (!gameWorld.isSkillAvailable) return;

                    if (!gameWorld.isRole(player, DevourerRole.DEVOURER_ROLE)) return;

                    DevourerPlayerComponent comp = ModComponents.DEVOURER.get(player);

                    // 已有吞噬目标 → 吐出
                    if (comp.swallowedUUID != null) {
                        comp.releaseSwallowed();
                        return;
                    }

                    // 无吞噬目标 → 寻找 2.15 格内最近存活玩家
                    ServerPlayer target = null;
                    double closest = DevourerRole.EAT_RANGE * DevourerRole.EAT_RANGE;
                    for (ServerPlayer p : player.serverLevel()
                        .getPlayers(p -> p != player && GameUtils.isPlayerAliveAndSurvival(p))) {
                        double dist = player.distanceToSqr(p);
                        if (dist < closest && player.hasLineOfSight(p)) {
                            closest = dist;
                            target = p;
                        }
                    }

                    if (target != null) {
                        comp.tryEat(target);
                    } else {
                        player.displayClientMessage(
                            Component.literal("§c面前没有可吞噬的目标"),
                            true);
                    }
                });
            }
        );
        LOGGER.info("吞噬者网络包处理器注册完成");

        // ===== 电击狂人网络包处理器 =====
        registerElectricManiacPacketHandlers();

        // ===== 八云紫网络包处理器 =====
        registerYukariPacketHandlers();
    }

    private void registerYukariPacketHandlers() {
        // 右键方块 → 设置坐标（替代原技能轮盘中的「设置坐标」）
        ServerPlayNetworking.registerGlobalReceiver(
                YukariSetCoordinateC2SPacket.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;
                    var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isRole(player, YukariRole.YUKARI)) return;
                    if (!GameUtils.isPlayerAliveAndSurvival(player)) return;
                    net.minecraft.core.BlockPos pos = payload.pos();
                    // 校验距离（防止作弊）：8 格内
                    if (player.distanceToSqr(pos.getCenter()) > 64) return;
                    ModComponents.YUKARI.get(player).addCoordinate(
                            player,
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                            player.level().dimension().location().toString());
                }));
        LOGGER.info("八云紫网络包处理器注册完成");
    }

    private void registerElectricManiacPacketHandlers() {
        // 按技能键 → 自身充电
        ServerPlayNetworking.registerGlobalReceiver(
                com.dim201.dim201addon.packet.electric.ElectricManiacChargeC2SPacket.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;
                    if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                            || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) return;
                    if (!com.dim201.dim201addon.role.electric.ElectricManiacManager.isElectricManiac(player)) return;
                    com.dim201.dim201addon.role.electric.ElectricManiacManager.useSkill(player);
                }));

        // 右键方块 → 附着静电
        ServerPlayNetworking.registerGlobalReceiver(
                com.dim201.dim201addon.packet.electric.ElectricManiacBlockC2SPacket.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;
                    if (!com.dim201.dim201addon.role.electric.ElectricManiacManager.isElectricManiac(player)) return;
                    net.minecraft.core.BlockPos pos = payload.pos();
                    // 校验距离（防止作弊）
                    if (player.distanceToSqr(pos.getCenter()) > 64) return; // 8 格内
                    com.dim201.dim201addon.role.electric.ElectricManiacManager.placeStaticBlock(player.level(), pos);
                }));
        LOGGER.info("电击狂人网络包处理器注册完成");
    }

    /**
     * 杀手在肚内按技能键 → 触发开膛破肚反杀吞噬者
     *
     * 客户端检测：只有当玩家被 PelicanManager 标记为 stashed（即被某人吞掉），
     * 且对应吞人的那位玩家是吞噬者角色时，才会发送这个包。
     * 服务端在这里再做一次完整校验以防伪造。
     */
    private void registerDevourerKillerEscapePacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            DevourerKillerEscapeC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer killer = context.player();
                    // 被吞下后 SRE 会切到 spectator 模式，因此不检查 isSpectator；只靠 PelicanManager.isStashed + killerTryEscape 内部校验即可
                    // 技能禁用检查：如果有 SKILL_BANED / SKILL_FREEZED 则不允许反杀（虽然吞噬时专门移除了 SKILL_BANED，但其他来源可能施加）
                    if (killer.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || killer.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        killer.displayClientMessage(
                            Component.literal("§c技能被禁用，无法反杀"),
                            true);
                        return;
                    }
                    DevourerPlayerComponent.killerTryEscape(killer);
                });
            }
        );
        LOGGER.info("吞噬者-杀手反杀网络包处理器注册完成");
    }

    /**
     * 蕾忍切换攻击模式网络包处理器
     * 客户端按 G 键 → 发送 SwitchDaoLeirenModeC2SPacket → 服务端调用组件 switchMode
     */
    private void registerSwitchDaoLeirenModePacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            SwitchDaoLeirenModeC2SPacket.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;

                    // 技能禁用检查
                    if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        return;
                    }

                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isSkillAvailable) return;
                    if (!gameWorld.isRole(player, DaoLeirenRole.DAO_LEIREN)) return;

                    DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(player);
                    if (comp != null) {
                        comp.switchMode(player);
                    }
                });
            }
        );
        LOGGER.info("蕾忍切换攻击模式网络包处理器注册完成");
    }


    /**
     * 大怪盗网络包处理器：
     * - 预告信（背包界面点击玩家头像 → PhantomThiefAnnounceC2SPacket）：校验后发送预告
     * - 妙手空空（G 键 → PhantomThiefStealC2SPacket）：校验后开始读条
     */
    private void registerPhantomThiefPacketHandler() {
        // 预告信
        ServerPlayNetworking.registerGlobalReceiver(
            PhantomThiefAnnounceC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;
                    if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        player.displayClientMessage(Component.literal("§c技能被封印，无法发送预告信"), true);
                        return;
                    }
                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isRunning()
                            || !gameWorld.isRole(player, PhantomThiefRole.PHANTOM_THIEF)) return;

                    PhantomThiefPlayerComponent comp = ModComponents.PHANTOM_THIEF.get(player);
                    if (comp.phase != PhantomThiefPlayerComponent.Phase.NONE) return; // 已有预告进行中

                    // 预告信冷却中（得手/到期后 15 秒）
                    io.wifi.starrailexpress.cca.SREAbilityPlayerComponent abilityComp =
                            io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(player);
                    if (abilityComp != null && abilityComp.cooldown > 0) {
                        int seconds = (abilityComp.cooldown + 19) / 20;
                        player.displayClientMessage(Component.literal(
                                "§c预告信还在冷却中，剩余 §e" + seconds + " §c秒"), true);
                        return;
                    }

                    ServerPlayer target = player.server.getPlayerList().getPlayer(payload.targetPlayer());
                    if (target == null || target == player) return;
                    if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                        player.displayClientMessage(Component.literal("§c目标已不在场上"), true);
                        return;
                    }
                    comp.startAnnouncement(player, target);
                });
            }
        );

        // 妙手空空
        ServerPlayNetworking.registerGlobalReceiver(
            PhantomThiefStealC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;
                    if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        player.displayClientMessage(Component.literal("§c技能被封印，无法行窃"), true);
                        return;
                    }
                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isRunning()
                            || !gameWorld.isRole(player, PhantomThiefRole.PHANTOM_THIEF)) return;

                    ModComponents.PHANTOM_THIEF.get(player).tryStartChannel(player);
                });
            }
        );
        LOGGER.info("大怪盗网络包处理器注册完成");
    }

    /**
     * 克劳恩皮斯：客户端在背包界面点击玩家头像 → 发包到服务端 → 校验金币 → 清除目标背包物品冷却
     */
    private void registerClownpieceRefreshPacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            ClownpieceRefreshC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator()) return;

                    // 技能禁用检查
                    if (player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || player.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        return;
                    }

                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                    if (gameWorld == null || !gameWorld.isSkillAvailable) return;
                    if (!gameWorld.isRole(player, ClownpieceRole.CLOWNPIECE)) return;

                    // 获取目标玩家
                    ServerPlayer target = player.level().getServer()
                            .getPlayerList().getPlayer(payload.targetPlayer());
                    if (target == null) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§c目标不在线"), true);
                        return;
                    }
                    if (!target.isAlive() || target.isSpectator()) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§c目标已死亡或旁观"), true);
                        return;
                    }

                    // 冷却检查（和普奇神父「认知妨碍」同款，用 SREAbilityPlayerComponent）
                    io.wifi.starrailexpress.cca.SREAbilityPlayerComponent abilityComp =
                            io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(player);
                    if (abilityComp.cooldown > 0) {
                        int seconds = (abilityComp.cooldown + 19) / 20;
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "§c「燃烧生命的火把」冷却中，剩余 §f" + seconds + " §c秒"), true);
                        return;
                    }

                    // 金币检查
                    io.wifi.starrailexpress.cca.SREPlayerShopComponent shop =
                            io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < ClownpieceRole.REFRESH_TARGET_COST) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "§c金币不足！需要 " + ClownpieceRole.REFRESH_TARGET_COST
                                        + " 金币，当前 " + shop.balance), true);
                        return;
                    }

                    // 扣款 + 刷新冷却
                    shop.setBalance(shop.balance - ClownpieceRole.REFRESH_TARGET_COST);
                    shop.sync();

                    // 消耗技能冷却（自动 tick 递减 + sync 到客户端 UI）
                    abilityComp.setCooldown(ClownpieceRole.REFRESH_COOLDOWN);

                    int cleared = ClownpieceRole.clearAllCooldowns(target);

                    String msg = String.format("§6燃烧生命的火把！刷新了 §f%s §6的 §f%d §6个物品冷却",
                            target.getName().getString(), cleared);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(msg), true);
                });
            }
        );
        LOGGER.info("克劳恩皮斯刷新冷却网络包处理器注册完成");
    }

    /**
     * 普奇神父：两阶段变形请求（「认知妨碍」）。
     *
     * <p>服务端流程：
     * 1. 校验角色身份 + 双方存活 + 技能可用
     * 2. 查普奇自己身上的 RoleData → 检查冷却（60s）
     * 3. 用 RoleData.getOrCreate 在被变形者身上动态创建 RoleData
     *    → setDisguiseTarget(外观目标UUID) → sync() 广播到所有客户端
     * 4. 消耗普奇自己的冷却
     *
     * <p>为什么 disguise 数据存在被变形者身上（Morphling 同款）？
     * 皮肤渲染 Mixin 直接查被渲染玩家自己的 RoleData，
     * 数据和使用者在同一个实体上，客户端不需要跨实体访问其他玩家组件。
     * 旧方案把数据存在普奇身上 → 客户端遍历永远找不到 → 皮肤替换永远不触发。
     */
    private void registerPucciDisguisePacketHandler() {
        ServerPlayNetworking.registerGlobalReceiver(
            PucciDisguiseC2SPacket.TYPE,
            (payload, context) -> {
                context.server().execute(() -> {
                    ServerPlayer pucci = context.player();
                    if (pucci.isSpectator()) return;

                    // 角色校验
                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(pucci.level());
                    if (gameWorld == null || !gameWorld.isSkillAvailable) return;
                    if (!gameWorld.isRole(pucci, PucciRole.PUCCI)) return;

                    // 技能禁用检查
                    if (pucci.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_BANED)
                        || pucci.hasEffect(org.agmas.noellesroles.init.ModEffects.SKILL_FREEZED)) {
                        return;
                    }

                    // 获取两个目标
                    ServerPlayer transformTarget = pucci.level().getServer()
                            .getPlayerList().getPlayer(payload.transformTarget());
                    ServerPlayer appearanceTarget = pucci.level().getServer()
                            .getPlayerList().getPlayer(payload.appearanceTarget());

                    if (transformTarget == null || appearanceTarget == null) {
                        pucci.displayClientMessage(
                                Component.literal("§c目标不在线"), true);
                        return;
                    }
                    if (!transformTarget.isAlive() || !appearanceTarget.isAlive()) {
                        pucci.displayClientMessage(
                                Component.literal("§c目标已死亡，无法变形"), true);
                        return;
                    }
                    if (transformTarget == appearanceTarget) {
                        pucci.displayClientMessage(
                                Component.literal("§c不能让玩家变成自己"), true);
                        return;
                    }

                    // 冷却检查：用 SREAbilityPlayerComponent（自动 tick 递减 + 自动 sync）
                    io.wifi.starrailexpress.cca.SREAbilityPlayerComponent abilityComp =
                            io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(pucci);

                    if (abilityComp.cooldown > 0) {
                        int seconds = (abilityComp.cooldown + 19) / 20;
                        pucci.displayClientMessage(
                                Component.literal("§c「认知妨碍」冷却中，剩余 §f" + seconds + " §c秒"), true);
                        return;
                    }

                    // 核心：直接广播 S2C 给所有客户端，不走 RoleData
                    // 因为被变形者没有绑定 setRoleData(PucciDisguiseRoleData::new)，
                    // RoleData.getOrCreate 在非角色玩家上不会创建组件
                    PucciDisguiseS2CPacket s2c = new PucciDisguiseS2CPacket(
                            payload.transformTarget(), payload.appearanceTarget());
                    for (ServerPlayer p : pucci.level().getServer().getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(p, s2c);
                    }

                    // 消耗冷却：abilityComp 已在上方获取，直接 setCooldown
                    // 自动 tick 递减 + 自动 sync 到客户端，UI 直接读 cooldown 显示秒数
                    abilityComp.setCooldown(PucciDisguiseRoleData.DISGUISE_COOLDOWN);

                    pucci.displayClientMessage(
                            Component.literal("§6「认知妨碍」：§f" + transformTarget.getName().getString()
                                    + " §6已被塑造成 §f" + appearanceTarget.getName().getString()), true);
                });
            }
        );
        LOGGER.info("普奇神父变形网络包处理器注册完成");
    }

    private void registerLinkedDeathEvents() {
        // 处理有杀手的死亡（武器击杀等）
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (isProcessingLinkedDeath) return;
            if (!(victim instanceof ServerPlayer serverVictim)) return;

            ServerPlayer linked = findLinkedPlayer(serverVictim);
            if (linked != null && linked.isAlive()) {
                isProcessingLinkedDeath = true;
                try {
                    // deathReason 已经是 ResourceLocation，直接使用
                    GameUtils.killPlayer(linked, true, killer, deathReason);

                    LOGGER.info("绑定死亡: {} 死亡导致 {} 死亡 (死因: {})", 
                        serverVictim.getName().getString(), 
                        linked.getName().getString(), 
                        deathReason);

                    broadcastLinkedDeath(linked, serverVictim);
                } finally {
                    isProcessingLinkedDeath = false;
                }
            }
        });

        // 处理无杀手的死亡（摔死、毒死、环境伤害等）
        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (isProcessingLinkedDeath) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ServerPlayer linked = findLinkedPlayer(serverPlayer);
            if (linked != null && linked.isAlive()) {
                isProcessingLinkedDeath = true;
                try {
                    // deathReason 已经是 ResourceLocation，直接使用
                    GameUtils.killPlayer(linked, true, null, deathReason);

                    LOGGER.info("绑定死亡: {} 死亡导致 {} 死亡 (死因: {})", 
                        serverPlayer.getName().getString(), 
                        linked.getName().getString(), 
                        deathReason);

                    broadcastLinkedDeath(linked, serverPlayer);
                } finally {
                    isProcessingLinkedDeath = false;
                }
            }
        });

        LOGGER.info("白蛇-普奇绑定死亡事件注册完成");

        // ===== 倒霉蛋：10 格内有人死亡 → 触发被动 =====
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!(victim instanceof ServerPlayer victimSp)) return;
            MinecraftServer server = victimSp.server;
            // 遍历所有玩家，找到倒霉蛋并尝试触发
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UnluckyBastardPlayerComponent comp = ModComponents.UNLUCKY_BASTARD.get(p);
                comp.tryTriggerPassive(victimSp);
            }
        });

        LOGGER.info("倒霉蛋被动死亡触发事件注册完成");

        // ===== 紫菀：被他人击杀 → 击杀者获得 CURSED 修饰符 + 金币清零 =====
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer victimSp)) return;
            if (killer == null) return;
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());
            if (gw == null) return;
            if (!gw.isRole(victimSp, ShionRole.SHION)) return;
            if (!(killer instanceof ServerPlayer killerSp)) return;
            ShionRole.onShionKilledByKiller(victimSp, killerSp);
        });

        LOGGER.info("紫菀击杀者诅咒事件注册完成");
    }

    /**
     * 广播绑定死亡消息
     */
    private void broadcastLinkedDeath(ServerPlayer dead, ServerPlayer partner) {
        String deadName = dead.getName().getString();
        String partnerName = partner.getName().getString();

        for (ServerPlayer p : dead.getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(
                Component.literal("§c§l「绑定死亡」§f " + deadName + " 与 " + partnerName + " 共赴黄泉"),
                false
            );
        }
    }

    /**
     * 找到死亡者的绑定同伴
     */
    private ServerPlayer findLinkedPlayer(ServerPlayer deadPlayer) {
        var gameWorld = SREGameWorldComponent.KEY.get(deadPlayer.level());
        if (gameWorld == null) return null;

        var role = gameWorld.getRole(deadPlayer);
        if (role == null) return null;

        SRERole targetRole = null;
        if (role.equals(WhiteSnakeRole.WHITESNAKE)) {
            targetRole = PucciRole.PUCCI;
        } else if (role.equals(PucciRole.PUCCI)) {
            targetRole = WhiteSnakeRole.WHITESNAKE;
        } else {
            return null;
        }

        // 遍历所有在线玩家找另一人
        for (ServerPlayer p : deadPlayer.getServer().getPlayerList().getPlayers()) {
            if (p == deadPlayer) continue;
            if (!p.isAlive()) continue;

            var pRole = gameWorld.getRole(p);
            if (pRole != null && pRole.equals(targetRole)) {
                return p;
            }
        }

        return null;
    }

    private void registerLifecycleEvents() {
        ModdedRoleAssigned.EVENT.register((ServerPlayer player, SRERole role) -> {
            // 体力：不在此处设置。SRE 原生机制已处理：
            //   resetPlayer() 在 baseInitialize() 中将 sprintingTicks 设为 -1（哨兵值），
            //   游戏开始 isRunning()=true 后，PlayerEntityMixin.tmm$limitSprint() 在 aiStep 中
            //   检测 sprintingTicks==-1 并转换为 role.getMaxSprintTime()。
            //   客户端在登录时通过 NBT 同步获得 -1，后续各自独立执行 tmm$limitSprint 转换。
            //   若在此处覆写 sprintingTicks，客户端不会收到新值，导致客户端体力条显示不正确。
            // 记录所有玩家在对局开始时的位置（不死鸟之尾复活用）
            PhoenixTailManager.recordGameStartPos(player);
            // 杀手角色：为所有能力卡牌设置 50% 折扣（天弓千亦商店半价）
            KillerCardDiscountManager.applyDiscountIfKiller(player, role);
            if (role.equals(LoneWolfRole.LONE_WOLF)) {
                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(player);
                comp.isDead = false;
            }
            else if (role.equals(TeacherZhangRole.TEACHER_ZHANG)) {
                TeacherZhangPlayerComponent comp = ModComponents.TEACHER_ZHANG.get(player);
                comp.init();
            }
            else if (role.equals(YukariRole.YUKARI)) {
                YukariPlayerComponent comp = ModComponents.YUKARI.get(player);
                comp.init();
            }
            else if (role.equals(ReimuRole.REIMU)) {
                ReimuPlayerComponent comp = ModComponents.REIMU.get(player);
                comp.init();
            }
            else if (role.equals(WhiteSnakeRole.WHITESNAKE)) {
                WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
                comp.init();
            }
            else if (role.equals(PucciRole.PUCCI)) {
                WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
                comp.init();
            }
            else if (role.equals(AdachiRole.ADACHI)) {
                AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
                comp.init();
                // 记录对局开始时的初始位置（复活用）
                comp.setSpawnPos(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                comp.sync();
            }
            else if (role.equals(NarukamiRole.NARUKAMI)) {
                NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
                comp.init();
                comp.sync();
                // 开局30秒安全时间，打刀同步进入30秒冷却
                player.getCooldowns().addCooldown(Dim201ModItems.UCHIGATANA, 30 * 20);
            }
            else if (role.equals(EmiyaRole.EMIYA)) {
                EmiyaPlayerComponent comp = ModComponents.EMIYA.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(GojoRole.GOJO)) {
                GojoPlayerComponent comp = ModComponents.GOJO.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(LegionRole.LEGION)) {
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                comp.init();
                comp.sync();
                // 防止上局/重选角色后深度伤口残留，清空自身身上的深度伤口（没意义的小清理）
                DeepWoundPlayerComponent dw = ModComponents.DEEP_WOUND.get(player);
                if (dw != null) { dw.clear(); dw.sync(player); }
            }
            else if (role.equals(LegionNoShopRole.LEGION_NO_SHOP)) {
                // 无商店军团：复用 LegionPlayerComponent，初始冷却与普通军团一致（120s）
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(SakiRole.SAKI)) {
                SakiPlayerComponent comp = ModComponents.SAKI.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(TicketInspectorRole.TICKET_INSPECTOR)) {
                TicketInspectorPlayerComponent comp = ModComponents.TICKET_INSPECTOR.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(DevourerRole.DEVOURER_ROLE)) {
                DevourerPlayerComponent comp = ModComponents.DEVOURER.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(DevourerGodRole.DEVOURER_GOD_ROLE)) {
                DevourerGodPlayerComponent comp = ModComponents.DEVOURER_GOD.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(DaoLeirenRole.DAO_LEIREN)) {
                DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(player);
                comp.init();
                comp.sync();
                // 开局30秒安全时间，打刀进入冷却
                player.getCooldowns().addCooldown(Dim201ModItems.KATANA, 30 * 20);
            }
            else if (role.equals(ShionRole.SHION)) {
                // 紫菀：重置 begCooldownTicks、被动计时、hasWon 等状态
                // 修复：确保上局/重选角色后 NBT 残留冷却被清零，避免乞讨技能始终"冷却中"
                ShionPlayerComponent comp = ModComponents.SHION.get(player);
                comp.init();
                comp.sync();
                // 修复：手动发放初始物品（碗）。
                // SRE 的 addInitialItemsForRole 会在 ModdedRoleAssigned invoker 先于 listener 执行，
                // 理论上已发放碗；但部分游戏模式/特殊流程（SREMurderGameMode.assignRole 后接
                // assignModifiers 等时序、或测试用 changeRole 命令）会偶发遗漏。
                // 此处复用 RoleUtils.insertStackInFreeSlot 做双保险：已有碗则不重复，无碗则补发。
                for (ItemStack def : role.getDefaultItems()) {
                    if (def == null || def.isEmpty()) continue;
                    boolean alreadyHas = false;
                    for (int i = 0; i < player.getInventory().getContainerSize() && !alreadyHas; i++) {
                        ItemStack s = player.getInventory().getItem(i);
                        if (s != null && s.is(def.getItem())) alreadyHas = true;
                    }
                    if (!alreadyHas) {
                        org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(player, def.copy());
                    }
                }
            }
            else if (role.equals(WeaponMasterRole.WEAPON_MASTER)) {
                WeaponMasterPlayerComponent comp = ModComponents.WEAPON_MASTER.get(player);
                comp.init();
                comp.sync();
            }
            else if (role.equals(TodoAoiRole.TODO_AOI)) {
                ModComponents.TODO_AOI.get(player).init();
            }
            else if (role.equals(ItadoriRole.ITADORI)) {
                ModComponents.ITADORI.get(player).init();
            }
            else if (role.equals(BlindRole.BLIND)) {
                ModComponents.BLIND.get(player).init();
            }
        });

        ModdedRoleRemoved.EVENT.register((Player player, SRERole role) -> {
            // 杀手角色移除：清除能力卡牌折扣（防止切换到非杀手后仍享受半价）
            KillerCardDiscountManager.clearDiscountIfKiller(player, role);
            if (role.equals(LoneWolfRole.LONE_WOLF)) {
                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(player);
                comp.isDead = false;
            }
            else if (role.equals(YukariRole.YUKARI)) {
                YukariPlayerComponent comp = ModComponents.YUKARI.get(player);
                comp.clear();
            }
            else if (role.equals(ReimuRole.REIMU)) {
                ReimuPlayerComponent comp = ModComponents.REIMU.get(player);
                comp.clear();
            }
            else if (role.equals(WhiteSnakeRole.WHITESNAKE)) {
                WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
                comp.clear();
            }
            else if (role.equals(PucciRole.PUCCI)) {
                WhiteSnakePlayerComponent comp = ModComponents.WHITESNAKE.get(player);
                comp.clear();
            }
            else if (role.equals(AdachiRole.ADACHI)) {
                AdachiPlayerComponent comp = ModComponents.ADACHI.get(player);
                comp.clear();
                AdachiRole.resetRoleState();
            }
            else if (role.equals(NarukamiRole.NARUKAMI)) {
                NarukamiPlayerComponent comp = ModComponents.NARUKAMI.get(player);
                comp.clear();
                NarukamiRole.resetRoleState();
            }
            else if (role.equals(EmiyaRole.EMIYA)) {
                EmiyaPlayerComponent comp = ModComponents.EMIYA.get(player);
                comp.clear();
                EmiyaRole.resetRoleState();
            }
            else if (role.equals(GojoRole.GOJO)) {
                GojoPlayerComponent comp = ModComponents.GOJO.get(player);
                comp.clear();
                GojoRole.resetRoleState();
            }
            else if (role.equals(LegionRole.LEGION)) {
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                comp.clear();
                DeepWoundPlayerComponent dw = ModComponents.DEEP_WOUND.get(player);
                if (dw != null) { dw.clear(); dw.sync(player); }
                LegionRole.resetRoleState();
            }
            else if (role.equals(LegionNoShopRole.LEGION_NO_SHOP)) {
                LegionPlayerComponent comp = ModComponents.LEGION.get(player);
                comp.clear();
            }
            else if (role.equals(SakiRole.SAKI)) {
                SakiPlayerComponent comp = ModComponents.SAKI.get(player);
                comp.clear();
                comp.sync();
            }
            else if (role.equals(KidnapperRole.KIDNAPPER)) {
                KidnapperPlayerComponent comp = ModComponents.KIDNAPPER.get(player);
                comp.clear();
                comp.sync();
            }
            else if (role.equals(TicketInspectorRole.TICKET_INSPECTOR)) {
                TicketInspectorPlayerComponent comp = ModComponents.TICKET_INSPECTOR.get(player);
                comp.clear();
                comp.sync();
            }
            else if (role.equals(DevourerRole.DEVOURER_ROLE)) {
                DevourerPlayerComponent comp = ModComponents.DEVOURER.get(player);
                comp.clear();
                comp.sync();
            }
            else if (role.equals(DevourerGodRole.DEVOURER_GOD_ROLE)) {
                DevourerGodPlayerComponent comp = ModComponents.DEVOURER_GOD.get(player);
                comp.clear();
                comp.sync();
            }
            else if (role.equals(DaoLeirenRole.DAO_LEIREN)) {
                DaoLeirenPlayerComponent comp = ModComponents.DAO_LEIREN.get(player);
                comp.clear();
                comp.sync();
                DaoLeirenRole.resetRoleState();
            }
            else if (role.equals(ShionRole.SHION)) {
                ShionPlayerComponent comp = ModComponents.SHION.get(player);
                comp.clear();
                comp.sync();
            }
            else if (role.equals(WeaponMasterRole.WEAPON_MASTER)) {
                WeaponMasterPlayerComponent comp = ModComponents.WEAPON_MASTER.get(player);
                comp.clear();
            }
            else if (role.equals(TodoAoiRole.TODO_AOI)) {
                ModComponents.TODO_AOI.get(player).clear();
            }
            else if (role.equals(ItadoriRole.ITADORI)) {
                ModComponents.ITADORI.get(player).clear();
            }
            else if (role.equals(BlindRole.BLIND)) {
                ModComponents.BLIND.get(player).clear();
            }
        });

        OnPlayerDeath.EVENT.register((player, deathReason) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(serverPlayer);
                comp.isDead = true;
                LoneWolfRole.checkWinCondition(serverPlayer.serverLevel());

                var gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
                if (gameWorld != null) {
                    var role = gameWorld.getRole(serverPlayer);
                    // if (role != null && role.equals(VanDDFRole.VAN_DDF)) { // 待重做
                    //     VanDDFRole.forceRevert(serverPlayer);
                    // }
                    if (role != null && role.equals(WhiteSnakeRole.WHITESNAKE)) {
                        WhiteSnakePlayerComponent wsComp = ModComponents.WHITESNAKE.get(serverPlayer);
                        wsComp.clear();
                    }
                    if (role != null && role.equals(PucciRole.PUCCI)) {
                        WhiteSnakePlayerComponent wsComp = ModComponents.WHITESNAKE.get(serverPlayer);
                        wsComp.clear();
                    }
                    // 吞噬者死亡时释放肚内玩家
                    if (role != null && role.equals(DevourerRole.DEVOURER_ROLE)) {
                        DevourerPlayerComponent devComp = ModComponents.DEVOURER.get(serverPlayer);
                        devComp.clear();
                    }
                    // 东堂葵死亡：给予虎杖左轮手枪
                    if (role != null && role.equals(TodoAoiRole.TODO_AOI)) {
                        BrotherSyncManager.onTodoDeath(serverPlayer);
                    }
                }
            }
        });

        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim instanceof ServerPlayer serverVictim) {
                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(serverVictim);
                comp.isDead = true;
                LoneWolfRole.checkWinCondition(serverVictim.serverLevel());

                var gameWorld = SREGameWorldComponent.KEY.get(serverVictim.level());
                if (gameWorld != null) {
                    var role = gameWorld.getRole(serverVictim);
                    // if (role != null && role.equals(VanDDFRole.VAN_DDF)) { // 待重做
                    //     VanDDFRole.forceRevert(serverVictim);
                    // }
                    if (role != null && role.equals(WhiteSnakeRole.WHITESNAKE)) {
                        WhiteSnakePlayerComponent wsComp = ModComponents.WHITESNAKE.get(serverVictim);
                        wsComp.clear();
                    }
                    if (role != null && role.equals(PucciRole.PUCCI)) {
                        WhiteSnakePlayerComponent wsComp = ModComponents.WHITESNAKE.get(serverVictim);
                        wsComp.clear();
                    }
                    // 吞噬者死亡时释放肚内玩家
                    if (role != null && role.equals(DevourerRole.DEVOURER_ROLE)) {
                        DevourerPlayerComponent devComp = ModComponents.DEVOURER.get(serverVictim);
                        devComp.clear();
                    }
                }
            }
        });

        ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                // 清理不死鸟之尾的对局开始位置记录
                PhoenixTailManager.clearGameStartPos(serverPlayer.getUUID());
                // 清理空白卡牌的已初始化标记（防止下局购买时不清空背包）
                BlankCard.resetState();

                LoneWolfComponent comp = ModComponents.LONE_WOLF.get(serverPlayer);
                comp.isDead = false;

                TeacherZhangPlayerComponent teacherComp = ModComponents.TEACHER_ZHANG.get(serverPlayer);
                teacherComp.clear();

                YukariPlayerComponent yukariComp = ModComponents.YUKARI.get(serverPlayer);
                yukariComp.clear();

                ReimuPlayerComponent reimuComp = ModComponents.REIMU.get(serverPlayer);
                reimuComp.clear();

                WhiteSnakePlayerComponent wsComp = ModComponents.WHITESNAKE.get(serverPlayer);
                wsComp.clear();

                AdachiPlayerComponent adachiComp = ModComponents.ADACHI.get(serverPlayer);
                adachiComp.clear();
                AdachiRole.resetRoleState();

                NarukamiPlayerComponent narukamiComp = ModComponents.NARUKAMI.get(serverPlayer);
                narukamiComp.clear();
                NarukamiRole.resetRoleState();

                EmiyaPlayerComponent emiyaComp = ModComponents.EMIYA.get(serverPlayer);
                emiyaComp.clear();
                EmiyaRole.resetRoleState();

                GojoPlayerComponent gojoComp = ModComponents.GOJO.get(serverPlayer);
                gojoComp.clear();
                GojoRole.resetRoleState();

                LegionPlayerComponent legionComp = ModComponents.LEGION.get(serverPlayer);
                legionComp.clear();
                DeepWoundPlayerComponent dwComp = ModComponents.DEEP_WOUND.get(serverPlayer);
                if (dwComp != null) { dwComp.clear(); dwComp.sync(serverPlayer); }
                LegionRole.resetRoleState();

                SakiPlayerComponent sakiComp = ModComponents.SAKI.get(serverPlayer);
                sakiComp.clear();
                sakiComp.sync();

                TicketInspectorPlayerComponent ticketComp = ModComponents.TICKET_INSPECTOR.get(serverPlayer);
                ticketComp.clear();
                ticketComp.sync();

                DevourerPlayerComponent devourerComp = ModComponents.DEVOURER.get(serverPlayer);
                devourerComp.clear();
                devourerComp.sync();

                // 神明吞噬者：对局结束清除状态
                DevourerGodPlayerComponent devourerGodComp = ModComponents.DEVOURER_GOD.get(serverPlayer);
                devourerGodComp.clear();
                devourerGodComp.sync();

                // 绑架者：对局结束清除状态（释放人质 + 清空 UUID + 重置冷却）
                KidnapperPlayerComponent kidnapperComp = ModComponents.KIDNAPPER.get(serverPlayer);
                kidnapperComp.clear();
                kidnapperComp.sync();

                // 蕾忍：对局结束清除状态（重置攻击模式标记、冷却、燃烧计数）
                DaoLeirenPlayerComponent daoComp = ModComponents.DAO_LEIREN.get(serverPlayer);
                daoComp.clear();
                daoComp.sync();
                DaoLeirenRole.resetRoleState();

                // 倒霉蛋：对局结束清除状态（重置被动触发标记、冷却、刀 UUID）
                UnluckyBastardPlayerComponent unluckyComp = ModComponents.UNLUCKY_BASTARD.get(serverPlayer);
                unluckyComp.clear();
                unluckyComp.sync();

                // 依神紫菀：对局结束清除状态（关键：重置「去吃饭」hasWon 标记、乞讨冷却、被动计时）
                // SRE 原生 clearRoleMap() 不会触发 ModdedRoleRemoved，因此这里是最底层的跨局清除保证，
                // 避免 hasWon=true 写入 playerdata/*.dat 后跨服/重登残留，导致下局开局无法购买「去吃饭」
                ShionPlayerComponent shionComp = ModComponents.SHION.get(serverPlayer);
                shionComp.clear();
                shionComp.sync();

                // 武器大师：对局结束清除零件计数
                WeaponMasterPlayerComponent wmComp = ModComponents.WEAPON_MASTER.get(serverPlayer);
                wmComp.clear();

                // 东堂葵 × 虎杖悠仁：对局结束清除默契状态
                ModComponents.TODO_AOI.get(serverPlayer).clear();
                ModComponents.ITADORI.get(serverPlayer).clear();

                // 电击狂人：对局结束清除静电状态
                ElectricManiacPlayerComponent emComp = ModComponents.ELECTRIC_MANIAC.get(serverPlayer);
                emComp.clear();
                emComp.sync();
            }
        });
        registerLegionKillGuard();
        registerWeaponMasterCrowbarDoorListener();
    }

    /**
     * 武器大师撬棍破门监听：手持撬棍右键 SRE 门时给予精密构件。
     * 原理：UseBlockCallback 在 CrowbarItem.useOn 之前触发，
     * 判断手持撬棍 + 目标方块是 SRE SmallDoorBlock → 给精密构件。
     * 不干扰原逻辑（返回 PASS 让撬棍正常执行破门）。
     */
    private void registerWeaponMasterCrowbarDoorListener() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ItemStack held = sp.getMainHandItem();
            if (!held.is(TMMItems.CROWBAR)) return InteractionResult.PASS;
            // 撬棍冷却中 → 本次右键不会真正破门，不给予精密构件（防止冷却期无限刷构件）
            if (sp.getCooldowns().isOnCooldown(TMMItems.CROWBAR)) return InteractionResult.PASS;
            var gw = SREGameWorldComponent.KEY.get(sp.level());
            if (gw == null || !gw.isRole(sp, WeaponMasterRole.WEAPON_MASTER)) return InteractionResult.PASS;
            // 检查目标是否为 SRE 门
            var blockEntity = world.getBlockEntity(hitResult.getBlockPos());
            if (blockEntity instanceof io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity) {
                WeaponMasterRole.onCrowbarDoorBroken(sp);
            } else {
                // 可能是门的上半部分，查下方
                blockEntity = world.getBlockEntity(hitResult.getBlockPos().below());
                if (blockEntity instanceof io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity) {
                    WeaponMasterRole.onCrowbarDoorBroken(sp);
                }
            }
            return InteractionResult.PASS;  // 不干扰原逻辑
        });
    }

    /** 军团 SRE 击杀守卫：
     *  1) 技能期间军团玩家用 SRE 刀 / 默认击杀时，若非「必杀一击」或「深度伤口时间到期」或「必杀死因」则拒绝
     *     拒绝后目标不会真的死亡（显示「在某某的攻击下存活了下来」，不会触发击杀记录），但这一刻我们就知道 SRE 刀确实 stab 命中了
     *     → 立刻转调 LegionPlayerComponent.onKnifeAttackPlayer 施加深度伤口/刷新技能时间/判必杀（这就是之前 NormalRole.onUseKnifeHit 要干的事，不用依赖角色回调覆盖）
     *  2) 其他玩家任意时候正常击杀（不影响）；
     *  OnPlayerDeath 清伤口；OnKillPlayerTriggered 守卫放这里；
     */
    private void registerLegionKillGuard() {
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceDeath) -> {
            if (killer == null) return TrueFalseResult.PASS;
            var gw = SREGameWorldComponent.KEY.get(killer.level());
            if (gw == null) return TrueFalseResult.PASS;
            SRERole killerRole = gw.getRole(killer);
            // 弗兰克（无商店军团）复用 LegionPlayerComponent，技能期间同样需要拦截默认击杀 → 转为施加深度伤口
            if (killerRole == null || (!killerRole.equals(LegionRole.LEGION)
                    && !killerRole.equals(LegionNoShopRole.LEGION_NO_SHOP))) return TrueFalseResult.PASS;
            LegionPlayerComponent leg = ModComponents.LEGION.get(killer);
            if (leg == null || !leg.skillActive) return TrueFalseResult.PASS; // 技能已关：默认
            // 技能开着时：只允许 ThreadLocal EXECUTING_KILL_NOW=true 的那一下（必杀/伤口时间到）
            if (LegionPlayerComponent.EXECUTING_KILL_NOW.get()) return TrueFalseResult.TRUE;

            // ======== 关键：拦截成功的这一刻就是 SRE 刀 stab 命中的"命中事件" ========
            // （此时目标是存活的 ServerPlayer；killer 是攻击者；deathReason 就是 KNIFE / 枪之类的）
            // 我们立刻施加军团命中逻辑：施加伤口+刷新时间+判必杀/结束（内部判断 nextHitIsExecution / 击中伤口玩家结束）
            if (killer instanceof ServerPlayer k && victim instanceof ServerPlayer v && GameUtils.isPlayerAliveAndSurvival(v)) {
                leg.onKnifeAttackPlayer(k, v);
            }

            // 返回 False → SRE 视为"有护盾挡住了"，目标存活 + 不会触发击杀记录
            return TrueFalseResult.FALSE;
        });
        // OnPlayerDeath 死亡时：
        //   1) 清掉死者身上的深度伤口（防止重影污染）
        //   2) 如果死者是军团玩家 且 野性狂暴技能开启中 → 停止播放 legionskill 音效（技能期死亡不经过 forceEndSkill，会漏停音）
        OnPlayerDeath.EVENT.register((player, reason) -> {
            if (player instanceof ServerPlayer sp) {
                DeepWoundPlayerComponent dw = ModComponents.DEEP_WOUND.get(sp);
                if (dw != null && dw.active) {
                    dw.clear();
                    dw.sync(sp);
                }
                LegionPlayerComponent leg = ModComponents.LEGION.get(sp);
                if (leg != null && leg.skillActive) {
                    LegionPlayerComponent.stopLegionSkillSound(sp);
                }
            }
        });
        registerLegionTapeFrankBreakSreDoors();
    }

    /** 用户 2026-08-11：灵梦封魔针 —— 空手右键即可发射，不需要任何物品。
     *  拦截 Fabric 的 UseBlockCallback + UseItemCallback，保证「右键空空气 / 右键方块（空手）」两种都能触发；
     *  有物品拿在手上时不拦截（防止干扰开箱子、开门、吃药、吃道具等正常右键使用）。
     *  最终调用 ReimuPlayerComponent.useSealingNeedle(ServerPlayer) 复用原逻辑（灵气判定+冷却+穿透门射击）。
     */
    /**
     * 【2026-08-11 方案废弃：用户实测"空手右键空气"在 Fabric 回调下不稳定（SRE ADVENTURE 模式 +
     *   ping 高 + 其他模组事件优先拦截 会导致 UseBlockCallback / UseItemCallback 静默吞掉）】
     * 已替换成「御币物品（ReimuGoheiItem）重写 use()」方案：MC 物品 use() 100% 稳定回调，
     * 不管游戏模式/准星/其他模组，只要玩家手持御币右键必触发。
     *
     * 本方法保留空实现（不注册任何事件），避免其他地方调用时 NPE。
     */
    private static void registerReimuSealingNeedleFireFromEmptyHand() {
        // 空手释放方案已废弃：什么都不注册
        // 现在御币：com.dim201.dim201addon.item.ReimuGoheiItem.use() 调封魔针
    }

    /** 判定：该方块是否属于「玩家会与之进行正常交互（不触发封魔针）的实用方块」
     *  命中规则（任一命中即 PASS）：
     *    1. Block 类名包含关键字（Button/Door/FenceGate/Chest/Barrel/ShulkerBox/
     *       EnderChest/CraftingTable/Furnace/BlastFurnace/Smoker/Anvil/Bed/
     *       EnchantingTable/BrewingStand/Jukebox/NoteBlock/Lectern/钟/Bell/
     *       Stonecutter/Grindstone/CartographyTable/FletchingTable/Loom/SmithingTable/
     *       SmithingTemplate/Campfire/RespawnAnchor/Beacon/Comparator/Repeater/
     *       Trapdoor/Button 等）
     *    2. BlockEntity 类名包含关键字（Lectern/Chest/Door 等 SRE 自定义方块实体）
     *  目的：空手右键这些方块时正常执行它们的 use()，不触发灵梦射针
     */
    private static final java.util.Set<String> REIMU_UTILITY_CLASS_KEYWORDS = java.util.Set.of(
            "button","door","fencegate","chest","barrel","shulkerbox","enderchest",
            "crafting","furnace","blastfurnace","smoker","anvil","bed","enchanting",
            "brewing","jukebox","noteblock","lectern","bell","stonecutter","grindstone",
            "cartography","fletching","loom","smithing","campfire","respawnanchor",
            "beacon","comparator","repeater","trapdoor","dispenser","dropper","hopper",
            "composter","cauldron","commandblock","structureblock","jigsaw","sculptsensor",
            "calibratedsculptsensor","chiseledbookshelf","decoratedpot","crafter"
    );
    private static boolean ReimuIsInteractiveUtilityBlock(BlockState state, Level level, BlockPos pos) {
        if (state == null) return false;
        String blockSimpleName = state.getBlock().getClass().getSimpleName().toLowerCase();
        for (String kw : REIMU_UTILITY_CLASS_KEYWORDS) {
            if (blockSimpleName.contains(kw)) return true;
        }
        // 兜底：BE 类名包含关键字（SRE 的 DoorBlockEntity / LecternBlockEntity / 自定义门方块实体）
        if (level != null && pos != null) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                String beName = be.getClass().getSimpleName().toLowerCase();
                for (String kw : REIMU_UTILITY_CLASS_KEYWORDS) {
                    if (beName.contains(kw)) return true;
                }
            }
        }
        return false;
    }

    /** 弗兰克的混音带（撬棍同款）：改为「右键与门交互」（和商店撬棍 Crowbar 一样的右键操作），
     *  强制永久开启门 + 标记 blasted（无法关闭），不破坏方块本体。
     *  触发条件：军团技能期 + tapeFrankPurchased=true + 手持军刀 SP_KNIFE + 右键 SRE 任何门。
     *  实现：完全复用 DoorBlockEntity.blast()，和原生撬棍 Crowbar 逻辑完全一致。
     */
    private static void registerLegionTapeFrankBreakSreDoors() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.PASS;
            if (hitResult == null) return InteractionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            LegionPlayerComponent comp = ModComponents.LEGION.get(sp);
            if (comp == null || !comp.skillActive || !comp.tapeFrankPurchased) return InteractionResult.PASS;
            ItemStack main = sp.getItemInHand(InteractionHand.MAIN_HAND);
            if (main == null || main.isEmpty()) return InteractionResult.PASS;
            boolean hasKnife = (main.is(ModItems.SP_KNIFE)) ||
                    (main.getItem() != null && "sp_knife".equalsIgnoreCase(BuiltInRegistries.ITEM.getKey(main.getItem()).getPath()));
            if (!hasKnife) return InteractionResult.PASS;
            // 只对 SRE 原生 SmallDoorBlock / TrainDoorBlock / 或 blockEntity instanceof DoorBlockEntity 生效
            BlockPos lowerPos = getSreDoorEntityBlockPosOrNull(state, pos, world);
            if (lowerPos == null) return InteractionResult.PASS;
            if (!(world.getBlockEntity(lowerPos) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity door)) return InteractionResult.PASS;
            if (door.isBlasted()) {
                LegionPlayerComponent.LegionSendActionBar(sp, "§7弗兰克混音带 · 该门已被撬开，保持常开");
                return InteractionResult.SUCCESS;
            }
            door.blast(); // ⭐ 完全复用原生撬棍：立即开 + blasted=true（无法关闭）+ tamperedTool=1 物证留痕
            LegionPlayerComponent.LegionSendActionBar(sp, "§a弗兰克的混音带 · 军刀撬门成功！§f这扇门已永久打开（无法再关闭，与撬棍效果完全相同）");
            return InteractionResult.SUCCESS;
        });
    }

    /** 对于 SRE 的门，BlockEntity（DoorBlockEntity）永远存在「下半块」(HALF=LOWER)。
     *  如果是 SmallDoorBlock：根据 HALF 属性取 LOWER 那格的位置；其他情况（TrainDoorBlock 等单 BlockState 存储的门）直接返回原 pos；如果不是 SRE 门则返回 null。
     */
    private static BlockPos getSreDoorEntityBlockPosOrNull(BlockState state, BlockPos pos, Level level) {
        try {
            if (state.getBlock() instanceof io.wifi.starrailexpress.content.block.SmallDoorBlock) {
                DoubleBlockHalf h = state.getValue(io.wifi.starrailexpress.content.block.SmallDoorBlock.HALF);
                return (h == DoubleBlockHalf.LOWER) ? pos : pos.below();
            }
        } catch (Throwable ignored) {}
        if (level.getBlockEntity(pos) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity) return pos;
        // fallback 命名空间判断：SRE/TMM 自定义门 block 实体可能在下半块，先试 pos.below()
        if (level != null && pos != null) {
            BlockPos below = pos.below();
            if (level.getBlockEntity(below) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity) return below;
        }
        return null;
    }

    private static boolean isSreDoor(@org.jetbrains.annotations.NotNull BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        String ns = id.getNamespace();
        String p  = id.getPath();
        if ("trainmurdermystery".equalsIgnoreCase(ns) || "starrailexpress".equalsIgnoreCase(ns)) return true;
        if (p == null) return false;
        String lp = p.toLowerCase();
        String ln = ns.toLowerCase();
        return (lp.contains("door") || lp.contains("gate")) &&
               (ln.contains("sre") || ln.contains("tmm") || ln.contains("trainmurder") || ln.contains("starrailexpress") ||
                lp.contains("sre_") || lp.contains("tmm_") || lp.contains("s24_") || lp.contains("car_") ||
                lp.contains("cabin") || lp.contains("wagon"));
    }

    /**
     * 判定玩家当前是否处于「对局中存活状态」
     * SRE 框架核心规则：
     *  - 存活玩家 GameType = ADVENTURE（冒险，不是 SURVIVAL！）
     *  - 死亡/出局玩家 GameType = SPECTATOR（旁观者）
     *  官方 API 叫 GameUtils.isPlayerAliveAndSurvival(p)（虽然名字带 Survival，但内部已经把 ADVENTURE 纳为"存活"）
     *  这里再加 fallback：!p.isSpectator() && p.isAlive() && !creative，双保险确保不会在冒险模式对局里误 return false
     */
    public static boolean isInGameAlive(Player p) {
        if (p == null) return false;
        if (p instanceof ServerPlayer sp) {
            try {
                if (GameUtils.isPlayerAliveAndSurvival(sp)) return true;
            } catch (Exception ignored) {}
            return !sp.isSpectator() && sp.isAlive() && !sp.isCreative();
        }
        return !p.isSpectator() && p.isAlive() && !p.isCreative();
    }

    // ============================================================================
    // 【绑架者：被绑架者无法操作】全局事件拦截
    //   - 攻击/用物品/用方块/用实体/破坏方块 → 全部 FAIL
    //   - 每 tick 让被绑架者的 WASD/jump/sprint 失效（设置 velocity = 0，不允许移动）
    // ============================================================================
    private static void registerKidnapperHostageActionBlock() {
        // 1) 攻击实体：被绑架的人不能挥刀砍人
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player == null) return InteractionResult.PASS;
            var c = ModComponents.KIDNAPPER.get(player);
            if (c != null && c.kidnapperUuid != null) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 2) 使用物品：被绑架的人不能开道具/吃药
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player == null) return net.minecraft.world.InteractionResultHolder.pass(ItemStack.EMPTY);
            var c = ModComponents.KIDNAPPER.get(player);
            if (c != null && c.kidnapperUuid != null) {
                return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            return net.minecraft.world.InteractionResultHolder.pass(ItemStack.EMPTY);
        });

        // 3) 使用方块：被绑架的人不能开门/开箱/按按钮
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player == null) return InteractionResult.PASS;
            var c = ModComponents.KIDNAPPER.get(player);
            if (c != null && c.kidnapperUuid != null) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 4) 使用实体：被绑架的人不能右键村民/矿车/船/盔甲架
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player == null) return InteractionResult.PASS;
            var c = ModComponents.KIDNAPPER.get(player);
            if (c != null && c.kidnapperUuid != null) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 5) 破坏方块：被绑架的人不能挖墙
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (player == null) return true;
            var c = ModComponents.KIDNAPPER.get(player);
            if (c != null && c.kidnapperUuid != null) return false;
            return true;
        });
    }
}