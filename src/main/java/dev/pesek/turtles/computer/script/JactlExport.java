package dev.pesek.turtles.computer.script;

import com.google.common.base.Preconditions;
import dev.pesek.turtles.computer.Computer;
import io.jactl.JactlContext;
import io.jactl.runtime.JactlClass;
import io.jactl.runtime.JactlFunction;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JactlExport {

    private static final Set<Class<?>> EXPORTED_TYPES = new HashSet<>();
    private static final AtomicBoolean HAS_EXPORTED_TYPES = new AtomicBoolean(false);

    public static void exportGlobals(Computer computer, Map<String, Object> globals) {
        Preconditions.checkNotNull(computer, "computer");
        Preconditions.checkNotNull(globals, "globals");
        ScriptExtension.Registry.get().getExtensions().forEach(scriptExtension ->
                globals.putAll(scriptExtension.globals(computer)));
    }

    public static void exportTypes() {
        if (!HAS_EXPORTED_TYPES.compareAndSet(false, true)) {
            throw new UnsupportedOperationException("Types have been already exported");
        }
        ScriptExtension.Registry.get().getExtensions().forEach(scriptExtension ->
                ScriptExtension.exportTypes(scriptExtension).forEach(JactlExport::exportType));
    }

    public static void exportFunctions(Computer computer, JactlContext context) {
        Preconditions.checkNotNull(computer, "computer");
        Preconditions.checkNotNull(context, "context");
        computer.getRegisteredExtensions().forEach(scriptExtension ->
                ScriptExtension.exportTypes(scriptExtension).forEach(type -> exportFunctions(context, type)));
    }

    private static void exportType(Class<?> type) {
        if (!type.isAnnotationPresent(Export.Type.class)) {
            return;
        }

        synchronized (EXPORTED_TYPES) {
            if (!EXPORTED_TYPES.add(type)) {
                return;
            }
        }

        Export.Type typeMeta = type.getAnnotation(Export.Type.class);
        JactlClass jactlClass = new JactlClass(typeMeta.name())
                .javaClass(type)
                .autoImport(typeMeta.autoImport());

        for (Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(Export.Method.class)) {
                continue;
            }
            addMethod(jactlClass, method, method.getAnnotation(Export.Method.class));
        }

        jactlClass.register();
    }

    private static void addMethod(JactlClass jactlClass, Method method, Export.Method meta) {
        var paramTypes = method.getParameterTypes();
        Object[] namesAndTypes = new Object[paramTypes.length * 2];
        for (int i = 0; i < paramTypes.length; i++) {
            namesAndTypes[i * 2] = meta.params()[i];
            namesAndTypes[i * 2 + 1] = paramTypes[i];
        }
        try {
            if (meta.doesThrow()) {
                jactlClass.methodCanThrow(meta.name(), method.getName(), namesAndTypes);
            } else {
                jactlClass.method(meta.name(), method.getName(), namesAndTypes);
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static void exportFunctions(JactlContext context, Class<?> type) {
        for (Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(Export.Function.class)) {
                continue;
            }
            Export.Function meta = method.getAnnotation(Export.Function.class);
            JactlFunction function = new JactlFunction(context)
                    .name(meta.name())
                    .impl(method.getDeclaringClass(), method.getName());
            for (String param : meta.params()) {
                function.param(param);
            }
            function.register();
        }
    }

    private JactlExport() {
        throw new UnsupportedOperationException();
    }

}
