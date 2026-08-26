package net.sf.jaer.event;

/**
 * Hardware-free regression for EventPacket classification from its declared
 * event class and authoritative bundle accounting.
 */
public final class EventPacketDeclaredTypeDemo {

    private static int assertions;

    private EventPacketDeclaredTypeDemo() {
    }

    public static void main(final String[] args) {
        declaredClassClassification();
        authoritativeSealingCountsPacketTypes();
        epochBehaviorIsPreserved();
        System.out.println("EVENT_PACKET_DECLARED_TYPE ASSERTIONS=" + assertions);
        System.out.println("EVENT_PACKET_DECLARED_TYPE PASS");
    }

    private static void declaredClassClassification() {
        final EventPacket<PolarityEvent> polarity = new EventPacket<>(PolarityEvent.class);
        final EventPacket<ExternalEvent> external = new EventPacket<>(ExternalEvent.class);
        final EventPacket<DerivedExternalEvent> derivedExternal
                = new EventPacket<>(DerivedExternalEvent.class);
        final EventPacket<OrdinaryEvent> ordinary = new EventPacket<>(OrdinaryEvent.class);

        require(polarity.getPacketType() == PacketType.POLARITY,
                "declared PolarityEvent packets remain POLARITY");
        require(external.isEmpty() && external.getPacketType() == PacketType.SPECIAL,
                "empty declared ExternalEvent packets are SPECIAL without content inspection");
        require(derivedExternal.getPacketType() == PacketType.SPECIAL,
                "declared ExternalEvent subclasses are SPECIAL");
        require(ordinary.getPacketType() == PacketType.POLARITY,
                "ordinary other EventPacket classes retain legacy POLARITY classification");
    }

    private static void authoritativeSealingCountsPacketTypes() {
        final EventPacket<PolarityEvent> polarity = new EventPacket<>(PolarityEvent.class);
        final OutputEventIterator<PolarityEvent> polarityOut = polarity.outputIterator();
        polarityOut.nextOutput().timestamp = 10;
        polarityOut.nextOutput().timestamp = 11;
        polarity.setTimestampEpoch(4);

        final EventPacket<ExternalEvent> external = new EventPacket<>(ExternalEvent.class);
        final OutputEventIterator<ExternalEvent> externalOut = external.outputIterator();
        externalOut.nextOutput().timestamp = 12;
        externalOut.nextOutput().timestamp = 13;
        externalOut.nextOutput().timestamp = 14;
        external.setTimestampEpoch(4);

        final PacketBundle bundle = new PacketBundle();
        final AcquisitionMetadata metadata = bundle.beginAcquisition(17, 3);
        bundle.add(polarity);
        bundle.add(external);
        bundle.seal();

        require(bundle.isSealed(), "authoritative bundle seals before classification is observed");
        require(metadata.getAcceptedCount(PacketType.POLARITY) == 2,
                "authoritative sealing counts only DVS events as POLARITY");
        require(metadata.getAcceptedCount(PacketType.SPECIAL) == 3,
                "authoritative sealing counts external markers as SPECIAL");
        require(bundle.getNumPolarityEvents() == 2,
                "external markers do not inflate the bundle polarity count");
        require(bundle.getFirstPolarityPacket() == polarity,
                "external markers are not selected as the first polarity packet");
    }

    private static void epochBehaviorIsPreserved() {
        final EventPacket<ExternalEvent> external = new EventPacket<>(ExternalEvent.class);
        external.setTimestampEpoch(9);
        final EventPacket<ExternalEvent> filtered = external.constructNewPacket();

        require(filtered.getEventClass() == ExternalEvent.class,
                "constructNewPacket preserves the declared event class");
        require(filtered.getPacketType() == PacketType.SPECIAL,
                "constructNewPacket preserves declared-class packet classification");
        require(filtered.getTimestampEpoch() == 9,
                "constructNewPacket preserves the timestamp epoch");
        external.clear();
        require(external.getTimestampEpoch() == TypedDataPacket.UNASSIGNED_TIMESTAMP_EPOCH,
                "clearing an EventPacket still clears its timestamp epoch");
        require(external.getPacketType() == PacketType.SPECIAL,
                "clearing packet contents does not change declared-class classification");
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    public static final class OrdinaryEvent extends BasicEvent {
        public OrdinaryEvent() {
        }
    }

    public static final class DerivedExternalEvent extends ExternalEvent {
        public DerivedExternalEvent() {
        }
    }
}
