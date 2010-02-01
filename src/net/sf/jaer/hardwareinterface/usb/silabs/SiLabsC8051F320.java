/*
 * USBAEMon.java
 *
 * Created on February 17, 2005, 7:54 AM
 */

package net.sf.jaer.hardwareinterface.usb.silabs;

import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.chip.*;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;


/**
 *  Acquires data from the UNI-USE simple USB AER board that uses 
 Silicon Labs (http://www.silabs.com)
 * C8051F320 controller and SiLabs USBXPress device and host driver firmware and software.
 *<p>
 * The DLL's USBAEMonitor.dll and SiUSBXp.dll must be 
 accessible for windows programs.   Generally, this means they must be
 * somewhere on the PATH, for example, in <code>WINNT\system32</code>, 
 or they can be in the directory the program is started.
 *It is generally simplest to just put the folders on the PATH variable.
 *<p>
 *Events are captured as 16 bit addresses and 32 bit timestamps with 1us 
 tick. To use this class, construct an instance of <code>USB1AEMonitor</code>,
 *then {@link #open} it. Each time you want to capture available events, 
 call {@link #acquireAvailableEventsFromDriver}, which returns the packet of events.
 * <p>
 * {@link #overrunOccurred} can be used to see if there was a driver overrun.
 *<p>
 *NOTE: only supports a single device at a time now. (JNI limitations).
 *
 *
 * @author  tobi
 */
public class SiLabsC8051F320 implements AEMonitorInterface,  BiasgenHardwareInterface, USBInterface {
     // TODO should be using thesycon usbio driver instead of lame SiLabs USBXPress driver.
    
    static Logger log=Logger.getLogger("SiLabsC8051F320");
    
    protected AEChip chip;
    
    public void setChip(AEChip chip) {
        this.chip=chip;
    }
    
    public AEChip getChip() {
        return chip;
    }
    
    static boolean libLoaded=false;
    public static final String NATIVE_DLL_FILENAME="SiLabsC8051F320";
    public static final String USBXPRESS_DLL_FILENAME="SiUSBXp";
    boolean isOpened=false;
    public boolean eventAcquisitionEnabled=false;
    public static final int MAX_BYTES_PER_BIAS=4;
    int numEvents=0, lastNumEvents=0;
    int lastdt=0;
    AEPacketRaw events;
    
    static{
//        if(System.getProperty("os.name").startsWith("Windows")){
            try {
                System.loadLibrary(USBXPRESS_DLL_FILENAME); // you need to load this dependent DLL *first* if SiLabsC8051F320 is not on the Windows PATH
                    // see http://forum.java.sun.com/thread.jspa?threadID=679534&messageID=3963962
                System.loadLibrary(NATIVE_DLL_FILENAME);// Load Library for interfacing to Eco-Link
                libLoaded=true;
    //            log.info("SiLabsC8051F320: loaded dynamic link library "+ NATIVE_DLL_FILENAME+".dll");
            } catch (UnsatisfiedLinkError e) {
                //logging is special here because this one is static
                log.warning("Couldn't load JNI DLL for SiLabs: "+e);
                String path=null;
                try{
                    path=System.getenv("PATH");
                    path=path.replace(File.pathSeparatorChar,'\n');
                }catch(Exception e2){
                    log.warning("Couldn't read PATH from environment:" +e2.getMessage());
                }
//                log.warning(e.getMessage()+
//                        "\nSiLabsC8051F320: can't load "+NATIVE_DLL_FILENAME+".dll"+
//                        "\nNot found in " + System.getProperty("java.ext.dirs")+" or "+System.getProperty("java.library.path")+
//                        "\n user.dir="+System.getProperty("user.dir")+
//                        "\nYou will not be able to use this type of hardware interface"+
//                        "\nCould it be that you still need the SiLabsC8051F320.dll folder on the Windows PATH \n (and not just in java.library.path) because of dependent DLLs?"+
//                        "\nPATH="+path);
            }
//        }
        //        if(libLoaded){
        //            log.info("Registering static shutdown hook to close USBAEMonitor");
        //            Runtime.getRuntime().addShutdownHook(new Thread(){
        //                public void run(){ log.info("USBAEMonitor shutdown hook");}
        //            });
        //        }
//        System.loadLibrary(NATIVE_DLL_FILENAME);
        //        if(initIDs()!=0){ throw new USBAEMonitorException("can't init object field IDs");}
    }
    
