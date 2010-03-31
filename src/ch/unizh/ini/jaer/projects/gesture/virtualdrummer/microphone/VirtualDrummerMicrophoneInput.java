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
import javax.sound.sampled.Port;
import javax.sound.sampled.TargetDataLine;

/**
 * Uses the microphone input to detect and generate {@link DrumSoundBeatEvent}'s.
 * This object is a thread that reads the microphone input and generates beats from it.
 *To use this class, make a new instance, register {@link DrumBeatSoundEventListener}'s,
 * and {@link #startReporting} the thread. To stop reporting, use {@link #stopReporting}.
 *
<p>
 *An instance of this class is constructed  by
 *{@link ch.unizh.ini.friend.gui.FriendGUI}
when microphone recording is activated;  listeners are added there.
 *<p>
 *A beat is positive edge triggered by the microphone input going above the {@link #setThreshold};
a new beat is not generated until the input falls below the threshold-{@link #setHystersis hystersis level}.
 *
 *

 * @author  $Author: tobi $
 *@version $Revision: 1.3 $
 */
public class VirtualDrummerMicrophoneInput extends Thread /*implements SpikeReporter,  Updateable*/ {

    Logger log = Logger.getLogger("VirtualDrummer");
    private AudioFormat format;
//    private float readbufferRatio = .5f;
    private float sampleRate = 8000f; // hz
    private float sampleTime = 1 / sampleRate;
    private long threadDelay = (long) 10; // ms
    private TargetDataLine targetDataLine;
//    private Port port;
    int numBits = 8;
    //    HighPassFilter highpass;
    byte threshold = 10, hysteresis = 3;
    boolean isBeat = false; // flag to mark if mic input is presently above threshold

    /** sets the threshold for detecting beats.
     *@param t the threshold.
     */
    public void setThreshold(byte t) {
        threshold = t;
    }

    /** @return the threshold
     * @see #setThreshold
     */
    public byte getThreshold() {
        return threshold;
    }

    /** sets the hystersis for beat detection. Set this to avoid triggering multiple beats on a noisy signal.
     *A new {@link DrumSoundBeatEvent} can be not generated until the input drops below the {@link #getThreshold threshold}-hystersis.
     */
    public void setHystersis(byte h) {
        hysteresis = h;
    }

    /** @return hysteresis
     *@see #setHystersis
     */
    public byte getHystersis() {
        return hysteresis;
    }

    /** Creates a new instance of test. Opens the microphone input as the target line.
     * To start the reporting, {@link #start} the thread.
     * @throws LineUnavailableException if microphone input is not available
     */
    public VirtualDrummerMicrophoneInput() throws LineUnavailableException {
//        getAudioInfo();  // prints lots of useless information
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 8, 1, 1, sampleRate, false);

