/*
 * Copyright (C) 2024 tobi.
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
package net.sf.jaer.eventio;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.PrefObj;
import net.sf.jaer.util.textio.AbstractDavisTextIo;
import net.sf.jaer.util.textio.BufferedRandomAccessFile;

/**
 * Reads text file input streams as in DavisTextInputReader (from where most of
 * the code comes from)
 *
 * @author tobi
 */
public class TextFileInputStream extends BufferedInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String FILE_EXTENSION_TXT = "txt", FILE_EXTENSION_CSV = "csv";
    /**
     * BufferedRandomAccessFile buffer size in bytes
     */
    private static final int BUFFER_SIZE_BYTES = 10_0000_0000;
    /**
     * // interval to update position to save a lot of Swing calls
     */
    private static final int POSITION_PROPERTY_CHANGE_EVENT_INTERVAL = 10_0000;

    static private int positionUpdates = 0; // counter to avoid too many Swing updates

    /**
     * The AEChip object associated with this stream.
     */
    private AEChip chip = null;
    /**
     * The chip's event extractor, used to reconstruct raw packet from
     * ApsDvsEvent's
     */
    private EventExtractor2D eventExtractor = null;

    /**
     * The input file
     */
    private File file = null;

    /* length of input file in bytes */
    private long fileLength = 0;
    /**
     * Preferences to hold state over runs
     */
    private Preferences prefs = Preferences.userNodeForPackage(this.getClass());

    /**
     * Stores the canonical (unique) file path as the key and an array of 2
     * longs [filelength, numevents]
     */
    private HashMap<String, Long[]> previousFilesHashMap = null;
    private final static int PREV_FILE_LENGTH_INDEX = 0, PREV_NUM_EVENTS_INDEX = 1;

    // preference items
    protected boolean useCSV = prefs.getBoolean("useCSV", false);
    protected boolean useUsTimestamps = prefs.getBoolean("useUsTimestamps", false);
    protected boolean useSignedPolarity = prefs.getBoolean("useSignedPolarity", false);
    protected boolean timestampLast = prefs.getBoolean("timestampLast", false);
    private boolean specialEvents = prefs.getBoolean("specialEvents", false);
    private boolean readTimestampsAsLongAndSubtractFirst = prefs.getBoolean("readTimestampsAsLongAndSubtractFirst", false);
    protected boolean nonMonotonicTimestampsChecked = prefs.getBoolean("nonMonotonicTimestampsChecked", true);

    private BufferedRandomAccessFile reader = null;

    // state of stream
    private boolean noEventsReadYet = true; // set false when new file is opened
    private long numEventsInFile = 0;
    /**
     * the last timestamp read by readNextEvent
     */
    private int mostRecentTimestamp = 0;
    /**
     * The previous timestamp read by readNextEvent
     */
    private int previousTimestamp = 0;
    /**
     * the next event number to be read by readNextEvent (not the one that has
     * just been read
     */
    private long position = 0;
    /**
     * the last line read by readNextEvent
     */
    private String lastLineRead = null;
    /**
     * Last line number (event) read by readNextEvent
     */
    private int lastLineNumber = 0;

    /**
     * the first timestamp in file
     */
    private int firstTimestamp;

    private long firstLongTimestgamp = 0; // for parsing int timestamps in file that are larger than Integer.MAX_VALUE
    /**
     * the last timestamp in file
     */
    private int lastTimestamp = 0;

    /**
     * the reused output packet
     */
    private ApsDvsEventPacket outputPacket = null;
    /**
     * Used to hold dummy output for readNextEvent(null)
     */
    private ApsDvsEvent dummyEvent = new ApsDvsEvent(); // used to hold output when there is no output packet supplied to readNextEvent
    /**
     * the reused output packet
     */
    private AEPacketRaw aePacketRaw = null;

    private boolean repeat;

    private final static int ERROR_DELAY_MS = 1000;

    protected boolean checkNonMonotonicTimestamps = prefs.getBoolean("checkNonMonotonicTimestamps", true);
    private boolean openFileAndRecordAedat = false;

    protected boolean flipPolarity = prefs.getBoolean("flipPolarity", false);
    private boolean flipX = prefs.getBoolean("flipX", false);
    private boolean flipY = prefs.getBoolean("flipY", false);

    final int SPECIAL_COL = 4; // location of special flag (0 normal, 1 special) 

    private long markIn = 0, markOut = Long.MAX_VALUE;
    // following are to seek in file by bytes
    private long markInSeekPosition = 0, markOutSeekPosition = Long.MAX_VALUE;

    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    private float avgLineLength = 15; // TODO guesstimate, measuring during initial open
    private int startTimestamp = 0; // timestamp that packet starts at while readPacketByTime runs, reset by rewind
    private TextFileInputStreamOptionsDialog optionsDialog=null;

    /**
     * Construct and open the text file input stream from a file that has one
     * DVS event per line
     *
     * @param f the File
     * @param chip the AEChip (needed to construct the raw events that the
     * AEChip EventExtractor uses
     * @param progressMonitor, optional progress monitor to show progress of
     * opening file, if null one is constructed.
     * @throws IOException
     */
    public TextFileInputStream(File f, AEChip chip, ProgressMonitor progressMonitor) throws IOException {
        super(new FileInputStream(f));
        if (chip == null) {
            throw new NullPointerException("null AEchip, need it to construct the raw output events");
        }
        this.chip = chip;
        setFile(f);

        eventExtractor = chip.getEventExtractor();
        if (eventExtractor == null) {
            throw new NullPointerException(String.format("This AEChip %s has no event extractor to reconstruct raw events from the t,x,y,p DVS events", this.chip));
        }
        outputPacket = new ApsDvsEventPacket(ApsDvsEvent.class);
        outputPacket.allocate(32768);
        aePacketRaw = new AEPacketRaw();

        if (progressMonitor == null) {
            log.warning("Supplied progress monitor is null so we construct one to show progress of counting lines");
            progressMonitor = new ProgressMonitor(chip.getAeViewer(), "Counting lines", null, 0, 100);
        }
        init(progressMonitor);
    }

    private void delayForError() {
        try {
            Thread.sleep(ERROR_DELAY_MS);
        } catch (InterruptedException ex) {
            log.info("Interrupted error delay");
        }
    }

    /**
     * Read a packet with at least n events in it
     *
     * @param n the number of events to read
     * @return the raw packet, or null if there is a format error in a line
     * @throws IOException if there is a file error
     */
    @Override
    synchronized public AEPacketRaw readPacketByNumber(int n) throws IOException {
        startTimestamp = mostRecentTimestamp;
        OutputEventIterator outItr = outputPacket.outputIterator();
        try {
            for (int i = 0; i < n && !Thread.interrupted(); i++) {
                readNextEvent(outItr);
            }
            AEPacketRaw rawPacket = chip.getEventExtractor().reconstructRawPacket(outputPacket);
            return rawPacket;
        } catch (EventFormatException e) {
            delayForError();
        } catch (EOFException e) {
            rewind();
        }
        return null;
    }

    /**
     * Read a packet with at least dt us of events in it
     *
     * @param dt the delta time (duration) in us
     * @return the raw packet, or null if there is a format error in a line
     * @throws IOException if there is a file error
     */
    @Override
    synchronized public AEPacketRaw readPacketByTime(int dt) throws IOException {
        OutputEventIterator outItr = outputPacket.outputIterator();
        try {
            startTimestamp = mostRecentTimestamp;
            while (mostRecentTimestamp < startTimestamp + dt) {
                readNextEvent(outItr);
            }
            AEPacketRaw rawPacket = chip.getEventExtractor().reconstructRawPacket(outputPacket);
            return rawPacket;
        } catch (EventFormatException e) {
            delayForError();
        } catch (EOFException e) {
            rewind();
        }
        return null;
    }

    /**
     * Reads next event into output packet using its OutputPacketIterator
     *
     * @param outItr or null. If null, returns a dummy event
     * @return the event just read
     * @throws IOException if there is an error with file
     * @throws net.sf.jaer.eventio.TextFileInputStream.EventFormatException if
     * there is some problem in the line format
     * @throws EOFException at end of file or markOut mark
     */
    private ApsDvsEvent readNextEvent(OutputEventIterator outItr) throws IOException, EventFormatException, EOFException {
        if (reader == null) {
            throw new NullPointerException("the BufferedRandomAccessFile is null");
        }
        if (isMarkOutSet() && position() >= getMarkOutPosition()) {
            throw new EOFException("reached markOut position");
        }
        if (position >= numEventsInFile) {
            throw new EOFException("reached end of file");
        }
        String line = null;
        try {
            line = reader.getNextLine();
            while (line == null) {
                if (line == null) {
                    throw new EOFException(String.format("reached end of file after %,d events", lastLineNumber));
                }
                if (line.startsWith("#")) {
                    log.log(Level.INFO, "Comment: {0}", line);
                    line = reader.getNextLine();
                }
            }
        } catch (EOFException e) {
            throw new EOFException(String.format("%s at event %,d; lastLineNumber=%,d, lastLineRead=%s", e.toString(), position(), lastLineNumber, lastLineRead));
        } catch (IOException e) {
            throw new IOException(String.format("%s at event %,d; lastLineNumber=%,d, lastLineRead=%s", e.toString(), position(), lastLineNumber, lastLineRead));
        }
        try {
            String[] split = useCSV ? line.split(",") : line.split(" "); // split by comma or space
            if (split == null || (!isSpecialEvents() && split.length != 4) || (isSpecialEvents() && split.length != 5)) {
                String s = String.format("Only %d tokens but needs 4 without and 5 with specialEvents:\n%s\nCheck useCSV for ',' or space separator", split.length, lineinfo(line));
                throwLineFormatException(s);
            }

            int ix, iy, ip, it;
            if (timestampLast) {
                ix = 0;
                iy = 1;
                ip = 2;
                it = 3;
            } else {
                ix = 1;
                iy = 2;
                ip = 3;
                it = 0;
            }
            if (useUsTimestamps && split[it].contains(".")) {
                throwLineFormatException(String.format("timestamp %s has a '.' in it but useUsTimestamps is true\n%s", split[it], lineinfo(line)));
            } else if (!useUsTimestamps && !split[it].contains(".")) {
                throwLineFormatException(String.format("timestamp %s has no '.' in it but useUsTimestamps is false\n%s", split[it], lineinfo(line)));
            }
            if (useUsTimestamps) {
                if (readTimestampsAsLongAndSubtractFirst) {
                    long tsLong = Long.parseLong(split[it]);
                    if (noEventsReadYet) {
                        firstLongTimestgamp = tsLong;
                        noEventsReadYet = false;
                        log.info(String.format("First (long) timestamp is %,d", firstLongTimestgamp));
                    }
                    mostRecentTimestamp=(int)(tsLong-firstLongTimestgamp);
                } else {
                    long tsLong = Long.parseLong(split[it]);
                    if (tsLong > Integer.MAX_VALUE) {
                        String s = String.format("Int timestamp %s is larger than Integer.MAX_VALUE (%,d) (line %s)", split[it], Integer.MAX_VALUE, lineinfo(line));
                        throwLineFormatException(s);
                    }
                    mostRecentTimestamp=(int)tsLong;
                }
            } else {
                mostRecentTimestamp = (int) (Float.parseFloat(split[it]) * 1000000);
            }
//            mostRecentTimestamp = useUsTimestamps ? Integer.parseInt(split[it]) : (int) (Float.parseFloat(split[it]) * 1000000);
            previousTimestamp = mostRecentTimestamp;
            if (noEventsReadYet) {
                firstTimestamp = mostRecentTimestamp;
                noEventsReadYet = false;
                log.info(String.format("First timestamp is %,d", mostRecentTimestamp));
            }

            int dt = mostRecentTimestamp - previousTimestamp;
            if (nonMonotonicTimestampsChecked && dt < 0) {
                String s = String.format("timestamp %,d is %,d us earlier than previous %,d at %s", mostRecentTimestamp, dt, previousTimestamp, lineinfo(line));
                log.warning(s);
            }
            short x = Short.parseShort(split[ix]);
            short y = Short.parseShort(split[iy]);
            byte pol = Byte.parseByte(split[ip]);

            if (x < 0 || x >= chip.getSizeX() || y < 0 || y >= chip.getSizeY()) {
                throwLineFormatException(String.format("address x=%d y=%d outside of AEChip allowed range maxX=%d, maxY=%d: , ignoring. %s\nAre you using the correct AEChip?", x, y, chip.getSizeX(), chip.getSizeY(), lineinfo(line)));
            }
            PolarityEvent.Polarity polType = PolarityEvent.Polarity.Off;
            if (useSignedPolarity) {
                if (pol != -1 && pol != 1) {
                    throwLineFormatException(String.format("polarity %d is not valid (check useSignedPolarity flag), ignoring. %s", pol, lineinfo(line)));
                } else if (pol == 1) {
                    polType = PolarityEvent.Polarity.On;
                }
            } else {
                if (pol < 0 || pol > 1) {
                    throwLineFormatException(String.format("polarity %d is not valid (check useSignedPolarity flag), ignoring. %s", pol, lineinfo(line)));
                } else if (pol == 1) {
                    polType = PolarityEvent.Polarity.On;
                }
            }

            ApsDvsEvent e = outItr != null ? (ApsDvsEvent) outItr.nextOutput() : dummyEvent;
            e.setFilteredOut(false);
            e.setTimestamp(mostRecentTimestamp);
            e.setX(!flipX ? x : (short) (chip.getSizeX() - x - 1));
            e.setY(!flipY ? y : (short) (chip.getSizeY() - y - 1));
            e.setPolarity(polType);
            if (flipPolarity) {
                e.flipPolarity();
            }
            e.setType(polType == PolarityEvent.Polarity.Off ? (byte) 0 : (byte) 1);
            e.setDvsType();
            if (isSpecialEvents()) {
                int specialFlag = Integer.parseInt(split[SPECIAL_COL]);
                if (specialFlag == 0) {
                    e.setSpecial(false);
                } else if (specialFlag == 1) {
                    e.setSpecial(true);
                } else {
                    String s = String.format("Line #%d has Unknown type of special event, must be 0 (normal) or 1 (special):\n\"%s\"", lastLineNumber, line);
                    throwLineFormatException(s);
                }
            }

            setPositionValue(position + 1);
            lastLineNumber++;
            lastLineRead = line;

            return e;
        } catch (NumberFormatException nfe) {
            String s = String.format("%s: Line #%d has a bad number format: \"%s\"; check options; maybe you should set timestampLast?", nfe.toString(), lastLineNumber, line);
            throwLineFormatException(s);
        }
        return null;
    }

    private class EventFormatException extends NumberFormatException {

        private String s;
        private Throwable e;

        public EventFormatException(String s, Throwable e) {
            this.s = s;
            this.e = e;
        }

        public String toString() {
            return String.format("%s: caused by %s", s, e != null ? e.toString() : null);
        }
    }

    private String lineinfo(String line) {
        return String.format("Line #%d: \"%s\"", lastLineNumber, line);
    }

    /**
     * @return the checkNonMonotonicTimestamps
     */
    public boolean isCheckNonMonotonicTimestamps() {
        return nonMonotonicTimestampsChecked;
    }

    /**
     * @param checkNonMonotonicTimestamps the checkNonMonotonicTimestamps to set
     */
    public void setCheckNonMonotonicTimestamps(boolean nonMonotonicTimestampsChecked) {
        this.nonMonotonicTimestampsChecked = nonMonotonicTimestampsChecked;
        prefs.putBoolean("nonMonotonicTimestampsChecked", nonMonotonicTimestampsChecked);
    }

    /**
     * @return the flipX
     */
    public boolean isFlipX() {
        return flipX;
    }

    /**
     * @param flipX the flipX to set
     */
    public void setFlipX(boolean flipX) {
        this.flipX = flipX;
        prefs.putBoolean("flipX", flipX);
    }

    /**
     * @return the flipY
     */
    public boolean isFlipY() {
        return flipY;
    }

    /**
     * @param flipY the flipY to set
     */
    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
        prefs.putBoolean("flipY", flipY);

    }

    /**
     * @return the flipPolarity
     */
    public boolean isFlipPolarity() {
        return flipPolarity;
    }

    /**
     * @param flipPolarity the flipPolarity to set
     */
    public void setFlipPolarity(boolean flipPolarity) {
        String oldFormat = getFormattingHelpString();
        this.flipPolarity = flipPolarity;
        prefs.putBoolean("flipPolarity", flipPolarity);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    /**
     * @return the timestampLast
     */
    public boolean isTimestampLast() {
        return timestampLast;
    }

    /**
     * @param timestampLast the timestampLast to set
     */
    public void setTimestampLast(boolean timestampLast) {
        String oldFormat = getFormattingHelpString();
        this.timestampLast = timestampLast;
        prefs.putBoolean("timestampLast", timestampLast);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    /**
     * @return the readTimestampsAsLongAndSubtractFirst
     */
    public boolean isReadTimestampsAsLongAndSubtractFirst() {
        return readTimestampsAsLongAndSubtractFirst;
    }

    /**
     * @param readTimestampsAsLongAndSubtractFirst the
     * readTimestampsAsLongAndSubtractFirst to set
     */
    public void setReadTimestampsAsLongAndSubtractFirst(boolean readTimestampsAsLongAndSubtractFirst) {
        String oldFormat = getFormattingHelpString();
        this.readTimestampsAsLongAndSubtractFirst = readTimestampsAsLongAndSubtractFirst;
        prefs.putBoolean("readTimestampsAsLongAndSubtractFirst", readTimestampsAsLongAndSubtractFirst);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    /**
     * @return the useCSV
     */
    public boolean isUseCSV() {
        return useCSV;
    }

    /**
     * @param useCSV the useCSV to set
     */
    public void setUseCSV(boolean useCSV) {
        String oldFormat = getFormattingHelpString();
        this.useCSV = useCSV;
        prefs.putBoolean("useCSV", useCSV);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    /**
     * @return the useUsTimestamps
     */
    public boolean isUseUsTimestamps() {
        return useUsTimestamps;
    }

    /**
     * @param useUsTimestamps the useUsTimestamps to set
     */
    public void setUseUsTimestamps(boolean useUsTimestamps) {
        String oldFormat = getFormattingHelpString();
        this.useUsTimestamps = useUsTimestamps;
        prefs.putBoolean("useUsTimestamps", useUsTimestamps);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    private void throwLineFormatException(String s) throws EventFormatException {
        log.warning(s);
        getSupport().firePropertyChange("sampleLine", null, lastLineRead);
        getSupport().firePropertyChange("lastError", null, s);
        throw new EventFormatException(s, null);
    }

    /**
     * fires property change "position".
     *
     * @throws IOException if file is empty or there is some other error.
     */
    private void init(ProgressMonitor progressMonitor) throws IOException {
        try {
            try {
                this.previousFilesHashMap = (HashMap) getObject("previousFilesHashMap", new HashMap());
            } catch (Exception e) {
                log.warning(String.format("Making new previousFilesHashMap; could not load previousFilesHashMap from preferences: %s", e.toString()));
                previousFilesHashMap = new HashMap<String, Long[]>();
            }
            this.fileLength = this.file.length();
            final String canonicalPath = file.getCanonicalPath();
            boolean success = false;
            if (previousFilesHashMap.containsKey(canonicalPath)) {
                Object o = previousFilesHashMap.get(canonicalPath);
                if (o instanceof Long[]) {
                    Long[] vals = (Long[]) o;
                    if (vals[PREV_FILE_LENGTH_INDEX] == fileLength) {
                        numEventsInFile = vals[PREV_NUM_EVENTS_INDEX];
                        log.info(String.format("found file '%s' in previousFilesHashMap with same length %,d bytes that we read before that has %,d events", canonicalPath, fileLength, numEventsInFile));
                        success = true;
                    }
                }
            }
            if (!success) {
                log.info(String.format("file '%s' with length %,d bytes not found in previousFilesHashMap, counting events...", canonicalPath, this.fileLength));
                countEvents(this.file, progressMonitor);
            }

            reader = openReader(this.file);
        } catch (InterruptedException ie) {
            log.info("interrupted event counting");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(chip.getAeViewer(), ex.toString(), "Couldn't open input file", JOptionPane.WARNING_MESSAGE, null);
            throw ex;
        } finally {
            chip.getAeViewer().setCursor(null);
        }

    }

    void eraseFileHashMap() {
        previousFilesHashMap = new HashMap<String, Long[]>();
        putObject("previousFilesHashMap", previousFilesHashMap);
        log.info("erased hashed file map");
    }

    /**
     * Opens text input stream and optionally the timecode file, and enable
     * reading this stream.
     *
     * @param f the file
     * @return the stream, or null if IOException occurs
     * @throws java.io.IOException if there is an error opening the file
     * @throws java.lang.InterruptedException if open is interrupted
     *
     */
    private BufferedRandomAccessFile openReader(File f) throws IOException, InterruptedException {
//        https://stackoverflow.com/questions/1277880/how-can-i-get-the-count-of-line-in-a-file-in-an-efficient-way

        optionsDialog = new TextFileInputStreamOptionsDialog(chip.getAeViewer(), false, this); // non-model to allow correcting format while reading file
        optionsDialog.setVisible(true);

        //        RandomAccessFile reader = new RandomAccessFile(f, "r");
        // http://www.javaworld.com/javaworld/javatips/jw-javatip26.html
        // https://pdfbox.apache.org/docs/2.0.8/javadocs/org/apache/fontbox/ttf/BufferedRandomAccessFile.html
        // from https://raw.githubusercontent.com/apache/pdfbox/a27ee917bea372a1c940f74ae45fba94ba220b57/fontbox/src/main/java/org/apache/fontbox/ttf/BufferedRandomAccessFile.java
        BufferedRandomAccessFile reader = new BufferedRandomAccessFile(f, "r", BUFFER_SIZE_BYTES);
        setPositionValue(0);
        noEventsReadYet = true;
        lastLineNumber = 0;
        log.info(String.format("Opened text input file %s with %,d events", f.toString(), numEventsInFile));
        return reader;
    }

    @Override
    public String toString() {
        EngineeringFormat fmt = new EngineeringFormat();
        String s = "TextFileInputStream with size=" + fmt.format(size()) + " events, firstTimestamp=" + getFirstTimestamp() + " lastTimestamp="
                + getLastTimestamp() + " duration=" + fmt.format(getDurationUs() / 1e6f) + "s" + " event rate="
                + fmt.format(size() / (getDurationUs() / 1e6f)) + " eps";
        return s;
    }

    /**
     * @return the eventsProcessed
     */
    public long getEventsProcessed() {
        return position;
    }

    /**
     * Use this to update the position field. It fires propertyChange events
     * only every POSITION_PROPERTY_CHANGE_EVENT_INTERVAL for GUI efficiency.
     *
     * @param newPosition the eventsProcessed to set
     */
    public void setPositionValue(long newPosition) {
        long old = this.position;
        this.position = newPosition;
        if (old != this.position) {
            if (positionUpdates < 2 || (positionUpdates % POSITION_PROPERTY_CHANGE_EVENT_INTERVAL == 0)) {
                getSupport().firePropertyChange(AEInputStream.EVENT_POSITION, old, newPosition);
            }
            positionUpdates++;
        }
    }

    private float getFilePercent() {
        final float filePercent = 100 * (float) position / numEventsInFile;
        return filePercent;
    }

    private void countEvents(final File f, final ProgressMonitor progressMonitor) throws IOException, InterruptedException {
        progressMonitor.setMillisToPopup(300);
        progressMonitor.setMillisToDecideToPopup(300);
        final SwingWorker<Void, Void> worker = new SwingWorker() {
            Exception exception = null;

            @Override
            protected Object doInBackground() throws Exception {
                try {
                    chip.getAeViewer().setPaused(true);
                    chip.getAeViewer().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
//                    progressMonitor.setNote("Opening " + file);
                    progressMonitor.setProgress(0);
//                    TimeUnit.SECONDS.sleep(10);
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    long fileLength = f.length();

                    int numChars = 0;
                    numEventsInFile = 0;
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        numEventsInFile++;
                        numChars += line.length();
                        avgLineLength = (float) numChars / numEventsInFile;
                        int estTotalEvents = (int) (fileLength / avgLineLength);
                        if (isCancelled()) {
                            log.info("Cancelled event counting");
                            return null;
                        }
                        if (numEventsInFile % POSITION_PROPERTY_CHANGE_EVENT_INTERVAL == 0) {
                            int percentComplete = (int) (100 * (float) numEventsInFile / estTotalEvents);
                            setProgress(percentComplete);
                            log.info(String.format("Counting events: %,d events, %d%% complete", numEventsInFile, percentComplete));
                        }
                    }
                    setProgress(100);
                    previousFilesHashMap.put(file.getCanonicalPath(), new Long[]{file.length(), numEventsInFile});
                    putObject("previousFilesHashMap", previousFilesHashMap); // store in prefs
                    log.info(String.format("stored size %,d events from %s in previousFilesHashMap", numEventsInFile, file.getCanonicalPath()));

                    reader.close();

                } catch (Exception e) {
                    exception = e;
                    log.warning("other type of exception " + e.toString());
                } finally {
                    chip.getAeViewer().setCursor(Cursor.getDefaultCursor());
                    chip.getAeViewer().setPaused(false);
                }
                return null;
            }

            @Override
            protected void done() {
                progressMonitor.close();
            }

        };
        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getSource() == worker) {
                    if (evt.getPropertyName().equals("progress")) {
                        progressMonitor.setProgress((Integer) evt.getNewValue());
                    }
                    if (progressMonitor.isCanceled()) {
                        worker.cancel(true);
                    }
                }
            }
        });

        worker.execute();
    }

    /**
     * Returns the File that is being read, or null if the instance is
     * constructed from a FileInputStream
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the File reference but doesn't open the file
     */
    final public void setFile(File f) {
        this.file = f;
    }

    /**
     * @return the specialEvents
     */
    public boolean isSpecialEvents() {
        return specialEvents;
    }

    /**
     * @param specialEvents the specialEvents to set
     */
    public void setSpecialEvents(boolean specialEvents) {
        String oldFormat = getFormattingHelpString();
        this.specialEvents = specialEvents;
        prefs.putBoolean("specialEvents", specialEvents);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    /**
     * @return the useSignedPolarity
     */
    public boolean isUseSignedPolarity() {
        return useSignedPolarity;
    }

    /**
     * @param useSignedPolarity the useSignedPolarity to set
     */
    public void setUseSignedPolarity(boolean useSignedPolarity) {
        String oldFormat = getFormattingHelpString();
        this.useSignedPolarity = useSignedPolarity;
        prefs.putBoolean("useSignedPolarity", useSignedPolarity);
        getSupport().firePropertyChange("format", oldFormat, getFormattingHelpString());
    }

    public void doSetToRPGFormat() {
        /* "<html>Reads in text format files with DVS data from DAVIS and DVS cameras."
        + "<p>Input format is compatible with <a href=\"http://rpg.ifi.uzh.ch/davis_data.html\">rpg.ifi.uzh.ch/davis_data.html</a>"
        + "i.e. one DVS event per line,  <i>(timestamp x y polarity)</i>, timestamp is float seconds, x, y, polarity are ints. polarity is 0 for off, 1 for on"
        + "<p> DavisTextInputReader uses the current time slice duration or event count depending on FlexTime setting in View/Flextime enabled menu"
         */
        setTimestampLast(false);
        setFlipPolarity(false);
        setSpecialEvents(false);
        setUseCSV(false);
        setUseUsTimestamps(false);
        setUseSignedPolarity(false);
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        return nonMonotonicTimestampsChecked;
    }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
        this.nonMonotonicTimestampsChecked = nonMonotonicTimestampsChecked;
        prefs.putBoolean("checkNonMonotonicTimestamps", nonMonotonicTimestampsChecked);
    }

    @Override
    public long getAbsoluteStartingTimeMs() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public ZoneId getZoneId() {
        return null;
    }

    @Override
    public int getDurationUs() {
        return lastTimestamp - firstTimestamp;
    }

    @Override
    public int getFirstTimestamp() {
        return firstTimestamp;
    }

    @Override
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        getSupport().addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        getSupport().removePropertyChangeListener(listener);
    }

    /**
     * returns the last timestamp in the stream
     *
     * @return last timestamp in file
     */
    @Override
    public int getLastTimestamp() {
        return lastTimestamp;
    }

    @Override
    public int getMostRecentTimestamp() {
        return mostRecentTimestamp;
    }

    @Override
    public int getTimestampResetBitmask() {
        log.warning("timestamp reset not supported for TextFileInputStream");
        return 0;
    }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) {
        log.warning("timestamp reset not supported for TextFileInputStream");
        return;
    }

    @Override
    public void close() throws IOException {
        try {
            if (reader != null) {
                reader.close();
                reader = null;
                log.info(String.format("Closed %s", this.file.getCanonicalPath()));
            }
            setPositionValue(0);
            if(optionsDialog!=null){
                optionsDialog.dispose();
            }
            
        } catch (IOException ex) {
            log.warning(ex.toString());
        } finally {
            reader = null;
        }
    }

    @Override
    public int getCurrentStartTimestamp() {
        return mostRecentTimestamp;
    }

    @Override
    synchronized public void setCurrentStartTimestamp(int currentStartTimestamp) {
        this.startTimestamp = currentStartTimestamp;
    }

    @Override
    public float getFractionalPosition() {
        return (float) position / numEventsInFile;
    }

    @Override
    public void setFractionalPosition(float frac) {
        int n = (int) (frac * numEventsInFile);
        log.info(String.format("Setting fractional position %.2f to %,d events out of total %,d events", frac, n, numEventsInFile));
        position(n);
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    synchronized public void position(long n) {
        try {
            long filePos = (long) (avgLineLength * n);
            if (filePos >= file.length()) {
                log.severe(String.format("Asked for event %,d which computes to filePos(bytes)=%,d which is larger than file length %,d; setting filePos=fileLength-1", n, filePos, fileLength));
                filePos = fileLength - 1;
            }
            reader.seek(filePos); // TODO very approximate, since lines get longer later in file
//            String line = dvsReader.readLine();  // read to next line
            String line = reader.getNextLine();  // read to next line
            if (line == null) {
                log.warning(String.format("Setting postion %,d which computes to filePos(bytes)=%,d resulted in null line, rewinding", n, filePos));
                rewind();
                readNextEvent(null);
            }
            readNextEvent(null);
            setPositionValue(n);
        } catch (IOException ex) {
            log.severe(String.format("Could not seek to position event %,d, caught: %s", n, ex.toString()));
        }
    }

    public void putObject(String key, Serializable o) {
        try {
            PrefObj.putObject(prefs, key, o);
        } catch (IOException | ClassNotFoundException | BackingStoreException e) {
            log.warning(String.format("Could not store preference for %s; got %s", key, e));
        }
    }

    /**
     * Gets a preference for arbitrary Object, using PrefObj utility.
     *
     * @param key the property name, e.g. "hotPixels"
     * @param defObject the default Object
     * @return the object, which must be cast to the expected type
     * @throws java.io.IOException
     * @throws java.util.prefs.BackingStoreException
     */
    public Object getObject(String key, Object defObject) throws ClassCastException, IOException, BackingStoreException, ClassNotFoundException {
        Object o = PrefObj.getObject(prefs, key);
        if (o == null) {
            return defObject;
        }
        return o;
    }

    String getShortFormattingHintString() {
        char sep = useCSV ? ',' : ' ';
        String format;
        if (timestampLast) {
            format = "x,y,p,t";
        } else {
            format = "t,x,y,p";
        }
        if (isSpecialEvents()) {
            format += ",s";
        }
        format = format.replace(',', sep);
        return format;
    }

    String getFormattingHelpString() {
        String sb = "<html>Line formatting: \nComment lines start with '#'\n";
        char sep = useCSV ? ',' : ' ';
        String polOrder = isFlipPolarity() ? "ON/OFF" : "OFF/ON";
        String pol = useSignedPolarity ? "polarity XXX: -1/+1" : "polarity XXX: 0/1";
        pol = pol.replaceAll("XXX", polOrder);
        String ts = useUsTimestamps ? "timestamp: int [us], e.g. 1234" : "timestamp: float [s], e.g. 1.234";
        String tsLong = !readTimestampsAsLongAndSubtractFirst ? "timestamp are read as int [us]" : "timestamps are read as long [us] and first one is subtracted from subsequent";
        String xy = "addresses x,y: short,short".replace(',', sep);
        String special = isSpecialEvents() ? "special events: 0(normal)/1(special)" : "";
        String format = getShortFormattingHintString();
        sb += format + "\n";
        sb += "\n" + xy + "\n" + pol + "\n" + ts + "\n" + tsLong + "\n" + special;
        sb += "\nClick <i>Set format to RPG standard</i> to reset to standard in " + AbstractDavisTextIo.RPG_FORMAT_URL_HYPERLINK;
        sb = sb.replaceAll("\n", "<br>"); // for HTML rendering
        return sb;
    }

    @Override
    synchronized public void rewind() throws IOException {
        log.info(String.format("rewind at position=%,d", position()));
        reader.seek(isMarkInSet() ? markInSeekPosition : 0);
        reader.getNextLine(); // go to next line to avoid format exception from reading in middle of a line
        setPositionValue(getMarkInPosition());
        readNextEvent(null);// set mostRecentTimestamp
        getSupport().firePropertyChange(AEInputStream.EVENT_REWOUND, null, true);
    }

    @Override
    public long size() {
        return numEventsInFile;
    }

    @Override
    public void clearMarks() {
        markIn = 0;
        markOut = Long.MAX_VALUE;
        markInSeekPosition = 0;
        markOutSeekPosition = Long.MAX_VALUE;
        getSupport().firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, false, true);
    }

    @Override
    public long setMarkIn() {
        try {
            long pos = reader.getFilePointer(); // note that marks are stored internally as file bytes because we need to seek() to these marks
            if (isMarkOutSet() && pos >= markOutSeekPosition) {
                log.warning(String.format("tried to set mark IN later than mark OUT"));
                return 0;
            }
            this.markInSeekPosition = pos;
            markIn = position();
            getSupport().firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, null, markIn);
            return markIn;
        } catch (IOException ex) {
            Logger.getLogger(TextFileInputStream.class.getName()).log(Level.SEVERE, null, String.format("Could not set IN marker: %s", ex.toString()));
        }
        return 0;
    }

    @Override
    public long setMarkOut() {
        try {
            long pos = reader.getFilePointer();
            if (isMarkInSet() && pos <= markInSeekPosition) {
                log.warning(String.format("tried to set mark OUR earlier than mark IN"));
                return Long.MAX_VALUE;
            }
            this.markOutSeekPosition = pos;
            markOut = position();
            getSupport().firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, null, markOut);
            return markOut;
        } catch (IOException ex) {
            Logger.getLogger(TextFileInputStream.class.getName()).log(Level.SEVERE, null, String.format("Could not set OUT marker: %s", ex.toString()));
        }
        return Long.MAX_VALUE;
    }

    @Override
    public long getMarkInPosition() {
        return isMarkInSet() ? markIn : 0;
    }

    @Override
    public long getMarkOutPosition() {
        return isMarkOutSet() ? markOut : Long.MAX_VALUE;
    }

    @Override
    public boolean isMarkInSet() {
        return markIn > 0;
    }

    @Override
    public boolean isMarkOutSet() {
        return markOut <= size();
    }

    @Override
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    @Override
    public boolean isRepeat() {
        return this.repeat;
    }

}
