package net.sf.jaer.eventio.export;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import net.sf.jaer.eventio.export.SaveAsOptions.Format;

/**
 * Save As output names must use a suffix the selected format writes.
 */
public class SaveAsOptionsTest {

    @Test
    public void missingDotBeforeAedat4IsInserted() {
        assertEquals("Davis240C DVSFLOW16 Translating Boxes 1.aedat4",
                SaveAsOptions.ensureFormatExtensionName(
                        "Davis240C DVSFLOW16 Translating Boxes 1aedat4", Format.AEDAT4));
    }

    @Test
    public void missingExtensionIsAdded() {
        assertEquals("export.aedat4",
                SaveAsOptions.ensureFormatExtensionName("export", Format.AEDAT4));
        assertEquals("export.csv",
                SaveAsOptions.ensureFormatExtensionName("export", Format.CSV));
        assertEquals("export.h5",
                SaveAsOptions.ensureFormatExtensionName("export", Format.DSEC_H5));
    }

    @Test
    public void invalidExtensionIsReplaced() {
        assertEquals("fan.aedat4",
                SaveAsOptions.ensureFormatExtensionName("fan.mp4", Format.AEDAT4));
        assertEquals("fan.aedat4",
                SaveAsOptions.ensureFormatExtensionName("fan.aedat", Format.AEDAT4));
        assertEquals("fan.csv",
                SaveAsOptions.ensureFormatExtensionName("fan.aedat4", Format.CSV));
    }

    @Test
    public void acceptedAlternateExtensionsAreKept() {
        assertEquals("events.txt",
                SaveAsOptions.ensureFormatExtensionName("events.txt", Format.CSV));
        assertEquals("events.hdf5",
                SaveAsOptions.ensureFormatExtensionName("events.hdf5", Format.DSEC_H5));
        assertEquals("events.aedat4",
                SaveAsOptions.ensureFormatExtensionName("events.AEDAT4", Format.AEDAT4));
    }
}
