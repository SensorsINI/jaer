package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.HeadlessException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.SoundPlayerInterface;
import java.io.BufferedInputStream;

/**
 * Plays a sampled sound card name on the speaker.
 * Use it by constructing a new {@link SlotCarSoundSample}, then calling the {@link #play} method.
 * The sounds are stored in the resources folder and a file named "sounds.txt" holds the filenames
 * of the sound files, which should be wav files.
 *
 * @author  tobi
 * @version $Revision: 1.8 $
 */
public class SlotCarSoundSample implements SoundPlayerInterface {

    /** Path header to sounds files. */
    static public final String PATH_HEADER = "ch/unizh/ini/jaer/projects/virtualslotcar/resources/";
    static public final String SRC_HEADER = "src/"; // if sournds not in jar file, then look in jAER src tree
    static public final String INDEX_FILE_NAME = "sounds.txt"; // has names of sound files
    private String filename;
    /** length of intermediate buffer in bytes */
    private int bufferLength = 0;
    private AudioFormat audioFormat = null;
    private AudioInputStream audioInputStream = null;
    private byte[] samples = null; // , spikeSoundSamplesLeft, spikeSoundSamplesRight;
//    private AudioInputStream spikeStream, spikeStreamLeft, spikeStreamRight;
    private SourceDataLine line = null;
    static final Logger log = Logger.getLogger("SlotcarSoundEffects");
    private SlotcarSoundEffectsThread T;
    // used for buffering in to out in play()...
    private byte[] abData = null;
    private FloatControl panControl = null, volumeControl = null;
    
    
    
