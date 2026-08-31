package net.sf.jaer.eventio.aedat4;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.RecordingFilename;

/**
 * One camera in a muxed AEDAT-4 file: EVTS/FRME/IMUS stream IDs {@code 3i},
 * {@code 3i+1}, {@code 3i+2}, plus an independent 32-bit timestamp unwrapper.
 */
public final class Aedat4CameraTrack {

    public static final int STREAMS_PER_CAMERA = 3;

    public final AEChip chip;
    public final String source;
    public final int sizeX;
    public final int sizeY;
    public final Integer colorFilter;
    public final RecordingConfigurationSnapshot snapshot;
    public final int index;
    public final int streamBase;
    public final TimestampUnwrapper unwrapper = new TimestampUnwrapper();

    public Aedat4CameraTrack(AEChip chip, String source, RecordingConfigurationSnapshot snapshot, int index) {
        this.chip = chip;
        this.source = source != null && !source.isEmpty()
                ? source
                : (chip == null ? "jAER" : chip.getClass().getSimpleName());
        this.sizeX = chip == null ? 0 : chip.getSizeX();
        this.sizeY = chip == null ? 0 : chip.getSizeY();
        this.colorFilter = Aedat4InfoNode.colorFilterForChip(chip);
        this.snapshot = snapshot;
        this.index = index;
        this.streamBase = index * STREAMS_PER_CAMERA;
    }

    public static Aedat4CameraTrack fromChip(AEChip chip, RecordingConfigurationSnapshot snapshot, int index) {
        String serial = RecordingFilename.usbSerialAlnum(chip);
        String source = RecordingFilename.cameraToken(
                chip == null ? "jAER" : chip.getClass().getSimpleName(), serial);
        return new Aedat4CameraTrack(chip, source, snapshot, index);
    }

    public int eventsStreamId() {
        return streamBase;
    }

    public int framesStreamId() {
        return streamBase + 1;
    }

    public int imuStreamId() {
        return streamBase + 2;
    }
}
