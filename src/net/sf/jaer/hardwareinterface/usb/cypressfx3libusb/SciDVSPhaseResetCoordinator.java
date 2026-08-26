package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.io.IOException;
import java.util.Objects;

/**
 * Orders a fail-closed, host-only realignment of the SciDVS FPGA writer and
 * FX3 event endpoint.
 *
 * <p>The coordinator deliberately contains no USB implementation. The host
 * adapter is responsible for checked register writes/readbacks, reader
 * terminality, endpoint reset status, timestamp-marker ownership, and stream
 * qualification.</p>
 */
final class SciDVSPhaseResetCoordinator {

    interface Host {

        boolean readDvsRun() throws IOException;

        void verifyOtherProducersStopped() throws IOException;

        void writeDvsRun(boolean run) throws IOException;

        boolean hasActiveReader() throws IOException;

        void awaitSourceQuiescence() throws IOException;

        void writeUsbRun(boolean run) throws IOException;

        void awaitWriterThreadZero() throws IOException;

        boolean stopReader() throws IOException;

        void clearEventEndpoint() throws IOException;

        void configureAcquisitionInfrastructure() throws IOException;

        void startFreshReader() throws IOException;

        void armTimestampReset() throws IOException;

        void sendTimestampReset() throws IOException;

        boolean awaitTimestampReset() throws IOException;

        void disarmTimestampReset() throws IOException;

        void clearTimestampGuardForQualification() throws IOException;

        void beginQualification() throws IOException;

        boolean awaitQualification() throws IOException;

        void commitQualification() throws IOException;

        void failClosed();
    }

    void execute(final Host host) throws IOException {
        Objects.requireNonNull(host, "host");
        try {
            final boolean restoreDvsRun = host.readDvsRun();
            host.verifyOtherProducersStopped();
            host.writeDvsRun(false);
            if (host.hasActiveReader()) {
                host.awaitSourceQuiescence();
            }
            host.writeUsbRun(false);
            host.awaitWriterThreadZero();
            if (!host.stopReader()) {
                throw new IOException("old SciDVS reader did not terminate");
            }
            host.clearEventEndpoint();
            host.configureAcquisitionInfrastructure();
            host.startFreshReader();
            host.writeUsbRun(true);
            host.armTimestampReset();
            host.sendTimestampReset();
            if (!host.awaitTimestampReset()) {
                throw new IOException("fresh SciDVS reader did not observe the owned timestamp reset");
            }
            host.disarmTimestampReset();
            host.clearTimestampGuardForQualification();
            host.beginQualification();
            host.writeDvsRun(true);
            if (!host.awaitQualification()) {
                throw new IOException("SciDVS stream did not qualify after phase reset");
            }
            if (!restoreDvsRun) {
                host.writeDvsRun(false);
            }
            host.commitQualification();
        } catch (final IOException | RuntimeException failure) {
            failClosed(host, failure);
            throw failure;
        }
    }

    private static void failClosed(final Host host, final Throwable failure) {
        try {
            host.failClosed();
        } catch (final RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
