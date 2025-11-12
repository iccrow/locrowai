package com.crow.locrowai.internal;

import com.crow.locrowai.internal.backend.InstallationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.ApiStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * AIPackageManagerScreen with:
 * - selectable log lines (line-based)
 * - copy log button
 * - draggable scrollbar
 */

@ApiStatus.Internal
public class AIBackendManagerScreen extends Screen {
    private static final int TOP_BAR_HEIGHT = 36;
    private final Screen parent;

    private final AtomicBoolean showLog = new AtomicBoolean(false);
    private final ScrollableTextArea logArea = new ScrollableTextArea();

    private final List<String> volunteerList = new ArrayList<>();
    private EditBox volunteerInput;

    private Button installButton;
    private Button deleteButton;
    private Button viewLogButton;
    private Button copyLogButton;
    private Button offloadingToggleButton;
    private Button backButton;

    private final AtomicBoolean actionRunning = new AtomicBoolean(false);

    // Paths
    private final Path modGameDir = FMLPaths.GAMEDIR.get();
    private final Path aiEnvDir = InstallationManager.getBackendPath();
    private Path setupLog = InstallationManager.getLatestLogPath();

    // Async size computation
    private final AtomicLong totalSizeBytes = new AtomicLong(-1);
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-size-compute");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean sizeComputing = new AtomicBoolean(false);

