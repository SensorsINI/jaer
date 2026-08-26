package net.sf.jaer.graphics;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.eventio.AEDZDvsWriterAdapter;
import net.sf.jaer.eventio.AEDZOutputStream;

/** Package-private state and recording decisions used by {@link AEViewer}. */
final class AEViewerRouteState {

    enum LiveRoute {
        AUTHORITATIVE_TYPED,
        LEGACY_RAW
    }

    private enum AedzRoute {
        AUTHORITATIVE_TYPED,
        LEGACY_RAW,
        LEGACY_FILTERED_RAW
    }

    static final class LiveDecision {
        private final LiveRoute route;
        private final boolean acquisitionRestartRequired;

        private LiveDecision(final LiveRoute route,
                final boolean acquisitionRestartRequired) {
            this.route = route;
            this.acquisitionRestartRequired = acquisitionRestartRequired;
        }

        LiveRoute getRoute() {
            return route;
        }

        boolean requiresAcquisitionRestart() {
            return acquisitionRestartRequired;
        }
    }

    private Object monitor;
    private long monitorSession;
    private boolean monitorSessionKnown;
    private boolean typedDeliveryDeclined;
    private LiveRoute selectedRoute;

    LiveDecision selectLiveRoute(final Object currentMonitor,
            final long currentMonitorSession, final boolean typedEligible) {
        Objects.requireNonNull(currentMonitor, "currentMonitor");
        final boolean ownerChanged = !monitorSessionKnown
                || monitor != currentMonitor
                || monitorSession != currentMonitorSession;
        if (ownerChanged) {
            monitor = currentMonitor;
            monitorSession = currentMonitorSession;
            monitorSessionKnown = true;
            typedDeliveryDeclined = false;
            selectedRoute = null;
        }
        final LiveRoute requested = typedEligible && !typedDeliveryDeclined
                ? LiveRoute.AUTHORITATIVE_TYPED : LiveRoute.LEGACY_RAW;
        final boolean restartRequired = ownerChanged || selectedRoute != requested;
        selectedRoute = requested;
        return new LiveDecision(requested, restartRequired);
    }

    LiveDecision typedDeliveryDeclined(final Object currentMonitor,
            final long currentMonitorSession) {
        if (!monitorSessionKnown || monitor != currentMonitor
                || monitorSession != currentMonitorSession) {
            throw new IllegalStateException(
                    "typed delivery declined outside the current monitor session");
        }
        typedDeliveryDeclined = true;
        final boolean restartRequired = selectedRoute != LiveRoute.LEGACY_RAW;
        selectedRoute = LiveRoute.LEGACY_RAW;
        return new LiveDecision(LiveRoute.LEGACY_RAW, restartRequired);
    }

    void clear() {
        monitor = null;
        monitorSession = 0;
        monitorSessionKnown = false;
        typedDeliveryDeclined = false;
        selectedRoute = null;
    }

    static void writeAedz(final AEDZOutputStream output,
            final AEDZDvsWriterAdapter adapter,
            final boolean recordFilteredEvents,
            final AEPacketRaw rawPacket,
            final EventPacket cookedPacket,
            final PacketBundle cookedBundle,
            final PacketBundle authoritativeSourceBundle,
            final Function<EventPacket, AEPacketRaw> rawReconstructor)
            throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(rawReconstructor, "rawReconstructor");

        final AedzRoute route;
        if (authoritativeSourceBundle != null) {
            route = AedzRoute.AUTHORITATIVE_TYPED;
        } else if (recordFilteredEvents) {
            route = AedzRoute.LEGACY_FILTERED_RAW;
        } else {
            route = AedzRoute.LEGACY_RAW;
        }

        switch (route) {
            case AUTHORITATIVE_TYPED:
                final PacketBundle selectedBundle = recordFilteredEvents
                        ? cookedBundle : authoritativeSourceBundle;
                if (selectedBundle == null) {
                    throw new IllegalStateException(
                            "authoritative AEDZ recording has no selected typed bundle");
                }
                adapter.writeBundle(selectedBundle, recordFilteredEvents);
                return;
            case LEGACY_RAW:
                if (rawPacket == null) {
                    throw new IllegalStateException(
                            "legacy AEDZ recording has no raw input packet");
                }
                output.writePacket(rawPacket);
                return;
            case LEGACY_FILTERED_RAW:
                if (cookedPacket == null) {
                    throw new IllegalStateException(
                            "filtered legacy AEDZ recording has no cooked input packet");
                }
                final AEPacketRaw reconstructed = rawReconstructor.apply(cookedPacket);
                if (reconstructed == null) {
                    throw new IllegalStateException(
                            "filtered legacy AEDZ reconstruction returned no raw packet");
                }
                output.writePacket(reconstructed);
                return;
            default:
                throw new AssertionError("unhandled AEDZ route " + route);
        }
    }
}
