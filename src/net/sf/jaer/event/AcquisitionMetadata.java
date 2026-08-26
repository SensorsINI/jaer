/*
 * AcquisitionMetadata.java
 *
 * Source accounting for an authoritative typed PacketBundle.
 */
package net.sf.jaer.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Acquisition context and source accounting attached to an authoritative
 * {@link PacketBundle}. The accounting is mutable while a producer fills the
 * bundle and becomes immutable when the bundle is sealed.
 */
public final class AcquisitionMetadata {

    /** Whether a loss record has an exact count. */
    public enum LossQuantification {
        EXACT,
        UNQUANTIFIED
    }

    /** Machine-readable cause of source-side data loss. */
    public enum LossKind {
        HOST_CAPACITY,
        DEVICE_REPORTED,
        PARTIAL_FRAME,
        PARTIAL_IMU,
        MALFORMED_INPUT,
        UNKNOWN
    }

    /** Immutable description of source-side data loss. */
    public static final class LossRecord {

        private final PacketType packetType;
        private final LossKind kind;
        private final LossQuantification quantification;
        private final long exactCount;
        private final String reason;

        private LossRecord(final PacketType packetType, final LossKind kind,
                final LossQuantification quantification, final long exactCount,
                final String reason) {
            this.packetType = packetType;
            this.kind = kind;
            this.quantification = quantification;
            this.exactCount = exactCount;
            this.reason = reason;
        }

        private LossRecord(final LossRecord source) {
            this(source.packetType, source.kind, source.quantification,
                    source.exactCount, source.reason);
        }

        public PacketType getPacketType() {
            return packetType;
        }

        public LossKind getKind() {
            return kind;
        }

        public LossQuantification getQuantification() {
            return quantification;
        }

        public boolean isExact() {
            return quantification == LossQuantification.EXACT;
        }

        public boolean isUnquantified() {
            return quantification == LossQuantification.UNQUANTIFIED;
        }

        /**
         * @return the exact lost count
         * @throws IllegalStateException if this loss is unquantified
         */
        public long getExactCount() {
            if (!isExact()) {
                throw new IllegalStateException("loss is unquantified");
            }
            return exactCount;
        }

        public String getReason() {
            return reason;
        }
    }

    private final long acquisitionSessionId;
    private final long sequenceId;
    private final EnumMap<PacketType, Long> acceptedCounts
            = new EnumMap<>(PacketType.class);
    private final LinkedHashSet<Long> timestampEpochs = new LinkedHashSet<>();
    private final ArrayList<LossRecord> lossRecords = new ArrayList<>();
    private boolean preserveSourceAccounting;
    private boolean sealed;

    public AcquisitionMetadata(final long acquisitionSessionId, final long sequenceId) {
        if (acquisitionSessionId < 0 || sequenceId < 0) {
            throw new IllegalArgumentException(
                    "acquisition session and sequence identifiers must be non-negative");
        }
        this.acquisitionSessionId = acquisitionSessionId;
        this.sequenceId = sequenceId;
    }

    private AcquisitionMetadata(final AcquisitionMetadata source) {
        acquisitionSessionId = source.acquisitionSessionId;
        sequenceId = source.sequenceId;
        acceptedCounts.putAll(source.acceptedCounts);
        timestampEpochs.addAll(source.timestampEpochs);
        for (final LossRecord record : source.lossRecords) {
            lossRecords.add(new LossRecord(record));
        }
        preserveSourceAccounting = true;
        sealed = false;
    }

    static AcquisitionMetadata copySourceContext(final AcquisitionMetadata source) {
        Objects.requireNonNull(source, "source");
        if (!source.sealed) {
            throw new IllegalStateException("authoritative source metadata must be sealed before copying");
        }
        return new AcquisitionMetadata(source);
    }

