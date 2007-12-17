/*
 * AE3DOutputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.stereo3D;

import ch.unizh.ini.caviar.aemonitor.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

/**
 * Class to stream out packets of 3D reconstructed events in binary. The file format is
 <br>
 int32 x<br>
 int32 y<br>
 int32 z<br>
 float value<br>
 int32 timestamp
 <br>
 repeated for the number of events in the file.
 
 * @author tobi
 */
public class AE3DOutputStream extends DataOutputStream {
    
    private static Logger log=Logger.getLogger("AE3DOutputStream"); //?
    
    private boolean writeHeader=true;
    private boolean wrotePacket=false;
    
//    ByteArrayOutputStream bos=new ByteArrayOutputStream();
    
    /** 
     Creates a new instance of AEOutputStream.
     *
     @param os an output stream, e.g. from <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     */
    public AE3DOutputStream(OutputStream os) {
        super(os);
        if(writeHeader){
            try{
                writeHeaderLine("!3D-AER-DAT1.0");
                writeHeaderLine(" This is a 3D AE data file - do not edit");
                writeHeaderLine(" Data format is int32 x, int32 y, int 32 z, float value, int32 timestamp (6 bytes total), repeated for each event");
                writeHeaderLine(" Timestamps tick is "+AEConstants.TICK_DEFAULT_US+" us");
                writeHeaderLine(" created "+new Date());
                
                // plus focal length and other retina parameters?? paul
                
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Writes the packet out as sequence of address/timestamp's, just as they came as input from the device. The notion of a packet is discarded
     *to simplify later reading an input stream from the output stream result.
     *@param ae a raw addresse-event packet
     */
    public void writePacket(AEPacket3D ae) throws IOException {
        int n=ae.getNumEvents();
        int[] addrx=ae.getCoordinates3D_x();
        int[] addry=ae.getCoordinates3D_y();
        int[] addrz=ae.getCoordinates3D_z();
        float[] values=ae.getValues();
        int[] ts=ae.getTimestamps();
//        writeInt(n);
        for(int i=0;i<n;i++){
            writeInt(addrx[i]);
            writeInt(addry[i]);
            writeInt(addrz[i]);
            writeFloat(values[i]); // ?
            writeInt(ts[i]);
        }
        wrotePacket=true;
    }
    
    
    
    
    /**
     Writes a comment header line. Writes the string with prepended '#' and appended '\r\n'
     @param s the string
     */
    public void writeHeaderLine(String s) throws IOException {
        if(wrotePacket){
            log.warning("already wrote a packet, not writing the header");
            return;
        }
        writeByte('#');
        writeBytes(s);
        writeByte('\r');
        writeByte('\n');
    }
    
}

