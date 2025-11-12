package com.crow.locrowai.internal;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@ApiStatus.Internal
@Mod.EventBusSubscriber(modid = LocrowAI.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<Boolean> OFFLOADING = BUILDER
            .comment("Do you want to run the AI process on a client rather than the server? This is required for use on unsupported servers or helpful if the server is struggling with the process.")
            .define("offloading", false);

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOLUNTEERS = BUILDER
            .comment("A list of player names to search for offloading the AI process to, in order of priority.")
            .defineListAllowEmpty("volunteerNames", List.of(), Config::validateNames);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean offloading;
    public static List<String> volunteerNames;

    private static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static boolean validateNames(final Object obj) {
        return obj instanceof final String name && MC_NAME.matcher(name).matches();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        offloading = OFFLOADING.get();
        volunteerNames = new ArrayList<>(VOLUNTEERS.get());
    }

    public static void setOffloading(boolean option) {
        OFFLOADING.set(option);
        OFFLOADING.save();
        offloading = option;
    }

    public static void setVolunteers(List<String> names) {
        VOLUNTEERS.set(names);
        volunteerNames = names;

    }
}
