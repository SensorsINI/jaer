package ch.unizh.ini.jaer.projects.gesture.virtualdrummer.microphone;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
/**
 * Uses the microphone input to detect and generate {@link DrumBeatSoundEvent}'s.
 * This object is a thread that reads the microphone input and generates beats from it.
 *To use this class, make a new instance, register {@link DrumBeatSoundEventListener}'s,
 * and {@link #startReporting} the thread. To stop reporting, use {@link #stopReporting}.
 *
<p>
 *An instance of this class is constructed 
when microphone recording is activated;  listeners are added there.
 *<p>
 *A beat is positive edge triggered by the microphone input going above the {@link #setThreshold};
a new beat is not generated until the input falls below the threshold-{@link #setHystersis hystersis level}.
 *
 *

 * @author  $Author: tobi $
 *@version $Revision: 1.3 $
 */
public class VirtualDrummerMicrophoneInput extends Thread /*implements SpikeReporter,  Updateable*/{
    Logger log = Logger.getLogger("VirtualDrummer");
    private AudioFormat format;
//    private float readbufferRatio = .5f;
    private float sampleRate = 8000f; // hz
    private float sampleTime = 1 / sampleRate;
    private long threadDelay = (long)10; // ms
    private TargetDataLine targetDataLine;
    int numBits = 8;
    byte threshold = 10, hysteresis = 3;
    boolean isBeat = false; // flag to mark if mic input is presently above threshold
    private float tau = 30;  // time const of lp filter, ms
    private byte[] buffer = null;
    DrumSoundDetectorDemo gui;
    private int bufferSize=1000;

    /** Creates a new instance of test. Opens the microphone input as the target line.
     * To start the reporting, {@link #start} the thread.
     * @throws LineUnavailableException if microphone input is not available
     */
    public VirtualDrummerMicrophoneInput () throws LineUnavailableException{
//        getAudioInfo();  // prints lots of useless information
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,sampleRate,8,1,1,sampleRate,false);

