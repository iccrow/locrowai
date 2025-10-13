package com.crow.locrowai.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class SetupToast {
    private static boolean done_shown;

    public static void warmedUp() {

        Minecraft.getInstance().execute(() -> {
            var toasts = Minecraft.getInstance().getToasts();
            SystemToast.add(
                    toasts,
                    SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("AI packages are warmed up"),
                    Component.literal("Local AI features are now available.")
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
                    Component.literal("Finished installing AI packages"),
                    Component.literal("Local AI features will be available after warmup.")
            );
        });
    }
}
