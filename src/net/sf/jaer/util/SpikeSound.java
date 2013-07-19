package net.sf.jaer.util;
import java.awt.HeadlessException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;
/**
 * Plays a spike sound on the speaker. Use it by constructing a new {@link SpikeSound}, then calling the {@link #play} method.
 *
 * @author  tobi
 * @version $Revision: 1.8 $
 */
public class SpikeSound{
    static final byte[] ough = { -1,-1,-1,-1,0,-1,-1,-1,0,-1,-1,-1,0,-1,-1,-1,-1,-1,-1,0,0,-1,0,-1,-1,0,0,0,-1,0,-1,0,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,0,0,0,1,1,2,2,2,1,0,-2,-4,-5,-5,-3,-2,1,2,2,2,2,2,3,2,0,-2,-4,-5,-5,-4,-2,0,3,5,6,5,2,-2,-5,-7,-7,-6,-5,-4,-3,-1,1,3,3,-1,-5,-9,-11,-11,-11,-8,-7,-1,14,34,51,78,77,48,22,-29,-71,-80,-79,-64,-48,-33,-19,-5,18,53,63,68,63,32,1,-17,-33,-38,-45,-44,-42,-45,-28,-9,13,39,51,47,33,14,8,1,2,18,28,34,37,22,-3,-26,-44,-56,-50,-47,-35,-27,-24,-1,4,22,41,45,41,30,9,-12,-18,-28,-27,-24,-26,-24,-24,-22,-3,-1,16,25,16,20,10,5,12,25,47,65,68,52,6,-44,-71,-85,-76,-61,-48,-39,-33,-13,11,27,59,73,63,54,25,1,-21,-21,-26,-30,-32,-39,-41,-42,-27,-3,7,20,38,21,30,28,45,75,74,95,71,11,-21,-74,-102,-111,-96,-76,-70,-42,-35,2,32,63,92,86,76,54,27,-5,-28,-33,-43,-53,-51,-64,-68,-58,-28,0,26,40,50,47,49,62,67,79,89,85,58,-13,-60,-91,-127,-122,-102,-86,-67,-41,-5,7,44,85,102,100,83,65,18,-5,-22,-43,-55,-61,-60,-73,-75,-52,-27,-7,34,62,62,66,64,74,64,76,84,74,60,13,-47,-107,-127,-127,-123,-103,-79,-51,-24,16,40,76,115,116,109,84,41,4,-21,-36,-57,-62,-68,-80,-90,-79,-47,-24,17,56,62,70,62,62,65,62,88,84,86,73,18,-53,-109,-127,-127,-127,-116,-95,-64,-34,20,43,79,126,126,126,111,61,18,-16,-40,-57,-80,-86,-109,-114,-103,-77,-32,4,69,84,98,84,69,82,69,87,104,97,87,45,-52,-104,-127,-127,-127,-127,-122,-78,-50,-5,41,68,126,126,126,123,77,28,-8,-33,-39,-55,-90,-111,-124,-127,-104,-67,-15,45,57,74,69,64,78,91,94,108,105,96,84,42,-35,-67,-127,-127,-127,-127,-127,-101,-57,-18,35,68,126,126,126,126,106,60,38,-1,-23,-54,-85,-104,-127,-127,-127,-110,-64,-17,15,63,82,100,117,126,120,126,126,119,95,76,38,-64,-127,-127,-127,-127,-127,-127,-114,-64,18,65,87,126,126,126,126,126,81,17,-21,-48,-80,-127,-127,-127,-127,-127,-109,-94,-44,25,83,116,126,126,126,116,78,87,72,70,85,90,45,-20,-104,-127,-127,-127,-127,-127,-82,-50,5,38,73,126,126,126,126,119,68,8,-25,-52,-71,-93,-102,-116,-127,-127,-111,-89,-62,14,66,101,119,126,126,98,85,74,47,41,48,57,64,52,9,-72,-127,-127,-127,-127,-112,-55,-12,0,37,51,76,95,106,95,78,50,0,-40,-62,-68,-61,-60,-61,-62,-80,-79,-63,-51,-10,35,72,86,100,90,69,57,29,29,28,30,59,91,100,79,18,-36,-108,-127,-127,-127,-108,-66,-44,-28,-7,4,23,69,82,87,99,52,22,-7,-20,-18,-15,-15,-25,-40,-85,-95,-90,-86,-32,12,38,64,70,70,53,43,41,29,38,40,73,91,106,117,78,0,-77,-110,-127,-127,-127,-124,-87,-62,-30,-15,-20,35,86,96,116,116,83,52,29,15,3,-13,-44,-66,-115,-127,-123,-102,-58,-20,41,72,77,84,87,79,66,68,63,33,37,47,57,87,86,43,-11,-95,-127,-127,-127,-127,-81,-26,-2,17,6,21,45,79,107,118,110,59,8,-23,-32,-40,-41,-39,-70,-82,-100,-93,-62,-33,16,54,70,73,65,53,43,43,34,27,15,8,19,37,60,83,96,64,-4,-43,-98,-127,-116,-96,-56,-52,-50,-55,-75,-73,-26,15,69,104,126,103,76,45,32,22,18,7,-21,-68,-100,-127,-127,-104,-71,-5,33,55,57,46,42,42,46,66,67,68,61,54,38,44,53,71,64,10,-25,-81,-127,-127,-127,-112,-84,-64,-61,-62,-53,-22,41,72,116,126,126,112,77,50,32,19,-18,-60,-107,-127,-127,-127,-124,-85,-24,31,61,73,81,89,90,93,98,89,56,24,2,-22,-29,-12,9,33,48,52,32,-17,-52,-71,-90,-88,-68,-58,-63,-55,-34,-18,6,35,72,84,84,86,65,27,10,2,-28,-57,-78,-85,-104,-108,-71,-54,-30,7,38,47,51,70,78,74,61,40,14,-18,-40,-36,-28,-6,10,28,39,44,55,75,76,58,44,6,-41,-77,-88,-95,-86,-68,-49,-33,-29,-12,-1,14,34,53,59,54,33,6,-10,-35,-48,-39,-39,-35,-33,-31,-30,-28,-14,9,26,33,43,42,25,17,7,-1,-11,-15,-9,-4,1,11,35,52,70,81,90,98,94,88,53,9,-40,-79,-120,-127,-127,-121,-98,-76,-58,-49,-41,-10,21,51,76,95,93,69,49,19,-13,-39,-44,-52,-64,-69,-70,-66,-53,-28,-8,23,43,52,59,59,49,41,34,22,13,6,4,5,5,16,25,30,38,46,47,50,52,46,45,37,3,-35,-66,-90,-109,-108,-92,-77,-58,-43,-30,-27,-24,-4,15,24,38,42,36,29,22,15,12,9,4,2,-12,-16,-18,-20,-17,-13,-9,-8,-5,-6,-1,7,5,11,12,7,14,20,26,35,40,44,40,33,21,8,-1,-7,-7,-7,-3,5,5,13,18,23,26,9,-5,-29,-60,-73,-73,-68,-56,-37,-30,-25,-19,-20,-9,3,15,40,50,47,41,28,9,2,-5,-13,-18,-20,-25,-21,-18,-14,-1,3,4,8,6,9,15,19,25,29,25,22,19,17,11,6,0,-1,-8,-10,-11,-17,-23,-23,-23,-20,-13,-2,6,18,22,30,34,36,42,41,38,21,-4,-34,-54,-67,-74,-67,-60,-51,-39,-36,-30,-12,6,30,43,58,57,47,37,22,14,9,7,8,-8,-18,-24,-28,-21,-8,6,13,11,10,4,3,4,8,7,0,-9,-20,-27,-24,-18,-1,10,14,19,15,9,8,10,13,11,10,-3,-16,-24,-26,-20,-7,2,6,4,-5,-8,-6,3,11,13,12,7,-11,-17,-12,-11,-7,6,13,13,11,14,16,16,17,20,12,8,2,-2,-8,-12,-14,-18,-23,-21,-18,-19,-14,-9,-3,1,1,1,5,5,11,12,8,8,3,-2,-6,-10,-20,-25,-26,-31,-31,-24,-24,-19,-15,-9,-4,10,14,23,24,26,27,28,27,29,27,24,23,14,3,1,-1,-1,4,6,8,9,4,5,3,1,6,6,6,8,-2,-8,-12,-13,-10,-11,-17,-22,-32,-32,-34,-34,-36,-32,-20,-13,-2,7,9,11,13,15,17,17,16,9,3,-1,-3,-1,-6,-5,-2,-1,10,13,13,19,18,21,22,16,13,9,4,4,-4,-9,-6,-2,1,4,2,1,-6,-3,-4,-10,-8,-13,-12,-13,-22,-24,-25,-29,-23,-22,-21,-24,-15,-10,2,5,9,22,17,17,12,0,-5,-7,1,15,15,17,14,7,10,11,17,17,20,13,9,8,4,7,6,4,-3,-1,-6,-9,-12,-13,-6,-7,-3,0,-7,-11,-11,-10,-6,-10,0,-2,-1,-4,-10,-8,-8,-8,-1,-2,0,-7,-2,-4,-8,-3,-6,-2,6,0,8,2,-2,16,20,23,29,26,20,14,10,11,-1,-6,-4,-12,-15,-6,-1,0,5,7,-2,0,0,-8,-11,-3,-11,-15,-14,-20,-21,-17,-27,-19,-12,-9,-4,-1,-4,3,-2,-2,10,0,3,6,-1,-4,1,-4,2,14,11,10,9,5,10,4,2,10,7,13,1,3,-4,0,10,14,15,12,17,12,15,13,4,2,-4,-8,-11,-14,-12,-18,-27,-19,-22,-19,-16,-11,-13,-16,-11,-11,-6,-2,1,3,-1,6,10,0,11,6,-1,8,0,-3,8,-3,0,12,-1,4,8,10,8,4,2,6,5,5,20,9,1,6,5,2,2,2,4,-5,-6,-6,-16,-11,-12,-9,-10,-15,-11,-7,-14,-6,-2,-8,-1,9,2,10,-2,-3,-2,-8,-6,-2,-5,3,-5,-13,-3,-7,4,13,16,7,19,12,11,21,14,5,17,1,0,3,-11,3,-14,-12,-3,-20,-7,-9,-11,-4,-5,0,-4,-6,-2,-12,-11,-5,-12,-1,-2,-11,-3,-11,-13,-5,-4,2,8,5,3,9,7,1,13,6,18,11,1,14,12,5,9,3,5,-3,-2,4,-8,-9,0,-5,-3,1,7,5,1,2,0,-8,-8,3,-1,-8,5,2,0,-2,-2,-5,-9,-12,-4,-10,-15,-9,-15,-20,-13,-19,-8,-7,-2,10,2,6,14,13,14,10,9,17,14,13,15,11,9,7,7,1,-1,-4,-3,-7,-10,-11,-10,-11,-10,3,2,5,8,8,3,2,-6,-3,-1,-4,2,0,-6,0,-4,-6,-4,-3,-3,-1,-5,-7,-7,-9,-7,-7,-2,-6,2,4,-1,1,2,1,5,-1,-1,-2,-4,-6,-4,-3,-3,4,7,7,11,5,7,2,3,7,5,0,1,-1,0,-2,-3,-4,-3,-7,-4,-7,1,5,4,10,7,9,-1,1,-5,-10,-4,-10,-6,-7,-12,-10,-8,-10,-1,2,0,4,8,4,5,8,4,1,4,3,3,-1,2,7,0,1,4,0,1,3,6,0,-3,2,0,-3,-4,-4,2,-2,-2,1,-1,0,-5,0,-1,-3,-3,-6,-2,1,-3,-3,-7,-14,-6,-5,-7,-4,-8,-3,-8,-5,-2,-4,-2,1,1,3,3,1,1,7,9,8,16,11,7,4,1,4,-2,1,8,2,-2,-3,-2,0,-9,-7,1,-6,-3,4,1,4,2,3,6,-1,2,3,-3,-5,-4,-10,-9,-8,-7,-8,-5,-7,2,0,0,3,0,0,2,4,2,1,-1,0,-3,-1,-3,-6,0,5,3,5,5,8,7,6,7,3,-1,-1,-6,-7,-9,-8,-6,-2,-3,-5,-2,0,4,4,-1,3,4,0,3,-2,-2,0,-4,-4,-7,-7,-8,-7,-5,-5,0,-1,2,1,-2,4,3,4,0,2,1,0,4,1,1,1,1,4,4,4,1,1,-1,1,3,0,4,1,-1,0,-1,-1,0,-1,2,-2,-3,0,-3,-4,-1,-2,-2,-1,-1,-1,-1,1,-5,-2,-6,-5,-2,-8,-4,-5,-7,-2,-2,-3,0,2,3,6,6,6,6,6,5,7,3,3,4,-1,4,1,-2,0,-1,-3,-4,-3,-1,-1,0,3,3,2,5,3,1,0,-4,-6,-7,-7,-4,-5,-6,-3,-3,-5,-1,-3,0,1,2,1,-2,-2,1,-4,-1,-3,-5,-4,-3,-2,0,-1,5,3,0,5,4,3,7,2,7,3,3,2,1,-2,-3,0,-2,1,2,-1,-1,-5,-4,-5,-7,-5,-5,-4,-5,-2,-5,1,0,-2,0,1,2,4,0,0,-3,-3,-1,0,-1,3,1,-1,-1,0,-1,-1,-1,1,0,0,5,4,3,3,2,1,0,0,4,0,1,0,-2,-2,-6,-3,-3,-1,0,-3,-2,-5,-3,-3,-3,-2,-4,-3,1,-1,1,0,-2,-2,-3,-1,-1,-2,-3,-2,-2,-1,0,-1,0,2,3,3,3,2,6,2,4,3,2,2,0,0,1,-1,-1,-2,-1,0,-1,-2,1,-2,1,1,0,1,-1,0,2,0,2,3,-1,0,0,-2,1,-2,-6,-2,-3,-3,-2,-3,-3,-2,-4,-2,1,-2,0,0,0,1,2,1,1,0,0,-4,-2,-4,-3,-1,-2,1,0,0,0,2,1,-1,1,-3,-2,0,-2,-1,0,0,1,3,2,0,0,-1,1,-4,-5,-1,-4,1,-2,-2,-3,-4,2,1,2,2,2,2,3,2,3,1,-1,-1,-4,-4,-3,-4,-3,-5,-3,-3,-2,2,2,2,2,1,1,1,1,1,0,-1,-2,-1,-2,-3,0,0,-1,0,-3,0,-1,-1,0,-2,0,1,1,0,1,1,2,1,-1,-3,-3,-4,-3,-3,0,1,0,1,2,1,3,3,2,2,1,-1,0,-2,1,0,-4,0,-2,-3,-1,-3,-3,-2,-2,-1,-2,-1,0,1,2,3,1,2,3,2,0,-1,-2,-3,-1,-2,-3,-3,-2,-1,-1,-1,0,-1,0,-1,1,-1,-1,1,0,0,-1,-3,-2,-3,-2,-1,-2,-2,-1,-1,-1,0,0,0,-1,-1,-1,0,1,2,2,-1,0,-1,-1,0,-2,-1,-1,-2,-1,0,-1,1,0,0,0,0,0,0,-1,0,-1,-1,1,-1,1,1,1,1,1,0,0,-2,1,1,0,-1,-2,-2,-2,-1,-2,-3,-4,-2,-2,0,3,2,1,0,0,-1,0,0,1,-1,0,0,1,0,0,2,0,0,-1,-2,-1,0,0,-1,-2,-1,1,1,1 };
    enum SoundShape{
        SQUARE, ROUNDED, OUGH
    };
    SoundShape shape = SoundShape.ROUNDED;
    /** duration of spike sound in ms */
    public static final int SPIKE_DURATION_MS = 2;
    /** sample rate in Hz */
    public static final float SAMPLE_RATE = 8000f; // 44100; // tobi changed to try on linux since 8000f doesn't seem to work 8000f;
    /** length of spike sound sample in samples.  Duration of spike sound is BUFFER_LENGTH/{@link #SAMPLE_RATE}.
     * e.g., with a sample rate of 4kHz, 8 sample spikes make the spikes 2ms long.  This seems about right.
     */
    public static int SPIKE_BUFFER_LENGTH = Math.round(SAMPLE_RATE * SPIKE_DURATION_MS / 1000);
    /** amplitude of spike sound.  */
    public static final byte SPIKE_AMPLITUDE = 127;
    public static AudioFormat audioFormat = null;
    AudioInputStream spike = null, spikeLeft = null, spikeRight = null;
    private byte[] spikeSoundSamples, spikeSoundSamplesLeft, spikeSoundSamplesRight;
//    private AudioInputStream spikeStream, spikeStreamLeft, spikeStreamRight;
    private SourceDataLine line = null;
    static Logger log = Logger.getLogger("SpikeSound");

