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
        testExpectedFields();
        testProtectedTranslateAndNoDuplicateGaerParserState();
        testEagerConstructionReusesExistingConfiguration(source);
        testSinkWiringAndLazyHarden(source);
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
                && Modifier.isFinal(fields.get("gaerTypedSink").getModifiers()),
                "GAER decoder and sinks are eager final state owners");
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
