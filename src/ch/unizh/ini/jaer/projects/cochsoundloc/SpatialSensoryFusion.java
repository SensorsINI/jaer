/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.io.BufferedWriter;
import java.io.FileWriter;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * This is a retina-filter for sensory fusion of retinaEvents and ITDEvents.
 * 
 * @author Holger
 */
public class SpatialSensoryFusion extends EventFilter2D implements FrameAnnotater {

    FileWriter fstream;
    BufferedWriter SensoryEventsFile;
    private boolean writeEventsToFile = getPrefs().getBoolean("ITDFilter.writeEventsToFile", false);
    private boolean receiveITDEvents = getPrefs().getBoolean("ITDFilter.receiveITDEvents", false);

    public SpatialSensoryFusion(AEChip chip) {
        super(chip);
        initFilter();
    }

 
    @Override
    public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(GLAutoDrawable drawable) {
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in == null) {
            return null;
        }
        if (!filterEnabled) {
            return in;
        }

        //Process Visual Input:
        for (BasicEvent evt : in) {
            //i.e. write events to file:
            if (this.writeEventsToFile) {
                try {
                    SensoryEventsFile.write(evt.getTimestamp() + "\tretina\t" + evt.x + "\n");
                } catch (Exception ex) {
                    log.warning("Exception while writing to File: " + ex);
                    ex.printStackTrace();
                }
            }
        }

        //Process Auditory Input:
        ITDEvent itdEvent;
        if (receiveITDEvents == true) {
            itdEvent = ITDFilter.pollITDEvent();
            while (itdEvent != null) {

                //process the ITDEvent here ... 
                //i.e. write events to file:
                if (this.writeEventsToFile) {
                    try {
                        SensoryEventsFile.write(itdEvent.getTimestamp() + "\tITD\t" + itdEvent.getITD() + "\n");
                    } catch (Exception ex) {
                        log.warning("Exception while writing to File: " + ex);
                        ex.printStackTrace();
                    }
                }

                itdEvent = ITDFilter.pollITDEvent();
            }
        }

        return in;
    }

    public boolean isReceiveITDEvents() {
        return this.receiveITDEvents;
    }

    public void setReceiveITDEvents(boolean receiveITDEvents) {
        getPrefs().putBoolean("ITDFilter.receiveITDEvents", receiveITDEvents);
        getSupport().firePropertyChange("receiveITDEvents", this.receiveITDEvents, receiveITDEvents);
        this.receiveITDEvents = receiveITDEvents;
    }

    public boolean isWriteEventsToFile() {
        return this.writeEventsToFile;
    }

    public void setWriteEventsToFile(boolean writeEventsToFile) {
        getPrefs().putBoolean("ITDFilter.writeEventsToFile", writeEventsToFile);
        getSupport().firePropertyChange("writeEventsToFile", this.writeEventsToFile, writeEventsToFile);
        this.writeEventsToFile = writeEventsToFile;
        if (writeEventsToFile == true) {
            try {
                // Create file
                fstream = new FileWriter("SensoryFusionOutput.dat");
                SensoryEventsFile = new BufferedWriter(fstream);
                SensoryEventsFile.write("time\tsense\tevent\n");
            } catch (Exception ex) {
                log.warning("Exception while creating File: " + ex);
                ex.printStackTrace();
            }
        } else {
            try {
                //Close the output stream
                SensoryEventsFile.close();
            } catch (Exception ex) {
                log.warning("Exception while closing File: " + ex);
                ex.printStackTrace();
            }
        }
    }
}
