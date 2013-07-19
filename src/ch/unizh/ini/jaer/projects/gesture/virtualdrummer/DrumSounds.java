/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
/**
 * Encapsulates the drum sounds for the VirtualDrummer.
 * @author Tobi, Jun
 */
public class DrumSounds{
    private static Preferences prefs = Preferences.userNodeForPackage(DrumSounds.class);
    private Logger log = Logger.getLogger("DrumSounds");
    final static int NDRUMS = 2;
    final static int LEFT_BEATING = 0;
    final static int RIGHT_BEATING = 1;
    public SoundPlayerInterface[] drums = new SoundPlayerInterface[ NDRUMS ];
    public enum Type{
        MIDI, Sampled
    }
    public Type type=null;

    /** Construct a new set of drum sounds.
     *
     * @param type the sounds are either MIDI or Sampled type; this parameter chooses which type are built.
     */
    public DrumSounds (Type type){
        this.type=type;
        try{
            switch ( type ){
                case MIDI:
                    for ( int i = 0 ; i < NDRUMS ; i++ ){
                        drums[i] = new DrumSound(i);
                    }
                    break;
                case Sampled:
                    for ( int i = 0 ; i < NDRUMS ; i++ ){
                        drums[i] = new SampledSoundPlayer(i);
                    }
            }
        } catch ( Exception e ){
            log.warning(e.toString());
        }

    }

    public void play (final int drumNumber,int vel){
        if ( drumNumber < 0 || drumNumber > NDRUMS ){
            log.warning("No drum number " + drumNumber + ", range is 0 to " + NDRUMS);
            return;
        }
        drums[drumNumber].play();
    }
}
