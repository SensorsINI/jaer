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
 * @author tobi
 */
public class DrumSounds {

    Preferences prefs = Preferences.userNodeForPackage(DrumSounds.class);
    Logger log = Logger.getLogger("DrumSounds");
    Synthesizer synth = null;
    final static int NDRUMS = 4;
    private int nChannel = prefs.getInt("DrumSounds.channel", 0);
    private int defaultBank = prefs.getInt("DrumSounds.bank", 1);
    private int defaultProgram = prefs.getInt("DrumSounds.program", 1);
    private int defaultDurationMs = prefs.getInt("DrumSounds.durationMs", 200);
    private int defaultNote=prefs.getInt("DrumSounds.note",30);

    class Drum {

        int bank, program, note, durationMs;

        public Drum(int bank, int program, int note, int durationMs) {
            this.bank = bank;
            this.program = program;
            this.note = note;
            this.durationMs = durationMs;
        }

        public String toString(){
            if(channel==null) return "Drum sound - synthesizer not open";
            else {
                return "Drum sound";
            }
        }

        void play(int vel) {
            if (channel == null) {
                return;
            }
            channel.programChange(bank, program);
            channel.noteOn(note, vel);
            System.out.println(String.format("bank=%d program=%d note=%d velocity=%d", bank, program, note, vel));
            TimerTask noteofftask = new TimerTask() {

                public void run() {
                    channel.noteOff(note);

                }
            };
            timer.schedule(noteofftask, durationMs);

        }
    }
    Drum[] drums = new Drum[NDRUMS];
    private MidiChannel channel = null;
    private Timer timer = new Timer();

    public DrumSounds() {
        Random r=new Random();
        for (int i = 0; i < NDRUMS; i++) {
            drums[i] = new Drum(defaultBank, r.nextInt(127), defaultNote, defaultDurationMs);
        }
    }

    public void play(final int drumNumber, int vel) {
        if(drumNumber<0 || drumNumber>NDRUMS){
            log.warning("No drum number "+drumNumber+", range is 0 to "+NDRUMS);
            return;
        }
        drums[drumNumber].play(vel);
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
            i.getPatch().getBank();
            i.getPatch().getProgram();
        }

        MidiChannel[] channels = synth.getChannels();
        channel = channels[nChannel];
        if (channel == null) {
            log.warning("selected midi channel " + nChannel + " is null, cannot play notes");
            return;
        }
        channel.programChange(defaultBank, defaultProgram);

    }

    public void close() {
        if (synth != null) {
            synth.close();
        }
    }
}
