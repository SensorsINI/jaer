/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis.imu;

/**
* Defines allowed scales for IMU accelerometer.
 * 
 * These values are associated with Invensense PS-MPU-6100A capabilities.
 * @author tobi
 * 
*/
public enum ImuAccelScale {

    ImuAccelScaleG2(2, 0, 16384), ImuAccelScaleG4(4, 1, 8192), ImuAccelScaleG8(8, 2, 4096), ImuAccelScaleG16(16, 3, 2048);
    public float fullScaleG;

    public int afs_sel;
    public float scaleFactorLsbPerG;
    public String fullScaleString;

    private ImuAccelScale(float fullScaleDegPerSec, int afs_sel, float scaleFactorLsbPerG) {
        this.fullScaleG = fullScaleDegPerSec;
        this.afs_sel = afs_sel;
        this.scaleFactorLsbPerG = scaleFactorLsbPerG;
        fullScaleString = String.format("%.0f g", fullScaleDegPerSec);
    }
}
