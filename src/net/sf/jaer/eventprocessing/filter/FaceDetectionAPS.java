/**
 * Extracts CIS APS frames from SBRet10/20 DAVIS sensors.
 * Tag Faces in image and draw circle around them in real time.
 * Use
 * <ul>
 * <li>hasNewFrame() to check whether a new frame is available
 * <li>getDisplayBuffer() to get the latest float buffer displayed
 * <li>getNewFrame() to get the latest double buffer
 * </ul>
 *
 * @author  Federico Corradi, Christian Brändli
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileOutputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.ImageDisplay.Legend;
import net.sf.jaer.util.DATFileFilter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import eu.seebetter.ini.chips.DavisChip;


@Description("Detect Faces with OpenCV and label data for later supervised learning.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class FaceDetectionAPS extends EventFilter2D implements Observer /* Observer needed to get change events on chip construction */{

    private JFrame apsFrame = null;
    public ImageDisplay apsDisplay;
    private DavisChip apsChip = null;
    private boolean newFrame, useExtRender = false; // useExtRender means using something like OpenCV to render the data. If false, the displayBuffer is displayed
    private float[] resetBuffer, signalBuffer;
    /** Raw pixel values from sensor, before conversion, brightness, etc.*/
    private float[] displayBuffer;
    private float[] apsDisplayPixmapBuffer;
    /** Cooked pixel values, after brightness, contrast, log intensity conversion, etc. */
    private float[] displayFrame; // format is RGB triplets indexed by ??? what is this? How different than displayBuffer???
    public int width, height, maxADC, maxIDX;
    private float grayValue;
    public final float logSafetyOffset = 10000.0f;
    protected boolean showAPSFrameDisplay = getBoolean("showAPSFrameDisplay", true);
    private Legend apsDisplayLegend;
    public float pos_x, pos_y;
    public int pos_w, pos_h;
    public Labeled_image[] faces_in_frame;
    public String string;
    /**
     * A PropertyChangeEvent with this value is fired when a new frame has been
     * completely read. The oldValue is null. The newValue is the float[]
     * displayFrame that will be rendered.
     */
    public static final String EVENT_NEW_FRAME = AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE;
    private int lastFrameTimestamp=-1;
    private BufferedImage taggedImage;

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME); //add library opencv
    }
	/**
	 * Data logger for faces
	 */
    private boolean face_loggingEnabled =  getBoolean("face_loggingEnabled", false);
    private AEFileOutputStream face_loggingOutputStream;
    private String face_defaultLoggingFolderName = System.getProperty("user.dir");
    // lastLoggingFolder starts off at user.dir which is startup folder "host/java" where .exe launcher lives
    private String face_loggingFolder = "filterSettings/OpenCV_Nets/Labeled_data";
    private File face_loggingFile;
    private File face_labelFile;
    private int face_maxLogFileSizeMB = prefs().getInt("DataLogger.maxLogFileSizeMB", 100);
    private boolean face_rotateFilesEnabled = prefs().getBoolean("DataLogger.rotateFilesEnabled", false);
    private int face_rotatePeriod = prefs().getInt("DataLogger.rotatePeriod", 7);
    private long face_bytesWritten = 0;
    private String face_logFileBaseName = prefs().get("DataLogger.logFileBaseName", "");
    private int face_rotationNumber = 0;
    private boolean face_filenameTimestampEnabled = prefs().getBoolean("DataLogger.filenameTimestampEnabled", true);
    private File file_labels;
    private FileWriter fileWritter;
    private BufferedWriter bufferWritter;

    //opencv face detection
    public processor my_processor=new processor();
    public Mat webcam_image=new Mat();

    @Override
    public void update(Observable o, Object arg) {
        if((o instanceof AEChip) && (arg.equals(Chip2D.EVENT_SIZEX) || arg.equals(Chip2D.EVENT_SIZEY))){
            initFilter();
        }

    }

    public static enum Extraction {

        ResetFrame, SignalFrame, CDSframe
    };
    private boolean invertIntensity = getBoolean("invertIntensity", false);
    private boolean preBufferFrame = getBoolean("preBufferFrame", true);
    private boolean realTimeLabelFrame = getBoolean("realTimeLabelFrame", false);
    private boolean logCompress = getBoolean("logCompress", false);
    private boolean logDecompress = getBoolean("logDecompress", false);
    private float displayContrast = getFloat("displayContrast", 1.0f);
    private float displayBrightness = getFloat("displayBrightness", 0.0f);
    public Extraction extractionMethod = Extraction.valueOf(getString("extractionMethod", "CDSframe"));

    public FaceDetectionAPS(AEChip chip) {
        super(chip);
        apsDisplay = ImageDisplay.createOpenGLCanvas();
        apsFrame = new JFrame("APS Frame");
        apsFrame.setPreferredSize(new Dimension(400, 400));
        apsFrame.getContentPane().add(apsDisplay, BorderLayout.CENTER);
        apsFrame.pack();
        apsFrame.addWindowListener(new WindowAdapter() {
            @Override
			public void windowClosing(WindowEvent e) {
                setShowAPSFrameDisplay(false);
            }
        });
        apsDisplayLegend = apsDisplay.addLegend("", 0, 0);
        float[] displayColor = new float[3];
        displayColor[0] = 1.0f;
        displayColor[1] = 1.0f;
        displayColor[2] = 1.0f;
        apsDisplayLegend.color = displayColor;
        initFilter();

        setPropertyTooltip("invertIntensity", "Inverts grey scale, e.g. for raw samples of signal level");
        setPropertyTooltip("preBufferFrame", "Only display and use complete frames; otherwise display APS samples as they arrive");
        setPropertyTooltip("realTimeLabelFrame", "Label faces in APS frame in real time");
        setPropertyTooltip("logCompress", "Should the displayBuffer be log compressed");
        setPropertyTooltip("logDecompress", "Should the logComressed displayBuffer be rendered in log scale (true) or linearly (false)");
        setPropertyTooltip("displayContrast", "Gain for the rendering of the APS display");
        setPropertyTooltip("displayBrightness", "Offset for the rendering of the APS display");
        setPropertyTooltip("extractionMethod", "Method to extract a frame; CDSframe is the final result after subtracting signal from reset frame. Signal and reset frames are the raw sensor output before correlated double sampling.");
        setPropertyTooltip("showAPSFrameDisplay", "Shows the JFrame frame display if true");

        //final String cont = "Control", params = "Parameters";
        setPropertyTooltip( "face_loggingEnabled", "Enable to start logging data");
        setPropertyTooltip( "face_filenameTimestampEnabled", "adds a timestamp to the filename, but means rotation will not overwrite old data files and will eventually fill disk");
        setPropertyTooltip( "face_logFileBaseName", "the base name of the log file - if empty the AEChip class name is used");
        setPropertyTooltip( "face_rotatePeriod", "log file rotation period");
        setPropertyTooltip( "face_rotateFilesEnabled", "enabling rotates log files over rotatePeriod");
        setPropertyTooltip( "face_maxLogFileSizeMB", "logging is stopped when files get larger than this in MB");
        setPropertyTooltip( "face_loggingFolder", "directory to store logged data files");
        // check lastLoggingFolder to see if it really exists, if not, default to user.dir
        File lf = new File(face_loggingFolder);
        if (!lf.exists() || !lf.isDirectory()) {
            log.warning("face_loggingFolder " + lf + " doesn't exist or isn't a directory, defaulting to " + lf);
            face_setLoggingFolder(face_defaultLoggingFolderName);
        }

        chip.addObserver(this);

    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void resetFilter() {
        if (DavisChip.class.isAssignableFrom(chip.getClass())) {
            apsChip = (DavisChip) chip;
        } else {
            log.warning("The filter ApsFrameExtractor can only be used for chips that extend the ApsDvsChip class");
        }
        newFrame = false;
        width = chip.getSizeX(); // note that on initial construction width=0 because this constructor is called while chip is still being built
        height = chip.getSizeY();
        maxIDX = width * height;
        maxADC = apsChip.getMaxADC();
        apsDisplay.setImageSize(width, height);
        resetBuffer = new float[width * height];
        signalBuffer = new float[width * height];
        displayFrame = new float[width * height];
        displayBuffer = new float[width * height];
        apsDisplayPixmapBuffer = new float[3 * width * height];
        Arrays.fill(resetBuffer, 0.0f);
        Arrays.fill(signalBuffer, 0.0f);
        Arrays.fill(displayFrame, 0.0f);
        Arrays.fill(displayBuffer, 0.0f);
        Arrays.fill(apsDisplayPixmapBuffer, 0.0f);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

    	ApsDvsEventPacket packet = (ApsDvsEventPacket) in;
 	    loglabeledData(packet);

        checkMaps();

        if (packet == null) {
            return null;
        }
        if (packet.getEventClass() != ApsDvsEvent.class) {
            log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + ApsDvsEvent.class);
            return null;
        }
        Iterator apsItr = packet.fullIterator();
        while (apsItr.hasNext()) {
            ApsDvsEvent e = (ApsDvsEvent) apsItr.next();
            if (e.isSampleEvent()) {
                putAPSevent(e);
            }
        }
        if (showAPSFrameDisplay) {
            apsDisplay.repaint();
        }


        return in;
    }

    synchronized private void loglabeledData(EventPacket eventPacket) {
        if (eventPacket == null) {
            return;
        }
        // if we are logging data to disk do it here
        if (face_loggingEnabled) {
	        try {
	        	face_loggingOutputStream.writePacket(eventPacket); // log all events
	            face_bytesWritten += eventPacket.getSize();
	        	///faces_in_frame
	        	//faces_in_frame.length
                fileWritter = new FileWriter(file_labels,true);
                bufferWritter = new BufferedWriter(fileWritter);
	        	for (Labeled_image element : faces_in_frame) {
	        		//log.warning("FACE AT X " + element.getloc_x());
	        		//log.warning("FACE AT Y " + element.getloc_y());
	        		eventPacket.getFirstTimestamp();
	        		//for ( EventPacket e:eventPacket ){
	                int ts = eventPacket.getFirstTimestamp();
	                //log.warning("TIMESTAMP " + ts);
	                string = String.format("%d\t%f\t%f\t%d\t%d\n", ts, element.getloc_x(),element.getloc_y(), element.getw(), element.geth());
	            	try {
	            		bufferWritter.write(string);
	            		//bufferWritter.newLine();

	        		}
	        		catch (IOException e) {
	        			// TODO Auto-generated catch block
	        			e.printStackTrace();
	        		}
	        		//}
	        	}
	        	bufferWritter.flush();
    			bufferWritter.close();

	            if ((face_bytesWritten >>> 20) > face_maxLogFileSizeMB) {
	                setface_loggingEnabled(false);
	            }
	        } catch (IOException e) {
	            log.warning("while logging data to " + face_loggingFile + " caught " + e + ", will try to close file");
	            face_loggingEnabled = false;
	            getSupport().firePropertyChange("loggingEnabled", null, false);
	           // try {
	           // 	face_loggingOutputStream.close();
	            //    log.info("closed logging file " + face_loggingFile);
	            //} catch (IOException e2) {
	            //    log.warning("while closing logging file " + face_loggingFile + " caught " + e2);
	            //}
	        }
	        catch(NullPointerException e)
	        {
	            System.out.print("NullPointerException caught\n");
	        }

        }

    }

    private void checkMaps() {
        apsDisplay.checkPixmapAllocation();
        if (showAPSFrameDisplay && !apsFrame.isVisible()) {
            apsFrame.setVisible(true);
        }
    }

    public void putAPSevent(ApsDvsEvent e) {
        if (!e.isSampleEvent()) {
            return;
        }
        //if(e.isStartOfFrame())timestampFrameStart=e.timestampFrameStart;
        ApsDvsEvent.ReadoutType type = e.getReadoutType();
        float val = e.getAdcSample();
        int idx = getIndex(e.x, e.y);
        if (idx >= maxIDX) {
            return;
        }
        if (e.isStartOfFrame()) {
            if (newFrame && useExtRender) {
                log.warning("Acquistion of new frame started even though old frame was never delivered to ext renderer");
            }
        }
        if (idx < 0) {
            if (e.isEndOfFrame()) {
                if (preBufferFrame && (displayBuffer != null) && !useExtRender && showAPSFrameDisplay) {
                    displayPreBuffer();
                }
                if (realTimeLabelFrame && (displayBuffer != null) && !useExtRender && showAPSFrameDisplay) {
                	setPreBufferFrame(true);
                    displayPreBufferTagged(); //here label faces
                }
                newFrame = true;
                lastFrameTimestamp=e.timestamp;
                getSupport().firePropertyChange(EVENT_NEW_FRAME, null, displayFrame);
            }
            return;
        }
        switch (type) {
            case SignalRead:
                signalBuffer[idx] = val;
                break;
            case ResetRead:
            default:
                resetBuffer[idx] = val;
                break;
        }
        switch (extractionMethod) {
            case ResetFrame:
                displayBuffer[idx] = resetBuffer[idx];
                break;
            case SignalFrame:
                displayBuffer[idx] = signalBuffer[idx];
                break;
            case CDSframe:
            default:
                displayBuffer[idx] = resetBuffer[idx] - signalBuffer[idx];
                break;
        }
        if (invertIntensity) {
            displayBuffer[idx] = maxADC - displayBuffer[idx];
        }
        if (logCompress) {
            displayBuffer[idx] = (float) Math.log(displayBuffer[idx] + logSafetyOffset);
        }
        if (logCompress && logDecompress) {
            grayValue = scaleGrayValue((float) (Math.exp(displayBuffer[idx]) - logSafetyOffset));
        } else {
            grayValue = scaleGrayValue(displayBuffer[idx]);
        }
        displayFrame[idx] = grayValue;
        if (!preBufferFrame && !useExtRender && showAPSFrameDisplay) {
            apsDisplay.setPixmapGray(e.x, e.y, grayValue);
        } else {
            apsDisplayPixmapBuffer[3 * idx] = grayValue;
            apsDisplayPixmapBuffer[(3 * idx) + 1] = grayValue;
            apsDisplayPixmapBuffer[(3 * idx) + 2] = grayValue;
        }
    }

    /** convert Mat to BufferedImage, it works with grayscale images or 3byte_bgr
     *  @return BufferedImage (can be dim*3 if input mat is CV_8UC3 or dim*1 if input mat is CV_8UC1)
     */
    public static BufferedImage mat2Img(Mat in, int x, int y)
    {
        BufferedImage out;
        byte[] data = new byte[ x * y * (int)in.elemSize()];
        int type;
        in.get(0, 0, data);
        if(in.channels() == 1) {
			type = BufferedImage.TYPE_BYTE_GRAY;
		}
		else {
			type = BufferedImage.TYPE_3BYTE_BGR;
		}
        out = new BufferedImage(x, y, type);
        out.getRaster().setDataElements(0, 0, x, y, data);
        return out;
    }

    /** convert Mat to float,
     *  @return float
     */
    private float[] mat2float(Mat in, int x, int y) {
    	byte[] data = new byte[ x * y * (int)in.elemSize()];
    	float[] outputFile;
    	outputFile = new float[3 * width * height];
        for(int i = 0; i < (3 * width * height); i=i+3)
        {
        	outputFile[i*3] = data[i] >> 16 ;
        	outputFile[(i*3) + 1] = data[i+1] >> 8;
        	outputFile[(i*3) + 2] = data[i+2] >> 0;
        }
        return outputFile;
	}

    /** convert BufferedImage to float [3 * width * height],
     *  @return float
     */
    private float[] img2float(BufferedImage in, int x, int y) {
    	float[] outputFile;
    	outputFile = new float[3 * width * height];

    	//we need to flip it
    	int xa = 0;
    	int ya = 0;
        for (xa=0; xa < (width / 2); xa++) {
            for ( ya = 0; ya < height; ya++) {
                final int l = in.getRGB( width - (xa + 1), ya);
                final int r = in.getRGB( xa, ya);
                in.setRGB( xa, ya, l );
                in.setRGB( width - (xa + 1), ya, r );
            }
        }

        //if(in.getType() == BufferedImage.TYPE_INT_RGB)
        //{
            int[] dataBuff = in.getRGB(0, 0, x, y, null, 0, x);
            for(int i = 0; i < dataBuff.length; i++)
            {
            	outputFile[i*3] =  (dataBuff[dataBuff.length-i-1] >> 16) & 0xFF; //red
            	outputFile[(i*3) + 1] = (dataBuff[dataBuff.length-i-1] >> 8) & 0xFF; //green
            	outputFile[(i*3) + 2] = (dataBuff[dataBuff.length-i-1] >> 0) & 0xFF; //blue
            	outputFile[i*3] = (outputFile[i*3] ) / 255.0f;
            	outputFile[(i*3) + 1] = (outputFile[(i*3) + 1] ) / 255.0f;
            	outputFile[(i*3) + 2] = (outputFile[(i*3) + 2] ) / 255.0f;
            	//(dataBuff[dataBuff.length-i-1] >> 24) alpha
            }
            //ArrayUtils.reverse(outputFile);
        //}
        return outputFile;
	}


    /** convert BufferedImage to Mat, , it works with grayscale images or 3byte_bgr
     * @return mat (can be dim*3 if input image is TYPE_INT_RGB or dim*1 if input image is TYPE_BYTE_GRAY)
     */
    public static Mat img2Mat(BufferedImage in, int x, int y)
    {
          Mat out;
          byte[] data;
          int r, g, b;
          if(in.getType() == BufferedImage.TYPE_INT_RGB)
          {
              out = new Mat(y, x, CvType.CV_8UC3);
              data = new byte[x * y * (int)out.elemSize()];
              int[] dataBuff = in.getRGB(0, 0, x, y, null, 0, x);
              for(int i = 0; i < dataBuff.length; i++)
              {
                  data[i*3] = (byte) ((dataBuff[i] >> 16) & 0xFF);
                  data[(i*3) + 1] = (byte) ((dataBuff[i] >> 8) & 0xFF);
                  data[(i*3) + 2] = (byte) ((dataBuff[i] >> 0) & 0xFF);
              }
          }
          else
          {
              out = new Mat(y, x, CvType.CV_8UC1);
              data = new byte[x * y * (int)out.elemSize()];
              int[] dataBuff = in.getRGB(0, 0, x, y, null, 0, x);
              for(int i = 0; i < dataBuff.length; i++)
              {
                r = (byte) ((dataBuff[i] >> 16) & 0xFF);
                g = (byte) ((dataBuff[i] >> 8) & 0xFF);
                b = (byte) ((dataBuff[i] >> 0) & 0xFF);
                data[i] = (byte)((0.21 * r) + (0.71 * g) + (0.07 * b)); //luminosity
              }
           }
           out.put(0, 0, data);
           return out;
     }

    public void saveImage(){
        Date d=new Date();
        String fn="filterSettings/OpenCV_Nets/Saved_images/ApsFrame-"+AEDataFile.DATE_FORMAT.format(d)+".png";
        BufferedImage theImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_BYTE_GRAY);
        for(int y = 0; y<chip.getSizeY(); y++){
            for(int x = 0; x<chip.getSizeX(); x++){
                int idx = apsDisplay.getPixMapIndex(x, chip.getSizeY()-y-1);
                int value = ((int)(256*apsDisplay.getPixmapArray()[idx]) << 16) | ((int)(256*apsDisplay.getPixmapArray()[idx+1]) << 8) | (int)(256*apsDisplay.getPixmapArray()[idx+2]);
                theImage.setRGB(x, y, value);
            }
        }
        File outputfile = new File(fn);
        try {
            ImageIO.write(theImage, "png", outputfile);
        } catch (IOException ex) {
            Logger.getLogger(ApsFrameExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void labelImage(){
        Date d=new Date();
        String fn="filterSettings/OpenCV_Nets/Saved_images/ApsLabeledFrame-"+AEDataFile.DATE_FORMAT.format(d)+".png";
        BufferedImage theImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_BYTE_GRAY);
        for(int y = 0; y<chip.getSizeY(); y++){
            for(int x = 0; x<chip.getSizeX(); x++){
                int idx = apsDisplay.getPixMapIndex(x, chip.getSizeY()-y-1);
                int value = ((int)(256*apsDisplay.getPixmapArray()[idx]) << 16) | ((int)(256*apsDisplay.getPixmapArray()[idx+1]) << 8) | (int)(256*apsDisplay.getPixmapArray()[idx+2]);
                theImage.setRGB(x, y, value);
            }
        }
        //convert image to mat
        Mat newMat = img2Mat(theImage, chip.getSizeX(), chip.getSizeY());
        //opencv neural net face detection from APS frame
        faces_in_frame = my_processor.detect(newMat);
        //re-convert the tagged mat back to image with circles around faces
        theImage = mat2Img(newMat, chip.getSizeX(), chip.getSizeY());
        File outputfile = new File(fn);
        try {
            ImageIO.write(theImage, "png", outputfile);
        } catch (IOException ex) {
            Logger.getLogger(ApsFrameExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Returns timestampFrameStart of last frame, which is the timestampFrameStart of the frame end event
     *
     * @return the timestampFrameStart (usually in us)
     */
    public int getLastFrameTimestamp() {
        return lastFrameTimestamp;
    }

    private float scaleGrayValue(float value) {
        float v;
        v = ((displayContrast * value) + displayBrightness) / maxADC;
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return v;
    }

    public void updateDisplayValue(int xAddr, int yAddr, float value) {
        if (logCompress && logDecompress) {
            grayValue = scaleGrayValue((float) (Math.exp(value) - logSafetyOffset));
        } else {
            grayValue = scaleGrayValue(value);
        }
        apsDisplay.setPixmapGray(xAddr, yAddr, grayValue);
    }

    public void setPixmapArray(float[] pixmapArray) {
        apsDisplay.setPixmapArray(pixmapArray);
    }

    public void displayPreBuffer() {
        apsDisplay.setPixmapArray(apsDisplayPixmapBuffer);
    }



    public void displayPreBufferTagged() {
    	//System.out.println(apsDisplayPixmapBuffer);

        Date d=new Date();
        BufferedImage theImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_BYTE_GRAY);
        for(int y = 0; y<chip.getSizeY(); y++){
            for(int x = 0; x<chip.getSizeX(); x++){
                int idx = apsDisplay.getPixMapIndex(x, chip.getSizeY()-y-1);
                int value = ((int)(256*apsDisplayPixmapBuffer[idx]) << 16) | ((int)(256*apsDisplayPixmapBuffer[idx+1]) << 8) | (int)(256*apsDisplayPixmapBuffer[idx+2]);
                theImage.setRGB(x, y, value);
            }
        }
        //convert image to mat
        Mat newMat = img2Mat(theImage, chip.getSizeX(), chip.getSizeY());
        //opencv neural net face detection from APS frame
        faces_in_frame = my_processor.detect(newMat);
        //reconvert mat to image
        theImage = mat2Img(newMat, chip.getSizeX(), chip.getSizeY());
        //re-convert the tagged mat back to image with circles around faces
        apsDisplayPixmapBuffer = img2float(theImage, chip.getSizeX(), chip.getSizeY());

        apsDisplay.setPixmapArray(apsDisplayPixmapBuffer);
    }


	/**
     * returns the index <code>y * width + x</code> into pixel arrays for a given x,y location where x is
     * horizontal address and y is vertical and it starts at lower left corner
     * with x,y=0,0 and x and y increase to right and up.
     *
     * @param x
     * @param y
     * @param idx the array index
     * @see #getWidth()
     * @see #getHeight()
     */
    public int getIndex(int x, int y) {
        return (y * width) + x;
    }

    /**
     * Checks if new frame is available.
     *
     * @return true if new frame is available
     * @see #getNewFrame()
     */
    public boolean hasNewFrame() {
        return newFrame;
    }

    /**
     * Returns a double[] buffer of latest displayed frame with adjustments like brightness, contrast, log intensity conversion, etc.
     * The array is indexed by y * width + x. To access a particular pixel,
     * use getIndex().
     *
     * @return the double[] frame
     */
    public float[] getNewFrame() {
        newFrame = false;
        return displayFrame;
    }

    /**
     * Returns a clone of the latest float buffer. The array is indexed by <code>y * width + x</code>.
     * To access a particular pixel, use getIndex() for convenience.
     *
     * @return the float[] of pixel values
     * @see #getIndex(int, int)
     */
    public float[] getDisplayBuffer() {
        newFrame = false;
        return displayBuffer.clone();
    }

    /**
     * Tell chip to acquire new frame, return immediately.
     *
     */
    public void acquireNewFrame() {
        apsChip.takeSnapshot();
    }

    public float getMinBufferValue() {
        float minBufferValue = 0.0f;
        if (logCompress) {
            minBufferValue = (float) Math.log(minBufferValue + logSafetyOffset);
        }
        return minBufferValue;
    }

    public float getMaxBufferValue() {
        float maxBufferValue = maxADC;
        if (logCompress) {
            maxBufferValue = (float) Math.log(maxBufferValue + logSafetyOffset);
        }
        return maxBufferValue;
    }

    public void setExtRender(boolean setExt) {
        this.useExtRender = setExt;
    }

    public void setLegend(String legend) {
        this.apsDisplayLegend.s = legend;
    }

    public void setDisplayGrayFrame(double[] frame) {
        int xc = 0;
        int yc = 0;
        for (double element : frame) {
            apsDisplay.setPixmapGray(xc, yc, (float) element);
            xc++;
            if (xc == width) {
                xc = 0;
                yc++;
            }
        }
    }

    public void setDisplayFrameRGB(float[] frame) {
        int xc = 0;
        int yc = 0;
        for (int i = 0; i < frame.length; i += 3) {
            apsDisplay.setPixmapRGB(xc, yc, frame[i + 2], frame[i + 1], frame[i]);
            xc++;
            if (xc == width) {
                xc = 0;
                yc++;
            }
        }
    }

    /**
     * @return the invertIntensity
     */
    public boolean isInvertIntensity() {
        return invertIntensity;
    }

    /**
     * @param invertIntensity the invertIntensity to set
     */
    public void setInvertIntensity(boolean invertIntensity) {
        this.invertIntensity = invertIntensity;
        putBoolean("invertIntensity", invertIntensity);
    }

    /**
     * @return the preBufferFrame
     */
    public boolean isPreBufferFrame() {
        return preBufferFrame;
    }

    /**
     * @return the realTimeLabelFrame
     */
    public boolean isrealTimeLabelFrame() {
        return realTimeLabelFrame;
    }


    /**
     * @param invertIntensity the invertIntensity to set
     */
    public void setPreBufferFrame(boolean preBuffer) {
        this.preBufferFrame = preBuffer;
        putBoolean("preBufferFrame", preBufferFrame);
    }

    /**
     */
    public void setrealTimeLabelFrame(boolean realTimeLabelFrame) {
        this.realTimeLabelFrame = realTimeLabelFrame;
        putBoolean("realTimeLabelFrame", realTimeLabelFrame);
    }

    /**
     * @return the logDecompress
     */
    public boolean isLogDecompress() {
        return logDecompress;
    }

    /**
     * @param logDecompress the logDecompress to set
     */
    public void setLogDecompress(boolean logDecompress) {
        this.logDecompress = logDecompress;
        putBoolean("logDecompress", logDecompress);
    }

    /**
     * @return the logCompress
     */
    public boolean isLogCompress() {
        return logCompress;
    }

    /**
     * @param logCompress the logCompress to set
     */
    public void setLogCompress(boolean logCompress) {
        this.logCompress = logCompress;
        putBoolean("logCompress", logCompress);
    }

    /**
     * @return the displayContrast
     */
    public float getDisplayContrast() {
        return displayContrast;
    }

    /**
     * @param displayContrast the displayContrast to set
     */
    public void setDisplayContrast(float displayContrast) {
        this.displayContrast = displayContrast;
        putFloat("displayContrast", displayContrast);
        resetFilter();
    }

    /**
     * @return the displayBrightness
     */
    public float getDisplayBrightness() {
        return displayBrightness;
    }

    /**
     * @param displayBrightness the displayBrightness to set
     */
    public void setDisplayBrightness(float displayBrightness) {
        this.displayBrightness = displayBrightness;
        putFloat("displayBrightness", displayBrightness);
        resetFilter();
    }

    public Extraction getExtractionMethod() {
        return extractionMethod;
    }

    synchronized public void setExtractionMethod(Extraction extractionMethod) {
        getSupport().firePropertyChange("extractionMethod", this.extractionMethod, extractionMethod);
        putString("edgePixelMethod", extractionMethod.toString());
        this.extractionMethod = extractionMethod;
        resetFilter();
    }

    /**
     * @return the showAPSFrameDisplay
     */
    public boolean isShowAPSFrameDisplay() {
        return showAPSFrameDisplay;
    }


    public boolean isface_loggingEnabled(){
    	return face_loggingEnabled;
    }

   // public void setface_loggingEnabled(boolean logData) {
   //     this.face_loggingEnabled = logData;
   //     putBoolean("face_loggingEnabled", logData);
   // }

    /**
     * @param showAPSFrameDisplay the showAPSFrameDisplay to set
     */
    public void setShowAPSFrameDisplay(boolean showAPSFrameDisplay) {
        this.showAPSFrameDisplay = showAPSFrameDisplay;
        putBoolean("showAPSFrameDisplay", showAPSFrameDisplay);
        if (apsFrame != null) {
            apsFrame.setVisible(showAPSFrameDisplay);
        }
        getSupport().firePropertyChange("showAPSFrameDisplay", null, showAPSFrameDisplay);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes); //To change body of generated methods, choose Tools | Templates.
        if (!isFilterEnabled()) {
            if (apsFrame != null) {
                apsFrame.setVisible(false);
            }
        }
    }

    /**
     * returns frame width in pixels.
     *
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * returns frame height in pixels
     *
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * returns max ADC value
     *
     * @return the maxADC
     */
    public int getMaxADC() {
        return maxADC;
    }

    /**
     * returns max index into frame buffer arrays
     *
     * @return the maxIDX
     */
    public int getMaxIDX() {
        return maxIDX;
    }

    /**
     */
    public void doSaveAsPNG() {
            saveImage();
    }

    /**
     */
    public void doLabelFacesAndSaveAsPNG() {
            labelImage();
    }


    /**
     * @param loggingEnabled the loggingEnabled to set
     */
    public void setface_loggingEnabled(boolean this_face) {
    	this.face_loggingEnabled = this_face;
    	putBoolean("face_loggingEnabled", this_face);
        boolean old = this.face_loggingEnabled;
        boolean success = false;
        if (this_face) {
            File f = face_startLogging();
            if (f == null) {
                log.warning("face_startLogging returned null");
                this.face_loggingEnabled=false;
                putBoolean("face_loggingEnabled", false);
            } else {
                success = true;
            }
        } else {
            File f = face_stopLogging(false);
            if (f == null) {
                log.warning("face_stopLogging returned null");
            } else {
                success = true;
            }
        }
            //this.face_loggingEnabled = face_loggingEnabled;
            getSupport().firePropertyChange("face_loggingEnabled", old, face_loggingEnabled);
    }

    /** Starts logging AE data to a file.
    *
    * @param filename the filename to log to, including all path information. Filenames without path
    * are logged to the startup folder. The default extension of AEDataFile.DATA_FILE_EXTENSION is appended if there is no extension.
    *
    * @return the file that is logged to.
    */
   synchronized public File face_startLogging(String filename) {
       if (filename == null) {
           log.warning("tried to log to null filename, aborting");
           return null;
       }
       if (!filename.toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION)) {
           filename = filename + AEDataFile.DATA_FILE_EXTENSION;
           log.info("Appended extension to make filename=" + filename);
       }
       try {
           face_loggingFile = new File(filename);
           face_loggingOutputStream = new AEFileOutputStream(new BufferedOutputStream(new FileOutputStream(face_loggingFile), 100000), chip);
           face_loggingEnabled = true;
           getSupport().firePropertyChange("face_loggingEnabled", null, true);
           log.info("starting logging to " + face_loggingFile);
       } catch (IOException e) {
           face_loggingFile = null;
           log.warning("exception on starting to log data to file "+filename+": "+e);
           face_loggingEnabled=false;
           getSupport().firePropertyChange("face_loggingEnabled", null, false);
       }

       return face_loggingFile;
   }

   /** Starts logging data to a default data logging file.
    * @return the file that is logged to.
 * @throws IOException
    */
   public File face_startLogging()  {

       String dateString = face_filenameTimestampEnabled ? AEDataFile.DATE_FORMAT.format(new Date()) : "";
       String base =
               chip.getClass().getSimpleName();
       int suffixNumber = face_rotateFilesEnabled ? face_rotationNumber++ : 0;
       if (face_rotationNumber >= face_rotatePeriod) {
    	   face_rotationNumber = 0;
       }
       boolean succeeded = false;
       String filename;

       if ((face_logFileBaseName != null) && !face_logFileBaseName.isEmpty()) {
           base = face_logFileBaseName;
       }
       String suffix;
       if (face_rotateFilesEnabled) {
           suffix = String.format("%02d", suffixNumber);
       } else {
           suffix = "";
       }
       do {
           filename = face_loggingFolder + File.separator + base + "-" + dateString + "-" + suffix + AEDataFile.DATA_FILE_EXTENSION;
           File lf = new File(filename);
           if (face_rotateFilesEnabled) {
               succeeded = true; // if rotation, always use next file
           } else if (!lf.isFile()) {
               succeeded = true;
           }

       } while ((succeeded == false) && (suffixNumber++ <= 99));
       if (succeeded == false) {
           log.warning("could not open a unigue new file for logging after trying up to " + filename + " aborting startLogging");
           return null;
       }

       log.info("SAVING LABELED DATA IN ###############################\n" +filename);
       log.info("FILENAME label ################> "+filename+"_label.txt");
       file_labels = new File(filename+"_label.txt");
       //if file doesn't exists, then create it
		if(!file_labels.exists()){
			try {
				file_labels.createNewFile();
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	try {
			fileWritter = new FileWriter(filename+"_label.txt");
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	bufferWritter = new BufferedWriter(fileWritter);
    	try {
			bufferWritter.append("# LABEL DATA from OPEN CV\n");
			bufferWritter.append("# The format of the file is timestamp, loc_x, loc_y, width, and height of the face (in pixels)\n");
			bufferWritter.append("# timestamp loc_x loc_y dimx dimy\n");
			bufferWritter.flush();
			bufferWritter.close();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
       face_bytesWritten = 0;
       File lf = face_startLogging(filename);
       log.info("AER DATA FILENAME ################> "+lf);
       return lf;

   }

   /** Stops logging and optionally opens file dialog for where to save file.
    * If number of AEViewers is more than one, dialog is also skipped since we may be logging from multiple viewers.
    * @param confirmFilename true to show file dialog to confirm filename, false to skip dialog.
    * @return chosen File
    */
   synchronized public File face_stopLogging(boolean confirmFilename) {
       if (!face_loggingEnabled) {
           return null;
       }
       // the file has already been logged somewhere with a timestamped name, what this method does is
       // to move the already logged file to a possibly different location with a new name, or if cancel is hit,
       // to delete it.
       int retValue = JFileChooser.CANCEL_OPTION;

       try {
           log.info("stopped logging at " + AEDataFile.DATE_FORMAT.format(new Date()));
           face_loggingEnabled = false;
           bufferWritter.flush();
           bufferWritter.close();
           fileWritter.close();
           face_loggingOutputStream.close();
//if jaer viewer is logging synchronized data files, then just save the file where it was logged originally

           if (confirmFilename) {
               JFileChooser chooser = new JFileChooser();
               chooser.setCurrentDirectory(new File(face_loggingFolder));
               chooser.setFileFilter(new DATFileFilter());
               chooser.setDialogTitle("Save logged data");

               String fn =
                       face_loggingFile.getName();
//               System.out.println("fn="+fn);
               // strip off .aedat to make it easier to add comment to filename
               String base =
                       fn.substring(0, fn.lastIndexOf(AEDataFile.DATA_FILE_EXTENSION));
               chooser.setSelectedFile(new File(base));
               chooser.setDialogType(JFileChooser.SAVE_DIALOG);
               chooser.setMultiSelectionEnabled(false);
               boolean savedIt = false;
               do {
                   // clear the text input buffer to prevent multiply typed characters from destroying proposed datetimestamped filename
                   retValue = chooser.showSaveDialog(chip.getAeViewer());
                   if (retValue == JFileChooser.APPROVE_OPTION) {
                       File newFile = chooser.getSelectedFile();
                       // make sure filename ends with .aedat
                       if (!newFile.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION)) {
                           newFile = new File(newFile.getCanonicalPath() + AEDataFile.DATA_FILE_EXTENSION);
                       }
//we'll rename the logged data file to the selection

                       boolean renamed = face_loggingFile.renameTo(newFile);
                       if (renamed) {
                           // if successful, cool, save persistence
                           savedIt = true;
                           face_setLoggingFolder(chooser.getCurrentDirectory().getPath());
                           face_loggingFile = newFile; // so that we play it back if it was saved and playback immediately is selected
                           log.info("renamed logging file to " + newFile);
                       } else {
                           // confirm overwrite
                           int overwrite = JOptionPane.showConfirmDialog(chooser, "Overwrite file \"" + newFile + "\"?", "Overwrite file?", JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                           if (overwrite == JOptionPane.OK_OPTION) {
                               // we need to delete the file
                               boolean deletedOld = newFile.delete();
                               if (deletedOld) {
                                   savedIt = face_loggingFile.renameTo(newFile);
                                   savedIt = true;
                                   log.info("renamed logging file to " + newFile); // TODO something messed up here with confirmed overwrite of logging file
                                   face_loggingFile = newFile;
                               } else {
                                   log.warning("couldn't delete logging file " + newFile);
                               }

                           } else {
                               chooser.setDialogTitle("Couldn't save file there, try again");
                           }

                       }
                   } else {
                       // user hit cancel, delete logged data
                       boolean deleted = face_loggingFile.delete();
                       if (deleted) {
                           log.info("Deleted temporary logging file " + face_loggingFile);
                       } else {
                           log.warning("Couldn't delete temporary logging file " + face_loggingFile);
                       }

                       savedIt = true;
                   }

               } while (savedIt == false); // keep trying until user is happy (unless they deleted some crucial data!)
               }

       } catch (IOException e) {
           e.printStackTrace();
       }

       face_loggingEnabled = false;
       getSupport().firePropertyChange("loggingEnabled", null, false);
       return face_loggingFile;
   }


    /**
     * @param loggingFolder the lastFolderName to set
     */
    public void face_setLoggingFolder(String loggingFolder) {
        String old = loggingFolder;
        this.face_loggingFolder = loggingFolder;
        getPrefs().put("DataLogger.loggingFolder", loggingFolder);
        getSupport().firePropertyChange("loggingFolder", old, loggingFolder);
    }

    /**
     * @return the maxLogFileSizeMB
     */
    public int getface_MaxLogFileSizeMB() {
        return face_maxLogFileSizeMB;
    }

    /**
     * @param maxLogFileSizeMB the maxLogFileSizeMB to set
     */
    public void setface_MaxLogFileSizeMB(int maxLogFileSizeMB) {
        this.face_maxLogFileSizeMB = maxLogFileSizeMB;
        prefs().putInt("DataLogger.maxLogFileSizeMB", maxLogFileSizeMB);
    }


}

class Labeled_image {
    private Mat inputframe;
    private int w;
    private int h;
    private double loc_x;
    private double loc_y;

	// constructor
	public Labeled_image(Mat inputframe, int w, int h, double d, double e) {
	   this.inputframe = inputframe;
	   this.w = w;
	   this.h = h;
	   this.loc_x = d;
	   this.loc_y = e;
	}

    // getter
    public Mat getMatImage() { return inputframe; }
    public int getw() { return w; }
    public int geth() { return h; }
    public double getloc_x() { return loc_x; }
    public double getloc_y() { return loc_y; }
    // setter

    public void setMatImage(Mat name) { this.inputframe = name; }
    public void setw(int code) { this.w = code; }
    public void seth(int code) { this.h = code; }
    public void setloc_x(float code) { this.loc_x = code; }
    public void setloc_y(float code) { this.loc_y = code; }
 }

class processor {
    private CascadeClassifier face_cascade;
    // Create a constructor method
    public processor(){
       //load network pre-trained weights
       face_cascade=new CascadeClassifier("filterSettings/OpenCV_Nets/haarcascade_frontalface_alt.xml");
  	   //face_cascade=new CascadeClassifier("/Users/federicocorradi/Documents/workspace/FaceDetectionOpenCV/src/main/resources/lbpcascade_frontalface.xml");
  	   if(face_cascade.empty())
         {
              System.out.println("--(!)Error loading A\n");
               return;
         }
         else
         {
                    System.out.println("Face classifier loaded...");
         }
    }

    public Labeled_image[] detect(Mat inputframe){
        MatOfRect faces = new MatOfRect();
        Imgproc.equalizeHist( inputframe, inputframe );
        face_cascade.detectMultiScale(inputframe, faces);
        System.out.println(String.format("Detected %s faces", faces.toArray().length));
        Labeled_image[] this_frame = new Labeled_image[faces.toArray().length];
        int counter = 0;
        for(Rect rect:faces.toArray())
        {
             Point center= new Point(rect.x + (rect.width*0.5), rect.y + (rect.height*0.5) );
             Imgproc.ellipse( inputframe, center, new Size( rect.width*0.5, rect.height*0.5), 0, 0, 360, new Scalar( 255, 0, 255 ), 4, 8, 0 );
             System.out.println(String.format("Face detected in center (%f,%f) rect size (%d,%d)", rect.x+ (rect.width*0.5), rect.y+ (rect.height*0.5), rect.width , rect.height));
             this_frame[counter] = new Labeled_image(inputframe, rect.width, rect.height, rect.x+ (rect.width*0.5), rect.y+ (rect.height*0.5));
            	 //setMatImage(inputframe);
             //this_frame[counter].setw(rect.width);
             //this_frame[counter].seth(rect.height);
             //this_frame[counter].setloc_x(rect.x);
             //this_frame[counter].setloc_y(rect.y);
             counter = counter +1;
        }
        return this_frame;
   }

}