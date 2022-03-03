/*
 * Copyright (C) 2021 arios.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package es.us.atc.jaer.hardwareinterface;

import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import com.opalkelly.frontpanel.okFrontPanel;
import de.thesycon.usbio.PnPNotifyInterface;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;

/**
 *
 * @author Antonio Ríos, University of Seville, arios@us.es, 01/02/2021
 */
public class OpalKellyFX3Monitor implements AEMonitorInterface, PnPNotifyInterface, ReaderBufferControl{

    private static final Logger LOG = Logger.getLogger("OpalKellyFX3Factory");
    public static okFrontPanel ok_front_panel;
    
    /** The pool of raw AE packets, used for data transfer */
    private AEPacketRawPool aePacketRawPool;
    private AEPacketRaw lastEventsAcquired;
    private int eventCounter;
    private boolean overrunOccuredFlag;
    private int estimatedEventRate;
    
    public PropertyChangeSupport support;
    public PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE;
    
    private static Preferences prefs = Preferences.userNodeForPackage(OpalKellyFX3Monitor.class);
    private int aeBufferSize; // Must be multiple of usbBlockSize and multiple of 16 as a minimum
    private static final int MIN_AEBUFFERSIZE = 1024;
    private static final int DEFAULT_AEBUFFERSIZE = 64*1024;
    private static final int MAX_AEBUFFERSIZE = 8*1024*1024;
    protected AEOpalKellyReader AEReader;
    
    private int usbBlockSize;
    private static final int MIN_USBBLOCKSIZE = 16;
    private static final int DEFAULT_USBBLOCKSIZE = 1024;
    private static final int MAX_USBBLOCKSIZE = 16 * 1024;
    private static final int NUM_USB_PIPES = 1;
    
    private long absolute_timestamp;
    private final int TICK_US = 1;
    public final double TICK_US_CLK_FPGA = 0.01;
    private boolean eventAcquisitionEnabled;
    private AEChip chip;
    private boolean isOpenedDevice;
    //This is the VHDL endpoint address
    
    public final int OK_UBS_OUTPIPE_ENDPOINT = 0xA0;
    public final int OK_USB_INWIRE_COMMAND_ENDPOINT = 0x00;
    public final int OK_USB_INWIRE_SELINPUT_ENDPOINT = 0x01;   
    public final int OK_USB_INVIRE_SWRST_ENDPOINT = 0x02;
    
