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
    private static final Path PACKET_BUNDLE_POOL_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "event", "PacketBundlePool.java");
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
        require(translate.contains("if (gaerResolved != null && gaerResolved)"),
                "active GAER branch is null-safe so an unknown chip falls through and later retries");
    }

    private static void testLazyResolutionAndEarlyGaerBranch(final String source) {
        final String translate = between(source,
                "protected void translateEvents(final ByteBuffer b)",
                "public void propertyChange(final PropertyChangeEvent arg0)");
        final int typedModeStart = translate.indexOf(
                "if (isAuthoritativeTypedDelivery())");
        final int typedPoolLock = translate.indexOf(
                "synchronized (packetBundlePool)", typedModeStart);
        final int typedAttach = translate.indexOf(
                "typedBuilder.attach(typedOut, getChip(), apsSizeX, apsSizeY)",
                typedPoolLock);
        final int typedResolver = translate.indexOf(
                "SciDVSGaerMode.resolveFromSystemProperty", typedAttach);
        final int typedBranchStart = translate.indexOf(
                "if (gaerResolved != null && gaerResolved)", typedResolver);
        final int typedBranchReturn = translate.indexOf("return;", typedBranchStart);
        final int typedModeReturn = translate.indexOf("return;", typedBranchReturn + 1);
        final int rawPoolLock = translate.indexOf(
                "synchronized (aePacketRawPool)", typedModeReturn);
        final int rawResolver = translate.indexOf(
                "SciDVSGaerMode.resolveFromSystemProperty", rawPoolLock);
        final int rawBranchStart = translate.indexOf(
                "if (gaerResolved != null && gaerResolved)", rawResolver);
        final int rawBranchReturn = translate.indexOf("return;", rawBranchStart);
        final int standardLoop = translate.indexOf("for (int i = 0; i < sBuf.limit(); i++) {");
        require(typedModeStart >= 0, "translateEvents has an authoritative typed delivery branch");
        require(typedPoolLock > typedModeStart && typedAttach > typedPoolLock,
                "authoritative typed delivery locks its own pool before attaching geometry");
        require(typedResolver > typedAttach && typedBranchStart > typedResolver,
                "typed GAER resolution follows typed metadata and builder attachment");
        require(rawPoolLock > typedModeReturn && rawResolver > rawPoolLock
                && rawBranchStart > rawResolver,
                "legacy raw GAER resolution is isolated after the typed branch returns");
        require(count(translate, "gaerModeUnresolved && getChip() != null") == 2,
                "both mutually exclusive GAER branches retain the chip-nonnull lazy guard");
        require(count(translate, "gaerResolved == null") == 2,
                "both mutually exclusive GAER branches retain nullable unresolved state");

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
