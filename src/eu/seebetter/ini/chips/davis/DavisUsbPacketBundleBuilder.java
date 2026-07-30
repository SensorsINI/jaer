/*
 * DavisUsbPacketBundleBuilder.java
 *
 * jAER 3.0: build typed PacketBundle while parsing DAVIS USB words.
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
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
 * emit homogeneous typed packets directly from USB decode (no mixed
 * {@code ApsDvsEvent} path).
 * <p>
 * Accumulates into a consumer-supplied {@link PacketBundle} (the USB write
 * buffer). Polarity / IMU / external working packets are flushed into the
 * bundle on type change or {@link #flushAll()}.
 *
 * @author tobi
 */
public class DavisUsbPacketBundleBuilder {

    private PacketBundle out;

    private EventPacket<PolarityEvent> polarity;
    private OutputEventIterator<PolarityEvent> polarityOut;
    private ImuPacket imu;
    private EventPacket<ExternalEvent> external;
    private OutputEventIterator<ExternalEvent> externalOut;
    private DavisFrameAssembler frameAssembler;

    private enum Active {
        NONE, POLARITY, IMU, EXTERNAL
    }

    private Active active = Active.NONE;
    private boolean rollingShutter;
    private int apsWidth;
    private int apsHeight;

    public void attach(PacketBundle writeBundle, int apsWidth, int apsHeight) {
        this.out = writeBundle;
        this.apsWidth = apsWidth;
        this.apsHeight = apsHeight;
        if (polarity == null) {
            polarity = new EventPacket<>(PolarityEvent.class);
        }
        if (imu == null) {
            imu = new ImuPacket();
        }
        if (external == null) {
            external = new EventPacket<>(ExternalEvent.class);
        }
        if (frameAssembler == null || this.apsWidth != apsWidth || this.apsHeight != apsHeight) {
            frameAssembler = new DavisFrameAssembler(apsWidth, apsHeight, 0);
        }
        // Do not clear out — USB buffers accumulate until pool swap.
        polarityOut = null;
        externalOut = null;
    }

    public void setRollingShutter(boolean rollingShutter) {
        this.rollingShutter = rollingShutter;
    }

    public void onFrameStart(boolean rolling) {
        setRollingShutter(rolling);
        frameAssembler.reset();
    }

    public void addPolarity(final int x, final int y, final boolean on, final int timestamp) {
        ensurePolarityActive();
        if (polarityOut == null) {
            polarityOut = polarity.outputIterator();
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
        if (active == Active.POLARITY) {
            flushPolarity();
        } else if (active == Active.IMU) {
            flushImu();
        }
        active = Active.EXTERNAL;
        if (externalOut == null) {
            external.clear();
            externalOut = external.outputIterator();
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
        if (active == Active.POLARITY) {
            flushPolarity();
        } else if (active == Active.EXTERNAL) {
            flushExternal();
        }
        active = Active.IMU;
        imu.appendCopy(sample);
    }

    /**
     * Feed one APS ADC sample into the frame assembler. Returns a completed
     * frame if this sample finished the frame.
     */
    public FramePacket addApsSample(final int adcSample, final int timestamp, final int x, final int y,
            final boolean resetRead, final boolean pixFirst, final boolean pixLast) {
        if (active == Active.POLARITY) {
            flushPolarity();
        } else if (active == Active.IMU) {
            flushImu();
        } else if (active == Active.EXTERNAL) {
            flushExternal();
        }
        active = Active.NONE;
        ApsDvsEvent.ReadoutType type = resetRead ? ApsDvsEvent.ReadoutType.ResetRead : ApsDvsEvent.ReadoutType.SignalRead;
        FramePacket frame = frameAssembler.process(adcSample, timestamp, (short) x, (short) y, type, pixFirst, pixLast,
                rollingShutter);
        if (frame != null && out != null) {
            out.add(frame);
        }
        return frame;
    }

    public void flushAll() {
        if (active == Active.POLARITY) {
            flushPolarity();
        } else if (active == Active.IMU) {
            flushImu();
        } else if (active == Active.EXTERNAL) {
            flushExternal();
        }
        active = Active.NONE;
    }

    private void ensurePolarityActive() {
        if (active == Active.IMU) {
            flushImu();
        } else if (active == Active.EXTERNAL) {
            flushExternal();
        }
        if (active != Active.POLARITY) {
            polarity.clear();
            polarityOut = polarity.outputIterator();
            active = Active.POLARITY;
        }
    }

    private void flushPolarity() {
        if (out == null || polarity == null || polarity.isEmpty()) {
            active = Active.NONE;
            polarityOut = null;
            return;
        }
        EventPacket<PolarityEvent> copy = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> itr = copy.outputIterator();
        for (PolarityEvent e : polarity) {
            PolarityEvent d = itr.nextOutput();
            d.copyFrom(e);
        }
        out.add(copy);
        polarity.clear();
        polarityOut = null;
        active = Active.NONE;
    }

    private void flushImu() {
        if (out == null || imu == null || imu.isEmpty()) {
            active = Active.NONE;
            return;
        }
        ImuPacket copy = new ImuPacket(Math.max(ImuPacket.DEFAULT_CAPACITY, imu.getSize()));
        for (int i = 0; i < imu.getSize(); i++) {
            copy.appendCopy(imu.get(i));
        }
        out.add(copy);
        imu.clear();
        active = Active.NONE;
    }

    private void flushExternal() {
        if (out == null || external == null || external.isEmpty()) {
            active = Active.NONE;
            externalOut = null;
            return;
        }
        EventPacket<ExternalEvent> copy = new EventPacket<>(ExternalEvent.class);
        OutputEventIterator<ExternalEvent> itr = copy.outputIterator();
        for (ExternalEvent e : external) {
            ExternalEvent d = itr.nextOutput();
            d.copyFrom(e);
        }
        out.add(copy);
        external.clear();
        externalOut = null;
        active = Active.NONE;
    }
}
