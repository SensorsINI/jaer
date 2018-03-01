/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package es.us.atc.jaer.chips.NodeBoard;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.Point2D.Float;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.logging.Level;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import eu.seebetter.ini.chips.DVSWithIntensityDisplayMethod;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.RetinaExtractor;
//import es.us.atc.jaer.chips.NodeBoard.ApsDvsEvent;
//import eu.seebetter.ini.chips.sbret10.SBret10.SBret10Config.*;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEvent.ReadoutType;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
//import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.util.filter.LowpassFilter2D;


    @Description("NodeBoard FrameGrabber 256x256 version 0.0")
public final class NBFG256 extends AETemporalConstastRetina {
    private final NBFG256DisplayMethod NBFG256DisplayMethod;

    /** Describes size of array of pixels on the chip, in the pixels address space */
    public static class PixelArray extends Rectangle {

        int pitch;

        /** Makes a new description. Assumes that Extractor already right shifts address to remove even and odd distinction of addresses.
         *
         * @param pitch pitch of pixels in raw AER address space
         * @param x left corner origin x location of pixel in its address space
         * @param y bottom origin of array in its address space
         * @param width width in pixels
         * @param height height in pixels.
         */
        public PixelArray(int pitch, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.pitch = pitch;
        }
    }
        public static final int POLMASK = 0x80,
            XMASK = 255 ,
            // so that y addresses don't overlap ADC bits and cause fake ADC events Integer.bitCount(POLMASK | XMASK),
            YMASK = 255 ;

    public static final PixelArray EntirePixelArray = new PixelArray(1, 0, 0, 256, 256);
    private FrameEventPacket frameEventPacket = new FrameEventPacket(ApsDvsEvent.class);
    private NBFG256DisplayMethod sbretDisplayMethod = null;
    private boolean displayIntensity;
    private int exposureB;
   // private int exposureC;
    private int frameTime;
    private int pixCnt, oldn;
    private boolean displayLogIntensityChangeEvents;
    private boolean ignoreReadout;
   // private boolean snapshot = false;
   // private boolean resetOnReadout = false;
    private IntensityFrameData frameData = new IntensityFrameData();
//    private SBret10Config config;

    /** Creates a new instance of cDVSTest20.  */
    public NBFG256() {
        setName("NBFG256");
        setEventClass(ApsDvsEvent.class);
        setSizeX(EntirePixelArray.width*EntirePixelArray.pitch);
        setSizeY(EntirePixelArray.height*EntirePixelArray.pitch);
        setNumCellTypes(2); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new NBFG256Extractor(this));

//        setBiasgen(config = new NBFG256.SBret10Config(this));

        displayIntensity = getPrefs().getBoolean("displayIntensity", false);
        displayLogIntensityChangeEvents = getPrefs().getBoolean("displayLogIntensityChangeEvents", false);

        //setRenderer((new NBFG256Renderer(this)));

        NBFG256DisplayMethod = new NBFG256DisplayMethod(this);
        getCanvas().addDisplayMethod(NBFG256DisplayMethod);
        getCanvas().setDisplayMethod(NBFG256DisplayMethod);

    }

//    @Override
//    public void onDeregistration() {
//        unregisterControlPanel();
//        getAeViewer().removeHelpItem(help1);
//    }
//    JComponent help1 = null;
//
//    @Override
//    public void onRegistration() {
//        registerControlPanel();
//        help1 = getAeViewer().addHelpURLItem("https://svn.ini.uzh.ch/repos/tobi/tretina/pcb/cDVSTest/cDVSTest.pdf", "cDVSTest PCB design", "shows pcb design");
//    }

//    private void registerControlPanel() {
//        try {
//            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
//            JPanel imagePanel = viewer.getImagePanel();
//			JFrame controlFrame = new JFrame("SBret10 display controls");
//            JPanel imagePanel = new JPanel();
//			imagePanel.add((displayControlPanel = new eu.seebetter.ini.chips.sbret10.SBret10DisplayControlPanel(this)), BorderLayout.SOUTH);
 //           imagePanel.revalidate();
//			controlFrame.getContentPane().add(imagePanel);
//			controlFrame.pack();
//			controlFrame.setVisible(true);
//        } catch (Exception e) {
//            log.log(Level.WARNING, "could not register control panel: {0}", e);
//        }
//    }

//    private void unregisterControlPanel() {
//        try {
//            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
//            JPanel imagePanel = viewer.getImagePanel();
//            imagePanel.remove(displayControlPanel);
 //           imagePanel.revalidate();
