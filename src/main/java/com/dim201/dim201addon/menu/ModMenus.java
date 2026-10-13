package com.dim201.dim201addon.menu;

import com.dim201.dim201addon.Dim201Addon;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 模组菜单类型注册
 *
 * <p>原理：使用 Fabric 的 {@link ExtendedScreenHandlerType} 支持传递额外数据
 * （TradeData：isInitiator + partnerName）到客户端 MenuType factory。
 */
public class ModMenus {

    /**
     * 交易数据（服务端→客户端同步）
     *
     * @param isInitiator  当前玩家是否为发起方（卖方）
     * @param partnerName  对方玩家名
     * @param buyerIsKiller  接收方（买方）是否为杀手角色——决定买方是否看到"半价"提示
     * @param sellerIsKiller  发起方（卖方）是否为杀手角色——决定卖方是否看到"50%惩罚"提示
     */
    public record TradeData(boolean isInitiator, String partnerName,
                            boolean buyerIsKiller, boolean sellerIsKiller) {
        /** StreamCodec：序列化/反序列化 TradeData */
        public static final StreamCodec<RegistryFriendlyByteBuf, TradeData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, data) -> {
                            buf.writeBoolean(data.isInitiator);
                            buf.writeUtf(data.partnerName);
                            buf.writeBoolean(data.buyerIsKiller);
                            buf.writeBoolean(data.sellerIsKiller);
                        },
                        buf -> new TradeData(
                                buf.readBoolean(), buf.readUtf(),
                                buf.readBoolean(), buf.readBoolean())
                );
    }

    /** 卡牌交易菜单类型 */
    public static final ExtendedScreenHandlerType<CardTradeMenu, TradeData> CARD_TRADE =
            Registry.register(BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "card_trade"),
                    new ExtendedScreenHandlerType<>(
                            (syncId, inv, data) -> new CardTradeMenu(syncId, inv, data),
                            TradeData.STREAM_CODEC
                    ));

    /** 乞讨菜单类型 */
    public static final ExtendedScreenHandlerType<BeggarMenu, BeggarMenu.BeggarData> BEGGAR =
            Registry.register(BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "beggar"),
                    new ExtendedScreenHandlerType<>(
                            (syncId, inv, data) -> new BeggarMenu(syncId, inv, data),
                            BeggarMenu.BeggarData.STREAM_CODEC
                    ));

    /** 恶魔契约菜单数据：本次契约恶魔随机提供的力量 id 列表 */
    public record ContractData(List<String> powerIds) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ContractData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, data) -> ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                                .encode(buf, data.powerIds),
                        buf -> new ContractData(
                                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf))
                );
    }

    /** 恶魔契约菜单类型 */
    public static final ExtendedScreenHandlerType<DemonContractMenu, ContractData> DEMON_CONTRACT =
            Registry.register(BuiltInRegistries.MENU,
                    ResourceLocation.fromNamespaceAndPath(Dim201Addon.MOD_ID, "demon_contract"),
                    new ExtendedScreenHandlerType<>(
                            DemonContractMenu::new,
                            ContractData.STREAM_CODEC
                    ));

    public static void init() {
        Dim201Addon.LOGGER.info("Dim201Addon 菜单类型注册完成");
    }
}