    public long getAcquisitionSessionId() {
        return acquisitionSessionId;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public long getAcceptedCount(final PacketType packetType) {
        Objects.requireNonNull(packetType, "packetType");
        return acceptedCounts.getOrDefault(packetType, 0L);
    }

    public Map<PacketType, Long> getAcceptedCounts() {
        return Collections.unmodifiableMap(new EnumMap<>(acceptedCounts));
    }

    public Collection<Long> getTimestampEpochs() {
        final Set<Long> copy = new LinkedHashSet<>(timestampEpochs);
        return Collections.unmodifiableSet(copy);
    }

    public Collection<LossRecord> getLossRecords() {
        final List<LossRecord> copy = new ArrayList<>(lossRecords);
        return Collections.unmodifiableList(copy);
    }

    public boolean isSealed() {
        return sealed;
    }

    /**
     * Compatibility overload for callers that do not yet provide a structured
     * loss kind.
     */
    public void recordExactLoss(final PacketType packetType, final long count,
            final String reason) {
        recordExactLoss(packetType, LossKind.UNKNOWN, count, reason);
    }

    public void recordExactLoss(final PacketType packetType, final LossKind kind,
            final long count, final String reason) {
        ensureMutable();
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        if (count < 0) {
            throw new IllegalArgumentException("exact loss count must be non-negative");
        }
        lossRecords.add(new LossRecord(packetType, kind,
                LossQuantification.EXACT, count, reason));
    }

    /**
     * Compatibility overload for callers that do not yet provide a structured
     * loss kind.
     */
    public void recordUnquantifiedLoss(final PacketType packetType, final String reason) {
        recordUnquantifiedLoss(packetType, LossKind.UNKNOWN, reason);
    }

    public void recordUnquantifiedLoss(final PacketType packetType,
            final LossKind kind, final String reason) {
        ensureMutable();
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        lossRecords.add(new LossRecord(packetType, kind,
                LossQuantification.UNQUANTIFIED, 0, reason));
    }

    public long getExactLossCount(final PacketType packetType) {
        Objects.requireNonNull(packetType, "packetType");
        long total = 0;
        for (final LossRecord record : lossRecords) {
            if (record.packetType == packetType && record.isExact()) {
                total = Math.addExact(total, record.exactCount);
            }
        }
        return total;
    }

    public boolean hasUnquantifiedLoss(final PacketType packetType) {
        Objects.requireNonNull(packetType, "packetType");
        for (final LossRecord record : lossRecords) {
            if (record.packetType == packetType && record.isUnquantified()) {
                return true;
            }
        }
        return false;
    }

    void sealFromPackets(final Iterable<TypedDataPacket> packets) {
        ensureMutable();
        Objects.requireNonNull(packets, "packets");

        final EnumMap<PacketType, Long> derivedCounts = new EnumMap<>(PacketType.class);
        final LinkedHashSet<Long> derivedEpochs = new LinkedHashSet<>();
        for (final TypedDataPacket packet : packets) {
            if (packet == null) {
                throw new IllegalStateException("authoritative bundle contains a null typed packet");
            }
            final PacketType packetType = packet.getPacketType();
            if (packetType == null) {
                throw new IllegalStateException("authoritative typed packet has no packet type");
            }
            final long epoch = packet.getTimestampEpoch();
            if (epoch < 0) {
                throw new IllegalStateException(
                        "authoritative typed packet has no non-negative timestamp epoch");
            }
            if (!preserveSourceAccounting) {
                final long current = derivedCounts.getOrDefault(packetType, 0L);
                derivedCounts.put(packetType, Math.addExact(current, packet.getSize()));
                derivedEpochs.add(epoch);
            }
        }

        if (!preserveSourceAccounting) {
            acceptedCounts.clear();
            acceptedCounts.putAll(derivedCounts);
            timestampEpochs.clear();
            timestampEpochs.addAll(derivedEpochs);
        }
        sealed = true;
    }

    private void ensureMutable() {
        if (sealed) {
            throw new IllegalStateException("acquisition metadata is sealed");
        }
    }
}
