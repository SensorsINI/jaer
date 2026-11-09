package net.sf.jaer.eventio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.time.Instant;

import org.junit.Test;

public class FilenameStartTest {

    @Test
    public void muxedNameUsesTheDateTokenAfterTheSerials() {
        File f = new File("PropheseeIMX636HD-00050491_NRVS5KRC1S-us1addr8_2026-11-09T11-58-09-0500 fly box.aedat4");
        AEDataFile.FilenameStart start = AEDataFile.parseFilenameStart(f);
        assertNotNull(start);
        assertEquals(Instant.parse("2026-11-09T16:58:09Z").toEpochMilli(), start.epochMs);
        assertEquals("-05:00", start.zone.getId());
    }

    @Test
    public void classicNameStillParses() {
        File f = new File("Tmpdiff128-2007-04-04T11-32-21-0700-0 ants.dat");
        AEDataFile.FilenameStart start = AEDataFile.parseFilenameStart(f);
        assertNotNull(start);
        assertEquals(Instant.parse("2007-04-04T18:32:21Z").toEpochMilli(), start.epochMs);
    }

    @Test
    public void nameWithoutADateReturnsNull() {
        assertNull(AEDataFile.parseFilenameStart(new File("recording.aedat4")));
    }
}