    public AIBackendManagerScreen(Screen parent) {
        super(Component.literal("AI Package Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int midX = this.width / 2;
        int y = TOP_BAR_HEIGHT + 10;

        // Back
        backButton = this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(8, 8, 80, 20).build());

        y += 26;

        // Install
        installButton = this.addRenderableWidget(Button.builder(Component.literal("Install AI Packages"), btn -> {
            if (actionRunning.get() || InstallationManager.installing.get()) return;
            actionRunning.set(true);
            new Thread(() -> {
                try {
//                    EnvironmentInstaller.install(base, probeResult);
                } finally {
                    actionRunning.set(false);
                    scheduleComputeTotalSize();
                }
            }, "ai-install-thread").start();
        }).bounds(midX - 100, y, 200, 20).build());

        // Delete
        deleteButton = this.addRenderableWidget(Button.builder(Component.literal("Delete AI Packages"), btn -> {
            if (actionRunning.get() || InstallationManager.installing.get()) return;
            confirmAndRun("Delete AI packages? This will remove installed files.", () -> {
                actionRunning.set(true);
                new Thread(() -> {
                    try {
                        try {
                            FileUtils.deleteDirectory(aiEnvDir.toFile());
                        } catch (IOException e) {
                            // TODO: log
                        }
                    } finally {
                        actionRunning.set(false);
                        scheduleComputeTotalSize();
                    }
                }, "ai-delete-thread").start();
            });
        }).bounds(midX - 100, y, 200, 20).build());

        y += 26;

        // View Log
        viewLogButton = this.addRenderableWidget(Button.builder(Component.literal("View Setup Log"), btn -> {
            boolean now = !showLog.get();
            showLog.set(now);
            if (now) {
                List<String> raw = readLogLinesRaw();
                logArea.setWrappedText(raw, this.font, logArea.getWidthOrDefault(this.width));
            }
        }).bounds(midX - 100, y, 95, 20).build());

        // Copy Log (next to view)
        copyLogButton = this.addRenderableWidget(Button.builder(Component.literal("Copy Log"), btn -> {
            String copy = logArea.getSelectedTextOrAll();
            if (copy == null || copy.isEmpty()) return;
            // copy to system clipboard via Minecraft's keyboard handler if available
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(copy);
            } catch (Throwable t) {
                // fallback: attempt to write to file in game dir
                try {
                    Files.writeString(modGameDir.resolve("copied_log.txt"), copy, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException ignored) {}
            }
        }).bounds(midX + 5, y, 95, 20).build());

        y += 28;

        // Offloading
        offloadingToggleButton = this.addRenderableWidget(Button.builder(Component.literal(offloadingText()), btn -> {
            Config.setOffloading(!Config.offloading);
            offloadingToggleButton.setMessage(Component.literal(offloadingText()));
        }).bounds(midX - 100, y, 200, 20).build());

        y += 34;

        // Volunteers UI
        volunteerInput = new EditBox(this.minecraft.font, midX - 100, y, 140, 20, Component.literal("Enter player name"));
        volunteerInput.setMaxLength(64);
        this.addRenderableWidget(volunteerInput);

        this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
            String name = volunteerInput.getValue().trim();
            if (!name.isEmpty()) {
                volunteerList.add(name);
                volunteerInput.setValue("");
                Config.setVolunteers(volunteerList);
            }
        }).bounds(midX + 45, y, 50, 20).build());

        y += 26;

        // Log area init
        int logX = midX - 200;
        int logY = y;
        int logW = 400;
        int logH = this.height - y - 40;
        logArea.init(logX, logY, logW, logH);

        // Load volunteers
        volunteerList.clear();
        if (Config.volunteerNames != null) {
            volunteerList.addAll(Config.volunteerNames);
        }

        // Start size compute
        scheduleComputeTotalSize();

        updateButtonsState();
    }

    @Override
    public void removed() {
        super.removed();
        background.shutdownNow();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonsState();
    }

    private void updateButtonsState() {
        boolean installing = InstallationManager.installing.get();
        boolean installed = false;
        installed = InstallationManager.isFullyInstalled();

        boolean actionDisabled = installing || actionRunning.get();

        installButton.visible = !installed;
        installButton.active = !actionDisabled;

        deleteButton.visible = installed;
        deleteButton.active = !actionDisabled;

        viewLogButton.active = true;
        copyLogButton.active = showLog.get();
        offloadingToggleButton.active = !installing;
        backButton.active = true;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui);
        gui.drawCenteredString(this.font, this.title.getString(), this.width / 2, TOP_BAR_HEIGHT + 4, 0xFFFFFF);

        long size = totalSizeBytes.get();
        String sizeText = (size < 0) ? "Calculating..." : humanReadable(size);
        gui.drawString(this.font, Component.literal("Total packages size: " + sizeText),
                12, TOP_BAR_HEIGHT + 36, 0xFFFFFF);

        super.render(gui, mouseX, mouseY, partialTicks);

        renderVolunteers(gui, mouseX, mouseY);

        if (showLog.get()) {
            logArea.render(gui, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showLog.get() && logArea.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // Forward mouse press / drag / release for logArea interaction
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showLog.get() && logArea.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (showLog.get() && logArea.mouseDragged(mouseX, mouseY, button, dx, dy)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showLog.get() && logArea.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(parent);
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private void scheduleComputeTotalSize() {
        if (!sizeComputing.compareAndSet(false, true)) return;
        totalSizeBytes.set(-1);
        try {
            background.submit(() -> {
                try {
                    long total = computeTotalSizeBlocking();
                    Minecraft.getInstance().execute(() -> totalSizeBytes.set(total));
                } finally {
                    sizeComputing.set(false);
                }
            });
        } catch (Exception ignored) {}
    }

    private long computeTotalSizeBlocking() {
        if (!Files.exists(aiEnvDir)) return 0L;
        try (Stream<Path> stream = Files.walk(aiEnvDir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private String offloadingText() {
        return "Offloading: " + (Config.offloading ? "ON" : "OFF");
    }

    private void confirmAndRun(String message, Runnable action) {
        this.minecraft.setScreen(new SimpleConfirmScreen(this, message, () -> {
            this.minecraft.setScreen(this);
            action.run();
            scheduleComputeTotalSize();
        }));
    }

    private List<String> readLogLinesRaw() {
        setupLog = InstallationManager.getLatestLogPath();
        if (setupLog == null || !Files.exists(setupLog)) return List.of("No log found.");
        try {
            return Files.readAllLines(setupLog, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of("Failed to read log: " + e.getMessage());
        }
    }

    private static String humanReadable(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }

    private void renderVolunteers(GuiGraphics gui, int mouseX, int mouseY) {
        int x = 12;
        int y = TOP_BAR_HEIGHT + 60;
        gui.drawString(this.font, Component.literal("Player volunteers:"), x, y, 0xFFFFFF);
        y += 12;

        int i = 0;
        List<String> copy = new ArrayList<>(volunteerList);
        for (String v : copy) {
            int textY = y + (i * 12);
            gui.drawString(this.font, Component.literal(v), x + 4, textY, 0xFFFFAA);
            i++;
        }
    }

    // ---------------------------
    // Simple confirm popup screen
    // ---------------------------
    private static class SimpleConfirmScreen extends Screen {
        private final Screen parent;
        private final String message;
        private final Runnable onProceed;

        protected SimpleConfirmScreen(Screen parent, String message, Runnable onProceed) {
            super(Component.literal("Confirm"));
            this.parent = parent;
            this.message = message;
            this.onProceed = onProceed;
        }

        @Override
        protected void init() {
            super.init();
            int cx = width / 2;
            int cy = height / 2;
            this.addRenderableWidget(Button.builder(Component.literal("Proceed"), b -> onProceed.run()).bounds(cx - 100, cy + 6, 98, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.minecraft.setScreen(parent)).bounds(cx + 2, cy + 6, 98, 20).build());
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(gui);
            gui.fill(0, 0, width, height, 0xAA000000);
            gui.drawCenteredString(font, message, width / 2, height / 2 - 12, 0xFFFFFF);
            super.render(gui, mouseX, mouseY, partialTicks);
        }
    }

    // ---------------------------
    // Scrollable/wrapping/selectable log area with draggable scrollbar
    // ---------------------------
    private static class ScrollableTextArea {
        private List<String> wrappedLines = new ArrayList<>();
        private int x, y, w, h;
        private int scroll = 0;
        private final int lineHeight = 9;
        private final int scrollbarWidth = 10;

        // selection (line-based)
        private int selStart = -1;
        private int selEnd = -1;

        // dragging state for scrollbar
        private boolean draggingThumb = false;
        private int dragOffsetY = 0;
        private int initialScroll = 0;

        void init(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        int getWidthOrDefault(int screenWidth) {
            return (w > 0) ? w : Math.min(400, screenWidth - 40);
        }

        void setWrappedText(List<String> rawLines, Font font, int maxWidth) {
            wrappedLines.clear();
            if (rawLines == null || rawLines.isEmpty()) return;
            for (String raw : rawLines) wrapLineInto(raw, font, maxWidth - 8);
            int maxLinesShown = Math.max(1, h / lineHeight);
            scroll = Math.max(0, wrappedLines.size() - maxLinesShown);
            clearSelection();
        }

        private void wrapLineInto(String raw, Font font, int maxWidth) {
            if (raw == null) return;
            if (font.width(raw) <= maxWidth) {
                wrappedLines.add(raw);
                return;
            }

            // Helper to split a single long word into pieces that fit maxWidth
            java.util.function.Function<String, List<String>> splitLongWord = (word) -> {
                List<String> parts = new ArrayList<>();
                int len = word.length();
                int idx = 0;
                while (idx < len) {
                    int end = idx;
                    StringBuilder piece = new StringBuilder();
                    // Grow piece char-by-char while it fits
                    while (end < len && font.width(piece.toString() + word.charAt(end)) <= maxWidth) {
                        piece.append(word.charAt(end));
                        end++;
                    }
                    // If no char could be added (rare: single char wider than maxWidth), force one char
                    if (piece.isEmpty() && end < len) {
                        piece.append(word.charAt(end));
                        end++;
                    }
                    parts.add(piece.toString());
                    idx = end;
                }
                return parts;
            };

            // Split input into words (whitespace collapsed). We keep it simple:
            String[] words = raw.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (String w : words) {
                if (w.isEmpty()) continue;

                if (line.isEmpty()) {
                    // current line empty: try to put the whole word
                    if (font.width(w) <= maxWidth) {
                        line.append(w);
                    } else {
                        // word too long: break into pieces and append each as its own wrapped line
                        List<String> parts = splitLongWord.apply(w);
                        wrappedLines.addAll(parts);
                        // leave line empty for next words
                    }
                } else {
                    // current line has content: try adding " " + word
                    String candidate = line.toString() + " " + w;
                    if (font.width(candidate) <= maxWidth) {
                        line.append(" ").append(w);
                    } else {
                        // flush current line
                        wrappedLines.add(line.toString());
                        line.setLength(0);

                        // now put the word on new line (or split if too long)
                        if (font.width(w) <= maxWidth) {
                            line.append(w);
                        } else {
                            List<String> parts = splitLongWord.apply(w);
                            wrappedLines.addAll(parts);
                            // keep line empty after adding pieces
                        }
                    }
                }
            }

            if (!line.isEmpty()) {
                wrappedLines.add(line.toString());
            }
        }


        boolean mouseScrolled(double mx, double my, double delta) {
            if (mx < x || mx > x + w || my < y || my > y + h) return false;
            scroll -= (int) delta * 3;
            clampScroll();
            return true;
        }

        boolean mouseClicked(double mx, double my, int button) {
            // check if click is inside the log area
            if (mx < x || mx > x + w || my < y || my > y + h) return false;

            // is click on scrollbar?
            int sbX = x + w - scrollbarWidth;
            if (mx >= sbX && mx <= sbX + scrollbarWidth) {

                clearSelection();
                // calculate thumb area
                int totalLines = Math.max(1, wrappedLines.size());
                int visibleLines = Math.max(1, h / lineHeight);
                double proportion = (double) visibleLines / (double) totalLines;
                int thumbHeight = (int) Math.max(10, h * proportion);
                int maxLines = Math.max(1, wrappedLines.size());
                double scrollRatio = (maxLines <= visibleLines) ? 0.0 : (double) scroll / (maxLines - visibleLines);
                int thumbY = y + (int) ((h - thumbHeight) * scrollRatio);

                if (my >= thumbY && my <= thumbY + thumbHeight) {
                    // start dragging
                    draggingThumb = true;
                    dragOffsetY = (int) my - thumbY;
                    initialScroll = scroll;
                    return true;
                } else {
                    // click above/below thumb -> page up/down
                    if (my < thumbY) {
                        scroll -= h / lineHeight;
                    } else {
                        scroll += h / lineHeight;
                    }
                    clampScroll();
                    return true;
                }
            }

            // click on text area: determine clicked line
            int relY = (int) my - y;
            int clickedLine = relY / lineHeight + scroll;
            if (clickedLine < 0) clickedLine = 0;
            if (clickedLine >= wrappedLines.size()) clickedLine = wrappedLines.size() - 1;
            // set or start selection
            selStart = clickedLine;
            selEnd = clickedLine;
            return true;
        }

        boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (draggingThumb) {
                // move thumb -> update scroll
                int thumbAreaHeight = h;
                int totalLines = Math.max(1, wrappedLines.size());
                int visibleLines = Math.max(1, h / lineHeight);
                int thumbHeight = (int) Math.max(10, h * ((double) visibleLines / totalLines));
                int maxScroll = Math.max(0, wrappedLines.size() - visibleLines);

                int thumbTrack = thumbAreaHeight - thumbHeight;
                int newThumbY = (int) my - dragOffsetY - y;
                double ratio = thumbTrack <= 0 ? 0.0 : (double) newThumbY / (double) thumbTrack;
                if (ratio < 0) ratio = 0;
                if (ratio > 1) ratio = 1;
                int newScroll = (int) Math.round(ratio * maxScroll);
                scroll = newScroll;
                clampScroll();
                return true;
            } else {
                // selection by dragging across lines
                if (mx < x || mx > x + w || my < y || my > y + h) return false;
                int relY = (int) my - y;
                int line = relY / lineHeight + scroll;
                if (line < 0) line = 0;
                if (line >= wrappedLines.size()) line = wrappedLines.size() - 1;
                selEnd = line;
                return true;
            }
        }

        boolean mouseReleased(double mx, double my, int button) {
            if (draggingThumb) {
                draggingThumb = false;
                return true;
            }
            return false;
        }

        void clampScroll() {
            int maxLinesShown = Math.max(1, h / lineHeight);
            int maxScroll = Math.max(0, wrappedLines.size() - maxLinesShown);
            if (scroll < 0) scroll = 0;
            if (scroll > maxScroll) scroll = maxScroll;
            // also clamp selection
            if (selStart >= wrappedLines.size()) selStart = -1;
            if (selEnd >= wrappedLines.size()) selEnd = wrappedLines.size() - 1;
            if (selStart < 0 || selEnd < 0) { selStart = selEnd = -1; }
        }

        void clearSelection() { selStart = selEnd = -1; }

        String getSelectedTextOrAll() {
            if (wrappedLines.isEmpty()) return "";
            if (selStart == -1 || selEnd == -1) {
                // return entire log
                StringBuilder sb = new StringBuilder();
                for (String s : wrappedLines) { sb.append(s).append(System.lineSeparator()); }
                return sb.toString();
            }
            int a = Math.min(selStart, selEnd);
            int b = Math.max(selStart, selEnd);
            StringBuilder sb = new StringBuilder();
            for (int i = a; i <= b && i < wrappedLines.size(); i++) {
                sb.append(wrappedLines.get(i)).append(System.lineSeparator());
            }
            return sb.toString();
        }

        void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            gui.fill(x, y, x + w, y + h, 0xDD000000);
            gui.fill(x - 1, y - 1, x, y + h + 1, 0xFF666666);
            gui.fill(x + w, y - 1, x + w + 1, y + h + 1, 0xFF666666);

            Font font = Minecraft.getInstance().font;
            int maxLines = Math.max(1, h / lineHeight);
            for (int i = 0; i < maxLines; i++) {
                int idx = i + scroll;
                if (idx >= wrappedLines.size()) break;
                String line = wrappedLines.get(idx);
                int drawY = y + i * lineHeight;
                // highlight selected lines
                if (selStart != -1 && selEnd != -1) {
                    int a = Math.min(selStart, selEnd);
                    int b = Math.max(selStart, selEnd);
                    if (idx >= a && idx <= b) {
                        gui.fill(x + 2, drawY - 1, x + w - scrollbarWidth - 2, drawY + lineHeight - 1, 0x336699FF);
                    }
                }
                gui.drawString(font, line, x + 4, drawY, 0xFFFFFF, false);
            }

            // scrollbar background
            int sbX = x + w - scrollbarWidth;
            gui.fill(sbX, y, sbX + scrollbarWidth, y + h, 0x66000000);

            // thumb
            int totalLines = Math.max(1, wrappedLines.size());
            int visibleLines = Math.max(1, h / lineHeight);
            double proportion = (double) visibleLines / (double) totalLines;
            int thumbHeight = (int) Math.max(10, h * proportion);
            maxLines = Math.max(1, wrappedLines.size());
            double scrollRatio = (maxLines <= visibleLines) ? 0.0 : (double) scroll / (maxLines - visibleLines);
            int thumbY = y + (int) ((h - thumbHeight) * scrollRatio);
            gui.fill(sbX + 1, thumbY, sbX + scrollbarWidth - 1, thumbY + thumbHeight, draggingThumb ? 0xFFCCCCCC : 0xFFAAAAAA);
        }
    }
}
