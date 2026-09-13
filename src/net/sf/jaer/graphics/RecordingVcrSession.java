package net.sf.jaer.graphics;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingFilename;

/**
 * One VCR-style timed-recording session: infinite or rotating AEDAT-4 cassettes
 * in a folder named from the chip basename and start instant. {@link Mode#OFF}
 * is not a live session (single-file recording stays on {@code AEViewer}).
 */
public final class RecordingVcrSession {

    public enum Mode {
        OFF, INFINITE, ROTATE
    }

    public static final int ROTATE_MIN = 2;
    public static final int ROTATE_DEFAULT = 8;
    public static final int ROTATE_MAX = 999;
    public static final String MANIFEST_NAME = "vcr-session.txt";
    /** Appended to the chip+date folder so a VCR deck is obvious next to single files. */
    public static final String FOLDER_MARK = "-VCR";
    private static final Pattern CASSETTE_NAME = Pattern.compile(".*_c(\\d+)\\.[Aa][Ee][Dd][Aa][Tt]4$");

    private final Mode mode;
    private final int rotateKeep;
    private final long cassetteDurationMs;
    private final File sessionDir;
    private final String basename;
    private final long startEpochMs;
    private final List<File> closedCassettes = new ArrayList<>();

    private int cassetteIndex;
    private File currentCassette;

    private RecordingVcrSession(Mode mode, int rotateKeep, long cassetteDurationMs,
            File sessionDir, String basename, long startEpochMs) {
        this.mode = mode;
        this.rotateKeep = rotateKeep;
        this.cassetteDurationMs = cassetteDurationMs;
        this.sessionDir = sessionDir;
        this.basename = basename;
        this.startEpochMs = startEpochMs;
    }

