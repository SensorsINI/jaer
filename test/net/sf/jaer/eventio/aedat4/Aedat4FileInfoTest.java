package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.ZoneId;

import org.junit.Test;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * File info table rows, clipboard text, and the legacy summary string.
 */
public class Aedat4FileInfoTest {

    private static final long BASE_US = 1_700_000_000_000_000L;

    @Test
    public void truncatePathKeepsStartAndEnd() {
        String path = "C:\\Users\\tobid\\Dropbox\\GitHub\\SensorsINI\\jaer\\tmp\\jaer test data\\evk4\\flyrena\\"
                + "PropheseeIMX636HD-00050491_2026-10-13T10-29-32-0400-VCR_spider-concat final 3.aedat4";
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            FontMetrics fm = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            assertEquals(path, Aedat4FileInfo.truncatePath(path, fm, 4000));
            String shown = Aedat4FileInfo.truncatePath(path, fm, 220);
            assertTrue(shown.startsWith("C:\\Users"));
            assertTrue(shown.endsWith("3.aedat4"));
            assertTrue(shown.contains("\u2026"));
            assertTrue(fm.stringWidth(shown) <= 220);
            assertTrue(shown.length() < path.length());
        } finally {
            g.dispose();
        }
    }

    @Test
    public void zstdRowsAndLegacySummaryRoundTrip() {
        Aedat4FileInfo info = Aedat4FileInfo.fromRecording(
                "C:\\data\\rec.aedat4",
                CompressionType.ZSTD,
                14_860_621_072L,
                0,
                0,
                "2w1d13m12.92s",
                1_500_000L,
                10,
                true,
                1_000L,
                5_000L,
                -1L,
                0,
                "PropheseeIMX636HD",
                47_637_994,
                0,
                0,
                "PropheseeIMX636HD",
                BASE_US,
                ZoneId.of("America/New_York"));
        assertEquals("C:\\data\\rec.aedat4", info.value("Path"));
        assertEquals("ZSTD", info.value("Compression"));
        assertEquals(String.format("%,d", 14_860_621_072L) + " (+14.861G)", info.value("Events"));
        assertEquals("0", info.value("Frames"));
        assertEquals(String.format("%,d", 47_637_994), info.value("EVTS packets"));
        assertEquals("right shift 10 bits (jAER-only EVTS), On and Off collapsed", info.value("Lossy time bins"));
        assertEquals(Aedat4FileInfo.RATIO_UNAVAILABLE, info.value("Compression ratio"));
        assertEquals(Aedat4FileInfo.UNCOMPRESSED_NOT_IN_HEADERS, info.value("Uncompressed payload"));
        assertTrue(info.value("Duration").startsWith("2w1d13m12.92s"));
        assertTrue(info.value("Recording start").startsWith("2023-11-14"));
        assertTrue(info.value("Event rate").contains("events/s"));
        String summary = info.toSummaryText();
        assertTrue(summary.startsWith("C:\\data\\rec.aedat4\n"));
        assertTrue(summary.contains("AEDAT-4 ZSTD: +14.861G (14,860,621,072) events"));
        assertTrue(summary.contains("Lossy time bins: right shift 10 bits (jAER-only EVTS), On and Off collapsed"));
        assertTrue(summary.contains("Stream 0 (PropheseeIMX636HD), 47,637,994 EVTS packets indexed"));
        assertTrue(summary.contains("Chip: PropheseeIMX636HD"));

        Aedat4FileInfo.Section parsed = Aedat4FileInfo.parseSummary(null, summary);
        assertEquals(info.value("Path"), parsed.value("Path"));
        assertEquals(info.value("Events"), parsed.value("Events"));
        assertEquals(info.value("Compression"), parsed.value("Compression"));
        assertEquals(info.value("Chip"), parsed.value("Chip"));
        assertEquals(info.value("EVTS packets"), parsed.value("EVTS packets"));
        assertEquals(info.value("Lossy time bins"), parsed.value("Lossy time bins"));
        assertEquals(Aedat4FileInfo.RATIO_UNAVAILABLE, parsed.value("Compression ratio"));

        String tsv = Aedat4FileInfo.toTabSeparated(java.util.List.of(info.asSection(null)));
        assertTrue(tsv.startsWith("Property\tValue\n"));
        assertTrue(tsv.contains("Path\tC:\\data\\rec.aedat4\n"));
        assertTrue(tsv.contains("Compression\tZSTD\n"));
        String html = Aedat4FileInfo.toHtml(java.util.List.of(info.asSection(null)));
        assertTrue(html.contains("<td>Path</td><td>C:\\data\\rec.aedat4</td>"));
    }

    @Test
    public void uncompressedRatioParsesBack() {
        long raw = 8_000L;
        long packed = 2_000L;
        String payload = Aedat4Compression.formatPayloadCompression(CompressionType.LZ4, raw, packed);
        String text = "C:\\rec.aedat4\n"
                + "AEDAT-4 LZ4: +1.000k (1,000) events, 0 (0) frames, 0 (0) IMU samples, duration=1s\n"
                + payload + "\n"
                + "Stream 1 (cam), 10 EVTS packets indexed\n"
                + "Chip: DVS128\n";
        Aedat4FileInfo.Section parsed = Aedat4FileInfo.parseSummary("Exported (output)", text);
        assertEquals("4.0:1 (25% of uncompressed)", parsed.value("Compression ratio"));
        assertEquals("1 (cam)", parsed.value("Stream"));
        assertEquals("10", parsed.value("EVTS packets"));
        assertNotNull(parsed.value("Compressed payload"));
        assertNotNull(parsed.value("Uncompressed payload"));
    }

    @Test
    public void snapshotMatchesGetFileInfo() throws Exception {
        File file = File.createTempFile("jaer-file-info-table", ".aedat4");
        DVS128 chip = new DVS128();
        try {
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    file, chip, CompressionType.NONE, BASE_US)) {
                out.writeBundle(sampleEvents());
            }
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                Aedat4FileInfo info = in.snapshotFileInfo();
                assertEquals(in.getFileInfo(), info.toSummaryText());
                assertEquals(file.getAbsolutePath(), info.value("Path"));
                assertEquals("NONE", info.value("Compression"));
                assertEquals("DVS128", info.value("Chip"));
                assertTrue(info.value("Events").startsWith("5 ("));
                assertEquals("0", info.value("Frames"));
                assertEquals("1:1", info.value("Compression ratio"));
                assertEquals("off", info.value("Lossy time bins"));
                assertTrue(info.value("File size").contains("bytes"));
            } finally {
                in.close();
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static PacketBundle sampleEvents() {
        PacketBundle bundle = new PacketBundle();
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        add(out, 100, 1, 2, PolarityEvent.Polarity.On);
        add(out, 200, 3, 4, PolarityEvent.Polarity.Off);
        add(out, 300, 1, 2, PolarityEvent.Polarity.On);
        add(out, 400, 3, 4, PolarityEvent.Polarity.On);
        add(out, 1500, 1, 2, PolarityEvent.Polarity.On);
        bundle.add(events);
        return bundle;
    }

    private static void add(OutputEventIterator<PolarityEvent> out, int t, int x, int y,
            PolarityEvent.Polarity polarity) {
        PolarityEvent e = out.nextOutput();
        e.timestamp = t;
        e.x = (short) x;
        e.y = (short) y;
        e.setPolarity(polarity);
    }
}
