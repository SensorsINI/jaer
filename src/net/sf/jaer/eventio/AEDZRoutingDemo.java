package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.eventio.export.SaveAsExporter;
import net.sf.jaer.eventio.export.SaveAsOptions;

/**
 * Headless production-path test for wiring the AEDZ compressed recording
 * format into jAER master routing (plan Todo 4).
 *
 * <p>Asserts the new {@link AEDataFile} constants and
 * {@link AEDataFile#extensionForVersion}/{@link AEDataFile#hasDataFileExtension}
 * mappings; that {@link DATFileFilter} accepts {@code .aedz} (and nothing odd);
 * that {@link AEChip#constuctFileInputStream(File, javax.swing.ProgressMonitor)}
 * opens a real {@code .aedz} file as an {@link AEDZInputStream} with matching
 * events and still rejects an unknown extension; and that the new routing
 * branch cannot capture a legacy {@code .aedat2} (the dispatch predicate for
 * {@code .aedz} is false for {@code .aedat2}). Each check throws on mismatch
 * (non-zero exit) and prints PASS lines.
 *
 * <p>This machine has no X server / GPU, so an {@link AEChip} is allocated
 * without running its JOGL-bound constructor (its constructor builds a
 * {@code ChipCanvas} that requires OpenGL). The routing method only touches the
 * chip's (absent) {@code aeViewer}, so the allocated instance is sufficient to
 * exercise the production dispatch. The instance is allocated via reflection
 * (no constructor, no OpenGL) so javac never compiles against internal
 * {@code sun.misc.Unsafe} types.
 *
 * <p>Run headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.AEDZRoutingDemo}
 */
public class AEDZRoutingDemo {

    public static void main(String[] args) throws Exception {
        extensionAndConstantMappings();
        fileFilterRouting();
        openDialogFilterInstallation();
        aedzOpenRouting();
        rejectUnknownExtension();
        aedat2NotCapturedByAedzBranch();
        saveAsAedzRouting();
        System.out.println("ALL AEDZ ROUTING TESTS PASS");
    }

