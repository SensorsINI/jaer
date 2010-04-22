package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.sound.midi.*;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Synthesizer;

/**
 * A particular drum sound.
 */
public class DrumSound {

    private static Preferences prefs = Preferences.userNodeForPackage(DrumSound.class);
    int channelNumber = 0;
    private int bank;
    private int program;
    private int note;
    private int durationMs;
    private int volume;
    private MidiChannel channel;
    DrumSounds drumSounds;
    private Timer timer = new Timer();
    private MidiChannel[] channels = null; // each channel is one drum
    private Logger log = Logger.getLogger("DrumSound");
    private Synthesizer synth = null;
    /** Possible instrument names */
    public static final String[] INSTRUMENT_NAMES = {"Bass Drum", "Closed Hi-Hat", "Open Hi-Hat",
        "Acoustic Snare", "Crash Cymbal", "Hand Clap",
        "High Tom", "Hi Bongo", "Maracas", "Whistle",
        "Low Conga", "Cowbell", "Vibraslap", "Low-mid Tom",
        "High Agogo", "Open Hi Conga"};
    /** Instrument program numbers */
    public static final int[] INSTRUMENT_PROGRAM_NUMBERS = {35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};

    /** Constructs a drum for channel 0.
     * 
     */
    public DrumSound(){
        this(0);
    }

    /** Constructs a new DrumSound, using the midi channel number given.
     * The synthesizer is opened and the selected MIDI channel is used to select a stored preference for the bank and program.
     * The note and duration are also set from stored preferences for this channelNumber.
     *
     * @param channelNumber a valid midi channel number in range 0-12.
     */
    public DrumSound(int channelNumber) {
        this.channelNumber = channelNumber;
        try {
            open();
        } catch (MidiUnavailableException ex) {
            log.warning(ex.toString());
        }
        this.bank = prefs.getInt(prefsKey() + ".bank", 0);
        this.program = prefs.getInt(prefsKey() + ".program", INSTRUMENT_PROGRAM_NUMBERS[0]);
        this.note = prefs.getInt(prefsKey() + ".note", 63);
        this.durationMs = prefs.getInt(prefsKey() + ".durationMs", 200);
        setProgram(program);
    }

    public DrumSound(MidiChannel channel, int bank, int program, int note, int durationMs, DrumSounds drumSounds) {
        super();
        this.drumSounds = drumSounds;
        this.channel = channel;
        this.bank = bank;
        this.program = program;
        this.note = note;
        this.durationMs = durationMs;
        channel.programChange(program);
    }

    private String prefsKey() {
        return "DrumSound." + channelNumber;
    }

    public String toString() {
        if (channel == null) {
            return "Drum sound - synthesizer not open";
        } else {
            return "Drum sound";
        }
    }

    private void open() throws MidiUnavailableException {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        channels = synth.getChannels();
    }

    /** Plays the note with given velocity.
     *
     * @param vel 0-127 velocity (loudness)
     */
    public void play(int vel) {
        if (channel == null) {
            return;
        }
        channel.noteOn(getNote(), vel);
        TimerTask noteofftask = new TimerTask() {

            public void run() {
                channel.noteOff(getNote());
            }
        };
        timer.schedule(noteofftask, getDurationMs());
    }

    public String getInstrumentName(){
        return INSTRUMENT_NAMES[program];
    }

    /** Sets drum by instrument name.
     *
     * @param name one of the INSTRUMENT_NAMES
     */
    public void setInstrumentName(String name){
        for(int i=0;i<INSTRUMENT_NAMES.length;i++){
            if(name.equals(INSTRUMENT_NAMES[i])){
                setProgram(INSTRUMENT_PROGRAM_NUMBERS[i]);
            }
        }
    }


    /**
     * @return the program
     */
    public int getProgram() {
        return program;
    }
    
    /** Sets the program (instrument).
     * 
     * @param program
     */
    public void setProgram(int program) {
        this.program = program;
        prefs.putInt(prefsKey()+".program", program);
        if(channel==null) return;
        channel.programChange(bank, program);
    }

    /**
     * @return the note
     */
    public int getNote() {
        return note;
    }

    /**
     * @param note the note to set
     */
    public void setNote(int note) {
        this.note = note;
        prefs.putInt(prefsKey()+".note",note);
    }

    /**
     * @return the durationMs
     */
    public int getDurationMs() {
        return durationMs;
    }

    /**
     * @param durationMs the durationMs to set
     */
    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
        prefs.putInt(prefsKey()+".durationMs", durationMs);
    }

    /**
     * @return the volume
     */
    public int getVolume() {
        return volume;
    }

    /**
     * @param volume the volume to set
     */
    public void setVolume(int volume) {
        this.volume = volume;
        prefs.putInt(prefsKey()+".volume",volume);
    }


}
