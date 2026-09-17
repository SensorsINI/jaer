/*
 * Copyright (C) 2020 Tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.apache.commons.io.IOUtils;

/**
 * Should hold constants for project
 * 
 * 
 * @author Tobi
 */
public class JaerConstants {
    private static final Logger log=Logger.getLogger(JaerConstants.class.getName());
    
    /** Root of jaer preferences */
    public static final String PREFS_ROOT_NAME="jaer";
    public static final Preferences PREFS_ROOT=Preferences.userRoot().node(PREFS_ROOT_NAME);
    /**
     * When true, shutdown must not rewrite Preferences (File → Preferences → revert-all).
     */
    public static volatile boolean skipPreferenceWriteOnExit = false;
    /** Used for devices, retinas, cochleas */
    public static final Preferences PREFS_ROOT_CHIPS=Preferences.userRoot().node(PREFS_ROOT_NAME).node("chips");
    /** Used for things like USB interfaces that might not (yet) be attached to specific chips */
    public static final Preferences PREFS_ROOT_HARDWARE=Preferences.userRoot().node(PREFS_ROOT_NAME).node("hardware");
    public static final String ICON_IMAGE="/net/sf/jaer/images/jaer-icon.png";
    public static final String ICON_IMAGE_MAIN="/net/sf/jaer/images/jaer-main.png";
    public static final String ICON_IMAGE_HARDWARE="/net/sf/jaer/images/jaer-hardware.png";
    public static final String ICON_IMAGE_FILTERS="/net/sf/jaer/images/jaer-filters.png";
    public static final String SPLASH_SCREEN_IMAGE="/net/sf/jaer/images/SplashScreen.png";
    public static final String VERSION_FILE="BUILDVERSION.txt";
    /**
     * User-facing product name (About, README). Same string as install4j
     * {@code <application name>} / SignPath pe-file product-name.
     */
    public static final String APPLICATION_NAME = "jAER - Desktop Application for Event Sensors";
    /** @deprecated use {@link #APPLICATION_NAME} */
    @Deprecated
    public static final String INSTALLER_PRODUCT_NAME = APPLICATION_NAME;
    public static final String JAER_HOME = "https://github.com/SensorsINI/jaer.git";
    public static final String JAER_RELEASES = "https://github.com/SensorsINI/jaer/releases";
    /** Curated recordings zip on the GitHub Latest release (not packed in the installer). */
    public static final String SAMPLE_DATA_DOWNLOAD_URL = "https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip";
    /** User-facing file list on GitHub (opened in the browser when online). */
    public static final String SAMPLE_DATA_README_URL = "https://github.com/SensorsINI/jaer/tree/master/sampleData#readme";
    public static final String JAER_COMMITS = "https://github.com/SensorsINI/jaer/commits/master";
    public static final String JAER_ISSUES = "https://github.com/SensorsINI/jaer/issues";
    public static final String JAER_ISSUES_NEW = JAER_ISSUES + "/new";
    /** Short anonymous Google Form (Help → Give feedback…). */
    public static final String HELP_URL_FEEDBACK_FORM = "https://docs.google.com/forms/d/e/1FAIpQLSe1YZNLg1gN7n82YnSK3ilgSV-YBkcaKEunx5woUJ7inE9cvw/viewform?usp=sharing";
    public static final String HELP_URL_JAER_HOME = JAER_HOME;
    public static final String HELP_USER_GUIDE_URL_FLASHY = "https://docs.inivation.com/hardware/hardware-advanced-usage/firmware-update.html"; //"https://gitlab.com/inivation/devices-bin";
    public static final String HELP_FLASHY_LINUX_DOWNLOAD="https://s3.eu-central-1.amazonaws.com/release.inivation.com/flashy/flashy-linux-1.7.1.zip";
    public static final String HELP_URL_USER_GUIDE = "https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing";
    public static final String HELP_URL_INIVATION_CAMERAS = "https://docs.inivation.com/hardware/current-products/index.html";
    public static final String HELP_URL_PROPHESEE_CAMERAS = "https://docs.prophesee.ai/stable/hw/sensors/index.html";
    public static final String HELP_URL_NRV_CAMERAS = "https://nrvcorp.github.io/docs/";
    /** @deprecated use {@link #HELP_URL_INIVATION_CAMERAS} */
    @Deprecated
    public static final String HELP_URL_HARDWARE_USER_GUIDE = HELP_URL_INIVATION_CAMERAS;
    public static final String HELP_URL_HELP_FORUM = "https://groups.google.com/forum/#!forum/jaer-users";
    /** Community list of event-based vision papers, datasets, code, workshops, etc. */
    public static final String HELP_URL_EVENT_BASED_VISION_RESOURCES = "https://github.com/uzh-rpg/event-based_vision_resources";
    /** DVS09 / DVS128 sample recordings (Google Doc with download links). */
    public static final String HELP_URL_DVS128_SAMPLE_DATA = "https://docs.google.com/document/d/16b4H78f4vG_QvYDK2Tq0sNBA-y7UFnRbNnsGbD1jJOg/edit?tab=t.0";
    /** DAVIS346 AEDAT-2 sample recordings (Google Drive via DAVIS24 site). */
    public static final String HELP_URL_DAVIS346_SAMPLE_DATA = "https://sites.google.com/view/davis24-davis-sample-data/home";
    /** Open AEDAT-4 (DV) sample recordings with per-file download links (DAVIS346). */
    public static final String HELP_URL_AEDAT4_SAMPLE_DATA = "https://github.com/MISTLab/event_based_data";
    /** iniVation release repository AEDAT-4 sample datasets. */
    public static final String HELP_URL_INIVATION_AEDAT4_DATA = "https://release.inivation.com/?prefix=datasets/";
    /** Dual-camera DAVIS346 + DVXplorer muxed AEDAT-4 (Ghosh et al. EvDownsampling). */
    public static final String HELP_URL_EVDOWNSAMPLING = "https://github.com/anindyaghosh/EvDownsampling#readme";
    /** Prophesee / Metavision sample recordings and datasets (RAW EVT2/EVT3, HDF5, DAT). */
    public static final String HELP_URL_PROPHESEE_SAMPLE_DATA = "https://docs.prophesee.ai/stable/datasets.html#chapter-datasets";
    /** User guide: File → Remote (OpenCV / DNN mmap / ROS2) and Python dataloaders. */
    public static final String HELP_URL_DNN_OPENCV_ROS = "https://github.com/SensorsINI/jaer/blob/master/docs/README-DNN-OpenCV-ROS.md";
    private static boolean loggedVersionInfoAlready=false;
    private static String cachedBuildVersion;