    /** Creates new SlotcarSoundEffects labeled number i, using sampled sound stored as preference for this number.
     *
     * @param soundNumber an index, used to look up the preferred sound file.
     * @throws IOException
     * @throws LineUnavailableException
     * @throws UnsupportedAudioFileException
     */
    public SlotCarSoundSample(String filename) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
//        if (soundNumber >= SlotCarSoundSample.getSoundFilePaths().size()) {
//            throw new IOException("There is no sound number " + soundNumber + " available");
//        }
        setFile(filename);
        open();
    }

    private void open() throws LineUnavailableException {
        // get info on possible SourceDataLine's
        // these are lines that you can source into
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        // get a line from the system
        line = (SourceDataLine) AudioSystem.getLine(info);
//            line = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);
        //open it with our AudioFormat
        line.open(audioFormat);
        line.start();
        Control[] controls = line.getControls();
        StringBuilder sb = new StringBuilder("Line controls are: ");
        for (Control c : controls) {
            sb.append(", ").append(c.toString());
        }
        log.info(sb.toString());
        if (line.isControlSupported(FloatControl.Type.VOLUME)) {
            volumeControl = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
        } else if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        }
        if (line.isControlSupported(FloatControl.Type.PAN)) {
            panControl = (FloatControl) line.getControl(FloatControl.Type.PAN);
        }
        T = new SlotcarSoundEffectsThread();
        T.start();
    }

    /** plays the spike sound once, by notifying the player thread to send the data to the line. */
    @Override
	synchronized public void play() {
        if (T == null) {
            return;
        }
//        System.out.println("notifying player thread");
        synchronized (T) {
            T.notify();
        }
    }

    @Override
	public void close() {
        if (T == null) {
            return;
        }
        synchronized (T) {
            T.interrupt();
        }
    }
    private static ArrayList<String> nameList = null;

    public static ArrayList<String> getSoundFilePaths() {
        if (nameList != null) {
            return nameList;
        }
        nameList = new ArrayList<String>();
        try {
            InputStream inputStream;
            inputStream = SlotCarSoundSample.class.getResourceAsStream(PATH_HEADER + INDEX_FILE_NAME);
            String pathHeader = PATH_HEADER;
            if (inputStream == null) {
                pathHeader = SRC_HEADER + PATH_HEADER;
                inputStream = new FileInputStream(pathHeader + INDEX_FILE_NAME);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = null;
            while ((line = reader.readLine()) != null) {
                if ((line.length() == 0) || line.startsWith("#")) {
                    continue;
                }
                log.info("added sound \"" + line + "\"");
                nameList.add(pathHeader + line);
            }


        } catch (Exception e) {
            log.warning(e.toString());
        }
        return nameList;
    }

    /** Loads a binary sound file into memory. The filename is used to search the resource path (i.e. the jar archives on the classpath).
     * If the file is not found in the resources (jars or classes on classpath) then the file system is checked.
     *@param soundNumber the file number of the available files
     **/
    public final synchronized boolean setFile(String filename) throws UnsupportedAudioFileException, FileNotFoundException, IOException {
//        if (soundNumber >= getSoundFilePaths().size()) {
//            throw new FileNotFoundException("invalid file index");
//        }
        this.filename = SRC_HEADER + PATH_HEADER+ filename; // getSoundFilePaths().get(soundNumber);
        InputStream inputStream;
        // load firmware file (this is binary file of 8051 firmware)
        inputStream = getClass().getResourceAsStream(this.filename);
        if (inputStream == null) {
            inputStream = new FileInputStream(this.filename);
        }
        InputStream bufferedIn=new BufferedInputStream(inputStream);
        audioInputStream = AudioSystem.getAudioInputStream(bufferedIn);
        audioFormat = audioInputStream.getFormat();
        abData = new byte[audioInputStream.available()];
       if (audioInputStream.markSupported()) {
           audioInputStream.mark(abData.length);
            return true;
        }
        samples = new byte[audioInputStream.available()]; // hopefully we get entire
        bufferLength = audioInputStream.read(samples);
        abData = new byte[bufferLength / 8];
        audioInputStream = new AudioInputStream(new ByteArrayInputStream(samples), audioFormat, bufferLength);
        log.info("loaded sound named " + filename + " with " + bufferLength + " samples at sample rate " + audioFormat.getSampleRate());
        return false;
    }

    public void setVolume(float f) {
        if (volumeControl == null) {
            return;
        }
        float max = volumeControl.getMaximum(), min = volumeControl.getMinimum();
        volumeControl.setValue((f * (max - min)) + min);
    }

    public void setPan(float f) {
        if (panControl == null) {
            return;
        }
        float max = panControl.getMaximum(), min = panControl.getMinimum();
        panControl.setValue((f * (max - min)) + min);
    }

    
    private class SlotcarSoundEffectsThread extends Thread {

        public SlotcarSoundEffectsThread() {
            super("SlotcarSoundEffectsThread");
        }

        @Override
        public void run() {
            while (true) {
                synchronized (this) {
                    try {
//                        System.out.println("waiting on caller thread");
                        wait();
                    } catch (InterruptedException ex) {
                        log.info("Interrupted");
                        break;
                    }
                    if (line == null) {
                        continue;
                    }
//                    System.out.println("filling line");
                    try {
                        // flush any sound not yet played out.
                        line.flush();

                        // reset ourselves to start of input data, to reread the spike data samples
                        audioInputStream.reset();
                        int avail = audioInputStream.available();
                        while (avail > 0) {
                            // read the data to write
                            int nRead = audioInputStream.read(abData);

                            // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
                            int nWritten = line.write(abData, 0, nRead);
                            avail = audioInputStream.available();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    static class SSTest extends JFrame {

        SlotCarSoundSample ss = null;
        float pan = 0.5f, vol = 1f;

        public SSTest() throws HeadlessException {
            super("SSTest");
            try {
                Random r = new Random();
                ss = new SlotCarSoundSample("finalLap.wav");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(200, 200);  // We use window width as volume control
            addKeyListener(new KeyAdapter() {

                @Override
                public void keyPressed(KeyEvent e) {
                    if ((e.getKeyCode() == KeyEvent.VK_X) || (e.getKeyCode() == KeyEvent.VK_ESCAPE)) {
                        ss.close();
                        dispose();
                        System.exit(0);
                    }
                    ss.play();
                }

                @Override
                public void keyReleased(KeyEvent e) {
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {

                @Override
                public void mouseMoved(MouseEvent e) {
                    pan = (float) e.getX() / getWidth();
                    vol = 1 - ((float) e.getY() / getHeight()); // java y increases downwards
                    ss.setPan(pan);
                    ss.setVolume(vol);
                }
            });
        }
    }

    public static void main(String[] args) {
        SSTest ssTest;
        ssTest = new SSTest();
        ssTest.setVisible(true);
    }
} // SpikeSound

