import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Merges the jAER Ctrl+F5 task keybinding into Cursor (or VS Code) user
 * {@code keybindings.json}. VS Code/Cursor do not load {@code .vscode/keybindings.json}.
 *
 * <p>Usage: {@code java InstallJaerRunShortcut [cursor|code|both]}
 */
public final class InstallJaerRunShortcut {

    static final String ARGS_MARKER = "\"args\": \"jAER: ant run\"";

    static final String BINDING = String.join("\n",
            "    {",
            "        // jAER: Ctrl+F5 runs ant run as a task (no debugger). Output reuses the",
            "        // \"jAER: ant run\" terminal. F5 stays Java/debug launch.",
            "        // Installed by: ant install-jaer-run-shortcut",
            "        \"key\": \"ctrl+f5\",",
            "        \"command\": \"workbench.action.tasks.runTask\",",
            "        \"args\": \"jAER: ant run\",",
            "        \"when\": \"workspaceName == 'jaer'\"",
            "    }");

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        String editor = args.length > 0 ? args[0].trim().toLowerCase(Locale.ROOT) : "cursor";
        List<Path> targets = keybindingFiles(editor);
        if (targets.isEmpty()) {
            System.err.println("Unknown jaer.shortcut.editor=" + editor + " (use cursor, code, or both)");
            System.exit(2);
        }
        int changed = 0;
        for (Path path : targets) {
            Result r = install(path);
            System.out.println(r.message);
            if (r.changed) {
                changed++;
            }
        }
        if (changed > 0) {
            System.out.println("Reload the Cursor/VS Code window if Ctrl+F5 does not take effect.");
        }
    }

    enum Status {
        CREATED, UPDATED, ALREADY
    }

    static final class Result {
        final Status status;
        final boolean changed;
        final String message;

        Result(Status status, Path path) {
            this.status = status;
            this.changed = status == Status.CREATED || status == Status.UPDATED;
            switch (status) {
                case CREATED:
                    this.message = "Created " + path;
                    break;
                case UPDATED:
                    this.message = "Added jAER Ctrl+F5 binding to " + path;
                    break;
                default:
                    this.message = "Already installed in " + path;
                    break;
            }
        }
    }

    static List<Path> keybindingFiles(String editor) {
        Set<Path> out = new LinkedHashSet<Path>();
        if ("cursor".equals(editor) || "both".equals(editor)) {
            out.add(userKeybindings("Cursor"));
        }
        if ("code".equals(editor) || "both".equals(editor) || "vscode".equals(editor)) {
            out.add(userKeybindings("Code"));
        }
        return new ArrayList<Path>(out);
    }

    static Path userKeybindings(String productDir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Paths.get(System.getProperty("user.home"));
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            Path root = appdata != null && !appdata.isEmpty()
                    ? Paths.get(appdata)
                    : home.resolve("AppData").resolve("Roaming");
            return root.resolve(productDir).resolve("User").resolve("keybindings.json");
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support")
                    .resolve(productDir).resolve("User").resolve("keybindings.json");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path config = xdg != null && !xdg.isEmpty() ? Paths.get(xdg) : home.resolve(".config");
        return config.resolve(productDir).resolve("User").resolve("keybindings.json");
    }

    static Result install(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, ("[\n" + BINDING + "\n]\n").getBytes(StandardCharsets.UTF_8));
            return new Result(Status.CREATED, path);
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.contains(ARGS_MARKER)) {
            return new Result(Status.ALREADY, path);
        }
        String merged = insertBinding(text);
        Files.write(path, merged.getBytes(StandardCharsets.UTF_8));
        return new Result(Status.UPDATED, path);
    }

    static String insertBinding(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "[\n" + BINDING + "\n]\n";
        }
        int close = lastArrayClose(text);
        if (close < 0) {
            throw new IllegalStateException("keybindings.json is not a JSON array (no closing ])");
        }
        int code = lastCodeChar(text, close);
        String before = text.substring(0, close);
        String after = text.substring(close);
        if (code < 0 || text.charAt(code) == '[') {
            return before + BINDING + "\n" + after;
        }
        if (text.charAt(code) == ',') {
            return before + BINDING + "\n" + after;
        }
        return before + ",\n" + BINDING + "\n" + after;
    }

    static int lastArrayClose(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ']') {
                return i;
            }
            if (!Character.isWhitespace(c)) {
                return -1;
            }
        }
        return -1;
    }

    /** Index of last non-whitespace, non-//-comment character before {@code before}. */
    static int lastCodeChar(String text, int before) {
        int i = before - 1;
        while (i >= 0) {
            while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
                i--;
            }
            if (i < 0) {
                return -1;
            }
            int lineStart = text.lastIndexOf('\n', i) + 1;
            String line = text.substring(lineStart, i + 1).trim();
            if (line.startsWith("//")) {
                i = lineStart - 1;
                continue;
            }
            return i;
        }
        return -1;
    }

    static void selfTest() {
        String added = insertBinding("[\n]\n");
        require(added.contains(ARGS_MARKER), "empty array");
        String withItem = insertBinding("[\n    { \"key\": \"alt+x\", \"command\": \"foo\" }\n]\n");
        require(withItem.contains("\"command\": \"foo\"") && withItem.contains(ARGS_MARKER), "append");
        require(("[\n" + BINDING + "\n]\n").contains(ARGS_MARKER), "marker");
        System.out.println("self-test ok");
    }

    static void require(boolean ok, String name) {
        if (!ok) {
            throw new IllegalStateException("self-test failed: " + name);
        }
    }
}