    /**
     * Full identity for About, startup log, and GitHub issue reports: release
     * line, git commit (SHA), describe, subject, author date, then the Ant
     * build host. Prefers {@code BUILDVERSION.txt} in the jar, then a copy next
     * to the launcher. If {@code user.dir} is a jAER git checkout, appends the
     * live working-tree commit (may differ from the jar after {@code ant run}
     * without rebuild).
     */
    public static final String getBuildVersion(){
        if (cachedBuildVersion != null) {
            return cachedBuildVersion;
        }
        String baked = readClasspathBuildVersion();
        if (baked == null) {
            baked = readFileBuildVersion(Path.of(VERSION_FILE));
        }
        if (baked == null) {
            baked = "(missing file " + VERSION_FILE + " in jAER.jar and next to the launcher)";
        }
        String live = workingTreeGitSummary();
        String version = live == null ? baked : baked.stripTrailing() + "\n\n" + live;
        if (!loggedVersionInfoAlready) {
            String commit = parseGitCommitId(version);
            String describe = parseLabeledValue(version, "git.describe");
            String subject = parseLabeledValue(version, "git.subject");
            log.info("jAER " + getReleaseVersionFromBuildText(baked)
                    + (commit != null ? " commit " + commit : "")
                    + (describe != null ? " (" + describe + ")" : "")
                    + (subject != null ? " — " + subject : ""));
            log.info("Build identity:\n" + formatBuildIdentityTable(buildIdentityRows(version,
                    currentRuntimeSummary(), null)));
            loggedVersionInfoAlready = true;
        }
        cachedBuildVersion = version;
        return version;
    }