        DataLine.Info dlinfo = new DataLine.Info(TargetDataLine.class,
                format);
        if (AudioSystem.isLineSupported(dlinfo)) {
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dlinfo);
        }
        targetDataLine.open();


    }

    /** starts acquisition from microphone port and generation of {@link DrumSoundBeatEvent}'s. If line is not available it does nothing. */
    public void startReporting() {
        stop = false;
        if (targetDataLine != null) {
            targetDataLine.start();
        } else {
            return;
        }
        super.start();
    }
    private boolean stop = false;

    /** removes all beat event listeners, ends thread after first stopping microphone acquisition. This ends generation of {@link DrumSoundBeatEvent}'s */
    public void stopReporting() {
        clearListeners();
        stop = true;
    }

    /** grabs samples from microphone input and generates {@link DrumSoundBeatEvent}'s whenver spikes are detected.
     * Stopped by {@link #stopReporting}
     */
    @Override
    public void run() {
        byte[] buffer = new byte[(int) (targetDataLine.getBufferSize())];
//        float val = 0f;
//        byte max = Byte.MIN_VALUE, min = Byte.MAX_VALUE;
        log.info("started monitoring microphone for drumbeats");
        while (!stop) {
            if (listeners.size() == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                continue;
            }// don't even bother unless someone is listening

            if (targetDataLine.available() < buffer.length) { // don't process until the buffer has enough data for our external buffer
//                System.out.println("mic recorder: yielding (only " + targetDataLine.available() + "/" + buffer.length + " available)");
                try {
                    sleep(threadDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            long lineTime = targetDataLine.getMicrosecondPosition();
            int nRead = targetDataLine.read(buffer, 0, buffer.length);
//            System.out.println("mic recorder: read "+nRead+" bytes");
            for (int i = 0; i < nRead; i++) {
                byte b = buffer[i];
                if (!isBeat) {
                    if (b > threshold) {
                        isBeat = true;
//                        System.out.println("mic recorder: spike");
                        informListeners(new DrumBeatSoundEvent(this, (long) (lineTime / 1000 + i * sampleTime * 1000)));
                    }
                } else { // beat
                    if (b < threshold - hysteresis) {
                        isBeat = false;
                    }
                }
                //                val=highpass.filter(b, sampleTime);
                //                max=(byte)(val>max?val:max);
                //                min=(byte)(val<min?val:min);
            }
            //            System.out.println(min+"\t"+max);
            try {
                sleep(threadDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        targetDataLine.stop();
//        System.out.println("mic recorder: ended");
    }

    void informListeners(DrumBeatSoundEvent e) {
        Iterator i = listeners.iterator();
        while (i.hasNext()) {
            DrumBeatSoundEventListener l = (DrumBeatSoundEventListener) i.next();
            l.drumBeatSoundOccurred(e);
        }

    }

    /** Release the line on finialization. */
    @Override
    protected void finalize() throws Throwable {
        if (targetDataLine.isOpen()) {
            targetDataLine.close();
        }
        super.finalize();
    }
    LinkedList listeners = new LinkedList();

    /** add a listener for all spikes. Listeners are {@link SpikeListener#spikeOccurred called} when a spike occurs and are passed a {@link DrumSoundBeatEvent}.
     *@param listener the listener
     */
    public void addBeatListener(DrumBeatSoundEventListener listener) {
        listeners.add(listener);
    }

    /** removes a listener
     *@param listener to remove
     */
    public void removeSpikeListener(DrumBeatSoundEventListener listener) {
        listeners.remove(listener);
    }

    /** remove all listeners */
    public void clearListeners() {
        listeners.clear();
    }

    /** test class by just printing . when it gets beats */
    public static void main(String[] args) {
        try {
            VirtualDrummerMicrophoneInput reporter = new VirtualDrummerMicrophoneInput();
            reporter.addBeatListener(new DrumBeatSoundPrinter());
            reporter.startReporting();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** @return the Collection of listeners  */
    public Collection getBeatListeners() {
        return listeners;
    }

    // prints some audio system information
    void getAudioInfo() {

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        log.info(mixerInfos.length + " mixers");
        for (int i = 0; i < mixerInfos.length; i++) {

            Mixer mixer = AudioSystem.getMixer(mixerInfos[i]);
            System.out.println("Mixer " + mixer);
            // target data lines
            Line.Info[] lineInfos = mixer.getTargetLineInfo();
            System.out.println("\t" + lineInfos.length + " lineInfos");
            for (int j = 0; j < lineInfos.length; j++) {
                if (lineInfos[j] instanceof DataLine.Info) {
                    AudioFormat[] formats = ((DataLine.Info) lineInfos[j]).getFormats();
                    System.out.println("\t\t\t" + formats.length + " formats");
                    for (int k = 0; k < formats.length; k++) {
                        System.out.println("\t\tFormat " + formats[k]);
                    }
                }
                Line line = null;
                try {
                    line = mixer.getLine(lineInfos[j]);
                    System.out.println("\tLine " + line);
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** @return whether reporting is enabled ({@link java.lang.Thread#isAlive})  */
    public boolean isReporting() {
        return isAlive();
    }

    /** Computes the new state of this component of the simulation.
     * @param dt The time that has passed since the last invocation.
     *
     */
    public void compute(float dt) {
        // does nothing
    }

    /** Updates the actual state to the newly computed - aka double-buffering.
     *
     */
    public void update() {
        // does nothing
    }
}

