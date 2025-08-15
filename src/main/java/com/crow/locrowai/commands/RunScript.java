package com.crow.locrowai.commands;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.api.Script;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class RunScript {
    public RunScript(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(LocrowAI.MODID)
                .then(literal("run")
                        .then(argument("script", StringArgumentType.string())
                                .executes(context -> {
                                    String script = StringArgumentType.getString(context, "script");
                                    new Script(script).execute(jsonObject -> {
                                        LocrowAI.LOGGER().info(jsonObject.toString());
                                        context.getSource().sendSystemMessage(Component.literal(jsonObject.toString()));
                                    });

                                    return 1;
                                })
                        )
                )
        );
    }
}
