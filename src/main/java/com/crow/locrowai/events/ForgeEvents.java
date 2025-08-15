package com.crow.locrowai.events;

import com.crow.locrowai.LocrowAI;
import com.crow.locrowai.commands.RunScript;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        new RunScript(event.getDispatcher());
    }
}
