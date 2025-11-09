package com.crow.locrowai.internal.commands;

import com.crow.locrowai.internal.LocrowAI;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
class Dispatcher {

    @SubscribeEvent
    static void registerCommands(RegisterCommandsEvent event) {
        new RunScriptCommand(event.getDispatcher());
    }
}
