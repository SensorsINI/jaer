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
    boolean noEventsReadYet = true; // set false when new file is opened
    private boolean writeOnlyWhenMousePressed = getBoolean("writeOnlyWhenMousePressed", false);
    private ApsDvsEventPacket outputPacket = null;
    int maxX = chip.getSizeX(), maxY = chip.getSizeY();

    public DavisTextInputReader(AEChip chip) {
        super(chip);
        setPropertyTooltip("openFile", "Opens the input file and starts reading from it. The text file is in format timestamp x y polarity, with polarity 0 for OFF and 1 for ON");
        setPropertyTooltip("closeFile", "Closes the input file if it is open.");
        chip.getSupport().addPropertyChangeListener(this);
    }

    /**
     * Processes packet to write output
     *
     * @param in input packet
     * @return input packet
     */
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = readPacket(in);
        eventsProcessed += out.getSize();
        getChip().getAeViewer().setEventPacket(out);
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
            getChip().getAeViewer().setPlayMode(AEViewer.PlayMode.FILTER_INPUT); // TODO may not work
        } else {
            getChip().getAeViewer().setPlayMode(AEViewer.PlayMode.WAITING); // TODO may not work
        }
    }

    /**
     * Opens text output stream and optionally the timecode file, and enable
     * writing to this stream.
     *
     * @param f the file
     * @param additionalComments additional comments to be written to timecode
     * file, Comment header characters are added if not supplied.
     * @return the stream, or null if IOException occurs
     *
     */
    public BufferedReader openReader(File f) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(f));
        lastFile = f;
        setEventsProcessed(0);
        noEventsReadYet = true;
        lastLineNumber = 0;
        lastPacketLastTimestamp = Integer.MIN_VALUE;
        lastTimestampRead = Integer.MIN_VALUE;
        log.info("Opened text input file " + f.toString() + " with text format");
        return reader;
    }

    synchronized public void doOpenFile() {
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt") || f.getName().toLowerCase().endsWith(".dat");
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
            }
            setEventsProcessed(0);
        } catch (Exception ex) {
            log.warning(ex.toString());
            ex.printStackTrace();
        } finally {
            dvsReader = null;
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
        while (noEventsReadYet || noEventsInThisPacket || lastTimestampRead < lastPacketLastTimestamp + durationUs) {
            try {
                line = dvsReader.readLine();
                if (line == null) {
                    log.info("reached end of file after " + lastLineNumber + " lines/events; rewinding");
                    doCloseFile();
                    dvsReader = openReader(lastFile);
                    break;
                }
                parseEvent(line, outItr);
                noEventsInThisPacket = false;
            } catch (NumberFormatException nfe) {
                log.warning(String.format("%s: Line #%d has a bad number format: \"%s\"", nfe.toString(), lastLineNumber, line));
            } catch (EOFException eof) {
                log.info("EOF");
                doCloseFile();
            } catch (IOException e) {
                log.warning(e.toString());
                doCloseFile();
            }
        }
        lastPacketLastTimestamp = lastTimestampRead;
        return outputPacket;
    }

    private void parseEvent(String line, OutputEventIterator outItr) {
        if (line.startsWith("#")) {
            log.info("Comment: " + line);
            return;
        }
        String[] split = useCSV ? line.split(",") : line.split(" "); // split by comma or space
        if (split == null || split.length < 4) {
            log.warning(String.format("Line #%d does not have 4 tokens: \"%s\"", lastLineNumber, line));
            return;
        }

        lastTimestampRead = useUsTimestamps ? Integer.parseInt(split[0]) : (int) (Float.parseFloat(split[0]) * 1000000);
        short x = Short.parseShort(split[1]);
        short y = Short.parseShort(split[2]);
        byte pol = Byte.parseByte(split[3]);
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
        e.x = x;
        e.y = y;
        e.polarity = polType;
        setEventsProcessed(getEventsProcessed() + 1);
        lastLineNumber++;
        noEventsReadYet = false;
    }

    private final String lineinfo(String line) {
        return String.format("Line #%d: \"%s\"", lastLineNumber, line);
    }

}
