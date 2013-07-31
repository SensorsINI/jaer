package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;
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
import java.util.prefs.Preferences;

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
/**
 * Plays a sampled sound on the speaker. Use it by constructing a new {@link SampledSoundPlayer}, then calling the {@link #play} method.
 *
 * @author  tobi
 * @version $Revision: 1.8 $
 */
public class SampledSoundPlayer implements SoundPlayerInterface{
    private Preferences prefs=Preferences.userNodeForPackage(SampledSoundPlayer.class);
    private String filename;
    /** length of intermediate buffer in bytes */
    private int bufferLength = 0;
    private AudioFormat audioFormat = null;
    private AudioInputStream audioInputStream = null;
    private byte[] samples = null; // , spikeSoundSamplesLeft, spikeSoundSamplesRight;
//    private AudioInputStream spikeStream, spikeStreamLeft, spikeStreamRight;
    private SourceDataLine line = null;
    static Logger log = Logger.getLogger("SampledSoundPlayer");
    private SampledSoundPlayerThread T ;
    // used for buffering in to out in play()...
    private byte[] abData = null;
    private FloatControl panControl = null, volumeControl = null;
    private int drumNumber=0;
    private int soundNumber=0;

    /** Creates new SampledSoundPlayer labeled number i, using sampled sound stored as preference for this number.
     *
     * @param i an index, used to look up the preferred sound file.
     * @throws IOException
     * @throws LineUnavailableException
     * @throws UnsupportedAudioFileException
     */
    public SampledSoundPlayer(int i)throws IOException, UnsupportedAudioFileException, LineUnavailableException{
        drumNumber=i;
        soundNumber=prefs.getInt(prefsKey(),0);
        if(soundNumber>=SampledSoundPlayer.getSoundFilePaths().size()) {
			throw new IOException("There is no sound number "+soundNumber+" available");
		}
        setFile(soundNumber);
        open();
    }

    private String prefsKey (){
        return "SampledSoundPlayer.soundNumber." + drumNumber;
    }



    private void open() throws LineUnavailableException{
                // get info on possible SourceDataLine's
        // these are lines that you can source into
        DataLine.Info info = new DataLine.Info(SourceDataLine.class,audioFormat);
        // get a line from the system
        line = (SourceDataLine)AudioSystem.getLine(info);
//            line = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);
        //open it with our AudioFormat
        line.open(audioFormat);
        line.start();
        Control[] controls = line.getControls();
        StringBuilder sb = new StringBuilder("Line controls are: ");
        for ( Control c:controls ){
            sb.append(", " + c.toString());
        }
        log.info(sb.toString());
        if ( line.isControlSupported(FloatControl.Type.VOLUME) ){
            volumeControl = (FloatControl)line.getControl(FloatControl.Type.VOLUME);
        } else if ( line.isControlSupported(FloatControl.Type.MASTER_GAIN) ){
            volumeControl = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
        }
        if ( line.isControlSupported(FloatControl.Type.PAN) ){
            panControl = (FloatControl)line.getControl(FloatControl.Type.PAN);
        }
        T = new SampledSoundPlayerThread();
        T.start();
    }
    /** plays the spike sound once, by notifying the player thread to send the data to the line. */
    @Override
	synchronized public void play (){
        if ( T == null ){
            return;
        }
//        System.out.println("notifying player thread");
        synchronized ( T ){
            T.notify();
        }
    }

    @Override
	public void close (){
        if ( T == null ){
            return;
        }
        synchronized ( T ){
            T.interrupt();
        }
    }

    private static ArrayList<String> nameList=null;

