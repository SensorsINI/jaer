/*
 * Copyright (C) 2018 tobid.
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
package net.sf.jaer.util.textio;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.MalformedParametersException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * "Writes out text format files with DVS and IMU data from DAVIS and DVS
 * cameras. Previous filtering affects the output. Output format is compatible
 * with http://rpg.ifi.uzh.ch/davis_data.html
 *
 * @author Tobi Delbruck
 */
@Description("<html>Reads in text format files with DVS data from DAVIS and DVS cameras."
        + "<p>Input format is compatible with <a href=\"http://rpg.ifi.uzh.ch/davis_data.html\">rpg.ifi.uzh.ch/davis_data.html</a>"
        + "i.e. one DVS event per line,  <i>(timestamp x y polarity)</i>, timestamp is float seconds, x, y, polarity are ints. polarity is 0 for off, 1 for on"
        + "<p> DavisTextInputReader uses the current time slice duration or event count depending on FlexTime setting in View/Flextime enabled menu")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DavisTextInputReader extends AbstractDavisTextIo implements PropertyChangeListener, FrameAnnotater {

// for logging concole messages
    // for logging concole messages
    private BufferedReader dvsReader = null;
    private int lastTimestampRead = 0, lastPacketLastTimestamp = 0;
    private boolean noEventsReadYet = true; // set false when new file is opened
    private int numEventsThisPacket = 0, numEventsInFile = 0;
    private ApsDvsEventPacket outputPacket = null;
    int maxX = chip.getSizeX(), maxY = chip.getSizeY();
    private boolean weWereNeverEnabled = true; // Tobi added this hack to work around the problem that if we are included in FilterChain but not enabled,
    // we set PlayMode to WAITING even if a file is currently playing back
    private final int MAX_ERRORS = 3;
    private int errorCount = 0;
    protected boolean checkNonMonotonicTimestamps = getBoolean("checkNonMonotonicTimestamps", true);
    private int previousTimestamp = 0;
    private boolean openFileAndRecordAedat = false;
    protected boolean flipPolarity = getBoolean("flipPolarity", false);
    final int SPECIAL_COL = 4; // location of special flag (0 normal, 1 special) 
    long lastFileLength = 0; // used to check if the file is the same length as before to avoid line count

    public DavisTextInputReader(AEChip chip) {
        super(chip);
        setPropertyTooltip("openFile", "Opens the input file and starts reading from it. The text file is in format timestamp x y polarity, with polarity 0 for OFF and 1 for ON");
        setPropertyTooltip("closeFile", "Closes the input file if it is open.");
        setPropertyTooltip("rewind", "Closes and reopens the file.");
        setPropertyTooltip("checkNonMonotonicTimestamps", "Checks to ensure timestamps are read in monotonically increasing order.");
        setPropertyTooltip("openFileAndRecordAedat", "Opens text file and re-records it as an AEDAT file with same name but .aedat2 extension.");
        setPropertyTooltip("flipPolarity", "Reading polarity: Unselected ON:1 or +1, OFF, 0 or -1. Selected flips ON and OFF so that ON is 0 or -1, OFF is 1 or +1.");
        chip.getSupport().addPropertyChangeListener(this);
    }

    /**
     * Processes packet to write output
     *
     * @param in input packet
     * @return input packet
     */
    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (dvsReader == null) {
            return in;
        }
        out = readPacket(in);
        return out;
    }

    /**
     * Overridden to set AEViewer to PLAYBACK mode and substitute our input
     * packet for the one that would have been obtained from the camera, file,
     * or network
     *
     * @param yes
     */
    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            setViewerToFilterInputViewMode();
        } else if (!weWereNeverEnabled) {
            getChip().getAeViewer().setPlayMode(AEViewer.PlayMode.WAITING); // TODO may not work
        }
    }

    private void setViewerToFilterInputViewMode() {
        getChip().getAeViewer().setPlayMode(AEViewer.PlayMode.FILTER_INPUT); // TODO may not work
        weWereNeverEnabled = false;
    }

    /**
     * Opens text output stream and optionally the timecode file, and enable
     * writing to this stream.
     *
     * @param f the file
     * @return the stream, or null if IOException occurs
     * @throws java.io.IOException
     *
     */
    public BufferedReader openReader(File f) throws IOException {
        resetFilter();
//        https://stackoverflow.com/questions/1277880/how-can-i-get-the-count-of-line-in-a-file-in-an-efficient-way
        BufferedReader reader = new BufferedReader(new FileReader(f));
        long fileLength = f.length();
        if (fileLength != lastFileLength || numEventsInFile == 0) {
            log.info("counting events in file....");
            numEventsInFile = 0;
            int numChars = 0;
            numEventsInFile = 0;
            String line = null;
            while ((line = reader.readLine()) != null) {
                numEventsInFile++;
                numChars += line.length();
                float avgEventLengthChars = (float) numChars / numEventsInFile;
                int estTotalEvents = (int) (fileLength / avgEventLengthChars);
                if (numEventsInFile % 1000000 == 0) {
                    log.info(String.format("Counting events: %,d events, %.1f%% complete", numEventsInFile, 100 * (float) numEventsInFile / estTotalEvents));
                }
            }

            reader.close();
            lastFileLength=fileLength;
        }
        reader = new BufferedReader(new FileReader(f));
        lastFile = f;
        setEventsProcessed(0);
        noEventsReadYet = true;
        lastLineNumber = 0;
        log.info(String.format("Opened text input file %s with %,d events",f.toString(),numEventsInFile));
        setViewerToFilterInputViewMode();
        return reader;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(.8f, .8f, .8f); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, 30, 0);
        float filePercent = getFilePercent();
        String s = null;
        s = String.format("File: %%%6.1f",
                filePercent);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glPopMatrix();
        getChip().getAeViewer().getAePlayer().setFractionalPosition(filePercent / 100);
    }

    private float getFilePercent() {
        final float filePercent = 100 * (float) eventsProcessed / numEventsInFile;
        return filePercent;
    }

    synchronized public void doOpenFileAndRecordAedat() {
        getChip().getAeViewer().startLogging();
        openFileAndRecordAedat = true;
        doOpenFile();
    }

    synchronized public void doOpenFile() {
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt") || f.getName().toLowerCase().endsWith(".dat") || f.getName().toLowerCase().endsWith(".csv");
            }

            @Override
            public String getDescription() {
                return "text with lines of timestamp x y polarity";
            }
        });
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showOpenDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        try {
            if (dvsReader != null) {
                doCloseFile();
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            dvsReader = openReader(c.getSelectedFile());

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't open input file", JOptionPane.WARNING_MESSAGE, null);
        } finally {
            setCursor(null);
        }
    }

    synchronized public void doCloseFile() {
        try {
            if (dvsReader != null) {
                dvsReader.close();
                dvsReader = null;
                log.info(String.format("Closed %s",lastFile));
            }
            setEventsProcessed(0);
        } catch (IOException ex) {
            log.warning(ex.toString());
        } finally {
            dvsReader = null;
        }
    }

    synchronized public void doRewind() {
        doCloseFile();
        if (lastFile != null) {
            try {
                dvsReader = openReader(lastFile);
            } catch (IOException ex) {
                Logger.getLogger(DavisTextInputReader.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * @return the useCSV
     */
    public boolean isUseCSV() {
        return useCSV;
    }

    /**
     * @return the useUsTimestamps
     */
    public boolean isUseUsTimestamps() {
        return useUsTimestamps;
    }

    @Override
    public void resetFilter() {
        setEventsProcessed(0);
        noEventsReadYet = true;
        lastPacketLastTimestamp = 0;
        lastTimestampRead = 0;
        errorCount = 0;
        previousTimestamp = 0;
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    private EventPacket readPacket(EventPacket<? extends BasicEvent> in) {
        if (outputPacket == null) {
            outputPacket = new ApsDvsEventPacket(ApsDvsEvent.class);
            outputPacket.allocate(10000);
        }
        if (dvsReader == null) {
            return outputPacket;
        }
        boolean flextime = getChip().getAeViewer().getAePlayer().isFlexTimeEnabled();
        int durationUs = getChip().getAeViewer().getAePlayer().getTimesliceUs(); // TODO handle flex time (constant count)
        int eventCount = getChip().getAeViewer().getAePlayer().getPacketSizeEvents();
        OutputEventIterator outItr = outputPacket.outputIterator();
//        lastTimestampRead = lastPacketLastTimestamp;
        String line = null;
        maxX = chip.getSizeX();
        maxY = chip.getSizeY();
        boolean noEventsInThisPacket = true;
        numEventsThisPacket = 0;
        while (dvsReader != null && (noEventsReadYet || noEventsInThisPacket)
                || (!flextime && lastTimestampRead < lastPacketLastTimestamp + durationUs)
                || (flextime && numEventsThisPacket < eventCount)) {
            try {
                line = dvsReader.readLine();
                if (line == null) {
                    log.info(String.format("reached end of file after %,d lines and %,d events; rewinding", lastLineNumber, getEventsProcessed()));
                    if (openFileAndRecordAedat) {
                        getChip().getAeViewer().stopLogging(true);
                        openFileAndRecordAedat = false;
                        doCloseFile();
                        break;
                    } else {
                        doCloseFile();
                        dvsReader = openReader(lastFile);
                        break;
                    }
                }
                parseEvent(line, outItr);
                noEventsInThisPacket = false;
                numEventsThisPacket++;
            } catch (EOFException eof) {
                log.info("EOF (end of file)");
                doCloseFile();
            } catch (IOException e) {
                log.warning(e.toString());
                doCloseFile();
            }
        }
        lastPacketLastTimestamp = lastTimestampRead;
        return outputPacket;
    }

    private void checkErrorHalt(String s) {
        log.warning(s);
        errorCount++;
        if (errorCount++ > MAX_ERRORS) {
            showWarningDialogInSwingThread(s, "DavisTextInputReader error");
            throw new MalformedParametersException(s);
        }
    }

    private void parseEvent(String line, OutputEventIterator outItr) throws IOException {
        lastLineNumber++;
        if (line.startsWith("#")) {
            log.info("Comment: " + line);
            return;
        }
        String[] split = useCSV ? line.split(",") : line.split(" "); // split by comma or space
        if (split == null || (!isSpecialEvents() && split.length != 4) || (isSpecialEvents() && split.length != 5)) {
            String s = String.format("Only %d tokens but needs 4 without and 5 with specialEvents:\n%s\nCheck useCSV for ',' or space separator", split.length, lineinfo(line));
            checkErrorHalt(s);
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
        try {
            if(useUsTimestamps&&split[it].contains(".")){
                checkErrorHalt(String.format("timestamp %s has a '.' in it but useUsTimestamps is true\n%s", split[it], lineinfo(line)));
            }else if(!useUsTimestamps&&!split[it].contains(".")){
                checkErrorHalt(String.format("timestamp %s has no '.' in it but useUsTimestamps is false\n%s", split[it], lineinfo(line)));
            }
            lastTimestampRead = useUsTimestamps ? Integer.parseInt(split[it]) : (int) (Float.parseFloat(split[it]) * 1000000);
            int dt = lastTimestampRead - previousTimestamp;
            if (checkNonMonotonicTimestamps && dt < 0) {
                checkErrorHalt(String.format("timestamp %,d is %,d us earlier than previous %,d at %s", lastTimestampRead, dt, previousTimestamp, lineinfo(line)));
            }
            previousTimestamp = lastTimestampRead;
            short x = Short.parseShort(split[ix]);
            short y = Short.parseShort(split[iy]);
            byte pol = Byte.parseByte(split[ip]);

            if (x < 0 || x >= maxX || y < 0 || y >= maxY) {
                checkErrorHalt(String.format("address x=%d y=%d outside of AEChip allowed range maxX=%d, maxY=%d: , ignoring. %s\nAre you using the correct AEChip?", x, y, maxX, maxY, lineinfo(line)));
            }
            Polarity polType = PolarityEvent.Polarity.Off;
            if (useSignedPolarity) {
                if (pol != -1 && pol != 1) {
                    checkErrorHalt(String.format("polarity %d is not valid (check useSignedPolarity flag), ignoring. %s", pol, lineinfo(line)));
                } else if (pol == 1) {
                    polType = Polarity.On;
                }
            } else {
                if (pol < 0 || pol > 1) {
                    checkErrorHalt(String.format("polarity %d is not valid (check useSignedPolarity flag), ignoring. %s", pol, lineinfo(line)));
                } else if (pol == 1) {
                    polType = Polarity.On;
                }
            }

            ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
            noEventsReadYet = false;
            e.setFilteredOut(false);
            e.setTimestamp(lastTimestampRead);
            e.setX(x);
            e.setY(y);
            e.setPolarity(polType);
            if (flipPolarity) {
                e.flipPolarity();
            }
            e.setType(polType == Polarity.Off ? (byte) 0 : (byte) 1);
            e.setDvsType();
            if (isSpecialEvents()) {
                int specialFlag = Integer.parseInt(split[SPECIAL_COL]);
                if (specialFlag == 0) {
                    e.setSpecial(false);
                } else if (specialFlag == 1) {
                    e.setSpecial(true);
                } else {
                    String s = String.format("Line #%d has Unknown type of special event, must be 0 (normal) or 1 (special):\n\"%s\"", lastLineNumber, line);
                    checkErrorHalt(s);
                }
            }
            setEventsProcessed(this.eventsProcessed + 1);
            noEventsReadYet = false;
        } catch (NumberFormatException nfe) {
            String s = String.format("%s: Line #%d has a bad number format: \"%s\"; check options; maybe you should set timestampLast?", nfe.toString(), lastLineNumber, line);
            log.warning(s);
            showWarningDialogInSwingThread(s, "DavisTextInputReader error");
            throw new MalformedParametersException(s);
        }
    }

    private String lineinfo(String line) {
        return String.format("Line #%d: \"%s\"", lastLineNumber, line);
    }

    /**
     * @return the checkNonMonotonicTimestamps
     */
    public boolean isCheckNonMonotonicTimestamps() {
        return checkNonMonotonicTimestamps;
    }

    /**
     * @param checkNonMonotonicTimestamps the checkNonMonotonicTimestamps to set
     */
    public void setCheckNonMonotonicTimestamps(boolean checkNonMonotonicTimestamps) {
        this.checkNonMonotonicTimestamps = checkNonMonotonicTimestamps;
        putBoolean("checkNonMonotonicTimestamps", checkNonMonotonicTimestamps);
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
        this.flipPolarity = flipPolarity;
        putBoolean("flipPolarity", flipPolarity);
    }

}
