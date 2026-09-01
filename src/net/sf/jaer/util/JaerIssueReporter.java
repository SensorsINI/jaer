package net.sf.jaer.util;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.JaerConstants;
import net.sf.jaer.graphics.AEViewer;

/**
 * Builds a local issue report and opens GitHub Issues. Cannot attach files
 * without an API token; copies the report to the clipboard and prefills
 * {@code issues/new}.
 */
public final class JaerIssueReporter {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int MAX_LOG_CHARS = 256 * 1024;
    private static final int MAX_URL_BODY_CHARS = 2500;
    private static final int MAX_CLIPBOARD_CHARS = 512 * 1024;
    private static final String SESSION_LOG_PREFIX = "jAER-";
    private static final String SESSION_LOG_SUFFIX = ".log";

    private JaerIssueReporter() {
    }

    /** Opens the GitHub Issues list (Help menu). */
    public static void openIssuesPage(Component parent) {
        browse(JaerConstants.JAER_ISSUES, parent);
    }

    /**
     * Writes a report file, copies it to the clipboard, and opens a prefilled
     * GitHub new-issue page.
     *
     * @param parent dialog parent, may be null
     * @param title short issue title
     * @param exceptionText stack trace or exception window text, may be null
     * @param extraConsole in-app console text, may be null
     * @return the written report file, or null if writing failed
     */
    public static File report(Component parent, String title, String exceptionText, String extraConsole) {
        return report(parent, title, exceptionText, extraConsole, null, null);
    }

    /**
     * Same as {@link #report(Component, String, String, String)} plus leftover
     * semaphore text and JVM crash dump files from an unclean previous exit.
     */
    public static File report(Component parent, String title, String exceptionText, String extraConsole,
            String semaphoreText, List<File> dumpFiles) {
        String heading = (title == null || title.isBlank()) ? "jAER issue" : title.trim();
        StringBuilder report = new StringBuilder(16 * 1024);
        report.append("jAER issue report\n");
        report.append("generated ").append(new Date()).append('\n');
        report.append("title: ").append(heading).append("\n\n");
        report.append("=== System information ===\n");
        report.append(systemInfo()).append('\n');
        if (semaphoreText != null && !semaphoreText.isBlank()) {
            report.append("=== Previous session semaphore ===\n");
            report.append(semaphoreText.trim()).append("\n\n");
        }
        if (exceptionText != null && !exceptionText.isBlank()) {
            report.append("=== Exception / window text ===\n");
            report.append(tail(exceptionText, MAX_LOG_CHARS)).append("\n\n");
        }
        if (extraConsole != null && !extraConsole.isBlank()) {
            report.append("=== In-app console ===\n");
            report.append(tail(extraConsole, MAX_LOG_CHARS)).append("\n\n");
        }
        appendSessionLogs(report);
        appendDumpFiles(report, dumpFiles);

        File reportFile = writeReportFile(report.toString());
        copyToClipboard(report.toString());
        String body = buildIssueBody(heading, reportFile, exceptionText);
        String url = JaerConstants.JAER_ISSUES_NEW
                + "?title=" + encode(heading)
                + "&body=" + encode(body);
        browse(url, parent);
        if (reportFile != null) {
            Window owner = parent instanceof Window w ? w
                    : (parent != null ? SwingUtilities.getWindowAncestor(parent) : null);
            String html = "<html>A report was copied to the clipboard and saved to:<br>"
                    + ShowFolderSaveConfirmation.escapeHtml(reportFile.getAbsolutePath())
                    + "<br><br>Paste it into the GitHub issue, or drag the file onto the issue page after signing in.";
            ShowFolderSaveConfirmation dialog = new ShowFolderSaveConfirmation(
                    owner, reportFile, html, null, null, "Report issue");
            dialog.setModal(true);
            dialog.setVisible(true);
        }
        return reportFile;
    }

