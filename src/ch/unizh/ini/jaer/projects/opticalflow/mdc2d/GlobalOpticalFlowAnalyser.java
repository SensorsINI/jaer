/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich
*/


package ch.unizh.ini.jaer.projects.opticalflow.mdc2d;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.dsPIC33F_COM_OpticalFlowHardwareInterface;

import com.phidgets.SpatialEventData;

/**
 * When streaming frames containing global motion vectors, use this class
 * to save the vectors in RAM and some of the frame data and to write it subsequently
 * to the disk in <code>.csv</code> format for further analysis.
 * <br /><br />
 * 
 * see <a href="http://n.ethz.ch/~andstein/MDC2Dsrinivasan.pdf">http://n.ethz.ch/~andstein/MDC2Dsrinivasan.pdf</a> 
 * for some data recorded using this class
 * <br /><br />
 * 
 * the data is only stored in memory and has to be written to the disk by
 * calling <code>storeData</code>, once the analysis is finished
 * 
 * 
The class "GlobalOpticalFlowAnalyser"  will write all motion data
(global flow x/y as well as some of the raw frame data) to a directory
as used by Andi Steiner's python scripts (basically a text file
containing recording settings such as channel fps etc and a csv file
containing the data). You can directly call the methods
startAnalysis() and stopAnalysis() on the hardware interface
dsPIC33F_COM_OpticalFlowHardwareInterface. the directories will be
created in the current working directory unless this is changed in the
source of GlobalOpticalFlowAnalyser [1]. note that no new thread is
created for writing the data to disk. the parameter 'saveRate' was
mainly used for debugging (calculating global flow on the computer and
compare it to the firmware output) and can only be used if the raw
data is  streamed (unchecked checkbox "pixels" in the GUI).

* 
 * 
 * @author andstein
 */
public class GlobalOpticalFlowAnalyser {
    
    static final Logger log=Logger.getLogger(GlobalOpticalFlowAnalyser.class.getName());
    /** two consecutive frames out of <code>frameSaveRate</code> will be entirely
        saved to disk */
    protected int frameSaveRate= 100;

    /** directory where all data will be stored */
    public static final String storagePath= "."; // startup folder
    
    protected String dirName;
    protected boolean dataStored= false;
    protected boolean storingData= false;
    
    // files that will be created in data directory
    public static final String motionLogFileName= "motionLog.csv";
    public static final String infoFileName= "info.txt";
    
    // contains all (valid) motions calculated
    protected String hardwareConfiguration;
    protected ArrayList<FrameMotion> motions;
    protected ArrayList<FramePicture> pictures;
    
    // containes all information gathered during data analysing; will be
    // saved to disk at the end
    protected StringBuilder loggedInfo;
    
    protected ArrayList<Integer> erroneousCalculations;
    protected int bogusFrames;
    protected int streamingErrors;
 
    
    /**
     * logs a string to the <code>infoFileName</code>; the message only gets
     * written to the disk when <code>storeData()</code> is called !
     * @param msg to write; will be combined with number of <i>last</i> frame
     * @see #storeData
     */
    protected void logInfo(String msg) {
        if (motions.isEmpty())
            loggedInfo.append(msg + "\n");
        else
            loggedInfo.append("after #"+getCurrentSeq()+" : " + msg + "\n");
    }
    

    /**
     * creates a new instance to analyze data
     * @param dirName where all data will be stored (relative 
     *      to <code>storagePath</code>)
     * @param frameSaveRate 
     * @see #storagePath
     * @see #frameSaveRate
     */
    public GlobalOpticalFlowAnalyser(dsPIC33F_COM_OpticalFlowHardwareInterface hardwareInterface,String dirName,int frameSaveRate) {
        this(hardwareInterface, dirName);
        this.frameSaveRate= frameSaveRate;
    }

    /**
     * @see #GlobalOpticalFlowAnalyser
     * @param dirName 
     */
    public GlobalOpticalFlowAnalyser(dsPIC33F_COM_OpticalFlowHardwareInterface hardwareInterface,String dirName)
    {
        loggedInfo= new StringBuilder();
        
        hardwareConfiguration= hardwareInterface.dump();
        motions= new ArrayList<FrameMotion>();
        pictures= new ArrayList<FramePicture>(); 
        erroneousCalculations= new ArrayList<Integer>();
        bogusFrames= 0;
        streamingErrors= 0;
        
        this.dirName= dirName;
        
        log.info("starting analyzing global flow ("+dirName+")");
    }
    
