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

import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
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
import net.sf.jaer.eventio.AEFileInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.AEViewer;

/**
 * "Writes out text format files with DVS and IMU data from DAVIS and DVS
 * cameras. Previous filtering affects the output. Output format is compatible
 * with http://rpg.ifi.uzh.ch/davis_data.html
 *
 * @author Tobi Delbruck
 */
@Description("<html>Reads in text format files with DVS data from DAVIS and DVS cameras."
        + "<p>Input format is compatible with <a href=\"http://rpg.ifi.uzh.ch/davis_data.html\">rpg.ifi.uzh.ch/davis_data.html</a>"
        + "i.e. one DVS event per line,  <i>(timestamp x y polarity)</i>, timestamp is float seconds, x, y, polarity are ints. polarity is 0 for off, 1 for on")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DavisTextInputReader extends AbstractDavisTextIo implements PropertyChangeListener {

// for logging concole messages
    // for logging concole messages
    private BufferedReader dvsReader = null;
    private int lastTimestampRead = Integer.MIN_VALUE, lastPacketLastTimestamp = Integer.MIN_VALUE;
    private boolean noEventsReadYet = true; // set false when new file is opened
    private ApsDvsEventPacket outputPacket = null;
    int maxX = chip.getSizeX(), maxY = chip.getSizeY();
    private boolean weWereNeverEnabled = true; // Tobi added this hack to work around the problem that if we are included in FilterChain but not enabled,
    // we set PlayMode to WAITING even if a file is currently playing back
    private final int MAX_ERRORS = 10;
    private int errorCount = 0;
    protected boolean checkNonMonotonicTimestamps = getBoolean("checkNonMonotonicTimestamps", true);
    private int previousTimestamp = 0;
    private boolean openFileAndRecordAedat = false;
    protected boolean flipPolarity = getBoolean("flipPolarity", false);

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
        BufferedReader reader = new BufferedReader(new FileReader(f));
        lastFile = f;
        setEventsProcessed(0);
        noEventsReadYet = true;
        lastLineNumber = 0;
        lastPacketLastTimestamp = Integer.MIN_VALUE;
        lastTimestampRead = Integer.MIN_VALUE;
        log.info("Opened text input file " + f.toString() + " with text format");
        setViewerToFilterInputViewMode();
        return reader;
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
            dvsReader = openReader(c.getSelectedFile());

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.toString(), "Couldn't open input file", JOptionPane.WARNING_MESSAGE, null);
        }
    }

    synchronized public void doCloseFile() {
        try {
            if (dvsReader != null) {
                dvsReader.close();
                dvsReader = null;
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
        lastPacketLastTimestamp = Integer.MIN_VALUE;
        lastTimestampRead = Integer.MIN_VALUE;
        errorCount = 0;
        previousTimestamp = Integer.MIN_VALUE;
    }

    @Override
    public void initFilter() {
    }

    private EventPacket readPacket(EventPacket<? extends BasicEvent> in) {
        if (outputPacket == null) {
            outputPacket = new ApsDvsEventPacket(ApsDvsEvent.class);
            outputPacket.allocate(10000);
        }
        if (dvsReader == null) {
            return outputPacket;
        }
        int durationUs = getChip().getAeViewer().getAePlayer().getTimesliceUs(); // TODO handle flex time (constant count)
        OutputEventIterator outItr = outputPacket.outputIterator();
        lastTimestampRead = lastPacketLastTimestamp;
        String line = null;
        maxX = chip.getSizeX();
        maxY = chip.getSizeY();
        boolean noEventsInThisPacket = true;
        while (dvsReader != null && (noEventsReadYet || noEventsInThisPacket || lastTimestampRead < lastPacketLastTimestamp + durationUs)) {
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
            } catch (NumberFormatException nfe) {
                log.warning(String.format("%s: Line #%d has a bad number format: \"%s\"; check options", nfe.toString(), lastLineNumber, line));
                if (errorCount++ > MAX_ERRORS) {
                    log.warning(String.format("Generated more than %d errors reading file; giving up and closing file.", errorCount));
                    doCloseFile();
                }
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

    private void parseEvent(String line, OutputEventIterator outItr) throws IOException {
        lastLineNumber++;
        if (line.startsWith("#")) {
            log.info("Comment: " + line);
            return;
        }
        String[] split = useCSV ? line.split(",") : line.split(" "); // split by comma or space
        if (split == null || split.length < 4) {
            log.warning(String.format("Line #%d does not have 4 tokens: \"%s\"", lastLineNumber, line));
            return;
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
        lastTimestampRead = useUsTimestamps ? Integer.parseInt(split[it]) : (int) (Float.parseFloat(split[it]) * 1000000);
        int dt = lastTimestampRead - previousTimestamp;
        if (checkNonMonotonicTimestamps && dt < 0) {

            log.warning(String.format("timestamp %,d is %,d us earlier than previous %,d", lastTimestampRead, dt, previousTimestamp));
            if (errorCount++ > MAX_ERRORS) {
                throw new IOException(String.format("Generated more than %d errors reading file; giving up and closing file.", errorCount));
            }

        }
        previousTimestamp = lastTimestampRead;
        short x = Short.parseShort(split[ix]);
        short y = Short.parseShort(split[iy]);
        byte pol = Byte.parseByte(split[ip]);
        if (x < 0 || x >= maxX || y < 0 || y >= maxY) {
            log.warning(String.format("address outside of AEChip allowed range: x=%d y=%d, ignoring. %s ", x, y, pol, lineinfo(line)));
            return;
        }
        Polarity polType = PolarityEvent.Polarity.Off;
        if (useSignedPolarity) {
            if (pol != -1 && pol != 1) {
                log.warning(String.format("polarity %d is not valid (check useSignedPolarity flag), ignoring, %s", pol, lineinfo(line)));
                return;
            } else if (pol == 1) {
                polType = Polarity.On;

            }
        } else {
            if (pol < 0 || pol > 1) {
                log.warning(String.format("polarity %d is not valid (check useSignedPolarity flag), ignoring. %s", pol, lineinfo(line)));
                return;
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
        e.setDvsType();
        setEventsProcessed(this.eventsProcessed + 1);
        noEventsReadYet = false;
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
