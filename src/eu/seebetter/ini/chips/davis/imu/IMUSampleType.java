package eu.seebetter.ini.chips.davis.imu;

import eu.seebetter.ini.chips.DavisChip;

/** Defines MPU-6150 sample types and bit encodings. Enums are ordered as in sensor output I2C address space. */
public enum IMUSampleType {

    ax("AccelX", 0), ay("AccelY", 1), az("AccelZ", 2), temp("Temperature", 3), gx("GyroTiltX", 4), gy("GyroPanY", 5), gz("GyroRollZ", 6); 
    public final String name;
    /** code for this sample type used in raw address encoding in AEPacketRaw events and index into data array containing raw samples */
    public final int code;
    
    /** The actual bits to code the IMU sample type shifted to the correct positions in the 32-bit address and including the DavisChip.ADDRESS_TYPE_IMU */
    public final int codeBits;

    IMUSampleType(String name, int code) {
        this.name = name;
        this.code=code;
        this.codeBits=(code<<(IMUSample.CODEBITSHIFT))|DavisChip.ADDRESS_TYPE_IMU;
    }

}