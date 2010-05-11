/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2D;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;

/** does blurringFiltering after the vegence of stereo images
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereo extends BlurringFilter2D{
    
    StereoVergenceFilter svf;

    /**
     * Constructor. A StereoVergenceFilter is inside a FilterChain that is enclosed in this.
     * @param chip
     */
    public BlurringFilterStereo(AEChip chip) {
        super(chip);
        svf = new StereoVergenceFilter(chip);
        FilterChain fc=new FilterChain(chip);
        fc.add(svf);
        setEnclosedFilterChain(fc);
    }

    /** The super's filterPacket is called. BlurringFilter2D first processes it's enclosed FilterChain which holds StereoVergenceFilter. It then runs the BlurringFilter2D, which
     * first runs the BlurringFilter.
     *
     * @param in
     * @return the verged input packet.
     */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = super.filterPacket(in);// super is BlurringFilter2D, and it's enclosed filter chain has been set here to hold the svf. This filterchain is processed in the super BlurringFilter2D.
        return out;
    }

    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
        super.resetFilter();
    }

    /** returns the global disparity between left and right eyes.
     * it returns prevDisparity rather than currentGlobalDisparity to cover the failure of disparity estimation.
     *
     * @return
     */
    synchronized public int getGlobalDisparity(){
        return svf.getGlobalDisparity();
    }

    /** returns true if the disparity of the specified position is properly updated.
     *
     * @param yPos
     * @return
     */
    public boolean isDisparityValid(int yPos){
        return svf.isDisparityValid(yPos);
    }

    /** returns the disparity at the specified position.
     * Disparity values obtained from mutiples sections are used to get it.
     *
     * @param yPos
     * @return
     */
    public int getDisparity(int yPos){
        return svf.getDisparity(yPos);
    }
}
