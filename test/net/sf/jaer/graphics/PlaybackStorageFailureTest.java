package net.sf.jaer.graphics;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Disk-gone reads must surface; interrupt-closed channels must not. */
public class PlaybackStorageFailureTest {

    @Test
    public void inputOutputErrorIsAStorageFailure() {
        assertTrue(AEPlayer.isPlaybackStorageFailure(new IOException("Input/output error")));
    }

    @Test
    public void closedChannelWrappedAsIoExceptionIsNotAStorageFailure() {
        assertFalse(AEPlayer.isPlaybackStorageFailure(
                new IOException("fileChannel is closed", new ClosedChannelException())));
        assertFalse(AEPlayer.isPlaybackStorageFailure(
                new IOException("fileChannel unexpectedly was closed")));
        assertFalse(AEPlayer.isPlaybackStorageFailure(
                new IOException("AEDAT-4 channel closed and file is null")));
    }
}