    /** Creates a new instance of SpikeSound */
    public SpikeSound (){
        // define format of sound
        audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,SAMPLE_RATE,8,2,2,SAMPLE_RATE,false);

        // spike sound defined here by the waveform contructed here
        spikeSoundSamples = new byte[ SPIKE_BUFFER_LENGTH ];
        spikeSoundSamplesLeft = new byte[ SPIKE_BUFFER_LENGTH ];
        spikeSoundSamplesRight = new byte[ SPIKE_BUFFER_LENGTH ];

        // even samples are right channel, odd samples are left channel (by expt)

        switch ( shape ){
            case SQUARE:
                for ( int i = 0 ; i < spikeSoundSamples.length / 2 - 1 ; i++ ){
                    spikeSoundSamples[i] = (byte)( SPIKE_AMPLITUDE );
                }
                for ( int i = spikeSoundSamples.length / 2 ; i < spikeSoundSamples.length ; i++ ){
                    spikeSoundSamples[i] = (byte)( -SPIKE_AMPLITUDE );
                }
                break;
            case ROUNDED: {
                int m = spikeSoundSamples.length / 2;
                for ( int i = 0 ; i < m - 1 ; i += 2 ){
                    double x = Math.PI * (double)( i - m / 2 ) / m * 2;
                    double y = Math.sin(x);
                    byte z = (byte)( ( SPIKE_AMPLITUDE ) * x );
                    spikeSoundSamples[i] = z;
                    spikeSoundSamples[i + 1] = z;
                    spikeSoundSamplesLeft[i + 1] = z;
                    spikeSoundSamplesRight[i] = z;
                }
            }
            break;
            case OUGH: {
                SPIKE_BUFFER_LENGTH = ough.length;
                spikeSoundSamples = new byte[ SPIKE_BUFFER_LENGTH ];
                spikeSoundSamplesLeft = new byte[ SPIKE_BUFFER_LENGTH ];
                spikeSoundSamplesRight = new byte[ SPIKE_BUFFER_LENGTH ];
                for ( int i = 0 ; i < spikeSoundSamples.length ; i++ ){
                    spikeSoundSamples[i] = ough[i];
                    spikeSoundSamplesLeft[i] = ough[i];
                    spikeSoundSamplesRight[i] = ough[i];
                }
            }
            break;
            default:
                throw new Error("no such sound shape " + shape);
        }


