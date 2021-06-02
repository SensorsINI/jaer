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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.StringTokenizer;
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

    // tobi changed to 8k buffer (from 400k) because this has measurably better performance than super large buffer
    /**
     * buffer size for this output stream
     */
    private static final int BUFFER_EVENTS = 8192;
    private static final int SIZE_EVENT = (Integer.SIZE / 8) * 2;

    private FileChannel channel = null;
    private ByteBuffer byteBuf = null;

    private int eventCounter = 0;
    private String dataFileVersionNumber;

    /**
     * Creates a new instance of AEOutputStream and writes the header. If there
     * is any IOException a stack trace is printed.
     *
     * @param os an output stream, e.g. from
     * <code>new BufferedOutputStream(new FileOutputStream(File f)</code>.
     * @param chip (optionally) provide the chip used and write out additional
     * header info
     * @param dataFileVersionNum provide the AEDAT file data format string, e.g.
     * "2.0", "3.0", or "3.1". ("2.0" is standard AEDAT file format for pre-caer
     * records and is most stable))
     *
     * @throws java.io.IOException thrown when write to file failed
     */
    public AEFileOutputStream(final OutputStream os, final AEChip chip, String dataFileVersionNum) throws IOException {
        super(os);
        try {
            dataFileVersionNumber = dataFileVersionNum;
            writeHeaderLine(AEDataFile.DATA_FILE_FORMAT_HEADER + dataFileVersionNumber);
            writeHeaderLine(" This is a raw AE data file - do not edit");
            writeHeaderLine(" Data format is int32 address, int32 timestamp (8 bytes total), repeated for each event");
            writeHeaderLine(" Timestamps tick: " + AEConstants.TICK_DEFAULT_US + " us");
            writeHeaderLine(" Creation date: " + new Date());
            writeHeaderLine(" Creation time: System.currentTimeMillis() " + System.currentTimeMillis());
            writeHeaderLine(" User name: " + System.getProperty("user.name"));
            String computerName = null;
            try {
                computerName = InetAddress.getLocalHost().getHostName();
            } catch (Exception ex) {
                log.warning("couldn't determine local host name");
            }
            writeHeaderLine(" Hostname: " + computerName);

            // optionally write chip-specific info
            if (chip.getHardwareInterface() != null) {
                writeHeaderLine(" HardwareInterface: " + chip.getHardwareInterface().toString());
            }

            chip.writeAdditionalAEFileOutputStreamHeader(this);
            writeHeaderLine(DATA_START_TIME_SYSTEMCURRENT_TIME_MILLIS + System.currentTimeMillis());
            writeHeaderLine(END_OF_HEADER_STRING);

            if (os instanceof FileOutputStream) {
                channel = ((FileOutputStream) os).getChannel();
                AEOutputStream.log.info("using ByteBuffer with " + AEFileOutputStream.BUFFER_EVENTS + " events to buffer disk writes");
                byteBuf = ByteBuffer.allocateDirect(AEFileOutputStream.BUFFER_EVENTS * AEFileOutputStream.SIZE_EVENT);
            }
        } catch (final BackingStoreException ex) {
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
    public AEFileOutputStream(final OutputStream os) throws IOException {
        this(os, null, null);
    }

    /**
     * Writes a comment header line. Writes the string with prepended '#' and
     * appended '\r\n' (CRLF)
     *
     * @param s the string to write
     * @throws java.io.IOException when we try to write header but have already
     * written a data packet
     */
    public final void writeHeaderLine(final String s) throws IOException {
        if (wrotePacket) {
            throw new IOException("already wrote a packet, not writing the header");
        }
        writeByte(AEDataFile.COMMENT_CHAR); // '#'
        writeBytes(s);
        writeByte(AEDataFile.EOL[0]); // '\r'
        writeByte(AEDataFile.EOL[1]); // '\n'
    }

    /**
     * Writes a comment header from an input String s containing multiple lines.
     * Each line is prepended with '#'
     *
     * @param s the multiline string to write
     * @throws java.io.IOException when we try to write header but have already
     * written a data packet
     */
    public final void writeHeaderBlock(final String s) throws IOException {
        if (wrotePacket) {
            throw new IOException("already wrote a packet, not writing the header");
        }
        final StringBuilder sb = new StringBuilder();
        final StringTokenizer st = new StringTokenizer(s, System.lineSeparator(), true);
        while (st.hasMoreElements()) {
            sb.append(AEDataFile.COMMENT_CHAR);
            sb.append(st.nextToken());
        }
        writeBytes(sb.toString());
        // writeByte(AEDataFile.EOL[0]); // '\r'
        // writeByte(AEDataFile.EOL[1]); // '\n'
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
    @Override
    public void writePacket(final AEPacketRaw ae) throws IOException {
        if (ae == null) {
            return;
        }

        if (!dataFileVersionNumber.equals("2.0") && !dataFileVersionNumber.equals("3.1")) {
            log.warning("The file version is not supported.");
            return;
        }

        final int n = ae.getNumEvents();
        if (n == 0) {
            return;
        }

        final int[] addr = ae.getAddresses();
        final int[] ts = ae.getTimestamps();

        int startIdx = 0;

        if (eventCounter == 0) {
            // For first event written out, make sure that the data is not a comment char character,
            // or else the first data will be commented away as part of the file header.
            // The ByteBuffer is ByteOrder.BIG_ENDIAN (default) which means that MSB is first (lower index)

            // addr[0]=((AEDataFile.COMMENT_CHAR&0xFFFF)<<16); // DEBUG
            while ((startIdx < n) && (((addr[startIdx] & 0xFFFF0000) >>> 16) == (AEDataFile.COMMENT_CHAR & 0xFFFF))) {
                log.warning(String.format(
                        "address #%d with address value %d destined for start of data file happens to code the comment char %c, skipping this event",
                        startIdx, addr[0], AEDataFile.COMMENT_CHAR));
                startIdx++;
            }
        }

        // Check the data file version, if it's 2.0, then it just put addr and timestamp in sequence. 
        // If it's 3.1, then we should add packet header for every different event types.
        if (dataFileVersionNumber.equals("2.0")) {
            for (int i = startIdx; i < n; i++) {
                byteBuf.putInt(addr[i]);
                byteBuf.putInt(ts[i]);

                eventCounter++;

                if (byteBuf.remaining() < AEFileOutputStream.SIZE_EVENT) {
                    byteBuf.rewind();
                    channel.write(byteBuf);
                    byteBuf.clear();
                }
            }

            wrotePacket = true;
        }

    }

    @Override
    public void close() throws IOException {
        // Flush last buffer to file, to avoid loosing small amounts of data.
        byteBuf.flip();
        channel.write(byteBuf);
        byteBuf.clear();

        channel.close();
        byteBuf = null;

        super.close();

        AEOutputStream.log.info("wrote " + eventCounter + " events");
    }
}
