package dev.pesek.turtles.computer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.pesek.turtles.computer.Computer;
import dev.pesek.turtles.computer.script.Export;
import dev.pesek.turtles.computer.script.ScriptExtension;
import dev.pesek.turtles.util.Colors;
import dev.pesek.turtles.util.Docs;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

@Docs("""
        Usage:
          apis
          apis <extension>
        
        Displays list of available script extensions or their documentation""")
public final class Apis implements ComputerCommand<Computer> {

    private static final Apis INSTANCE = new Apis();

    public static Apis get() {
        return INSTANCE;
    }

    private final LiteralArgumentBuilder<Computer.Executor<Computer>> node = LiteralArgumentBuilder
            .<Computer.Executor<Computer>>literal("apis")
            .executes(ctx -> {
                Computer computer = ctx.getSource().computer();
                String joinedExtensions = computer.getRegisteredExtensions().stream()
                        .map(ScriptExtension::key)
                        .map(NamespacedKey::toString)
                        .collect(Collectors.joining(" "));

                computer.print("Available script extensions:\n" + joinedExtensions);
                return SUCCESS;
            })
            .then(RequiredArgumentBuilder.<Computer.Executor<Computer>, String>argument("extension", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Computer computer = ctx.getSource().computer();
                        String targetExtension = ctx.getArgument("extension", String.class).toLowerCase().trim();
                        
                        computer.getRegisteredExtensions().stream()
                                .filter(extension -> extension.key().toString()
                                        .equalsIgnoreCase(targetExtension))
                                .findFirst()
                                .ifPresentOrElse(
                                        extension -> computer.print(getExtensionHelpPage(extension)),
                                        () -> computer.print(Colors.RED + "Unknown script extension: '"
                                                + targetExtension + "'")
                                );
                        return SUCCESS;
                    })
            );

    private Apis() {
    }

    @Override
    public LiteralArgumentBuilder<Computer.Executor<Computer>> build() {
        return node;
    }
    
    private static String getExtensionHelpPage(ScriptExtension extension) {
        StringBuilder builder = new StringBuilder();
        appendDocs(builder, extension.getClass().getAnnotation(Docs.class));
        builder.append(Colors.GREEN).append(extension.key()).append(Colors.RESET).append('\n');
        
        for (Class<?> exported : ScriptExtension.exportTypes(extension)) {
            for (Method method : exported.getMethods()) {
                if (method.isAnnotationPresent(Export.Function.class)) {
                    appendFunctionDocs(builder, method);
                }
            }
            if (exported.isAnnotationPresent(Export.Type.class)) {
                appendTypeDocs(builder, exported);
            }
        }
        
        return builder.toString();
    }
    
    private static void appendFunctionDocs(StringBuilder builder, Method method) {
        appendDocs(builder, method.getAnnotation(Docs.class));
        
        String name;
        String[] params;
        
        if (method.isAnnotationPresent(Export.Function.class)) {
            Export.Function meta = method.getAnnotation(Export.Function.class);
            name = meta.name();
            params = meta.params();
        } else if (method.isAnnotationPresent(Export.Method.class)) {
            Export.Method meta = method.getAnnotation(Export.Method.class);
            name = meta.name();
            params = meta.params();
        } else {
            throw new IllegalArgumentException("Missing meta annotation for " + method.getName());
        }
        
        builder.append(Colors.GOLD).append(name).append('(').append(String.join(", ", params))
                .append(')').append(Colors.RESET).append('\n');
    }
    
    private static void appendTypeDocs(StringBuilder builder, Class<?> type) {
        appendDocs(builder, type.getAnnotation(Docs.class));
        Export.Type meta = type.getAnnotation(Export.Type.class);
        builder.append(Colors.RED).append(meta.name()).append(Colors.RESET);
        if (meta.autoImport()) {
            builder.append(Colors.GRAY).append(Colors.ITALIC).append(" (auto-imported)").append(Colors.RESET);
        }
        builder.append('\n');
        for (Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(Export.Method.class)) {
                continue;
            }
            StringBuilder typeMethodBuilder = new StringBuilder();
            appendFunctionDocs(typeMethodBuilder, method);
            String[] lines = typeMethodBuilder.toString().split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (i != lines.length - 1) {
                    builder.append("  ").append(lines[i]).append('\n');
                } else {
                    builder.append("  ").append(Colors.YELLOW).append("*").append(Colors.RESET).append(" ")
                            .append(lines[i]).append('\n');
                }
            }
        }
    }

    private static void appendDocs(StringBuilder builder, @Nullable Docs docs) {
        if (docs == null) {
            return;
        }
        Arrays.stream(docs.value().split("\n")).forEach(l -> builder.append(Colors.GRAY)
                .append("// ").append(l).append(Colors.RESET).append('\n'));
    }

}