        // make an input stream from the sound sample bytes
        InputStream spikeStream = new ByteArrayInputStream(spikeSoundSamples);
        InputStream spikeStreamLeft = new ByteArrayInputStream(spikeSoundSamplesLeft);
        InputStream spikeStreamRight = new ByteArrayInputStream(spikeSoundSamplesRight);

        // make an audio input stream using the bytes and with with audio format
        spike = new AudioInputStream(spikeStream,audioFormat,SPIKE_BUFFER_LENGTH);
        spikeLeft = new AudioInputStream(spikeStreamLeft,audioFormat,SPIKE_BUFFER_LENGTH);
        spikeRight = new AudioInputStream(spikeStreamRight,audioFormat,SPIKE_BUFFER_LENGTH);

//        System.out.println("spike format="+spike.getFormat());
//        System.out.println("spike frame length="+spike.getFrameLength());
        // get info on possible SourceDataLine's
        // these are lines that you can source into
        DataLine.Info info = new DataLine.Info(SourceDataLine.class,audioFormat);
        try{
            // get a line from the system
            line = (SourceDataLine)AudioSystem.getLine(info);
//            line = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);
            //open it with our AudioFormat
            line.open(audioFormat);
            if ( spike.markSupported() ){
                spike.mark(SPIKE_BUFFER_LENGTH);
            }
//            System.out.println("line bufferSize="+line.getBufferSize());
//            System.out.println("line format="+line.getFormat());
//            System.out.println("line info="+line.getLineInfo());
            line.start();
        } catch ( LineUnavailableException e ){
            log.warning("Could not open sound output for playing spike sounds: " + e.toString());
            line = null;
        } catch ( Exception e ){
            log.warning("Could not open sound output for playing spike sounds: " + e.toString());
//            e.printStackTrace();
            line = null;
        }
    }
    // used for buffering in to out in play()...
    private byte[] abData = new byte[ SPIKE_BUFFER_LENGTH ];

    /** plays the spike sound once. Has a problem  that it seems to recirculate the sound forever... */
    public void play (){
        if ( line == null ){
            return;
        }
        try{
            // flush any spike sound not yet played out.
            line.flush();

            // reset ourselves to start of input data, to reread the spike data samples
            spike.reset();
            // read the data to write
            int nRead = spike.read(abData);

            // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
            int nWritten = line.write(abData,0,nRead);
        } catch ( IOException e ){
            e.printStackTrace();
        }
    }

    /** @param leftRight 0 to play left, 1 to play right */
    public void play (int leftRight){
        if ( line == null ){
            return;
        }
        try{
            // flush any spike sound not yet played out.
            line.flush();

            if ( leftRight % 2 == 0 ){// reset ourselves to start of input data, to reread the spike data samples
                spikeLeft.reset();
                // read the data to write
                int nRead = spikeLeft.read(abData);

                // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
                int nWritten = line.write(abData,0,nRead);
            } else{
                spikeRight.reset();
                // read the data to write
                int nRead = spikeRight.read(abData);

                // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
                int nWritten = line.write(abData,0,nRead);
            }
        } catch ( IOException e ){
            e.printStackTrace();
        }
    }
    static class SSTest extends JFrame{
        final SpikeSound ss = new SpikeSound();
        int side = 0;

        public SSTest () throws HeadlessException{
            super("SSTest");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(50,128);  // We use window width as volume control
            addKeyListener(new KeyAdapter(){
                public void keyPressed (KeyEvent e){
                    ss.play(side);
                }

                public void keyReleased (KeyEvent e){
                }
            });

            addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseMoved (MouseEvent e){
                    side = e.getX() > getWidth() / 2 ? 0 : 1;
                }
            });
        }
    }

    public static void main (String[] args){
        SSTest ssTest;
        ssTest=new SSTest();
        ssTest.setVisible(true);
    }
} // SpikeSound


