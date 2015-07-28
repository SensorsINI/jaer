/*
 * AEOutputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;

/**
 * Streams out packets of events in binary. The only difference to AEOuputStream
 * is the addition of a file header in text format.
 *
 * @author tobi
 */
public class AEFileOutputStream extends AEOutputStream implements AEDataFile {

    // tobi changed to 8k buffer (from 400k) because this has measurablly better performance than super large buffer
    /**
     * buffer size for this output stream
     */
    public static final int OUTPUT_BUFFER_SIZE = 8192 * 4;
    protected FileChannel channel = null;
    ByteBuffer byteBuf = null;
//    IntBuffer intBuf = null;
    protected int BUF_SIZE_EVENTS = 8192;
    int eventCounter = 0;

    /**
     * Creates a new instance of AEOutputStream and writes the header. If there
     * is any IOException a stack trace is printed.
     *
     * @param os an output stream, e.g. from
     * <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     * @param chip (optionally) provide the chip used and write out additional
     * header info
     * @throws java.io.IOException thrown when write to file failed
     */
    public AEFileOutputStream(OutputStream os, AEChip chip) throws IOException {
        super(os);
        try {
            writeHeaderLine(AEDataFile.DATA_FILE_FORMAT_HEADER + AEDataFile.DATA_FILE_VERSION_NUMBER);
            writeHeaderLine(" This is a raw AE data file - do not edit");
            writeHeaderLine(" Data format is int32 address, int32 timestamp (8 bytes total), repeated for each event");
            writeHeaderLine(" Timestamps tick is " + AEConstants.TICK_DEFAULT_US + " us");
            writeHeaderLine(" created " + new Date());

            // optionally write chip-specific info
            if(chip.getHardwareInterface()!=null){
                writeHeaderLine(" HardwareInterface: "+ chip.getHardwareInterface().toString());
            }
            if (chip != null) {
                chip.writeAdditionalAEFileOutputStreamHeader(this);
            }
            if (os instanceof FileOutputStream) {
                channel = ((FileOutputStream) os).getChannel();
                int bufSizeEvents = BUF_SIZE_EVENTS;
                log.info("using ByteBuffer with " + bufSizeEvents + " events to buffer disk writes");
                byteBuf = ByteBuffer.allocateDirect(bufSizeEvents * Integer.SIZE / 8 * 2);
//                byteBuf.order(ByteOrder.LITTLE_ENDIAN);
//                intBuf = byteBuf.asIntBuffer();
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(AEFileOutputStream.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex.getMessage());
        }
        eventCounter = 0;
    }

    /**
     * * Creates a new instance of AEOutputStream and writes the header.
     *
     * @param os an output stream, e.g. from
     * <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     * @throws java.io.IOException thrown when write to file failed
     */
    public AEFileOutputStream(OutputStream os) throws IOException {
        this(os, null);
    }

    /**
     * Writes a comment header line. Writes the string with prepended '#' and
     * appended '\r\n'
     *
     * @param s the string to write
     * @throws java.io.IOException when we try to write header but have already
     * written a data packet
     */
    ;
    public final void writeHeaderLine(String s) throws IOException {
        if (wrotePacket) {
            throw new IOException("already wrote a packet, not writing the header");
        }
        writeByte(COMMENT_CHAR); //'#'
        writeBytes(s);
        writeByte(EOL[0]); //'\r'
        writeByte(EOL[1]); //'\n'
    }

    /**
     * Writes the raw (device) address-event packet out as sequence of
     * address/timestamps, just as they came as input from the device. The
     * notion of a packet is discarded to simplify later reading an input stream
     * from the output stream result. A null or empty packet returns immediately
     * without writing anything.
     *
     * @param ae a raw address-event packet
     */
    public void writePacket(AEPacketRaw ae) throws IOException {
        if (ae == null) {
            return;
        }
        int n = ae.getNumEvents();
        if (n == 0) {
            return;
        }
        int[] addr = ae.getAddresses();
        int[] ts = ae.getTimestamps();
//        writeInt(n);
        for (int i = 0; i < n; i++) {
            byteBuf.putInt(addr[i]);// changed from writeShort with change to int raw addressses in v2.0 format
            byteBuf.putInt(ts[i]);
//            if(ts[i]==0){
//                log.info("zero timestamp at event "+i+" (eventCounter="+eventCounter+")");
//            }
            eventCounter++;
            if (byteBuf.remaining() < AEFileInputStream.EVENT32_SIZE) {
                byteBuf.rewind();
                channel.write(byteBuf);
                byteBuf.clear();
            }

        }
        wrotePacket = true;
    }

    @Override
    public void close() throws IOException {
//        channel.force(true);
        channel.close();
        byteBuf = null;
        super.close();
//        intBuf=null;
        log.info("wrote "+eventCounter+" events");
    }

}
