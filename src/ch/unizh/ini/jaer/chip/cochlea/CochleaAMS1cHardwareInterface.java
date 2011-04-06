/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterface;
import ch.unizh.ini.jaer.chip.util.scanner.ScannerHardwareInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import java.math.BigInteger;
import java.util.prefs.Preferences;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2MonitorSequencer;

/**
 * The hardware interface to CochleaAMS1c.
 * 
 * @author tobi
 */
public class CochleaAMS1cHardwareInterface extends CypressFX2MonitorSequencer implements BiasgenHardwareInterface, ADCHardwareInterface, ScannerHardwareInterface {

    static Preferences hwPrefs = Preferences.userNodeForPackage(CochleaAMS1cHardwareInterface.class); // TODO should really come from Chip instance, not this class
    /** The USB product ID of this device */
    static public final short PID = (short) 0x840A;
    private boolean adcEnabled = hwPrefs.getBoolean("CochleaAMS1cHardwareInterface.adcEnabled", true);
    private short TrackTime = (short) hwPrefs.getInt("CochleaAMS1cHardwareInterface.TrackTime", 50),
            RefOnTime = (short) hwPrefs.getInt("CochleaAMS1cHardwareInterface.RefOnTime", 20),
            RefOffTime = (short) hwPrefs.getInt("CochleaAMS1cHardwareInterface.RefOffTime", 20),
            IdleTime = (short) hwPrefs.getInt("CochleaAMS1cHardwareInterface.IdleTime", 10);
    private boolean UseCalibration = hwPrefs.getBoolean("CochleaAMS1cHardwareInterface.UseCalibration", false);
    private boolean scanContinuouslyEnabled = hwPrefs.getBoolean("CochleaAMS1cHardwareInterface.scanContinuouslyEnabled", true);
    private int scanX = hwPrefs.getInt("CochleaAMS1cHardwareInterface.scanX", 0);
    private int scanY = hwPrefs.getInt("CochleaAMS1cHardwareInterface.scanY", 0);
    private byte ADCchannel = (byte)hwPrefs.getInt("CochleaAMS1cHardwareInterface.ADCchannel", 3);
    private static final int ADCchannelshift = 5;
    private static final short ADCconfig = (short) 0x100;   //normal power mode, single ended, sequencer unused : (short) 0x908;
    private final static short ADCconfigLength = (short) 12;

    final byte VR_CONFIG = CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES;

    public CochleaAMS1cHardwareInterface(int n) {
        super(n);
    }

    /** Sends the vendor request to power down the Masterbias from the Masterbias GUI panel. This powerdown bit is the
     * same as ConfigBit D5 in the CochleaAMS1c.Biasgen but is handled here differently to be backward compatible.
     *
     * @param powerDown true to power down the master bias.
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
     */
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
         if(gUsbIo==null){
            throw new RuntimeException("device must be opened before sending this vendor request");
        }
        USBIO_CLASS_OR_VENDOR_REQUEST vendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
        int result;
        //        System.out.println("sending bias bytes");
        USBIO_DATA_BUFFER dataBuffer=new USBIO_DATA_BUFFER(0); // no data, control is in setupdat
        vendorRequest.Request=VENDOR_REQUEST_POWERDOWN;
        vendorRequest.Type=UsbIoInterface.RequestTypeVendor;
        vendorRequest.Recipient=UsbIoInterface.RecipientDevice;
        vendorRequest.RequestTypeReservedBits=0;
        vendorRequest.Index=0;  // meaningless for this request

        vendorRequest.Value=(short)(powerDown?1:0);  // this is the request bit, if powerDown true, send value 1, false send value 0

        dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        result=gUsbIo.classOrVendorOutRequest(dataBuffer,vendorRequest);
        if(result!= de.thesycon.usbio.UsbIoErrorCodes.USBIO_ERR_SUCCESS ){
            throw new HardwareInterfaceException("setPowerDown: unable to send: "+UsbIo.errorText(result));
        }
        HardwareInterfaceException.clearException();
       
    }

    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (!(biasgen instanceof CochleaAMS1c.Biasgen)) {
            log.warning("biasgen is not instanceof CochleaAMS1c.Biasgen");
            return;
        }
        ((CochleaAMS1c.Biasgen) biasgen).sendConfiguration(); // delegates actual work to Biasgen object
    }

    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        throw new HardwareInterfaceException("Flashing configuration not supported yet.");
    }

    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        if (!(biasgen instanceof CochleaAMS1c.Biasgen)) {
            log.warning(biasgen + " is not instanceof CochleaAMS1c.Biasgen, returning null array");
            return null;
        }
        CochleaAMS1c.Biasgen b = (CochleaAMS1c.Biasgen) biasgen;
        return new byte[0];
    }

        synchronized public void resetTimestamps() {
        try {
            sendVendorRequest(this.VENDOR_REQUEST_RESET_TIMESTAMPS);
           

        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    /** 
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
    our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new AEReader(this));
        allocateAEBuffers();
        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class AEReader extends CypressFX2MonitorSequencer.MonSeqAEReader {

        public AEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        @Override
        protected void translateEvents(UsbIoBuf b) {
            translateEventsWithCPLDEventCode(b);
        }
    }


        public void setTrackTime(short trackTimeUs) {
        try {
            int old=this.TrackTime;
            TrackTime = trackTimeUs;  // TODO bound values here
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.TrackTime", TrackTime);
            getSupport().firePropertyChange(EVENT_TRACK_TIME,old,TrackTime);
            sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setIdleTime(short trackTimeUs) {
        try {
             int old=this.IdleTime;
           IdleTime = trackTimeUs;// TODO bound values here
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.IdleTime", IdleTime);
            getSupport().firePropertyChange(EVENT_IDLE_TIME,old,IdleTime);
            sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setRefOnTime(short trackTimeUs) {
        try {
            int old=this.RefOnTime;
            RefOnTime = trackTimeUs;// TODO bound values here
            sendADCConfiguration();
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.RefOnTime", RefOnTime);
            getSupport().firePropertyChange(EVENT_REF_ON_TIME,old,RefOnTime);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setRefOffTime(short trackTimeUs) {
        try {
            int old=this.RefOffTime;
            RefOffTime = trackTimeUs;// TODO bound values here
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.RefOffTime", RefOffTime);
            getSupport().firePropertyChange(EVENT_REF_OFF_TIME,old,RefOffTime);
            sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void setADCchannel(byte chan) {
        try {
            int old=this.ADCchannel;
            if (chan < 0) {
                chan = 0;
            } else if (chan > 3) {
                chan = 3;
            }
            ADCchannel = chan;
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.ADCchannel", ADCchannel);
         getSupport().firePropertyChange(EVENT_ADC_CHANNEL, old, ADCchannel);
             sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    /**
     * @return the scanContinuouslyEnabled
     */
    @Override
    public boolean isScanContinuouslyEnabled() {
        return scanContinuouslyEnabled;
    }

    /**
     * @param scanContinuouslyEnabled the scanContinuouslyEnabled to set
     */
    public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled) {
        try {
            boolean old=this.scanContinuouslyEnabled;
            this.scanContinuouslyEnabled = scanContinuouslyEnabled;
            hwPrefs.putBoolean("CochleaAMS1cHardwareInterface.scanContinuouslyEnabled", scanContinuouslyEnabled);
            getSupport().firePropertyChange(EVENT_SCAN_CONTINUOUSLY_ENABLED, old, this.scanContinuouslyEnabled);
            sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

       @Override
    public int getSizeX() {
        return 64;
    }

    @Override
    public int getSizeY() {
        return 1;
    }



    /**
     * @return the scanX
     */
    public int getScanX() {
        return scanX;
    }

    /**
     * @param scanX the scanX to set
     */
    public void setScanX(int scanX) {
        int old=this.scanX;
        if (scanX < 0) {
            scanX = 0;
        } else if (scanX >= getSizeX()) {
            scanX = getSizeX() - 1;
        }
        try {
            this.scanX = scanX;
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.scanX", scanX);
            getSupport().firePropertyChange(EVENT_SCAN_X,old,this.scanX);
            sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    /**
     * @return the scanY
     */
    public int getScanY() {
        return scanY;
    }

    /**
     * @param scanY the scanY to set
     */
    public void setScanY(int scanY) {
       int old=this.scanY;
         if (scanY < 0) {
            scanY = 0;
        } else if (scanY >= getSizeY()) {
            scanY = getSizeY() - 1;
        }
        try {
            this.scanY = scanY;
            hwPrefs.putInt("CochleaAMS1cHardwareInterface.scanY", scanY);
           getSupport().firePropertyChange(EVENT_SCAN_Y,old,this.scanY);
             sendADCConfiguration();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    private String getBitString(short value, short nSrBits) {
        StringBuilder s = new StringBuilder();

        int k = nSrBits - 1;
        while (k >= 0) {
            int x = value & (1 << k); // start with msb
            boolean b = (x == 0); // get bit
            s.append(b ? '0' : '1'); // append to string 0 or 1, string grows with msb on left
            k--;
        } // construct big endian string e.g. code=14, s='1011'
        String bitString = s.toString();
        return bitString;
    }

    @Override
    synchronized public void sendADCConfiguration() throws HardwareInterfaceException {
        short ADCword = (short) (ADCconfig | (getADCchannel() << ADCchannelshift));

        int nBits = 0;

        StringBuilder s = new StringBuilder();

        // scanner control
        final int SCANXY_NBITS = 6;
        s.append(getBitString(isScanContinuouslyEnabled() ? (short) 1 : (short) 0, (short) 1));
        s.append(getBitString((short) getScanX(), (short) SCANXY_NBITS));
        s.append(getBitString((short) getScanY(), (short) SCANXY_NBITS));
        nBits+=1+2*SCANXY_NBITS;

        // ADC params
        s.append(getBitString((short) (getIdleTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString((short) (getRefOffTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString((short) (getRefOnTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString((short) (getTrackTime() * 15), (short) 16)); // multiplication with 15 to get from us to clockcycles
        nBits += 16;
        s.append(getBitString(ADCword, ADCconfigLength));
        nBits += ADCconfigLength;

//        // readout pathway
//        if (isUseCalibration()) {
//            s.append(getBitString((short) 1, (short) 1));
//        } else {
//            s.append(getBitString((short) 0, (short) 1));
//        }
        nBits += 1;

//        if (isSelect5Tbuffer()) {
//            s.append(getBitString((short) 1, (short) 1));
//        } else {
//            s.append(getBitString((short) 0, (short) 1));
//        }
        nBits += 1;

        //s.reverse();
        //System.out.println(s);

        BigInteger bi = new BigInteger(s.toString(), 2);
        byte[] byteArray = bi.toByteArray(); // finds minimal set of bytes in big endian format, with MSB as first element
        // we need to pad out to nbits worth of bytes
        int nbytes = (nBits % 8 == 0) ? (nBits / 8) : (nBits / 8 + 1); // 8->1, 9->2
        byte[] bytes = new byte[nbytes];
        System.arraycopy(byteArray, 0, bytes, nbytes - byteArray.length, byteArray.length);

        this.sendVendorRequest(VENDOR_REQUEST_WRITE_CPLD_SR, (short) 0, (short) 0, bytes); // stops ADC running
        setADCEnabled(isADCEnabled());
    }

    synchronized public void startADC() throws HardwareInterfaceException {
        this.sendVendorRequest(VENDOR_REQUEST_RUN_ADC, (short) 1, (short) 0);
    }

    synchronized public void stopADC() throws HardwareInterfaceException {
        this.sendVendorRequest(VENDOR_REQUEST_RUN_ADC, (short) 0, (short) 0);
    }

    public boolean isADCEnabled() {
        return adcEnabled;
    }

    public void setADCEnabled(boolean yes) throws HardwareInterfaceException {
        boolean old=this.adcEnabled;
        this.adcEnabled = yes;
        hwPrefs.putBoolean("CochleaAMS1cHardwareInterface.adcEnabled", yes);
        getSupport().firePropertyChange(EVENT_ADC_ENABLED, old, this.adcEnabled);
        if (yes) {
            startADC();
        } else {
            stopADC();
        }
    }
    /**
     * @return the TrackTime
     */
    public short getTrackTime() {
        return TrackTime;
    }

    /**
     * @return the RefOnTime
     */
    public short getRefOnTime() {
        return RefOnTime;
    }

    /**
     * @return the RefOffTime
     */
    public short getRefOffTime() {
        return RefOffTime;
    }

    /**
     * @return the IdleTime
     */
    public short getIdleTime() {
        return IdleTime;
    }
    /**
     * @return the ADCchannel
     */
    public byte getADCchannel() {
        return (byte)ADCchannel;
    }

}
