/*
 * Copyright (C) 2020 Tobi.
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

import java.io.File;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.MessageWithLink;

/**
 * Abstract class for text IO filters for DAVIS cameras.
 *
 * The RPG DVS text file datatset looks like this. Each line has (time(float s),
 * x, y, polarity (0=off,1=on)
 * <pre>
 * 0.000000000 33 39 1
 * 0.000011001 158 145 1
 * 0.000050000 88 143 0
 * 0.000055000 174 154 0
 * 0.000080001 112 139 1
 * 0.000123000 136 171 0
 * 0.000130001 173 90 0
 * 0.000139001 106 140 0
 * 0.000148001 192 79 1
 * </pre>
 *
 *
 * @author Tobi
 */
public abstract class AbstractDavisTextIo extends EventFilter2D {

    protected int LOG_EVERY_THIS_MANY_MS = 2000;
    protected long nextGuiUpdateTime = System.currentTimeMillis();
    protected static String DEFAULT_FILENAME = "JAEERDavisTextIO.txt";
    protected final int LOG_EVERY_THIS_MANY_DVS_EVENTS = 10000; // for logging concole messages
    protected String lastFileName = getString("lastFileName", DEFAULT_FILENAME);
    protected File lastFile = null;
    protected int eventsProcessed = 0;
    protected int lastLineNumber = 0;
    protected boolean useCSV = getBoolean("useCSV", false);
    protected boolean useUsTimestamps = getBoolean("useUsTimestamps", false);
    protected boolean useSignedPolarity = getBoolean("useSignedPolarity", false);
    protected boolean timestampLast = getBoolean("timestampLast", false);
    protected boolean flipPolarity = getBoolean("flipPolarity", false);
    protected boolean specialEvents = getBoolean("specialEvents", false);
    protected final int SPECIAL_COL = 4; // location of special flag (0 normal, 1 special) 
    public static final String RPG_FORMAT_URL_HYPERLINK=" <a href=\"http://rpg.ifi.uzh.ch/davis_data.html\">rpg.ifi.uzh.ch/davis_data</a>";
    
    public AbstractDavisTextIo(AEChip chip) {
        super(chip);
        setPropertyTooltip("setToRPGFormat", "<html>Set options to RPG standard <a href=\"http://rpg.ifi.uzh.ch/davis_data.html\">rpg.ifi.uzh.ch/davis_data.html</a>");
        setPropertyTooltip("showFormattingHelp", "Show help for line formatting");
        setPropertyTooltip("format", "Readonly string to show line formatting with current options");
        setPropertyTooltip("formattingHelp", "Show help for line formatting");
        setPropertyTooltip("eventsProcessed", "READONLY, shows number of events read");
        setPropertyTooltip("useCSV", "use CSV (comma separated) format rather than space separated values");
        setPropertyTooltip("useUsTimestamps", "use us int timestamps rather than float time in seconds");
        setPropertyTooltip("useSignedPolarity", "use -1/+1 OFF/ON polarity rather than 0,1 OFF/ON polarity");
        setPropertyTooltip("timestampLast", "use x,y,p,t rather than t,x,y,p ordering");
        setPropertyTooltip("specialEvents", "<HTML>Include extra 5th column that labels events as special (1) or normal DVS events (0). "
                + "<p>For NoiseTesterFilter, events that are special are treated as labeled noisee events..");
    }


    /**
     * @return the eventsProcessed
     */
    public int getEventsProcessed() {
        return eventsProcessed;
    }

    /**
     * @param eventsProcessed the eventsProcessed to set
     */
    public void setEventsProcessed(int eventsProcessed) {
        int old = this.eventsProcessed;
        this.eventsProcessed = eventsProcessed;
        if (old != this.eventsProcessed && System.currentTimeMillis() > nextGuiUpdateTime) {
            nextGuiUpdateTime = System.currentTimeMillis() + LOG_EVERY_THIS_MANY_MS;
            log.info(String.format("processed %,d events", eventsProcessed));
            getSupport().firePropertyChange("eventsProcessed", null, eventsProcessed);
        }
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
        String oldFormat = getShortFormattingHintString();
        this.useSignedPolarity = useSignedPolarity;
        putBoolean("useSignedPolarity", useSignedPolarity);
        getSupport().firePropertyChange("format", oldFormat, getShortFormattingHintString());
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
        String oldFormat = getShortFormattingHintString();
        this.timestampLast = timestampLast;
        putBoolean("timestampLast", timestampLast);
        getSupport().firePropertyChange("format", oldFormat, getShortFormattingHintString());
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
        String oldFormat = getShortFormattingHintString();
        this.specialEvents = specialEvents;
        putBoolean("specialEvents", specialEvents);
        getSupport().firePropertyChange("format", oldFormat, getShortFormattingHintString());
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
    
        /**
     * @param useCSV the useCSV to set
     */
    public void setUseCSV(boolean useCSV) {
        String oldFormat = getShortFormattingHintString();
        this.useCSV = useCSV;
        putBoolean("useCSV", useCSV);
        getSupport().firePropertyChange("format", oldFormat, getShortFormattingHintString());
    }

    /**
     * @param useUsTimestamps the useUsTimestamps to set
     */
    public void setUseUsTimestamps(boolean useUsTimestamps) {
        String oldFormat = getShortFormattingHintString();
        this.useUsTimestamps = useUsTimestamps;
        putBoolean("useUsTimestamps", useUsTimestamps);
        getSupport().firePropertyChange("format", oldFormat, getShortFormattingHintString());
    }


    public void doShowFormattingHelp() {
        showPlainMessageDialogInSwingThread(new MessageWithLink(getFormattingHelpString()), "Event line formatting help");
    }
    
    public void doSetToRPGFormat(){
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

    private String getShortFormattingHintString() {
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

    private String getFormattingHelpString() {
        String sb = "Line formatting: Comment lines start with '#'\n";
        char sep = useCSV ? ',' : ' ';
        String polOrder=isFlipPolarity()?"ON/OFF":"OFF/ON";
        String pol = useSignedPolarity ? "polarity XXX: -1/+1" : "polarity XXX: 0/1";
        pol=pol.replaceAll("XXX", polOrder);
        String ts = useUsTimestamps ? "timestamp: int [us], e.g. 1234" : "timestamp: float [s], e.g. 1.234";
        String xy = "addresses x,y: short,short".replace(',', sep);
        String special = isSpecialEvents() ? "special events: 0(normal)/1(special)" : "";
        String format = getShortFormattingHintString();
        sb += format + "\n";
        sb += "\n" + xy + "\n" + pol + "\n" + ts + "\n" + special;
        sb+= "\nClick SetToRPG format to reset to standard in "+RPG_FORMAT_URL_HYPERLINK;
        sb=sb.replaceAll("\n", "<br>"); // for HTML rendering
        return sb;
    }

    public String getFormat() {
        return getShortFormattingHintString();
    }

    public void setFormat(String s) {
        log.info(String.format("Format is %s", s));
    }

}