    /**
     * Full SHA from {@link #getBuildVersion()}, or null if git was unavailable
     * at build time and there is no working tree.
     */
    public static String getGitCommitId() {
        return parseGitCommitId(getBuildVersion());
    }

    /**
     * Field/value rows for About and issue reports. Human fields first; SHA last
     * among git identity. Tab-separated copy is {@link #getBuildIdentityTable()}.
     */
    public static List<String[]> getBuildIdentityRows() {
        return buildIdentityRows(getBuildVersion(), currentRuntimeSummary(), getProcessAuthenticodeSummary());
    }

    /** Same rows as TSV ({@code Field\\tValue}), including a header, for clipboard and logs. */
    public static String getBuildIdentityTable() {
        return formatBuildIdentityTable(getBuildIdentityRows());
    }

    static List<String[]> buildIdentityRows(String raw, String runningOn, String signature) {
        List<String[]> rows = new ArrayList<>();
        addRow(rows, "Version", getReleaseVersionFromBuildText(raw));
        addRow(rows, "Revision", parseLabeledValue(raw, "git.describe"));
        addRow(rows, "Last change", parseLabeledValue(raw, "git.subject"));
        addRow(rows, "Date", formatGitDate(parseLabeledValue(raw, "git.date")));
        addRow(rows, "Author", parseLabeledValue(raw, "git.author"));
        String effective = parseGitCommitId(raw);
        String baked = parseFirstLabeledValue(raw, "git.commit");
        addRow(rows, "Commit", effective);
        if (baked != null && effective != null && !baked.equalsIgnoreCase(effective)) {
            addRow(rows, "Jar commit", baked);
        }
        addRow(rows, "Source", parseLabeledValue(raw, "git.url"));
        addRow(rows, "Built", parseLinePrefix(raw, "Built "));
        String osName = parseFirstLabeledValue(raw, "os.name");
        String osVer = parseFirstLabeledValue(raw, "os.version");
        if (osName != null) {
            addRow(rows, "Build OS", osVer == null ? osName : osName + " " + osVer);
        }
        String javaVer = parseFirstLabeledValue(raw, "java.version");
        String javaVendor = parseFirstLabeledValue(raw, "java.vendor");
        if (javaVer != null) {
            addRow(rows, "Build Java", javaVendor == null ? javaVer : javaVer + " (" + javaVendor + ")");
        }
        addRow(rows, "Running on", runningOn);
        addRow(rows, "Signature", signature);
        addRow(rows, "Checkout", parseWorkingTreePath(raw));
        return rows;
    }

    static String formatBuildIdentityTable(List<String[]> rows) {
        StringBuilder sb = new StringBuilder(rows.size() * 64);
        sb.append("Field\tValue\n");
        for (String[] row : rows) {
            sb.append(row[0]).append('\t').append(flattenCell(row[1])).append('\n');
        }
        return sb.toString();
    }

    private static void addRow(List<String[]> rows, String field, String value) {
        if (value == null) {
            return;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return;
        }
        rows.add(new String[] { field, v });
    }

