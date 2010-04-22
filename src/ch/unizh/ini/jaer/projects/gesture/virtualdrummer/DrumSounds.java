/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;

/**
 * Encapsulates the drum sounds for the VirtualDrummer.
 * @author Tobi, Jun
 */
public class DrumSounds {

    private static Preferences prefs = Preferences.userNodeForPackage(DrumSounds.class);
    private Logger log = Logger.getLogger("DrumSounds");
    final static int NDRUMS = 2;
    final static int LEFT_BEATING = 0;
    final static int RIGHT_BEATING = 1;
    public DrumSound[] drums = new DrumSound[NDRUMS];

    public DrumSounds() {
        for(int i=0;i<NDRUMS;i++){
            drums[i]=new DrumSound(i);
        }
    }

    public void play(final int drumNumber, int vel) {
        if (drumNumber < 0 || drumNumber > NDRUMS) {
            log.warning("No drum number " + drumNumber + ", range is 0 to " + NDRUMS);
            return;
        }
        drums[drumNumber].play(vel);
    }

}