//        } catch (Exception e) {
//            log.log(Level.WARNING, "could not unregister control panel: {0}", e);
//        }
//    }

    /** Creates a new instance of cDVSTest10
     * @param hardwareInterface an existing hardware interface. This constructor is preferred. It makes a new cDVSTest10Biasgen object to talk to the on-chip biasgen.
     */
    public NBFG256(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

//    int pixcnt=0; // TODO debug
    /** The event extractor. Each pixel has two polarities 0 and 1.
     *
     * <p>
     *The bits in the raw data coming from the device are as follows.
     * <p>
     *Bit 0 is polarity, on=1, off=0<br>
     *Bits 1-9 are x address (max value 320)<br>
     *Bits 10-17 are y address (max value 240) <br>
     *<p>
     */
    public class NBFG256Extractor extends RetinaExtractor {

        private int firstFrameTs = 0;
        private short[] countX;
        private short[] countY;
   //     private int oldcnt,pixCnt=65535; // TODO debug
        boolean ignore = true;

        public NBFG256Extractor(NBFG256 chip) {
            super(chip);
            resetCounters();
        }


        private void resetCounters(){
            int numReadoutTypes = 1;
            if((countX == null) || (countY == null)){
                countX = new short[numReadoutTypes];
                countY = new short[numReadoutTypes];
            }
            //oldcnt=pixCnt;
            //if (pixCnt>=65535)
            pixCnt=0;
            Arrays.fill(countX, 0, numReadoutTypes, (short)0);
            Arrays.fill(countY, 0, numReadoutTypes, (short)0);
        }

//        private void lastADCevent(){
//            if (resetOnReadout){
//                config.nChipReset.set(true);
//            }
//            ignore = false;
//        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new FrameEventPacket(getChip().getEventClass());
            } else {
                out.clear();
            }
            if (in == null ) {
                return out;
            }
           // while (in.getNumEvents()<32768) {
           // }
            int n = in.getNumEvents(); //addresses.length;

            int[] datas = in.addresses;
            int[] timestamps = in.timestamps;
            OutputEventIterator outItr = out.outputIterator();

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps, including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
            int diffts=1000;
            int lastts=0;
            int t=0;
            for (int i = 0; i < n; i++) {  // TODO implement skipBy
                    int data = datas[i];
                    int data1;
                    if (i<(n-1)) {
						data1=datas[i+1];
					}
					else {
						data1=0;
					}
                    diffts=timestamps[i]-lastts;
                    lastts=timestamps[i];
                    if(((data & 0x7FFF) == 0x7FFF) && ((data1 & 0x7FFF) < 0x7FFF) && (diffts>10) ) {
                        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                        ignore = false;
                        System.out.println("SOF - pixcount: "+pixCnt);
                        resetCounters();
                        frameTime = timestamps[i] - firstFrameTs;
                        firstFrameTs = timestamps[i];
                        //diffts=timestamps[i]-lastts;
                        //lastts=timestamps[i];
                        e.setReadoutType(ReadoutType.SOF);
                        e.address=0;
                        e.x=0;
                        e.y=0;
                        countY[0]=0;
                        countX[0]=0;
                        //pixCnt=0;
                    } else if (!ignore) {
                        //if (pixCnt%256 < 128 && (pixCnt/256)%256<128) {
                        if (((data%256)>=0) && ((data%256)<64)) {
                            for (t=0;t<(64-(data%128));t++){
                                ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                                e.setReadoutType(ReadoutType.DVS);
                                e.setAdcSample(-1);//(short)data);//(data & 0xFF)*32); //data_l*16
                                e.timestamp = (timestamps[i]);
                                //e.address = pixCnt; //(data & 0xFF)*32; //data_l*16
                                e.polarity= ApsDvsEvent.Polarity.Off;
                                e.x= (short)(128-(pixCnt%128)); //countX[0];
                                e.y= (short)(128-((pixCnt/256)%128)); //countY[0];
                            }
                        }else if (((data%256)>64) && ((data%256)<128)) {
                            for (t=0;t<((data%128)-64);t++){
                                ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                                e.setReadoutType(ReadoutType.DVS);
                                e.setAdcSample(-1);//(short)data);//(data & 0xFF)*32); //data_l*16
                                e.timestamp = (timestamps[i]);
                                //e.address = pixCnt; //(data & 0xFF)*32; //data_l*16
                                e.polarity= ApsDvsEvent.Polarity.On;
                                e.x= (short)(128-(pixCnt%128)); //countX[0];
                                e.y= (short)(128-((pixCnt/256)%128)); //countY[0];
                            }
                        }

                        pixCnt++;
                        //if (pixCnt%256 < 128 && (pixCnt/256)%256<128) {
                        if (((data/256)>=0) && ((data/256)<64)) {
                            for (t=0;t<(64-((data/256)%128));t++) {
                                ApsDvsEvent e1 = (ApsDvsEvent) outItr.nextOutput();
                                e1.setAdcSample(-1);
                                e1.timestamp = (timestamps[i]);
                                //e1.address = pixCnt;
                                e1.polarity= ApsDvsEvent.Polarity.Off;
                                //e1.readoutType = ApsDvsEvent.Type.ResetRead;
                                e1.x= (short)(128-(pixCnt%128));
                                e1.y= (short)(128-((pixCnt/256)%128));
                                e1.setReadoutType(ReadoutType.DVS);
                            }
                        }else if (((data/256)>64) && ((data/256)<128)) {
                            for (t=0;t<(((data/256)%128)-64);t++) {
                                ApsDvsEvent e1 = (ApsDvsEvent) outItr.nextOutput();
                                e1.setAdcSample(-1); //(short)(data/256)); //((data & 0xFF00)/256)*32); //data_h*16
                                e1.timestamp = (timestamps[i]);
                                //e1.address = pixCnt;//((data & 0xFF00)/256)*32; //data_h*16
                                e1.polarity= ApsDvsEvent.Polarity.On;
                                //e1..readoutType = ApsDvsEvent.Type.ResetRead;
                                e1.x= (short)(128-(pixCnt%128)); //countX[0];
                                e1.y= (short)(128-((pixCnt/256)%128)); //countY[0];
                                e1.setReadoutType(ReadoutType.DVS);
                            }
                        }


                        //System.out.println("New ADC event: type "+sampleType+", x "+e.x+", y "+e.y);
                        pixCnt++;
                        if ((pixCnt>=65535) || (countX[0]>255)) {

                            ignore=true;
                            return out;
                        }
                    }
					else {
						pixCnt ++;
					}

                }
            oldn=n;
            return out;
        } // extractPacket
    } // extractor



    /**
     * @return the displayLogIntensity
     */
    public boolean isDisplayIntensity() {
        return displayIntensity;
    }

    /**
     * @return the displayLogIntensity
     */
