package ch.unizh.ini.jaer.projects.gesture.virtualdrummer.microphone;

import java.util.EventListener;

/**
 * Listeners should implement this interface in order to be able to register themselves as recipients of spike events.
 *
 */
public interface DrumBeatSoundEventListener extends EventListener {

    /** called by source  when a drum beat sound is detected
     @param e the  event
     */
    public void drumBeatSoundOccurred(DrumBeatSoundEvent e);

}