    public OpalKellyFX3Monitor() {
        this.NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);
        this.eventAcquisitionEnabled = false;
        this.estimatedEventRate = 0;
        this.absolute_timestamp = 0;
        this.isOpenedDevice = false;
        this.usbBlockSize = DEFAULT_USBBLOCKSIZE;
        this.aeBufferSize = DEFAULT_AEBUFFERSIZE;
        this.lastEventsAcquired = new AEPacketRaw();
        this.support = new PropertyChangeSupport(this);
        this.AEReader = null;
    }
    
    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (isEventAcquisitionEnabled()) 
        {
            this.overrunOccuredFlag = false;
            // get the 'active' buffer for events (the one that has just been written by the hardware thread)
            synchronized (aePacketRawPool) 
            { // synchronize on aeReader so that we don't try to access the events at the same time
                aePacketRawPool.swap();
                this.lastEventsAcquired = aePacketRawPool.readBuffer();
            }
            int nEvents = lastEventsAcquired.getNumEvents();
            this.eventCounter = 0;
            computeEstimatedEventRate(lastEventsAcquired);

            if (nEvents != 0) 
            {
                support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE); // call listeners  
            }
        }
        return this.lastEventsAcquired;
    }
    
    void computeEstimatedEventRate(AEPacketRaw events) 
    {
        if (events == null || events.getNumEvents() < 2) 
        {
            this.estimatedEventRate = 0;
        } else {
            int[] ts = events.getTimestamps();
            int n = events.getNumEvents();
            int dt = ts[n - 1] - ts[0];
            this.estimatedEventRate = (int) (1e6f * (float) n / (float) dt);
        }
    }

    @Override
    public int getNumEventsAcquired() {
        return this.lastEventsAcquired.getNumEvents();
    }

    @Override
    public AEPacketRaw getEvents() {
        return this.lastEventsAcquired;
    }

    @Override
    public void resetTimestamps() {
        this.absolute_timestamp = 0;
    }

    @Override
    public boolean overrunOccurred() {
        return this.overrunOccuredFlag;
    }

    @Override
    public int getAEBufferSize() {
        return this.aeBufferSize;
    }

    @Override
    public void setAEBufferSize(int AEBufferSize) {
        if(AEBufferSize%16 != 0){
            LOG.log(Level.WARNING, "ignoring unreasonable aeBufferSize of {0}, it should be multiple of 16.", new Object[]{AEBufferSize});
            return;
        }
        if (AEBufferSize < MIN_AEBUFFERSIZE || AEBufferSize > MAX_AEBUFFERSIZE) {
            LOG.log(Level.WARNING, "ignoring unreasonable aeBufferSize of {0}, choose a more reasonable size between {1} and {2}", new Object[]{AEBufferSize, MIN_AEBUFFERSIZE, MAX_AEBUFFERSIZE});
            return;
        }
        if(AEBufferSize < this.usbBlockSize)
        {
            LOG.log(Level.WARNING, "ignoring unreasonable aeBufferSize of {0}, aeBufferSize cannot be smaller than usbBlokSize = {1}", new Object[]{AEBufferSize, this.usbBlockSize});
            return;
        }
        this.aeBufferSize = AEBufferSize;
        synchronized (aePacketRawPool) 
        {
            aePacketRawPool.allocateMemory(); //Allocates internal memory for transferring data from reader to consumer, e.g. rendering.
        }
        prefs.putInt("OpalKellyFX3Monitor.aeBufferSize", this.aeBufferSize);
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException 
    {
        if (enable) 
        {
            this.AEReader = new AEOpalKellyReader();
            LOG.log(Level.INFO, "OpalKellyFX3Monitor AEreader start...");
            
            //ok_front_panel.ResetFPGA();
            ok_front_panel.SetWireInValue(this.OK_USB_INVIRE_SWRST_ENDPOINT, 0x00000001); // Send sw reset to the hardware
            ok_front_panel.UpdateWireIns();
            ok_front_panel.SetWireInValue(this.OK_USB_INVIRE_SWRST_ENDPOINT, 0x00000000); // Clear sw reset to the hardware
            ok_front_panel.UpdateWireIns();
            //Send ECU command and select input 1
            ok_front_panel.SetWireInValue(this.OK_USB_INWIRE_COMMAND_ENDPOINT, 0x00000001);
            ok_front_panel.SetWireInValue(this.OK_USB_INWIRE_SELINPUT_ENDPOINT, 0x00000001);
            ok_front_panel.UpdateWireIns();
            
            this.AEReader.start();
            this.eventAcquisitionEnabled = true;
        } else{
            if (this.AEReader != null) 
            {
                ok_front_panel.SetWireInValue(this.OK_USB_INWIRE_COMMAND_ENDPOINT, 0x00000000);
                ok_front_panel.SetWireInValue(this.OK_USB_INWIRE_SELINPUT_ENDPOINT, 0x00000000);
                ok_front_panel.UpdateWireIns();
                ok_front_panel.Close();
                ok_front_panel.delete();
                ok_front_panel = null;
                if(this.AEReader != null) {
                    this.AEReader.finish();
                    this.AEReader = null;
                }
            }
        }
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return this.eventAcquisitionEnabled;
    }

    @Override
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public int getMaxCapacity() {
        return this.MAX_AEBUFFERSIZE;
    }

    @Override
    public int getEstimatedEventRate() {
        return this.estimatedEventRate;
    }

    @Override
    public int getTimestampTickUs() {
        return TICK_US;
    }

    @Override
    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    @Override
    public AEChip getChip() {
        return this.chip;
    }

    @Override
    public String getTypeName() {
        return "OpalKellyFX3Monitor"; // TODO it can be done better
    }
    
    @Override
    public String toString()
    {
        return "OKAERTool";
    }

    @Override
    public void close() {
        try 
        {
            setEventAcquisitionEnabled(false);
        } catch (HardwareInterfaceException ex) 
        {
            LOG.log(Level.WARNING, ex.toString());
        }
        this.isOpenedDevice = false;
    }

    @Override
    public void open() throws HardwareInterfaceException {
        System.loadLibrary("okjFrontPanel");
        ok_front_panel = new okFrontPanel();
        okFrontPanel.ErrorCode error = error = ok_front_panel.OpenBySerial("");
        if(error != okFrontPanel.ErrorCode.NoError) {
            LOG.log(Level.WARNING, error.toString());
        }else{
            LOG.log(Level.INFO, "Opening OpalKellyFX3Monitor with result: {0}", error.toString());
            error = ok_front_panel.ConfigureFPGA("jars/okt_top.bit");
            if(error != okFrontPanel.ErrorCode.NoError){
                LOG.log(Level.WARNING, error.toString());
            }else{
                LOG.log(Level.INFO, "OKAERTool fpga programmed");
            }
            setEventAcquisitionEnabled(true);
            this.isOpenedDevice = true;
        }
    }

    @Override
    public boolean isOpen() {
        return this.isOpenedDevice;
    }

    @Override
    public void onAdd() {
        LOG.log(Level.INFO, "OpalKellyFX3Monitor device added");
    }

    @Override
    public void onRemove() {
        LOG.log(Level.INFO, "OpalKellyFX3Monitor device removed");
    }

    @Override
    public int getFifoSize() {
        return usbBlockSize;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        if(fifoSize >= aeBufferSize){
            usbBlockSize = aeBufferSize;
            LOG.log(Level.WARNING, "USB block size {0} cannot be bigger than USB transfer length {1}.", new Object[]{fifoSize, aeBufferSize});
        }
        else if(fifoSize <= MIN_USBBLOCKSIZE){
            usbBlockSize = MIN_USBBLOCKSIZE;
            LOG.log(Level.WARNING, "USB minimum block size is {0}.", new Object[]{MIN_USBBLOCKSIZE});
        }
        else if(fifoSize >= MAX_USBBLOCKSIZE){
            usbBlockSize = MAX_USBBLOCKSIZE;
            LOG.log(Level.WARNING, "USB maximum block size is {0}.", new Object[]{MAX_USBBLOCKSIZE});
        } 
        else {
            usbBlockSize = fifoSize;
        }
    }

    @Override
    public int getNumBuffers() {
        return NUM_USB_PIPES;
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        LOG.log(Level.WARNING, "USB pipes cannot be modified.");
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return this.support;
    }
    
    
    public class AEOpalKellyReader extends Thread implements Runnable
    {
        //OpalKellyHardwareInterface monitor;
        
        protected boolean running;
        
        public AEOpalKellyReader()
        {
            setName("OpalKellyFX3Monitor AEReader");
            aePacketRawPool = new AEPacketRawPool(OpalKellyFX3Monitor.this);
            this.running = true;
        }
        
        @Override
        public void run()
        {
            while(running)
            {
                try
                {
                    synchronized (ok_front_panel)
                    {
                        byte[] buffer = new byte[aeBufferSize];
                        int readBytes = ok_front_panel.ReadFromBlockPipeOut(OK_UBS_OUTPIPE_ENDPOINT, usbBlockSize, buffer.length, buffer);
                        if(readBytes >= 0){
                            translateEvents(buffer, readBytes);
                        }else{
                            LOG.log(Level.SEVERE, "Error reading from USB. Error code (" + readBytes + ")");
                            LOG.log(Level.SEVERE, "Possible solution:");
                            switch(readBytes){
                                case -1:
                                    LOG.log(Level.SEVERE, "Unplug and plug the board power supply.");
                                    break;
                                case -10:
                                    LOG.log(Level.SEVERE, "Configure USB transfer length and USB block size to default values. "
                                            + "Transfer length --> "+DEFAULT_AEBUFFERSIZE+", USB Block size --> "+DEFAULT_USBBLOCKSIZE+"."
                                            + "If the problem persists, unplug and plug the USB cable.");
                                    break;
                            }
                            this.running = false;
                            //close();
                        }
                    }
                }
                catch(Exception ex)
                {
                    LOG.log(Level.SEVERE, "Exception reading from USB : "+ ex.toString());
                    this.running = false;
                }
            }
        }
        
        synchronized public void finish() {
            this.running = false;
            LOG.log(Level.INFO, "OpalKellyFX3Monitor reader thread ending");
            interrupt();
        }
        
        protected void translateEvents(byte[] buff, int numBytes)
        {
            synchronized (aePacketRawPool) 
            {
                AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                
                int[] addresses = buffer.getAddresses();
                int[] timestamps = buffer.getTimestamps();
                
                buffer.lastCaptureIndex = eventCounter;
                if((numBytes % 8) != 0)
                {
                    numBytes = (numBytes / 8) * 8;// truncate off any extra part-event
                }
                
                for(int i = 0; i < numBytes; i=i+8)
                {
                    byte[] timestamp = new byte[4];
                    byte[] address = new byte[4];
                    try
                    {
                        timestamp[0] = buff[i];
                        timestamp[1] = buff[i+1];
                        timestamp[2] = buff[i+2];
                        timestamp[3] = buff[i+3];
                        
                        address[0] = buff[i+4];
                        address[1] = buff[i+5];
                        address[2] = buff[i+6];
                        address[3] = buff[i+7];
                    }
                    catch(Exception ex)
                    {
                        LOG.log(Level.WARNING, "Exception caught when getting information from USB buffer. Problem: " + ex.toString());
                        //continue;
                        break;
                    }
                    
                    if (eventCounter >= lastEventsAcquired.getCapacity()) 
                    {
                        overrunOccuredFlag = true;
                        continue;
                    }
                                                            
                    int addr =  ((address[3] & 0xFF) << 24) | ((address[2] & 0xFF) << 16) 
                            | ((address[1] & 0xFF) << 8) | (address[0] & 0xFF);
                    
                    
                    int time = ((timestamp[3] & 0xFF) << 24) | ((timestamp[2] & 0xFF) << 16) 
                            | ((timestamp[1] & 0xFF) << 8) | (timestamp[0] & 0xFF);
                    
                                        
                    absolute_timestamp += (time * TICK_US_CLK_FPGA);
                    if(time == 0xFFFFFFFF || (time == 0 && addr == 0) || time == 0)
                    //if(time == 0xFFFFFFFF || time == 0 || addr == 0)
                    {
                        //Timestamp overflow or null data to fill out the USB package
                        continue;
                    }
                    /*else{
                        LOG.log(Level.INFO, "Caso raro: ts: " + String.format("0x%08X", time) + " addr:" + String.format("0x%08X", addr));
                    }*/
                    
                    /*try {
                        int y_coord = address[1];
                        int x_coord = (address[0] & 0xFE) >> 1;
                        //my_file.write("TS: 0x"+Integer.toHexString(time).toUpperCase()+" - ");
                        //my_file.write("ADDR: (Y) 0x"+Integer.toHexString(y_coord)+" (X) 0x"+Integer.toHexString(x_coord)+"\n");
                        my_file.write("TS 0x"+Integer.toHexString(time).toUpperCase()+": "+ time*TICK_US_CLK_FPGA +"us -- ");
                        my_file.write("ADDR 0x"+Integer.toHexString(addr).toUpperCase()+": (Y) "+ y_coord +"; (X) "+ x_coord +"\n");
                    } catch (IOException ex) {
                        Logger.getLogger(OpalKellyFX3Monitor.class.getName()).log(Level.SEVERE, null, ex);
                    }*/
                    
                    addresses[eventCounter] = addr;
                    timestamps[eventCounter] = (int)absolute_timestamp;
                    eventCounter++;
                    buffer.setNumEvents(eventCounter);
                    buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
                }
                //LOG.log(Level.INFO, "Buffer processed");
            }
        }
    }
}
