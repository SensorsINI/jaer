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
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;

/**
 * Resolves which {@link AEChip} a recording likely needs, preferring the
 * jAER filename convention ({@code ChipSimpleName-...}), then AEDAT-4
 * {@code infoNode} source / size, then AEDAT-2 ASCII header hints.
 * <p>
 * Only matches against the viewer's loaded (selected) chip class names.
 */
public final class RecordingChipDetector {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Pattern ATTR_SOURCE = Pattern.compile(
            "key\\s*=\\s*\"source\"\\s+value\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_SIZE_X = Pattern.compile(
            "key\\s*=\\s*\"sizeX\"\\s+value\\s*=\\s*\"(\\d+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_SIZE_Y = Pattern.compile(
            "key\\s*=\\s*\"sizeY\"\\s+value\\s*=\\s*\"(\\d+)\"", Pattern.CASE_INSENSITIVE);

    private RecordingChipDetector() {
    }

    /** Hint extracted from a recording (may be partial). */
    public static final class Hint {
        public final String name;
        public final Integer sizeX;
        public final Integer sizeY;
        public final String origin;

        public Hint(String name, Integer sizeX, Integer sizeY, String origin) {
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return String.format("Hint{name=%s size=%sx%s origin=%s}",
                    name, sizeX, sizeY, origin);
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
        if (fromHeader == null) {
            log.fine("No chip hint from filename or header for " + file.getName());
            return null;
        }
        Class<? extends AEChip> byHeader = resolve(fromHeader, loaded);
        if (byHeader != null) {
            log.info("Recording chip from header: " + byHeader.getSimpleName() + " (" + fromHeader + ")");
            return byHeader;
        }
        log.info("Could not match recording chip hint among loaded AEChips: " + fromHeader);
        return null;
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

    /** Peek AEDAT-4 infoNode or AEDAT-2 ASCII header without full open. */
    public static Hint fromHeader(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)
                || name.endsWith(".aedat4")) {
            return fromAedat4InfoNode(file);
        }
        if (AEDataFile.hasDataFileExtension(file.getName())) {
            return fromAedat2AsciiHeader(file);
        }
        return null;
    }

    static Hint fromAedat4InfoNode(File file) {
        try (FileInputStream in = new FileInputStream(file); FileChannel channel = in.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(channel, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                return null;
            }
            ByteBuffer headerBytes = readSizePrefixed(channel);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            String info = header.infoNode();
            if (info == null || info.isEmpty()) {
                return null;
            }
            String source = firstGroup(ATTR_SOURCE, info);
            Integer sx = parseInt(firstGroup(ATTR_SIZE_X, info));
            Integer sy = parseInt(firstGroup(ATTR_SIZE_Y, info));
            if ((source == null || source.isEmpty()) && sx == null && sy == null) {
                return null;
            }
            return new Hint(source, sx, sy, "aedat4-infoNode");
        } catch (Exception e) {
            log.log(Level.FINE, "Could not peek AEDAT-4 infoNode from " + file.getName() + ": " + e, e);
            return null;
        }
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
     * Resolve hint against loaded chips by name (exact, then unique soft match).
     * Size alone is not used — many chips share resolution (e.g. Davis346*).
     */
    public static Class<? extends AEChip> resolve(Hint hint, List<Class<? extends AEChip>> loaded) {
        if (hint == null || loaded == null || loaded.isEmpty()) {
            return null;
        }
        if (hint.name == null || hint.name.isEmpty()) {
            return null;
        }
        return matchByName(hint.name, loaded);
    }

    private static Class<? extends AEChip> matchByName(String hintName, List<Class<? extends AEChip>> loaded) {
        String normHint = normalize(hintName);
        if (normHint.isEmpty()) {
            return null;
        }
        Class<? extends AEChip> exact = null;
        Class<? extends AEChip> soft = null;
        int softCount = 0;
        for (Class<? extends AEChip> c : loaded) {
            String simple = c.getSimpleName();
            String normSimple = normalize(simple);
            if (simple.equalsIgnoreCase(hintName) || normSimple.equals(normHint)) {
                exact = c;
                break;
            }
            if (normSimple.contains(normHint) || normHint.contains(normSimple)) {
                soft = c;
                softCount++;
            }
        }
        if (exact != null) {
            return exact;
        }
        if (softCount == 1) {
            return soft;
        }
        return null;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @SuppressWarnings("unchecked")
    private static List<Class<? extends AEChip>> loadClasses(List<String> fqcn) {
        List<Class<? extends AEChip>> out = new ArrayList<>();
        for (String name : fqcn) {
            try {
                Class<?> c = Class.forName(name);
                if (AEChip.class.isAssignableFrom(c)) {
                    out.add((Class<? extends AEChip>) c);
                }
            } catch (ClassNotFoundException e) {
                log.fine("Loaded chip list entry not found: " + name);
            }
        }
        return out;
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
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
