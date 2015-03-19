/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis.imu;

import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisDisplayConfigInterface;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import eu.seebetter.ini.chips.davis.imu.ImuAccelScale;
import eu.seebetter.ini.chips.davis.imu.ImuGyroScale;
import java.util.Observable;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.PropertyTooltipSupport;

/**
 * IMU control of Invensense IMU-6100A, encapsulated here to control via DavisConfig object that contains the low level control registers.
 */
public class ImuControl extends Observable implements HasPropertyTooltips, Biasgen.HasPreference, PreferenceChangeListener {
    PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();
    private ImuGyroScale imuGyroScale;
    private ImuAccelScale imuAccelScale;
    private boolean displayImuEnabled;
    private final DavisConfig davisConfig;
    private final DavisBaseCamera davisChip;

    public ImuControl(final DavisConfig davisConfig) {
        super();
        this.davisConfig = davisConfig;
        this.davisChip=(DavisBaseCamera)davisConfig.getChip();
        // imu0PowerMgmtClkRegConfig.addObserver(this);
        // imu1DLPFConfig.addObserver(this);
        // imu2SamplerateDividerConfig.addObserver(this);
        // imu3GyroConfig.addObserver(this);
        // imu4AccelConfig.addObserver(this);
        // TODO awkward renaming of properties here due to wrongly named delegator methods
        davisConfig.getHasPreferenceList().add(this);
        loadPreference();
        tooltipSupport.setPropertyTooltip("imu0", davisConfig.imu0PowerMgmtClkRegConfig.getDescription());
        tooltipSupport.setPropertyTooltip("imu1", davisConfig.imu1DLPFConfig.getDescription());
        tooltipSupport.setPropertyTooltip("imu2", davisConfig.imu2SamplerateDividerConfig.getDescription());
        tooltipSupport.setPropertyTooltip("imu3", davisConfig.imu3GyroConfig.getDescription());
        tooltipSupport.setPropertyTooltip("imu4", davisConfig.imu4AccelConfig.getDescription());
        IMUSample.setFullScaleGyroDegPerSec(imuGyroScale.fullScaleDegPerSec);
        IMUSample.setGyroSensitivityScaleFactorDegPerSecPerLsb(1 / imuGyroScale.scaleFactorLsbPerDegPerSec);
        IMUSample.setFullScaleAccelG(imuAccelScale.fullScaleG);
        IMUSample.setAccelSensitivityScaleFactorGPerLsb(1 / imuAccelScale.scaleFactorLsbPerG);
        davisConfig.getChip().getPrefs().addPreferenceChangeListener(this);
    }

    public boolean isImuEnabled() {
        return (davisConfig.miscControlBits.get() & 1) == 1;
    }

