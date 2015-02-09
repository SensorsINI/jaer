/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import eu.seebetter.ini.chips.ApsDvsChip;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.util.avioutput.AbstractAviWriter;

/**
 * Writes AVI file from DAVIS APS frames, using ApsFrameExtractor. The AVI file
 * has pixel values 0-255 coming from ApsFrameExtractor
 * displayed frames, which are offset and scaled by it.
 *
 * @author Tobi
 */
@Description("Writes AVI file from DAVIS APS frames, using ApsFrameExtractor. This AVI has spatial resolution the same as the AEChip (not the display resolution)")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DavisFrameAviWriter extends AbstractAviWriter {

    ApsFrameExtractor apsFrameExtractor;
    ApsDvsChip apsDvsChip = null;

    public DavisFrameAviWriter(AEChip chip) {
        super(chip);
        FilterChain filterChain = new FilterChain(chip);
        apsFrameExtractor = new ApsFrameExtractor(chip);
        apsFrameExtractor.getSupport().addPropertyChangeListener(this);
        filterChain.add(apsFrameExtractor);
        setEnclosedFilterChain(filterChain);
        setPropertyTooltip("saveAVIFileAs", "Opens the output file. The AVI file is in RAW format with pixel values 0-255 coming from ApsFrameExtractor displayed frames, which are offset and scaled by it.");
        setPropertyTooltip("closeFile", "Closes the output file if it is open.");
        setPropertyTooltip("writeTimecodeFile", "writes a file alongside AVI file (with suffix " + TIMECODE_SUFFIX + ") that maps from AVI frame to AER timestamp for that frame (the frame end timestamp)");
        setPropertyTooltip("closeOnRewind", "closes recording on rewind event, to allow unattended operation");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        super.filterPacket(in); // adds propertychangelistener for rewind event
        apsDvsChip = (ApsDvsChip) chip;
        apsFrameExtractor.filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
        apsFrameExtractor.resetFilter();
    }

    @Override
    public void initFilter() {
        apsFrameExtractor.initFilter();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (aviOutputStream != null && evt.getPropertyName() == ApsFrameExtractor.EVENT_NEW_FRAME) {
            float[] frame = apsFrameExtractor.getNewFrame();

            BufferedImage bufferedImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = bufferedImage.getRaster();
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    int k = apsFrameExtractor.getIndex(x, y);
//                    bufferedImage.setRGB(x, y, (int) (frame[k] * 1024));
                    int v = (int) (frame[k] * 255), yy = sy - y - 1; // must flip image vertially according to java convention that image starts at upper left
                    raster.setSample(x, yy, 0, v);
                    raster.setSample(x, yy, 1, v);
                    raster.setSample(x, yy, 2, v);
                }
            }
            try {
                aviOutputStream.writeFrame(bufferedImage);
                int timestamp = apsFrameExtractor.getLastFrameTimestamp();
                writeTimecode(timestamp);
                incrementFramecountAndMaybeCloseOutput();

            } catch (IOException ex) {
                Logger.getLogger(DavisFrameAviWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if (evt.getPropertyName() == AEInputStream.EVENT_REWIND) {
            doCloseFile();
        }
    }

}
