package com.crow.locrowai.api.registration;

import com.crow.locrowai.api.AIContext;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.util.*;

public class AIRegistry {
    private static final Type PLUGIN_ANNOT = Type.getType(LocrowAIPlugin.class);

    private static final Map<String, AIContext> contexts = new HashMap<>();
    private static final Map<String, AIExtension> registry = new HashMap<>();
    private static final Set<String> declared = new HashSet<>();
    private static final Map<String, ClassLoader> loaders = new HashMap<>();

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
                loaders.put(MODID, loader);

                AIPlugin registrant = (AIPlugin) clazz.getDeclaredConstructor().newInstance();

                registrant.register(context);

                AIContext.RegistrationResults results = context.finishRegistration();

                for (AIExtension extension : results.registered()) {
                    registry.put(extension.getId(), extension);
                }

                declared.addAll(results.declared());

            } catch (Exception e) {

                e.printStackTrace();
            }
        }

        registry.keySet().retainAll(declared);
        declared.retainAll(registry.keySet());
    }

    public static AIContext getContext(String MODID) {
        return contexts.get(MODID);
    }

    public static Set<String> getDeclared() {
        return Set.copyOf(declared);
    }

    public static AIExtension getExtension(String MODID) {
        return registry.get(MODID);
    }
    public static List<AIExtension> getExtensions() {
        return registry.values().stream().toList();
    }

    public static ClassLoader getLoader(String MODID) {
        return loaders.get(MODID);
    }
}
