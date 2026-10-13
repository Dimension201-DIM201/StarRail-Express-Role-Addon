package com.dim201.dim201addon.item;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.item.BlindStickItem;
import com.dim201.dim201addon.item.ability.GrapplingClawItem;
import com.dim201.dim201addon.item.ability.HypnoticLocketItem;
import com.dim201.dim201addon.item.MysteryGiftBoxItem;
import com.dim201.dim201addon.item.UsbDriveItem;
import com.dim201.dim201addon.item.card.ability.BackDoorCard;
import com.dim201.dim201addon.item.card.ability.BlankCard;
import com.dim201.dim201addon.item.card.ability.BribeDeathCard;
import com.dim201.dim201addon.item.card.ability.IbisChieftainIntimidationCard;
import com.dim201.dim201addon.item.card.ability.KoishiHatCard;
import com.dim201.dim201addon.item.card.ability.MiserTeachingCard;
import com.dim201.dim201addon.item.card.ability.MiracleMalletCard;
import com.dim201.dim201addon.item.card.ability.OfferingCard;
import com.dim201.dim201addon.item.card.ability.PhoenixTailCard;
import com.dim201.dim201addon.item.card.ability.TorchOfLifeCard;
import com.dim201.dim201addon.item.card.ability.UndergroundSunCard;
import com.dim201.dim201addon.item.card.ability.YamabukiCleaverCard;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Dim201ModItems {

    /** 盲人商店专属：盲杖，右键消耗使所有存活玩家发光 1.5 秒 */
    public static final Item BLIND_STICK = register(
            "blind_stick",
            new BlindStickItem(new Item.Properties())
    );

    /**
     * 镇静剂：右键使用获得 30 秒镇静效果，心情值锁定为 50%（疯子减半为 15 秒）。
     *
     * <p>贴图使用自定义红色药水 PNG（不再依赖 POTION_CONTENTS 组件染色，
     * 避免商店/创造等途径出现空瓶子）。
     */
    public static final Item SEDATIVE = register(
            "sedative",
            new SedativeItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON))
    );

    /** 依神紫菀专属初始物品：破碗（套用原版碗贴图，避免 SRE 对局中清空空碗的清理逻辑） */
    public static final Item BROKEN_BOWL = register(
            "broken_bowl",
            new Item(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.COMMON))
    );

    public static final Item ABILITY_DISC = register(
        "ability_disc",
        new AbilityDiscItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC))
    );

    /** 打刀（鸣上悠专属武器）：左键直接击杀，误杀平民自身死亡，使用后30秒冷却 */
    public static final Item UCHIGATANA = register(
        "uchigatana",
        new SwordItem(Tiers.IRON, new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE))
    );

    /** 小丑喇叭：右键消耗，播放自定义音效 clown（8格范围玩家可听），tooltip「！？区区？！」 */
    public static final Item CLOWN_HORN = register(
            "clown_horn",
            new ClownHornItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON))
    );

    /** 博丽灵梦专属初始物品：御币（手持右键 → 发射封魔针，木棍作为材质替代） */
    public static final Item REIMU_GOHEI = register(
            "reimu_gohei",
            new ReimuGoheiItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE))
    );

    /**
     * 野生狗奶：饮品
     *
     * <p>关键设计：必须挂载一个零营养的 {@link FoodProperties}（nutrition=0, saturation=0, 无 effects）。
     * 原因：SRE 的护盾/毒药机制（{@code PlayerEntityMixin.tmm$poisonedFoodEffect}）通过 Mixin 注入到
     * {@code Player.eat(Level, ItemStack, FoodProperties)} 方法——只有带 FoodProperties 的物品
     * 走 {@link ItemUtils#finishUsingItem} → {@code Player.eat()} 路径时，才会处理
     * {@code SREDataComponentTypes.ARMORER / WEAK_ARMORER / POISONER} 数据组件（托盘上倒在饮料上的
     * 防御药剂/毒药）。如果不挂 FoodProperties，喝下野生狗奶会绕过 Player.eat()，
     * 即使托盘倒了防御药剂也不会获得护盾，毒药也不会生效。
     *
     * <p>nutrition=0/saturation=0 确保不影响饱食度，无 effects 确保不施加任何药水效果
     * （用户要求：野生狗奶喝完不给任何药水效果）。
     */
    public static final Item WILD_DOG_MILK = register(
            "wild_dog_milk",
            new WildDogMilkItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.UNCOMMON)
                    .food(new FoodProperties.Builder()
                            .nutrition(0)
                            .saturationModifier(0.0F)
                            .alwaysEdible()  // 饱食度满时也能喝（饮品语义，与原版药水/蜂蜜瓶一致）
                            .build()))
    );

    /** 补票员专属：神秘发票（原版纸贴图），存储物品数据，在商店中可补票 */
    public static final Item INVOICE = register(
            "invoice",
            new InvoiceItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.COMMON))
    );

    /** 蕾忍专属打刀：两种攻击模式（岚刀/燃烧刀），攻击逻辑在 DaoLeirenRole 事件中处理 */
    public static final Item KATANA = register(
            "katana",
            new KatanaItem(Tiers.IRON, new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE))
    );

    /** 舍利子：蕾忍死亡掉落，好人拾取后右键复活蕾忍（末影之眼贴图） */
    public static final Item SARIRA = register(
            "sarira",
            new SariraItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC))
    );

    /** 背后之门：携带生效卡牌，抵挡一次来自背后的伤害后消失 */
    public static final Item BACK_DOOR = register(
            "back_door",
            new BackDoorCard()
    );

    /** 有钱能使鬼推磨：携带生效卡牌，消费200金币回避死亡 */
    public static final Item BRIBE_DEATH = register(
            "bribe_death",
            new BribeDeathCard()
    );

    /** 空白：右键消耗卡牌，夺取面前3格内玩家的全部物品；获得时清空自身背包 */
    public static final Item BLANK = register(
            "blank",
            new BlankCard()
    );

    /** 献给神山的供物：右键消耗卡牌，回收16格内全部掉落物 */
    public static final Item OFFERING = register(
            "offering",
            new OfferingCard()
    );

    /** 小石子帽子：右键消耗卡牌，持续30秒移除自身碰撞体积 */
    public static final Item KOISHI_HAT = register(
            "koishi_hat",
            new KoishiHatCard()
    );

    /** 鬼杰组长的恐吓：右键消耗卡牌，掠夺10格内每个玩家50金币 */
    public static final Item IBIS_INTIMIDATION = register(
            "ibis_intimidation",
            new IbisChieftainIntimidationCard()
    );

    /** 守财奴的教诲：携带生效，每次完成任务额外获得15金币 */
    public static final Item MISER_TEACHING = register(
            "miser_teaching",
            new MiserTeachingCard()
    );

    /** 燃烧生命的火把：右键消耗，刷新所有物品冷却 */
    public static final Item TORCH_OF_LIFE = register(
            "torch_of_life",
            new TorchOfLifeCard()
    );

    /** 万宝槌：携带生效，每次击杀玩家额外获得50金币 */
    public static final Item MIRACLE_MALLET = register(
            "miracle_mallet",
            new MiracleMalletCard()
    );

    /** 山姥的菜刀：右键消耗，投掷菜刀命中直接击杀玩家（每把只能杀1人） */
    public static final Item YAMABUKI_CLEAVER = register(
            "yamabuki_cleaver",
            new YamabukiCleaverCard()
    );

    /** 地底的太阳：右键消耗，5秒无敌+0.5s爆炸+5格范围击杀+结束自爆 */
    public static final Item UNDERGROUND_SUN = register(
            "underground_sun",
            new UndergroundSunCard()
    );

    /** 不死鸟之尾：携带生效，死亡后在出生点复活，消耗此卡牌（仅一次） */
    public static final Item PHOENIX_TAIL = register(
            "phoenix_tail",
            new PhoenixTailCard()
    );

    /** 钩影手：大怪盗专属位移道具，右键发射钩索拉拽自己/拖拽玩家（拴绳贴图） */
    public static final Item PHANTOM_GRAPPLING_CLAW = register(
            "phantom_grappling_claw",
            new GrapplingClawItem()
    );

    /** 迷魂铃：大怪盗专属消耗道具，对准玩家使用使其禁足+禁转向+技能封印 2.5 秒（铃铛贴图） */
    public static final Item PHANTOM_HYPNOTIC_LOCKET = register(
            "phantom_hypnotic_locket",
            new HypnoticLocketItem()
    );

    /** 神秘礼盒：对局开始 1 分钟后可右键开启，随机获得一张能力卡牌（不含空白卡），小概率获得 U 盘 */
    public static final Item MYSTERY_GIFT_BOX = register(
            "mystery_gift_box",
            new MysteryGiftBoxItem(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC))
    );

    /** U 盘：神秘礼盒小概率奖品，后续会赋予机制 */
    public static final Item USB_DRIVE = register(
            "usb_drive",
            new UsbDriveItem()
    );

    /**
     * 「偷来的」标记组件：挂在被大怪盗偷走的物品上，
     * 大怪盗死亡时所有带此标记的物品戏剧性散落一地。
     * 数据组件随物品 NBT 持久化并同步到客户端（ItemStack 组件同步要求必须有网络编解码器）。
     */
    public static final DataComponentType<Boolean> STOLEN_MARK = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "stolen_mark"),
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    private static Item register(String name, Item item) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, name);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    /** 所有能力卡牌集合（用于交易系统判断手持物品是否为能力卡牌） */
    public static final Set<Item> ABILITY_CARDS = Set.of(
            BACK_DOOR, BRIBE_DEATH, BLANK, OFFERING, KOISHI_HAT, IBIS_INTIMIDATION, MISER_TEACHING, TORCH_OF_LIFE, MIRACLE_MALLET, YAMABUKI_CLEAVER, UNDERGROUND_SUN, PHOENIX_TAIL
    );

    /**
     * 能力卡牌价格表（金币）
     * 原理：在类初始化阶段填充，供交易系统查询。
     * 定价依据卡牌效果强度：被动保命类 > 主动夺取类 > 位移辅助类 > 回收辅助类。
     */
    private static final Map<Item, Integer> CARD_PRICES = new HashMap<>();
    static {
        CARD_PRICES.put(BACK_DOOR, 300);       // 被动保命，自动抵挡背后致命攻击
        CARD_PRICES.put(BRIBE_DEATH, 200);      // 与消耗成本一致
        CARD_PRICES.put(BLANK, 0);              // 天弓千亦专属，免费（每期限购1次）
        CARD_PRICES.put(OFFERING, 100);          // 回收掉落物
        CARD_PRICES.put(KOISHI_HAT, 100);         // 移除碰撞体积
        CARD_PRICES.put(IBIS_INTIMIDATION, 150);  // 掠夺金币
        CARD_PRICES.put(MISER_TEACHING, 50);       // 任务奖励
        CARD_PRICES.put(TORCH_OF_LIFE, 300);       // 刷新冷却
        CARD_PRICES.put(MIRACLE_MALLET, 100);       // 击杀奖励金币
        CARD_PRICES.put(YAMABUKI_CLEAVER, 400);     // 投掷菜刀击杀
        CARD_PRICES.put(UNDERGROUND_SUN, 800);      // 核爆清场+自爆
        CARD_PRICES.put(PHOENIX_TAIL, 600);        // 死亡复活
    }

    /** 判断物品堆是否为能力卡牌 */
    public static boolean isAbilityCard(ItemStack stack) {
        return !stack.isEmpty() && ABILITY_CARDS.contains(stack.getItem());
    }

    /**
     * 查询能力卡牌价格
     * @return 价格（金币）；非能力卡牌返回 0
     */
    public static int getCardPrice(Item item) {
        return CARD_PRICES.getOrDefault(item, 0);
    }

    /** 查询物品堆对应卡牌价格 */
    public static int getCardPrice(ItemStack stack) {
        return stack.isEmpty() ? 0 : getCardPrice(stack.getItem());
    }

    public static void init() {
        Dim201Addon.LOGGER.info("Dim201Addon items registered");
    }
}