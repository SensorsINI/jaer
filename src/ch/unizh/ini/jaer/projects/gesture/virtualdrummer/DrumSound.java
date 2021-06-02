package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Synthesizer;
/**
 * A particular drum sound.
 */
public class DrumSound implements SoundPlayerInterface{
    private static Preferences prefs = Preferences.userNodeForPackage(DrumSound.class);
    int drumNumber = 0;
    private int note;  // this is actually the drum we play, since we use percussion channel 9
    private int durationMs;
    private int velocity;
    MidiDevice midiDevice = null;
    private MidiChannel channel;
    DrumSounds drumSounds;
    private Timer timer = new Timer();
    private MidiChannel[] channels = null; // each channel is one drum
    private Logger log = Logger.getLogger("DrumSound");
    private Synthesizer synth = null;
    private int index;
    private String name;
    /** Possible instrument names */
    public static final String[] PERCUSSION_NAMES = { "Bass Drum","Closed Hi-Hat","Open Hi-Hat",
        "Acoustic Snare","Crash Cymbal","Hand Clap",
        "High Tom","Hi Bongo","Maracas","Whistle",
        "Low Conga","Cowbell","Vibraslap","Low-mid Tom",
        "High Agogo","Open Hi Conga" };
    /** Instrument program numbers, extend from 35 to 81 */
    public static final int[] PERCUSSION_NOTE_NUMBERS = { 35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63 };
    // Channel 10 is the GeneralMidi percussion channel.  In Java code, we
    // number channels from 0 and use channel 9 instead.
    private final int PERCUSSION_CHANNEL = 9;

    /** Constructs a drum for channel 0.
     * 
     */
    public DrumSound () throws MidiUnavailableException{
        this(0);
    }

    /** Constructs a new DrumSound, using the midi channel number given.
     * The synthesizer is opened.
     * The note and duration are also set from stored preferences for this drumNumber.
     *
     * @param drumNumber an arbitrary drum number - has nothing to do with sound emitted.
     */
    public DrumSound (int drumNumber) throws MidiUnavailableException{
        this.drumNumber = drumNumber;
        open();
        this.note = prefs.getInt(prefsKey() + ".note",PERCUSSION_NOTE_NUMBERS[0]);
        this.durationMs = prefs.getInt(prefsKey() + ".durationMs",500);
        this.velocity = prefs.getInt(prefsKey() + ".velocity",127);
        findSoundNameAndIndex();
    }

    private void findSoundNameAndIndex (){
        // from stored note, set name and index
        for ( int i = 0 ; i < PERCUSSION_NAMES.length ; i++ ){
            if ( note == PERCUSSION_NOTE_NUMBERS[i] ){
                name = PERCUSSION_NAMES[i];
                index = i;
            }
        }
    }

    private String prefsKey (){
        return "DrumSound." + drumNumber;
    }

    public String toString (){
        if ( channel == null ){
            return "Drum sound - synthesizer not open";
        } else{
            return "Drum sound";
        }
    }

    private void open () throws MidiUnavailableException{
        synth = MidiSystem.getSynthesizer();
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        MidiDevice.Info msInfo = null;
        StringBuilder sb = new StringBuilder();
        sb.append("Available MidiDevice are\n");
        for ( MidiDevice.Info i:infos ){
            if ( i.toString().contains("Microsoft GS Wavetable Synth") ){
                msInfo = i;
                sb.append(" *****");
            }
            sb.append("\t" + i.toString() + ": " + i.getDescription() + '\n');
        }
//        MidiDevice msDevice = MidiSystem.getMidiDevice(msInfo);
        synth.open();

        sb.append("synth=" + synth.getDeviceInfo().toString() + " with default soundbank " + synth.getDefaultSoundbank().getDescription() + '\n');
        sb.append("max synthesizer latency =" + synth.getLatency() + " us\n");
        log.info(sb.toString());
        channels = synth.getChannels();
        channel = channels[PERCUSSION_CHANNEL];
    }

    public void close (){
        synth.close();
    }
    class MyReceiver implements Receiver{
        public void close (){
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void send (MidiMessage message,long timeStamp){
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    /** Play with current velocity */
    public void play (){
        if ( channel == null ){
            return;
        }
        channel.noteOn(note,velocity);
        TimerTask noteofftask = new TimerTask(){
            public void run (){
                channel.noteOff(note);
            }
        };
        timer.schedule(noteofftask,durationMs);
    }

    public String getInstrumentName (){
        return PERCUSSION_NAMES[note];
    }

    /**
     * @return the note
     */
    public int getNote (){
        return note;
    }

    /**
     * @param note the note to set (the particular percussion sound)
     */
    public void setNote (int note){
        note = clip(note);
        this.note = note;
        prefs.putInt(prefsKey() + ".note",note);
        findSoundNameAndIndex();
    }

    /**
     * @return the durationMs
     */
    public int getDurationMs (){
        return durationMs;
    }

    /**
     * @param durationMs the durationMs to set
     */
    public void setDurationMs (int durationMs){
        this.durationMs = durationMs;
        prefs.putInt(prefsKey() + ".durationMs",durationMs);
    }

    /**
     * @return the preference velocity.
     */
    public int getVelocity (){
        return velocity;
    }

    /**
     * Sets the preferred (default) velocity.
     * @param velocity  the volume to set
     */
    public void setVelocity (int velocity){
        velocity = clip(velocity);
        this.velocity = velocity;
        prefs.putInt(prefsKey() + ".volume",velocity);
    }

    /** Sets velocity for next played note, without storing in preferences.
     */
    public void setVelocityVolatile (int velocity){
        this.velocity = clip(velocity);
    }

    private int clip (int i){
        if ( i < 0 ){
            i = 0;
        } else if ( i > 127 ){
            i = 127;
        }
        return i;
    }

    /** The midi channel number (same as drumNumber) for this drum */
    public int getDrumNumber (){
        return drumNumber;
    }

    /**
     * Returns the index of the percussion sound in PERCUSSION_NOTE_NUMBERS[]
     * @return the index
     */
    public int getIndex (){
        return index;
    }

    /**
     * Sets percussion sound by index in PERCUSSION_NAMES[]
     * @param index the index to set
     */
    public void setIndex (int index){
        this.index = index;
    }

    /**
     * @return the name
     */
    public String getName (){
        return name;
    }
}
