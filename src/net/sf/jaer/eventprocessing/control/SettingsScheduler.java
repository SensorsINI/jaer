/*
 * Tobi 27.4.2009
 */

package net.sf.jaer.eventprocessing.control;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Selects bias and filter settings for a chip based on a clock-based schedule, e.g. night/day/twilight
 * @author tobi
 */
@Description("Activates filter settings based on schedule")
public class SettingsScheduler extends EventFilter2D {

    public static DevelopmentStatus getDevelopmentStatus(){ return DevelopmentStatus.Alpha;}
    

    public enum Daytime {Night,Twilight,Day};
    private Daytime daytime;

    Timer timer;
    
    ArrayList<File> biasFiles=new ArrayList<File>(2);

    public SettingsScheduler(AEChip chip){
        super(chip);
    }

    @Override
    public void resetFilter() {

    }

    @Override
    public void initFilter() {

        timer=new Timer("BiasSettingsControllerTimer",true);  // create as daemon to avoid prolonging exit

    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private class BiasLoader extends TimerTask{

        @Override
        public void run() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

}
