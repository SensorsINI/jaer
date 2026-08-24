package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.sf.jaer.aemonitor.AEPacketRaw;

/** Offline proof that the legacy reader uses the tested production transfer seam. */
public final class SciDVSGaerProductionPathDemo {

    private static int assertions;

    private SciDVSGaerProductionPathDemo() {
    }

    public static void main(final String[] args) throws Exception {
        testProductionTransferSeamAcrossBuffers();
        testRetinaReaderIsAThinStateOwner();
        testLegacyConstructionAndIdentityContracts();
        System.out.println("SCIDVS GAER PRODUCTION ASSERTIONS=" + assertions);
        System.out.println("SCIDVS GAER PRODUCTION PATH PASS");
    }

    private static void testProductionTransferSeamAcrossBuffers() {
        final SciDVSGaerDecoder.Config config = new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                112, 126, false,
                2, 2, false, false, false,
                false, false, false);
        final SciDVSGaerDecoder decoder = new SciDVSGaerDecoder(config);
        final int[] resets = {0};
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> resets[0]++);
        final AEPacketRaw packet = new AEPacketRaw();

        int eventCounter = SciDVSHardwareInterface.decodeGaerTransfer(
                decoder, raw, packet, 0,
                words(0x8064, 0x100A, 0x2011));
        require(eventCounter == 2, "production seam first transfer count");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 2,
                "production seam first capture bounds");

        eventCounter = SciDVSHardwareInterface.decodeGaerTransfer(
                decoder, raw, packet, eventCounter,
                words(0x7003, 0x8005, 0x2002, 0x0001, 0x0002));
        require(eventCounter == 4, "production seam second transfer count");
        require(packet.lastCaptureIndex == 2 && packet.lastCaptureLength == 2,
                "production seam second capture bounds");
        require(packet.getNumEvents() == 4, "production seam final packet count");
        require(Arrays.equals(Arrays.copyOf(packet.getAddresses(), 4),
                new int[]{42397696, 42399744, 42393600, 1026}),
                "production seam exact fixed legacy addresses");
        require(Arrays.equals(Arrays.copyOf(packet.getTimestamps(), 4),
                new int[]{100, 100, 98309, 0}),
                "production seam exact fixed legacy timestamps");
        require(resets[0] == 1, "production seam forwards timestamp reset once");

        final ByteBuffer odd = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        odd.putShort((short) 0x0003).put((byte) 0x55).flip();
        eventCounter = SciDVSHardwareInterface.decodeGaerTransfer(
                decoder, raw, packet, eventCounter, odd);
        require(eventCounter == 5, "production seam odd transfer emits complete word only");
        require(packet.getAddresses()[4] == 1027 && packet.getTimestamps()[4] == 0,
                "production seam odd transfer exact raw event");
        require(packet.lastCaptureIndex == 4 && packet.lastCaptureLength == 1,
                "production seam odd transfer capture bounds");
        require(odd.limit() == 2 && odd.position() == 0,
                "production seam retains legacy caller truncation contract");
    }

    private static void testRetinaReaderIsAThinStateOwner() throws Exception {
        final Class<?> reader = SciDVSHardwareInterface.RetinaAEReader.class;
        final Set<String> fieldNames = new HashSet<>();
        for (final Field field : reader.getDeclaredFields()) {
            fieldNames.add(field.getName());
        }
        require(fieldNames.contains("gaerDecoder"), "reader owns one GAER decoder");
        require(fieldNames.contains("gaerRawSink"), "reader owns one GAER raw sink");

        final String[] movedParserState = {
            "wrapAdd", "lastTimestamp", "currentTimestamp", "dvsLastY",
            "apsCurrentReadoutType", "apsRGBPixelOffset", "apsRGBPixelOffsetDirection",
            "apsCountX", "apsCountY", "imuEvents", "imuType", "imuCount", "imuTmpData"
        };
        for (final String moved : movedParserState) {
            require(!fieldNames.contains(moved), "reader no longer duplicates parser field " + moved);
        }

        final Method translate = reader.getDeclaredMethod("translateEvents", ByteBuffer.class);
        require(Modifier.isProtected(translate.getModifiers()),
                "reader translateEvents remains protected override");
        final Method seam = SciDVSHardwareInterface.class.getDeclaredMethod(
                "decodeGaerTransfer",
                SciDVSGaerDecoder.class, SciDVSGaerRawSink.class,
                AEPacketRaw.class, int.class, ByteBuffer.class);
        require(Modifier.isStatic(seam.getModifiers()) && !Modifier.isPublic(seam.getModifiers()),
                "offline production transfer seam is package-local static");
    }

    private static void testLegacyConstructionAndIdentityContracts() throws Exception {
        require(SciDVSHardwareInterface.PID_FX3 == (short) 0x841A,
                "legacy non-FX10 FX3 PID unchanged");
        require(SciDVSHardwareInterface.PID_FX2 == (short) 0x841B,
                "legacy non-FX10 FX2 PID unchanged");
        require(SciDVSHardwareInterface.REQUIRED_FIRMWARE_VERSION_FX3 == 6,
                "legacy FX3 firmware requirement unchanged");
        require(SciDVSHardwareInterface.REQUIRED_FIRMWARE_VERSION_FX2 == 4,
                "legacy FX2 firmware requirement unchanged");
        require(SciDVSHardwareInterface.REQUIRED_LOGIC_REVISION_FX3 == 18,
                "legacy FX3 logic requirement unchanged");
        require(SciDVSHardwareInterface.REQUIRED_LOGIC_REVISION_FX2 == 18,
                "legacy FX2 logic requirement unchanged");

        final Constructor<?> outer = SciDVSHardwareInterface.class
                .getDeclaredConstructor(org.usb4java.Device.class);
        require(Modifier.isProtected(outer.getModifiers()),
                "outer Device constructor remains protected");
        final Constructor<?> reader = SciDVSHardwareInterface.RetinaAEReader.class
                .getDeclaredConstructor(SciDVSHardwareInterface.class, CypressFX3.class);
        require(Modifier.isPublic(reader.getModifiers()),
                "RetinaAEReader CypressFX3 constructor remains public");
        require(SciDVSHardwareInterface.class.getDeclaredMethod("startAEReader") != null,
                "startAEReader override remains present");
        require(SciDVSHardwareInterface.class.getDeclaredMethod(
                "adjustHWParam", short.class, short.class, int.class) != null,
                "clock-scaling adjustHWParam override remains present");
    }

    private static ByteBuffer words(final int... words) {
        final ByteBuffer buffer = ByteBuffer.allocate(words.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (final int word : words) {
            buffer.putShort((short) word);
        }
        return buffer.flip();
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
