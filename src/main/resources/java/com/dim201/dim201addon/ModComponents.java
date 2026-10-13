package com.dim201.dim201addon;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.resources.ResourceLocation;

public class ModComponents implements EntityComponentInitializer {
    
    public static final ComponentKey<LoneWolfComponent> LONE_WOLF =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "lone_wolf"),
            LoneWolfComponent.class
        );

    public static final ComponentKey<TeacherZhangPlayerComponent> TEACHER_ZHANG =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "teacher_zhang"),
            TeacherZhangPlayerComponent.class
        );

    public static final ComponentKey<YukariPlayerComponent> YUKARI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "yukari"),
            YukariPlayerComponent.class
        );

    public static final ComponentKey<ReimuPlayerComponent> REIMU =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "reimu"),
            ReimuPlayerComponent.class
        );

    public static final ComponentKey<KoishiPlayerComponent> KOISHI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "koishi"),
            KoishiPlayerComponent.class
        );

    // 白蛇组件
    public static final ComponentKey<WhiteSnakePlayerComponent> WHITESNAKE =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "whitesnake"),
            WhiteSnakePlayerComponent.class
        );

    public static final ComponentKey<AdachiPlayerComponent> ADACHI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "adachi"),
            AdachiPlayerComponent.class
        );

    public static final ComponentKey<NarukamiPlayerComponent> NARUKAMI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "narukami"),
            NarukamiPlayerComponent.class
        );

    public static final ComponentKey<EmiyaPlayerComponent> EMIYA =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "emiya"),
            EmiyaPlayerComponent.class
        );

    public static final ComponentKey<GojoPlayerComponent> GOJO =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "gojo"),
            GojoPlayerComponent.class
        );

    public static final ComponentKey<LegionPlayerComponent> LEGION =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "legion"),
            LegionPlayerComponent.class
        );

    public static final ComponentKey<SakiPlayerComponent> SAKI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "saki"),
            SakiPlayerComponent.class
        );

    public static final ComponentKey<DeepWoundPlayerComponent> DEEP_WOUND =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "deep_wound"),
            DeepWoundPlayerComponent.class
        );

    public static final ComponentKey<KidnapperPlayerComponent> KIDNAPPER =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "kidnapper"),
            KidnapperPlayerComponent.class
        );

    // 补票员组件：平民阵营，管理发票列表
    public static final ComponentKey<TicketInspectorPlayerComponent> TICKET_INSPECTOR =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "ticket_inspector"),
            TicketInspectorPlayerComponent.class
        );

    // 吞噬者组件：平民阵营，吞噬/吐出玩家 + 冷却
    public static final ComponentKey<DevourerPlayerComponent> DEVOURER =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "devourer"),
            DevourerPlayerComponent.class
        );

    // 蕾忍组件：好人阵营，蕾克拉/攻击模式/连击/舍利子
    public static final ComponentKey<DaoLeirenPlayerComponent> DAO_LEIREN =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "dao_leiren"),
            DaoLeirenPlayerComponent.class
        );

    // 神明吞噬者组件：吞噬技能（3s CD）+ 速度3 + 护盾递增
    public static final ComponentKey<DevourerGodPlayerComponent> DEVOURER_GOD =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "devourer_god"),
            DevourerGodPlayerComponent.class
        );

    // 倒霉蛋组件：平民阵营，被动能力（10格内死亡 → 刀 + 30s 冷却 + 75金币）
    public static final ComponentKey<UnluckyBastardPlayerComponent> UNLUCKY_BASTARD =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "unlucky_bastard"),
            UnluckyBastardPlayerComponent.class
        );

    // 依神紫菀组件：中立阵营，被动扣金币/乞讨CD/胜利标记
    public static final ComponentKey<ShionPlayerComponent> SHION =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "shion"),
            ShionPlayerComponent.class
        );

    // 强盗组件：警长阵营，信念考验/不是今天/战斗技巧 + 回形针限购
    public static final ComponentKey<DismasPlayerComponent> DISMAS =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "dismas"),
            DismasPlayerComponent.class
        );

    // 大怪盗组件：中立阵营，预告信/妙手空空/收藏层数/胜利计数 + 道具限购状态
    public static final ComponentKey<PhantomThiefPlayerComponent> PHANTOM_THIEF =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "phantom_thief"),
            PhantomThiefPlayerComponent.class
        );

    public static final ComponentKey<WeaponMasterPlayerComponent> WEAPON_MASTER =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "weapon_master"),
            WeaponMasterPlayerComponent.class
        );

    // 赤蛮奇组件：平民阵营，飞头蛮技能（头颅脱离本体 + 90s 冷却 + 60s 失明）
    public static final ComponentKey<SeikibankiPlayerComponent> SHIKIBANKI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "seikibanki"),
            SeikibankiPlayerComponent.class
        );

    // 东堂葵组件：平民阵营，不义游戏响应窗口 + 及川学派被动体力（共享状态在 BrotherSyncManager）
    public static final ComponentKey<TodoAoiPlayerComponent> TODO_AOI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "todo_aoi"),
            TodoAoiPlayerComponent.class
        );

    // 虎杖悠仁组件：警长阵营，逕庭拳空手连击击杀 + 不义游戏专注状态
    public static final ComponentKey<ItadoriPlayerComponent> ITADORI =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "itadori"),
            ItadoriPlayerComponent.class
        );

    // 盲人组件：平民阵营，被动失明 + 透视
    public static final ComponentKey<BlindPlayerComponent> BLIND =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "blind"),
            BlindPlayerComponent.class
        );

    // 电击狂人组件：杀手阵营，静电状态
    public static final ComponentKey<ElectricManiacPlayerComponent> ELECTRIC_MANIAC =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "electric_maniac"),
            ElectricManiacPlayerComponent.class
        );

    // 恶魔契约者组件：平民阵营，契约力量/烙印/引导计时
    public static final ComponentKey<DemonContractorComponent> DEMON_CONTRACTOR =
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath("dim201addon", "demon_contractor"),
            DemonContractorComponent.class
        );

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(LONE_WOLF, LoneWolfComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(TEACHER_ZHANG, TeacherZhangPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(YUKARI, YukariPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(REIMU, ReimuPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(KOISHI, KoishiPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(WHITESNAKE, WhiteSnakePlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(ADACHI, AdachiPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(NARUKAMI, NarukamiPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(EMIYA, EmiyaPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(GOJO, GojoPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(LEGION, LegionPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(SAKI, SakiPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(DEEP_WOUND, DeepWoundPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(KIDNAPPER, KidnapperPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(TICKET_INSPECTOR, TicketInspectorPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(DEVOURER, DevourerPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(DAO_LEIREN, DaoLeirenPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(DEVOURER_GOD, DevourerGodPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(UNLUCKY_BASTARD, UnluckyBastardPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(SHION, ShionPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(DISMAS, DismasPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(PHANTOM_THIEF, PhantomThiefPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(WEAPON_MASTER, WeaponMasterPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(SHIKIBANKI, SeikibankiPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(TODO_AOI, TodoAoiPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(ITADORI, ItadoriPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(BLIND, BlindPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(ELECTRIC_MANIAC, ElectricManiacPlayerComponent::new, RespawnCopyStrategy.NEVER_COPY);
        registry.registerForPlayers(DEMON_CONTRACTOR, DemonContractorComponent::new, RespawnCopyStrategy.NEVER_COPY);
    }
}