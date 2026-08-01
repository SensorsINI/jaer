/*
 * DavisUsbPacketBundleBuilder.java
 *
 * jAER 3.0: build typed PacketBundle while parsing DAVIS USB words.
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.ExternalEvent;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;

/**
 * Stateful helper used by {@code DAViSFX3HardwareInterface.RetinaAEReader} to
 * emit homogeneous typed packets directly from USB decode.
 * <p>
 * Matches {@code DavisEventExtractor#extractBundleTyped}: one polarity packet
 * per write-buffer slice; do not flush polarity on interleaved APS; frames
 * added as they complete.
 */
public class DavisUsbPacketBundleBuilder {

    private PacketBundle out;
    private PacketBundle slot0;
    private PacketBundle slot1;
    private DavisBaseCamera chip;

    private EventPacket<PolarityEvent> polarity0;
    private EventPacket<PolarityEvent> polarity1;
    private EventPacket<PolarityEvent> polarity;
    private OutputEventIterator<PolarityEvent> polarityOut;
    private boolean polarityInBundle;

    private ImuPacket imu0;
    private ImuPacket imu1;
    private ImuPacket imu;
    private boolean imuInBundle;

    private EventPacket<ExternalEvent> external0;
    private EventPacket<ExternalEvent> external1;
    private EventPacket<ExternalEvent> external;
    private OutputEventIterator<ExternalEvent> externalOut;
    private boolean externalInBundle;

    private DavisFrameAssembler frameAssembler;
    private boolean rollingShutter;
    private int apsWidth;
    private int apsHeight;

    public void attach(PacketBundle writeBundle, AEChip aeChip, int apsWidth, int apsHeight) {
        if (aeChip instanceof DavisBaseCamera) {
            this.chip = (DavisBaseCamera) aeChip;
        }
        if (writeBundle != this.out) {
            // Different pool slot after swap — reuse grown packets for this slot.
            this.out = writeBundle;
            bindSlot(writeBundle);
            polarityOut = polarity.outputIterator();
            polarityInBundle = false;
            if (imu != null) {
                imu.clear();
            }
            imuInBundle = false;
            if (external != null) {
                externalOut = external.outputIterator();
            } else {
                externalOut = null;
            }
            externalInBundle = false;
        }
        ensureAssembler(apsWidth, apsHeight);
    }

    private void bindSlot(PacketBundle writeBundle) {
        if (slot0 == null || writeBundle == slot0) {
            slot0 = writeBundle;
            if (polarity0 == null) {
                polarity0 = new EventPacket<>(PolarityEvent.class);
            }
            if (imu0 == null) {
                imu0 = new ImuPacket();
            }
            if (external0 == null) {
                external0 = new EventPacket<>(ExternalEvent.class);
            }
            polarity = polarity0;
            imu = imu0;
            external = external0;
            return;
        }
        if (slot1 == null || writeBundle == slot1) {
            slot1 = writeBundle;
            if (polarity1 == null) {
                polarity1 = new EventPacket<>(PolarityEvent.class);
            }
            if (imu1 == null) {
                imu1 = new ImuPacket();
            }
            if (external1 == null) {
                external1 = new EventPacket<>(ExternalEvent.class);
            }
            polarity = polarity1;
            imu = imu1;
            external = external1;
            return;
        }
        slot0 = writeBundle;
        if (polarity0 == null) {
            polarity0 = new EventPacket<>(PolarityEvent.class);
        }
        if (imu0 == null) {
            imu0 = new ImuPacket();
        }
        if (external0 == null) {
            external0 = new EventPacket<>(ExternalEvent.class);
        }
        polarity = polarity0;
        imu = imu0;
        external = external0;
    }