    public static String systemInfo() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("jAER version: ").append(JaerConstants.getReleaseVersion()).append('\n');
        sb.append("jAER build:\n").append(JaerConstants.getBuildVersion()).append('\n');
        appendProp(sb, "os.name");
        appendProp(sb, "os.version");
        appendProp(sb, "os.arch");
        appendProp(sb, "java.version");
        appendProp(sb, "java.vm.name");
        appendProp(sb, "java.vm.version");
        appendProp(sb, "java.home");
        appendProp(sb, "user.dir");
        appendProp(sb, "java.io.tmpdir");
        sb.append("jaer.tmpdir=").append(JaerTmpdir.get().getAbsolutePath()).append('\n');
        appendProp(sb, "java.util.logging.config.file");
        try {
            sb.append("pid=").append(ProcessHandle.current().pid()).append('\n');
        } catch (Exception e) {
            sb.append("pid=(unavailable: ").append(e).append(")\n");
        }
        try {
            sb.append("heap: ").append(MemoryDiagnostics.heapSummary()).append('\n');
        } catch (Exception e) {
            sb.append("heap=(unavailable: ").append(e).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Newest rotating JUL session log ({@code %t/jaer/jAER-%g.log}), or null.
     * Also checks the legacy {@code %t/jAER-%g.log} location.
     */
    public static File sessionLogFile() {
        List<File> logs = findSessionLogs();
        return logs.isEmpty() ? null : logs.get(0);
    }

    public static List<File> findSessionLogs() {
        List<File> list = new ArrayList<>();
        collectSessionLogs(JaerTmpdir.get(), list);
        // Legacy location before ${java.io.tmpdir}/jaer/
        File systemTmp = JaerTmpdir.systemTmp();
        if (!systemTmp.equals(JaerTmpdir.get())) {
            collectSessionLogs(systemTmp, list);
        }
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        return list;
    }

    private static void collectSessionLogs(File dir, List<File> list) {
        File[] files = dir.listFiles((d, name) -> name.startsWith(SESSION_LOG_PREFIX)
                && name.endsWith(SESSION_LOG_SUFFIX));
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile()) {
                list.add(f);
            }
        }
    }

    /**
     * {@code hs_err_pid*.log} and {@code replay_pid*.log} in {@code user.dir},
     * {@link JaerTmpdir}, and the system temp root, matching {@code pid} when known,
     * otherwise newer than {@code sinceMs}.
     */
    public static List<File> findCrashDumps(Long pid, long sinceMs) {
        List<File> found = new ArrayList<>();
        File[] dirs = {
            new File(System.getProperty("user.dir", ".")),
            JaerTmpdir.get(),
            JaerTmpdir.systemTmp()
        };
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (!f.isFile()) {
                    continue;
                }
                String n = f.getName();
                if (!(n.startsWith("hs_err") && n.endsWith(".log"))
                        && !(n.startsWith("replay_pid") && n.endsWith(".log"))) {
                    continue;
                }
                if ((pid != null && n.contains("pid" + pid)) || f.lastModified() >= sinceMs) {
                    found.add(f);
                }
            }
        }
        found.sort(Comparator.comparingLong(File::lastModified).reversed());
        return found;
    }

    public static Long parseSemaphorePid(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        for (String line : detail.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("pid=")) {
                try {
                    return Long.parseLong(t.substring(4).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean isPidAlive(long pid) {
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * One-line command (and optional command line) for the live-PID dialog.
     */
    public static String processSummary(long pid) {
        try {
            return ProcessHandle.of(pid).map(ph -> {
                ProcessHandle.Info info = ph.info();
                String cmd = info.command().orElse("");
                String cl = info.commandLine().orElse("");
                if (cmd.isEmpty() && cl.isEmpty()) {
                    return "(OS did not report the program name)";
                }
                String s = cmd.isEmpty() ? cl : cmd;
                if (!cl.isEmpty() && !cl.equals(cmd) && cl.length() < 400) {
                    s = cl;
                }
                if (s.length() > 400) {
                    s = s.substring(0, 397) + "...";
                }
                return s;
            }).orElse("(process gone)");
        } catch (Exception e) {
            return "(could not inspect PID: " + e.getMessage() + ")";
        }
    }

    /**
     * True when {@code pid} is still a JVM/jAER launcher that likely wrote the
     * semaphore (not a reused PID). Never true for this starting process.
     */
    public static boolean looksLikeJaerProcess(long pid, long semaphoreMtimeMs) {
        if (pid == ProcessHandle.current().pid()) {
            return false;
        }
        try {
            return ProcessHandle.of(pid).map(ph -> looksLikeJaerProcess(ph, semaphoreMtimeMs)).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean looksLikeJaerProcess(ProcessHandle ph, long semaphoreMtimeMs) {
        if (!ph.isAlive() || ph.pid() == ProcessHandle.current().pid()) {
            return false;
        }
        ProcessHandle.Info info = ph.info();
        String cmd = info.command().orElse("").toLowerCase(Locale.ROOT);
        String cl = info.commandLine().orElse("").toLowerCase(Locale.ROOT);
        String blob = cmd + " " + cl;
        boolean javaOrLauncher = blob.contains("java") || blob.contains("javaw")
                || blob.contains("jaer") || blob.contains("install4j");
        if (!javaOrLauncher) {
            return false;
        }
        if (semaphoreMtimeMs > 0 && info.startInstant().isPresent()) {
            long started = info.startInstant().get().toEpochMilli();
            // Reused PID: process started well after the semaphore was written.
            if (started > semaphoreMtimeMs + 60_000L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Politely destroy, then {@link ProcessHandle#destroyForcibly()} if needed.
     * Does not destroy this JVM. Returns true when the PID is gone.
     */
    public static boolean forceQuitPid(long pid) {
        if (pid == ProcessHandle.current().pid()) {
            return false;
        }
        ProcessHandle ph;
        try {
            ph = ProcessHandle.of(pid).orElse(null);
        } catch (Exception e) {
            return false;
        }
        if (ph == null || !ph.isAlive()) {
            return true;
        }
        try {
            ph.destroy();
        } catch (Exception e) {
            log.warning("destroy PID " + pid + ": " + e);
        }
        if (waitUntilDead(ph, 5000L)) {
            return true;
        }
        try {
            ph.destroyForcibly();
        } catch (Exception e) {
            log.warning("destroyForcibly PID " + pid + ": " + e);
        }
        return waitUntilDead(ph, 3000L);
    }

    private static boolean waitUntilDead(ProcessHandle ph, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (!ph.isAlive()) {
                return true;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return !ph.isAlive();
            }
        }
        return !ph.isAlive();
    }

    /** In-app console text from visible {@link AEViewer} windows. */
    public static String collectOpenConsoleText() {
        StringBuilder sb = new StringBuilder();
        for (Frame f : Frame.getFrames()) {
            if (f instanceof AEViewer) {
                String t = ((AEViewer) f).getConsoleText();
                if (t != null && !t.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n--- next AEViewer console ---\n");
                    }
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }

    public static String semaphoreMetadata() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("JAERViewer started ").append(new Date()).append('\n');
        try {
            sb.append("pid=").append(ProcessHandle.current().pid()).append('\n');
        } catch (Exception e) {
            sb.append("pid=(unavailable)\n");
        }
        sb.append("version=").append(JaerConstants.getReleaseVersion()).append('\n');
        sb.append("user.dir=").append(System.getProperty("user.dir", "")).append('\n');
        sb.append("os=").append(System.getProperty("os.name", "")).append(' ')
                .append(System.getProperty("os.version", "")).append('\n');
        sb.append("java=").append(System.getProperty("java.version", "")).append('\n');
        return sb.toString();
    }

    /**
     * Temporary test UI (Ctrl+Shift+F12 in {@code AEViewer}). Throws an uncaught
     * exception or {@link Runtime#halt(int)} after writing a fake {@code hs_err}
     * so the next startup can offer to report an unclean exit.
     */
    public static void offerCrashTest(Component parent) {
        Object[] options = {
            "Uncaught exception (thread)",
            "Uncaught exception (EDT)",
            "Simulate native crash (halt)",
            "Cancel"
        };
        int choice = JOptionPane.showOptionDialog(parent,
                "<html>Temporary test trigger (<b>Ctrl+Shift+F12</b>).<br><br>"
                + "Uncaught exception opens the existing exception window (Report issue).<br>"
                + "Native crash writes a fake <code>hs_err_pid*.log</code> and "
                + "<code>Runtime.halt</code> so the running semaphore stays.<br>"
                + "Restart jAER to test the unclean-exit dialog.</html>",
                "Test issue reporter",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            Thread t = new Thread(() -> {
                throw new RuntimeException("jAER test uncaught exception (Ctrl+Shift+F12)");
            }, "jaer-issue-reporter-test");
            t.start();
        } else if (choice == 1) {
            SwingUtilities.invokeLater(() -> {
                throw new RuntimeException("jAER test uncaught EDT exception (Ctrl+Shift+F12)");
            });
        } else if (choice == 2) {
            simulateNativeCrash(parent);
        }
    }

    private static void simulateNativeCrash(Component parent) {
        int ok = JOptionPane.showConfirmDialog(parent,
                "Halt the JVM immediately (no shutdown hook).\n"
                + "Unsaved work is lost. Restart jAER to test crash reporting.",
                "Simulate native crash", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        long pid;
        try {
            pid = ProcessHandle.current().pid();
        } catch (Exception e) {
            pid = 0L;
        }
        File dump = new File(System.getProperty("user.dir", "."), "hs_err_pid" + pid + ".log");
        String text = "# Simulated hs_err for jAER issue-reporter test\n"
                + "# pid=" + pid + "\n"
                + "A fatal error has been detected by the Java Runtime Environment:\n"
                + "  EXCEPTION_ACCESS_VIOLATION (simulated via Ctrl+Shift+F12)\n\n"
                + systemInfo();
        try {
            Files.writeString(dump.toPath(), text, StandardCharsets.UTF_8);
            log.warning("Wrote simulated crash dump " + dump.getAbsolutePath() + "; Runtime.halt(1)");
        } catch (IOException e) {
            log.warning("Could not write simulated hs_err " + dump + ": " + e);
        }
        Runtime.getRuntime().halt(1);
    }

    private static void appendSessionLogs(StringBuilder report) {
        List<File> logs = findSessionLogs();
        if (logs.isEmpty()) {
            report.append("=== Session log ===\n(none found in ").append(JaerTmpdir.get().getAbsolutePath())
                    .append(")\n\n");
            return;
        }
        File newest = logs.get(0);
        report.append("=== Session log (").append(newest.getAbsolutePath()).append(") ===\n");
        report.append(readTail(newest, MAX_LOG_CHARS)).append("\n\n");
    }

    private static void appendDumpFiles(StringBuilder report, List<File> dumpFiles) {
        if (dumpFiles == null || dumpFiles.isEmpty()) {
            return;
        }
        for (File f : dumpFiles) {
            report.append("=== Dump ").append(f.getAbsolutePath()).append(" ===\n");
            report.append(readTail(f, MAX_LOG_CHARS)).append("\n\n");
        }
    }

    private static File writeReportFile(String text) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File file = JaerTmpdir.file("jaer-issue-report-" + stamp + ".txt");
        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            log.info("Wrote issue report " + file.getAbsolutePath());
            return file;
        } catch (IOException e) {
            log.warning("Could not write issue report " + file + ": " + e);
            return null;
        }
    }

    private static void copyToClipboard(String text) {
        try {
            String clip = tail(text, MAX_CLIPBOARD_CHARS);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(clip), null);
        } catch (Exception e) {
            log.warning("Could not copy issue report to clipboard: " + e);
        }
    }

    private static String buildIssueBody(String title, File reportFile, String exceptionText) {
        StringBuilder body = new StringBuilder(MAX_URL_BODY_CHARS);
        body.append("## ").append(title).append("\n\n");
        body.append("**jAER** ").append(JaerConstants.getReleaseVersion())
                .append(" / ").append(System.getProperty("os.name", "?"))
                .append(" ").append(System.getProperty("os.version", ""))
                .append(" / Java ").append(System.getProperty("java.version", "?")).append("\n\n");
        if (exceptionText != null && !exceptionText.isBlank()) {
            String oneLine = exceptionText.trim().split("\\R", 2)[0];
            if (oneLine.length() > 240) {
                oneLine = oneLine.substring(0, 240) + "...";
            }
            body.append(oneLine).append("\n\n");
        }
        body.append("The full report is on the clipboard");
        if (reportFile != null) {
            body.append(" and saved to `").append(reportFile.getAbsolutePath()).append('`');
        }
        body.append(". Please paste it below or attach the file.\n\n");
        body.append("```\n");
        body.append(systemInfo());
        body.append("```\n");
        String s = body.toString();
        if (s.length() > MAX_URL_BODY_CHARS) {
            s = s.substring(0, MAX_URL_BODY_CHARS);
        }
        return s;
    }

    private static void browse(String url, Component parent) {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(parent, "No Desktop support, can't open " + url);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Couldn't open " + url + "; caught " + ex);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static void appendProp(StringBuilder sb, String key) {
        sb.append(key).append('=').append(System.getProperty(key, "")).append('\n');
    }

    private static String tail(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return "...[truncated " + (text.length() - maxChars) + " chars]\n" + text.substring(text.length() - maxChars);
    }

    private static String readTail(File file, int maxChars) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String s = new String(bytes, StandardCharsets.UTF_8);
            return tail(s, maxChars);
        } catch (Exception e) {
            return "(could not read " + file + ": " + e + ")";
        }
    }
}
