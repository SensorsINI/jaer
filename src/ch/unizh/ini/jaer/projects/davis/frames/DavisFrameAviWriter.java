/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.seebetter.ini.chips.DavisChip;
import javax.swing.JOptionPane;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.util.avioutput.AbstractAviWriter;

/**
 * Writes AVI file from DAVIS APS frames, using ApsFrameExtractor. The AVI file
 * has pixel values 0-255 coming from ApsFrameExtractor displayed frames, which
 * are offset and scaled by it.
 *
 * @author Tobi
 */
@Description("Writes AVI file from DAVIS APS frames, using ApsFrameExtractor. This AVI has spatial resolution the same as the AEChip (not the display resolution)")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DavisFrameAviWriter extends AbstractAviWriter {

//    ApsFrameExtractor apsFrameExtractor;
    DavisChip apsDvsChip = null;
    private boolean rendererPropertyChangeListenerAdded = false;
    private DavisRenderer renderer = null;

    public DavisFrameAviWriter(AEChip chip) {
        super(chip);
//        FilterChain filterChain = new FilterChain(chip);
////        apsFrameExtractor = new ApsFrameExtractor(chip);
////        apsFrameExtractor.getSupport().addPropertyChangeListener(this);
////        filterChain.add(apsFrameExtractor);
//        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("saveAVIFileAs", "Opens the output file. The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
        setPropertyTooltip("writeTimecodeFile", "writes a file alongside AVI file (with suffix " + TIMECODE_SUFFIX + ") that maps from AVI frame to AER timestamp for that frame (the frame end timestamp)");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        super.filterPacket(in); // adds propertychangelistener for rewind event
        if (!rendererPropertyChangeListenerAdded) {
            rendererPropertyChangeListenerAdded = true;
            renderer = (DavisRenderer) chip.getRenderer();
            renderer.getSupport().addPropertyChangeListener(this);
        }
        apsDvsChip = (DavisChip) chip;
//        apsFrameExtractor.filterPacket(in);
        return in;
    }

//    @Override
//    public void resetFilter() {
//        apsFrameExtractor.resetFilter();
//    }
//
//    @Override
//    public void initFilter() {
//        apsFrameExtractor.initFilter();
//    }
    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if (isRecordingActive()
                && (evt.getPropertyName() == DavisRenderer.EVENT_NEW_FRAME_AVAILBLE)
                && !chip.getAeViewer().isPaused()) {
            FloatBuffer frame = ((DavisRenderer) chip.getRenderer()).getPixmap();

            BufferedImage bufferedImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = bufferedImage.getRaster();
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int k = renderer.getPixMapIndex(x, y);
//                    bufferedImage.setRGB(x, y, (int) (frame[k] * 1024));
                    int yy = sy - y - 1;
                    int r = (int) (frame.get(k) * 255); // must flip image vertially according to java convention that image starts at upper left
                    int g = (int) (frame.get(k + 1) * 255); // must flip image vertially according to java convention that image starts at upper left
                    int b = (int) (frame.get(k + 2) * 255); // must flip image vertially according to java convention that image starts at upper left
                    raster.setSample(x, yy, 0, r);
                    raster.setSample(x, yy, 1, g);
                    raster.setSample(x, yy, 2, b);
                }
            }
            int timestamp = renderer.getTimestampFrameEnd();
            writeFrame(bufferedImage, timestamp);
        } else if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            if (!ignoreRewinwdEventFlag && closeOnRewind && getAviOutputStream() != null) {
                doFinishRecording();
                JOptionPane.showMessageDialog(chip.getAeViewer(), "Closed file" + lastFileName + " on Rewind event after " + framesWritten + " frames were written");
            }
            ignoreRewinwdEventFlag = false;
        }
    }

}
