package net.sf.jaer.hardwareinterface.usb.prophesee.evk4;

import org.usb4java.DeviceHandle;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.prophesee.PropheseeBiases;

/**
 * IMX636 ISSD init/start/stop for EVK4 (port of neuromorphic-drivers prophesee_evk4.rs open/Drop).
 */
public final class Imx636Init {

    private static final int REG_BIAS_PR = 0x1000;
    private static final int REG_BIAS_FO = 0x1004;
    private static final int REG_BIAS_HPF = 0x100C;
    private static final int REG_BIAS_DIFF_ON = 0x1010;
    private static final int REG_BIAS_DIFF = 0x1014;
    private static final int REG_BIAS_DIFF_OFF = 0x1018;
    private static final int REG_BIAS_INV = 0x101C;
    private static final int REG_BIAS_REFR = 0x1020;
    private static final int REG_BIAS_REQPUY = 0x1040;
    private static final int REG_BIAS_REQPUX = 0x1044;
    private static final int REG_BIAS_SENDREQPDY = 0x1048;
    private static final int REG_BIAS_UNKNOWN1 = 0x104C;
    private static final int REG_BIAS_UNKNOWN2 = 0x1050;

    private Imx636Init() {
    }

    public static PropheseeBiases readDefaultBiases(DeviceHandle handle) throws HardwareInterfaceException {
        final PropheseeBiases b = new PropheseeBiases();
        b.pr = Evk4BoardCommand.readRegister(handle, REG_BIAS_PR) & 0xff;
        b.fo = Evk4BoardCommand.readRegister(handle, REG_BIAS_FO) & 0xff;
        b.hpf = Evk4BoardCommand.readRegister(handle, REG_BIAS_HPF) & 0xff;
        b.diffOn = Evk4BoardCommand.readRegister(handle, REG_BIAS_DIFF_ON) & 0xff;
        b.diff = Evk4BoardCommand.readRegister(handle, REG_BIAS_DIFF) & 0xff;
        b.diffOff = Evk4BoardCommand.readRegister(handle, REG_BIAS_DIFF_OFF) & 0xff;
        b.inv = Evk4BoardCommand.readRegister(handle, REG_BIAS_INV) & 0xff;
        b.refr = Evk4BoardCommand.readRegister(handle, REG_BIAS_REFR) & 0xff;
        b.reqpuy = Evk4BoardCommand.readRegister(handle, REG_BIAS_REQPUY) & 0xff;
        b.reqpux = Evk4BoardCommand.readRegister(handle, REG_BIAS_REQPUX) & 0xff;
        b.sendreqpdy = Evk4BoardCommand.readRegister(handle, REG_BIAS_SENDREQPDY) & 0xff;
        b.unknown1 = Evk4BoardCommand.readRegister(handle, REG_BIAS_UNKNOWN1) & 0xff;
        b.unknown2 = Evk4BoardCommand.readRegister(handle, REG_BIAS_UNKNOWN2) & 0xff;
        return b;
    }

    public static void applyBiases(DeviceHandle handle, PropheseeBiases biases) throws HardwareInterfaceException {
        writeBias(handle, REG_BIAS_PR, biases.pr);
        writeBias(handle, REG_BIAS_FO, biases.fo);
        writeBias(handle, REG_BIAS_HPF, biases.hpf);
        writeBias(handle, REG_BIAS_DIFF_ON, biases.diffOn);
        writeBias(handle, REG_BIAS_DIFF, biases.diff);
        writeBias(handle, REG_BIAS_DIFF_OFF, biases.diffOff);
        writeBias(handle, REG_BIAS_INV, biases.inv);
        writeBias(handle, REG_BIAS_REFR, biases.refr);
        writeBias(handle, REG_BIAS_REQPUY, biases.reqpuy);
        writeBias(handle, REG_BIAS_REQPUX, biases.reqpux);
        writeBias(handle, REG_BIAS_SENDREQPDY, biases.sendreqpdy);
        writeBias(handle, REG_BIAS_UNKNOWN1, biases.unknown1);
        writeBias(handle, REG_BIAS_UNKNOWN2, biases.unknown2);
    }

