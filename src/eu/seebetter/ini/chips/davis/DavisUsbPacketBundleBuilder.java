/*
 * DavisUsbPacketBundleBuilder.java
 *
 * jAER 3.0: build typed PacketBundle while parsing DAVIS USB words.
 */
package eu.seebetter.ini.chips.davis;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.util.EnumMap;
import java.util.function.IntSupplier;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.AcquisitionMetadata;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.ExternalEvent;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
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
    private long timestampEpoch;
    private long acquisitionSessionId = -1;
    private long acceptedElements;
    private boolean imuAssemblyInProgress;
    private IntSupplier hostCapacitySupplier = () -> Integer.MAX_VALUE;
    private final EnumMap<PacketType, Long> pendingHostCapacityLoss
            = new EnumMap<>(PacketType.class);

    /** Sets the authoritative typed host-capacity limit for subsequent slices. */
    public void setHostCapacitySupplier(final IntSupplier hostCapacitySupplier) {
        if (hostCapacitySupplier == null) {
            throw new NullPointerException("hostCapacitySupplier");
        }
        this.hostCapacitySupplier = hostCapacitySupplier;
    }

    public void attach(PacketBundle writeBundle, AEChip aeChip, int apsWidth, int apsHeight) {
        if (aeChip instanceof DavisBaseCamera) {
            this.chip = (DavisBaseCamera) aeChip;
        }
        final AcquisitionMetadata metadata = writeBundle.getAcquisitionMetadata();
        final long attachedSessionId = metadata == null
                ? -1 : metadata.getAcquisitionSessionId();
        final boolean newAcquisitionSession = metadata != null
                && attachedSessionId != acquisitionSessionId;
        if (writeBundle != this.out || newAcquisitionSession) {
            // Different pool slot after swap — reuse grown packets for this slot.
            this.out = writeBundle;
            if (newAcquisitionSession) {
                acquisitionSessionId = attachedSessionId;
                timestampEpoch = 0;
                imuAssemblyInProgress = false;
                pendingHostCapacityLoss.clear();
                if (frameAssembler != null) {
                    frameAssembler.reset();
                }
            }
            acceptedElements = 0;
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
        addPolarity(x, y, on, timestamp, 0);
    }

    public void addPolarity(final int x, final int y, final boolean on, final int timestamp, final int address) {
        if (!accept(PacketType.POLARITY)) {
            return;
        }
        if (polarity == null) {
            polarity = new EventPacket<>(PolarityEvent.class);
            polarityOut = polarity.outputIterator();
            polarityInBundle = false;
        }
        if (polarityOut == null) {
            polarityOut = polarity.isEmpty() ? polarity.outputIterator() : polarity.getOutputIterator();
        }
        if (polarity.isEmpty()) {
            polarity.setTimestampEpoch(timestampEpoch);
        }
        PolarityEvent e = polarityOut.nextOutput();
        e.reset();
        e.timestamp = timestamp;
        e.x = (short) x;
        e.y = (short) y;
        e.polarity = on ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
        e.type = (byte) (on ? 1 : 0);
        e.setSpecial(false);
        e.address = address;
    }

    public void addExternal(final int code, final int timestamp) {
        if (!accept(PacketType.SPECIAL)) {
            return;
        }
        if (external == null) {
            external = new EventPacket<>(ExternalEvent.class);
            externalInBundle = false;
        }
        if (externalOut == null) {
            externalOut = external.isEmpty() ? external.outputIterator() : external.getOutputIterator();
        }
        if (external.isEmpty()) {
            external.setTimestampEpoch(timestampEpoch);
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
        imuAssemblyInProgress = false;
        if (!accept(PacketType.IMU6)) {
            return;
        }
        if (imu == null) {
            imu = new ImuPacket();
            imuInBundle = false;
        }
        if (imu.isEmpty()) {
            imu.setTimestampEpoch(timestampEpoch);
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
            if (accept(PacketType.FRAME)) {
                frame.setTimestampEpoch(timestampEpoch);
                out.add(frame);
                if (chip != null) {
                    chip.noteUsbAssembledFrame(frame);
                }
            }
        }
        return frame;
    }

    /** Notes that the standard DAVIS parser has started assembling one IMU sample. */
    public void onImuStart() {
        imuAssemblyInProgress = true;
    }

    /** Records an incomplete IMU sample exactly only when its start was observed. */
    public void onIncompleteImuSample(final String reason) {
        if (imuAssemblyInProgress) {
            recordExactLoss(PacketType.IMU6,
                    AcquisitionMetadata.LossKind.PARTIAL_IMU, 1, reason);
        } else {
            recordUnquantifiedLoss(PacketType.IMU6,
                    AcquisitionMetadata.LossKind.PARTIAL_IMU,
                    reason + "; no tracked IMU start, count unavailable");
        }
        imuAssemblyInProgress = false;
    }

    /** Applies the standard DAVIS address patch to the most recent typed polarity event. */
    public void patchLastPolarityAddress(final int orMask) {
        if (polarity == null || polarity.isEmpty()) {
            return;
        }
        final PolarityEvent event = polarity.getEvent(polarity.getSize() - 1);
        event.address |= orMask;
    }

    /**
     * Flushes the current epoch, records reset-discarded assembly state, and
     * advances to fresh packets for the next epoch.
     *
     * @param imuStateUnknown true when the decoder cannot expose whether an IMU
     * sample was partial at reset
     */
    public void onTimestampReset(final boolean imuStateUnknown) {
        flushAll();
        if (frameAssembler != null && frameAssembler.isInFrame()) {
            recordExactLoss(PacketType.FRAME,
                    AcquisitionMetadata.LossKind.PARTIAL_FRAME, 1,
                    "timestamp reset discarded one incomplete frame");
            frameAssembler.reset();
        }
        if (imuAssemblyInProgress) {
            recordExactLoss(PacketType.IMU6,
                    AcquisitionMetadata.LossKind.PARTIAL_IMU, 1,
                    "timestamp reset discarded one incomplete IMU sample");
        } else if (imuStateUnknown) {
            recordUnquantifiedLoss(PacketType.IMU6,
                    AcquisitionMetadata.LossKind.PARTIAL_IMU,
                    "timestamp reset may have discarded decoder IMU assembly state; count unavailable");
        }
        imuAssemblyInProgress = false;
        timestampEpoch = Math.addExact(timestampEpoch, 1);
        startEpochPackets();
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
        flushHostCapacityLoss();
    }

    private boolean accept(final PacketType packetType) {
        final int configuredCapacity = hostCapacitySupplier.getAsInt();
        final long capacity = configuredCapacity < 0 ? 0 : configuredCapacity;
        if (acceptedElements >= capacity) {
            pendingHostCapacityLoss.merge(packetType, 1L, Math::addExact);
            return false;
        }
        acceptedElements++;
        return true;
    }

    private void flushHostCapacityLoss() {
        final AcquisitionMetadata metadata = out == null ? null : out.getAcquisitionMetadata();
        if (metadata == null || pendingHostCapacityLoss.isEmpty()) {
            return;
        }
        for (final var loss : pendingHostCapacityLoss.entrySet()) {
            metadata.recordExactLoss(loss.getKey(),
                    AcquisitionMetadata.LossKind.HOST_CAPACITY, loss.getValue(),
                    "authoritative typed host capacity exhausted");
        }
        pendingHostCapacityLoss.clear();
    }

    private void recordExactLoss(final PacketType packetType,
            final AcquisitionMetadata.LossKind kind, final long count,
            final String reason) {
        final AcquisitionMetadata metadata = out == null ? null : out.getAcquisitionMetadata();
        if (metadata != null) {
            metadata.recordExactLoss(packetType, kind, count, reason);
        }
    }

    private void recordUnquantifiedLoss(final PacketType packetType,
            final AcquisitionMetadata.LossKind kind, final String reason) {
        final AcquisitionMetadata metadata = out == null ? null : out.getAcquisitionMetadata();
        if (metadata != null) {
            metadata.recordUnquantifiedLoss(packetType, kind, reason);
        }
    }

    private void startEpochPackets() {
        polarity = new EventPacket<>(PolarityEvent.class);
        polarityOut = polarity.outputIterator();
        polarityInBundle = false;
        imu = new ImuPacket();
        imuInBundle = false;
        external = new EventPacket<>(ExternalEvent.class);
        externalOut = external.outputIterator();
        externalInBundle = false;

        if (out == slot0) {
            polarity0 = polarity;
            imu0 = imu;
            external0 = external;
        } else if (out == slot1) {
            polarity1 = polarity;
            imu1 = imu;
            external1 = external;
        }
    }
}
