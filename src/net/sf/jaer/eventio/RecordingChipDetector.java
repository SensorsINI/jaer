/*
 * RecordingChipDetector.java
 *
 * Detect AEChip for a recording from filename, then AEDAT-4 / AEDAT-2 header.
 */
package net.sf.jaer.eventio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.JaerAllowedSubclasses;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import ch.unizh.ini.jaer.chip.retina.DVS128;
import ch.unizh.ini.jaer.chip.retina.DVS1280x720SD;
import ch.unizh.ini.jaer.chip.retina.DVS640;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import prophesee.chip.PropheseeIMX636HD;
import prophesee.eventio.MetavisionDatFileInputStream;
import prophesee.eventio.MetavisionRawFileInputStream;

/**
 * Resolves which {@link AEChip} a recording likely needs, preferring the
 * jAER filename convention ({@code ChipSimpleName-...}), then AEDAT-4
 * {@code infoNode} source / size / colorFilter, then AEDAT-2 ASCII header hints.
 * Legacy jAER {@code .dat} (pre-2010 / DVS09 dataset) falls back to {@link DVS128}.
 * Prophesee / Metavision {@code .dat} (ASCII {@code % } header) is detected separately.
 * <p>
 * Only matches against the viewer's loaded (selected) chip class names.
 * AEDAT-4 {@code infoNode} uses DV attribute form
 * {@code <attr key="k" type="t">v</attr>}.
 */
public final class RecordingChipDetector {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** DV: {@code <attr key="source" type="string">DAVIS346_…</attr>} */
    private static final Pattern ATTR_TYPED = Pattern.compile(
            "<attr\\s+key\\s*=\\s*\"([^\"]+)\"\\s+type\\s*=\\s*\"[^\"]*\"\\s*>\\s*([^<]*?)\\s*</attr>",
            Pattern.CASE_INSENSITIVE);

    private RecordingChipDetector() {
    }

    /** Hint extracted from a recording (may be partial). */
    public static final class Hint {
        public final String name;
        public final Integer sizeX;
        public final Integer sizeY;
        /** DV colorFilter (0–3 Bayer); null if absent (mono / unknown). */
        public final Integer colorFilter;
        public final String origin;

        public Hint(String name, Integer sizeX, Integer sizeY, String origin) {
            this(name, sizeX, sizeY, null, origin);
        }

        public Hint(String name, Integer sizeX, Integer sizeY, Integer colorFilter, String origin) {
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.colorFilter = colorFilter;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return String.format("Hint{name=%s size=%sx%s colorFilter=%s origin=%s}",
                    name, sizeX, sizeY, colorFilter, origin);
        }
    }

    /**
     * One AEDAT-4 {@code infoNode} stream (events/frames/IMU from a camera or module).
     * DV Recorder can mux several cameras into one file with distinct stream IDs.
     */
    public static final class StreamHint {
        public final int streamId;
        public final String typeIdentifier;
        public final String source;
        public final Integer sizeX;
        public final Integer sizeY;
        public final Integer colorFilter;
        public final String originalOutputName;
        public final String originalModuleName;

        public StreamHint(int streamId, String typeIdentifier, String source,
                Integer sizeX, Integer sizeY, Integer colorFilter, String originalOutputName) {
            this(streamId, typeIdentifier, source, sizeX, sizeY, colorFilter, originalOutputName, null);
        }

        public StreamHint(int streamId, String typeIdentifier, String source,
                Integer sizeX, Integer sizeY, Integer colorFilter,
                String originalOutputName, String originalModuleName) {
            this.streamId = streamId;
            this.typeIdentifier = typeIdentifier;
            this.source = source;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.colorFilter = colorFilter;
            this.originalOutputName = originalOutputName;
            this.originalModuleName = originalModuleName;
        }

        /**
         * True when coordinates follow DV/OpenCV convention (origin top-left),
         * as opposed to jAER live addresses (Davis already X/Y flipped).
         */
        public boolean hasDvOpenCvCoordinates() {
            if (originalModuleName != null && originalModuleName.equalsIgnoreCase("jAER")) {
                return false;
            }
            return dvCameraFamily(source) != null;
        }

