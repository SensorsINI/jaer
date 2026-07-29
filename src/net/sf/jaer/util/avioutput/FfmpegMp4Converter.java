package net.sf.jaer.util.avioutput;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import net.sf.jaer.util.MessageWithLink;

/**
 * Converts an intermediate AVI to MP4 using an external ffmpeg on PATH (or a
 * prefs-configured path). Does not bundle ffmpeg.
 * <p>
 * On Windows, also reads the User/Machine PATH from the registry and scans
 * common WinGet install locations, so ffmpeg installed after jAER started can
 * still be found without restarting the JVM.
 *
 * @author tobi
 */
public final class FfmpegMp4Converter {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Preferences prefs = Preferences.userNodeForPackage(FfmpegMp4Converter.class);

    public static final String FFMPEG_DOWNLOAD_URL = "https://ffmpeg.org/download.html";
    public static final String PREF_FFMPEG_PATH = "ffmpegPath";

    private FfmpegMp4Converter() {
    }

    /**
     * @return configured ffmpeg path, or empty string for PATH lookup
     */
    public static String getConfiguredFfmpegPath() {
        return prefs.get(PREF_FFMPEG_PATH, "");
    }

    public static void setConfiguredFfmpegPath(String path) {
        if (path == null) {
            path = "";
        }
        prefs.put(PREF_FFMPEG_PATH, path.trim());
    }

    /**
     * Resolves ffmpeg executable: prefs path if it exists, then PATH (including
     * refreshed Windows registry PATH), then common install locations.
     *
     * @return absolute path to ffmpeg, or null if not found
     */
    public static String findFfmpeg() {
        String configured = getConfiguredFfmpegPath();
        if (configured != null && !configured.isEmpty()) {
            File f = new File(configured);
            if (f.isFile()) {
                return f.getAbsolutePath();
            }
            log.warning("Configured ffmpeg path not found: " + configured);
        }

        // Absolute candidates first (survive stale JVM PATH after winget install)
        for (File candidate : discoverFfmpegCandidates()) {
            if (candidate != null && candidate.isFile()) {
                log.info("Found ffmpeg at " + candidate.getAbsolutePath());
                return candidate.getAbsolutePath();
            }
        }

        // Last resort: bare command using current process PATH
        if (canRunFfmpeg("ffmpeg")) {
            String resolved = resolveViaWhere("ffmpeg");
            return resolved != null ? resolved : "ffmpeg";
        }
        if (isWindows() && canRunFfmpeg("ffmpeg.exe")) {
            String resolved = resolveViaWhere("ffmpeg.exe");
            return resolved != null ? resolved : "ffmpeg.exe";
        }
        return null;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    /**
     * Collect likely ffmpeg.exe locations without relying on the JVM's inherited PATH.
     */
    private static List<File> discoverFfmpegCandidates() {
        LinkedHashSet<File> out = new LinkedHashSet<>();
        for (String dir : collectSearchDirs()) {
            if (dir == null || dir.isEmpty()) {
                continue;
            }
            File exe = new File(dir, isWindows() ? "ffmpeg.exe" : "ffmpeg");
            if (exe.isFile()) {
                out.add(exe);
            }
        }
        if (isWindows()) {
            scanWingetPackages(out);
        }
        return new ArrayList<>(out);
    }

    private static Set<String> collectSearchDirs() {
        LinkedHashSet<String> dirs = new LinkedHashSet<>();
        // Current process PATH
        addPathEntries(dirs, System.getenv("PATH"));
        if (isWindows()) {
            // Registry PATH — updated by winget even if this JVM was already running
            addPathEntries(dirs, readWindowsRegistryPath("HKEY_CURRENT_USER\\Environment", "Path"));
            addPathEntries(dirs, readWindowsRegistryPath(
                    "HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment", "Path"));
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                dirs.add(localAppData + "\\Microsoft\\WinGet\\Links");
            }
            String pf = System.getenv("ProgramFiles");
            if (pf != null) {
                dirs.add(pf + "\\ffmpeg\\bin");
            }
            String home = System.getProperty("user.home");
            if (home != null) {
                dirs.add(home + "\\scoop\\apps\\ffmpeg\\current\\bin");
            }
        } else {
            dirs.add("/usr/bin");
            dirs.add("/usr/local/bin");
            dirs.add("/opt/homebrew/bin");
        }
        return dirs;
    }

