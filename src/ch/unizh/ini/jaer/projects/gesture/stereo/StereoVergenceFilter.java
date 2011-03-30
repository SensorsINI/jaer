/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import java.util.Observable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.stereopsis.StereoTranslateRotate;

/**
 * Vergence filter for stereo DVS.
 * It estimates the disparity of the closest moving object, and then use it to overlap the events from left and right eyes.
 * A global disparity is obtained from the mass histogram at each point along the x-axis.
 * 
 * @author Jun Haeng Lee
 */
public class StereoVergenceFilter extends DisparityUpdater /*, PreferenceChangeListener*/ {

    /**
     * stero translate rotate filter
     */
    public StereoTranslateRotate str = null;


    /**
     *
     * @param chip
     */
    public StereoVergenceFilter(AEChip chip) {
        super(chip);
        addObserver(this);

        // encloses a StereoTranslateRotate filter to adjust the offset of x-axis
        str = new StereoTranslateRotate (chip);
        FilterChain fc=new FilterChain(chip);
        fc.add(str);
        setEnclosedFilterChain(fc);
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        str.resetFilter();
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!initialized){
            initFilter();
        }
        
        // updates the histogram for each event
        for(int i=0; i<in.getSize(); i++){
            BinocularEvent be = (BinocularEvent)in.getEvent(i);
            addEvent(be);

            maybeCallUpdateObservers(in, lastTimestamp);
        }

        
        out=getEnclosedFilterChain().filterPacket(in);

        return out;
    }

    @Override
    public void update (Observable o,Object arg){
        super.update(o, arg);

        // str filter
        if (o == this)
            str.setDx(-(int)(disparityForVergence.getDisparity()/2));
    }
}
