package com.dim201.dim201addon.command;

import com.dim201.dim201addon.Dim201Addon;
import com.dim201.dim201addon.modifier.DevourerGodManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 神明吞噬者时刻触发指令。
 *
 * <p>用法：{@code /dim201:devourergod <player>}
 * <p>权限：OP（permission level 2）
 * <p>效果：跳过修饰符分配与死亡复活流程，立即让目标玩家变为神明吞噬者。
 */
public class DevourerGodCommand {

    private DevourerGodCommand() {}

    /**
     * 注册指令到 dispatcher。
     *
     * @param dispatcher 命令分发器
     */
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dim201:devourergod")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DevourerGodCommand::execute))
        );
    }

    /**
     * 指令执行逻辑：直接触发神明吞噬者时刻。
     */
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();

        // 调用 DevourerGodManager 直接触发（跳过修饰符分配与死亡复活流程）
        boolean success = DevourerGodManager.triggerDevourerGodMoment(target);
        if (success) {
            source.sendSuccess(() -> Component.literal("§5已触发神明吞噬者时刻：" + target.getName().getString()), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("§c触发失败：游戏未运行或玩家不在线"));
            return 0;
        }
    }
}