    /** Creates a new instance of USB1AEMonitor. Note that it is possible but probably bad to construct several instances
     * and use each of them to open and read from the same device.
     * This class would probably better be a singleton until multiple devices are supported.
     */
    public SiLabsC8051F320() {
    }
    
    //    private static native int initIDs();
    PropertyChangeSupport support=new PropertyChangeSupport(this);
    
    private native short[] nativeGetAddresses();
    private native int[] nativeGetTimestamps();
    private native boolean nativeOverrunOccured();
    private native int nativeOpen();
    private native int nativeClose();
    private native int nativeAcquireAvailableEventsFromDriver();
    private native int nativeGetNumEventsAcquired();
    private native int nativeSetEventAcquisitionEnabled(boolean eventAcquisitionEnabled);
    private native int nativeGetNumDevices();
    private native int nativeOpen(int n);
    private native int nativeSetPowerdown(boolean powerDownEnabled);
    private native int nativeSendBiases(byte[] bytes);
    private native int nativeFlashBiases(byte[] bytes);
    public native int nativeResetTimestamps();
    
    private int interfaceNumber=0; // this is the number of the interface to actually open (we can only open one)
    
    /** Opens the device driver and starts acquiring events.
     *
     * @see #close
     *@throws USBAEMonitorException if there is a problem. Diagnostics are printed to stdout in the native code.
     */
    public void open() throws HardwareInterfaceException {
        if(!libLoaded) return;
        int status=nativeOpen(getInterfaceNumber());
        if(status==0) {
            isOpened=true;
            setEventAcquisitionEnabled(true);
//            log.info("SiLabsC8051F320.open(): device opened");
            HardwareInterfaceException.clearException();
            return;
        }else {
            isOpened=false;
            //close(); // not opened, so don't need to close
            throw new HardwareInterfaceException("nativeOpen: can't open device, device returned status "+errorText(status));
        }
    }
    
    @Override
    public String toString() {
        return (getTypeName() + ": Interface " + getInterfaceNumber());
    }
    
