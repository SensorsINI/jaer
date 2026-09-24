package net.sf.jaer.eventprocessing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.jaer.eventio.AEDataFile;

/**
 * Path, exclusive-open, and Save-As move shared by CSV sidecars.
 * Row format stays in the sidecar class.
 */
public final class SidecarFiles {

    private SidecarFiles() {
    }

    public static File fileBeside(File recording, String extension) {
        if (recording == null) {
            return null;
        }
        String base = stripKnownExtension(recording.getName());
        File parent = recording.getParentFile();
        return new File(parent == null ? new File(".") : parent, base + extension);
    }

    public static String stripKnownExtension(String name) {
        String[] ext = {
            AEDataFile.DATA_FILE_EXTENSION_AEDAT4,
            AEDataFile.DATA_FILE_EXTENSION_AEDAT2,
            AEDataFile.DATA_FILE_EXTENSION_AEDZ,
            AEDataFile.DATA_FILE_EXTENSION,
            AEDataFile.OLD_DATA_FILE_EXTENSION
        };
        for (String e : ext) {
            if (name.toLowerCase(Locale.ROOT).endsWith(e)) {
                return name.substring(0, name.length() - e.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * @return writer, or {@code null} if another owner already holds this path
     */
    public static BufferedWriter tryOpen(ConcurrentHashMap<String, Boolean> owners, File sidecar,
            String... headerLines) throws IOException {
        if (sidecar == null) {
            return null;
        }
        String key = sidecar.getAbsolutePath();
        if (owners.putIfAbsent(key, Boolean.TRUE) != null) {
            return null;
        }
        try {
            BufferedWriter w = Files.newBufferedWriter(sidecar.toPath(), StandardCharsets.UTF_8);
            if (headerLines != null) {
                for (String line : headerLines) {
                    w.write(line);
                    w.newLine();
                }
            }
            w.flush();
            return w;
        } catch (IOException e) {
            owners.remove(key);
            throw e;
        }
    }

    public static void close(ConcurrentHashMap<String, Boolean> owners, File sidecar, BufferedWriter w)
            throws IOException {
        if (w != null) {
            w.close();
        }
        if (sidecar != null) {
            owners.remove(sidecar.getAbsolutePath());
        }
    }

    /**
     * Move {@code sidecar} onto {@code dest}. No-op when either path is missing
     * or they already match.
     *
     * @return {@code dest} after a move, otherwise {@code sidecar}
     */
    public static File moveTo(File sidecar, File dest) throws IOException {
        if (sidecar == null || !sidecar.isFile() || dest == null) {
            return sidecar;
        }
        if (sidecar.getAbsoluteFile().equals(dest.getAbsoluteFile())) {
            return sidecar;
        }
        File parent = dest.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.move(sidecar.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }
}
