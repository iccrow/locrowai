package com.crow.locrowai.internal.backend;

import com.crow.locrowai.internal.AIPackageManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
class ProgressBarManager {

    private static final AtomicBoolean pass = new AtomicBoolean();

    // Add a button to start/cancel the install
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!InstallationManager.installing.get() && !InstallationManager.hadError.get()) return;
        Screen screen = event.getScreen();
        if (!(screen instanceof JoinMultiplayerScreen) && !(screen instanceof SelectWorldScreen)) return;
        if (pass.get()) {
            pass.set(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String line1 = "ERROR", line3 = "ERROR";
        int titleColor = 0xFFFFFF;

        if (InstallationManager.installing.get()) {
            line1 = "AI packages are still installing!";
            line3 = "Proceeding will install the packages in the background.";
            titleColor = 0xFFFF55;
        } else if (InstallationManager.hadError.get()) {
            line1 = "AI packages failed to install!";
            line3 = "Visit the Locrow AI Mod Config Page to try to resolve the issue.";
            titleColor = 0xFF5555;
        }

        Component message = Component.empty()
                .append(Component.literal("AI features will be disabled and crashes may occur.\n\n")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))) // white
                .append(Component.literal(line3)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)))); // gray

        mc.setScreen(new ConfirmScreen(proceed -> {
            if (proceed) {
                pass.set(true);
                mc.setScreen(screen);
            } else {
                mc.setScreen(new TitleScreen());
            }
        }, Component.literal(line1).withStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(titleColor))), message,
                Component.literal("Proceed Anyway"), Component.literal("Go Back")));
    }

    // Render segmented bar and labels
    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen) && !(screen instanceof AIPackageManagerScreen)) return;


        if (!InstallationManager.installing.get() && !InstallationManager.hadError.get()) return;
        int color = InstallationManager.hadError.get() ? 0xAAAA3333 : 0xAA33AA33;
        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int screenW = screen.width;
        int barWidth = Math.min(400, screenW - 40);
        int barHeight = 6;
        int x = (screenW - barWidth) / 2;
        int y = 2 + barHeight; // top of the screen area
        int textY = y + 11;

        // stage label (above)
        String stageLabel = InstallationManager.hadError.get() ? "An error occurred" : InstallationManager.getCurrentStageName();
        gui.drawString(font, stageLabel, (screenW - font.width(stageLabel)) / 2, textY, 0xFFFFFFFF, false);

        // background
        gui.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, 0xAA000000);

        // draw segment separators and segment fills
        double total = InstallationManager.STAGES.stream().mapToDouble(s -> s.weight()).sum();
        // precompute segment widths
        double pos = 0;
        int segments = InstallationManager.STAGES.size();
        int currentIndex = InstallationManager.currentStageIndex.get();

        // draw empty (base) bar
        gui.fill(x, y, x + barWidth, y + barHeight, 0xFF333333);

        // draw completed segments + current partial
        int px = x;
        for (int i = 0; i < segments; i++) {
            double wFrac = total == 0 ? (1.0 / segments) : (InstallationManager.STAGES.get(i).weight() / total);
            int segWidth = (int) Math.round(barWidth * wFrac);
            if (segWidth <= 0) continue;

            if (i < currentIndex) {
                // fully completed
                gui.fill(px, y, px + segWidth, y + barHeight, color); // green if installing, red if error
            } else if (i == currentIndex) {
                int filled = (int) (segWidth * (InstallationManager.stagePercent.get() / 100.0f));
                if (filled > 0) gui.fill(px, y, px + filled, y + barHeight, color);
            }
            // draw vertical separator line
            gui.fill(px + segWidth - 1, y - 1, px + segWidth, y + barHeight + 1, 0xFF555555);
            px += segWidth;
        }

        // percentage text on top-right of bar
        String pct = InstallationManager.getOverallPercent() + "%";
        gui.drawString(font, pct, x + barWidth - font.width(pct), textY, 0xFFFFFFFF, false);
    }
}
