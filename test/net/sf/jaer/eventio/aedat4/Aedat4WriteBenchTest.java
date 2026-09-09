package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.sf.jaer.Help;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * Tiny write-path bench: every codec, both workloads, quiet data compresses.
 */
public class Aedat4WriteBenchTest {

    @Test
    public void tinyBenchRunsAllCodecsAndQuietCompresses() throws Exception {
        Aedat4WriteBench.Report report = Aedat4WriteBench.run(Aedat4WriteBench.Config.tiny());
        assertEquals(Aedat4WriteBench.CODECS.length * Aedat4WriteBench.Workload.values().length,
                report.rows.size());
        Aedat4WriteBench.Row quietNone = null;
        Aedat4WriteBench.Row quietZstdHigh = null;
        Aedat4WriteBench.Row highNone = null;
        for (Aedat4WriteBench.Row row : report.rows) {
            assertTrue(row.events > 0);
            assertTrue(row.fileBytes > 0);
            assertTrue(row.wallNs > 0);
            if (row.workload == Aedat4WriteBench.Workload.QUIET_BURST) {
                if (row.compression == CompressionType.NONE) {
                    quietNone = row;
                } else if (row.compression == CompressionType.ZSTD_HIGH) {
                    quietZstdHigh = row;
                }
            }
            if (row.workload == Aedat4WriteBench.Workload.HIGH_RATE
                    && row.compression == CompressionType.NONE) {
                highNone = row;
            }
        }
        assertTrue(quietNone != null && quietZstdHigh != null && highNone != null);
        assertTrue("quiet ZSTD_HIGH should beat NONE on size",
                quietZstdHigh.fileBytes < quietNone.fileBytes);
        assertTrue(quietZstdHigh.payloadRatio() > 1.2);
        assertEquals(1.0, highNone.payloadRatio(), 0.05);
        String html = Help.Html.of(Aedat4Compression.class);
        assertTrue(html != null && html.contains("LZ4"));
        assertFalse(report.toPlainText().isEmpty());
    }
}