    /** Format-version sentinel constants and extension mapping helpers. */
    private static void extensionAndConstantMappings() {
        assertTrue(AEDataFile.DATA_FILE_EXTENSION_AEDZ.equals(".aedz"),
                "DATA_FILE_EXTENSION_AEDZ == .aedz");
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals("aedz"),
                "DATA_FILE_VERSION_NUMBER_AEDZ == aedz");
        // extensionForVersion mappings are unchanged for AEDAT-1/2/4 and new for aedz.
        assertTrue(AEDataFile.extensionForVersion("4.0").equals(".aedat4"),
                "extensionForVersion(4.0) == .aedat4");
        assertTrue(AEDataFile.extensionForVersion("2.0").equals(".aedat2"),
                "extensionForVersion(2.0) == .aedat2");
        assertTrue(AEDataFile.extensionForVersion("aedz").equals(".aedz"),
                "extensionForVersion(aedz) == .aedz");
        // hasDataFileExtension true for .aedz and every existing known extension.
        for (String name : new String[]{"x.aedz", "x.aedat4", "x.aedat2", "x.aedat", "x.DAT"}) {
            assertTrue(AEDataFile.hasDataFileExtension(name),
                    "hasDataFileExtension true for " + name);
        }
        assertTrue(!AEDataFile.hasDataFileExtension("x.foo"),
                "hasDataFileExtension false for .foo");
        System.out.println("PASS extension/constant mappings");
    }

    /** DATFileFilter must accept .aedz among the usual extensions and reject a stray extension. */
    private static void fileFilterRouting() {
        DATFileFilter filter = new DATFileFilter();
        assertTrue(filter.accept(new File("recording.aedz")),
                "DATFileFilter.accept(recording.aedz)");
        assertTrue(filter.accept(new File("recording.aedat4")),
                "DATFileFilter.accept(recording.aedat4)");
        assertTrue(!filter.accept(new File("recording.foo")),
                "DATFileFilter rejects .foo");
        DATFileFilter aedat4 = new DATFileFilter(DATFileFilter.Category.AEDAT4);
        assertTrue(aedat4.accept(new File("recording.aedat4")),
                "AEDAT4 filter accepts .aedat4");
        assertTrue(!aedat4.accept(new File("recording.aedat2")),
                "AEDAT4 filter rejects .aedat2");
        DATFileFilter other = new DATFileFilter(DATFileFilter.Category.OTHER);
        assertTrue(other.accept(new File("recording.aedz")),
                "OTHER filter accepts .aedz");
        assertTrue(!other.accept(new File("recording.aedat4")),
                "OTHER filter rejects .aedat4");
        DATFileFilter allFiles = new DATFileFilter(DATFileFilter.Category.ALL_FILES);
        assertTrue(allFiles.accept(new File("recording.foo")),
                "ALL_FILES accepts any file");
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        assertTrue(aedat4.accept(tmpDir) && other.accept(tmpDir) && allFiles.accept(tmpDir),
                "every filter shows directories");
        System.out.println("PASS DATFileFilter routing");
    }

    /** Production chooser installation, ordering, restoration, and extension matrix. */
    private static void openDialogFilterInstallation() {
        JFileChooser chooser = new JFileChooser();
        DATFileFilter.installOpenDialogFilters(chooser, null);
        assertTrue(!chooser.isAcceptAllFileFilterUsed(),
                "built-in All Files filter disabled");

        DATFileFilter.Category[] expected = {
            DATFileFilter.Category.ALL_RECOGNIZED,
            DATFileFilter.Category.AEDAT,
            DATFileFilter.Category.AEDAT2,
            DATFileFilter.Category.AEDAT4,
            DATFileFilter.Category.OTHER,
            DATFileFilter.Category.ALL_FILES
        };
        FileFilter[] installed = chooser.getChoosableFileFilters();
        assertTrue(installed.length == expected.length,
                "open dialog installs exactly six filters");
        for (int i = 0; i < expected.length; i++) {
            assertTrue(installed[i] instanceof DATFileFilter,
                    "open dialog filter " + i + " is DATFileFilter");
            assertTrue(((DATFileFilter) installed[i]).getCategory() == expected[i],
                    "open dialog filter order at " + i + " is " + expected[i]);
        }
        assertTrue(((DATFileFilter) chooser.getFileFilter()).getCategory()
                == DATFileFilter.Category.ALL_RECOGNIZED,
                "all recognized selected by default");

        DATFileFilter previous = (DATFileFilter) installed[3];
        DATFileFilter.installOpenDialogFilters(chooser, previous);
        assertTrue(((DATFileFilter) chooser.getFileFilter()).getCategory()
                == DATFileFilter.Category.AEDAT4,
                "previous AEDAT-4 filter restored");

        DATFileFilter allRecognized = new DATFileFilter(DATFileFilter.Category.ALL_RECOGNIZED);
        String[] dataExtensions = {
            "aedat", "aedat2", "aedat4", "aedz", "dat", "raw", "h5",
            "hdf5", "bag", "csv", "txt"
        };
        String[] indexExtensions = {"aeidx", "index"};
        for (String extension : dataExtensions) {
            assertTrue(allRecognized.accept(new File("recording." + extension)),
                    "all recognized accepts ." + extension);
            assertTrue(allRecognized.accept(new File("recording." + extension.toUpperCase())),
                    "all recognized accepts uppercase ." + extension);
        }
        for (String extension : indexExtensions) {
            assertTrue(allRecognized.accept(new File("recording." + extension)),
                    "all recognized accepts index ." + extension);
        }
        assertTrue(!allRecognized.accept(null), "all recognized rejects null");
        assertTrue(!allRecognized.accept(new File("recording")),
                "all recognized rejects extensionless file");
        assertTrue(!allRecognized.accept(new File("recording.foo")),
                "all recognized rejects unknown extension");
        assertTrue(allRecognized.getDescription().contains("*.hdf5")
                && allRecognized.getDescription().contains("*.index"),
                "all recognized description lists accepted HDF5 and legacy index formats");
        DATFileFilter otherRecognized = new DATFileFilter(DATFileFilter.Category.OTHER);
        assertTrue(otherRecognized.getDescription().contains("*.hdf5")
                && otherRecognized.getDescription().contains("*.index"),
                "other description lists accepted HDF5 and legacy index formats");

        DATFileFilter.installSequencingDialogFilters(chooser);
        installed = chooser.getChoosableFileFilters();
        assertTrue(installed.length == 2,
                "sequencing dialog installs data and all-files filters only");
        DATFileFilter sequencing = (DATFileFilter) installed[0];
        assertTrue(sequencing.getCategory() == DATFileFilter.Category.SEQUENCING_DATA,
                "sequencing data filter is first and selected");
        assertTrue(chooser.getFileFilter() == sequencing,
                "sequencing data selected by default");
        for (String extension : dataExtensions) {
            assertTrue(sequencing.accept(new File("recording." + extension)),
                    "sequencing accepts data ." + extension);
        }
        for (String extension : indexExtensions) {
            assertTrue(!sequencing.accept(new File("recording." + extension)),
                    "sequencing rejects index ." + extension);
        }
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        for (FileFilter installedFilter : installed) {
            assertTrue(installedFilter.accept(tmpDir),
                    "sequencing filter shows directories: " + installedFilter.getDescription());
            assertTrue(!installedFilter.accept(null),
                    "sequencing filter rejects null: " + installedFilter.getDescription());
        }
        System.out.println("PASS open-dialog filter installation and restoration");
    }

    /** A real .aedz file opened through chip.constuctFileInputStream routes to AEDZInputStream. */
    private static void aedzOpenRouting() throws Exception {
        AEPacketRaw packet = makePacket(5, 1);
        File file = tempFile(".aedz");
        try (AEDZOutputStream out = new AEDZOutputStream(new FileOutputStream(file), null)) {
            out.writePacket(packet);
        }
        AEChip chip = allocateChip();
        AEFileInputStreamInterface in = chip.constuctFileInputStream(file, null);
        assertTrue(in instanceof AEDZInputStream, "constuctFileInputStream(.aedz) returns AEDZInputStream");
        try {
            assertTrue(in.size() == 5, "routed AEDZ size() == 5");
            AEPacketRaw r = in.readPacketByNumber(5);
            assertTrue(r.getNumEvents() == 5, "routed AEDZ reads 5 events");
            for (int i = 0; i < 5; i++) {
                if (r.getAddresses()[i] != packet.getAddresses()[i]
                        || r.getTimestamps()[i] != packet.getTimestamps()[i]) {
                    throw new AssertionError("routed AEDZ event " + i + " mismatch");
                }
            }
        } finally {
            in.close();
        }
        System.out.println("PASS .aedz open routing through AEChip.constuctFileInputStream");
        file.delete();
    }

    /** The dispatch rejects an unknown extension with the documented FileNotFoundException. */
    private static void rejectUnknownExtension() throws Exception {
        AEChip chip = allocateChip();
        File file = tempFile(".foo");
        try {
            chip.constuctFileInputStream(file, null);
            throw new AssertionError("constuctFileInputStream(.foo) should throw FileNotFoundException");
        } catch (FileNotFoundException expected) {
            // good
        } finally {
            file.delete();
        }
        System.out.println("PASS unknown .foo rejected with FileNotFoundException");
    }

    /**
     * The new {@code .aedz} branch (keyed on {@code aedz}) must never capture a
     * legacy format. Unlike the branches that read events, constructing a legacy
     * {@link AEFileInputStream} needs a fully-initialized chip, so the guarantee
     * is asserted at the same predicate {@code constuctFileInputStream} dispatches
     * on (see {@link AEChip#constuctFileInputStream}): neither {@code .aedat2} nor
     * {@code .aedat4} is an AEDZ extension, so the added branch is unreachable for
     * them and the legacy reader branches are left untouched.
     */
    private static void aedat2NotCapturedByAedzBranch() {
        // Same predicate the routed dispatcher uses for the AEDZ branch.
        String aedzExt = AEDataFile.DATA_FILE_EXTENSION_AEDZ.substring(1);
        for (String legacy : new String[]{"recording.aedat2", "recording.aedat4", "recording.aedat"}) {
            assertTrue(!org.apache.commons.io.FilenameUtils.isExtension(legacy, aedzExt),
                    legacy + " must not match the .aedz dispatch predicate");
        }
        assertTrue(!AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals("2.0")
                        && !AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals("4.0"),
                "aedz sentinel != 2.0/4.0, so legacy version routing is unaffected");
        System.out.println("PASS legacy formats not captured by AEDZ routing branch");
    }

    /** File -> Save As exposes AEDZ and opens the real AEDZ writer used by the export loop. */
    private static void saveAsAedzRouting() throws Exception {
        SaveAsOptions.Format format;
        try {
            format = SaveAsOptions.Format.valueOf("AEDZ");
        } catch (IllegalArgumentException e) {
            throw new AssertionError("File -> Save As is missing the AEDZ output format", e);
        }
        assertTrue("aedz".equals(format.extension) && format.label.contains("AEDZ"),
                "Save As AEDZ choice has the user-facing label and extension");
        Method factory;
        try {
            factory = SaveAsExporter.class.getDeclaredMethod("openAedzOutputStream", File.class, AEChip.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Save As lacks a real AEDZ output-stream path", e);
        }
        factory.setAccessible(true);
        File file = tempFile(".aedz");
        file.delete();
        AEPacketRaw source = makePacket(11, 5);
        AEDZOutputStream out = (AEDZOutputStream) factory.invoke(null, file, null);
        try (out) {
            out.writePacket(source);
        }
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            AEPacketRaw got = in.readPacketByNumber(11);
            assertTrue(got.getNumEvents() == 11, "Save As AEDZ writer emits a readable recording");
            for (int i = 0; i < 11; i++) {
                assertTrue(got.getAddresses()[i] == source.getAddresses()[i]
                        && got.getTimestamps()[i] == source.getTimestamps()[i],
                        "Save As AEDZ preserves address/timestamp at " + i);
            }
        } finally {
            file.delete();
        }
        System.out.println("PASS File -> Save As AEDZ menu and real writer routing");
    }

    private static AEPacketRaw makePacket(int n, int seed) throws IOException {
        AEPacketRaw p = new AEPacketRaw(Math.max(1, n));
        int[] addr = p.getAddresses();
        int[] ts = p.getTimestamps();
        for (int i = 0; i < n; i++) {
            addr[i] = (seed * 131 + i) * 7 + 3;
            ts[i] = 1000 + i;
        }
        p.setNumEvents(n);
        return p;
    }

    private static File tempFile(String ext) throws IOException {
        return File.createTempFile("jaer-aedz-routing", ext);
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    /** Allocate an {@link AEChip} without running its OpenGL-bound constructor (test-only). */
    private static AEChip allocateChip() {
        try {
            // Class.forName so javac does not compile against sun.misc.Unsafe
            // (internal API warning). Same pattern as AEFileInputStream.closeDirectBuffer.
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field f = unsafeClass.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
            return (AEChip) allocate.invoke(unsafe, AEChip.class);
        } catch (Exception e) {
            throw new AssertionError("could not allocate headless AEChip for routing test", e);
        }
    }
}
