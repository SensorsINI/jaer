package net.sf.jaer.event;

import eu.seebetter.ini.chips.davis.imu.IMUSample;

/**
 * This event class is used in the extractor to hold IMU data from DAVIS cameras so
 * that it can be logged to files and played back here. It adds the ADC sample
 * value and IMU samples. This event has the usual timestamp in us but adds fields for the IMU sample value and IMU sample timestamp
 * (if the event is an IMU sample) and the necessary
 * methods to extract IMU data.
 */
public class ApsDvsEvent extends PolarityEvent {

    /**
     * The readout type of the multiple readouts: ResetRead is the readout of
     * reset level, SignalRead is the readout of first sample, C, is the readout
     * of 2nd sample, etc. Normally only ResetRead and SignalRead are used and
     * the CDS is done in digital domain by subtracting ResetRead-SignalRead
     * readings.
     */
    public enum ReadoutType {
        DVS, ResetRead, SignalRead, SOF, EOF, SOE, EOE, IMU, Null
    };

    /**
     * The ADC sample value. Has value -1 by convention for non-sample events.
     */
    public int adcSample = 0;

    /**
     * This bit determines whether it is the first read (ResetRead) or the
     * second read (SignalRead) of a pixel. Start/end of frame (SOF/EOF) and
     * start/end of exposure (SOE/EOE)
     */
    public ReadoutType readoutType = ReadoutType.Null;

    public ApsDvsEvent() {
    }

    @Override
    public String toString() {
        return super.toString() + ", adcSample=" + adcSample + ", readoutType=" + readoutType.toString();
    }

    /**
     * The ADC sample value.
     *
     * @return the adcSample
     */
    public int getAdcSample() {
        return adcSample;
    }

    /**
     * Sets the ADC sample value.
     *
     * @param adcSample the adcSample to set
     */
    public void setAdcSample(int adcSample) {
        this.adcSample = adcSample;
    }

    /**
     * The readout type (ResetRead,SignalRead,C)
     *
     * @return the readoutType
     */
    public ReadoutType getReadoutType() {
        return readoutType;
    }

    /**
     * Sets the readout type (ResetRead,SignalRead,C)
     *
     * @param readoutType the readoutType to set
     */
    public void setReadoutType(ReadoutType readoutType) {
        this.readoutType = readoutType;
    }

    /**
     * Flags if this sample is from the start of the frame.
     *
     * @param startOfFrame the startOfFrame to set
     */
    public void setStartOfFrame(boolean startOfFrame) {
        if (startOfFrame) {
            this.readoutType = ReadoutType.SOF;
        }
    }

    @Override
    public void copyFrom(BasicEvent src) {
        if (!(src instanceof ApsDvsEvent) && (src instanceof PolarityEvent)) {
            // we want to copy the source PolarityEvent as much as possible to the ApsDvsEvent not including APS fields
            PolarityEvent pe = (PolarityEvent) src;
            super.copyFrom(src);
            adcSample = 0;
            readoutType = ReadoutType.Null;
            setStartOfFrame(false);
        } else {
            ApsDvsEvent e = (ApsDvsEvent) src;
            super.copyFrom(src);
            adcSample = e.getAdcSample();
            readoutType = e.getReadoutType();
            setStartOfFrame(e.isStartOfFrame());
        }
    }

    public void setIsDVS(boolean isDVS) {
        if (isDVS) {
            readoutType = ReadoutType.DVS;
        }else{
            readoutType = ReadoutType.Null;
        }
    }

    /**
     * Returns true if this is an ADC sample from the APS stream
     *
     * @return true if this is an ADC sample from the active pixel sensor imager output
     */
    public boolean isApsData() {
        switch(this.readoutType){
            case EOE:
            case Null:
            case ResetRead:
            case SOE:
            case SOF:
            case EOF:
            case SignalRead:
                return true;
            default:
                return false;
        }
    }
    
    public boolean isImuSample(){
        return readoutType==ReadoutType.IMU;
    }
    
    public boolean isDVSEvent() {
        return readoutType == ReadoutType.DVS;
    }

    public boolean isResetRead() {
        return readoutType == ReadoutType.ResetRead;
    }

    public boolean isSignalRead() {
        return readoutType == ReadoutType.SignalRead;
    }

    /**
     * Flags if this sample is from the start of the frame.
     *
     * @return the startOfFrame
     */
    public boolean isStartOfFrame() {
        return this.readoutType == ReadoutType.SOF;
    }

    public boolean isEndOfFrame() {
        return readoutType == ReadoutType.EOF;
    }

    public boolean isStartOfExposure() {
        return this.readoutType == ReadoutType.SOE;
    }

    public boolean isEndOfExposure() {
        return readoutType == ReadoutType.EOE;
    }

    /**
     * @return the imuSample
     */
    public IMUSample getImuSample() {
        return imuSample;
    }

    /**
     * Sets the associated IMUSample and ReadoutType.IMU. If imuSample==null, then ReadoutType is set to ReadoutType.Null
     * @param imuSample the imuSample to set
     */
    public void setImuSample(IMUSample imuSample) {
        this.imuSample = imuSample;
        if(imuSample!=null) setReadoutType(ReadoutType.IMU); else setReadoutType(ReadoutType.Null);
    }
    
    /** If this DAVIS camera event is an IMUSample then this object will be non null */
    private IMUSample imuSample=null;
    
    
}