    public static ArrayList<String> getSoundFilePaths (){
        if(nameList!=null) {
			return nameList;
		}
        nameList=new ArrayList<String>();
        try{
            String pathHeader = "ch/unizh/ini/jaer/projects/gesture/virtualdrummer/resources/";
            String srcHeader = "src/";
            String filename = "sounds.txt";
            InputStream inputStream;
            // load firmware file (this is binary file of 8051 firmware)
            inputStream = SampledSoundPlayer.class.getResourceAsStream(pathHeader+filename);
            if ( inputStream == null ){
                pathHeader=srcHeader+pathHeader;
                inputStream = new FileInputStream(pathHeader+filename);
            }
            BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream));
            String line=null;
            while((line=reader.readLine())!=null){
                if((line.length()==0) || line.startsWith("#")) {
					continue;
				}
                log.info("added \""+line+"\"");
                nameList.add(pathHeader+line);
            }


        } catch ( Exception e ){
            log.warning(e.toString());
        }
        return nameList;
    }

     /** Loads a binary sound file into memory. The filename is used to search the resource path (i.e. the jar archives on the classpath).
     * If the file is not found in the resources (jars or classes on classpath) then the file system is checked.
     *@param i the file number of the available files
     **/
   synchronized public boolean setFile (int i) throws UnsupportedAudioFileException,FileNotFoundException,IOException{
        if(i>=getSoundFilePaths().size()) {
			throw new FileNotFoundException("invalid file index");
		}
        soundNumber=i;
        this.filename = getSoundFilePaths().get(soundNumber);
        InputStream inputStream;
        // load firmware file (this is binary file of 8051 firmware)
        inputStream = getClass().getResourceAsStream(filename);
        if ( inputStream == null ){
            inputStream = new FileInputStream(filename);
        }
        audioInputStream = AudioSystem.getAudioInputStream(inputStream);
        audioFormat = audioInputStream.getFormat();
        if ( audioInputStream.markSupported() ){
            return true;
        }
        samples = new byte[ audioInputStream.available() ]; // hopefully we get entire
        bufferLength = audioInputStream.read(samples);
        abData = new byte[ bufferLength / 8 ];
        audioInputStream = new AudioInputStream(new ByteArrayInputStream(samples),audioFormat,bufferLength);
        prefs.putInt(prefsKey(),soundNumber);
        log.info("for drumNumber="+drumNumber+" loaded sound number "+soundNumber+" named " + filename + " with " + bufferLength + " samples at sample rate " + audioFormat.getSampleRate());
        return false;
    }

    public void setVolume (float f){
        if ( volumeControl == null ){
            return;
        }
        float max = volumeControl.getMaximum(), min = volumeControl.getMinimum();
        volumeControl.setValue((f * ( max - min )) + min);
    }

    public void setPan (float f){
        if ( panControl == null ){
            return;
        }
        float max = panControl.getMaximum(), min = panControl.getMinimum();
        panControl.setValue((f * ( max - min )) + min);
    }

    /**
     * @return the drumNumber
     */
    public int getDrumNumber (){
        return drumNumber;
    }

    /**
     * @return the soundNumber
     */
    public int getSoundNumber (){
        return soundNumber;
    }

    private class SampledSoundPlayerThread extends Thread{
        public SampledSoundPlayerThread (){
            super("SampledSoundPlayerThread");
        }

        @Override
        public void run (){
            while ( true ){
                synchronized ( this ){
                    try{
//                        System.out.println("waiting on caller thread");
                        wait();
                    } catch ( InterruptedException ex ){
                        log.info("Interrupted");
                        break;
                    }
                    if ( line == null ){
                        continue;
                    }
//                    System.out.println("filling line");
                    try{
                        // flush any sound not yet played out.
                        line.flush();

                        // reset ourselves to start of input data, to reread the spike data samples
                        audioInputStream.reset();
                        int avail=audioInputStream.available();
                        while(avail>0){
                        // read the data to write
                        int nRead = audioInputStream.read(abData);

                        // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
                        int nWritten = line.write(abData,0,nRead);
                        avail=audioInputStream.available();
                        }
                    } catch ( IOException e ){
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    static class SSTest extends JFrame{
        SampledSoundPlayer ss = null;
        float pan = 0.5f, vol = 1f;

        public SSTest () throws HeadlessException{
            super("SSTest");
            try{
                Random r=new Random();
                ss = new SampledSoundPlayer(r.nextInt(SampledSoundPlayer.getSoundFilePaths().size()));
            } catch ( Exception e ){
                e.printStackTrace();
                System.exit(1);
            }

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(200,200);  // We use window width as volume control
            addKeyListener(new KeyAdapter(){
                @Override
                public void keyPressed (KeyEvent e){
                    if ( (e.getKeyCode() == KeyEvent.VK_X) || (e.getKeyCode() == KeyEvent.VK_ESCAPE) ){
                        ss.close();
                        dispose();
                        System.exit(0);
                    }
                    ss.play();
                }

                @Override
                public void keyReleased (KeyEvent e){
                }
            });

            addMouseMotionListener(new MouseMotionAdapter(){
                @Override
                public void mouseMoved (MouseEvent e){
                    pan = (float)e.getX() / getWidth();
                    vol = 1 - ((float)e.getY() / getHeight()); // java y increases downwards
                    ss.setPan(pan);
                    ss.setVolume(vol);
                }
            });
        }
    }

    public static void main (String[] args){
        SSTest ssTest;
        ssTest = new SSTest();
        ssTest.setVisible(true);
    }
} // SpikeSound


