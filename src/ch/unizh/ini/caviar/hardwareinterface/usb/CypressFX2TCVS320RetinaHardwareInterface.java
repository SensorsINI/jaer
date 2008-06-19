/*
 * CypressFX2Biasgen.java
 *
 * Created on 23 Jan 2008
 *
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import de.thesycon.usbio.UsbIoBuf;

/**
 * Adds functionality of TCVS320 retina to base classes for Cypress FX2 interface.
 *
 * @author tobi
 */
public class CypressFX2TCVS320RetinaHardwareInterface extends CypressFX2Biasgen {
    
    /** Creates a new instance of CypressFX2Biasgen */
    protected CypressFX2TCVS320RetinaHardwareInterface(int devNumber) {
        super(devNumber);
    }
    /** 
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
     our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();
        getAeReader().startThread(3); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

       
    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class RetinaAEReader extends CypressFX2.AEReader{
        public RetinaAEReader(CypressFX2 cypress) throws HardwareInterfaceException{
            super(cypress);
        }
   //    private int transState=0; // state of data translator
        private int lasty=0;
        private int lastts=0;
        
        /** Method to translate the UsbIoBuffer for the TCVS320 sensor which uses the 32 bit address space.
         *<p>
         * It has a CPLD to timetamp events and uses the CypressFX2 in slave 
         * FIFO mode. 
         *<p>The TCVS320 has a burst mode readout mechanism that outputs a row address, then all the latched column addresses.
         *The columns are output left to right. A timestamp is only meaningful at the row addresses level. Therefore
         *the board timestamps on row address, and then sends the data in the following sequence: row, timestamp, col, col, col,...., row,timestamp,col,col...
         * <p>
         *The bit encoding of the data is as follows
         *<literal>
        Address bit	Address bit pattern
        0	LSB Y or Polarity ON=1
        1	Y1 or LSB X
        2	Y2 or X1
        3	Y3 or X2
        4	Y4 or X3
        5	Y5 or X4
        6	Y6 or X5
        7	MSBY or X6
        8	intensity or X7
        9	MSBX
        10	Y=0, X=1

        Address	Address Name
        00xx xxxx xxxx xxxx	pixel address
        01xx xxxx xxxx xxxx	timestamp
        10xx xxxx xxxx xxxx	wrap
        11xx xxxx xxxx xxxx	timestamp reset
        </literal>
         
         *The msb of the 16 bit timestamp is used to signal a wrap (the actual timestamp is only 15 bits).
         * The wrapAdd is incremented when an emtpy event is received which has the timestamp bit 15
         * set to one.
         *<p>
         * Therefore for a valid event only 15 bits of the 16 transmitted timestamp bits are valid, bit 15
         * is the status bit. overflow happens every 32 ms.
         * This way, no roll overs go by undetected, and the problem of invalid wraps doesn't arise.
         *@param b the data buffer
         *@see #translateEvents
         */
        protected void translateEvents_TCVS320(UsbIoBuf b){
            try{
  //          final int STATE_IDLE=0,STATE_GOTY=1,STATE_GOTTS=2;
             
//            if(tobiLogger.isEnabled()==false) tobiLogger.setEnabled(true); //debug
            synchronized(aePacketRawPool){
                AEPacketRaw buffer=aePacketRawPool.writeBuffer();
               
                int shortts;
                int NumberOfWrapEvents;
                NumberOfWrapEvents=0;
                
                int numberOfY=0;
                
                byte[] buf=b.BufferMem;
                int bytesSent=b.BytesTransferred;
                if(bytesSent%2!=0){
                    System.err.println("CypressFX2.AEReader.translateEvents(): warning: "+bytesSent+" bytes sent, which is not multiple of 2");
                    bytesSent=(bytesSent/2)*2; // truncate off any extra part-event
                }
                
                int[] addresses=buffer.getAddresses();
                int[] timestamps=buffer.getTimestamps();
                
                boolean gotY=false;
                
                // write the start of the packet
                buffer.lastCaptureIndex=eventCounter;
//                tobiLogger.log("#packet");
                for(int i=0;i<bytesSent;i+=2){
//                    tobiLogger.log(String.format("%d %x %x",eventCounter,buf[i],buf[i+1])); // DEBUG
                 //   int val=(buf[i+1] << 8) + buf[i]; // 16 bit value of data
                    int code=(buf[i+1]&0xC0)>>6; // (val&0xC000)>>>14;
                  //  log.info("code " + code);
                    switch(code){
                        case 0: // address
                            if ((eventCounter>aeBufferSize-1) || (buffer.overrunOccuredFlag)){
                                buffer.overrunOccuredFlag=true;
                                // throw away events
                            } else {
                                if ((buf[i+1] & 0x04) == 0x04) ////  received an X address
                                { // x adddress
                                    int xadd=(((0x03 & buf[i+1]) ^ 0x02) << 8 ) |  (buf[i]&0xff);  // invert bit 9 of the x address
                                    addresses[eventCounter]= (lasty << 12 ) | xadd;                 //(0xffff&((short)buf[i]&0xff | ((short)buf[i+1]&0xff)<<8));
                                    
                                    timestamps[eventCounter]=(TICK_US*(lastts+wrapAdd)); //*TICK_US; //add in the wrap offset and convert to 1us tick
                                    
                                    eventCounter++;
                                    //    log.info("received x address");
                                    buffer.setNumEvents(eventCounter);
                                    gotY=false;
                                } else // y address
                                {
                                    if (gotY) {// created bogus event to see y without x
                                        addresses[eventCounter]= (lasty << 12) + (349 << 1) ;                 //(0xffff&((short)buf[i]&0xff | ((short)buf[i+1]&0xff)<<8));
                                        timestamps[eventCounter]=(TICK_US*(lastts+wrapAdd)); //*TICK_US; //add in the wrap offset and convert to 1us tick
                                        eventCounter++;
                                        buffer.setNumEvents(eventCounter);
                                    }
                                    
                                    lasty = (0xFF &  buf[i]); //
                                    gotY=true;
//                                if (lasty>239) ///////debug
//                                    lasty=239;
//                                else if(lasty<0)
//                                    lasty=0;
//                                numberOfY++;
                                    
                                    //  log.info("received y");
                                }
                            }
                            break;
                        case 1: // timestamp
                            lastts=((0x3f & buf[i+1]) << 8) | (buf[i]&0xff);
                          //  log.info("received timestamp");
                            break;
                        case 2: // wrap
                            wrapAdd+=0x4000L;
                            NumberOfWrapEvents++;
                         //   log.info("wrap");
                            break;
                        case 3: // ts reset event
                            this.resetTimestamps();
                         //   log.info("reset");
                            break;
                    }
                    
//                    switch(transState){
//                        case STATE_IDLE:
//                        case STATE_GOTTS:
//                        case STATE_GOTY:
//                    }
                    

                    
              
                } // end for
                
                // write capture size
                buffer.lastCaptureLength=eventCounter-buffer.lastCaptureIndex;
                
           //     log.info("packet size " + buffer.lastCaptureLength + " number of Y addresses " + numberOfY);
                // if (NumberOfWrapEvents!=0) {
                //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                //}
                //System.out.println("wrapAdd : "+ wrapAdd);
            } // sync on aePacketRawPool
            } 
            catch (java.lang.IndexOutOfBoundsException e){
                log.warning(e.toString());
            }
        }
    }
}