        public boolean isEvents() {
            return typeIdentifier != null && typeIdentifier.equalsIgnoreCase("EVTS");
        }

        public boolean isFrames() {
            return typeIdentifier != null && typeIdentifier.equalsIgnoreCase("FRME");
        }

        public boolean isImu() {
            return typeIdentifier != null && typeIdentifier.equalsIgnoreCase("IMUS");
        }

        public Hint toChipHint() {
            return new Hint(source, sizeX, sizeY, colorFilter, "aedat4-stream-" + streamId);
        }

        /** Short label for UI selection lists. */
        public String displayLabel() {
            StringBuilder sb = new StringBuilder();
            sb.append("stream ").append(streamId);
            if (typeIdentifier != null && !typeIdentifier.isEmpty()) {
                sb.append(" [").append(typeIdentifier).append(']');
            }
            if (source != null && !source.isEmpty()) {
                sb.append(": ").append(source);
            }
            if (sizeX != null && sizeY != null) {
                sb.append(" (").append(sizeX).append('\u00d7').append(sizeY).append(')');
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return displayLabel();
        }
    }

    /**
     * Detect chip class among {@code loadedChipClassNames} (FQCN list from the
     * AEViewer device menu). Returns null if unknown or ambiguous.
     */
    public static Class<? extends AEChip> detect(File file, List<String> loadedChipClassNames) {
        if (file == null || !file.isFile() || loadedChipClassNames == null || loadedChipClassNames.isEmpty()) {
            return null;
        }
        List<Class<? extends AEChip>> loaded = loadClasses(loadedChipClassNames);
        if (loaded.isEmpty()) {
            return null;
        }

        Hint fromName = fromFilename(file.getName());
        Class<? extends AEChip> byName = resolve(fromName, loaded);
        if (byName != null) {
            log.info("Recording chip from filename: " + byName.getSimpleName() + " (" + fromName + ")");
            return byName;
        }

        Hint fromHeader = fromHeader(file);
        if (fromHeader != null) {
            Class<? extends AEChip> byHeader = resolve(fromHeader, loaded);
            if (byHeader != null) {
                log.info("Recording chip from header: " + byHeader.getSimpleName() + " (" + fromHeader + ")");
                return byHeader;
            }
            log.info("Could not match recording chip hint among loaded AEChips: " + fromHeader);
        }

        // Pre-2010 jAER / DVS09 downloads use .dat and almost always came from DVS128.
        // Skip if this .dat is a Prophesee / Metavision DAT (already tried in fromHeader).
        Hint fromLegacyDat = fromLegacyDatExtension(file);
        if (fromLegacyDat != null) {
            Class<? extends AEChip> byDat = resolve(fromLegacyDat, loaded);
            if (byDat != null) {
                log.info("Recording chip from legacy .dat extension: " + byDat.getSimpleName()
                        + " (" + fromLegacyDat + ")");
                return byDat;
            }
        }

        if (fromHeader == null) {
            log.fine("No chip hint from filename, header, or extension for " + file.getName());
        }
        return null;
    }

    /**
     * Legacy {@link AEDataFile#OLD_DATA_FILE_EXTENSION} ({@code .dat}) recordings
     * (DVS09 dataset and early jAER) are assumed to be {@link DVS128} when no
     * stronger filename/header hint is available. Prophesee / Metavision DAT
     * files that share the extension are excluded.
     */
    static Hint fromLegacyDatExtension(File file) {
        if (file == null) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(AEDataFile.OLD_DATA_FILE_EXTENSION)) {
            return null;
        }
        if (MetavisionDatFileInputStream.isMetavisionDatFile(file)) {
            return null;
        }
        return new Hint(DVS128.class.getSimpleName(), 128, 128, "legacy-.dat");
    }