    void ensureOpen() throws HardwareInterfaceException{
        if(!isOpen()){
//            log.info("SiLabsC8051F320.acquireAvailableEventsFromDriver(): device not open, opening it");
            open();
        }
    }


//    long t0=System.nanoTime(); // TODO remove
    /** Gets available events from driver and return them in a new AEPacketRaw.
     *{@link #overrunOccurred} will be true if these was an overrun of the host USBXPress driver buffers (>16k events).
     *<p>
     *AEListeners are called if new events have been collected.
     *
     * @return packet of raw events
     *@throws USBAEMonitorException
     *@see #addAEListener
     * .
     */
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        try{
            if(!libLoaded) return null;
            ensureOpen();
            numEvents=nativeAcquireAvailableEventsFromDriver();
//            System.out.println(numEvents+","+(System.nanoTime()-t0));
            if (numEvents<0) {
                close();
                throw new HardwareInterfaceException("nativeAcquireAvailableEventsFromDriver, device returned "+errorText(numEvents));
            }
            events=new AEPacketRaw(numEvents); // TODO should reuse packet
            if(numEvents==0){
                return new AEPacketRaw(0);
            }
            short[] shortAddr=nativeGetAddresses(); // returns null when device is disconnected
            if(shortAddr==null){
                throw new NullPointerException("array of short[] addresses returned was null, probably device was unplugged");
            }
            // add copy to handle change from original short[] addresses to new int[] raw addresses
            int[] addr=events.addresses;
            for(int i=0;i<shortAddr.length;i++){
                addr[i]=shortAddr[i]&0xFFFF;
            }
            int[] t=nativeGetTimestamps();
            if(t==null){
               throw new NullPointerException("should have gotten "+numEvents+" events but got null array. Probably device was unplugged");
            }
            int[] ts=events.timestamps;
            System.arraycopy(t, 0, ts, 0, ts.length);
            events.setNumEvents(numEvents);
            if(numEvents>0){
                support.firePropertyChange(newEventPropertyChange);
                lastdt=t[numEvents-1]-t[0];
            }
            lastNumEvents=numEvents;
            HardwareInterfaceException.clearException();
            return events;
        } catch (NullPointerException e) {
            e.printStackTrace();
            return new AEPacketRaw(0);
        }
    }





    /**
     * event supplied to listeners when new events are collected. this is final because it is just a marker for the listeners that new events are available
     */
    public final PropertyChangeEvent newEventPropertyChange=new PropertyChangeEvent(this, "NewEvents", null,null);
    
    /** Returns the number of events acquired by the last call to {@link
     * #acquireAvailableEventsFromDriver }
     * @return number of events acquired
     */
    public int getNumEventsAcquired(){
        if(!libLoaded) return 0;
        return nativeGetNumEventsAcquired();
    }
    
    /** resets the timestamps to zero */
    public void resetTimestamps() {
        if(!libLoaded) return;
        nativeResetTimestamps();
    }
    
    
    /** Closes the device and frees the internal device handle. Never throws an exception.
     */
    public void close(){
        
        if(!libLoaded) return;
        int status=nativeClose();
        if(status!=0) System.err.println("SiLabsC8051F320.close(): returned "+errorText(status));
        isOpened=false;
        eventAcquisitionEnabled=false;
//        log.info("SiLabsC8051F320.close(): device closed");
    }
    
    
    /** The buffer size is fixed at 16k events on this USBXPress device
     *@return 16k
     */
    public int getAEBufferSize() {
        return (1<<16);
    }
    
    
    /** Is true if an overrun occured in the driver (>16k events) the last time {@link
     * #acquireAvailableEventsFromDriver } was called. This flag is cleared by the next {@link #acquireAvailableEventsFromDriver}.
     *If there is an overrun, the events grabbed are the most ancient; events after the overrun are discarded. The timestamps continue on but will
     *probably be lagged behind what they should be.
     * @return true if there was an overrun.
     */
    public boolean overrunOccurred(){
        if(!libLoaded) return false;
        return nativeOverrunOccured();
    }
    
    /** on this USBXPress device you cannot set the buffer size, so this call generates a warning print */
    public void setAEBufferSize(int AEBufferSize) {
        System.err.println("warning USB1AEMonitor.setAEBufferSize(): size is fixed to 16k events -- didn't change size");
    }
    
    
    public String[] getStringDescriptors() {
        return null;
    }
    
    public PropertyChangeSupport getSupport() {
        return this.support;
    }
    
    /** adds a listener for new events captured from the device
     * @param listener the listener. It is called with a PropertyChangeEvent when new events
     * are received by a call to {@link #acquireAvailableEventsFromDriver}.
     * @see #acquireAvailableEventsFromDriver
     */
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }
    
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }
    
    
    /** not implmented for SiLabs devices
     @return null
     */
    public int[] getVIDPID() {
        return null;
    }
    
    public boolean isOpen() {
        return isOpened;
    }
    
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if(!libLoaded) return;
        
        if(biasgen.getPotArray()==null) {
            log.info("BiasgenUSBInterface.send(): iPotArray=null, no biases to send");
            return; // may not have been constructed yet.
        }
        
        
        ensureOpen();
        
        byte[] toSend = getBiasBytes(biasgen);
        
        int status=nativeFlashBiases(toSend);
        if(status==0) {
            HardwareInterfaceException.clearException();
            return;
        }else {
            close();
            throw new HardwareInterfaceException("nativeFlashBiases: can't flash values: "+errorText(status));
        }
    }
    
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        
        if(biasgen.getPotArray()==null) {
            log.info("BiasgenUSBInterface.send(): iPotArray=null, no biases to send");
            return; // may not have been constructed yet.
        }
        
        ensureOpen();
        
        //        throw new IPotException("null USBIO interface");
        //        if(iPotArray==null) throw new IPotException("null iPotArray");
        //
        // we make an array of bytes to hold the values sent, then we fill the array, copy it to a
        // new array of the proper size, and pass it to the routine that actually sends a vendor request
        // with a data buffer that is the bytes
        
        byte[] toSend;
        // construct the byte array to send to hardware interface depending on wheter the PotArray is actually an IPotArray
        if (biasgen.getPotArray() instanceof net.sf.jaer.biasgen.IPotArray)
        {
            toSend = getBiasBytes(biasgen);
            
        } else { 
            // asssume we only have VPots
            VPot p=null;
            
            ArrayList<Pot> pots=biasgen.getPotArray().getPots();
            
            toSend=new byte[pots.size()*3];
            int i=0;
            for(Pot pot:pots){
                p=(VPot)pot;
                toSend[i]=(byte)p.getChannel(); //address
                toSend[i+1]=(byte)((p.getBitValue() & 0x0F00) >> 8 );  //value msb
                toSend[i+2]=(byte)(p.getBitValue() & 0x00FF); //value lsb
                i+=3;
            }
            
            //    for (int k=0;k<pots.size();k++)
            //      System.out.println("bias  " + k +": channel " + toSend[3*k] + " value " +toSend[3*k+1] +" "+ toSend[3*k+2]);
        }
        
        int status=nativeSendBiases(toSend);
        if(status==0) {
            HardwareInterfaceException.clearException();
            return;
        }else {
            close();
            throw new HardwareInterfaceException("nativeSendBiases: can't send biases: "+errorText(status));
        }
    }
    
    // this method can only be used for IPotArrays
    private byte[] getBiasBytes(final Biasgen biasgen) {
        IPotArray iPotArray= (IPotArray)biasgen.getPotArray();
        byte[] bytes=new byte[iPotArray.getNumPots()*MAX_BYTES_PER_BIAS]; // oversize this for now, later we copy to actual array
        int byteIndex=0;
        byte[] toSend;
        Iterator i=iPotArray.getShiftRegisterIterator();
        while(i.hasNext()){
            IPot iPot=(IPot)i.next();
            // for each bias starting with the first one (the one closest to the ** END ** of the shift register
            // we get the bitValue and from MSB ro LSB stuff these values into the byte array
            
            for(int k=iPot.getNumBytes()-1;k>=0;k--){ // for k=2..0
                bytes[byteIndex++]=(byte)((iPot.getBitValue()>>>k*8)&0xff);
            }
        }
        toSend=new byte[byteIndex];
        System.arraycopy(bytes, 0, toSend, 0, byteIndex);
       
        return toSend;
    }
    
    /** toggles the powerDown pin correctly to ensure on-chip biasgen is powered up. Chip may have been plugged in without being
     * powered up. If powerdown is true, simply sets powerdown high. If powerdown is false, powerdown is toggled high and then low, to make
     * sure a nagative transistion occurs. This transistion is necessary to ensure the startup circuit starts up the masterbias again.
     * @param powerDown true to power OFF the biasgen, false to power on
     */
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if(!libLoaded) return;
        ensureOpen();
        if(!powerDown){
            int status=nativeSetPowerdown(true);
            if(status!=0){
                close();
                throw new HardwareInterfaceException("nativeSetPowerdown: can't set powerDown: "+errorText(status));
            }
        }
        int status=nativeSetPowerdown(powerDown);
        if(status==0) {
            HardwareInterfaceException.clearException();
            return;
        }else {
            close();
            throw new HardwareInterfaceException("nativeSetPowerdown: can't set powerDown: "+errorText(status));
        }
    }
    
    
    /** returns state of event acquisition flag. this may not have been sent to the device yet. */
    public boolean isEventAcquisitionEnabled() {
        return this.eventAcquisitionEnabled;
    }
    
    /** @param eventAcquisitionEnabled true to enable sending events from device */
    public void setEventAcquisitionEnabled(final boolean eventAcquisitionEnabled) throws HardwareInterfaceException {
        if(!libLoaded) return;
        if(!isOpen()){
            log.info("SiLabsC8051F320.setEventAcquisitionEnabled(): device not open, opening it");
            open();
        }
        int status =nativeSetEventAcquisitionEnabled(eventAcquisitionEnabled);
        if(status==0) {
            this.eventAcquisitionEnabled = eventAcquisitionEnabled;
            HardwareInterfaceException.clearException();
            return;
        }else {
            close();
            throw new HardwareInterfaceException("eventAcquisitionEnabled: can't enable event acquisition "+errorText(status));
        }
    }
    
    public String getTypeName() {
        return "SiLabsC8051F320";
    }
    
    
    static String errorText(int status){
        switch(status){
            case 0xff: return "SI_DEVICE_NOT_FOUND";
            case 1: return "SI_INVALID_HANDLE";
            case 2: return "SI_READ_ERROR";
            case 3: return "SI_RX_QUEUE_NOT_READY";
            case 4: return "SI_WRITE_ERROR";
            case 5: return "SI_RESET_ERROR";
            case 6: return "SI_INVALID_PARAMETER";
            case 7: return "SI_INVALID_REQUEST_LENGTH";
            case 8: return "SI_DEVICE_IO_FAILED";
            case 9: return "SI_INVALID_BAUDRATE";
            case -2: return "USBAEMON_ALREADY_OPEN";
            case -3: return "USBAEMON_NO_DEVICES";
            case -4: return "USBAEMON_NUM_DEVICES_ERROR";
            case -5: return "USBAEMON_DEVICE_NOT_OPEN";
            default: return "Unknown error "+status;
        }
    }
    
    /** returns the SiLabs USBXPress device number to open() */
    int getInterfaceNumber() {
        return interfaceNumber;
    }
    
    /** sets the SiLabs USBXPress device number to open() */
    void setInterfaceNumber(int interfaceNumber) {
        this.interfaceNumber = interfaceNumber;
    }
    
    
    int getNumDevices(){
        if(!libLoaded) return 0;
        int n=nativeGetNumDevices();
        if(n<0) {
            System.err.println("SiLabsC8051F320.getNumDevices(): error getting number of devices "+SiLabsC8051F320.errorText(n));
            return 0;
        }
        return n;
    }
    
