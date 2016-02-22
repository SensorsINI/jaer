/*
 * InstrumentStringFilter.java
 *
 * Created during CapoCaccia 2014 workshop
 * @author Ellias & Adrien
 * We used Tobi's BackgroundActivityFilter file as a template.
 *
 * The purpose of this filter is to detect the vibrating strings
 * of a instrument that is shown to a (fixed) DVS camera
 *
 * ----- Adrien's remark -----
 * "@author Adrien" just means that I added a commentary. Not that I wrote the
 * designed piece of code!
 * -----------------------------------------------------------------------------
 */
package net.sf.jaer.eventprocessing.filter;

/* @author Adrien
 * Maybe some libraries are not useful anymore as we modified our template...
 */
//import ch.unizh.ini.jaer.projects.eventbasedfeatures.PixelBuffer;

//import com.sun.opengl.util.GLUT;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileNotFoundException;
//import java.math.MathContext;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

@Description("Measures the frequency of vibrating structures")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class InstrumentStringFilter extends EventFilter2D implements Observer, FrameAnnotater {

	final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;

    private boolean filterInPlace=getBoolean("filterInPlace", true);

    /* @author Adrien
     * Options that we want to be (or at least try to make...) user-defined
     * STILL NON WORKING
     */
    // The number of frequency values that can be stored for each pixel in a ring buffer
    private int pixelBufferLength = getPrefs().getInt("InstrumentStringFilter.pixelBufferLength", 32); // int type => we can only go up to 255 ?
    // The raw range of plausible frequency values, such as the earable range by default
    private float lowFrequencyRange  = getPrefs().getFloat("InstrumentStringFilter.lowFrequencyRange", (float) 20.0); // in Hz
    private float highFrequencyRange = getPrefs().getFloat("InstrumentStringFilter.highFrequencyRange", (float) 20000.0); // in Hz
    // The half width of the averaging window that is used to estimate the frequency fine value.
    private float halfWidthOfAveragingWindow = getPrefs().getFloat("InstrumentStringFilter.halfWidthOfAveragingWindow (in tones)", (float) 1.0); // tone_n+1 = tone_n*2^(1/12)
    // Decay window
    private int coloredPixelsAmount = getPrefs().getInt("InstrumentStringFilter.coloredPixelsAmount", 256);
    // Still to be engineered // A->B->A->B->A->B case ?
    //private boolean ABABA??? = getBoolean("?", false)
    // Still to be engineered // Type of A events
    //private  boolean areFirstEventsOfTypeOff = getBoolean("areFirstEventsOfTypeOff", true)

    float timestampUnit = (float) 1.0e-6; // See a previous remark: could be detected before to avoid problem if we are working with a different scale of timestamp...
    // Remark : USING TIMESTAMP_DEFAULT SEEMS TO BRING ISSUES THAT FEEL LIKE SIGNED/UNSIGNED ONES...

    PrintStream writer; // @author Adrien: is it still needed if we do not write an output histogram file?
    Pixel[][] pixelsMap; // a map of the objects that will store the relevant informations for each pixel
    ringBuffer displayedPixelsX, displayedPixelsY; // ring buffers to store the coordinates of the pixels that will be displayed
    ringBuffer timestampsOfDisplayedPixels ; // ring buffer that stores the timestamps (in number of timestampUnits) of the displayed pixels
    ringBuffer lastValidFrequencies ; // to store the finely averaged values of the last displayed events

    float[][] colorsTonesInRGB;

    public int getPixelBufferLength() {
    	return this.pixelBufferLength;
    }
    public void setPixelBufferLength (final int value){
        int old = this.pixelBufferLength;
    	this.pixelBufferLength = value;
        getPrefs().putInt("InstrumentStringFilter.pixelBufferLength", value);
        getSupport().firePropertyChange("pixelBufferLength", old, value);
    }

    public float getLowFrequencyRange() {
    	return this.lowFrequencyRange;
    }
    public void setLowFrequencyRange (final float value){
        float old = this.lowFrequencyRange;
    	this.lowFrequencyRange = value;
        getPrefs().putFloat("InstrumentStringFilter.lowFrequencyRange", value);
        getSupport().firePropertyChange("lowFrequencyRange", old, value);
    }

    public float getHighFrequencyRange() {
    	return this.highFrequencyRange;
    }
    public void setHighFrequencyRange (final float value){
        float old = this.highFrequencyRange;
    	this.highFrequencyRange = value;
        getPrefs().putFloat("InstrumentStringFilter.highFrequencyRange", value);
        getSupport().firePropertyChange("highFrequencyRange", old, value);
    }

    public float getHalfWidthOfAveragingWindow() {
    	return this.halfWidthOfAveragingWindow;
    }
    public void setHalfWidthOfAveragingWindow (final float value){
        float old = this.halfWidthOfAveragingWindow;
    	this.halfWidthOfAveragingWindow = value;
        getPrefs().putFloat("InstrumentStringFilter.halfWidthOfAveragingWindow", value);
        getSupport().firePropertyChange("halfWidthOfAveragingWindow", old, value);
    }

    public int getColoredPixelsAmount() {
    	return this.coloredPixelsAmount;
    }
    public void setColoredPixelsAmount (final int value){
        int old = this.coloredPixelsAmount;
    	this.coloredPixelsAmount = value;
        getPrefs().putInt("InstrumentStringFilter.coloredPixelsAmount", value);
        getSupport().firePropertyChange("coloredPixelsAmount", old, value);
    }


    public InstrumentStringFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        this.pixelsMap = new Pixel[chip.getSizeX()][chip.getSizeY()]; // We allocate a Pixel object per pixel of the DVS camera
        resetFilter();

        // UI sugar
        setPropertyTooltip("pixelBufferLength", "Length of the buffer that stores the frequencies values for each pixel.");
        setPropertyTooltip("lowFrequencyRange",  "Value (in Hz) of the lowest allowed frequency.");
        setPropertyTooltip("highFrequencyRange", "Value (in Hz) of the highest allowed frequency.");
        setPropertyTooltip("halfWidthOfAveragingWindow", "Half width of the fine averaging window, *in number of tones*.");
        setPropertyTooltip("coloredPixelsAmount", "Amount (maximum number...) of pixels for which a frequency guess is given.");

        try { // @author Adrien: Do we really still need this ?
            writer = new PrintStream(new File("freq.txt"));
        } catch (FileNotFoundException e) {
            // ... // @author Adrien: what?
        }

        resetFilter(); // Useful to take into account (in Pixel objects) the allowed frequencies range, etc.

   }

    private void allocateObjects(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
        	this.pixelsMap = new Pixel[chip.getSizeX()][chip.getSizeY()];
        	this.displayedPixelsX = new ringBuffer(this.coloredPixelsAmount);
        	this.displayedPixelsY = new ringBuffer(this.coloredPixelsAmount);
        	this.timestampsOfDisplayedPixels = new ringBuffer(this.coloredPixelsAmount);
        	this.lastValidFrequencies = new ringBuffer(this.coloredPixelsAmount);
        	this.colorsTonesInRGB = new float[12][3]; // we will display the color of each tone in an compete octave
        	resetFilter();
        }
    }
    private int ts = 0; // used to reset filter

    @Override
	synchronized public EventPacket filterPacket(EventPacket in) {

    	// DEBUG
        System.out.println(String.format("pBL = %d", this.pixelBufferLength));
        System.out.println(String.format("lFR = %f Hz", this.lowFrequencyRange));
        System.out.println(String.format("hFR = %f Hz", this.highFrequencyRange));
        System.out.println(String.format("hWoAW = %f", this.halfWidthOfAveragingWindow));
        System.out.println(String.format("cPA = %d", this.coloredPixelsAmount));

    	/* @ Adrien
    	 * Adapted from old Ellias' version...
    	 */
        if (pixelsMap == null) {
            allocateObjects(chip);
        }

        int sx = chip.getSizeX() - 1;
        int sy = chip.getSizeY() - 1;
        for (Object e : in) {
            if(e==null)
			 {
				break;  // this can occur if we are supplied packet that has data (e.g. APS samples) but no events @author tobi
			}
            PolarityEvent i = (PolarityEvent)e;
            if (i.isSpecial()) {
				continue;
			}
            ts = i.timestamp;
            short x = (i.x);
            short y = (i.y);

            /* @author Adrien
             * Do we still need this?
             */
            if ( ((x < 0) || (sx < x)) || ((y < 0) || (sy < y)) ) {
                continue;
            }

            /*
             ***************************
             * INCOMING EVENTS STORING *
             ***************************
             */

            /*
             * What could be or has to be done (@author Adrien):
             * - try to use the spatial neighboors events -> could be used to reduced the issue of a moving camera?
             * - detect (before) the unit of timestamp and use this information, instead of using this ugly hard coding hack...
             * - define the range of the first filtering (the eaudible frequencies at the moment) in the options of the filter, so the user can tune it easily
             * - let the user define the "type_A" events in the options, instead of hard coding it in the source...
             */
            int dt = 0; // estimation of the raw period

            // We are waiting for a first (given) type of events, let us say type_A
            if (i.polarity == PolarityEvent.Polarity.Off) {

            	// If the other type of event (type_B) occurred before, then we do some processing
            	if (pixelsMap[x][y].otherPolarityOccurred == true) {

            		// A sequence type_A (@ ts) -> type_B (cf. otherPolarityOccurred[x][y])-> type_A (@ lastTimestamps[x][y]) occured
                	// So we can estimate the fundamental period for this pixel
                	dt = ts - pixelsMap[x][y].lastTimestamp;

                	// We do a first raw filtering by rejecting the values that are clearly irrelevant.
                	// Indeed, if we do not, we can face absurdly high frequency values (-> even infinite).
                	// If the frequency is in the raw range of possible (or simply allowed) values, then we
                    // store the relevant informations for this pixel in a set of ring buffers
                	if (	(( dt*pixelsMap[x][y].pixelTimestampUnit) >  (1.0/pixelsMap[x][y].frequencyHighBoundary))
                		&&  (( dt*pixelsMap[x][y].pixelTimestampUnit) <  (1.0/pixelsMap[x][y].frequencyLowBoundary))) {

                		/*
                         * DEBUG
                         */
//                		System.out.print("f_low: ");
//                		System.out.print(pixelsMap[x][y].frequencyLowBoundary);
//                		System.out.println(" Hz.");
//                		System.out.print("1/dt: ");
//                		System.out.print((float) (1.0/(dt*pixelsMap[x][y].pixelTimestampUnit)));
//                		System.out.println(" Hz.");
//                		System.out.print("f_high: ");
//                		System.out.print(pixelsMap[x][y].frequencyHighBoundary);
//                		System.out.println(" Hz.");

                		// Updating the Pixel object with the relevant information
                		pixelsMap[x][y].measuredFrequencies.insertValue((float) (1.0/(dt*pixelsMap[x][y].pixelTimestampUnit)));
                        // Inserting this pixel to those that may be displayed
                		//System.out.println(String.format("measuredFrequencies: %.3f Hz", pixelsMap[x][y].measuredFrequencies.get_lastInsertedValue())); // DEBUG : SEEMS TO BE WORKING
                		this.displayedPixelsX.insertValue(x);
                		this.displayedPixelsY.insertValue(y);
                		this.timestampsOfDisplayedPixels.insertValue(ts);
                	}
                }

            	// In every case, we record the timestamp of the event for this pixel.
                // We also reset the flag concerning the occurence of a B type event.
                pixelsMap[x][y].lastTimestamp = ts; //
                pixelsMap[x][y].otherPolarityOccurred = false;
                /* @author Adrien
                 ***********************************************************************
                 ************************** IMPORTANT! *********************************
                 ***********************************************************************
                 * The previous lines might not handle very well the weird situations...
                 */
            } // type_B event detected
            else {
            	pixelsMap[x][y].otherPolarityOccurred = true;
            }

            /*
             *******************
             * PIXEL TREATMENT *
             *******************
             */

            // Looking for the octave of the fundamental frequency of each (recently activated) pixel.
            // We compute the histogram of the possible octaves, by looking for each value
        	// in the ad-hoc ring buffer which fits in the octaves range.
        	// NB: the bins should have already been built during the Pixel constructor call.
            float[] tempOctavesLowBoundaries = new float[pixelsMap[x][y].octavesRange.octavesLowBoundaries.length];
        	tempOctavesLowBoundaries = pixelsMap[x][y].octavesRange.octavesLowBoundaries; // just for readability
        	int[] tempOctavesHistogram = new int[tempOctavesLowBoundaries.length]; // Will store the counts numbers
	        Arrays.fill(tempOctavesHistogram, 0);

	        for (int cntFreq = 0; cntFreq < pixelsMap[x][y].measuredFrequencies.bufferSize; cntFreq++){
	        	float tempFrequency = pixelsMap[x][y].measuredFrequencies.get_bufferValues(cntFreq);
	        	if (0.0 < tempFrequency ){ // A null value indicates that nothing was written since the initialization so we do not take this value into account.
    	        	for (int cntBnd = (tempOctavesHistogram.length -1); 0 < cntBnd; cntBnd--) {
    	        		if (tempOctavesLowBoundaries[cntBnd] < tempFrequency) {
        	        		tempOctavesHistogram[cntBnd]++;
        	        		break;
        	            }
    	        	}
	        	}
	        }

	        // We detect the bin of the maximum value and its position
	        // What could be done:
	        // - find a built-in function to do this...
	        int histOctavesMax = 0;
	        int histOctavesMaxIndex = 0;

	        for (int cnt = 0; cnt < tempOctavesHistogram.length; cnt++) {
	            if (tempOctavesHistogram[cnt] > histOctavesMax) {
	                histOctavesMax = tempOctavesHistogram[cnt];
	                histOctavesMaxIndex = cnt;
	            }
	        }

	        // We update the Octave object of the Pixel one.
	        pixelsMap[x][y].octave = new Octave(pixelsMap[x][y].octavesRange.get_octavesIndex(histOctavesMaxIndex));

            /* @author Adrien
             * Looking for the tone of the fundamental frequency of each (recently activated) pixel.
             * We look for it inside the previously determined octave.
             */
	        // We compute the histogram of the possible tones, by looking for each value
        	// NB: the bins should have already been rebuilt during the new Octave constructor call.
        	float[] tempTonesLowBoundaries = pixelsMap[x][y].octave.tonesLowBoundaries; // again, just for readability
        	int[] tempTonesHistogram = new int[tempTonesLowBoundaries.length]; // Will store the counts numbers
        	Arrays.fill(tempTonesHistogram, 0);
        	float lowOctaveFilterFreq  = tempTonesLowBoundaries[0];
        	float highOctaveFilterFreq = tempTonesLowBoundaries[tempTonesLowBoundaries.length - 1]*pixelsMap[x][y].octave.reason;

        	for (int cntFreq = 0; cntFreq < pixelsMap[x][y].measuredFrequencies.bufferSize; cntFreq++){
	        	float tempFrequency = pixelsMap[x][y].measuredFrequencies.get_bufferValues(cntFreq);
	        	if ((lowOctaveFilterFreq <= tempFrequency) && (tempFrequency < highOctaveFilterFreq)){ // We only care of the frequencies values in the octave.
    	        	for (int cntBnd = (tempTonesHistogram.length - 1); 0 < cntBnd; cntBnd--) {
    	        		if (tempTonesLowBoundaries[cntBnd] < tempFrequency) {
        	        		tempTonesHistogram[cntBnd]++;
        	                break;
        	            }
    	        	}
	        	}
	        }

        	// Again, we detect the bin of the maximum value and its position
	        //
	        int histTonesMax = 0;
	        int histTonesMaxIndex = 0;
	        for (int cnt = 0; cnt < tempTonesHistogram.length; cnt++) {
	            if (tempTonesHistogram[cnt] > histTonesMax) {
	                histTonesMax = tempTonesHistogram[cnt];
	                histTonesMaxIndex = cnt;
	            }
	        }
	        pixelsMap[x][y].presumedTone = pixelsMap[x][y].octave.tones[histTonesMaxIndex];
	        pixelsMap[x][y].pixelHSB[0] = pixelsMap[x][y].presumedTone.hue ; // Else, we will stay stuck with the initial hue...

            /* @author Adrien
             * Estimating the real value of the fundamental frequency of each (recently activated) pixel.
             * Are averaged only the values that fits in a (user) given range around the tone frequency.
             */
	        float lowFineFilterFreq  = (float) (pixelsMap[x][y].presumedTone.frequency / Math.pow(2.0, halfWidthOfAveragingWindow/12.0));
	        float highFineFilterFreq = (float) (pixelsMap[x][y].presumedTone.frequency * Math.pow(2.0, halfWidthOfAveragingWindow/12.0));

	        //System.out.println(String.format("lowFineFreq  = %.3f Hz\nhighFineFreq = %.3f Hz", lowFineFilterFreq, highFineFilterFreq)); // DEBUG : SEEMS TO BE WORKING

	        float tempAvgFreq = (float) 0.0;
	        int avgElemNumber = 0; // number of averaged elements
	        for (int cntFreq = 0; cntFreq < pixelsMap[x][y].measuredFrequencies.bufferSize; cntFreq++){
	        	float tempFrequency = pixelsMap[x][y].measuredFrequencies.get_bufferValues(cntFreq);
	        	if ((lowFineFilterFreq <= tempFrequency) && (tempFrequency <= highFineFilterFreq)){ // We only care of the frequencies values in the octave.
	        		tempAvgFreq += tempFrequency;
	        		avgElemNumber++;
	        	}
	        }
	        if (0 < avgElemNumber){
	        	tempAvgFreq /= avgElemNumber;
	        }
	        // Updating the relevant properties of the Pixel object
	        //System.out.println(String.format("tempAvgFreq = %.3f Hz", tempAvgFreq)); // DEBUG : SEEMS TO BE WORKING
	        pixelsMap[x][y].averageFrequency = tempAvgFreq;
	        pixelsMap[x][y].set_saturationCorrection();
        }

	return in; //@author Adrien added by Eclipse...
    }

    //private final GLUT glut = new GLUT();
    private final GLUT glutColorCode = new GLUT();
    //private final GLUT glutLastEventsAvg = new GLUT();

    public void annotate(float[][][] frame) {
    }

    /**
     * not used
     */
    public void annotate(Graphics2D g) {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

        if(!isFilterEnabled())
		 {
			return; // @author Adrien: what?
		}
        GL2 gl=drawable.getGL().getGL2();
        GL2 colorCodeGL = drawable.getGL().getGL2();
        GL2 lastEventsAvgGL = drawable.getGL().getGL2();

        if (gl == null) {
			return;
		}
        if (colorCodeGL == null) {
			return;
		}
        if (lastEventsAvgGL == null) {
			return;
		}

        /* @author Adrien
         * Some display stuff.
         * Some computations are done here because that might reduce the computational load.
         */
        float displayedAvg;
    	int displayAvgElemNumber;
    	int presumedToneCnt;
    	Octave presumedOctave;

        gl.glPointSize(4f);
        colorCodeGL.glPointSize(4f);
        lastEventsAvgGL.glPointSize(4f);
        try{
            gl.glBegin(GL.GL_POINTS);
            {
            	for (int cnt = 0; cnt < this.timestampsOfDisplayedPixels.bufferSize; cnt++){
            		//if (0 < this.timestampsOfDisplayedPixels.get_bufferValues(cnt)){ // else it means that this value was not modified since its initialization
                    int xCoordinate = (int) this.displayedPixelsX.get_bufferValues(cnt);
                    int yCoordinate = (int) this.displayedPixelsY.get_bufferValues(cnt);
                    pixelsMap[xCoordinate][yCoordinate].set_displayActive(true);
                    if (pixelsMap[xCoordinate][yCoordinate].get_displayActive() == true){ // Yes, for the moment, it is a useless test...
        				// We transmit the (RGB) color the pixel
            			gl.glColor3f(pixelsMap[xCoordinate][yCoordinate].pixelRGB[0],
                        			 pixelsMap[xCoordinate][yCoordinate].pixelRGB[1],
                        			 pixelsMap[xCoordinate][yCoordinate].pixelRGB[2]);
            			// We display the pixel
                        gl.glVertex2i(xCoordinate, yCoordinate);
                        this.pixelsMap[xCoordinate][yCoordinate].set_displayActive(false); // reset of the activeDisplay tag => indeed, for the moment, this tag is quite useless
                    }
                }
            }
            gl.glEnd();

            lastEventsAvgGL.glBegin(GL.GL_POINTS);
            {
            	displayedAvg = (float) 0.0;
            	displayAvgElemNumber = 0;
            	for (int tempCnt = 0; tempCnt < displayedPixelsX.bufferSize; tempCnt++){
            		int tempX = (int) displayedPixelsX.get_bufferValues(tempCnt);
            		int tempY = (int) displayedPixelsY.get_bufferValues(tempCnt);
            		if (0.0 < pixelsMap[tempX][tempY].averageFrequency) {
            			displayedAvg += pixelsMap[tempX][tempY].averageFrequency;
            			displayAvgElemNumber++;
    	        	}
    	        }
    	        if (0 < displayAvgElemNumber){
    	        	displayedAvg /= displayAvgElemNumber;
    	        }
    	        // We try to find the associated tone
    	        // Beware, maybe something weird might happen if displayedAvg = 0...
    	        presumedOctave = new Octave((int) Math.ceil(Math.log(displayedAvg/27.5)/Math.log(2.0)));
	        	presumedToneCnt = 0;
    	        for (int octCnt = 11; 0 < octCnt; octCnt--){
	        		if (presumedOctave.get_tonesLowBoundaries(octCnt) <= displayedAvg) {
	        			presumedToneCnt = octCnt;
	        			break;
	        		}
	        	}
            }
            lastEventsAvgGL.glEnd();
        } finally{
        }

        /*
         * There is something weird with the colors. It seems like if the provided values
         * were used only the next cycle...
         */

        // Displaying a color code of the different tone in an octave
        float vertical_position;
        for (int cnt = 0; cnt < 12; cnt++){
        	vertical_position = ((chip.getSizeY() + (cnt*8)) - 96); //(float) (240.0 - (float) (8*cnt)); //Sim - made the display positions dependent on chip size
	       	colorCodeGL.glRasterPos3f(chip.getSizeX() + 5, vertical_position, 0); // display position //Sim - made the display positions dependent on chip size
	       	colorCodeGL.glColor3f(colorsTonesInRGB[cnt][0], colorsTonesInRGB[cnt][1], colorsTonesInRGB[cnt][2]); // display color
	        glutColorCode.glutBitmapString(GLUT.BITMAP_HELVETICA_18, pixelsMap[0][0].octave.tones[cnt].name); // for getting the name, we pray that the Tone object was initialized for the pixel (0, 0)...
        }

        // Displaying values of the average of the last valid frequencies values and their presumed tone
        lastEventsAvgGL.glRasterPos3f(chip.getSizeX() + 15, chip.getSizeY() - 5, 0); // display position //Sim - made the display positions dependent on chip size
       	lastEventsAvgGL.glColor3f((float) 0.2, (float) 0.2, (float) 0.2); // display color
        glutColorCode.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("<f> = %.3f Hz", displayedAvg));
        lastEventsAvgGL.glRasterPos3f(chip.getSizeX() + 15, chip.getSizeY() - 13, 0); // display position //Sim - made the display positions dependent on chip size
        lastEventsAvgGL.glColor3f((float) 0.2, (float) 0.2, (float) 0.2); // display color
        glutColorCode.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("f_tone = %.3f Hz (%s%d)", presumedOctave.tones[presumedToneCnt].frequency,
        																								  presumedOctave.tones[presumedToneCnt].name,
        																								  presumedOctave.tones[presumedToneCnt].index));

    }

    @Override
    // @author Adrien: do not know what I do but Eclipse was yelling for this tag...
    synchronized public void resetFilter() {
        if (this.pixelsMap == null){
    		return;
    	}
    	this.pixelsMap = new Pixel[chip.getSizeX()][chip.getSizeY()]; // @author Adrien. Might be not necessary.
    	for (int cnt1=0; cnt1 < chip.getSizeX(); cnt1++){
    		for (int cnt2=0; cnt2 < chip.getSizeY(); cnt2++){
    			this.pixelsMap[cnt1][cnt2] = new Pixel(this.pixelBufferLength, this.timestampUnit, this.lowFrequencyRange, this.highFrequencyRange);
    		}
    	}
    	this.displayedPixelsX.reset_bufferValues();
    	this.displayedPixelsY.reset_bufferValues();
    	this.timestampsOfDisplayedPixels.reset_bufferValues();
    	this.lastValidFrequencies.reset_bufferValues();

    	// Concerns the color bar
    	float[] toneHSB = new float[3];
    	Arrays.fill(toneHSB, (float) 1.0);
		Color tempColorObject;
    	int tempIntRGB = 0;
    	for (int tempCnt = 0; tempCnt < 12; tempCnt++) {
    		toneHSB[0] = this.pixelsMap[0][0].octave.tones[tempCnt].hue; // only the hue changes for perfectly matched frequencies
    		tempIntRGB = Color.HSBtoRGB(toneHSB[0], toneHSB[1], toneHSB[2]);
    		tempColorObject = new Color(tempIntRGB);
    		this.colorsTonesInRGB[tempCnt][0] = (float) (tempColorObject.getRed()/255.0);
    		this.colorsTonesInRGB[tempCnt][1] = (float) (tempColorObject.getGreen()/255.0);
    		this.colorsTonesInRGB[tempCnt][2] = (float) (tempColorObject.getBlue()/255.0);
    	}
    }

    @Override
    // @author Adrien: do not know what I do but Eclipse was yelling for this tag...
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            initFilter();
        }
    }

    @Override
    // @author Adrien: do not know what I do but Eclipse was yelling for this tag...
    public void initFilter() {
        allocateObjects(chip);
    }

    // A ring buffer that store (float) values
    public class ringBuffer {
    	public int bufferSize;
        private float[] bufferValues = new float[this.bufferSize];
    	private int lastInsertedValueIndex;

    	// Minimal constructor with default value
    	public ringBuffer() {
    		this(5);
		}

    	// Generic constructor
    	public ringBuffer(int bufferSizeValue){
    		this.bufferSize = bufferSizeValue;
    		this.bufferValues = new float[this.bufferSize];
    		Arrays.fill(this.bufferValues, (float) 0.0); // initializes every buffer value to zero
    		this.lastInsertedValueIndex = 0;
    	}

    	float get_bufferValues(int cnt){
    		return this.bufferValues[cnt];
    	}

    	float get_lastInsertedValue(){
    		return this.bufferValues[this.lastInsertedValueIndex];
    	}

    	void set_bufferValues(int cnt, float value){
    		bufferValues[cnt] = value;
    	}

    	void reset_bufferValues() {
            Arrays.fill(this.bufferValues, (float) 0.0);
            this.lastInsertedValueIndex = -1;
        }

    	void insertValue(float value){
    		// Modulo operator to avoid overflowing the buffer location
    		if (this.lastInsertedValueIndex < (this.bufferSize-1)){
    			this.lastInsertedValueIndex++;
    		} else {
    			this.lastInsertedValueIndex = 0;
    		}
    		this.bufferValues[this.lastInsertedValueIndex] = value;
    	}

    	void removeLastInsertedValue(){
    		this.bufferValues[this.lastInsertedValueIndex] = (float) 0.0;
    		// Inverted modulo operator
    		if (this.lastInsertedValueIndex > 0){
    			this.lastInsertedValueIndex--;
    		} else {
    			this.lastInsertedValueIndex = this.bufferSize - 1;
    		}

    	}
    }

    /* @author Adrien
     * What can be done:
     * - use a nice (r_, g_, b_) object instead of this bruteforce method
     */
    public class Tone {
        public float frequency; // in Hz, e.g. 440
        public String name; // e.g. A
        public int index; // e.g. 4 as in A4 (<=> 440 Hz)
        public float hue, saturation, brightness; // HSL color code. All values are between 0 and 1 except for the hue

        // Constructor with default values
        public Tone(){
        	this((float) 440.0, "A", 4, (float) 180.0, (float) 1.0, (float) 0.5);
        }

        // (Almost) Generic constructor (saturation and brightness are set to their maximum value of 100)
        public Tone(float f, String letter, int n, float hueValue) {
        	this(f, letter, n, hueValue, (float) 1.0, (float) 1.0);
        }

        // Generic constructor
        public Tone(float f, String letter, int n, float hueValue, float satValue, float briValue) {
        	this.frequency = f;
            this.name = letter;
            this.index = n;
            this.hue = hueValue;
            this.saturation = satValue;
            this.brightness = briValue;
        }
    }

     public class Octave {
        public int index;

        private Tone[] tones;
    	private float[] tonesLowBoundaries;
        private float refFrequency; // frequency of the A tone
        private float reason; // geometrical reason between two consecutive tones

        // Minimal constructor with default values
        public Octave() { // octave of the C4 @ 440 Hz
        	this(4);
        }

        // (Almost) Generic constructor (saturation and brightness are set to their maximum value of 1.0)
        public Octave(int indexValue) {

        	this.tones = new Tone[12];
        	this.tonesLowBoundaries = new float[this.tones.length];

        	this.index = indexValue;
        	this.refFrequency = (float) (Math.pow(2.0, this.index)*27.5); // A0 is (exactly) @ 27.5 Hz
        	this.reason = (float) (Math.pow(2.0, 1.0/12.0));

        	this.tones[0]  = new Tone((float) Math.pow(this.reason, -9)*this.refFrequency, "C",  this.index, (float)   0.0/(float)360.0);
        	this.tones[1]  = new Tone((float) Math.pow(this.reason, -8)*this.refFrequency, "C#", this.index, (float)  30.0/(float)360.0);
        	this.tones[2]  = new Tone((float) Math.pow(this.reason, -7)*this.refFrequency, "D",  this.index, (float)  60.0/(float)360.0);
        	this.tones[3]  = new Tone((float) Math.pow(this.reason, -6)*this.refFrequency, "D#", this.index, (float)  90.0/(float)360.0);
        	this.tones[4]  = new Tone((float) Math.pow(this.reason, -5)*this.refFrequency, "E",  this.index, (float) 120.0/(float)360.0);
        	this.tones[5]  = new Tone((float) Math.pow(this.reason, -4)*this.refFrequency, "F",  this.index, (float) 150.0/(float)360.0);
        	this.tones[6]  = new Tone((float) Math.pow(this.reason, -3)*this.refFrequency, "F#", this.index, (float) 180.0/(float)360.0);
        	this.tones[7]  = new Tone((float) Math.pow(this.reason, -2)*this.refFrequency, "G",  this.index, (float) 210.0/(float)360.0);
        	this.tones[8]  = new Tone((float) Math.pow(this.reason, -1)*this.refFrequency, "G#", this.index, (float) 240.0/(float)360.0);
        	this.tones[9]  = new Tone(		 					   		this.refFrequency, "A",  this.index, (float) 270.0/(float)360.0);
        	this.tones[10] = new Tone((float)  Math.pow(this.reason, 1)*this.refFrequency, "A#", this.index, (float) 300.0/(float)360.0);
        	this.tones[11] = new Tone((float)  Math.pow(this.reason, 2)*this.refFrequency, "B",  this.index, (float) 330.0/(float)360.0);

        	for (int cnt = 0; cnt < 12; cnt++) {
        		this.tonesLowBoundaries[cnt] = (this.tones[cnt].frequency)/((float) Math.sqrt(this.reason));
        	}
        }

        Tone get_tones(int cnt){
        	return this.tones[cnt];
        }

        void set_tones(int cnt, Tone objectInstance){
        	this.tones[cnt] = objectInstance;
        }

        float get_tonesLowBoundaries(int cnt){
        	return this.tonesLowBoundaries[cnt];
        }

        void set_tonesLowBoundaries(int cnt, float value){
        	this.tonesLowBoundaries[cnt] = value;
        }
    }

    public class rangeOfOctaves {
        public float lowFrequencyBoundary;
        public float highFrequencyBoundary;

        private float refFrequency; // *A0* tone frequency
        private int numberOfOctaves;
        private int firstOctaveIndex;

        private float[] octavesLowBoundaries;
        private int[] octavesIndices;

        // Minimal constructor with default values
        public rangeOfOctaves() { // audible range (from 20 to 20000 Hz)
        	this((float) 20.0, (float) 20.0e3);
        }

        // Generic constructor
        public rangeOfOctaves(float fmin, float fmax) {
        	this.lowFrequencyBoundary  = fmin;
   			this.highFrequencyBoundary = fmax;

   			this.refFrequency = (float) 27.5; // *A0* tone, in Hz
   			this.numberOfOctaves = (int) (1 + (Math.log(this.highFrequencyBoundary/this.lowFrequencyBoundary)/Math.log(2.0)));
   			this.firstOctaveIndex = (int) Math.ceil(Math.log(this.lowFrequencyBoundary/this.refFrequency));

   			this.octavesLowBoundaries = new float[this.numberOfOctaves];
   			this.octavesIndices = new int[this.numberOfOctaves];

   			for (int cnt = 0; cnt < this.numberOfOctaves; cnt++) {
   	        	this.octavesLowBoundaries[cnt] = (float) (this.refFrequency*(Math.pow(2, (this.firstOctaveIndex + cnt))));
   	        	this.octavesIndices[cnt] = this.firstOctaveIndex + cnt;
	        }
        }

        int get_octavesIndex(int cnt){
        	return this.octavesIndices[cnt];
        }

        float get_octavesLowBoundaries(int cnt){
        	return this.octavesLowBoundaries[cnt];
        }

        void set_octavesLowBoundaries(int cnt, float value){
        	this.octavesLowBoundaries[cnt] = value;
        }
    }

    public class Pixel {

    	public int lastTimestamp; // in time stamp unit
    	public float lastPatternPeriod; // in seconds
    	public float averageFrequency; // in Hz
    	public ringBuffer measuredFrequencies;
    	public boolean otherPolarityOccurred;

    	public rangeOfOctaves octavesRange;
    	public Octave octave;
    	public Tone presumedTone;

    	private float Correctness; // characterizes how far for the presumed tone is the average frequency value.
    	private float SaturationCorrection; // tuning of the saturation value (in HSL space) that depends on the correctness of the average frequency with respect to the corresponding tone frequency.
    	private int LongIntRGB;
    	private Color ColorObjectRGB;

    	// Has a get or set method
    	private int xValue; // normally, should be useless
    	private int yValue; // idem
    	private float pixelTimestampUnit; // in seconds
    	private float frequencyLowBoundary;
    	private float frequencyHighBoundary;
    	private float[] pixelRGB;
    	private float[] pixelHSB;
    	private boolean displayActive;

    	// Minimal constructor with default values
    	public Pixel(){
    		this(5, (float) 1.0e-6, (float) 20.0, (float) 20.0e3);
    	}

    	// Partial constructor with default values
    	public Pixel(int bufferSize){
    		this(bufferSize, (float) 1.0e-6, (float) 20.0, (float) 20.0e3);
    	}

    	// (Almost) Generic constructor
    	public Pixel(int bufferSize, float timestampUnitValue, float lowFrequencyBoundaryValue, float highFrequencyBoundaryValue) {
    		this.lastTimestamp = 0;
    		this.lastPatternPeriod = (float) 0.0;
    		this.averageFrequency = (float) 0.0;
    		this.measuredFrequencies = new ringBuffer(bufferSize);
    		this.otherPolarityOccurred = false;

    		this.pixelTimestampUnit = timestampUnitValue;
    		this.frequencyLowBoundary = lowFrequencyBoundaryValue;
    		this.frequencyHighBoundary = highFrequencyBoundaryValue;

    		this.octavesRange = new rangeOfOctaves(this.frequencyLowBoundary, this.frequencyHighBoundary);
    		this.octave = new Octave((int) Math.ceil(Math.log(Math.sqrt(this.frequencyLowBoundary*this.frequencyHighBoundary)/27.5))); // beware, for the moment it might be out of the octaveRange boundaries...
    		this.presumedTone = this.octave.tones[9]; // "A" tone

    		this.Correctness = (float) 1.0; // Do not use the evaluateCorrectness method because of the division by zero that it would imply...

    		this.pixelRGB = new float[3];
    		this.pixelHSB = new float[3];

    		this.pixelHSB[0] = this.presumedTone.hue;
    		this.pixelHSB[1] = this.presumedTone.saturation;
    		this.pixelHSB[2] = this.presumedTone.brightness; // HSB values understandable by the ad-hoc Java routine
    		this.LongIntRGB  = Color.HSBtoRGB(this.pixelHSB[0], this.pixelHSB[1], this.pixelHSB[2]);
    		this.ColorObjectRGB = new Color(LongIntRGB);
    		this.displayActive = false;

    		// Initializing the color of the pixel to its brightest case
    		this.pixelRGB[0] = (float) (this.ColorObjectRGB.getRed()/255.0);
    		this.pixelRGB[1] = (float) (this.ColorObjectRGB.getGreen()/255.0);
    		this.pixelRGB[2] = (float) (this.ColorObjectRGB.getBlue()/255.0);
    	}

    	float evaluateCorrectness(){ // Correctness has to be 1.0 when <f> = f_tone and else to go toward 0.
        	float correctnessValue = (float) 0.0;
    		if (this.averageFrequency <= this.presumedTone.frequency){
    			correctnessValue = this.averageFrequency / this.presumedTone.frequency;
    		} else {
    			correctnessValue = this.presumedTone.frequency / this.averageFrequency;
    		}
    		return correctnessValue;
    	}

    	void set_displayActive(boolean value){
    		this.displayActive = value;
    	}

    	boolean get_displayActive(){
    		return this.displayActive;
    	}

    	void set_Correctness(float value){
    		this.Correctness = value;
    	}

    	void set_Correctness(){
    		this.Correctness = this.evaluateCorrectness();
    	}

    	float get_Correctness(){
    		return this.Correctness;
    	}

    	float evaluateSaturationCorrection(){
    		// We first update the Correctness variable
    		this.set_Correctness();
    		// We return a Saturation value that depends on this Correctness value
    		// Sat = a*Cor + b with Sat_max = 1.0 for Cor = 1.0 and Sat_min = 0.0 for Cor = 0.0
    		final float slope = (float) 1.0;
    		final float intercept = (float) 0.0;
    		return (slope*this.Correctness) + intercept;
    	}

    	void set_saturationCorrection(float value){
    		// Setting the Saturation value.
    		this.SaturationCorrection = value;
    		// Updating the parameters related to the color of the pixel.
    		this.pixelHSB[1] = this.SaturationCorrection;
    		this.LongIntRGB  = Color.HSBtoRGB(this.pixelHSB[0], this.pixelHSB[1], this.pixelHSB[2]);
    		this.ColorObjectRGB = new Color(LongIntRGB);
    		this.pixelRGB[0] = (float) (this.ColorObjectRGB.getRed()/255.0);
    		this.pixelRGB[1] = (float) (this.ColorObjectRGB.getGreen()/255.0);
    		this.pixelRGB[2] = (float) (this.ColorObjectRGB.getBlue()/255.0);
    	}

    	void set_saturationCorrection(){
    		float saturationCorrectionValue = this.evaluateSaturationCorrection();
    		this.set_saturationCorrection(saturationCorrectionValue);
    	}

    	float get_saturationCorrection(){
    		return this.SaturationCorrection;
    	}

    	int get_xValue(){
    		return this.xValue;
    	}

    	int get_yValue(){
    		return this.yValue;
    	}

    	float get_timestampValue(){
    		return this.lastTimestamp*this.pixelTimestampUnit;
    	}

    	void set_timestampValue(int value){
    		this.lastTimestamp = value;
    	}

    	/**
    	 * Not really necessary anymore but anyway...
    	 * The RGB values have to be between 0 and 1
    	 */
    	void set_pixelRGB(float rValue, float gValue, float bValue){
    		this.pixelRGB[0] = rValue;
    		this.pixelRGB[1] = gValue;
    		this.pixelRGB[2] = bValue;
    	}
    }
}