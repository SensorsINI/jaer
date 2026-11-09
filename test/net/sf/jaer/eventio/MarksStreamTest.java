package net.sf.jaer.eventio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import net.sf.jaer.eventio.AEFileInputStream.Marks;

/** One AEDAT-4 file, two cameras: marks are per stream, and an unset OUT is not an event count. */
public class MarksStreamTest {

    @Test
    public void unsetOutStoredAsEventCountBelongsOnlyToThatCamera() {
        Marks marks = new Marks();
        marks.markIn = 0;
        marks.markOut = 25_370_119L;
        marks.otherMarks.add(17_274_387L);
        marks.otherMarks.add(17_293_257L);
        assertTrue(AEFileInputStream.marksBelongToStream(marks, 25_370_119L));
        assertFalse(AEFileInputStream.marksBelongToStream(marks, 86_747_329L));
    }

    @Test
    public void explicitOutIsNotTakenFromTheSharedSidecar() {
        Marks marks = new Marks();
        marks.markOut = 1_000L;
        assertFalse(AEFileInputStream.marksBelongToStream(marks, 25_370_119L));
    }

    @Test
    public void sentinelOutBelongsWhenMarkersFitThisStream() {
        Marks marks = new Marks();
        marks.markOut = Long.MAX_VALUE;
        marks.otherMarks.add(10L);
        assertTrue(AEFileInputStream.marksBelongToStream(marks, 100L));
        assertFalse(AEFileInputStream.marksBelongToStream(marks, 5L));
    }

    @Test
    public void streamSidecarNameDiffersFromTheWholeFile() {
        File file = new File("/Volumes/jaer data/flies/recording.aedat4");
        String shared = AEFileInputStream.marksCacheFileName(file);
        String stream0 = AEFileInputStream.marksCacheFileName(file, 0);
        String stream3 = AEFileInputStream.marksCacheFileName(file, 3);
        assertTrue(stream0.endsWith(".s0.marks.csv"));
        assertTrue(stream3.endsWith(".s3.marks.csv"));
        assertFalse(shared.equals(stream0));
        assertFalse(stream0.equals(stream3));
    }
}