    private void ensureAssembler(int apsWidth, int apsHeight) {
        this.apsWidth = apsWidth;
        this.apsHeight = apsHeight;
        if (frameAssembler == null) {
            if (chip != null) {
                // Chip sizes + exposure fallback match extractBundleTyped
                frameAssembler = new DavisFrameAssembler(chip);
            } else {
                frameAssembler = new DavisFrameAssembler(apsWidth, apsHeight, 0);
            }
        }
    }

    public void setRollingShutter(boolean rollingShutter) {
        this.rollingShutter = rollingShutter;
    }

    /**
     * APS Frame-Start special from USB. Opens the assembler if idle; does not
     * {@link DavisFrameAssembler#reset()} (that caused SignalRead-without-frame
     * when Reset column samples were sparse or reordered).
     */
    public void onFrameStart(boolean rolling, int timestamp) {
        setRollingShutter(rolling);
        ensureAssembler(apsWidth, apsHeight);
        frameAssembler.ensureFrameOpen(timestamp);
    }

    public void addPolarity(final int x, final int y, final boolean on, final int timestamp) {
        if (polarity == null) {
            polarity = new EventPacket<>(PolarityEvent.class);
            polarityOut = polarity.outputIterator();
            polarityInBundle = false;
        }
        if (polarityOut == null) {
            polarityOut = polarity.isEmpty() ? polarity.outputIterator() : polarity.getOutputIterator();
        }
        PolarityEvent e = polarityOut.nextOutput();
        e.reset();
        e.timestamp = timestamp;
        e.x = (short) x;
        e.y = (short) y;
        e.polarity = on ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
        e.type = (byte) (on ? 1 : 0);
        e.setSpecial(false);
    }

    public void addExternal(final int code, final int timestamp) {
        if (external == null) {
            external = new EventPacket<>(ExternalEvent.class);
            externalInBundle = false;
        }
        if (externalOut == null) {
            externalOut = external.isEmpty() ? external.outputIterator() : external.getOutputIterator();
        }
        ExternalEvent e = externalOut.nextOutput();
        e.reset();
        e.timestamp = timestamp;
        e.setCode(code);
        switch (code) {
            case 2:
                e.setEdge(ExternalEvent.Edge.Falling);
                break;
            case 3:
                e.setEdge(ExternalEvent.Edge.Rising);
                break;
            case 4:
                e.setEdge(ExternalEvent.Edge.Pulse);
                break;
            default:
                e.setEdge(ExternalEvent.Edge.Other);
                break;
        }
        e.setSpecial(true);
    }

    public void addImu(final IMUSample sample) {
        if (imu == null) {
            imu = new ImuPacket();
            imuInBundle = false;
        }
        imu.appendCopy(sample);
        // Overlay / Steadicam still read DavisBaseCamera.getImuSample()
        if (chip != null) {
            chip.setImuSample(sample);
        }
    }

    public FramePacket addApsSample(final int adcSample, final int timestamp, final int x, final int y,
            final boolean resetRead, final boolean pixFirst, final boolean pixLast) {
        ensureAssembler(apsWidth, apsHeight);
        ApsDvsEvent.ReadoutType type = resetRead ? ApsDvsEvent.ReadoutType.ResetRead : ApsDvsEvent.ReadoutType.SignalRead;
        FramePacket frame = frameAssembler.process(adcSample, timestamp, (short) x, (short) y, type, pixFirst, pixLast,
                rollingShutter);
        if (frame != null && out != null) {
            out.add(frame);
            if (chip != null) {
                chip.noteUsbAssembledFrame(frame);
            }
        }
        return frame;
    }

    public void flushAll() {
        if (out == null) {
            return;
        }
        if (imu != null && !imu.isEmpty() && !imuInBundle) {
            out.add(imu);
            imuInBundle = true;
        }
        if (external != null && !external.isEmpty() && !externalInBundle) {
            out.add(external);
            externalInBundle = true;
        }
        if (polarity != null && !polarity.isEmpty() && !polarityInBundle) {
            out.add(polarity);
            polarityInBundle = true;
        }
    }
}
