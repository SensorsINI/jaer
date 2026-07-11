package nrv.usb;

import java.nio.ByteBuffer;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * FX20 (PID 0x00F0) I2C via vendor requests 0xBA (write) / 0xAB (read).
 * Write uses a 4-byte payload: slave, addrH, addrL, value.
 *
 * @see https://nrv.kr/
 */
public class NRVI2CFX20Transport implements NRVI2CTransport {

    private static final byte VENDOR_REQUEST_I2C_WRITE = (byte) 0xBA;
    private static final byte VENDOR_REQUEST_I2C_READ = (byte) 0xAB;
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    private final DeviceHandle deviceHandle;

    public NRVI2CFX20Transport(DeviceHandle deviceHandle) {
        this.deviceHandle = deviceHandle;
    }

    @Override
    public void writeReg(int slaveAddr, int regAddr, int value) throws HardwareInterfaceException {
        final ByteBuffer buf = BufferUtils.allocateByteBuffer(4);
        buf.put((byte) (slaveAddr & 0xff));
        buf.put((byte) ((regAddr >> 8) & 0xff));
        buf.put((byte) (regAddr & 0xff));
        buf.put((byte) (value & 0xff));
        buf.rewind();

        final int status = LibUsb.controlTransfer(deviceHandle,
                (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE | LibUsb.ENDPOINT_OUT),
                VENDOR_REQUEST_I2C_WRITE,
                (short) 0,
                (short) 0,
                buf,
                DEFAULT_TIMEOUT_MS);
        if (status < LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("FX20 I2C write failed: " + LibUsb.errorName(status));
        }
    }

    @Override
    public int readReg(int slaveAddr, int regAddr) throws HardwareInterfaceException {
        final ByteBuffer buf = BufferUtils.allocateByteBuffer(1);
        final int status = LibUsb.controlTransfer(deviceHandle,
                (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE | LibUsb.ENDPOINT_IN),
                VENDOR_REQUEST_I2C_READ,
                (short) (slaveAddr & 0xffff),
                (short) (regAddr & 0xffff),
                buf,
                DEFAULT_TIMEOUT_MS);
        if (status < LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("FX20 I2C read failed: " + LibUsb.errorName(status));
        }
        return buf.get(0) & 0xff;
    }
}