    /**
     * Create the session directory (unique if the basename already exists) and
     * write an empty manifest. Does not open a cassette; call
     * {@link #openNextCassette()}.
     */
    public static RecordingVcrSession begin(File parent, String chipOrMuxBasename, Date start,
            Mode mode, int rotateKeep, long cassetteDurationMs) throws IOException {
        if (mode == null || mode == Mode.OFF) {
            throw new IllegalArgumentException("VCR session requires INFINITE or ROTATE");
        }
        if (cassetteDurationMs <= 0L) {
            throw new IllegalArgumentException("VCR cassette duration must be > 0");
        }
        int keep = clampRotateKeep(rotateKeep);
        String base = vcrSessionFolderName(chipOrMuxBasename);
        File parentDir = parent != null ? parent : new File(".");
        if (!parentDir.isDirectory() && !parentDir.mkdirs()) {
            throw new IOException("cannot create parent folder: " + parentDir);
        }
        File dir = uniqueSessionDir(parentDir, base);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("cannot create VCR session folder: " + dir);
        }
        long epoch = start != null ? start.getTime() : System.currentTimeMillis();
        String folderBase = dir.getName();
        RecordingVcrSession session = new RecordingVcrSession(mode, keep, cassetteDurationMs,
                dir, folderBase, epoch);
        session.writeManifest();
        return session;
    }

    /**
     * Bind to a session folder the setup dialog already created. Does not open a
     * cassette; after the recorder opens {@code …_c0001.aedat4}, call
     * {@link #noteOpenedCassette(File)}.
     */
    public static RecordingVcrSession attachExisting(File sessionDir, Mode mode, int rotateKeep,
            long cassetteDurationMs, Date start) throws IOException {
        if (mode == null || mode == Mode.OFF) {
            throw new IllegalArgumentException("VCR session requires INFINITE or ROTATE");
        }
        if (cassetteDurationMs <= 0L) {
            throw new IllegalArgumentException("VCR cassette duration must be > 0");
        }
        if (sessionDir == null) {
            throw new IOException("VCR session folder is null");
        }
        if (!sessionDir.isDirectory() && !sessionDir.mkdirs()) {
            throw new IOException("cannot create VCR session folder: " + sessionDir);
        }
        int keep = clampRotateKeep(rotateKeep);
        long epoch = start != null ? start.getTime() : System.currentTimeMillis();
        RecordingVcrSession session = new RecordingVcrSession(mode, keep, cassetteDurationMs,
                sessionDir, sessionFolderName(sessionDir.getName()), epoch);
        session.writeManifest();
        return session;
    }

    /**
     * Recorder already opened this cassette file (typically {@code _c0001}).
     * Sets the 1-based index from the filename when it matches.
     */
    public synchronized void noteOpenedCassette(File file) throws IOException {
        if (file == null) {
            throw new IOException("opened cassette is null");
        }
        int idx = cassetteIndexFromFile(file);
        cassetteIndex = idx > 0 ? idx : Math.max(1, cassetteIndex);
        currentCassette = file;
        writeManifestLocked();
    }

    public static int clampRotateKeep(int n) {
        if (n < ROTATE_MIN) {
            return ROTATE_MIN;
        }
        if (n > ROTATE_MAX) {
            return ROTATE_MAX;
        }
        return n;
    }

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.OFF;
        }
        String t = raw.trim();
        for (Mode m : Mode.values()) {
            if (m.name().equalsIgnoreCase(t)) {
                return m;
            }
        }
        throw new IllegalArgumentException("unknown VCR mode: " + raw);
    }

    /** True when VCR would run (not {@link Mode#OFF} and a positive cassette length). */
    public static boolean enabled(Mode mode, long cassetteDurationMs) {
        return mode != null && mode != Mode.OFF && cassetteDurationMs > 0L;
    }

    /**
     * Folder / file basename: chip token plus date, no extension, filesystem-safe.
     */
    public static String sessionFolderName(String chipOrMuxBasename) {
        String raw = chipOrMuxBasename == null ? "" : chipOrMuxBasename.trim();
        if (raw.isEmpty()) {
            raw = "jAER";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)) {
            raw = raw.substring(0, raw.length() - AEDataFile.DATA_FILE_EXTENSION_AEDAT4.length());
        }
        return RecordingFilename.sanitizeSegment(raw);
    }

    /** Suggested VCR session folder: {@code {chip-date}-VCR}. */
    public static String vcrSessionFolderName(String chipOrMuxBasename) {
        return ensureVcrFolderMark(sessionFolderName(chipOrMuxBasename));
    }

    public static boolean hasVcrFolderMark(String name) {
        String base = sessionFolderName(name);
        return base.toUpperCase(Locale.ROOT).endsWith(FOLDER_MARK);
    }

    public static String ensureVcrFolderMark(String name) {
        String base = sessionFolderName(name);
        if (hasVcrFolderMark(base)) {
            return base;
        }
        return base + FOLDER_MARK;
    }

    public static String stripVcrFolderMark(String name) {
        String base = sessionFolderName(name);
        if (hasVcrFolderMark(base)) {
            return base.substring(0, base.length() - FOLDER_MARK.length());
        }
        return base;
    }

    public static String cassetteIndexLabel(int index) {
        int n = Math.max(1, index);
        return String.format(Locale.ROOT, "%04d", n);
    }

    public static String cassetteFileName(String basename, int index) {
        return sessionFolderName(basename) + "_c" + cassetteIndexLabel(index)
                + AEDataFile.DATA_FILE_EXTENSION_AEDAT4;
    }

    /** First cassette path {@code parent/session/session_c0001.aedat4} (directory need not exist). */
    public static File firstCassetteFile(File parent, String sessionFolderName) {
        String base = sessionFolderName(sessionFolderName);
        File dir = new File(parent != null ? parent : new File("."), base);
        return new File(dir, cassetteFileName(base, 1));
    }

    public static File uniqueSessionDir(File parent, String basename) {
        File dir = parent == null ? new File(".") : parent;
        String base = sessionFolderName(basename);
        File first = new File(dir, base);
        if (!first.exists()) {
            return first;
        }
        for (int suffix = 1; suffix <= 32; suffix++) {
            File f = new File(dir, base + "-" + suffix);
            if (!f.exists()) {
                return f;
            }
        }
        return new File(dir, base + "-" + System.currentTimeMillis());
    }

    public Mode getMode() {
        return mode;
    }

    public int getRotateKeep() {
        return rotateKeep;
    }

    public long getCassetteDurationMs() {
        return cassetteDurationMs;
    }

    public File getSessionDir() {
        return sessionDir;
    }

    public String getBasename() {
        return basename;
    }

    public long getStartEpochMs() {
        return startEpochMs;
    }

    /** 0 before the first cassette; 1-based while a cassette is open or after close. */
    public synchronized int getCassetteIndex() {
        return cassetteIndex;
    }

    public synchronized File getCurrentCassette() {
        return currentCassette;
    }

    public synchronized List<File> getClosedCassettes() {
        return Collections.unmodifiableList(new ArrayList<>(closedCassettes));
    }

    /**
     * Close the current cassette in the model (caller closes the writer first).
     * Does not delete files.
     */
    public synchronized File closeCurrentCassette() throws IOException {
        File closed = currentCassette;
        if (closed != null) {
            closedCassettes.add(closed);
            currentCassette = null;
            writeManifestLocked();
        }
        return closed;
    }

    /**
     * Delete oldest closed files so that after the next cassette is opened at
     * most {@link #rotateKeep} files remain (including the new one). No-op for
     * {@link Mode#INFINITE}. Failed deletes are skipped (folder may exceed N).
     *
     * @return files that were removed from disk
     */
    public synchronized List<File> deleteOldestClosedIfNeeded() {
        List<File> deleted = new ArrayList<>();
        if (mode != Mode.ROTATE) {
            return deleted;
        }
        int opening = currentCassette == null ? 1 : 0;
        int maxClosed = Math.max(0, rotateKeep - opening);
        while (closedCassettes.size() > maxClosed) {
            File oldest = closedCassettes.remove(0);
            try {
                if (Files.deleteIfExists(oldest.toPath())) {
                    deleted.add(oldest);
                }
            } catch (IOException e) {
                // leave the file on disk; do not re-queue (Dropbox lock, etc.)
            }
        }
        return deleted;
    }

    /**
     * Rotate-delete if needed, then allocate the next cassette path (1-based).
     * Does not create the file; the recorder opens the writer.
     */
    public synchronized File openNextCassette() throws IOException {
        if (currentCassette != null) {
            throw new IllegalStateException("close current cassette before opening the next");
        }
        deleteOldestClosedIfNeeded();
        cassetteIndex++;
        currentCassette = new File(sessionDir, cassetteFileName(basename, cassetteIndex));
        writeManifestLocked();
        return currentCassette;
    }

    /**
     * {@code VCR c0003/8} (rotate keep-N) or {@code VCR c0012 ∞}. {@code null} if
     * {@link Mode#OFF}.
     */
    public static String overlayLine(Mode mode, int rotateKeep, int cassetteIndex) {
        if (mode == null || mode == Mode.OFF) {
            return null;
        }
        String idx = "c" + cassetteIndexLabel(Math.max(1, cassetteIndex));
        if (mode == Mode.INFINITE) {
            return "VCR " + idx + " ∞";
        }
        return "VCR " + idx + "/" + clampRotateKeep(rotateKeep);
    }

    /**
     * 1-based cassette index from {@code …_c0003.aedat4}, or {@code 0} if the
     * name is not a VCR cassette.
     */
    public static int cassetteIndexFromFile(File file) {
        if (file == null) {
            return 0;
        }
        Matcher m = CASSETTE_NAME.matcher(file.getName());
        if (!m.matches()) {
            return 0;
        }
        return Integer.parseInt(m.group(1));
    }

    /** Cassette files in {@code sessionDir}, ordered by index. Empty if none. */
    public static List<File> listCassetteFiles(File sessionDir) {
        List<File> out = new ArrayList<>();
        if (sessionDir == null || !sessionDir.isDirectory()) {
            return out;
        }
        File[] kids = sessionDir.listFiles();
        if (kids == null) {
            return out;
        }
        for (File f : kids) {
            if (f != null && f.isFile() && cassetteIndexFromFile(f) > 0) {
                out.add(f);
            }
        }
        out.sort(Comparator.comparingInt(RecordingVcrSession::cassetteIndexFromFile));
        return out;
    }

    /** True when {@code file} is the session manifest {@code vcr-session.txt}. */
    public static boolean isManifest(File file) {
        return file != null && file.isFile() && MANIFEST_NAME.equalsIgnoreCase(file.getName());
    }

    /**
     * True if this is a VCR deck: a folder with {@code vcr-session.txt} or at
     * least two {@code *_cNNNN.aedat4} files; a cassette whose parent is a deck;
     * or the session manifest itself.
     */
    public static boolean isDeck(File file) {
        return deckFolder(file) != null;
    }

    /**
     * Session folder for a deck path, or {@code null} if {@code file} is not a
     * deck / cassette / manifest.
     */
    public static File deckFolder(File file) {
        if (file == null) {
            return null;
        }
        if (file.isDirectory()) {
            return directoryIsDeck(file) ? file : null;
        }
        if (isManifest(file)) {
            File parent = file.getParentFile();
            return parent != null && parent.isDirectory() ? parent : null;
        }
        if (cassetteIndexFromFile(file) > 0) {
            File parent = file.getParentFile();
            return parent != null && directoryIsDeck(parent) ? parent : null;
        }
        return null;
    }

    /**
     * {@link #deckFolder(File)} using the open-dialog current directory when the
     * selection is empty.
     */
    public static File deckFolder(File currentDirectory, File selected) {
        File fromSelected = deckFolder(selected);
        if (fromSelected != null) {
            return fromSelected;
        }
        return deckFolder(currentDirectory);
    }

    private static boolean directoryIsDeck(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        if (new File(dir, MANIFEST_NAME).isFile()) {
            return true;
        }
        return listCassetteFiles(dir).size() >= 2;
    }

    /**
     * Cassettes to concatenate: manifest {@code file} / {@code current} lines
     * that still exist, else {@link #listCassetteFiles(File)}.
     */
    public static List<File> cassetteFilesForConcat(File sessionDir) {
        if (sessionDir == null || !sessionDir.isDirectory()) {
            return List.of();
        }
        File man = new File(sessionDir, MANIFEST_NAME);
        if (man.isFile()) {
            try {
                RecordingVcrSession s = readManifest(sessionDir);
                List<File> fromMan = new ArrayList<>();
                for (File f : s.getClosedCassettes()) {
                    if (f != null && f.isFile()) {
                        fromMan.add(f);
                    }
                }
                File current = s.getCurrentCassette();
                if (current != null && current.isFile() && !fromMan.contains(current)) {
                    fromMan.add(current);
                }
                fromMan.sort(Comparator.comparingInt(RecordingVcrSession::cassetteIndexFromFile));
                if (!fromMan.isEmpty()) {
                    return fromMan;
                }
            } catch (IOException ignore) {
            }
        }
        return listCassetteFiles(sessionDir);
    }

    /** Default concat path: {@code {parent}/{deckName}-concat.aedat4}. */
    public static File defaultConcatOutput(File sessionDir) {
        if (sessionDir == null) {
            return new File("vcr-concat" + AEDataFile.DATA_FILE_EXTENSION_AEDAT4);
        }
        File parent = sessionDir.getParentFile() != null ? sessionDir.getParentFile() : sessionDir;
        return new File(parent, sessionDir.getName() + "-concat" + AEDataFile.DATA_FILE_EXTENSION_AEDAT4);
    }

    /** File-dialog overlay for a VCR folder (not a playable recording). */
    public static String previewOverlay(File sessionDir) {
        if (sessionDir == null) {
            return "VCR deck";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("VCR deck\n").append(sessionDir.getName());
        try {
            if (new File(sessionDir, MANIFEST_NAME).isFile()) {
                RecordingVcrSession s = readManifest(sessionDir);
                sb.append('\n').append(s.getMode() == Mode.ROTATE
                        ? "rotate " + s.getRotateKeep()
                        : s.getMode().name().toLowerCase());
            }
        } catch (IOException ignore) {
        }
        List<File> cassettes = cassetteFilesForConcat(sessionDir);
        sb.append('\n').append(cassettes.size()).append(" cassette")
                .append(cassettes.size() == 1 ? "" : "s");
        long bytes = 0L;
        for (File f : cassettes) {
            bytes += f.length();
        }
        if (bytes > 0L) {
            sb.append('\n').append(net.sf.jaer.util.RecordingDiskSpace.formatBytes(bytes));
        }
        sb.append("\nnot a recording — Merge VCR deck");
        return sb.toString();
    }

    /** {@code VCR c0003/8} or {@code VCR c0012 ∞}. */
    public synchronized String overlayCassetteLabel() {
        return overlayLine(mode, rotateKeep, cassetteIndex);
    }

    public synchronized File manifestFile() {
        return new File(sessionDir, MANIFEST_NAME);
    }

    public synchronized void writeManifest() throws IOException {
        writeManifestLocked();
    }

    private void writeManifestLocked() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# jAER VCR session\n");
        sb.append("mode ").append(mode.name()).append('\n');
        sb.append("rotateKeep ").append(rotateKeep).append('\n');
        sb.append("cassetteDurationMs ").append(cassetteDurationMs).append('\n');
        sb.append("startEpochMs ").append(startEpochMs).append('\n');
        sb.append("basename ").append(basename).append('\n');
        sb.append("cassetteIndex ").append(cassetteIndex).append('\n');
        File current = currentCassette;
        if (current != null) {
            sb.append("current ").append(current.getName()).append('\n');
        }
        for (File f : closedCassettes) {
            sb.append("file ").append(f.getName()).append('\n');
        }
        Files.writeString(manifestFile().toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Parse {@link #MANIFEST_NAME} from a session folder (concat / tests).
     * Closed files are listed; {@code current} is not added to closed.
     */
    public static RecordingVcrSession readManifest(File sessionDir) throws IOException {
        if (sessionDir == null || !sessionDir.isDirectory()) {
            throw new IOException("not a VCR session folder: " + sessionDir);
        }
        File man = new File(sessionDir, MANIFEST_NAME);
        if (!man.isFile()) {
            throw new IOException("missing " + MANIFEST_NAME + " in " + sessionDir);
        }
        Mode mode = Mode.INFINITE;
        int keep = ROTATE_DEFAULT;
        long duration = 0L;
        long start = 0L;
        String base = sessionDir.getName();
        int index = 0;
        File current = null;
        List<File> closed = new ArrayList<>();
        for (String line : Files.readAllLines(man.toPath(), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] parts = t.split(" ", 2);
            String key = parts[0];
            String val = parts.length > 1 ? parts[1].trim() : "";
            switch (key) {
                case "mode" ->
                    mode = parseMode(val);
                case "rotateKeep" ->
                    keep = clampRotateKeep(Integer.parseInt(val));
                case "cassetteDurationMs" ->
                    duration = Long.parseLong(val);
                case "startEpochMs" ->
                    start = Long.parseLong(val);
                case "basename" ->
                    base = val;
                case "cassetteIndex" ->
                    index = Integer.parseInt(val);
                case "current" ->
                    current = new File(sessionDir, val);
                case "file" ->
                    closed.add(new File(sessionDir, val));
                default -> {
                }
            }
        }
        if (mode == Mode.OFF) {
            throw new IOException("manifest mode is OFF");
        }
        RecordingVcrSession session = new RecordingVcrSession(mode, keep, duration, sessionDir, base, start);
        session.cassetteIndex = index;
        session.currentCassette = current;
        session.closedCassettes.addAll(closed);
        return session;
    }

    @Override
    public String toString() {
        return "RecordingVcrSession{" + mode + " keep=" + rotateKeep + " dir=" + sessionDir + '}';
    }
}