    private static void addPathEntries(Set<String> dirs, String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) {
            return;
        }
        String sep = isWindows() ? ";" : ":";
        for (String part : pathEnv.split(sep)) {
            String p = part.trim();
            if (!p.isEmpty()) {
                dirs.add(p);
            }
        }
    }

    /**
     * Reads a PATH-like value from the Windows registry via {@code reg query}.
     */
    private static String readWindowsRegistryPath(String key, String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", key, "/v", valueName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            // REG_SZ or REG_EXPAND_SZ line: "    Path    REG_EXPAND_SZ    C:\..."
            for (String line : sb.toString().split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.toLowerCase(Locale.ROOT).startsWith(valueName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String[] parts = trimmed.split("\\s{2,}");
                if (parts.length >= 3) {
                    return expandWindowsEnv(parts[parts.length - 1].trim());
                }
            }
        } catch (Exception e) {
            log.fine("Could not read registry PATH from " + key + ": " + e);
        }
        return null;
    }

    private static String expandWindowsEnv(String value) {
        if (value == null) {
            return null;
        }
        // Expand common %VAR% used in REG_EXPAND_SZ
        String out = value;
        out = out.replace("%USERPROFILE%", System.getProperty("user.home", ""));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            out = out.replace("%LOCALAPPDATA%", local);
        }
        String pf = System.getenv("ProgramFiles");
        if (pf != null) {
            out = out.replace("%ProgramFiles%", pf);
        }
        String pfx86 = System.getenv("ProgramFiles(x86)");
        if (pfx86 != null) {
            out = out.replace("%ProgramFiles(x86)%", pfx86);
        }
        return out;
    }

    private static void scanWingetPackages(Set<File> out) {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) {
            return;
        }
        Path packages = Paths.get(localAppData, "Microsoft", "WinGet", "Packages");
        if (!Files.isDirectory(packages)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packages, "Gyan.FFmpeg*")) {
            for (Path pkg : stream) {
                findFfmpegUnder(pkg, out, 0);
            }
        } catch (Exception e) {
            log.fine("WinGet package scan: " + e);
        }
        // Also any *FFmpeg* package id
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packages, "*FFmpeg*")) {
            for (Path pkg : stream) {
                findFfmpegUnder(pkg, out, 0);
            }
        } catch (Exception e) {
            log.fine("WinGet package scan: " + e);
        }
    }

    private static void findFfmpegUnder(Path dir, Set<File> out, int depth) {
        if (dir == null || depth > 5 || !Files.isDirectory(dir)) {
            return;
        }
        Path direct = dir.resolve("ffmpeg.exe");
        if (Files.isRegularFile(direct)) {
            out.add(direct.toFile());
            return;
        }
        Path bin = dir.resolve("bin").resolve("ffmpeg.exe");
        if (Files.isRegularFile(bin)) {
            out.add(bin.toFile());
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    findFfmpegUnder(child, out, depth + 1);
                } else if (child.getFileName() != null
                        && child.getFileName().toString().equalsIgnoreCase("ffmpeg.exe")) {
                    out.add(child.toFile());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String resolveViaWhere(String cmd) {
        if (!isWindows()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("where.exe", cmd);
            pb.redirectErrorStream(true);
            // Use refreshed PATH for where.exe
            String refreshed = joinPath(collectSearchDirs());
            pb.environment().put("PATH", refreshed);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                if (line != null && !line.isEmpty() && new File(line.trim()).isFile()) {
                    p.waitFor(3, TimeUnit.SECONDS);
                    return line.trim();
                }
            }
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.fine("where.exe failed: " + e);
        }
        return null;
    }

    private static String joinPath(Set<String> dirs) {
        String sep = isWindows() ? ";" : ":";
        StringBuilder sb = new StringBuilder();
        for (String d : dirs) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(d);
        }
        return sb.toString();
    }

    private static boolean canRunFfmpeg(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isFfmpegAvailable() {
        return findFfmpeg() != null;
    }

    /**
     * Shows that AVI was saved but MP4 needs ffmpeg, with download link.
     */
    public static void showMissingFfmpegDialog(Component parent, File aviFile) {
        String aviMsg = aviFile != null ? ("AVI saved to:<br><code>" + aviFile.getAbsolutePath() + "</code><p>") : "";
        String html = aviMsg
                + "MP4 conversion requires <b>ffmpeg</b>, which was not found.<p>"
                + "If you just installed it (e.g. via winget), click <b>Detect</b> in the Export video dialog "
                + "(jAER refreshes PATH from Windows without restarting), or paste the full path to "
                + "<code>ffmpeg.exe</code>.<p>"
                + "Download Windows builds here: "
                + "<a href=\"" + FFMPEG_DOWNLOAD_URL + "\">" + FFMPEG_DOWNLOAD_URL + "</a>";
        JOptionPane.showMessageDialog(parent, new MessageWithLink(html), "ffmpeg not found", JOptionPane.WARNING_MESSAGE);
    }

    public interface ConvertCallback {
        void done(boolean success, File mp4File, String message);
    }

    /**
     * Runs ffmpeg asynchronously; invokes callback on the EDT.
     */
    public static void convertAviToMp4Async(Component parent, File aviFile, File mp4File, boolean deleteAviOnSuccess, ConvertCallback callback) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            private String message = "";
            private File out = mp4File;

            @Override
            protected Boolean doInBackground() {
                String ffmpeg = findFfmpeg();
                if (ffmpeg == null) {
                    message = "ffmpeg not found";
                    return false;
                }
                if (aviFile == null || !aviFile.isFile()) {
                    message = "AVI file missing: " + aviFile;
                    return false;
                }
                if (out == null) {
                    String base = aviFile.getAbsolutePath();
                    int dot = base.lastIndexOf('.');
                    out = new File((dot > 0 ? base.substring(0, dot) : base) + ".mp4");
                }
                try {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(ffmpeg);
                    cmd.add("-y");
                    cmd.add("-i");
                    cmd.add(aviFile.getAbsolutePath());
                    cmd.add("-c:v");
                    cmd.add("libx264");
                    cmd.add("-pix_fmt");
                    cmd.add("yuv420p");
                    cmd.add("-movflags");
                    cmd.add("+faststart");
                    cmd.add(out.getAbsolutePath());
                    log.info("Running: " + String.join(" ", cmd));
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            sb.append(line).append('\n');
                            if (sb.length() > 8000) {
                                sb.delete(0, sb.length() - 4000);
                            }
                        }
                    }
                    int code = p.waitFor();
                    if (code != 0) {
                        message = "ffmpeg exited with code " + code + "\n" + sb;
                        log.warning(message);
                        return false;
                    }
                    if (deleteAviOnSuccess && aviFile.exists() && !aviFile.delete()) {
                        log.warning("Could not delete intermediate AVI " + aviFile);
                    }
                    message = "Wrote " + out.getAbsolutePath();
                    log.info(message);
                    return true;
                } catch (Exception e) {
                    message = e.toString();
                    log.warning(message);
                    return false;
                }
            }

            @Override
            protected void done() {
                boolean ok = false;
                try {
                    ok = get();
                } catch (Exception e) {
                    message = e.toString();
                }
                if (!ok && "ffmpeg not found".equals(message)) {
                    showMissingFfmpegDialog(parent, aviFile);
                }
                if (callback != null) {
                    callback.done(ok, out, message);
                }
            }
        };
        worker.execute();
    }
}