//    /**
//     * Tests event acquisition.
//     *
//     * @param args the command line arguments
//     */
//    public static void main(String[] args) {
//        int i;
//        AEMonitorInterface aemon=null;
//        int numDevices=SiLabsC8051F320Factory.instance().getNumInterfacesAvailable();
//        log.info(numDevices+" devices found");
//        if(numDevices==0) System.exit(0);
//        try{
//            log.info("opening first device");
//            aemon=(AEMonitorInterface)HardwareInterfaceFactory.instance().getFirstAvailableInterface();
////           aemon.close();
////            log.info("opening last device");
////           aemon=(USBAEMonitorInterface)SiLabsC8051F320Factory.instance().getInterface(numDevices-1);
//            log.info("opening device");
//            aemon.open();
//        }catch(Exception e){
//            e.printStackTrace();
//            aemon.close();
//            System.exit(1);
//        }
//
//        EventExtractor2D extractor=new Tmpdiff128().getEventExtractor();
//        AEPacketRaw raw=null;
//
//        log.info("acquiring events");
//        for(i=0;i<15;i++){
//            int numEvents=0;
//            if(i==5){
//                aemon.resetTimestamps();
//                log.info("*************timestamps reset");
//            }
//            try{
//                raw=aemon.acquireAvailableEventsFromDriver();
//            }catch(Exception e){
//                e.printStackTrace();
//                aemon.close();
//                System.exit(1);
//            }
//            if(aemon.overrunOccurred()){
//                System.out.print("overrun ");
//            }
//            numEvents=raw.getNumEvents();
//            if(numEvents==0) log.info("no events");
//            if(numEvents>0){
//                AEPacket2D events=extractor.extract(raw);
//                System.out.print(numEvents+" events: ");
//                int k=numEvents<10?numEvents:10;
//                for(int j=0;j<k;j++){
//
//                    short x=events.getXs()[j]; // map to retina address space
//                    short y=events.getYs()[j];
//                    byte pol=events.getTypes()[j];
//                    int t=events.getTimestamps()[j];
//                    System.out.print(x+","+y+","+t+" ");
//                }
//                log.info("");
//            }
//            try {Thread.currentThread().sleep(200);} catch (java.lang.InterruptedException e) {}
//        }
//        aemon.close();
//        log.info("closed");
//        System.exit(0);
//    }
    
    /** the max capacity of this USB1 bus interface with SiLabs overhead is ~1 Mb/sec/4 bytes/event/2 ~ 150keps
     */
    public int getMaxCapacity() {
        return 150000;
    }
    
    /** Only valid after {@link #acquireAvailableEventsFromDriver}.
     * @return event rate in events/sec as computed from last acquisition
     *
     */
    public int getEstimatedEventRate() {
        if(lastNumEvents<2) return 0;
        if(lastdt==0) return 0;
        int rate=(int)(1e6f*(float)numEvents/((float)lastdt));
//        log.info("lastNumEvents="+lastNumEvents+" lastdt="+lastdt+" rate="+rate);
        return rate;
    }
    
    /** @return timestamp tick in us */
    final public int getTimestampTickUs() {
        return 1;
    }
    
    
    public AEPacketRaw getEvents() {
        return this.events;
    }
    
    /** not implmented for SiLabs devices
     @return 0
     */
    public short getDID() {
        return 0;
    }
    
    /** not implmented for SiLabs devices
     @return 0
     */
    public short getPID() {
        return 0;
    }
    
    /** not implmented for SiLabs devices
     @return 0
     */
    public short getVID() {
        return 0;
    }

    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return getBiasBytes(biasgen);
    }
}

