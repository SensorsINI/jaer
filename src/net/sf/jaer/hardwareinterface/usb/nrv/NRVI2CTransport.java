package net.sf.jaer.hardwareinterface.usb.nrv;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * I2C register access for NRV DVS devices over USB vendor requests.
 *
 * @see https://nrv.kr/
 */
public interface NRVI2CTransport {

    void writeReg(int slaveAddr, int regAddr, int value) throws HardwareInterfaceException;

    int readReg(int slaveAddr, int regAddr) throws HardwareInterfaceException;
}
