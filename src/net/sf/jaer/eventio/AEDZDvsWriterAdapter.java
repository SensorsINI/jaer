package net.sf.jaer.eventio;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedDataPacket;

/**
 * Streams the DVS projection of an authoritative typed bundle to AEDZ.
 * Non-polarity payloads are not converted to raw events; their skipped element
 * counts remain available by packet type.
 */
public final class AEDZDvsWriterAdapter {

    private final AEDZOutputStream output;
    private final ToIntFunction<PolarityEvent> addressReconstructor;
    private final EnumMap<PacketType, Long> skippedCounts
            = new EnumMap<>(PacketType.class);

    /**
     * Creates a typed DVS projection over an existing AEDZ output stream.
     *
     * @param output destination stream
     * @param addressReconstructor reconstructs the raw address from each
     *        current, possibly filtered/transformed polarity event
     */
    public AEDZDvsWriterAdapter(final AEDZOutputStream output,
            final ToIntFunction<PolarityEvent> addressReconstructor) {
        this.output = Objects.requireNonNull(output, "output");
        this.addressReconstructor = Objects.requireNonNull(
                addressReconstructor, "addressReconstructor");
    }

    /**
     * Writes every non-filtered polarity event in bundle packet/event order.
     * This compatibility overload preserves the historical behavior of
     * omitting events marked {@code filteredOut}.
     *
     * @param bundle sealed authoritative typed bundle
     * @throws IOException on AEDZ output failure
     */
    public synchronized void writeBundle(final PacketBundle bundle) throws IOException {
        writeBundle(bundle, true);
    }

    /**
     * Writes polarity events from a sealed authoritative typed bundle.
     *
     * @param bundle sealed authoritative typed bundle
     * @param skipFilteredOut if true, omit events marked {@code filteredOut};
     *        if false, include them for record-all behavior
     * @throws IOException on AEDZ output failure
     */
    public synchronized void writeBundle(final PacketBundle bundle,
            final boolean skipFilteredOut) throws IOException {
        requireAuthoritative(bundle);
        for (final TypedDataPacket packet : bundle) {
            output.beginTimestampEpoch(packet.getTimestampEpoch());
            if (packet.getPacketType() == PacketType.POLARITY) {
                writePolarityPacket(packet, skipFilteredOut);
            } else {
                addSkipped(packet.getPacketType(), packet.getSize());
            }
        }
    }

    /** Returns the number of unsupported payload elements skipped for a type. */
    public synchronized long getSkippedCount(final PacketType packetType) {
        Objects.requireNonNull(packetType, "packetType");
        return skippedCounts.getOrDefault(packetType, 0L);
    }

    private static void requireAuthoritative(final PacketBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        if (!bundle.isSealed() || bundle.getAcquisitionMetadata() == null
                || !bundle.getAcquisitionMetadata().isSealed()) {
            throw new IllegalStateException(
                    "AEDZ typed projection requires a sealed authoritative bundle");
        }
        if (bundle.isLegacyRawBridge() || bundle.getRawPacket() != null) {
            throw new IllegalStateException(
                    "AEDZ typed projection does not accept a legacy raw bridge");
        }
    }

    private void writePolarityPacket(final TypedDataPacket packet,
            final boolean skipFilteredOut) throws IOException {
        if (!(packet instanceof EventPacket<?>)) {
            throw new IllegalStateException("POLARITY payload is not an EventPacket");
        }
        final EventPacket<?> events = (EventPacket<?>) packet;
        for (int i = 0; i < events.getSize(); i++) {
            final BasicEvent basicEvent = events.getEvent(i);
            if (!(basicEvent instanceof PolarityEvent)) {
                throw new IllegalStateException(
                        "POLARITY EventPacket contains a non-polarity event");
            }
            final PolarityEvent event = (PolarityEvent) basicEvent;
            if (!skipFilteredOut || !event.isFilteredOut()) {
                output.writeEvent(addressReconstructor.applyAsInt(event), event.timestamp);
            }
        }
    }

    private void addSkipped(final PacketType packetType, final int count) {
        final long current = skippedCounts.getOrDefault(packetType, 0L);
        skippedCounts.put(packetType, Math.addExact(current, count));
    }
}