    /**
     * stores data gathered previously to disk; depending on the amount of
     * data gathered, this might take some time.
     * <br /><br />
     * 
     * @throws IOException 
     */
    public void storeData() throws IOException {
        
        storingData= true;
        
        File storageDir= new File(storagePath);
        File destinationDirectory;
        
        if (!storageDir.isDirectory()) 
            throw new IOException(storagePath + " folder does not exist or is not a directory");
        else {
            int i=0;
            do {
                i+=1;
                destinationDirectory= new File(storageDir.getPath() + File.separator + dirName + "_" + i);
            } while(destinationDirectory.exists());

            destinationDirectory.mkdir();
            log.info("writing memory logged data to folder " + destinationDirectory.getAbsolutePath()+ " from startup folder "+System.getProperties().getProperty("user.dir"));
       }

        // generate general info, including lost frames etc
        BufferedWriter infoWriter= new BufferedWriter(new FileWriter(destinationDirectory.getPath() + File.separator + infoFileName));
        DateFormat fmt= new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        Date now= new Date();
        infoWriter.write("stored on " + fmt.format(now) + "\n\n");
        
        infoWriter.write(hardwareConfiguration+"\n\n");
        
        infoWriter.write(erroneousCalculations.size() + " erroneousCalculations : ");
        for(Integer i : erroneousCalculations) 
            infoWriter.write(String.format("0x%02X ",i.intValue()));
        infoWriter.write("\n");

        infoWriter.write(bogusFrames + " bogusFrames\n");
                
        infoWriter.write("in total " + motions.size() + " frames were analysed.\n");
        infoWriter.write(loggedInfo.toString());
        infoWriter.close();

        // store motions as .csv file
        BufferedWriter motionLogWriter= new BufferedWriter(new FileWriter(destinationDirectory.getPath() + File.separator + motionLogFileName));
        motionLogWriter.write(FrameMotion.HEADER_STRING+"\n");        
        for(FrameMotion fm : motions)
            motionLogWriter.write(fm + "\n");
        motionLogWriter.close();
            
        // store captured frames
        for(FramePicture fp : pictures)
            fp.writeToDirectory(destinationDirectory);
        
        storingData= false;
    }
    
    /**
     * does exactly the same as <code>storeData()</code> but does not throw an i/o exception
     * (just complains in log if this happens)
     * @see #storeData
     */
    public void finish() {
        try {
            if (!dataStored) {
                storeData();
                dataStored= true;
            }
        } catch (IOException ex) {
            log.warning("could not save data : " + ex);
        }
    }
    
    @Override
    protected void finalize() throws Throwable
    {
        // make sure data is written
        finish();
        super.finalize();
    }
    
    protected int getCurrentSeq() {
        return motions.get(motions.size()-1).getSeq();
    }
    
    protected int getFirstSeq() {
        return motions.get(0).getSeq();
    }
    
    protected int getRelativeSeq() {
        return getCurrentSeq() - getFirstSeq();
    }
    
    /**
     * increments counter of streaming errors
     */
    public void incrementStreamingErrors() { 
        logInfo("lost message");
        streamingErrors++; 
    }
    /**
     * increments bogus frame counter
     * @param frame will be saved to disk, together with the previous frame
     */
    public void addBogusFrame(MotionData frame) { 
        if (storingData) return;
        logInfo("got bogus frame");
        pictures.add(new FramePicture(frame));
        bogusFrames++; 
    }
    /**
     * increments erroneous calculation counter; also stores frame data that
     * lead to this error
     * 
     * @param errorCode as returned by the firmware
     * @param frame will be saved to disk, together with the preceeding frame
     */
    public void addErroneousCalculations(int errorCode,MotionData frame) { 
        if (storingData) return;
        logInfo("erroneous calculation code="+errorCode+" seq="+frame.getSequenceNumber());
        erroneousCalculations.add(new Integer(errorCode)); 
        pictures.add(new FramePicture(frame));
    }
    /**
     * increments counter of lost messages
     * 
     * @param seq sequence of message that was lost
     */
    public void addLostMessage(int seq) { 
        if (storingData) return;
        logInfo("lost message : seq="+seq);
    }   

    /**
     * adds data to be analyzed; just copies relevant data into internal
     * structures and returns (should not take too long); the global vectors,
     * <code>dt</code> and <code>seq</code> are stored in any case; the frame
     * data is stored depending on <code>frameSaveRate</code> and whenever
     * the two motion vectors (from firmware vs computer) are "too different"
     * 
     * @param frame 
     * @see #frameSaveRate
     */
    public void analyseMotionData(MotionData frame)
    {
        if (storingData) return;
        FrameMotion fm= new FrameMotion(frame);
        motions.add(fm);

        // save frame data for this & precedent frame for every XXX-th specified
        // or if an error is detected
        if (getRelativeSeq() % frameSaveRate == 0 && frame.hasPhoto())
            //|| fm.getLengthDifference() > 0.1)
            pictures.add(new FramePicture(frame));
    }



