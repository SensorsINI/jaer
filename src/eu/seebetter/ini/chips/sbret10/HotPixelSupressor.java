/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.sbret10;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Cheaply suppresses hot pixels from DVS; ie pixels that continuously fire a
 * sustained stream of events.
 * <p>
 * The method is based on a list of hot pixels that is filled probabalistically.
 * A hot pixel is likely to occupy the list and addresses in the list are not
 * output. The list is filled at with some baseline probability which is reduced
 * inversely with the average event rate. This way, activity induced output of
 * the sensor does not overwrite the stored hot pixels.
 *
 * @author tobi
 */
@Description("Cheaply suppresses hot pixels from DVS; ie pixels that continuously fire a sustained stream of events.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class HotPixelSupressor extends EventFilter2D {

    private int numHotPixels = getInt("numHotPixels", 30);
    private float baselineProbability = getFloat("baselineProbability", 1e-3f);
    private float thresholdEventRate=getFloat("thresholdEventRate",10e3f);
    private HashSet<Integer> hotPixelMap = new HashSet();
    Random r = new Random();

    public HotPixelSupressor(AEChip chip) {
        super(chip);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        for (BasicEvent e : in) {
            if (r.nextFloat() < getBaselineProbability()) {
                hotPixelMap.add(e.address);
                for (Integer i : hotPixelMap) {
                    if (i != e.address && hotPixelMap.size() >= numHotPixels) {
                        hotPixelMap.remove(i); // remove some other element, not known which
                        break;
                    }
                }
            }
            if (hotPixelMap.contains(e.address)) {
                log.info("filtered out "+e);
                continue;
            }
                ApsDvsEvent a = (ApsDvsEvent) outItr.nextOutput();
                a.copyFrom(e);

        }
        return out;
    }

    @Override
    synchronized public void resetFilter() {
        hotPixelMap.clear();
        
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the numHotPixels
     */
    public int getNumHotPixels() {
        return numHotPixels;
    }

    /**
     * @param numHotPixels the numHotPixels to set
     */
    public void setNumHotPixels(int numHotPixels) {
        this.numHotPixels = numHotPixels;
        putInt("numHotPixels",numHotPixels);
    }

    /**
     * @return the baselineProbability
     */
    public float getBaselineProbability() {
        return baselineProbability;
    }

    /**
     * @param baselineProbability the baselineProbability to set
     */
    public void setBaselineProbability(float baselineProbability) {
        this.baselineProbability = baselineProbability;
        putFloat("baselineProbability",baselineProbability);
    }
}