        DataLine.Info dlinfo = new DataLine.Info(TargetDataLine.class,
                format);
        if ( AudioSystem.isLineSupported(dlinfo) ){
            targetDataLine = (TargetDataLine)AudioSystem.getLine(dlinfo);
        }
        targetDataLine.open(format,bufferSize);
        bufferSize=targetDataLine.getBufferSize();
        gui = new DrumSoundDetectorDemo();
        gui.setVirtualDrummerMicrophoneInput(this);

    }

    /** starts acquisition from microphone port and generation of {@link DrumBeatSoundEvent}'s. If line is not available it does nothing. */
    public void startReporting (){
        stop = false;
        if ( targetDataLine != null ){
            targetDataLine.start();
        } else{
            return;
        }
        super.start();
        gui.setVisible(true);
    }
    private boolean stop = false;

    /** removes all beat event listeners, ends thread after first stopping microphone acquisition. This ends generation of {@link DrumBeatSoundEvent}'s */
    public void stopReporting (){
        clearListeners();
        stop = true;
        gui.setVisible(false);
    }

    /** grabs samples from microphone input and generates {@link DrumBeatSoundEvent}'s whenver spikes are detected.
     * Stopped by {@link #stopReporting}
     */
    @Override
    public void run (){
        float lpval = 0;
        buffer = new byte[ (int)( targetDataLine.getBufferSize() ) ];
//        float val = 0f;
//        byte max = Byte.MIN_VALUE, min = Byte.MAX_VALUE;
        log.info("started monitoring microphone for drumbeats");
        while ( !stop ){
            if ( listeners.size() == 0 ){
                try{
                    Thread.sleep(100);
                } catch ( InterruptedException e ){
                }
                continue;
            }// don't even bother unless someone is listening

            if ( targetDataLine.available() < buffer.length/4 ){ // don't process until the buffer has enough data for our external buffer
//                System.out.println("mic recorder: yielding (only " + targetDataLine.available() + "/" + buffer.length + " available)");
                try{
                    sleep(threadDelay);
                } catch ( InterruptedException e ){
                    e.printStackTrace();
                }
                continue;
            }

            float m = 1 / ( sampleRate * tau / 1000 );
            long lineTime = targetDataLine.getMicrosecondPosition();
            int nRead = targetDataLine.read(buffer,0,buffer.length);
            for ( int i = 0 ; i < nRead ; i++ ){
                byte b = buffer[i];
                int b2 = b * b;
                lpval = ( 1 - m ) * lpval + m * ( b2 - lpval ); // lowpass IIR filter of squared sound signal
                gui.addSample((float)Math.sqrt(lpval));
                if ( !isBeat ){
                    if ( lpval > threshold ){
                        isBeat = true;
//                        System.out.println("mic recorder: spike");
                        informListeners(new DrumBeatSoundEvent(this,(long)( lineTime / 1000 + i * sampleTime * 1000 )));
                    }
                } else{ // beat
                    if ( lpval < threshold - hysteresis ){
                        isBeat = false;
                    }
                }
            }
            try{
                sleep(threadDelay);
            } catch ( InterruptedException e ){
                e.printStackTrace();
            }
        }
        targetDataLine.stop();
    }

    void informListeners (DrumBeatSoundEvent e){
        Iterator i = listeners.iterator();
        while ( i.hasNext() ){
            DrumBeatSoundEventListener l = (DrumBeatSoundEventListener)i.next();
            l.drumBeatSoundOccurred(e);
        }

    }

    /** Release the line on finialization. */
    @Override
    protected void finalize () throws Throwable{
        if ( targetDataLine.isOpen() ){
            targetDataLine.close();
        }
        super.finalize();
    }
    LinkedList listeners = new LinkedList();

    /** add a listener for all spikes. Listeners are {@link DrumBeatSoundEventListener#drumBeatSoundOccurred(ch.unizh.ini.jaer.projects.gesture.virtualdrummer.microphone.DrumBeatSoundEvent)  called} when a spike occurs and are passed a {@link DrumBeatSoundEvent}.
     *@param listener the listener
     */
    public void addBeatListener (DrumBeatSoundEventListener listener){
        listeners.add(listener);
    }

    /** removes a listener
     *@param listener to remove
     */
    public void removeBeatListener (DrumBeatSoundEventListener listener){
        listeners.remove(listener);
    }

    /** remove all listeners */
    public void clearListeners (){
        listeners.clear();
    }

    /** test class by just printing . when it gets beats */
    public static void main (String[] args){
        try{
            VirtualDrummerMicrophoneInput reporter = new VirtualDrummerMicrophoneInput();
            reporter.addBeatListener(new DrumBeatSoundPrinter());
            reporter.startReporting();
        } catch ( LineUnavailableException e ){
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** @return the Collection of listeners  */
    public Collection getBeatListeners (){
        return listeners;
    }

    // prints some audio system information
    void getAudioInfo (){

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        log.info(mixerInfos.length + " mixers");
        for ( int i = 0 ; i < mixerInfos.length ; i++ ){

            Mixer mixer = AudioSystem.getMixer(mixerInfos[i]);
            System.out.println("Mixer " + mixer);
            // target data lines
            Line.Info[] lineInfos = mixer.getTargetLineInfo();
            System.out.println("\t" + lineInfos.length + " lineInfos");
            for ( int j = 0 ; j < lineInfos.length ; j++ ){
                if ( lineInfos[j] instanceof DataLine.Info ){
                    AudioFormat[] formats = ( (DataLine.Info)lineInfos[j] ).getFormats();
                    System.out.println("\t\t\t" + formats.length + " formats");
                    for ( int k = 0 ; k < formats.length ; k++ ){
                        System.out.println("\t\tFormat " + formats[k]);
                    }
                }
                Line line = null;
                try{
                    line = mixer.getLine(lineInfos[j]);
                    System.out.println("\tLine " + line);
                } catch ( LineUnavailableException e ){
                    e.printStackTrace();
                }
            }
        }
    }

    /** @return whether reporting is enabled ({@link java.lang.Thread#isAlive})  */
    public boolean isReporting (){
        return isAlive();
    }

    /**
     * @return the tau in ms of LP filter of sound power
     */
    public int getTau (){
        return (int)tau;
    }

    /**
     * @param tau the tau in ms of LP filter to set
     */
    public void setTau (int tau){
        this.tau = tau;
        log.info("set tau="+tau+"ms");
    }

    /**
     * @return the buffer
     */
    public // time const of lp filter, seconds
            byte[] getBuffer (){
        return buffer;
    }

    /** sets the threshold for detecting beats.
     *@param t the threshold.
     */
    public void setThreshold (int t){
        threshold = (byte)t;
    }

    /** @return the threshold
     * @see #setThreshold
     */
    public int getThreshold (){
        return threshold;
    }

    /** sets the hystersis for beat detection. Set this to avoid triggering multiple beats on a noisy signal.
     *A new {@link DrumBeatSoundEvent} can be not generated until the input drops below the {@link #getThreshold threshold}-hystersis.
     */
    public void setHystersis (int h){
        hysteresis = (byte)h;
    }

    /** @return hysteresis
     *@see #setHystersis
     */
    public int getHystersis (){
        return hysteresis;
    }

    /**
     * @return the bufferSize
     */
    public int getBufferSize (){
        return bufferSize;
    }
}

