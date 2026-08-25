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
    private static final String STANDARD_LOOP_SHA256
            = "350e0256b375ec2bedb793ee6522ea62ed1b1e1344d54caac409437892583be4";
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
        testQuiescentDrainShutdownWiring(source, cypressSource);
        testLazyResolutionAndEarlyGaerBranch(source);
        testStandardDavisLoopHash(source);
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
        require(compact.contains("gaerTypedSink = new SciDVSGaerTypedSink( typedBuilder, gaerRawSink,"),
                "GAER typed sink uses the existing typed builder and raw sink");
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
        require(compact.contains("() -> !usbTypedDemuxActive || dualWriteApsImuAe"),
                "raw sink gate is exactly the dual-write predicate");
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
        require(translate.contains("if (gaerResolved != null && gaerResolved)"),
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
        final int completed = callback.indexOf(
                "transfer.status() == LibUsb.TRANSFER_COMPLETED");
        final int note = callback.indexOf(
                "noteCompletedTransfer(transfer.actualLength())", completed);
        final int translate = callback.indexOf(
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
                ".noteCompletedTransfer(actualLength)"),
                "DAViS reader forwards completed payload length to its barrier");

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
        final String start = between(source,
                "public void startAEReader()",
                "private void getRealClockValues()");
        final int startThread = start.indexOf("reader.startThread()");
        final int resetCommand = start.indexOf("resetTimestamps()");
        final int waitForMarker = start.indexOf("reader.awaitStartupTimestampReset(");
        final int initialPoolAllocation = start.indexOf("allocateAEBuffers()");
        final int postMarkerPoolClear = start.indexOf("allocateAEBuffers()",
                initialPoolAllocation + 1);
        require(startThread >= 0, "new reader starts before startup reset command");
        require(resetCommand > startThread,
                "hardware timestamp reset follows reader start");
        require(waitForMarker > resetCommand,
                "startup waits for the decoded hardware reset marker");
        require(postMarkerPoolClear > waitForMarker,
                "packet pools clear only after the reset marker is observed");
        require(start.contains("abortStartupTimestampReset(reader)"),
                "timeout and interruption abort the startup reader");

        final String acquisition = between(cypressSource,
                "public synchronized void setEventAcquisitionEnabled",
                "public boolean isEventAcquisitionEnabled()");
        final int endpointEnable = acquisition.indexOf("setInEndpointEnabled(enable)");
        final int readerStart = acquisition.indexOf("startAEReader()");
        require(endpointEnable >= 0 && readerStart > endpointEnable,
                "input endpoint is configured before the reader reset barrier runs");

        final String handler = between(source,
                "private void handleGaerTimestampReset()",
                "private void checkMonotonicTimestamp()");
        require(handler.contains("startupTimestampReset.markResetObserved()"),
                "GAER reset callback releases the exact reader barrier");
    }

    private static void testLazyResolutionAndEarlyGaerBranch(final String source) {
        final String translate = between(source,
                "protected void translateEvents(final ByteBuffer b)",
                "public void propertyChange(final PropertyChangeEvent arg0)");
        final int resolver = translate.indexOf("SciDVSGaerMode.resolveFromSystemProperty");
        final int branchStart = translate.indexOf("if (gaerResolved");
        final int standardLoop = translate.indexOf("for (int i = 0; i < sBuf.limit(); i++) {");
        require(resolver >= 0, "translateEvents lazily invokes the production mode resolver");
        require(branchStart > resolver, "GAER branch follows lazy resolution");
        require(standardLoop > branchStart, "GAER branch is before the standard DAViS loop");
        require(translate.substring(0, resolver).contains("gaerResolved == null"),
                "mode resolver is guarded by nullable unresolved state");

        final int branchReturn = translate.indexOf("return;", branchStart);
        require(branchReturn > branchStart && branchReturn < standardLoop,
                "GAER branch returns before standard DAViS parsing");
        final String branch = translate.substring(branchStart, branchReturn + "return;".length());
        require(translate.substring(0, branchStart).contains(
                "typedBuilder.attach(typedOut, getChip(), apsSizeX, apsSizeY)"),
                "shared prologue attaches actual typed bundle geometry before GAER routing");
        require(branch.contains("gaerRawSink.begin(buffer, eventCounter)"),
                "GAER branch begins the actual raw sink at existing cursor");
        require(branch.replaceAll("\\s+", " ").contains(
                "gaerDecoder.decode(b, typedOut != null ? gaerTypedSink : gaerRawSink)"),
                "GAER branch selects typed composite or raw sink for one decoder pass");
        require(branch.contains("eventCounter = gaerRawSink.end()"),
                "GAER branch adopts raw sink final cursor");
        require(count(branch, "typedBuilder.flushAll()") == 1,
                "GAER branch flushes typed packets exactly once");
        require(count(branch, "typedOut.setRawPacket(buffer)") == 1,
                "GAER branch links raw packet exactly once");
        require(!branch.contains("for (int i = 0; i < sBuf.limit(); i++)"),
                "GAER branch does not duplicate the DAViS parser loop");
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