    private static void writeBias(DeviceHandle handle, int address, int idacCtl)
            throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, address, Evk4BoardCommand.encodeBiasValue(idacCtl));
    }

    public static void initializeAndStart(DeviceHandle handle, PropheseeBiases biases)
            throws HardwareInterfaceException {
        Evk4BoardCommand.readFirmwareInfo(handle);
        Evk4BoardCommand.runDeviceDiscoveryHandshake(handle);

        final PropheseeBiases chipBiases = readDefaultBiases(handle);
        if (biases == null) {
            biases = chipBiases;
        }

        issdStop(handle);
        issdDestroy(handle);
        issdInit(handle);
        configureFiltersAndErc(handle);
        Evk4BoardCommand.flushEventEndpoint(handle);
        Evk4BoardCommand.request(handle, new byte[] { 0x72, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
        applyBiases(handle, biases);
        issdStart(handle);
    }

    public static void shutdown(DeviceHandle handle) {
        try {
            Evk4BoardCommand.writeRegister(handle, 0x000C, packLifoCtrl(0, 0, 0));
            Evk4BoardCommand.writeRegister(handle, 0x0074, packIphMirrCtrl(0, 0));
            Evk4BoardCommand.writeRegister(handle, 0x005C, 0x80023);
            Evk4BoardCommand.writeRegister(handle, 0x004C, 0x000006C8);
            issdStop(handle);
            issdDestroy(handle);
        } catch (HardwareInterfaceException e) {
            // best effort on close
        }
    }

    private static void issdStop(DeviceHandle handle) throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, 0x0004, packRoiCtrl(1, 0, 1, 0, 0x1e000a));
        Evk4BoardCommand.writeRegister(handle, 0x002C, 0x0022c324);
        Evk4BoardCommand.writeRegister(handle, 0x9028, packRoCtrl(0, 1, 0));
        sleepMs(1);
        Evk4BoardCommand.writeRegister(handle, 0x9008, packTimeBaseCtrl(0, 0, 1, 0, 0x64));
        Evk4BoardCommand.writeRegister(handle, 0xB000, 0x000002f8);
        sleepUs(300);
    }

    private static void issdDestroy(DeviceHandle handle) throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, 0x0070, 0x00400008);
        Evk4BoardCommand.writeRegister(handle, 0x006C, 0x0ee47114);
        sleepUs(500);
        Evk4BoardCommand.writeRegister(handle, 0xA00C, 0x00020400);
        sleepUs(500);
        Evk4BoardCommand.writeRegister(handle, 0xA010, 0x00008068);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0x1104, 0x00000000);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA020, 0x00000050);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA004, 0x000b0500);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA008, 0x00002404);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA000, 0x000b0500);
        Evk4BoardCommand.writeRegister(handle, 0xB044, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB004, 0x0000000a);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x0000000e);
        Evk4BoardCommand.writeRegister(handle, 0xB0C8, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x00000006);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x00000004);
        Evk4BoardCommand.writeRegister(handle, 0x0000, 0x4f006442);
        Evk4BoardCommand.writeRegister(handle, 0x0000, 0x0f006442);
        Evk4BoardCommand.writeRegister(handle, 0x00B8, 0x00000401);
        Evk4BoardCommand.writeRegister(handle, 0x00B8, 0x00000400);
        Evk4BoardCommand.writeRegister(handle, 0xB07C, 0x00000000);
    }

    private static void issdInit(DeviceHandle handle) throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, 0x001C, 0x00000001);
        Evk4BoardCommand.writeRegister(handle, 0x400004, 0x00000001);
        sleepSec(1);
        Evk4BoardCommand.writeRegister(handle, 0x400004, 0x00000000);
        sleepMs(500);
        Evk4BoardCommand.writeRegister(handle, 0xB000, 0x00000158);
        sleepSec(1);
        Evk4BoardCommand.writeRegister(handle, 0xB044, 0x00000000);
        sleepUs(300);
        Evk4BoardCommand.writeRegister(handle, 0xB004, 0x0000000a);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB0C8, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0x0000, 0x4f006442);
        Evk4BoardCommand.writeRegister(handle, 0x0000, 0x0f006442);
        Evk4BoardCommand.writeRegister(handle, 0x00B8, 0x00000400);
        Evk4BoardCommand.writeRegister(handle, 0x00B8, 0x00000400);
        Evk4BoardCommand.writeRegister(handle, 0xB07C, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB074, 0x00000002);
        Evk4BoardCommand.writeRegister(handle, 0xB078, 0x000000a0);
        Evk4BoardCommand.writeRegister(handle, 0x00C0, 0x00000110);
        Evk4BoardCommand.writeRegister(handle, 0x00C0, 0x00000210);
        Evk4BoardCommand.writeRegister(handle, 0xB120, 0x00000001);
        Evk4BoardCommand.writeRegister(handle, 0xE120, 0x00000000);
        Evk4BoardCommand.writeRegister(handle, 0xB068, 0x00000004);
        Evk4BoardCommand.writeRegister(handle, 0xB07C, 0x00000001);
        sleepUs(10);
        Evk4BoardCommand.writeRegister(handle, 0xB07C, 0x00000003);
        sleepMs(1);
        Evk4BoardCommand.writeRegister(handle, 0x00B8, 0x00000401);
        Evk4BoardCommand.writeRegister(handle, 0x00B8, 0x00000409);
        Evk4BoardCommand.writeRegister(handle, 0x0000, 0x4f006442);
        Evk4BoardCommand.writeRegister(handle, 0x0000, 0x4f00644a);
        Evk4BoardCommand.writeRegister(handle, 0xB080, 0x00000077);
        Evk4BoardCommand.writeRegister(handle, 0xB084, 0x0000000f);
        Evk4BoardCommand.writeRegister(handle, 0xB088, 0x00000037);
        Evk4BoardCommand.writeRegister(handle, 0xB08C, 0x00000037);
        Evk4BoardCommand.writeRegister(handle, 0xB090, 0x000000df);
        Evk4BoardCommand.writeRegister(handle, 0xB094, 0x00000057);
        Evk4BoardCommand.writeRegister(handle, 0xB098, 0x00000037);
        Evk4BoardCommand.writeRegister(handle, 0xB09C, 0x00000067);
        Evk4BoardCommand.writeRegister(handle, 0xB0A0, 0x00000037);
        Evk4BoardCommand.writeRegister(handle, 0xB0A4, 0x0000002f);
        Evk4BoardCommand.writeRegister(handle, 0xB0AC, 0x00000028);
        Evk4BoardCommand.writeRegister(handle, 0xB0CC, 0x00000001);
        Evk4BoardCommand.writeRegister(handle, 0xB000, 0x000002f8);
        Evk4BoardCommand.writeRegister(handle, 0xB004, 0x0000008a);
        Evk4BoardCommand.writeRegister(handle, 0xB01C, 0x00000030);
        Evk4BoardCommand.writeRegister(handle, 0xB020, 0x00002000);
        Evk4BoardCommand.writeRegister(handle, 0xB02C, 0x000000ff);
        Evk4BoardCommand.writeRegister(handle, 0xB030, 0x00003e80);
        Evk4BoardCommand.writeRegister(handle, 0xB028, 0x00000fa0);
        Evk4BoardCommand.writeRegister(handle, 0xA000, 0x000b0501);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA008, 0x00002405);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA004, 0x000b0501);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xA020, 0x00000150);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x00000007);
        Evk4BoardCommand.writeRegister(handle, 0xB064, 0x00000006);
        Evk4BoardCommand.writeRegister(handle, 0xB040, 0x0000000f);
        sleepUs(100);
        Evk4BoardCommand.writeRegister(handle, 0xB004, 0x0000008a);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xB0C8, 0x00000003);
        sleepUs(200);
        Evk4BoardCommand.writeRegister(handle, 0xB044, 0x00000001);
        Evk4BoardCommand.writeRegister(handle, 0xB000, 0x000002f9);
        Evk4BoardCommand.writeRegister(handle, 0x7008, 0x00000001);
        Evk4BoardCommand.writeRegister(handle, 0x7000, 0x00070001);
        Evk4BoardCommand.writeRegister(handle, 0x8000, 0x0001e085);
        Evk4BoardCommand.writeRegister(handle, 0x9008, packTimeBaseCtrl(0, 0, 1, 0, 0x64));
        Evk4BoardCommand.writeRegister(handle, 0x0004, packRoiCtrl(1, 0, 1, 0, 0x1e000a));
        Evk4BoardCommand.writeRegister(handle, 0x0018, 0x00000200);
        writeBias(handle, REG_BIAS_DIFF, 0x4d);
        Evk4BoardCommand.writeRegister(handle, 0x9004, 0);
        sleepMs(1);
        Evk4BoardCommand.writeRegister(handle, 0x9000, 0x00000200);
        initThermometerAndLifo(handle);
    }

    private static void initThermometerAndLifo(DeviceHandle handle) throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, 0x004C, 0x000006C8);
        Evk4BoardCommand.writeRegister(handle, 0x004C, 0x000006C9);
        Evk4BoardCommand.writeRegister(handle, 0x0054, 0x00000842);
        sleepUs(100);
        Evk4BoardCommand.writeRegister(handle, 0x005C, 0x00080022);
        Evk4BoardCommand.writeRegister(handle, 0x005C, 0x00080023);
        sleepUs(100);
        Evk4BoardCommand.writeRegister(handle, 0x004C, 0x000006C8);
        Evk4BoardCommand.writeRegister(handle, 0x004C, 0x000006C9);
        Evk4BoardCommand.writeRegister(handle, 0x0054, 0x00000884);
        Evk4BoardCommand.writeRegister(handle, 0x0074, packIphMirrCtrl(0, 1));
        sleepUs(10);
        Evk4BoardCommand.writeRegister(handle, 0x0074, packIphMirrCtrl(1, 1));
        sleepUs(20);
        Evk4BoardCommand.writeRegister(handle, 0x000C, packLifoCtrl(1, 0, 0));
        sleepUs(5);
        Evk4BoardCommand.writeRegister(handle, 0x000C, packLifoCtrl(1, 1, 0));
        Evk4BoardCommand.writeRegister(handle, 0x000C, packLifoCtrl(1, 1, 1));
    }

    private static void configureFiltersAndErc(DeviceHandle handle) throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, 0xC008, 0x00089c0f);
        Evk4BoardCommand.writeRegister(handle, 0xC000, 0x00000005);
        Evk4BoardCommand.writeRegister(handle, 0xD00C, 0x0001020d);
        Evk4BoardCommand.writeRegister(handle, 0xD004, 0x013005c8);
        Evk4BoardCommand.writeRegister(handle, 0xD008, 0x013186a0);
        Evk4BoardCommand.writeRegister(handle, 0xD0C0, 0x01180004);
        Evk4BoardCommand.writeRegister(handle, 0xD0C4, 0);
        Evk4BoardCommand.writeRegister(handle, 0xD000, 0x00000005);
        Evk4BoardCommand.writeRegister(handle, 0x6000, 0x00155400);
        Evk4BoardCommand.writeRegister(handle, 0x6004, 0);
        Evk4BoardCommand.writeRegister(handle, 0x6028, 0x00000001);
        Evk4BoardCommand.writeRegister(handle, 0x602C, 0x00000001);
        for (int offset = 0; offset < 230; offset++) {
            Evk4BoardCommand.writeRegister(handle, 0x6800 + offset * 4, 0x08080808);
        }
        Evk4BoardCommand.writeRegister(handle, 0x602C, 0x00000002);
        for (int offset = 0; offset < 256; offset++) {
            Evk4BoardCommand.writeRegister(handle, 0x6400 + offset * 4, ((offset * 2 + 1) << 16) | (offset * 2));
        }
        Evk4BoardCommand.writeRegister(handle, 0x6050, 0);
        Evk4BoardCommand.writeRegister(handle, 0x6060, 0);
        Evk4BoardCommand.writeRegister(handle, 0x6070, 0);
        Evk4BoardCommand.writeRegister(handle, 0x6000, 0x00155401);
        Evk4BoardCommand.writeRegister(handle, 0x7004, 0x000018ff);
    }

    private static void issdStart(DeviceHandle handle) throws HardwareInterfaceException {
        Evk4BoardCommand.writeRegister(handle, 0xB000, 0x000002f9);
        Evk4BoardCommand.writeRegister(handle, 0x9028, packRoCtrl(0, 0, 0));
        Evk4BoardCommand.writeRegister(handle, 0x9008, packTimeBaseCtrl(1, 0, 1, 0, 0x64));
        Evk4BoardCommand.writeRegister(handle, 0x002C, 0x0022c724);
        Evk4BoardCommand.writeRegister(handle, 0x0004, packRoiCtrl(1, 0, 1, 1, 0x1e000a));
    }

    private static int packRoiCtrl(int tdEnable, int tdShadowTrigger, int tdRoniNEn, int tdRstn, int reservedHigh) {
        return (tdEnable << 1) | (tdShadowTrigger << 5) | (tdRoniNEn << 6) | (tdRstn << 10) | reservedHigh;
    }

    private static int packRoCtrl(int areaCountEnable, int outputDisable, int keepTimerHigh) {
        return areaCountEnable | (outputDisable << 1) | (keepTimerHigh << 2);
    }

    private static int packTimeBaseCtrl(int enable, int external, int primary, int externalEnable, int reservedHigh) {
        return enable | (external << 1) | (primary << 2) | (externalEnable << 3) | (reservedHigh << 4);
    }

    private static int packLifoCtrl(int lifoEn, int lifoOutEn, int lifoCntEn) {
        return lifoEn | (lifoOutEn << 1) | (lifoCntEn << 2);
    }

    private static int packIphMirrCtrl(int iphMirrEn, int iphMirrAmpEn) {
        return iphMirrEn | (iphMirrAmpEn << 1);
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepUs(long us) {
        sleepMs(Math.max(1, us / 1000));
    }

    private static void sleepSec(long sec) {
        sleepMs(sec * 1000);
    }
}
