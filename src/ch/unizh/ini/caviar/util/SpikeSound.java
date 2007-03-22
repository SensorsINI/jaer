package ch.unizh.ini.caviar.util;

import	java.io.IOException;

import	javax.sound.sampled.AudioFormat;
import	javax.sound.sampled.AudioInputStream;
import	javax.sound.sampled.AudioSystem;
import	javax.sound.sampled.DataLine;
import	javax.sound.sampled.LineUnavailableException;
import	javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Plays a spike sound on the speaker. Use it by constructing a new {@link SpikeSound}, then calling the {@link #play} method.
 *
 * @author  tobi
 * @version $Revision: 1.8 $
 */

public class SpikeSound {
    
    enum SoundShape{ SQUARE, ROUNDED};
    
    SoundShape shape=SoundShape.ROUNDED;
    
    /** duration of spike sound in ms */
    public static final int SPIKE_DURATION_MS=2;
    
    /** sample rate in Hz */
    public static final float SAMPLE_RATE=8000f;
    
    /** length of spike sound sample in samples.  Duration of spike sound is BUFFER_LENGTH/{@link #SAMPLE_RATE}.
     * e.g., with a sample rate of 4kHz, 8 sample spikes make the spikes 2ms long.  This seems about right.
     */
    private static final int SPIKE_BUFFER_LENGTH=Math.round(SAMPLE_RATE*SPIKE_DURATION_MS/1000);
    
    /** amplitude of spike sound.  */
    public static final byte SPIKE_AMPLITUDE=127;
    
    AudioFormat	audioFormat=null;
    
    AudioInputStream spike=null, spikeLeft=null, spikeRight=null;
    
    private byte[] spikeSoundSamples, spikeSoundSamplesLeft, spikeSoundSamplesRight;
    
    private AudioInputStream spikeStream, spikeStreamLeft, spikeStreamRight;
    
    private SourceDataLine line=null;
    
    /** Creates a new instance of SpikeSound */
    public SpikeSound() {
        // define format of sound
        audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 8, 2, 2, SAMPLE_RATE, false);
        
        // spike sound defined here by the waveform contructed here
        spikeSoundSamples=new byte[SPIKE_BUFFER_LENGTH];
        spikeSoundSamplesLeft=new byte[SPIKE_BUFFER_LENGTH];
        spikeSoundSamplesRight=new byte[SPIKE_BUFFER_LENGTH];
        
        // even samples are right channel, odd samples are left channel (by expt)
        
        switch(shape){
            case SQUARE:
                for(int i=0;i<spikeSoundSamples.length/2-1;i++){
                    spikeSoundSamples[i]=(byte)(SPIKE_AMPLITUDE);
                }
                for(int i=spikeSoundSamples.length/2;i<spikeSoundSamples.length;i++){
                    spikeSoundSamples[i]=(byte)(-SPIKE_AMPLITUDE);
                }
                break;
            case ROUNDED:
            {
                int m=spikeSoundSamples.length/2;
                for(int i=0;i<m-1;i+=2){
                    double x=Math.PI*(double)(i-m/2)/m*2;
                    double y=Math.sin(x);
                    byte z=(byte)((SPIKE_AMPLITUDE)*x);
                    spikeSoundSamples[i]=z;
                    spikeSoundSamples[i+1]=z;
                    spikeSoundSamplesLeft[i+1]=z;
                    spikeSoundSamplesRight[i]=z;
                }
            }
            break;
            default:
                throw new Error("no such sound shape "+shape);
        }
        
        
        // make an input stream from the sound sample bytes
        InputStream spikeStream=new ByteArrayInputStream(spikeSoundSamples);
        InputStream spikeStreamLeft=new ByteArrayInputStream(spikeSoundSamplesLeft);
        InputStream spikeStreamRight=new ByteArrayInputStream(spikeSoundSamplesRight);
        
        // make an audio input stream using the bytes and with with audio format
        spike=new AudioInputStream(spikeStream,audioFormat,SPIKE_BUFFER_LENGTH);
        spikeLeft=new AudioInputStream(spikeStreamLeft,audioFormat,SPIKE_BUFFER_LENGTH);
        spikeRight=new AudioInputStream(spikeStreamRight,audioFormat,SPIKE_BUFFER_LENGTH);
        
//        System.out.println("spike format="+spike.getFormat());
//        System.out.println("spike frame length="+spike.getFrameLength());
        // get info on possible SourceDataLine's
        // these are lines that you can source into
        DataLine.Info	info = new DataLine.Info( SourceDataLine.class, audioFormat);
        try {
            // get a line from the system
            line = (SourceDataLine) AudioSystem.getLine(info);
//            line = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);
            //open it with our AudioFormat
            line.open(audioFormat);
            if(spike.markSupported()) spike.mark(SPIKE_BUFFER_LENGTH);
//            System.out.println("line bufferSize="+line.getBufferSize());
//            System.out.println("line format="+line.getFormat());
//            System.out.println("line info="+line.getLineInfo());
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            line=null;
        } catch (Exception e) {
            e.printStackTrace();
            line=null;
        }
    }
    
    // used for buffering in to out in play()...
    private  byte[] abData=new byte[SPIKE_BUFFER_LENGTH];
    
    /** plays the spike sound once. Has a problem  that it seems to recirculate the sound forever... */
    public void play(){
        if(line==null) return;
        try{
            // flush any spike sound not yet played out.
            line.flush();
            
            // reset ourselves to start of input data, to reread the spike data samples
            spike.reset();
            // read the data to write
            int	nRead = spike.read(abData);
            
            // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
            int	nWritten = line.write(abData, 0, nRead);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    /** @param leftRight 0 to play left, 1 to play right */
    public void play(int leftRight){
        if(line==null) return;
        try{
            // flush any spike sound not yet played out.
            line.flush();
            
            if(leftRight%2==0){// reset ourselves to start of input data, to reread the spike data samples
                spikeLeft.reset();
                // read the data to write
                int	nRead = spikeLeft.read(abData);
                
                // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
                int	nWritten = line.write(abData, 0, nRead);
            }else{
                spikeRight.reset();
                // read the data to write
                int	nRead = spikeRight.read(abData);
                
                // write out the data from the input stream (the spike samples) to the output stream (the SourceDataLine)
                int	nWritten = line.write(abData, 0, nRead);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args){
        new SpikeSound().play();
    }
} // SpikeSound


