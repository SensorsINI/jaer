/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;

/**
 * Interfaces to Jorg Conradt's 6-DOF serial port FTDI servo controller used for head.
 * @author tobi
 */
public class SerialFTDIServoInterface implements ServoInterface {

    @Override
    public void setServoValue(int servo, float value) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void disableServo(int servo) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getNumServos() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void disableAllServos() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setAllServoValues(float[] values) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float[] getLastServoValues() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float getLastServoValue(int servo) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setPort2(int portValue) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setFullDutyCycleMode(boolean yes) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isFullDutyCycleMode() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float setServoPWMFrequencyHz(float freq) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getTypeName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void open() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
