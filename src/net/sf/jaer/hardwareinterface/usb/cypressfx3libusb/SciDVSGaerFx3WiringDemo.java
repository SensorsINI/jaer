package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Frozen source-structure and reflection acceptance test for routing SciDVS GAER
 * through the factory-opened DAViS FX3 reader. It compiles before production
 * wiring exists and is intentionally runtime RED while the reader lacks its
 * decoder/raw/typed/resolved fields. It reads source only; it constructs no USB
 * device and exercises no hardware.
 */
public final class SciDVSGaerFx3WiringDemo {

    private static final Path SOURCE = Paths.get("src", "net", "sf", "jaer",
            "hardwareinterface", "usb", "cypressfx3libusb",
            "DAViSFX3HardwareInterface.java");
    private static final Path CYPRESS_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java");
    private static final Path PACKET_BUNDLE_POOL_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "event", "PacketBundlePool.java");
    /** Secondary structure guard; StandardDavisTypedParserDemo is the semantic oracle. */
    private static final String STANDARD_LOOP_SHA256
            = "01670958696bdda9f4073afc2fb6f2b26df93a8e855438febad42e91053d8f97";
    private static int assertions;

    private SciDVSGaerFx3WiringDemo() {
    }

    public static void main(final String[] args) throws Exception {
        final String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        final String cypressSource = Files.readString(CYPRESS_SOURCE, StandardCharsets.UTF_8);
        testExpectedFields();
        testProtectedTranslateAndNoDuplicateGaerParserState();
        testEagerConstructionReusesExistingConfiguration(source);
        testSinkWiringAndLazyHarden(source);
        testStartupTimestampResetBarrier(source, cypressSource);
        testStartupDvsRunGate(source);
        testPhaseQualificationWiring(source, cypressSource);
        testTimestampDisorderDiagnostics(source);
        testQuiescentDrainShutdownWiring(source, cypressSource);
        testLazyResolutionAndEarlyGaerBranch(source);
        testStandardDavisLoopHash(source);
        System.out.println("SCIDVS_GAER_FX3_WIRING EXISTING_ASSERTIONS=" + assertions);
        testTypedAuthorityDoesNotAcquireOrPublishRaw(source);
        System.out.println("SCIDVS_GAER_FX3_WIRING ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_GAER_FX3_WIRING PASS");
    }

    private static void testExpectedFields() {
        final Map<String, Field> fields = new HashMap<>();
        for (final Field field : DAViSFX3HardwareInterface.RetinaAEReader.class
                .getDeclaredFields()) {
            fields.put(field.getName(), field);
        }

        require(fields.containsKey("gaerDecoder"), "reader owns gaerDecoder");
        require(fields.containsKey("gaerRawSink"), "reader owns gaerRawSink");
        require(fields.containsKey("gaerTypedSink"), "reader owns gaerTypedSink");
        require(fields.containsKey("gaerResolved"), "reader owns lazy gaerResolved");
        require(fields.containsKey("startupTimestampReset"),
                "reader owns startup timestamp-reset barrier");
        require(fields.get("gaerDecoder").getType().getSimpleName().equals("SciDVSGaerDecoder"),
                "gaerDecoder has exact production type");
        require(fields.get("gaerRawSink").getType().getSimpleName().equals("SciDVSGaerRawSink"),
                "gaerRawSink has exact production type");
        require(fields.get("gaerTypedSink").getType().getSimpleName().equals("SciDVSGaerTypedSink"),
                "gaerTypedSink has exact production type");
        require(fields.get("gaerResolved").getType() == Boolean.class,
                "gaerResolved is nullable Boolean for lazy resolution");
        require(Modifier.isFinal(fields.get("gaerDecoder").getModifiers())
                && Modifier.isFinal(fields.get("gaerRawSink").getModifiers())
                && Modifier.isFinal(fields.get("gaerTypedSink").getModifiers())
                && Modifier.isFinal(fields.get("startupTimestampReset").getModifiers()),
                "GAER decoder, sinks, and startup reset barrier are final state owners");
        require(!Modifier.isFinal(fields.get("gaerResolved").getModifiers()),
                "lazy resolution field remains assignable");
    }

    private static void testProtectedTranslateAndNoDuplicateGaerParserState() throws Exception {
        final Class<?> reader = DAViSFX3HardwareInterface.RetinaAEReader.class;
        final Method translate = reader.getDeclaredMethod("translateEvents", ByteBuffer.class);
        require(Modifier.isProtected(translate.getModifiers()),
                "translateEvents remains a protected override");

        final String[] forbidden = {
            "gaerWrapAdd", "gaerLastTimestamp", "gaerCurrentTimestamp", "gaerDvsLastY",
            "gaerApsCurrentReadoutType", "gaerApsRGBPixelOffset",
            "gaerApsRGBPixelOffsetDirection", "gaerApsCountX", "gaerApsCountY",
            "gaerImuEvents", "gaerImuType", "gaerImuCount", "gaerImuTmpData"
        };
        for (final String name : forbidden) {
            try {
                reader.getDeclaredField(name);
                require(false, "reader must not duplicate GAER parser state " + name);
            } catch (final NoSuchFieldException expected) {
                require(true, "reader does not duplicate GAER parser state " + name);
            }
        }
    }

    private static void testEagerConstructionReusesExistingConfiguration(final String source) {
        final String constructor = between(source,
                "public RetinaAEReader(final CypressFX3 cypress)",
                "private void checkMonotonicTimestamp()");
        final String compact = constructor.replaceAll("\\s+", " ");
        require(count(constructor, "spiConfigReceive(") == 8,
                "reader constructor keeps exactly eight existing SPI configuration reads");
        require(compact.contains("gaerDecoder = new SciDVSGaerDecoder("),
                "GAER decoder is constructed eagerly in reader constructor");
        require(compact.contains("new SciDVSGaerDecoder.Config( chipID, dvsSizeX, dvsSizeY, dvsInvertXY, apsSizeX, apsSizeY, apsInvertXY, apsFlipX, apsFlipY, imuFlipX, imuFlipY, imuFlipZ"),
                "GAER decoder config is built only from already-read DAViS values");
        require(compact.contains("gaerRawSink = new SciDVSGaerRawSink("),
                "GAER raw sink is constructed eagerly");
        final int typedSinkStart = compact.indexOf(
                "gaerTypedSink = new SciDVSGaerTypedSink(");
        final int typedSinkEnd = compact.indexOf(");", typedSinkStart);
        require(typedSinkStart >= 0 && typedSinkEnd > typedSinkStart,
                "GAER typed sink construction has stable source anchors");
        final String typedSinkConstruction = compact.substring(typedSinkStart, typedSinkEnd);
        require(typedSinkConstruction.contains("typedBuilder,"),
                "GAER typed sink uses the existing typed builder");
        require(!typedSinkConstruction.contains("gaerRawSink"),
                "GAER typed sink construction has no raw-sink dependency");
        require(!constructor.contains("SciDVSGaerMode.resolve"),
                "GAER mode is not resolved eagerly before chip attachment");
    }

    private static void testSinkWiringAndLazyHarden(final String source) {
        final String constructor = between(source,
                "public RetinaAEReader(final CypressFX3 cypress)",
                "private void checkMonotonicTimestamp()");
        final String compact = constructor.replaceAll("\\s+", " ");

        require(compact.contains("super.toString(), this::shouldLogGaerWarning)"),
                "eager decoder receives super.toString and this::shouldLogGaerWarning");
        final int rawSinkStart = compact.indexOf(
                "gaerRawSink = new SciDVSGaerRawSink(");
        final int rawSinkEnd = compact.indexOf(");", rawSinkStart);
        require(rawSinkStart >= 0 && rawSinkEnd > rawSinkStart,
                "GAER raw sink construction has stable source anchors");
        final String rawSinkConstruction = compact.substring(rawSinkStart, rawSinkEnd);
        require(rawSinkConstruction.contains("() -> true"),
                "legacy raw GAER sink writes its complete raw payload");
        require(!rawSinkConstruction.contains(
                "!usbTypedDemuxActive || dualWriteApsImuAe"),
                "legacy raw GAER sink no longer uses the obsolete dual-write predicate");
        require(compact.contains("() -> getChip() != null ? getChip().getSizeX() : dvsSizeX"),
                "typed X supplier is exactly the chip-null fallback supplier");
        require(count(constructor, "this::handleGaerTimestampReset") == 2,
                "timestamp reset hook is passed to both raw and typed sinks");

        final String shouldLog = between(constructor, "private boolean shouldLogGaerWarning()",
                "private void handleGaerTimestampReset()");
        require(shouldLog.contains("warningCount++"), "shouldLogGaerWarning increments warningCount");
        require(shouldLog.contains("WARNING_INTERVAL"), "shouldLogGaerWarning throttles by WARNING_INTERVAL");

        final String translate = between(source,
                "protected void translateEvents(final ByteBuffer b)",
                "public void propertyChange(final PropertyChangeEvent arg0)");
        require(translate.substring(0, translate.indexOf("gaerResolved = SciDVSGaerMode.resolveFromSystemProperty"))
                .contains("gaerModeUnresolved && getChip() != null"),
                "lazy resolution is guarded by a chip nonnull guard");
        require(translate.contains("if (Boolean.TRUE.equals(gaerResolved))"),
                "active GAER branch is null-safe so an unknown chip falls through and later retries");
    }

    private static void testQuiescentDrainShutdownWiring(final String source,
            final String cypressSource) {
        final String endpoint = method(cypressSource,
                "public void setInEndpointEnabled(final boolean inEndpointEnabled)");
        final int disableBranch = endpoint.indexOf("else");
        final int tryBlock = endpoint.indexOf("try", disableBranch);
        final int hook = endpoint.indexOf("beforeDisableINEndpoint()", tryBlock);
        final int finallyBlock = endpoint.indexOf("finally", hook);
        final int finalDisable = endpoint.indexOf("disableINEndpoint()", finallyBlock);
        require(disableBranch >= 0 && tryBlock > disableBranch && hook > tryBlock,
                "endpoint disable invokes the pre-disable hook inside try");
        require(finallyBlock > hook && finalDisable > finallyBlock,
                "endpoint disable always invokes disableINEndpoint from finally");
        require(endpoint.indexOf("disableINEndpoint()", disableBranch) == finalDisable,
                "endpoint disable has no bypass around the finally block");

        final String baseHook = method(cypressSource,
                "protected void beforeDisableINEndpoint()");
        require(baseHook.substring(0, baseHook.indexOf("{")).contains(
                "throws HardwareInterfaceException"),
                "pre-disable hook exposes checked shutdown failure");

        final String readerHook = method(cypressSource,
                "protected void noteCompletedTransfer(");
        require(readerHook.substring(0, readerHook.indexOf("{")).contains(
                "int actualLength"),
                "completed-transfer reader hook accepts actualLength");
        final String callback = method(cypressSource,
                "public void processTransfer(final RestrictedTransfer transfer)");
        require(count(callback, "processTransferLocked(transfer)") == 2,
                "typed and raw callback locks both delegate to the shared completed-transfer path");
        final String lockedCallback = method(cypressSource,
                "private void processTransferLocked(final RestrictedTransfer transfer)");
        final int completed = lockedCallback.indexOf(
                "transfer.status() == LibUsb.TRANSFER_COMPLETED");
        final int note = lockedCallback.indexOf(
                "noteCompletedTransfer(transfer.actualLength())", completed);
        final int translate = lockedCallback.indexOf(
                "translateEvents(transfer.buffer())", completed);
        require(completed >= 0 && note > completed,
                "completed USB callback forwards actualLength to the reader hook");
        require(translate > note,
                "completed USB callback notes payload activity before translating");

        final String shutdown = method(source,
                "protected void beforeDisableINEndpoint()");
        require(shutdown.substring(0, shutdown.indexOf("{")).contains(
                "throws HardwareInterfaceException"),
                "DAViS pre-disable hook preserves checked failure");
        final String compact = shutdown.replaceAll("\\s+", " ");
        final int beginDrain = compact.indexOf(".beginDrain()");
        final int await = compact.indexOf(".awaitQuiescence(");
        require(beginDrain >= 0 && await > beginDrain,
                "DAViS begins its reader drain before awaiting quiescence");

        final String[][] shutoffs = {
            {"FPGA_EXTINPUT", "0", "external input"},
            {"FPGA_IMU", "2", "IMU accelerometer"},
            {"FPGA_IMU", "3", "IMU gyroscope"},
            {"FPGA_IMU", "4", "IMU temperature"},
            {"FPGA_APS", "4", "APS"},
            {"FPGA_DVS", "3", "DVS"},
            {"FPGA_MUX", "1", "timestamp generation"}
        };
        for (final String[] shutoff : shutoffs) {
            final String send = "spiConfigSend(CypressFX3." + shutoff[0]
                    + ", (short) " + shutoff[1] + ", 0)";
            final int at = compact.indexOf(send);
            require(at > beginDrain && at < await,
                    "DAViS disables " + shutoff[2] + " before drain wait");
        }

        final String beforeAwait = compact.substring(0, await);
        require(!beforeAwait.contains(
                "spiConfigSend(CypressFX3.FPGA_MUX, (short) 0, 0)"),
                "event mux remains enabled while payload drains");
        require(!beforeAwait.contains(
                "spiConfigSend(CypressFX3.FPGA_USB, (short) 0, 0)"),
                "FPGA USB output remains enabled while payload drains");

        final int argumentsStart = compact.indexOf("(", await);
        final int argumentsEnd = compact.indexOf(")", argumentsStart);
        final String[] arguments = compact.substring(argumentsStart + 1,
                argumentsEnd).split(",");
        require(arguments.length == 2,
                "quiescence wait has quiet and timeout bounds");
        final long quietMillis = positiveMillisBound(source, arguments[0],
                "quiescence quiet interval");
        final long timeoutMillis = positiveMillisBound(source, arguments[1],
                "quiescence timeout");
        require(timeoutMillis >= quietMillis,
                "quiescence timeout covers at least one quiet interval");

        final int failClosedIf = compact.lastIndexOf("if (!", await);
        final int timeoutThrow = compact.indexOf(
                "throw new HardwareInterfaceException", await);
        final int interrupted = compact.indexOf("InterruptedException", await);
        final int reinterrupt = compact.indexOf(
                "Thread.currentThread().interrupt()", interrupted);
        final int interruptionThrow = compact.indexOf(
                "throw new HardwareInterfaceException", interrupted);
        require(failClosedIf >= 0 && timeoutThrow > await
                && timeoutThrow < interrupted,
                "quiescence timeout fails closed with a checked exception");
        require(interrupted > await && reinterrupt > interrupted
                && interruptionThrow > interrupted,
                "quiescence interruption restores interrupt and fails closed");
        final int finallyDrain = compact.indexOf("finally", interrupted);
        final int endDrain = compact.indexOf(".endDrain()", finallyDrain);
        require(finallyDrain > interrupted && endDrain > finallyDrain,
                "DAViS always ends the reader drain state");

        final String davisReaderHook = method(source,
                "protected void noteCompletedTransfer(");
        require(davisReaderHook.replaceAll("\\s+", " ").contains(
                "completedTransferActualLength = actualLength"),
                "DAViS reader retains the callback length for the same transfer");
        final String davisTranslate = method(source,
                "protected void translateEvents(final ByteBuffer b)");
        final String translateCompact = davisTranslate.replaceAll("\\s+", " ");
        final int classify = translateCompact.indexOf(
                "SciDVSGaerDecoder.containsSourcePayload(b)");
        final int noteClassified = translateCompact.indexOf(
                "quiescentDrain.noteCompletedTransfer(");
        final int decode = translateCompact.indexOf("gaerDecoder.decode(b,");
        require(noteClassified >= 0 && classify > noteClassified && decode > classify,
                "DAViS classifies and records each completed transfer before decoding it");

        Field drainField = null;
        int drainFieldCount = 0;
        for (final Field field : DAViSFX3HardwareInterface.RetinaAEReader.class
                .getDeclaredFields()) {
            if (field.getType().getSimpleName().equals(
                    "Fx3QuiescentDrainBarrier")) {
                drainField = field;
                drainFieldCount++;
            }
        }
        require(drainFieldCount == 1,
                "each DAViS reader owns exactly one quiescent drain barrier");
        require(drainField != null && Modifier.isFinal(drainField.getModifiers()),
                "per-reader quiescent drain barrier is final");
    }

    private static void testStartupTimestampResetBarrier(final String source,
            final String cypressSource) {
        final String start = method(source, "public void startAEReader()");
        final int phaseGate = start.indexOf(
                "gaerActive && !gaerPhaseCoordinatorStartingReader");
        final int rejectBypass = start.indexOf(
                "throw new HardwareInterfaceException", phaseGate);
        final int startThread = start.indexOf("reader.startThread()");
        require(phaseGate >= 0 && rejectBypass > phaseGate,
                "direct SciDVS reader startup cannot bypass host phase correction");
        require(startThread > rejectBypass,
                "coordinator-authorized reader starts only after the bypass gate");
        require(!start.contains("resetTimestamps()"),
                "reader startup cannot issue the legacy unchecked timestamp reset");

        final String startupDrain = method(source,
                "private void awaitGaerStartupSourceQuiescence(")
                .replaceAll("\\s+", " ");
        final int beginDrain = startupDrain.indexOf(".beginDrain()");
        final int awaitDrain = startupDrain.indexOf(".awaitQuiescence(");
        final int endDrain = startupDrain.indexOf(".endDrain()");
        require(beginDrain >= 0 && awaitDrain > beginDrain,
                "startup drain begins before bounded quiescence wait");
        require(startupDrain.contains("QUIESCENT_DRAIN_QUIET_MS")
                && startupDrain.contains("STARTUP_SOURCE_DRAIN_TIMEOUT_MS"),
                "startup drain uses finite quiet and diagnostic timeout bounds");
        require(positiveMillisBound(source, "STARTUP_SOURCE_DRAIN_TIMEOUT_MS",
                "startup source-drain timeout") == 3_000L,
                "authorized startup source-drain diagnostic timeout is three seconds");
        require(startupDrain.contains("logStartupSourceDrainTimeline(reader)"),
                "startup drain records completed-transfer timing before reset");
        require(startupDrain.contains("throw new HardwareInterfaceException"),
                "startup drain timeout and interruption fail closed");
        require(endDrain > awaitDrain,
                "startup drain state always ends after the bounded wait");

        final String acquisition = method(source,
                "public synchronized void setEventAcquisitionEnabled(final boolean enable)");
        require(acquisition.contains(
                "gaerPhaseResetCoordinator.execute(new GaerPhaseResetHost())"),
                "SciDVS acquisition delegates startup to the frozen phase-reset coordinator");

        final String clearEndpoint = method(cypressSource,
                "protected final void clearEventEndpointHaltChecked()");
        require(clearEndpoint.contains("aeReader != null")
                && clearEndpoint.contains("LibUsb.clearHalt(handle, AE_MONITOR_ENDPOINT_ADDRESS)"),
                "checked endpoint reset requires reader terminality and targets the event endpoint");
        require(clearEndpoint.contains("status != LibUsb.SUCCESS"),
                "endpoint reset status is checked");

        final String infrastructure = method(cypressSource,
                "protected synchronized void configureINEndpointWithUsbStopped()");
        require(!infrastructure.contains(
                "spiConfigSend(CypressFX3.FPGA_USB, (short) 0, 1)"),
                "fresh-reader infrastructure never releases FPGA USB output");
        require(infrastructure.contains(
                "spiConfigSend(CypressFX3.FPGA_MUX, (short) 1, 1)")
                && infrastructure.contains(
                        "spiConfigSend(CypressFX3.FPGA_MUX, (short) 0, 1)"),
                "fresh-reader infrastructure enables timestamps and event mux while USB stays stopped");

        final String handler = between(source,
                "private void handleGaerTimestampReset()",
                "private void checkMonotonicTimestamp()");
        require(handler.contains("startupTimestampReset.markResetObserved()"),
                "GAER reset callback releases the exact reader barrier");
    }

    private static void testStartupDvsRunGate(final String source) {
        final String acquisition = method(source,
                "public synchronized void setEventAcquisitionEnabled(final boolean enable)");
        final String compact = acquisition.replaceAll("\\s+", " ");
        require(compact.contains(
                "gaerPhaseResetCoordinator.execute(new GaerPhaseResetHost())"),
                "SciDVS acquisition runs the fail-closed phase coordinator");
        require(!compact.contains("super.setEventAcquisitionEnabled(true)"),
                "SciDVS phase correction cannot use endpoint-before-reader base startup");

        final String readRun = method(source,
                "public boolean readDvsRun()").replaceAll("\\s+", " ");
        require(readRun.contains(
                "readConfig(CypressFX3.FPGA_DVS, (short) 3, \"DVS.Run\")"),
                "phase host reads and preserves DVS.Run");
        final String writeRun = method(source,
                "public void writeDvsRun(final boolean run)").replaceAll("\\s+", " ");
        require(writeRun.contains(
                "writeAndVerifyConfig(CypressFX3.FPGA_DVS, (short) 3"),
                "phase host writes DVS.Run through checked readback");
        final String writeUsb = method(source,
                "public void writeUsbRun(final boolean run)").replaceAll("\\s+", " ");
        require(writeUsb.contains(
                "writeAndVerifyConfig(CypressFX3.FPGA_USB, (short) 0"),
                "phase host writes FPGA USB.Run through checked readback");
        final String producers = method(source,
                "public void verifyOtherProducersStopped()");
        require(producers.contains("FPGA_EXTINPUT")
                && producers.contains("FPGA_IMU")
                && producers.contains("FPGA_APS"),
                "phase host verifies every non-DVS producer stopped");
    }

    private static void testTimestampDisorderDiagnostics(final String source)
            throws Exception {
        final Method count = DAViSFX3HardwareInterface.class.getMethod(
                "getGaerNonMonotonicTimestampCount");
        final Method maximum = DAViSFX3HardwareInterface.class.getMethod(
                "getGaerMaxBackwardTimestampUs");
        require(count.getReturnType() == long.class,
                "hardware interface exposes the current epoch disorder count");
        require(maximum.getReturnType() == int.class,
                "hardware interface exposes the largest current epoch decrease");

        final String countBody = method(source,
                "public long getGaerNonMonotonicTimestampCount()");
        final String maximumBody = method(source,
                "public int getGaerMaxBackwardTimestampUs()");
        require(countBody.contains("getNonMonotonicTimestampCount()"),
                "hardware count delegates to the active GAER decoder");
        require(maximumBody.contains("getMaxBackwardTimestampUs()"),
                "hardware maximum delegates to the active GAER decoder");
    }

    private static void testPhaseQualificationWiring(final String source,
            final String cypressSource) throws Exception {
        final Field qualification = DAViSFX3HardwareInterface.RetinaAEReader.class
                .getDeclaredField("gaerPhaseQualification");
        require(qualification.getType() == SciDVSPhaseQualification.class
                && Modifier.isFinal(qualification.getModifiers()),
                "fresh reader owns the exact final phase-qualification generation");

        final String translate = method(source,
                "protected void translateEvents(final ByteBuffer b)");
        final int quarantine = translate.indexOf(
                "gaerPhaseQualification.isQuarantining()");
        final int discardDecode = translate.indexOf(
                "gaerDecoder.decode(b, gaerTimestampResetOnlySink)", quarantine);
        final int completed = translate.indexOf(
                "gaerPhaseQualification.noteCompletedTransfer(", discardDecode);
        final int typedPublication = translate.indexOf(
                "if (isAuthoritativeTypedDelivery())", completed);
        require(quarantine >= 0 && discardDecode > quarantine
                && completed > discardDecode && typedPublication > completed,
                "quarantined callbacks decode into the discard sink and count only after decode before either publication branch");
        final String quarantinePath = translate.substring(quarantine,
                typedPublication);
        require(!quarantinePath.contains("gaerRawSink")
                && !quarantinePath.contains("gaerTypedSink")
                && !quarantinePath.contains("typedBuilder.attach")
                && !quarantinePath.contains("writeBuffer()"),
                "qualification mutates neither raw nor typed publication sinks");

        final String transferFailure = method(source,
                "protected void noteTransferFailure(final int status");
        require(transferFailure.contains(
                "gaerPhaseQualification.noteFailure("),
                "USB transfer errors permanently fail an active qualification");
        final String callback = method(cypressSource,
                "private void processTransferLocked(final RestrictedTransfer transfer)");
        require(callback.contains(
                "noteTransferFailure(transfer.status(), transfer.actualLength())"),
                "base USB callback forwards non-cancelled transfer errors to qualification");

        final String resetHandler = method(source,
                "private void handleGaerTimestampReset()");
        require(resetHandler.contains("gaerPhaseQualification.isActive()")
                && resetHandler.contains(
                        "additional timestamp reset during stream qualification"),
                "an additional reset marker permanently fails stream qualification");

        final String rawAcquire = method(source,
                "public AEPacketRaw acquireAvailableEventsFromDriver()");
        final String typedAcquire = method(source,
                "public PacketBundle acquireAvailablePacketBundle()");
        require(count(rawAcquire,
                "throwIfGaerPhaseQualificationPending()") == 2
                && count(typedAcquire,
                        "throwIfGaerPhaseQualificationPending()") == 2,
                "raw and typed acquisition check quarantine before and after publication");

        final String failClosed = method(source, "public void failClosed()");
        require(failClosed.contains("FPGA_DVS")
                && failClosed.contains("FPGA_USB")
                && failClosed.contains("stopAEReader()")
                && failClosed.contains(
                        "abandonNativeHandleAfterNonterminalReader()"),
                "phase failure stops both run controls and abandons an unproven reader handle");
        final String abandon = method(cypressSource,
                "protected final void abandonNativeHandleAfterNonterminalReader()");
        require(abandon.contains("nativeHandleAbandoned = true")
                && abandon.contains("deviceHandle = null")
                && !abandon.contains("LibUsb."),
                "nonterminal-reader abandonment drops host access without another native USB operation");

        require(source.contains(
                "private static final int PHASE_QUALIFICATION_BYTES = 32_768;")
                && source.contains(
                        "private static final int PHASE_QUALIFICATION_CALLBACKS = 3;")
                && source.contains(
                        "private static final long PHASE_QUALIFICATION_TIMEOUT_MS = 3_000L;"),
                "qualification uses the authorized byte, callback, and timeout bounds");
    }

    private static void testLazyResolutionAndEarlyGaerBranch(final String source) {
        final String translate = between(source,
                "protected void translateEvents(final ByteBuffer b)",
                "public void propertyChange(final PropertyChangeEvent arg0)");
        final int resolver = translate.indexOf(
                "SciDVSGaerMode.resolveFromSystemProperty");
        final int retainedFailureGate = translate.indexOf(
                "gaerTimestampCallbackFailure.get()", resolver);
        final int timestampOrderGate = translate.indexOf(
                "gaerTimestampOrderGuard.validate(b)", retainedFailureGate);
        final int quiescentDrainGate = translate.indexOf(
                "quiescentDrain.noteCompletedTransfer", timestampOrderGate);
        final int typedModeStart = translate.indexOf(
                "if (isAuthoritativeTypedDelivery())");
        final int typedPoolLock = translate.indexOf(
                "synchronized (packetBundlePool)", typedModeStart);
        final int typedAttach = translate.indexOf(
                "typedBuilder.attach(typedOut, getChip(), apsSizeX, apsSizeY)",
                typedPoolLock);
        final int typedBranchStart = translate.indexOf(
                "if (Boolean.TRUE.equals(gaerResolved))", typedAttach);
        final int typedBranchReturn = translate.indexOf("return;", typedBranchStart);
        final int typedModeReturn = translate.indexOf("return;", typedBranchReturn + 1);
        final int rawPoolLock = translate.indexOf(
                "synchronized (aePacketRawPool)", typedModeReturn);
        final int rawBranchStart = translate.indexOf(
                "if (Boolean.TRUE.equals(gaerResolved))", rawPoolLock);
        final int rawBranchReturn = translate.indexOf("return;", rawBranchStart);
        final int standardLoop = translate.indexOf("for (int i = 0; i < sBuf.limit(); i++) {");
        require(resolver >= 0 && retainedFailureGate > resolver
                && timestampOrderGate > retainedFailureGate
                && quiescentDrainGate > timestampOrderGate
                && typedModeStart > quiescentDrainGate,
                "shared GAER resolution, retained-failure, timestamp-order, and drain gates precede both delivery branches");
        require(count(translate, "SciDVSGaerMode.resolveFromSystemProperty") == 1
                && count(translate, "gaerResolved == null") == 1,
                "typed and raw delivery share one nullable GAER resolution gate");
        require(typedModeStart >= 0, "translateEvents has an authoritative typed delivery branch");
        require(typedPoolLock > typedModeStart && typedAttach > typedPoolLock,
                "authoritative typed delivery locks its own pool before attaching geometry");
        require(typedBranchStart > typedAttach,
                "typed GAER decoding follows typed metadata and builder attachment");
        require(rawPoolLock > typedModeReturn && rawBranchStart > rawPoolLock,
                "legacy raw GAER decoding is isolated after the typed branch returns");

        final String typedDelivery = translate.substring(typedModeStart, rawPoolLock);
        final String typedGaerBranch = translate.substring(
                typedBranchStart, typedBranchReturn + "return;".length());
        require(typedDelivery.contains("prepareAuthoritativeTypedBundle(typedOut)"),
                "authoritative typed delivery begins metadata on its own write bundle");
        require(typedDelivery.contains(
                "typedBuilder.attach(typedOut, getChip(), apsSizeX, apsSizeY)"),
                "authoritative typed delivery attaches actual geometry on its own branch");
        require(typedGaerBranch.contains("gaerDecoder.decode(b, gaerTypedSink)"),
                "authoritative typed GAER delivery decodes only into the typed sink");
        require(count(typedGaerBranch, "typedBuilder.flushAll()") == 1,
                "authoritative typed GAER delivery flushes exactly once before return");
        require(typedBranchReturn > typedBranchStart
                && typedModeReturn > typedBranchReturn
                && typedModeReturn < rawPoolLock && typedModeReturn < standardLoop,
                "typed GAER and standard typed routes return before raw delivery and the legacy loop");
        require(!typedDelivery.contains("gaerRawSink")
                && !typedDelivery.contains("AEPacketRaw")
                && !typedDelivery.contains("setRawPacket"),
                "authoritative typed delivery invokes no raw sink, raw packet, or raw sidecar");

        final String rawDelivery = translate.substring(rawPoolLock, standardLoop);
        final String rawGaerBranch = translate.substring(
                rawBranchStart, rawBranchReturn + "return;".length());
        require(rawGaerBranch.contains("gaerRawSink.begin(buffer, eventCounter)"),
                "legacy raw GAER delivery begins the raw sink at the existing cursor");
        require(rawGaerBranch.contains("gaerDecoder.decode(b, gaerRawSink)"),
                "legacy raw GAER delivery decodes only into the raw sink");
        require(rawGaerBranch.contains("eventCounter = gaerRawSink.end()"),
                "legacy raw GAER delivery adopts the raw sink final cursor");
        require(rawBranchReturn > rawBranchStart && rawBranchReturn < standardLoop,
                "legacy raw GAER delivery returns before the unchanged standard DAVIS loop");
        require(!rawDelivery.contains("packetBundlePool")
                && !rawDelivery.contains("typedBuilder")
                && !rawDelivery.contains("gaerTypedSink"),
                "legacy raw delivery never mutates the typed pool or typed builder");
        require(!translate.contains("typedOut != null ? gaerTypedSink : gaerRawSink"),
                "typed and legacy raw GAER delivery never use ternary sink selection");
    }

    private static void testStandardDavisLoopHash(final String source) throws Exception {
        final String first = "                for (int i = 0; i < sBuf.limit(); i++) {";
        final String last = "                } // end loop over usb data buffer";
        final int start = source.indexOf(first);
        final int end = source.indexOf(last, start);
        require(start >= 0 && end >= start, "standard DAViS loop source anchors exist");
        final String loop = source.substring(start, end + last.length()) + "\n";
        require(STANDARD_LOOP_SHA256.equals(sha256(loop)),
                "standard DAViS loop hash remains " + STANDARD_LOOP_SHA256);
    }

    private static String method(final String source, final String signature) {
        final int start = source.indexOf(signature);
        require(start >= 0, "source method exists: " + signature);
        final int openingBrace = source.indexOf("{", start);
        require(openingBrace > start, "source method opens: " + signature);
        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            final char item = source.charAt(i);
            if (item == "{".charAt(0)) {
                depth++;
            } else if (item == "}".charAt(0) && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("unterminated source method: " + signature);
    }

    private static long positiveMillisBound(final String source,
            final String argument, final String description) {
        final String token = argument.trim();
        final String literal = token.replace("_", "").replaceAll("[lL]$", "");
        long value;
        if (literal.matches("[0-9]+")) {
            value = Long.parseLong(literal);
        } else {
            final String compact = source.replaceAll("\\s+", " ");
            final Matcher matcher = Pattern.compile(
                    "(?:private |protected |public )?static final (?:long|int) "
                    + Pattern.quote(token) + " = ([0-9][0-9_]*)[lL]?;")
                    .matcher(compact);
            require(matcher.find(), description + " is a finite millisecond constant");
            value = Long.parseLong(matcher.group(1).replace("_", ""));
        }
        require(value > 0, description + " is positive");
        return value;
    }

    private static void testTypedAuthorityDoesNotAcquireOrPublishRaw(final String davisSource)
            throws Exception {
        final String cypressSource = Files.readString(CYPRESS_SOURCE, StandardCharsets.UTF_8);
        final String acquire = between(cypressSource,
                "public PacketBundle acquireAvailablePacketBundle()",
                "public PacketBundle getLastPacketBundle()");
        require(!acquire.contains("acquireAvailableEventsFromDriver()"),
                "typed acquisition does not delegate to raw acquisition");
        require(!acquire.contains("aePacketRawPool.swap()"),
                "typed acquisition does not swap the raw packet pool");
        require(acquire.contains("packetBundlePool.swap()"),
                "typed acquisition swaps the typed bundle pool");

        final int selectTyped = acquire.indexOf(
                "selectDeliveryMode(DeliveryMode.AUTHORITATIVE_TYPED)");
        final int enableAcquisition = acquire.indexOf("setEventAcquisitionEnabled(true)");
        final int beginMetadata = acquire.indexOf(
                "prepareAuthoritativeTypedBundle(packetBundlePool.writeBuffer())");
        final int swapTyped = acquire.indexOf("packetBundlePool.swap()");
        final int publishTyped = acquire.indexOf(
                "lastPacketBundle = packetBundlePool.readBuffer()");
        require(selectTyped >= 0 && selectTyped < enableAcquisition,
                "typed delivery mode is selected before acquisition is enabled");
        require(beginMetadata > enableAcquisition && swapTyped > beginMetadata
                && publishTyped > swapTyped,
                "typed metadata, sealing swap, and publication retain strict source order");

        final String poolSource = Files.readString(
                PACKET_BUNDLE_POOL_SOURCE, StandardCharsets.UTF_8);
        final String poolSwap = between(poolSource,
                "public final synchronized void swap()",
                "public final synchronized PacketBundle readBuffer()");
        final int sealCompletedWrite = poolSwap.indexOf("completedWrite.seal()");
        final int publishCompletedWrite = poolSwap.indexOf("if (readBuffer == 0)");
        require(sealCompletedWrite >= 0 && publishCompletedWrite > sealCompletedWrite,
                "typed pool seals the completed write bundle before publishing it");

        final String translate = between(davisSource,
                "protected void translateEvents(final ByteBuffer b)",
                "public void propertyChange(final PropertyChangeEvent arg0)");
        require(!translate.contains("typedOut.setRawPacket"),
                "typed decoding never attaches a raw sidecar");
        require(!translate.contains("typedOut != null ? gaerTypedSink : gaerRawSink"),
                "typed and legacy raw GAER decoding are mutually exclusive branches");
    }

    private static String between(final String source, final String first,
            final String second) {
        final int start = source.indexOf(first);
        final int end = source.indexOf(second, start);
        require(start >= 0, "source anchor exists: " + first);
        require(end > start, "source end anchor exists: " + second);
        return source.substring(start, end);
    }

    private static int count(final String value, final String needle) {
        int result = 0;
        int at = 0;
        while ((at = value.indexOf(needle, at)) >= 0) {
            result++;
            at += needle.length();
        }
        return result;
    }

    private static String sha256(final String value) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        final StringBuilder hex = new StringBuilder(digest.length * 2);
        for (final byte item : digest) {
            hex.append(String.format("%02x", item & 0xff));
        }
        return hex.toString();
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