    public void setImuEnabled(boolean yes) {
        boolean old = (davisConfig.miscControlBits.get() & 1) == 1;
        int oldval = davisConfig.miscControlBits.get();
        int newval = (oldval & (~1)) | (yes ? 1 : 0);
        davisConfig.miscControlBits.set(newval);
        davisConfig.getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_IMU_ENABLED, old, yes);
    }

    /**
     * Register 26: CONFIG, digital low pass filter setting DLPF_CFG
     */
    public void setDLPF(int dlpf) {
        if ((dlpf < 0) || (dlpf > 6)) {
            throw new IllegalArgumentException("dlpf=" + dlpf + " is outside allowed range 0-6");
        }
        int old = davisConfig.imu1DLPFConfig.get() & 7;
        int oldval = davisConfig.imu1DLPFConfig.get();
        int newval = (oldval & (~7)) | (dlpf);
        davisConfig.imu1DLPFConfig.set(newval);
        activateNewRegisterValues();
        davisConfig.getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_IMU_DLPF_CHANGED, old, dlpf);
    }

    public int getDLPF() {
        return davisConfig.imu1DLPFConfig.get() & 7;
    }

    /**
     * Register 27: Sample rate divider
     */
    public void setSampleRateDivider(int srd) {
        if ((srd < 0) || (srd > 255)) {
            throw new IllegalArgumentException("sampleRateDivider=" + srd + " is outside allowed range 0-255");
        }
        int old = davisConfig.imu2SamplerateDividerConfig.get();
        davisConfig.imu2SamplerateDividerConfig.set(srd & 255);
        activateNewRegisterValues();
        davisConfig.getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_IMU_SAMPLE_RATE_CHANGED, old, srd);
    }

    public int getSampleRateDivider() {
        return davisConfig.imu2SamplerateDividerConfig.get();
    }

    public ImuGyroScale getGyroScale() {
        return imuGyroScale;
    }

    public void setGyroScale(ImuGyroScale scale) {
        ImuGyroScale old = this.imuGyroScale;
        this.imuGyroScale = scale;
        setFS_SEL(imuGyroScale.fs_sel);
        IMUSample.setFullScaleGyroDegPerSec(imuGyroScale.fullScaleDegPerSec);
        IMUSample.setGyroSensitivityScaleFactorDegPerSecPerLsb(1 / imuGyroScale.scaleFactorLsbPerDegPerSec);
        davisConfig.getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_IMU_GYRO_SCALE_CHANGED, old, this.imuGyroScale);
    }

    /**
     * @return the imuAccelScale
     */
    public ImuAccelScale getAccelScale() {
        return imuAccelScale;
    }

    /**
     * @param imuAccelScale the imuAccelScale to set
     */
    public void setAccelScale(ImuAccelScale scale) {
        ImuAccelScale old = this.imuAccelScale;
        this.imuAccelScale = scale;
        setAFS_SEL(imuAccelScale.afs_sel);
        IMUSample.setFullScaleAccelG(imuAccelScale.fullScaleG);
        IMUSample.setAccelSensitivityScaleFactorGPerLsb(1 / imuAccelScale.scaleFactorLsbPerG);
        davisConfig.getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_IMU_ACCEL_SCALE_CHANGED, old, this.imuAccelScale);
    }

    // accel scale bits
    private void setAFS_SEL(int val) {
        // AFS_SEL bits are bits 4:3 in accel register
        if ((val < 0) || (val > 3)) {
            throw new IllegalArgumentException("value " + val + " is outside range 0-3");
        }
        int oldval = davisConfig.imu4AccelConfig.get();
        int newval = (oldval & (~(3 << 3))) | (val << 3);
        setImu4(newval);
    }

    // gyro scale bits
    private void setFS_SEL(int val) {
        // AFS_SEL bits are bits 4:3 in accel register
        if ((val < 0) || (val > 3)) {
            throw new IllegalArgumentException("value " + val + " is outside range 0-3");
        }
        int oldval = davisConfig.imu3GyroConfig.get();
        int newval = (oldval & (~(3 << 3))) | (val << 3);
        setImu3(newval);
    }

    public boolean isDisplayImu() {
        return displayImuEnabled;
    }

    public void setDisplayImu(boolean yes) {
        boolean old = this.displayImuEnabled;
        this.displayImuEnabled = yes;
        davisConfig.getSupport().firePropertyChange(DavisDisplayConfigInterface.PROPERTY_IMU_DISPLAY_ENABLED, old, displayImuEnabled);
    }

    private void setImu0(int value) throws IllegalArgumentException {
        davisConfig.imu0PowerMgmtClkRegConfig.set(value);
        activateNewRegisterValues();
    }

    private int getImu0() {
        return davisConfig.imu0PowerMgmtClkRegConfig.get();
    }

    private void setImu1(int value) throws IllegalArgumentException {
        davisConfig.imu1DLPFConfig.set(value);
        activateNewRegisterValues();
    }

    private int getImu1() {
        return davisConfig.imu1DLPFConfig.get();
    }

    private void setImu2(int value) throws IllegalArgumentException {
        davisConfig.imu2SamplerateDividerConfig.set(value);
        activateNewRegisterValues();
    }

    private int getImu2() {
        return davisConfig.imu2SamplerateDividerConfig.get();
    }

    private void setImu3(int value) throws IllegalArgumentException {
        davisConfig.imu3GyroConfig.set(value);
        activateNewRegisterValues();
    }

    private int getImu3() {
        return davisConfig.imu3GyroConfig.get();
    }

    private void setImu4(int value) throws IllegalArgumentException {
        davisConfig.imu4AccelConfig.set(value);
        activateNewRegisterValues();
    }

    private int getImu4() {
        return davisConfig.imu4AccelConfig.get();
    }

    private void activateNewRegisterValues() {
        if (isImuEnabled()) {
            setImuEnabled(false);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            setImuEnabled(true);
        }
    }

    @Override
    public String getPropertyTooltip(String propertyName) {
        return tooltipSupport.getPropertyTooltip(propertyName);
    }

    @Override
    public final void loadPreference() {
        try {
            setGyroScale(ImuGyroScale.valueOf(davisChip.getPrefs().get("ImuGyroScale", ImuGyroScale.GyroFullScaleDegPerSec1000.toString())));
            setAccelScale(ImuAccelScale.valueOf(davisChip.getPrefs().get("ImuAccelScale", ImuAccelScale.ImuAccelScaleG8.toString())));
            setDisplayImu(davisConfig.getChip().getPrefs().getBoolean("IMU.displayEnabled", true));
        } catch (Exception e) {
            davisConfig.getChip().getLog().warning(e.toString());
        }
    }

    @Override
    public void storePreference() {
        davisChip.getPrefs().put("ImuGyroScale", imuGyroScale.toString());
        davisChip.getPrefs().put("ImuAccelScale", imuAccelScale.toString());
        davisChip.getPrefs().putBoolean("IMU.displayEnabled", this.displayImuEnabled);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        if (e.getKey().toLowerCase().contains("imu")) {
            davisChip.getLog().info(this + " preferenceChange(): event=" + e + " key=" + e.getKey() + " newValue=" + e.getNewValue());
        }
        if (e.getNewValue() == null) {
            return;
        }
        try {
            // log.info(e.toString());
            switch (e.getKey()) {
                case "ImuAccelScale":
                    setAccelScale(ImuAccelScale.valueOf(e.getNewValue()));
                    break;
                case "ImuGyroScale":
                    setGyroScale(ImuGyroScale.valueOf(e.getNewValue()));
                    break;
                case "IMU.displayEnabled":
                    setDisplayImu(Boolean.valueOf(e.getNewValue()));
            }
        } catch (IllegalArgumentException iae) {
            davisChip.getLog().warning(iae.toString() + ": Preference value=" + e.getNewValue() + " for the preferenc with key=" + e.getKey() + " is not a proper enum for an IMU setting");
        }
    }
    
}
