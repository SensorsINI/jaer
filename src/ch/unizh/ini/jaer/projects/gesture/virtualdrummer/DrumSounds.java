/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;

/**
 * Encapsulates the drum sounds for the VirtualDrummer.
 * @author tobi
 */
public class DrumSounds {

    Logger log = Logger.getLogger("DrumSounds");
    Synthesizer synth = null;
    private int nChannel = 0;
    private int bank = 1;
    private int program = 38;
    private MidiChannel channel = null;
    private long durationMs = 200;
    private Timer timer = new Timer();

    public DrumSounds() {
    }

    public void play(final int note, int vel) {
        if (channel == null) {
            return;
        }
        channel.noteOn(note, vel);
        TimerTask noteofftask = new TimerTask() {

            public void run() {
                channel.noteOff(note);

            }
        };
        timer.schedule(noteofftask, durationMs);
    }

    public void open() {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
        } catch (MidiUnavailableException e) {
            log.warning(e.toString());
            return;
        }
        log.info("Synthesizer: " + synth);

        Soundbank soundBank = synth.getDefaultSoundbank();
        System.out.println("soundbank= " + soundBank.getDescription());
        Instrument[] instruments = soundBank.getInstruments();
        System.out.println("soundbank  instruments");
        for (Instrument i : instruments) {
            System.out.println(i.toString());
        }

        MidiChannel[] channels = synth.getChannels();
        channel = channels[nChannel];
        if (channel == null) {
            log.warning("selected midi channel " + nChannel + " is null, cannot play notes");
            return;
        }
        channel.programChange(bank, program);

    }

    public void close() {
        if (synth != null) {
            synth.close();
        }
    }
}
