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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
/**
 * Streams out packets of events in binary. The only difference to AEOuputStream is the addition of a file header in text format.
 * 
 * @author tobi
 */
public class AEFileOutputStream extends AEOutputStream implements AEDataFile {
        
    // tobi changed to 8k buffer (from 400k) because this has measurablly better performance than super large buffer
    /** buffer size for this output stream */
    public static final int OUTPUT_BUFFER_SIZE = 8192; 
    
    /** 
     * Creates a new instance of AEOutputStream and writes the header. If there is any IOException a stack trace is printed.
     *
     * @param os an output stream, e.g. from <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     * @param chip (optionally) provide the chip used and write out additional header info
     * @throws java.io.IOException thrown when write to file failed
     */
    public AEFileOutputStream (OutputStream os, AEChip chip) throws IOException{
        super(os);
        try{
            writeHeaderLine(AEDataFile.DATA_FILE_FORMAT_HEADER + AEDataFile.DATA_FILE_VERSION_NUMBER);
            writeHeaderLine(" This is a raw AE data file - do not edit");
            writeHeaderLine(" Data format is int32 address, int32 timestamp (8 bytes total), repeated for each event");
            writeHeaderLine(" Timestamps tick is " + AEConstants.TICK_DEFAULT_US + " us");
            writeHeaderLine(" created " + new Date());
            
            // optionally write chip-specific info
            if(chip != null) {
                chip.writeAdditionalAEFileOutputStreamHeader(this);
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(AEFileOutputStream.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex.getMessage());
        }
    }
    
    /**
     * * Creates a new instance of AEOutputStream and writes the header.
     *
     * @param os an output stream, e.g. from <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     * @throws java.io.IOException thrown when write to file failed
     */
    public AEFileOutputStream(OutputStream os) throws IOException {
        this(os,null);
    }
    

    /**
     * Writes a comment header line. Writes the string with prepended '#' and appended '\r\n'
     * @param s the string to write
     * @throws java.io.IOException when we try to write header but have already written a data packet
     */
    public final void writeHeaderLine (String s) throws IOException{
        if ( wrotePacket ){
            throw new IOException("already wrote a packet, not writing the header");
        }
        writeByte(COMMENT_CHAR); //'#'
        writeBytes(s);
        writeByte(EOL[0]); //'\r'
        writeByte(EOL[1]); //'\n'
    }
}

