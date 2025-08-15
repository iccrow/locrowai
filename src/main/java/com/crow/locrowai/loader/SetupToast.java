package com.crow.locrowai.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class SetupToast {
    private static boolean warn_shown;
    private static boolean done_shown;

    public static void warn() {
        if (warn_shown) return;
        warn_shown = true;

        Minecraft.getInstance().execute(() -> {
            var toasts = Minecraft.getInstance().getToasts();
            SystemToast.add(
                    toasts,
                    SystemToast.SystemToastIds.PACK_LOAD_FAILURE,
                    Component.literal("Setting up AI packages…"),
                    Component.literal("Some features will be unavailable for a moment.")
            );
        });
    }

    public static void done() {
        if (done_shown) return;
        done_shown = true;

        Minecraft.getInstance().execute(() -> {
            var toasts = Minecraft.getInstance().getToasts();
            SystemToast.add(
                    toasts,
                    SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("Finished setting up AI packages"),
                    Component.literal("Local AI features are now available.")
            );
        });
    }
}