    private static String flattenCell(String value) {
        return value.replace('\t', ' ').replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

    static String formatGitDate(String iso) {
        if (iso == null || iso.isEmpty()) {
            return iso;
        }
        return iso.replace('T', ' ');
    }

    static String parseFirstLabeledValue(String buildText, String key) {
        if (buildText == null || key == null) {
            return null;
        }
        String prefix = key + "=";
        for (String line : buildText.split("\\R")) {
            if (line.startsWith(prefix)) {
                String v = line.substring(prefix.length()).trim();
                return v.isEmpty() || v.startsWith("(") ? null : v;
            }
        }
        return null;
    }

    static String parseLinePrefix(String buildText, String prefix) {
        if (buildText == null || prefix == null) {
            return null;
        }
        for (String line : buildText.split("\\R")) {
            if (line.startsWith(prefix)) {
                String v = line.trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    static String parseWorkingTreePath(String buildText) {
        String line = parseLinePrefix(buildText, "Working tree ");
        if (line == null) {
            return null;
        }
        return line.substring("Working tree ".length()).trim();
    }

    private static String currentRuntimeSummary() {
        return System.getProperty("os.name")
                + " " + System.getProperty("os.version")
                + ", Java " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")";
    }

    /** Last {@code git.commit=} SHA in the text (working tree wins over the jar). */
    static String parseGitCommitId(String buildText) {
        if (buildText == null) {
            return null;
        }
        String last = null;
        String prefix = "git.commit=";
        for (String line : buildText.split("\\R")) {
            if (line.startsWith(prefix)) {
                String v = line.substring(prefix.length()).trim();
                if (v.matches("[0-9a-fA-F]{7,40}")) {
                    last = v;
                }
            }
        }
        if (last != null) {
            return last;
        }
        for (String line : buildText.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("commit ") && t.length() > 7) {
                String rest = t.substring(7).trim();
                int sp = rest.indexOf(' ');
                String token = sp < 0 ? rest : rest.substring(0, sp);
                if (token.matches("[0-9a-fA-F]{7,40}")) {
                    return token;
                }
            }
        }
        return null;
    }

    static String parseLabeledValue(String buildText, String key) {
        if (buildText == null || key == null) {
            return null;
        }
        String prefix = key + "=";
        String last = null;
        for (String line : buildText.split("\\R")) {
            if (line.startsWith(prefix)) {
                String v = line.substring(prefix.length()).trim();
                if (!v.isEmpty() && !v.startsWith("(")) {
                    last = v;
                }
            }
        }
        return last;
    }

    private static String getReleaseVersionFromBuildText(String build) {
        if (build == null || build.isEmpty() || build.startsWith("(")) {
            return "dev";
        }
        String first = build.split("\\R", 2)[0].trim();
        return first.isEmpty() ? "dev" : first;
    }

    private static String readClasspathBuildVersion() {
        ClassLoader cl = JaerConstants.class.getClassLoader();
        log.fine("Loading version info from resource " + VERSION_FILE);
        URL versionURL = cl.getResource(VERSION_FILE);
        log.fine("Version URL=" + versionURL);
        if (versionURL == null) {
            return null;
        }
        try {
            Object urlContents = versionURL.getContent();
            if (urlContents instanceof InputStream) {
                StringWriter writer = new StringWriter();
                IOUtils.copy((InputStream) urlContents, writer, StandardCharsets.UTF_8);
                String version = writer.toString();
                return version.isBlank() ? null : version;
            }
            return null;
        } catch (Exception e) {
            log.fine("Could not read classpath " + VERSION_FILE + ": " + e);
            return null;
        }
    }

    private static String readFileBuildVersion(Path p) {
        try {
            if (p != null && Files.isRegularFile(p)) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                return s.isBlank() ? null : s;
            }
        } catch (Exception e) {
            log.fine("Could not read " + p + ": " + e);
        }
        return null;
    }

    /**
     * Live git only when this process was started from a jAER checkout
     * ({@code .git} + {@code VERSION.txt} in {@code user.dir}). Installers have
     * neither.
     */
    private static String workingTreeGitSummary() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir.resolve(".git")) || !Files.isRegularFile(dir.resolve("VERSION.txt"))) {
            return null;
        }
        String commit = git(dir, "rev-parse", "HEAD");
        if (commit == null) {
            return null;
        }
        String describe = git(dir, "describe", "--tags", "--always", "--dirty");
        String subject = git(dir, "log", "-1", "--pretty=format:%s");
        String date = git(dir, "log", "-1", "--pretty=format:%cI");
        String author = git(dir, "log", "-1", "--pretty=format:%an");
        StringBuilder sb = new StringBuilder(256);
        sb.append("Working tree ").append(dir).append('\n');
        sb.append("git.commit=").append(commit).append('\n');
        if (describe != null) {
            sb.append("git.describe=").append(describe).append('\n');
        }
        if (subject != null) {
            sb.append("git.subject=").append(subject).append('\n');
        }
        if (date != null) {
            sb.append("git.date=").append(date).append('\n');
        }
        if (author != null) {
            sb.append("git.author=").append(author).append('\n');
        }
        sb.append("git.url=https://github.com/SensorsINI/jaer/commit/").append(commit);
        return sb.toString();
    }

    private static String git(Path dir, String... args) {
        List<String> cmd = new ArrayList<>(args.length + 1);
        cmd.add("git");
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String out = IOUtils.toString(p.getInputStream(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0 || out.isEmpty()) {
                return null;
            }
            return out;
        } catch (Exception e) {
            log.fine("git " + String.join(" ", args) + ": " + e);
            return null;
        }
    }

    /**
     * Short release version for UI (e.g. splash / welcome overlay), from first line of
     * {@link #VERSION_FILE} or repo-root {@code VERSION.txt}.
     */
    public static String getReleaseVersion() {
        String build = getBuildVersion();
        if (build != null && !build.isEmpty() && !build.startsWith("(")) {
            String first = build.split("\\R", 2)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        try {
            Path p = Path.of("VERSION.txt");
            if (Files.isRegularFile(p)) {
                String line = Files.readString(p, StandardCharsets.UTF_8).split("\\R", 2)[0].trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
        } catch (Exception e) {
            log.fine("Could not read VERSION.txt: " + e);
        }
        return "dev";
    }

    /**
     * Authenticode of <em>this process</em> (Windows PE only). Azure signs the
     * GitHub setup {@code jAER_windows-x64_*.exe}; install4j {@code --disable-signing}
     * leaves the installed launcher and {@code java.exe} unsigned. Linux/macOS
     * have no Authenticode; Mac notarization is on the DMG/.app.
     */
    public static String getProcessAuthenticodeSummary() {
        String os = System.getProperty("os.name", "");
        String cmd = ProcessHandle.current().info().command().orElse("(unknown executable)");
        if (!os.toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return "Authenticode applies to the Windows GitHub installer only (not this "
                    + os + " process: " + cmd + ").";
        }
        try {
            Path exe = Path.of(cmd);
            if (!Files.isRegularFile(exe)) {
                return "Windows Authenticode: could not resolve this process path (" + cmd + ").";
            }
            Process p = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$s = Get-AuthenticodeSignature -LiteralPath "
                            + "'" + exe.toString().replace("'", "''") + "'; "
                            + "Write-Output ($s.Status.ToString() + '|' + "
                            + "[string]$s.SignerCertificate.Subject)")
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "Windows Authenticode: timed out querying " + exe.getFileName();
            }
            String out = IOUtils.toString(p.getInputStream(), StandardCharsets.UTF_8).trim();
            int bar = out.indexOf('|');
            String status = bar < 0 ? out : out.substring(0, bar).trim();
            String subject = bar < 0 ? "" : out.substring(bar + 1).trim();
            if ("Valid".equalsIgnoreCase(status) && !subject.isEmpty()) {
                return "Windows Authenticode (this .exe): Valid, " + subject;
            }
            return "Windows Authenticode (this .exe): " + status
                    + ". GitHub setup installer is signed as Tobias Delbruck; "
                    + "the installed launcher/JVM is not.";
        } catch (Exception e) {
            return "Windows Authenticode: " + e.getMessage();
        }
    }
}
