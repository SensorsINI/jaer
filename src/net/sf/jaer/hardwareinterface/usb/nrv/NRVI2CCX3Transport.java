package net.sf.jaer.hardwareinterface.usb.nrv;

import java.nio.ByteBuffer;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * CX3 (PID 0x00F1) I2C via vendor requests 0xBA (write) / 0xAB (read).
 * wValue = slave address, wIndex = register address, 1–2 byte payload.
 */
public class NRVI2CCX3Transport implements NRVI2CTransport {

    private static final byte VENDOR_REQUEST_I2C_WRITE = (byte) 0xBA;
    private static final byte VENDOR_REQUEST_I2C_READ = (byte) 0xAB;
    private static final int I2C_SLAVE_ADDR_DVSL = 0x20;
    private static final int I2C_SLAVE_ADDR_DVSR = 0x30;
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    private final DeviceHandle deviceHandle;

    public NRVI2CCX3Transport(DeviceHandle deviceHandle) {
        this.deviceHandle = deviceHandle;
    }

    private static int i2cValueLen(int slaveAddr) {
        if (slaveAddr == I2C_SLAVE_ADDR_DVSL || slaveAddr == I2C_SLAVE_ADDR_DVSR) {
            return 1;
        }
        return 2;
    }

    @Override
    public void writeReg(int slaveAddr, int regAddr, int value) throws HardwareInterfaceException {
        final int valueLen = i2cValueLen(slaveAddr);
        final ByteBuffer buf = BufferUtils.allocateByteBuffer(valueLen);
        if (valueLen == 1) {
            buf.put((byte) (value & 0xff));
        } else {
            buf.put((byte) ((value >> 8) & 0xff));
            buf.put((byte) (value & 0xff));
        }
        buf.rewind();

        final int status = LibUsb.controlTransfer(deviceHandle,
                (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE | LibUsb.ENDPOINT_OUT),
                VENDOR_REQUEST_I2C_WRITE,
                (short) (slaveAddr & 0xffff),
                (short) (regAddr & 0xffff),
                buf,
                DEFAULT_TIMEOUT_MS);
        if (status < LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("CX3 I2C write failed: " + LibUsb.errorName(status));
        }
    }

    @Override
    public int readReg(int slaveAddr, int regAddr) throws HardwareInterfaceException {
        final int valueLen = i2cValueLen(slaveAddr);
        final ByteBuffer buf = BufferUtils.allocateByteBuffer(valueLen);
        final int status = LibUsb.controlTransfer(deviceHandle,
                (byte) (LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE | LibUsb.ENDPOINT_IN),
                VENDOR_REQUEST_I2C_READ,
                (short) (slaveAddr & 0xffff),
                (short) (regAddr & 0xffff),
                buf,
                DEFAULT_TIMEOUT_MS);
        if (status < LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("CX3 I2C read failed: " + LibUsb.errorName(status));
        }
        buf.rewind();
        if (valueLen == 1) {
            return buf.get(0) & 0xff;
        }
        return ((buf.get(0) & 0xff) << 8) | (buf.get(1) & 0xff);
    }
}
