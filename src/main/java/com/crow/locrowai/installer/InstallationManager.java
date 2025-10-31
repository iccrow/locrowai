package com.crow.locrowai.installer;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class InstallationManager {

    public record Stage(String name, double weight) {} // weight = relative portion of the bar

    // define your stages and their relative weights
    public static final List<Stage> STAGES = List.of(
            new Stage("Downloading AI Packages", 2),
            new Stage("Setting Up Environment", 1),
            new Stage("Downloading Dependencies", 10),
            new Stage("Installing AI Packages", 21),
            new Stage("Verifying Install", 1),
            new Stage("Done", 0) // zero weight for final "done" state (you can still display it)
    );

    public static final AtomicInteger currentStageIndex = new AtomicInteger(0); // index into STAGES
    public static final AtomicDouble stagePercent = new AtomicDouble(0); // 0..100 within current stage
    public static final AtomicBoolean installing = new AtomicBoolean(false);
    public static final AtomicBoolean hadError = new AtomicBoolean(false);
    public static final AtomicReference<String> subLabel = new AtomicReference<>("");

    // computed from STAGES weights
    private static double totalWeight() {
        return STAGES.stream().mapToDouble(Stage::weight).sum();
    }

    // returns 0..100 overall percent
    public static int getOverallPercent() {
        int idx = currentStageIndex.get();
        double total = totalWeight();
        if (total == 0) return 100;
        double completed = 0;
        for (int i = 0; i < idx; i++) completed += STAGES.get(i).weight;
        double currentWeight = STAGES.get(idx).weight;
        double curPct = (Math.max(0, stagePercent.get()) / 100.0) * currentWeight;
        double overall = (completed + curPct) / total * 100.0;
        if (overall > 100) overall = 100;
        return (int) Math.round(overall);
    }

    public static String getCurrentStageName() {
        int idx = currentStageIndex.get();
        if (idx >= 0 && idx < STAGES.size()) return STAGES.get(idx).name();
        return "";
    }

    public static void cancelInstall() {
        installing.set(false);
        subLabel.set("Canceled");
    }
}
