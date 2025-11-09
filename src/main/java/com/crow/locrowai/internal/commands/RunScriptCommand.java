package com.crow.locrowai.internal.commands;

import com.crow.locrowai.internal.LocrowAI;
import com.crow.locrowai.api.registration.AIRegistry;
import com.crow.locrowai.api.runtime.Script;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

class RunScriptCommand {
    RunScriptCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(LocrowAI.MODID)
                .then(literal("run")
                        .then(argument("script", StringArgumentType.string())
                                .executes(context -> {
                                    String script = StringArgumentType.getString(context, "script");
                                    AIRegistry.getContext(LocrowAI.MODID).execute(new Script(null, script))
                                            .thenAccept(jsonObject -> {
                                                LocrowAI.LOGGER().info(jsonObject.toString());
                                                context.getSource().sendSystemMessage(Component.literal(jsonObject.toString()));
                                            });
                                    return 0;
                                })
                        )
                )
        );
    }
}
