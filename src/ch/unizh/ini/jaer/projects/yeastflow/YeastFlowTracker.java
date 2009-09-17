/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.yeastflow;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
/**
 * Tracks yeast cells in the microfluidic impedance measurement setup of Ralf and Niels from the BEL lab, Basel.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL.
 */
public class YeastFlowTracker extends RectangularClusterTracker {

    public static String getDescription(){
        return "Customized RectangularClusterTracker for tracking yeast cells";
    }

    public YeastFlowTracker (AEChip chip){
        super(chip);
        clusterLogger=new YeastFlowTracker.ClusterLogger();  // override the default logger
    }

    @Override
    protected void logData (BasicEvent ev,EventPacket<BasicEvent> ae){
        super.logData(ev,ae);
    }

    private class ClusterLogger extends RectangularClusterTracker.ClusterLogger{

        @Override
        protected void writeCluster (Cluster c){
            logStream.println(String.format("%d %f %f %f %d %d %f %f",c.getLastEventTimestamp(),c.location.x, c.location.y, c.getAverageEventDistance(), c.getNumEvents(), c.getLifetime(), c.getVelocityPPS().x, c.getVelocityPPS().y));

        }

        @Override
        protected String getFieldDescription (){
            return "lasttimestamp_us x_pixels y_pixels avgRadius_pixels numEvents lifetime_us velx_pps vely_pps";
        }

    }


}