//    public void takeSnapshot() {
//        snapshot = true;
//        config.adc.setAdcEnabled(true);
//    }

    /**
     * @param displayLogIntensity the displayLogIntensity to set
     */
    public void setDisplayIntensity(boolean displayIntensity) {
        this.displayIntensity = displayIntensity;
        getPrefs().putBoolean("displayIntensity", displayIntensity);
        getAeViewer().interruptViewloop();
    }

    /**
     * @return the displayLogIntensityChangeEvents
     */
    public boolean isDisplayLogIntensityChangeEvents() {
        return displayLogIntensityChangeEvents;
    }

    /**
     * @param displayLogIntensityChangeEvents the displayLogIntensityChangeEvents to set
     */
    public void setDisplayLogIntensityChangeEvents(boolean displayLogIntensityChangeEvents) {
        this.displayLogIntensityChangeEvents = displayLogIntensityChangeEvents;
        getPrefs().putBoolean("displayLogIntensityChangeEvents", displayLogIntensityChangeEvents);
        getAeViewer().interruptViewloop();
    }

    /**
     * @return the ignoreReadout
     */
    public boolean isIgnoreReadout() {
        return ignoreReadout;
    }

    /**
     * @param displayLogIntensityChangeEvents the displayLogIntensityChangeEvents to set
     */
    public void setIgnoreReadout(boolean ignoreReadout) {
        this.ignoreReadout = ignoreReadout;
        getPrefs().putBoolean("ignoreReadout", ignoreReadout);
        getAeViewer().interruptViewloop();
    }

    /**
     * Displays data from SeeBetter test chip SeeBetter10/11.
     * @author Tobi
     */
    public class NBFG256DisplayMethod extends DVSWithIntensityDisplayMethod {

        private TextRenderer renderer = null;
        private TextRenderer exposureRenderer = null;

        public NBFG256DisplayMethod(NBFG256 chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            if (renderer == null) {
                renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 18), true, true);
                renderer.setColor(1, .2f, .2f, 0.4f);
            }
            if (exposureRenderer == null) {
                exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12), true, true);
                exposureRenderer.setColor(1, 1, 1, 1);
            }
            super.display(drawable);
            GL2 gl = drawable.getGL().getGL2();
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            // draw boxes around arrays

            rect(gl, 0, 0, 256, 256, "NodeBoard FrameGrabber 256x256");
        }

        private void rect(GL2 gl, float x, float y, float w, float h, String txt) {
            gl.glPushMatrix();
            gl.glTranslatef(-.5f, -.5f, 0);
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(x, y);
            gl.glVertex2f(x + w, y);
            gl.glVertex2f(x + w, y + h);
            gl.glVertex2f(x, y + h);
            gl.glEnd();
            // label arrays
            if (txt != null) {
                renderer.begin3DRendering();
                renderer.draw3D(txt, x, y, 0, .4f); // x,y,z, scale factor
                renderer.end3DRendering();
                if(displayIntensity){
                    exposureRenderer.begin3DRendering();
                    String frequency = "";
                    if(frameTime>0){
                         frequency = "("+((float)1000000/frameTime)+" Hz)";
                    }
                    String expC = "";
//                    if(config.useC.isSet()){
//                         expC = " ms, exposure 2: "+(float)exposureC/1000;
//                    }
                    exposureRenderer.draw3D("exposure 1: "+((float)exposureB/1000)+expC+" ms, frame period: "+((float)frameTime/1000)+" ms "+frequency, x, h, 0, .4f); // x,y,z, scale factor
                    exposureRenderer.end3DRendering();
                }

            }
            gl.glPopMatrix();
        }
    }

    public IntensityFrameData getFrameData() {
        return frameEventPacket.getFrameData();
    }

    /** Extends EventPacket to add the log intensity frame data */
    public class FrameEventPacket extends EventPacket {

        public FrameEventPacket(Class eventClass) {
            super(eventClass);
        }

        /**
         * @return the frameData
         */
        public IntensityFrameData getFrameData() {
            return frameData;
        }

        @Override
        public boolean isEmpty() {
            if (!frameData.isNewData() && super.isEmpty()) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "FrameEventPacket{" + "frameData=" + frameData + " " + super.toString() + '}';
        }
    }

    /**
     * Renders complex data from SeeBetter chip.
     *
     * @author tobi
     */
    public final class NBFG256Renderer extends RetinaRenderer {

        private NBFG256 cDVSChip = null;
        private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, greener={0,1,0}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
//        private final float[] brighter = {0, 1, 0}, darker = {1, 0, 0};
        private int sizeX = 1;
        private LowpassFilter2D agcFilter = new LowpassFilter2D();  // 2 lp values are min and max log intensities from each frame
        private boolean agcEnabled;
        /** PropertyChange */
        public static final String AGC_VALUES = "AGCValuesChanged";
        /** PropertyChange when value is changed */
        public static final String APS_INTENSITY_GAIN = "apsIntensityGain", APS_INTENSITY_OFFSET = "apsIntensityOffset";
        /** Control scaling and offset of display of log intensity values. */
        int apsIntensityGain, apsIntensityOffset;

        public NBFG256Renderer(NBFG256 chip) {
            super(chip);
            cDVSChip = chip;
            agcEnabled = chip.getPrefs().getBoolean("agcEnabled", false);
            setAGCTauMs(chip.getPrefs().getFloat("agcTauMs", 1000));
            apsIntensityGain = chip.getPrefs().getInt("apsIntensityGain", 1);
            apsIntensityOffset = chip.getPrefs().getInt("apsIntensityOffset", 0);
        }

        /** Overridden to make gray buffer special for bDVS array */
        @Override
        protected void resetPixmapGrayLevel(float value) {
            checkPixmapAllocation();
            final int n = 2 * chip.getNumPixels();
            boolean madebuffer = false;
            if ((grayBuffer == null) || (grayBuffer.capacity() != n)) {
                grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
                madebuffer = true;
            }
            if (madebuffer || (value != grayValue)) {
                grayBuffer.rewind();
                for (int y = 0; y < sizeY; y++) {
                    for (int x = 0; x < sizeX; x++) {
//                        if(displayLogIntensityChangeEvents){
//                            grayBuffer.put(0);
//                            grayBuffer.put(0);
//                            grayBuffer.put(0);
//                        } else {
                            grayBuffer.put(grayValue);
                            grayBuffer.put(grayValue);
//                            grayBuffer.put(grayValue);
//                        }
                    }
                }
                grayBuffer.rewind();
            }
            System.arraycopy(grayBuffer.array(), 0, pixmap.array(), 0, n);
            pixmap.rewind();
            pixmap.limit(n);
//        pixmapGrayValue = grayValue;
        }

        @Override
        public synchronized void render(EventPacket packet) {

            checkPixmapAllocation();
            resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel

            if (packet == null) {
                return;
            }
            this.packet = packet;
            if (packet.getEventClass() != ApsDvsEvent.class) {
                log.log(Level.WARNING, "wrong input event class, got {0} but we need to have {1}", new Object[]{packet.getEventClass(), ApsDvsEvent.class});
                return;
            }
            float[] pm = getPixmapArray();
            sizeX = chip.getSizeX();
            //log.info("pm : "+pm.length+", sizeX : "+sizeX);
            if (!accumulateEnabled) {
                resetFrame(.5f);
            }
            //String eventData = "NULL";
            boolean putADCData=displayIntensity && !getAeViewer().isPaused(); // don't keep reputting the ADC data into buffer when paused and rendering packet over and over again
            String event = "";
            try {
                step = 1f / (colorScale);
                for (Object obj : packet) {
                    ApsDvsEvent e = (ApsDvsEvent) obj;
                    //eventData = "address:"+Integer.toBinaryString(e.address)+"( x: "+Integer.toString(e.x)+", y: "+Integer.toString(e.y)+"), data "+Integer.toBinaryString(e.adcSample)+" ("+Integer.toString(e.adcSample)+")";
                    //System.out.println("Event: "+eventData);
                    if (putADCData && e.isApsData()) { // hack to detect ADC sample events
                        // ADC 'event'
                        frameData.putEvent(e);
                        //log.info("put "+e.toString());
                    } else if (!e.isApsData()) {
                        // real AER event
                        int type = e.getType();
                        if(frameData.useDVSExtrapolation){
                            frameData.updateDVScalib(e.x, e.y, type==0);
                        }
                        if(displayLogIntensityChangeEvents){
                            if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for large pixels
                                int xs = xsel, ys = ysel;
                                if ((e.x == xs) && (e.y == ys)) {
                                    playSpike(type);
                                }
                            }
                            int x = e.x, y = e.y;
                            switch (e.polarity) {
                                case On:
                                    changePixel(x, y, pm, brighter, step);
                                    break;
                                case Off:
                                    changePixel(x, y, pm, darker, step);
                                    break;
                            }
                        }
                    }
                }
                if (displayIntensity) {
                    int minADC = Integer.MAX_VALUE;
                    int maxADC = Integer.MIN_VALUE;
                    for (int y = 0; y < EntirePixelArray.height; y++) {
                        for (int x = 0; x < EntirePixelArray.width; x++) {
                            //event = "ADC x "+x+", y "+y;
                            int count = frameData.get(x, y);
                            if (agcEnabled) {
                                if (count < minADC) {
                                    minADC = count;
                                } else if (count > maxADC) {
                                    maxADC = count;
                                }
                            }
                            float v = adc01normalized(count);
                            float[] vv = {v, v, v};
                            changePixel(x, y, pm, vv, 1);
                        }
                    }
                    if (agcEnabled && ((minADC > 0) && (maxADC > 0))) { // don't adapt to first frame which is all zeros
                        Float filter2d = agcFilter.filter(minADC, maxADC, frameData.getTimestamp());
//                        System.out.println("agc minmax=" + filter + " minADC=" + minADC + " maxADC=" + maxADC);
                        getSupport().firePropertyChange(AGC_VALUES, null, filter2d); // inform listeners (GUI) of new AGC min/max filterd log intensity values
                    }

                }
                autoScaleFrame(pm);
            } catch (IndexOutOfBoundsException e) {
                log.log(Level.WARNING, "{0}: ChipRenderer.render(), some event out of bounds for this chip type? Event: {1}", new Object[]{e.toString(), event});//log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type? Event: "+eventData);
            }
            pixmap.rewind();
        }

        /** Changes scanned pixel value according to scan-out order
         *
         * @param ind the pixel to change, which marches from LL corner to right, then to next row up and so on. Physically on chip this is actually from UL corner.
         * @param f the pixmap RGB array
         * @param c the colors
         * @param step the step size which multiplies each color component
         */
        private void changeCDVSPixel(int ind, float[] f, float[] c, float step) {
            float r = c[0] * step, g = c[1] * step, b = c[2] * step;
            f[ind] += r;
            f[ind + 1] += g;
            f[ind + 2] += b;
        }

        /** Changes pixmap location for pixel affected by this event.
         * x,y refer to space of pixels
         */
        private void changePixel(int x, int y, float[] f, float[] c, float step) {
            int ind = 3* (x + (y * sizeX));
            changeCDVSPixel(ind, f, c, step);
        }

        public void setDisplayLogIntensityChangeEvents(boolean displayIntensityChangeEvents) {
            cDVSChip.setDisplayLogIntensityChangeEvents(displayIntensityChangeEvents);
        }

        public void setDisplayIntensity(boolean displayIntensity) {
            cDVSChip.setDisplayIntensity(displayIntensity);
        }

        public boolean isDisplayLogIntensityChangeEvents() {
            return cDVSChip.isDisplayLogIntensityChangeEvents();
        }

        public boolean isDisplayIntensity() {
            return cDVSChip.isDisplayIntensity();
        }

        private float adc01normalized(int count) {
            float v;
            if (!agcEnabled) {
                v = (float) ((apsIntensityGain*count)+apsIntensityOffset) / (float) 256;
            } else {
                Float filter2d = agcFilter.getValue2D();
                float offset = filter2d.x;
                float range = (filter2d.y - filter2d.x);
                v = ((count - offset)) / range;
//                System.out.println("offset="+offset+" range="+range+" count="+count+" v="+v);
            }
            if (v < 0) {
                v = 0;
            } else if (v > 1) {
                v = 1;
            }
            return v;
        }

        public float getAGCTauMs() {
            return agcFilter.getTauMs();
        }

        public void setAGCTauMs(float tauMs) {
            if (tauMs < 10) {
                tauMs = 10;
            }
            agcFilter.setTauMs(tauMs);
            chip.getPrefs().putFloat("agcTauMs", tauMs);
        }

        /**
         * @return the agcEnabled
         */
        public boolean isAgcEnabled() {
            return agcEnabled;
        }

        /**
         * @param agcEnabled the agcEnabled to set
         */
        public void setAgcEnabled(boolean agcEnabled) {
            this.agcEnabled = agcEnabled;
            chip.getPrefs().putBoolean("agcEnabled", agcEnabled);
        }

        void applyAGCValues() {
            Float f = agcFilter.getValue2D();
            setApsIntensityOffset(agcOffset());
            setApsIntensityGain(agcGain());
        }

        private int agcOffset() {
            return (int) agcFilter.getValue2D().x;
        }

        private int agcGain() {
            Float f = agcFilter.getValue2D();
            float diff = f.y - f.x;
            if (diff < 1) {
                return 1;
            }
            int gain = (int) (256 / (f.y - f.x));
            return gain;
        }

        /**
         * Value from 1 to MAX_ADC. Gain of 1, offset of 0 turns full scale ADC to 1. Gain of MAX_ADC makes a single count go full scale.
         * @return the apsIntensityGain
         */
        public int getApsIntensityGain() {
            return apsIntensityGain;
        }

        /**
         * Value from 1 to MAX_ADC. Gain of 1, offset of 0 turns full scale ADC to 1.
         * Gain of MAX_ADC makes a single count go full scale.
         * @param apsIntensityGain the apsIntensityGain to set
         */
        public void setApsIntensityGain(int apsIntensityGain) {
            int old = this.apsIntensityGain;
            if (apsIntensityGain < 1) {
                apsIntensityGain = 1;
            } else if (apsIntensityGain > 256) {
                apsIntensityGain = 256;
            }
            this.apsIntensityGain = apsIntensityGain;
            chip.getPrefs().putInt("apsIntensityGain", apsIntensityGain);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(APS_INTENSITY_GAIN, old, apsIntensityGain);
        }

        /**
         * Value subtracted from ADC count before gain multiplication. Ranges from 0 to MAX_ADC.
         * @return the apsIntensityOffset
         */
        public int getApsIntensityOffset() {
            return apsIntensityOffset;
        }

        /**
         * Sets value subtracted from ADC count before gain multiplication. Clamped between 0 to MAX_ADC.
         * @param apsIntensityOffset the apsIntensityOffset to set
         */
        public void setApsIntensityOffset(int apsIntensityOffset) {
            int old = this.apsIntensityOffset;
            if (apsIntensityOffset < 0) {
                apsIntensityOffset = 0;
            } else if (apsIntensityOffset > 256) {
                apsIntensityOffset = 256;
            }
            this.apsIntensityOffset = apsIntensityOffset;
            chip.getPrefs().putInt("apsIntensityOffset", apsIntensityOffset);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(APS_INTENSITY_OFFSET, old, apsIntensityOffset);
        }
    }
