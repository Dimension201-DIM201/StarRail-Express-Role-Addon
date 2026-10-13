package com.dim201.dim201addon.command;

import com.dim201.dim201addon.LoneWolfComponent;
import com.dim201.dim201addon.LoneWolfRole;
import com.dim201.dim201addon.ModComponents;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 独狼【清算时间】强制触发指令。
 *
 * <p>用法：{@code /dim201:lonewolfsettlement <player>}
 * <p>权限：OP（permission level 2）
 * <p>效果：跳过人数条件检查，立即让目标独狼进入清算时间。
 */
public class LoneWolfSettlementCommand {

    private LoneWolfSettlementCommand() {}

    /**
     * 注册指令到 dispatcher。
     *
     * @param dispatcher 命令分发器
     */
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dim201:lonewolfsettlement")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LoneWolfSettlementCommand::execute))
        );
    }

    /**
     * 指令执行逻辑：直接触发独狼清算时间。
     */
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(target.level());
        if (gameWorld == null || !gameWorld.isRunning()) {
            source.sendFailure(Component.literal("§c触发失败：对局未运行"));
            return 0;
        }
        if (!gameWorld.isRole(target, LoneWolfRole.LONE_WOLF)) {
            source.sendFailure(Component.literal("§c触发失败：目标玩家不是独狼"));
            return 0;
        }

        LoneWolfComponent comp = ModComponents.LONE_WOLF.getNullable(target);
        if (comp == null) {
            source.sendFailure(Component.literal("§c触发失败：目标组件异常"));
            return 0;
        }
        if (comp.settlementActive) {
            source.sendFailure(Component.literal("§c该独狼已处于清算时间"));
            return 0;
        }

        comp.activateSettlement(target);
        source.sendSuccess(() -> Component.literal("§4已触发独狼清算时间：" + target.getName().getString()), true);
        return 1;
    }
}
