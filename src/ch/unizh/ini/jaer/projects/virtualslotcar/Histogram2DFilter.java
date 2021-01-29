/*
 Created Capo Caccia 2014 to enable compter vs machine racing
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import javax.swing.JFileChooser;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Allows accumulating a histogram of active pixels and then filtering in or out all these x,y addresses from incoming event packets.
 *
 * @author tobi
 */
@Description("Does a kind of background modeling by allowing accumulating a histogram of active pixels, \n" +
" * and then filtering out the active pixels, or the inactive ones, depending on <i>invertFiltering</i>.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Histogram2DFilter extends EventFilter2D implements FrameAnnotater, Serializable {

    private static final long serialVersionUID = 8749822155491049760L; // tobi randomly defined
    private int[][] histogram = null;  // first dim is X, 2nd is Y
     private       boolean[][] bitmap = null;
    private boolean collect = false;
    private float threshold = getFloat("threshold", 20);
    private int histmax = 0;
    private static final String HISTOGRAM_FILE_NAME = "Histogram2DFilter.dat";
    private static final String HISTOGRAM_FILE_NAME_EXTENSION=".dat";
    private boolean showHistogram = getBoolean("showHistogram", true);
    private boolean showBitmap = getBoolean("showBitmap", true);
    private int numX=0, numY=0, numPix=0;
    private int erosionSize = getInt("erosionSize", 0);
    private int totalSum; // sum of histogram values
    private String filePath=getString("filePath",System.getProperty("user.dir")+File.separator+HISTOGRAM_FILE_NAME);
    protected boolean invertFiltering=getBoolean("invertFiltering", false);
 
    public Histogram2DFilter(AEChip chip) {
        super(chip);
         setPropertyTooltip("collect", "set true to accumulate histogram");
        setPropertyTooltip("threshold", "threshold in accumulated events to allow events to pass through");
        setPropertyTooltip("histmax", "maximum histogram count");
        setPropertyTooltip("showHistogram", "paints histogram ; blue for below threshold and yellow for above threshol");
        setPropertyTooltip("showBitmap", "paints bitmap as white pixels");
        setPropertyTooltip("clearHistogram", "clears histogram");
        setPropertyTooltip("freezeHistogram", "freezes current histogram");
        setPropertyTooltip("saveHistogram", "saves current histogram to the fixed filename " + HISTOGRAM_FILE_NAME);
        setPropertyTooltip("loadHistogram", "loads histogram from the fixed filename " + HISTOGRAM_FILE_NAME);
        setPropertyTooltip("collectHistogram", "turns on histogram accumulation");
        setPropertyTooltip("computeErodedBitmap", "computes the bitmap of valid pixels");
        setPropertyTooltip("clearBitmap", "clears the bitmap of valid pixels");
        setPropertyTooltip("erosionSize", "Amount in pixels to erode histogram bitmap on erode operation");
        setPropertyTooltip("invertFiltering", "Pass events from pixels that are NOT active during collection phase; used to filter out background noise");
        setPropertyTooltip("filePath", "The file path to this histogram mask; also the ID of the mask");
        String filePath=getString("filePath",null);
        if(filePath!=null) loadHistogramFromFile(new File(filePath)); // load last file by default; user can overwrite with some other file
   }

    
    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) { 
        checkOutputPacketEventType(in);
        checkHistogram();
        int max = getHistmax();
        int lastTimeStampInPacket=in.getLastTimestamp();
        int nOut=0;
        OutputEventIterator outItr=getOutputPacket().getOutputIterator();
        for (BasicEvent e : in) {
            if (e==null || e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            if(e.x>=numX || e.y>=numY || e.x<0 || e.y<0){ // ignore special events, e.g. with negative address
                continue;
            }
            if (isCollect()) {
                histogram[e.x][e.y]++;
                totalSum++;
                if (histogram[e.x][e.y] > max) {
                    max = histogram[e.x][e.y];
                }
            } else { // filter out events 
                if(bitmap==null) return in;
                if((!invertFiltering && bitmap[e.x][e.y]) || (invertFiltering && !bitmap[e.x][e.y])){
                    outItr.nextOutput().copyFrom(e);
                    nOut++;
                }
            }
        }
        if (isCollect()) {
            setHistmax(max);
        }
        if(nOut==0 && in.getSize()>0){  // ensure at least one event with address 0,0 in output packet to avoid trackers not being updated peridically
            BasicEvent e=outItr.nextOutput();
            e.setX((short)0); e.setY((short)0); e.setTimestamp(in.getLastTimestamp());
        }
        return isCollect()? in:getOutputPacket();
    }

    synchronized public void doClearHistogram() {
        if (histogram != null) {
            for (int i = 0; i < numX; i++) {
                Arrays.fill(histogram[i], 0);
            }
        }
        setHistmax(0);
        totalSum=0;
        bitmap=null;
        if(getFilePath()!=null){
            String s=getFilePath();
            int i=s.lastIndexOf(File.separator);
            s=s.substring(0, i);
            setFilePath(s);
        }
    }
    
    synchronized public void doCollectHistogram() {
        setCollect(true);
        setShowHistogram(true);
    }
    
    synchronized public void doComputeErodedBitmap(){
        computeErodedBitmap();
        setShowBitmap(true);
    }

    synchronized public void doFreezeHistogram() {
        setCollect(false);
        computeErodedBitmap();
    }

    synchronized public void doSaveHistogram() {
        if (histmax == 0 || histogram == null) {
            log.warning("no histogram to save");
            return;
        }
        try {
            JFileChooser fileChooser = new JFileChooser();
            String lastFilePath = getFilePath();
            if(!lastFilePath.endsWith(HISTOGRAM_FILE_NAME_EXTENSION))lastFilePath=lastFilePath+HISTOGRAM_FILE_NAME_EXTENSION;
            // get the last folder
//            fileChooser.setFileFilter(datFileFilter);
            File f=new File(lastFilePath);
            fileChooser.setCurrentDirectory(f);
            fileChooser.setSelectedFile(f);
            int retValue = fileChooser.showOpenDialog(fileChooser);
            if (retValue == JFileChooser.CANCEL_OPTION) {
                return;
            }
            File file = fileChooser.getSelectedFile();
            saveHistogramToFile(file);
        } catch (IOException e) {
            log.warning("couldn't save histogram: " + e);
        }

    }

    public void saveHistogramToFile(File file) throws IOException, FileNotFoundException {
        log.info("Saving histogram data to " + file.getName());
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(histogram.length);
        oos.writeObject(histogram);
        oos.writeObject(bitmap);
        oos.writeFloat(threshold);
        oos.close();
        fos.close();
        log.info("histogram saved to file " + file.getPath());
        setFilePath(file.getPath());
    }

     public void loadHistogramFromFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            setHistmax((Integer) ois.readObject());
            histogram = (int[][]) ois.readObject();
            bitmap=(boolean[][]) ois.readObject();
            setThreshold(ois.readFloat());
            numX=0; numY=0;
            if(histogram!=null){
                numX=histogram.length;
                if(histogram[0]!=null){
                    numY=histogram[0].length;
                }
            }
            numPix = numX * numY;
            computeTotalSum(); // in case loaded from file
            computeErodedBitmap(); // in case not stored
            ois.close();
            fis.close();
            log.info("histogram loaded from (usually host/java) file " + file.getPath() + "; histmax=" + histmax);
            setFilePath(file.getPath());

        } catch (Exception e) {
            log.info("couldn't load histogram from file " + file + ": " + e.toString());
        }
    }

     final synchronized public void doLoadHistogram() {
        JFileChooser fileChooser = new JFileChooser();
        String lastFilePath = getString("lastFile", System.getProperty("user.dir") );
        // get the last folder
//            fileChooser.setFileFilter(datFileFilter);
        File f=new File(lastFilePath);
        fileChooser.setCurrentDirectory(f);
        fileChooser.setSelectedFile(f);
        int retValue = fileChooser.showOpenDialog(fileChooser);
        if (retValue == JFileChooser.CANCEL_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();
        putString("lastFile",file.getPath());
        loadHistogramFromFile(file);
    }


    @Override
    synchronized public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    
    @Override
    public void annotate(GLAutoDrawable drawable) {
        if ((!showHistogram && !showBitmap) || histogram == null || histmax == 0) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int numX = chip.getSizeX(), numY = chip.getSizeY();
        final float bmbrightness=.4f;
        for (int y = 0; y < numY; y++) {
            for (int x = 0; x < numX; x++) {
                float v1 = (float) histogram[x][y];
                float v2 = v1 / threshold;
                if(showHistogram){
                    if (v1 > threshold) {
                        gl.glColor4f(v2, v2, 0, 0.25f);
                        gl.glRectf(x, y, x + 1, y + 1);
                    } else if (v1 > 0) {
                        gl.glColor4f(0, v2, v2, .25f);
                        gl.glRectf(x, y, x + 1, y + 1);
                    }
                }
               
                if(showBitmap && bitmap!=null && bitmap[x][y]){
                    gl.glColor4f(bmbrightness,bmbrightness,bmbrightness, 0.5f);
                    gl.glRectf(x, y, x + 1, y + 1);
                }
            }
        }
    }

    private AEViewer getViewer() {
        return getChip().getAeViewer();
    }

    private AEChipRenderer getRenderer() {
        return getChip().getRenderer();
    }

    private void checkHistogram() {
        if (histogram == null || numX != getChip().getSizeX() || numY != getChip().getSizeY()) {
            numX=chip.getSizeX();
            numY=chip.getSizeY();
            numPix = numX * numY;
           histogram = new int[numX][numY];
        }
    }

    /**
     * @return the collect
     */
    public boolean isCollect() {
        return collect;
    }

    /**
     * @param collect the collect to set
     */
    public void setCollect(boolean collect) {
        boolean old = this.collect;
        this.collect = collect;
        getSupport().firePropertyChange("collect", old, collect);
    }

    /**
     * @return the threshold
     */
    public float getThreshold() {
        return threshold;
    }

    /**
     * @param threshold the threshold to set
     */
    public void setThreshold(float threshold) {
        float old=this.threshold;
        this.threshold = threshold;
        putFloat("threshold", threshold);
        getSupport().firePropertyChange("threshhold",old,this.threshold); // update GUI on loading histogram
        if(old!=this.threshold){
            computeErodedBitmap();
        }
    }

    /**
     * @return the histmax
     */
    public int getHistmax() {
        return histmax;
    }

    /**
     * @param histmax the histmax to set
     */
    public void setHistmax(int histmax) {
        int old = this.histmax;
        this.histmax = histmax;
        getSupport().firePropertyChange("histmax", old, histmax);
    }

    /**
     * @return the showHistogram
     */
    public boolean isShowHistogram() {
        return showHistogram;
    }

    /**
     * @param showHistogram the showHistogram to set
     */
    public void setShowHistogram(boolean showHistogram) {
        boolean old=this.showHistogram;
        this.showHistogram = showHistogram;
        putBoolean("showHistogram", showHistogram);
        getSupport().firePropertyChange("showHistogram",old,this.showHistogram);
    }
    
    /** Computes morphological erosion of histogram to produce boolean bitmap of pixels
     * 
     * @return boolean[][] where first dimension is x and second is y, or null if there is no data yet
     */
    synchronized public boolean[][] computeErodedBitmap() {
       if(totalSum==0) return null;
        bitmap = new boolean[numX][numY];
        int erSize = getErosionSize();
        if (erSize <= 0) {
            // Return original image
            for (int x = 0; x < numX; x++) {
                for (int y = 0; y < numY; y++) {
                    if (histogram[x][y]  > threshold) {
                        bitmap[x][y] = true;
                    } else {
                        bitmap[x][y] = false;
                    }
                }
            }
            return bitmap;
        }


        for (int y = 0; y < numY; y++) {
            for (int x = 0; x < numX; x++) {
                boolean keep = true;
                for (int k = -erSize; k <= erSize; k++) {
                    for (int l = -erSize; l <= erSize; l++) {
                        int pixY = clip(y + k, numY - 1); // limit to size-1 to avoid arrayoutofbounds exceptions
                        int pixX = clip(x + l, numX - 1);
                        if (histogram[pixX][pixY] < threshold) {
                            keep = false;
                            break;
                        }
                    }
                    if (keep == false) {
                        break;
                    }
                }
                bitmap[x][y] = keep;
            }
        }

        return bitmap;
    }

      private int clip(int val, int limit) {
        if (val >= limit && limit != 0) {
            return limit;
        } else if (val < 0) {
            return 0;
        }
        return val;
    }

    /**
     * @return the erosionSize
     */
    public int getErosionSize() {
        return erosionSize;
    }

    /**
     * @param erosionSize the erosionSize to set
     */
    public void setErosionSize(int erosionSize) {
        this.erosionSize = erosionSize;
        putInt("erosionSize",erosionSize);
    }

    public Point2D.Float getMaxHistogramPoint() {
        int maxX = -1, maxY = -1;
        float maxVal = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < numY; y++) {
            for (int x = 0; x < numX; x++) {
                if (histogram[x][y] > maxVal) {
                    maxVal = histogram[x][y];
                    maxX = x;
                    maxY = y;
                }
            }
        }
        return new Point2D.Float(maxX,maxY);
    }

    /**
     * @return the showBitmap
     */
    public boolean isShowBitmap() {
        return showBitmap;
    }

    /**
     * @param showBitmap the showBitmap to set
     */
    public void setShowBitmap(boolean showBitmap) {
        boolean old=this.showBitmap;
        this.showBitmap = showBitmap;
        putBoolean("showBitmap", this.showBitmap);
        getSupport().firePropertyChange("showBitmap",old,this.showBitmap);
    }

    /**
     * @return the filePath
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @param filePath the filePath to set
     */
    public void setFilePath(String filePath) {
        String old=this.filePath;
        this.filePath = filePath;
        putString("filePath",filePath);
        getSupport().firePropertyChange("filePath", old, this.filePath);
    }

    private void computeTotalSum() {
        int sum=0;
        for(int i=0;i<histogram.length;i++){
            for(int j=0;j<histogram[0].length;j++){
                sum+=histogram[i][j];
            }
        }
        totalSum=sum;
    }

    /**
     * @return the invertFiltering
     */
    public boolean isInvertFiltering() {
        return invertFiltering;
    }

    /**
     * @param invertFiltering the invertFiltering to set
     */
    public void setInvertFiltering(boolean invertFiltering) {
        this.invertFiltering = invertFiltering;
        putBoolean("invertFiltering", invertFiltering);
    }


}