    /**
     * Leading token before first {@code '-'} in the filename (jAER convention).
     */
    public static Hint fromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        String base = filename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dash = base.indexOf('-');
        if (dash <= 0) {
            return null;
        }
        String token = base.substring(0, dash).trim();
        if (token.isEmpty() || token.equalsIgnoreCase("events") || token.equalsIgnoreCase("recording")) {
            return null;
        }
        return new Hint(token, null, null, "filename");
    }

    /** Peek AEDAT-4 infoNode, Metavision RAW/DAT, or AEDAT-2 ASCII header without full open. */
    public static Hint fromHeader(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)
                || name.endsWith(".aedat4")) {
            return fromAedat4InfoNode(file);
        }
        if (name.endsWith(".raw")) {
            return fromMetavisionRawHeader(file);
        }
        if (name.endsWith(".dat")) {
            Hint dat = fromMetavisionDatHeader(file);
            if (dat != null) {
                return dat;
            }
        }
        if (name.endsWith(".h5") || name.endsWith(".hdf5")) {
            return fromDsecHdf5(file);
        }
        if (AEDataFile.hasDataFileExtension(file.getName())) {
            return fromAedat2AsciiHeader(file);
        }
        return null;
    }

    /**
     * DSEC-layout cooked HDF5 → {@link DVS640} (640×480) or
     * {@link DVS1280x720SD} (1280×720), from peeked sensor size (attrs or max x/y).
     */
    static Hint fromDsecHdf5(File file) {
        if (!DsecHdf5AEInputStream.isDsecEventsFile(file)) {
            return null;
        }
        DsecHdf5AEInputStream.SensorSize size = DsecHdf5AEInputStream.peekSensorSize(file);
        int w = size != null ? size.width : DsecHdf5AEInputStream.DEFAULT_WIDTH;
        int h = size != null ? size.height : DsecHdf5AEInputStream.DEFAULT_HEIGHT;
        String chipName = preferredDsecChipName(w, h);
        String origin = size != null ? "dsec-hdf5/" + size.origin : "dsec-hdf5/default";
        return new Hint(chipName, w, h, origin);
    }

    /**
     * Map DSEC-layout geometry to a generic playback chip. Exact VGA / HD sizes
     * first; otherwise pick the smallest known chip that fits the sampled max x/y.
     */
    static String preferredDsecChipName(int width, int height) {
        if (width == DVS1280x720SD_WIDTH && height == DVS1280x720SD_HEIGHT) {
            return DVS1280x720SD.class.getSimpleName();
        }
        if (width == DVS640_WIDTH && height == DVS640_HEIGHT) {
            return DVS640.class.getSimpleName();
        }
        // Sparse samples may not hit the last pixel — fit into known chips.
        if (width <= DVS640_WIDTH && height <= DVS640_HEIGHT) {
            return DVS640.class.getSimpleName();
        }
        if (width <= DVS1280x720SD_WIDTH && height <= DVS1280x720SD_HEIGHT) {
            return DVS1280x720SD.class.getSimpleName();
        }
        // Larger than HD: still recommend HD viewer (events may clip) — log via Hint size.
        log.info(String.format(
                "DSEC HDF5 geometry %dx%d exceeds known playback chips; recommending %s",
                width, height, DVS1280x720SD.class.getSimpleName()));
        return DVS1280x720SD.class.getSimpleName();
    }

    private static final int DVS640_WIDTH = 640;
    private static final int DVS640_HEIGHT = 480;
    private static final int DVS1280x720SD_WIDTH = 1280;
    private static final int DVS1280x720SD_HEIGHT = 720;

    /**
     * Prophesee / Metavision decoded {@code .dat} (ASCII {@code % } header).
     * HD (1280×720) → {@link PropheseeIMX636HD}; otherwise same size mapping as DSEC.
     */
    static Hint fromMetavisionDatHeader(File file) {
        MetavisionDatFileInputStream.HeaderInfo hi = MetavisionDatFileInputStream.peekHeader(file);
        if (hi == null || hi.headerLineCount == 0) {
            return null;
        }
        int w = hi.width;
        int h = hi.height;
        String chipName;
        if (w == DVS1280x720SD_WIDTH && h == DVS1280x720SD_HEIGHT) {
            chipName = PropheseeIMX636HD.class.getSimpleName();
        } else if (w > 0 && h > 0) {
            chipName = preferredDsecChipName(w, h);
        } else {
            chipName = PropheseeIMX636HD.class.getSimpleName();
        }
        return new Hint(chipName, w > 0 ? w : null, h > 0 ? h : null, "metavision-dat");
    }

    /**
     * Metavision / Prophesee {@code .raw} EVT3 (EVK4 IMX636 or Gen4.1 HD) →
     * {@link PropheseeIMX636HD}.
     */
    static Hint fromMetavisionRawHeader(File file) {
        MetavisionRawFileInputStream.HeaderInfo hi = MetavisionRawFileInputStream.peekHeader(file);
        if (hi == null || !hi.evt3) {
            return null;
        }
        return new Hint(PropheseeIMX636HD.class.getSimpleName(), hi.width, hi.height, "metavision-raw");
    }

    static Hint fromAedat4InfoNode(File file) {
        List<StreamHint> streams = listAedat4Streams(file);
        if (streams.isEmpty()) {
            return null;
        }
        for (StreamHint s : streams) {
            if (s.isEvents()) {
                return s.toChipHint();
            }
        }
        return streams.get(0).toChipHint();
    }

    /** All streams declared in the AEDAT-4 {@code infoNode}, or empty if unavailable. */
    public static List<StreamHint> listAedat4Streams(File file) {
        String info = peekAedat4InfoNodeXml(file);
        if (info == null || info.isEmpty()) {
            return new ArrayList<>();
        }
        return streamsFromInfoNodeXml(info);
    }

    /** Polarity (EVTS) streams only — candidates for playback chip / stream selection. */
    public static List<StreamHint> listAedat4EventStreams(File file) {
        List<StreamHint> all = listAedat4Streams(file);
        List<StreamHint> events = new ArrayList<>();
        for (StreamHint s : all) {
            if (s.isEvents()) {
                events.add(s);
            }
        }
        return events;
    }

    static String peekAedat4InfoNodeXml(File file) {
        try (FileInputStream in = new FileInputStream(file); FileChannel channel = in.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(channel, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                return null;
            }
            ByteBuffer headerBytes = readSizePrefixed(channel);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            return header.infoNode();
        } catch (Exception e) {
            log.log(Level.FINE, "Could not peek AEDAT-4 infoNode from " + file.getName() + ": " + e, e);
            return null;
        }
    }

    /**
     * Parse DV-format infoNode XML into a hint (first EVTS stream, else first stream).
     * Prefer {@link #listAedat4EventStreams(File)} when multiple cameras may be present.
     */
    public static Hint hintFromInfoNodeXml(String info) {
        List<StreamHint> streams = streamsFromInfoNodeXml(info);
        if (streams.isEmpty()) {
            String source = attr(info, "source");
            Integer sx = parseInt(attr(info, "sizeX"));
            Integer sy = parseInt(attr(info, "sizeY"));
            Integer colorFilter = parseInt(attr(info, "colorFilter"));
            if ((source == null || source.isEmpty()) && sx == null && sy == null) {
                return null;
            }
            return new Hint(source, sx, sy, colorFilter, "aedat4-infoNode");
        }
        for (StreamHint s : streams) {
            if (s.isEvents()) {
                return s.toChipHint();
            }
        }
        return streams.get(0).toChipHint();
    }

    /** Parse each numbered stream node under {@code outInfo}. */
    public static List<StreamHint> streamsFromInfoNodeXml(String info) {
        List<StreamHint> out = new ArrayList<>();
        if (info == null || info.isEmpty()) {
            return out;
        }
        Pattern streamStart = Pattern.compile(
                "<node\\s+name\\s*=\\s*\"(\\d+)\"[^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher m = streamStart.matcher(info);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            ids.add(Integer.parseInt(m.group(1)));
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : info.length();
            String body = info.substring(from, to);
            out.add(new StreamHint(
                    ids.get(i),
                    attr(body, "typeIdentifier"),
                    attr(body, "source"),
                    parseInt(attr(body, "sizeX")),
                    parseInt(attr(body, "sizeY")),
                    parseInt(attr(body, "colorFilter")),
                    attr(body, "originalOutputName"),
                    attr(body, "originalModuleName")));
        }
        return out;
    }

    /**
     * Scan AEDAT-2 '#' header lines for a loaded chip simple name mentioned in text.
     * jAER writers do not always embed the chip class; this is best-effort.
     */
    static Hint fromAedat2AsciiHeader(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            StringBuilder headerText = new StringBuilder(2048);
            for (int i = 0; i < 64; i++) {
                reader.mark(512);
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (!line.startsWith("#") && !line.isEmpty() && line.charAt(0) != '!') {
                    // likely start of binary; stop
                    break;
                }
                headerText.append(line).append('\n');
                if (line.contains(AEDataFile.END_OF_HEADER_STRING)) {
                    break;
                }
            }
            String text = headerText.toString();
            if (text.isEmpty()) {
                return null;
            }
            // Prefer explicit "AEChip: Foo" if present
            Matcher m = Pattern.compile("AEChip\\s*[:=]\\s*([\\w.]+)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                String token = m.group(1);
                int dot = token.lastIndexOf('.');
                return new Hint(dot >= 0 ? token.substring(dot + 1) : token, null, null, "aedat2-header");
            }
            return null;
        } catch (Exception e) {
            log.log(Level.FINE, "Could not peek AEDAT-2 header from " + file.getName() + ": " + e, e);
            return null;
        }
    }

    /**
     * Resolve hint against loaded chips by name (exact, family+color, then unique soft match).
     * Size alone is not used — many chips share resolution (e.g. Davis346*).
     */
    public static Class<? extends AEChip> resolve(Hint hint, List<Class<? extends AEChip>> loaded) {
        if (hint == null || loaded == null || loaded.isEmpty()) {
            return null;
        }
        if (hint.name == null || hint.name.isEmpty()) {
            return null;
        }
        return matchByName(hint, loaded);
    }

    /**
     * Historical AEChip simple names that should resolve to a current class.
     * {@code Davis346mini} was the early-development name for the blue-case
     * prototype (now {@code Davis346blue}); those recordings share APS readout
     * and must not fall through to {@code Davis346red}.
     */
    static String aliasHistoricalChipSimpleName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String token = name;
        int dot = token.lastIndexOf('.');
        if (dot >= 0) {
            token = token.substring(dot + 1);
        }
        if ("davis346mini".equals(normalize(token))) {
            return "Davis346blue";
        }
        return name;
    }

    private static Class<? extends AEChip> matchByName(Hint hint, List<Class<? extends AEChip>> loaded) {
        String hintName = aliasHistoricalChipSimpleName(hint.name);
        String normHint = normalize(hintName);
        if (normHint.isEmpty()) {
            return null;
        }
        // DV camera names: MODEL_SERIAL → use MODEL for matching
        String family = dvCameraFamily(hintName);
        String normFamily = family != null ? normalize(family) : "";

        Class<? extends AEChip> exact = null;
        List<Class<? extends AEChip>> soft = new ArrayList<>();
        for (Class<? extends AEChip> c : loaded) {
            String simple = c.getSimpleName();
            String normSimple = normalize(simple);
            if (simple.equalsIgnoreCase(hintName) || normSimple.equals(normHint)) {
                exact = c;
                break;
            }
            if (normSimple.contains(normHint) || normHint.contains(normSimple)) {
                soft.add(c);
                continue;
            }
            if (!normFamily.isEmpty()
                    && (normSimple.contains(normFamily) || normFamily.contains(normSimple))) {
                soft.add(c);
            }
        }
        if (exact != null) {
            return exact;
        }
        if (soft.isEmpty()) {
            return null;
        }
        soft = preferColorMatch(soft, hint.colorFilter);
        if (soft.size() == 1) {
            return soft.get(0);
        }
        // DV source DAVIS346_* matches several Davis346* chips; prefer common iniVation red.
        Class<? extends AEChip> preferred = preferCommonDvChip(soft, normFamily);
        if (preferred != null) {
            return preferred;
        }
        // Prefer any unique "*red*" chip when still ambiguous among same family/color.
        Class<? extends AEChip> red = null;
        for (Class<? extends AEChip> c : soft) {
            if (c.getSimpleName().toLowerCase(Locale.ROOT).contains("red")) {
                if (red != null) {
                    return null; // still ambiguous
                }
                red = c;
            }
        }
        return red;
    }

    /**
     * Preferred jAER chip for a DV camera family when several soft-match.
     * {@code davis346} → {@code Davis346red} (most common iniVation camera).
     */
    private static Class<? extends AEChip> preferCommonDvChip(
            List<Class<? extends AEChip>> candidates, String normFamily) {
        if (candidates.isEmpty() || normFamily == null || normFamily.isEmpty()) {
            return null;
        }
        String want = null;
        if ("davis346".equals(normFamily)) {
            want = "davis346red";
        }
        if (want == null) {
            return null;
        }
        for (Class<? extends AEChip> c : candidates) {
            if (normalize(c.getSimpleName()).equals(want)) {
                return c;
            }
        }
        return null;
    }

    /**
     * DV {@code colorFilter} present (Bayer index) → prefer *Color* chip classes;
     * absent → prefer non-color. If filtering would empty the list, keep original.
     */
    private static List<Class<? extends AEChip>> preferColorMatch(
            List<Class<? extends AEChip>> candidates, Integer colorFilter) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        boolean wantColor = colorFilter != null;
        List<Class<? extends AEChip>> filtered = new ArrayList<>();
        for (Class<? extends AEChip> c : candidates) {
            boolean isColor = isColorChipName(c.getSimpleName());
            if (wantColor == isColor) {
                filtered.add(c);
            }
        }
        return filtered.isEmpty() ? candidates : filtered;
    }

    private static boolean isColorChipName(String simpleName) {
        String n = simpleName.toLowerCase(Locale.ROOT);
        return n.contains("color") || n.contains("rgb");
    }

    /**
     * {@code DAVIS346_00000843} → {@code DAVIS346}; non-DV names return null.
     */
    static String dvCameraFamily(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        int us = source.indexOf('_');
        if (us <= 0) {
            return null;
        }
        String family = source.substring(0, us);
        // Heuristic: DV models are uppercase alnum like DAVIS346, DVXplorerLite
        if (!family.equals(family.toUpperCase(Locale.ROOT)) && !family.matches("(?i)DVX.*|DAVIS.*|DVS.*")) {
            return null;
        }
        return family;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String attr(String xml, String key) {
        if (xml == null || key == null) {
            return null;
        }
        Matcher typed = ATTR_TYPED.matcher(xml);
        while (typed.find()) {
            if (key.equalsIgnoreCase(typed.group(1))) {
                return typed.group(2).trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Class<? extends AEChip>> loadClasses(List<String> fqcn) {
        List<Class<? extends AEChip>> out = new ArrayList<>();
        for (String name : fqcn) {
            try {
                Class<?> c = JaerAllowedSubclasses.load(name, AEChip.class);
                if (AEChip.class.isAssignableFrom(c)) {
                    out.add((Class<? extends AEChip>) c);
                }
            } catch (ClassNotFoundException e) {
                log.fine("Loaded chip list entry not found: " + name);
            }
        }
        return out;
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0 || size > 16 * 1024 * 1024) {
            throw new IOException("Implausible AEDAT-4 header size " + size);
        }
        ByteBuffer payload = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(size);
        readFully(channel, payload);
        payload.flip();
        return payload;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("EOF while reading AEDAT-4 header");
            }
        }
    }
}