/**
     *
     * Holds the frame of log intensity values to be display for a chip with log intensity readout.
     * Applies calibration values to get() the values and supplies put() and resetWriteCounter() to put the values.
     *
     * @author Tobi
     */
    public static enum Read{A, B, C, DIFF_B, DIFF_C, HDR, LOG_HDR};

    public class IntensityFrameData {

        /** The scanner is 240wide by 180 high  */
        public final int WIDTH = EntirePixelArray.width, HEIGHT = EntirePixelArray.height; // getWidthInPixels is BDVS pixels not scanner registers
        private final int NUMSAMPLES = WIDTH * HEIGHT;
        private int timestamp = 0; // timestamp of starting sample
        private float[] data = new float[NUMSAMPLES];
        private float[] bDiffData = new float[NUMSAMPLES];
        private float[] cDiffData = new float[NUMSAMPLES];
        private float[] hdrData = new float[NUMSAMPLES];
        private float[] oldData = new float[NUMSAMPLES];
        private float[] displayData = new float[NUMSAMPLES];
        private float[] onCalib = new float[NUMSAMPLES];
        private float[] offCalib = new float[NUMSAMPLES];
        private float onGain = 0f;
        private float offGain = 0f;
        private int[] onCount = new int[NUMSAMPLES];
        private int[] offCount = new int[NUMSAMPLES];
        private int[] aData, bData, cData;
        private float minC, maxC, maxB;
        /** Readers should access the current reading buffer. */
        private int writeCounterA = 0;
        private int writeCounterB = 0;
        private boolean useDVSExtrapolation = getPrefs().getBoolean("useDVSExtrapolation", false);
        private boolean invertADCvalues = getPrefs().getBoolean("invertADCvalues", true); // true by default for log output which goes down with increasing intensity
        private Read displayRead = Read.DIFF_B;

        public IntensityFrameData() {
            minC = Integer.MAX_VALUE;
            maxB = 0;
            maxC = 0;
            aData = new int[NUMSAMPLES];
            bData = new int[NUMSAMPLES];
            cData = new int[NUMSAMPLES];
            Arrays.fill(aData, 0);
            Arrays.fill(bData, 0);
            Arrays.fill(cData, 0);
            Arrays.fill(hdrData, 0);
            Arrays.fill(bDiffData, 0);
            Arrays.fill(cDiffData, 0);
            Arrays.fill(onCalib, 0);
            Arrays.fill(offCalib, 0);
            Arrays.fill(onCount, 0);
            Arrays.fill(offCount, 0);
            Arrays.fill(data, 0);
            Arrays.fill(displayData, 0);
            Arrays.fill(oldData, 0);
        }

        private int index(int x, int y){
            final int idx = y + (HEIGHT * x);
            return idx;
        }

        /** Gets the sample at a given pixel address (not scanner address)
         *
         * @param x pixel x from left side of array
         * @param y pixel y from top of array on chip
         * @return  value from ADC
         */
        private int outputData;

        public int get(int x, int y) {
            final int idx = index(x,y); // values are written by row for each column (row parallel readout in this chip, with columns addressed one by one)
            switch (displayRead) {
                case A:
                    outputData = aData[idx];
                    break;
                case B:
                    outputData = bData[idx];
                    break;
                case C:
                    outputData = cData[idx];
                    break;
                case DIFF_B:
                default:
                    if(useDVSExtrapolation){
                        outputData = (int)displayData[idx];
                    }else{
                        outputData = (int)bDiffData[idx];
                    }
                    break;
                case DIFF_C:
                    if(useDVSExtrapolation){
                        outputData = (int)displayData[idx];
                    }else{
                        outputData = (int)cDiffData[idx];
                    }
                    break;
                case HDR:
                case LOG_HDR:
                    if(useDVSExtrapolation){
                        outputData = (int)displayData[idx];
                    }else{
                        outputData = (int)hdrData[idx];
                    }
                    break;
            }
            if (invertADCvalues) {
                return (256 - outputData);
            } else {
                return outputData;
            }
        }

        private void putEvent(ApsDvsEvent e) {
            if(!e.isApsData()) {
				return;
			}
            if(e.isStartOfFrame()) {
                resetWriteCounter();
                setTimestamp(e.timestamp);
            }
//            putNextSampleValue(e.adcSample, e.readoutType, index(e.x, e.y));
        }

        private void putNextSampleValue(int val, ApsDvsEvent.ReadoutType type, int index) {
            float value = 0;
            float valueB = 0;
            switch(type){
//                case C:
//                    if (index >= cData.length) {
//                    //log.info("buffer overflowed - missing start frame bit? index "+index);
//                        return;
//                    }
//                    if (index == NUMSAMPLES-1) {
//                        updateDVSintensities();
//                    }
//                    cData[index] = val;
//                    cDiffData[index] = aData[index]-cData[index];
//                    if(config.useC.isSet()){
//                        if(displayRead==Read.LOG_HDR){
//                            value = (float)Math.log(cDiffData[index]/exposureC);
//                            valueB = (float)Math.log(bDiffData[index]/exposureB);
//                        }else{
//                            value = cDiffData[index]/exposureC;
//                            valueB = bDiffData[index]/exposureB;
//                        }
//                        if(value<minC){
//                            minC = value;
//                        }
//                        if(value>maxC){
//                            maxC = value;
//                        }
//                        if(valueB > value){
//                            value = valueB;
//                        }
////                       value = value + bDiffData[index]/(2*exposureB);
//                        hdrData[index]=256/(maxB-minC)*(value-minC);
////                        if(maxC-(1000/exposureB) > value){
////                            hdrData[index]=MAX_ADC/(maxB-minC)*(value-minC);
////                        }else{
////                            value = bDiffData[index]/exposureB;
////                            hdrData[index]=MAX_ADC/(maxB-minC)*(value-minC);
////                        }
//                    }
//                    break;
                case SignalRead:
                    if (index >= bData.length) {
        //            log.info("buffer overflowed - missing start frame bit?");
                        return;
                    }
//                    if (index == NUMSAMPLES-1) {
//                        updateDVSintensities();
//                    }
                    bData[index] = val;
                    bDiffData[index] = aData[index]-bData[index];
//                    if(!config.useC.isSet()){
//                        data[index] = bDiffData[index];
//                    }
//                    if(config.useC.isSet()){
//                        if(displayRead==Read.LOG_HDR){
//                            value = (float)Math.log(bDiffData[index]/exposureB);
//                        }else{
//                            value = bDiffData[index]/exposureB;
//                        }
//                        if(value>maxB){
//                            maxB = value;
//                        }
//                    }
                    break;
                case ResetRead:
                default:
                    if (index >= aData.length) {
        //            log.info("buffer overflowed - missing start frame bit?");
                        return;
                    }
                    aData[index] = val;
                    break;
            }
        }

        private void updateDVSintensities(){
            float difference = 1;
            for(int i = 0; i < NUMSAMPLES; i++){
                difference = data[i]-oldData[i];
                if(difference != 0){
                    if((onCount[i] > 0) && (oldData[i] > 0)){
                        onGain = (float)((99*onGain)+((difference-(Math.log(oldData[i])*offGain*offCount[i]))/onCount[i]))/100;
                    } else if((offCount[i] > 0) && (oldData[i] > 0)){
                        offGain = (float)((99*offGain)+((difference+(Math.log(oldData[i])*onGain*onCount[i]))/offCount[i]))/100;
                    }
                }
            }
            System.arraycopy(data, 0, displayData, 0, NUMSAMPLES);
            System.arraycopy(data, 0, oldData, 0, NUMSAMPLES);
            Arrays.fill(onCount, 0);
            Arrays.fill(offCount, 0);
        }

        public void updateDVScalib(int x, int y, boolean isOn){
            final int idx = index(x,y);
            if(isOn){
                onCount[idx]++;
                //System.out.println("On event - data: "+displayData[idx]+" + calib: "+onGain+" => "+onGain*Math.log(displayData[idx]));
                displayData[idx] = (float)(displayData[idx]+(onGain*Math.log(displayData[idx])));
                //System.out.println("displayData: "+displayData[idx]);
            } else {
                offCount[idx]++;
                //System.out.println("Off event - data: "+displayData[idx]+" + calib: "+offGain+" => "+onGain*Math.log(displayData[idx]));
                displayData[idx] = (float)(displayData[idx]+(offGain*Math.log(displayData[idx])));
                //System.out.println("displayData: "+displayData[idx]);
            }
        }

        /**
         * @return the timestamp
         */
        public int getTimestamp() {
            return timestamp;
        }

        /**
         * Sets the buffer timestamp.
         * @param timestamp the timestamp to set
         */
        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
        }

        /**
         * @return the useDVSExtrapolation
         */
        public boolean isUseDVSExtrapolation() {
            return useDVSExtrapolation;
        }

        /**
         * @param useDVSExtrapolation the useOffChipCalibration to set
         */
        public void setUseDVSExtrapolation(boolean useDVSExtrapolation) {
            this.useDVSExtrapolation = useDVSExtrapolation;
            getPrefs().putBoolean("useDVSExtrapolation", useDVSExtrapolation);
        }

        private int getMean(int[] dataIn) {
            int mean = 0;
            for (int element : dataIn) {
                mean += element;
            }
            mean = mean / dataIn.length;
            return mean;
        }

        private void subtractMean(int[] dataIn, int[] dataOut) {
            int mean = getMean(dataIn);

            for (int i = 0; i < dataOut.length; i++) {
                dataOut[i] = dataIn[i] - mean;
            }
        }

        public void setDisplayRead(Read displayRead){
            this.displayRead = displayRead;
        }

        public Read getDisplayRead(){
            return displayRead;
        }

        /**
         * @return the invertADCvalues
         */
        public boolean isInvertADCvalues() {
            return invertADCvalues;
        }

        /**
         * @param invertADCvalues the invertADCvalues to set
         */
        public void setInvertADCvalues(boolean invertADCvalues) {
            this.invertADCvalues = invertADCvalues;
            getPrefs().putBoolean("invertADCvalues", invertADCvalues);
        }

        public boolean isNewData() {
            return true; // dataWrittenSinceLastSwap; // TODO not working yet
        }

        @Override
        public String toString() {
            return "IntensityFrameData{" + "WIDTH=" + WIDTH + ", HEIGHT=" + HEIGHT + ", NUMSAMPLES=" + NUMSAMPLES + ", timestamp=" + timestamp + ", writeCounter=" + writeCounterA + '}';
        }

        public void resetWriteCounter() {
            minC = Integer.MAX_VALUE;
            maxC = 0;
            maxB = 0;
            writeCounterA = 0;
            writeCounterB = 0;
        }
        final String CALIB1_KEY = "IntensityFrameData.calibData1", CALIB2_KEY = "IntensityFrameData.calibData2";

        private void putArray(int[] array, String key) {
            if ((array == null) || (key == null)) {
                log.warning("null array or key");
                return;
            }
            try {
                // Serialize to a byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos);
                out.writeObject(array);
                out.close();

                // Get the bytes of the serialized object
                byte[] buf = bos.toByteArray();
                getPrefs().putByteArray(key, buf);
            } catch (Exception e) {
                log.warning(e.toString());
            }

        }

        private int[] getArray(String key) {
            int[] ret = null;
            try {
                byte[] bytes = getPrefs().getByteArray(key, null);
                if (bytes != null) {
                    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                    ret = (int[]) in.readObject();
                    in.close();
                }
            } catch (Exception e) {
                log.warning(e.toString());
            }
            return ret;
        }

    }

}
