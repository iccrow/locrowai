package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.AIContext;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIRegistry {
    private static final Type PLUGIN_ANNOT = Type.getType(LocrowAIPlugin.class);

    private static final Map<String, AIContext> contexts = new HashMap<>();
    private static final List<String> registry = new ArrayList<>();

    public static void init() {
        ModList modList = ModList.get();
        for (IModInfo mod : modList.getMods()) {
            String MODID = mod.getModId();
            ModContainer container = modList.getModContainerById(MODID).orElseThrow();
            ModFileScanData scanData = mod.getOwningFile().getFile().getScanResult();
            ClassLoader loader = container.getMod().getClass().getClassLoader();

            register(MODID, scanData, loader);
        }
    }

    public static void register(String MODID, ModFileScanData scanData, ClassLoader loader) {
        List<ModFileScanData.AnnotationData> annotated = scanData.getAnnotations().stream()
                .filter(a -> PLUGIN_ANNOT.equals(a.annotationType()))
                .toList();

        for (ModFileScanData.AnnotationData data : annotated) {
            try {
                Class<?> clazz = Class.forName(data.clazz().getClassName(), true, loader);
                if (!AIPlugin.class.isAssignableFrom(clazz)) continue;

                AIContext context = new AIContext(MODID, loader);

                contexts.put(MODID, context);

                AIPlugin registrant = (AIPlugin) clazz.getDeclaredConstructor().newInstance();

                registrant.register(context);

                List<AIExtension> pending = context.finishRegistration();

                for (AIExtension extension : pending) {

                }

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    public static AIContext getContext(String MODID) {
        return contexts.get(MODID);
    }
}
