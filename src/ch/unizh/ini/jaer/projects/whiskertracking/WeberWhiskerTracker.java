/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.whiskertracking;

import net.sf.jaer.chip.AEChip;
import org.ine.telluride.jaer.tell2010.pigtracker.PigTracker;

/**
 * Adapts PigTracker to whisker tracking for Bruno Weber lab, UZH, Universitaetspital.
 *
 * @author tobi
 */
public class WeberWhiskerTracker extends PigTracker{

    public static String getDescription(){
        return "Tracks whiskers for Weber lab";
    }
    private String hostname=getString("hostname", "localhost");
    private int port=getInt("port",8883);


    public WeberWhiskerTracker(AEChip chip) {
        super(chip);
        final String whisk="whisker";
        setPropertyTooltip(whisk, "maxNumWhiskers", "maximum number of whiskers to track");
        setPropertyTooltip(whisk, "hostname", "hostname or IP address of host to send whisker tracker output");
        setPropertyTooltip(whisk, "port", "port number to send to on hostname");
        chip.addObserver(this);
    }


}
