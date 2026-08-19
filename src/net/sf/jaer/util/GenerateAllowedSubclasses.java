package net.sf.jaer.util;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.filter.AbstractNoiseFilter;
import net.sf.jaer.graphics.DisplayMethod;

/**
 * Build-time scanner: writes allowlist resources under
 * {@link JaerAllowedSubclasses#RESOURCE_DIR}. Invoked from Ant after javac.
 * Does not initialize classes ({@code Class.forName(..., false, ...)}).
 */
public final class GenerateAllowedSubclasses {

    public static final Class<?>[] SUPERTYPES = {
        AEChip.class,
        EventFilter2D.class,
        EventFilter.class,
        AbstractNoiseFilter.class,
        DisplayMethod.class
    };

    private GenerateAllowedSubclasses() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: GenerateAllowedSubclasses <classesDir>");
            System.exit(2);
        }
        Path classesDir = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(classesDir)) {
            System.err.println("Not a directory: " + classesDir);
            System.exit(2);
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = GenerateAllowedSubclasses.class.getClassLoader();
        }
        List<String> all = listClassNames(classesDir);
        System.out.println("GenerateAllowedSubclasses: " + all.size() + " .class files under " + classesDir);
        Path outDir = classesDir.resolve(JaerAllowedSubclasses.RESOURCE_DIR.replace('/', java.io.File.separatorChar));
        Files.createDirectories(outDir);
        int failed = 0;
        for (Class<?> superType : SUPERTYPES) {
            TreeSet<String> names = new TreeSet<>();
            for (String fqcn : all) {
                try {
                    Class<?> c = Class.forName(fqcn, false, cl);
                    if (c == superType) {
                        continue;
                    }
                    if (c.getDeclaringClass() != null || fqcn.indexOf('$') >= 0) {
                        continue;
                    }
                    if (Modifier.isAbstract(c.getModifiers())) {
                        continue;
                    }
                    if (superType.isAssignableFrom(c)) {
                        names.add(fqcn);
                    }
                } catch (Throwable t) {
                    failed++;
                    if (failed <= 20) {
                        System.out.println("skip " + fqcn + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
                    }
                }
            }
            Path out = outDir.resolve(superType.getName() + ".txt");
            StringBuilder sb = new StringBuilder();
            for (String n : names) {
                sb.append(n).append('\n');
            }
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
            System.out.println("GenerateAllowedSubclasses: " + names.size() + " " + superType.getName()
                    + " -> " + out);
        }
        if (failed > 20) {
            System.out.println("GenerateAllowedSubclasses: " + failed + " classes skipped (errors)");
        }
    }

    static List<String> listClassNames(Path root) throws IOException {
        ArrayList<String> names = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".class")
                    && !p.getFileName().toString().contains("$"))
                    .forEach(p -> {
                        Path rel = root.relativize(p);
                        String s = rel.toString();
                        s = s.substring(0, s.length() - ".class".length());
                        names.add(s.replace('\\', '.').replace('/', '.'));
                    });
        }
        Collections.sort(names);
        return names;
    }
}
