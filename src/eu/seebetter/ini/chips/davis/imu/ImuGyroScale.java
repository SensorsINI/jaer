/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis.imu;

/**
 * Defines allowed scales for IMU rate gyro.
 * 
 * These values are associated with Invensense PS-MPU-6100A capabilities.
 * 
 */
public enum ImuGyroScale {

    GyroFullScaleDegPerSec250(250, 0, 131), GyroFullScaleDegPerSec500(500, 1, 63.5F), GyroFullScaleDegPerSec1000(1000, 2, 32.8F), GyroFullScaleDegPerSec2000(2000, 3, 16.4F);

    public float fullScaleDegPerSec;

    public int fs_sel;
    public float scaleFactorLsbPerDegPerSec;
    public String fullScaleString;

    private ImuGyroScale(float fullScaleDegPerSec, int fs_sel, float scaleFactorLsbPerG) {
        this.fullScaleDegPerSec = fullScaleDegPerSec;
        this.fs_sel = fs_sel;
        this.scaleFactorLsbPerDegPerSec = scaleFactorLsbPerG;
        fullScaleString = String.format("%.0f deg/s", fullScaleDegPerSec);
    }

    public String[] choices() {
        String[] s = new String[values().length];
        for (int i = 0; i < values().length; i++) {
            s[i] = values()[i].fullScaleString;
        }
        return s;
    }
}
