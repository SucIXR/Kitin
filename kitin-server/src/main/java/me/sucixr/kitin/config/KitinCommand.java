package me.sucixr.kitin.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class KitinCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kitin")
                // 权限检查：需要 OP 4级权限
                .requires(source -> source.getBukkitSender().hasPermission("kitin.command.reload"))

                .then(Commands.literal("reload")
                        .then(Commands.argument("module", StringArgumentType.word())
                                // Tab 补全建议
                                .suggests((context, builder) -> {
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(KitinConfig.getReloadableModules(), builder);
                                })
                                // 执行逻辑
                                .executes(context -> {
                                    String module = StringArgumentType.getString(context, "module");
                                    try {
                                        long start = System.nanoTime();
                                        // 调用 Config 的重载
                                        String result = KitinConfig.reload(module);
                                        long end = System.nanoTime();

                                        double ms = (end - start) / 1_000_000.0;
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                String.format("§a[Kitin] Reloaded §f%s §ain %.2fms", result, ms)
                                        ), true);

                                    } catch (IllegalArgumentException e) {
                                        context.getSource().sendFailure(Component.literal("§cUnknown module: " + module));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        context.getSource().sendFailure(Component.literal("§cError reloading config. Check console."));
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }
}