    /**
     * this class contains the global motion vector data, as well as the
     * sequence number and delay (in ms) between two frames
     */
    public static class FrameMotion
    {
        protected float dx,dy,dx2,dy2,px,py;
        protected long dt;
        protected int  seq;
        
        public FrameMotion(MotionData frame) {
            dt=frame.getTimeCapturedMs()-frame.getPastMotionData()[0].getTimeCapturedMs();
            
            // we want the raw values...
            dx= frame.getGlobalX();
            dy= frame.getGlobalY();
            dx2= frame.getGlobalX2();
            dy2= frame.getGlobalY2();
            SpatialEventData phidgetData= frame.getPhidgetData();
            if (phidgetData==null) {
                px= Float.NaN;
                py= Float.NaN;
            } else {
                px= (float) phidgetData.getAngularRate()[0];
                py= (float) phidgetData.getAngularRate()[1];
            }
            seq=frame.getSequenceNumber();
        }

        /** field names; should be first line in <code>.csv</code> file */
        public static String HEADER_STRING="dx,dy,dx2,dy2,px,py,dt,seq";
        
        protected float sanitize(float x) {
            if (Float.isInfinite(x) || Float.isNaN(x))
                return 0;
            return x;
        }
        
        /**
         * call this to store data to disk in <code>.csv</code> format
         * @see #HEADER_STRING
         * @return one row of data
         */
        @Override
        public String toString() {
            return sanitize(dx) + "," + sanitize(dy) + "," + 
                   sanitize(dx2) + "," + sanitize(dy2) + "," + 
                   sanitize(px) + "," + sanitize(py)+ "," + 
                   dt + "," + seq;
        }
        
        public boolean sameSigns() {
            return (dx>0?dx2>0:dx2<=0) &&
                   (dy>0?dy2>0:dy2<=0);
        }
        
        public double getAngleDifference() {
            return Math.atan2(dy, dx) - Math.atan2(dy2,dx2);
        }
        
        public double getLengthDifference() {
            return Math.abs(Math.sqrt(dy2*dy2+dx2*dx2) - Math.sqrt(dx*dx+dy*dy));
        }

        public double getLengthRatio() {
            return Math.sqrt(dy2*dy2+dx2*dx2) / Math.sqrt(dx*dx+dy*dy);
        }

        public int getSeq() {
            return seq;
        }
        
    }
    
    /**
     * this class is used to store frame pixel data of a given frame as well
     * as its predecessor; the pixel data is actually copied and will not be
     * changed if the pixel data is subsequently altered in the frame objects;
     * the data is then written to individual files whose name depends on the
     * sequence number.
     */
    public class FramePicture 
    {
        
        protected int seq;
        protected float[][] pos1;
        protected float[][] pos2;

        /**
         * copies the pixel data from the provided frame and its predecessor
         * @param frame 
         */
        public FramePicture(MotionData frame)
        {
            float[][] pixels1= frame.getLastMotionData().getRawDataPixel()[0];
            float[][] pixels2= frame.getRawDataPixel()[0];
            seq= frame.getSequenceNumber();
            
            pos1= new float[pixels1.length][pixels1[0].length];
            pos2= new float[pixels2.length][pixels2[0].length];
            for(int y=0; y<pixels1.length; y++)
                System.arraycopy(pixels1[y], 0, pos1[y], 0, pixels1.length);
            for(int y=0; y<pixels2.length; y++)
                System.arraycopy(pixels2[y], 0, pos2[y], 0, pixels2.length);
            
        }
        
        /**
         * generates two new files (headerless <code>.csv</code>) in the destination 
         * directory containing the pixel data
         * @param dstDir the destination directory
         * @throws IOException
         */
        public void writeToDirectory(File dstDir)
                throws IOException
        {
            BufferedWriter bw;
            
            bw= new BufferedWriter(new FileWriter(dstDir.getPath() + File.separator + "frame" + (seq-1) + ".csv"));
            for(int y=0; y<pos1.length; y++) {
                for(int x=0; x<pos1[y].length; x++)
                    bw.write(Float.toString(pos1[y][x]) + ",");
                bw.write("\n");
            }
            bw.close();

            bw= new BufferedWriter(new FileWriter(dstDir.getPath() + File.separator + "frame" + (seq  ) + ".csv"));
            for(int y=0; y<pos1.length; y++) {
                bw.write(Float.toString(pos2[y][0]));
                for(int x=1; x<pos2[y].length; x++)
                    bw.write(","+Float.toString(pos2[y][x]));
                bw.write("\n");
            }
            bw.close();
        }
        
    }

    @Override
    public String toString() {
        return "GlobalOpticalFlowAnalyser dirName="+dirName+" storagePath="+storagePath+" bogusFrames="+bogusFrames+" streamingErrors="+streamingErrors;
    }
    
    
}
