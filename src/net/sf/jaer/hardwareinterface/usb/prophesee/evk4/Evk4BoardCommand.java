package net.sf.jaer.hardwareinterface.usb.prophesee.evk4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.logging.Logger;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Treuzell bulk control for Prophesee EVK4 (port of neuromorphic-drivers request/register I/O).
 */
public final class Evk4BoardCommand {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final byte EP_CONTROL_OUT = 0x02;
    public static final byte EP_CONTROL_IN = (byte) 0x82;
    public static final byte EP_EVENTS_IN = (byte) 0x81;

    private static final int TIMEOUT_MS = 1000;
    private static final int MAX_RESPONSE = 1024;

    private Evk4BoardCommand() {
    }

    public static byte[] request(DeviceHandle handle, byte[] request) throws HardwareInterfaceException {
        final ByteBuffer out = BufferUtils.allocateByteBuffer(request.length);
        out.put(request);
        out.rewind();
        final int written = bulkTransfer(handle, EP_CONTROL_OUT, out, TIMEOUT_MS);
        if (written < 0) {
            throw new HardwareInterfaceException("EVK4 control write: " + LibUsb.errorName(written));
        }
        if (written != request.length) {
            throw new HardwareInterfaceException(String.format(
                    "EVK4 short write: requested %d, wrote %d", request.length, written));
        }
        final ByteBuffer in = BufferUtils.allocateByteBuffer(MAX_RESPONSE);
        final int read = bulkTransfer(handle, EP_CONTROL_IN, in, TIMEOUT_MS);
        if (read < 0) {
            throw new HardwareInterfaceException("EVK4 control read: " + LibUsb.errorName(read));
        }
        final byte[] response = new byte[read];
        in.rewind();
        in.get(response);
        return response;
    }

    private static int bulkTransfer(DeviceHandle handle, byte endpoint, ByteBuffer buffer, long timeoutMs)
            throws HardwareInterfaceException {
        final IntBuffer transferred = BufferUtils.allocateIntBuffer();
        final int status = LibUsb.bulkTransfer(handle, endpoint, buffer, transferred, timeoutMs);
        if (status != LibUsb.SUCCESS) {
            return status;
        }
        return transferred.get(0);
    }

    public static void writeRegister(DeviceHandle handle, int address, int value) throws HardwareInterfaceException {
        final byte[] req = new byte[] {
            0x02, 0x01, 0x01, 0x40, 0x0c, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            (byte) (address & 0xff), (byte) ((address >> 8) & 0xff),
            (byte) ((address >> 16) & 0xff), (byte) ((address >> 24) & 0xff),
            (byte) (value & 0xff), (byte) ((value >> 8) & 0xff),
            (byte) ((value >> 16) & 0xff), (byte) ((value >> 24) & 0xff),
        };
        request(handle, req);
    }

    public static int readRegister(DeviceHandle handle, int address) throws HardwareInterfaceException {
        final byte[] req = new byte[] {
            0x02, 0x01, 0x01, 0x00, 0x0c, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            (byte) (address & 0xff), (byte) ((address >> 8) & 0xff),
            (byte) ((address >> 16) & 0xff), (byte) ((address >> 24) & 0xff),
            0x01, 0x00, 0x00, 0x00,
        };
        final byte[] resp = request(handle, req);
        if (resp.length < 20) {
            throw new HardwareInterfaceException("EVK4 register read short response at 0x"
                    + Integer.toHexString(address));
        }
        return ByteBuffer.wrap(resp, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static int encodeBiasValue(int idacCtl) {
        final int v = idacCtl & 0xff;
        return v | (0x50 << 8) | (1 << 16) | (1 << 21) | (1 << 23) | (1 << 24) | (1 << 28);
    }

    public static String readSerial(DeviceHandle handle) throws HardwareInterfaceException {
        request(handle, new byte[] { 0x72, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
        final byte[] resp = request(handle, new byte[] { 0x72, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
        if (resp.length < 12) {
            return "unknown";
        }
        return String.format("%02X%02X%02X%02X", resp[11] & 0xff, resp[10] & 0xff, resp[9] & 0xff, resp[8] & 0xff);
    }

    public static void readFirmwareInfo(DeviceHandle handle) throws HardwareInterfaceException {
        request(handle, new byte[] { 0x79, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
        request(handle, new byte[] { 0x7a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
    }

    public static void runDeviceDiscoveryHandshake(DeviceHandle handle) throws HardwareInterfaceException {
        request(handle, new byte[] { 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00 });
        request(handle, new byte[] {
            0x03, 0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        });
        request(handle, new byte[] { 0x72, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
        request(handle, new byte[] { 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00 });
        request(handle, new byte[] {
            0x01, 0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        });
        request(handle, new byte[] {
            0x03, 0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        });
    }

    public static void flushEventEndpoint(DeviceHandle handle) {
        final ByteBuffer buf = BufferUtils.allocateByteBuffer(1 << 17);
        while (true) {
            int n;
            try {
                n = bulkTransfer(handle, EP_EVENTS_IN, buf, TIMEOUT_MS);
            } catch (HardwareInterfaceException e) {
                log.fine("EVK4 flush endpoint: " + e.getMessage());
                break;
            }
            if (n <= 0) {
                if (n == LibUsb.ERROR_TIMEOUT) {
                    break;
                }
                if (n < 0) {
                    log.fine("EVK4 flush endpoint: " + LibUsb.errorName(n));
                }
                break;
            }
            buf.rewind();
        }
    }
}
