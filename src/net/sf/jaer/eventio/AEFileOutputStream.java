/*
 * AEOutputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import net.sf.jaer.aemonitor.AEConstants;
/**
 * Streams out packets of events in binary. The only difference to AEOuputStream is the addition of a file header in text format.
 * 
 * @author tobi
 */
public class AEFileOutputStream extends AEOutputStream{
    /** 
    Creates a new instance of AEOutputStream and writes the header. If there is any IOException a stack trace is printed.
     *
    @param os an output stream, e.g. from <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     */
    public AEFileOutputStream (OutputStream os){
        super(os);
        try{
            writeHeaderLine(AEDataFile.DATA_FILE_FORMAT_HEADER + AEDataFile.DATA_FILE_VERSION_NUMBER);
            writeHeaderLine(" This is a raw AE data file - do not edit");
            writeHeaderLine(" Data format is int32 address, int32 timestamp (8 bytes total), repeated for each event");
            writeHeaderLine(" Timestamps tick is " + AEConstants.TICK_DEFAULT_US + " us");
            writeHeaderLine(" created " + new Date());
        } catch ( IOException e ){
            e.printStackTrace();
        }
    }

    /**
    Writes a comment header line. Writes the string with prepended '#' and appended '\r\n'
    @param s the string
     */
    public void writeHeaderLine (String s) throws IOException{
        if ( wrotePacket ){
            log.warning("already wrote a packet, not writing the header");
            return;
        }
        writeByte('#');
        writeBytes(s);
        writeByte('\r');
        writeByte('\n');
    }
}

