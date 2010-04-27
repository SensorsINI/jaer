/*
 * Select the drum sounds for the VirtualDrummer.
 * @author Jun
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;
import javax.swing.BoxLayout;
/**
 * Allows selection of drum sounds for virtual drummer demo.
 * @author Jun Haeng Lee/Tobi Delbruck
 */
public class BeatBoxSetting extends javax.swing.JFrame{
    public BeatBoxSetting (DrumSounds drumSounds){
        setTitle("VirtualDrummer.BeatBoxSetting");
        getContentPane().setLayout(new BoxLayout(getContentPane(),BoxLayout.Y_AXIS));
        for ( SoundPlayerInterface d:drumSounds.drums ){
            if ( d instanceof DrumSound ){
                getContentPane().add(new DrumSelector((DrumSound)d));
            } else if ( d instanceof SampledSoundPlayer ){
                getContentPane().add(new SampledSoundSelector((SampledSoundPlayer)d));
            }
        }
        pack();
    }
}
