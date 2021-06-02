package ch.unizh.ini.jaer.projects.gesture.virtualdrummer.microphone;


import java.util.EventObject;

/**
 * Represents a drum beat sound detection.
 *
 * @author  $Author: tobi $
 *@version $Revision: 1.1 $
 */
public class DrumBeatSoundEvent extends EventObject {

    long time=0;

    /** Creates a new instance of DrumBeatSoundEvent. The {@link #getTime time} of the spike is set to System.currentTimeMillis().
     @param source the object that generated the spike
     */
    public DrumBeatSoundEvent(Object source) {
        super(source);
        time=System.currentTimeMillis();
    }

    /** Creates a new instance of DrumBeatSoundEvent
     @param source the object that generated the spike
     *@param time the time of the spike
     */
    public DrumBeatSoundEvent(Object source, long time){
        this(source);
        this.time=time;
    }

    public long getTime(){ return time; }
    public void setTime(long t){time=t;}

    /** @return time of spike as string */
    public String toString(){
        return Long.toString(time);
    }

}
