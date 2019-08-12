/*
 * Copyright (C) 2018 Tobi Delbruck.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.humanpose;

import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip.SelectCamera;
import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.humanpose.AbstractDavisCNN;
//import ch.unizh.ini.jaer.projects.humanpose.DavisCNNPureJava;
import ch.unizh.ini.jaer.projects.humanpose.DavisClassifierCNNProcessor;
import ch.unizh.ini.jaer.projects.humanpose.TensorFlow;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.HotPixelFilter;
import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.DevelopmentStatus;

/**
 * @author Tobi, Gemma, Enrico
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class HumanPose2D extends DavisClassifierCNNProcessor implements FrameAnnotater {

    //private float confThreshold = getFloat("confThreshold", 0.3f);
    private float confThreshold;
    private float confThresholdUpper = getFloat("confThresholdUpper", 0.3f);
    private float confThresholdLower = getFloat("confThresholdLower", 0.3f);
    
    private int updateCounterMax = getInt("updateCounterMax", 5);
    private int camera = getInt("camera", 0);
    
    AEChipRenderer renderer = null;
    float xTarget = Float.NaN;
    float nbTarget = 0;
    float yTarget = Float.NaN;
    private ImageDisplay imageDisplay;
    private JFrame activationsFrame = null;
    private BackgroundActivityFilter backgroundActivityFilter;
    private HotPixelFilter hotPixel;
    float [][] instValueAndLocationMaxHeatmaps = new float[13][3]; // instantaneous CNN predictions
    float [][] valueAndLocationMaxHeatmaps = new float[13][3]; // final prediction after applying confidence threshold 
    
    
    String [] jointNames = {"head", "Rshoulder", "Lshoulder", "Relbow", 
                             "Lelbow", "Rhip", "Lhip", "Rhand", "Lhand", 
                             "Rknee", "Lknee", "Rfoot", "Lfoot"};
    
    // problem-specific. Some are redundant, output shapes are extracted at first CNN run.
    int sxInput = 346;
    int sxCNNInput = 344;
    int syInput = 260;
    int sxCNNOutput = 86;
    int syOutput = 65;
    int outputStride = 4;
    

    public HumanPose2D(AEChip chip) {
        super(chip);
        
        //Filter Chain
        FilterChain chain = getEnclosedFilterChain();
        //Add backgroundActivityFilter
        backgroundActivityFilter = new BackgroundActivityFilter(chip); 
        hotPixel = new HotPixelFilter(chip);
        chain.add(0, backgroundActivityFilter); 
        chain.add(1, hotPixel); 
        
        //remove ApsFrameExtractor (added in the Abstract)
        chain.remove(chain.get(2));
        
        chain.get(0).setFilterEnabled(true);
        chain.get(1).setFilterEnabled(true);
        chain.get(2).setFilterEnabled(true);
        setEnclosedFilterChain(chain);
            
        //Set parameters for DvsFramerSingleFrame
        ((DvsFramerSingleFrame) chain.get(2)).setDvsEventsPerFrame(7500); 
        ((DvsFramerSingleFrame) chain.get(2)).setDvsGrayScale(255); 
        //((DvsFramerSingleFrame) chain.get(2)).setFrameCutRight(1038);
        ((DvsFramerSingleFrame) chain.get(2)).setOutputImageHeight(syInput);
        ((DvsFramerSingleFrame) chain.get(2)).setOutputImageWidth(sxCNNInput);
        
        ((DvsFramerSingleFrame) chain.get(2)).setRangeNormalizeFrame(255); 

        ((DvsFramerSingleFrame) chain.get(2)).setRectifyPolarities(true);
        ((DvsFramerSingleFrame) chain.get(2)).setShowFrames(true);
        
        //set filter parameters
        setInputLayerName("input_1");
        setOutputLayerName("output0");
        setImageHeight(syInput);
        setImageWidth(sxCNNInput);
        //setSoftMaxOutput(false);
        
        //setPropertyTooltip("1b. Annotation", "confThreshold", "Sets the confidence threshold for joints update.");
        setPropertyTooltip("1b. Annotation", "confThresholdUpper", "Sets the confidence threshold for UPPER joints update.");
        setPropertyTooltip("1b. Annotation", "confThresholdLower", "Sets the confidence threshold for LOWER joints update.");
        
        setPropertyTooltip("1b. Annotation", "updateCounterMax", "Number of frames after which joint is no longer displayed if not updated.");
        setPropertyTooltip("1. Input", "camera", "Input camera for multicamera filter, otherwise leave it 0.");

        
        // initialize the final prediction to zero, both position and confidence.
        for (int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
            for (int j = 0; j < valueAndLocationMaxHeatmaps[0].length; j++) {
            valueAndLocationMaxHeatmaps[i][j] = Float.NaN; //0.0f;
            }
        }
    
    }
    
    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket<?> in1 = backgroundActivityFilter.filterPacket(in);
        EventPacket<?> in2 = hotPixel.filterPacket(in1);
        EventPacket<?> in3 = dvsFramer.filterPacket(in2);
        
        if (!addedPropertyChangeListener) {
            if (dvsFramer == null) {
                throw new RuntimeException("Null dvsSubsampler; this should not occur");
            } else {
                dvsFramer.getSupport().addPropertyChangeListener(DvsFramer.EVENT_NEW_FRAME_AVAILABLE, this);
            }
            addedPropertyChangeListener = true;        
        }
        
        if (apsDvsNet == null) {
            log.warning("null CNN; load one with the LoadApsDvsNetworkFromXML button");
//            return in;
        }
        // send DVS timeslice to convnet
        resetTimeLimiter();
        //            final int sizeX = chip.getSizeX();
        //            final int sizeY = chip.getSizeY();

        for (BasicEvent e : in3) {
            lastProcessedEventTimestamp = e.getTimestamp();
            PolarityEvent p = (PolarityEvent) e;
            if (dvsFramer != null) {
                dvsFramer.addEvent(p); // generates event when full, which processes it in propertyChange() which computes CNN
            }
            if (timeLimiter.isTimedOut()) {
                break; // discard rest of this packet
            }
        }
        return in3;

    }

    @Override
    protected void loadNetwork(File f) throws Exception {
        super.loadNetwork(f);
        if (apsDvsNet != null) {
            apsDvsNet.getSupport().addPropertyChangeListener(AbstractDavisCNN.EVENT_MADE_DECISION, this);
        }
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        processDecision(evt);

        if (evt.getPropertyName() == AbstractDavisCNN.EVENT_MADE_DECISION) {
            processDecision(evt);
        } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
            resetFilter();
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
      
        AEChipRenderer renderer = (AEChipRenderer) chip.getRenderer();
        GL2 gl = drawable.getGL().getGL2();
        
        if (apsDvsNet != null && apsDvsNet.getNetname() != null) {
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            //MultilineAnnotationTextRenderer.renderMultilineString(apsDvsNet.getNetname());     
            if ((measurePerformance == true) && (performanceString != null)) {
                MultilineAnnotationTextRenderer.renderMultilineString(performanceString);
                lastPerformanceString = performanceString;
            }

       
            instValueAndLocationMaxHeatmaps = apsDvsNet.getOutputLayer().getMaxActAndLocPerMap();
            
            // apply confidence threshold to CNN instantaneous predictions.
            // 
            for (int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
                
                if (i>8){ confThreshold=confThresholdLower; }
                else{     confThreshold = confThresholdUpper; }
                        
                if (instValueAndLocationMaxHeatmaps[i][0]>=confThreshold){ // confidence is in first position
                    
                    // the first position of valueAndLocationMaxHeatmaps[i] is not used for confidence
                    // (as done in instValueAndLocationMaxHeatmaps), but is used to store updateCounter
                    // that will be incremented when joint is not updated.
                    // When updateCounter reaches updateCounterMax, the joint is set back to nan.
                    valueAndLocationMaxHeatmaps[i][0] = 0;
                    for (int j = 1; j < valueAndLocationMaxHeatmaps[0].length; j++) {
                        valueAndLocationMaxHeatmaps[i][j] = instValueAndLocationMaxHeatmaps[i][j];
                    }
                }
                else{
                    valueAndLocationMaxHeatmaps[i][0]++;
                    if (valueAndLocationMaxHeatmaps[i][0] == updateCounterMax){
                        valueAndLocationMaxHeatmaps[i][0] = 0;
                        // TODO: in this case also the counter is set to nan, to avoid possible overflow if joint is never visible?
                        for (int j = 1; j < valueAndLocationMaxHeatmaps[0].length; j++) {
                            valueAndLocationMaxHeatmaps[i][j] = Float.NaN;
                        }
                    }
                }

            }
        }
        
        
       
        
        int camera_shift=sxInput*camera;
        
        
        
        // TODO: 
        // display skeleton only if head is visible;
        // display only those joints that have high enough confidence.
        
        gl.glColor3f(1, 1, 0);
        gl.glPointSize(4);
        gl.glBegin(GL.GL_POINTS);
        for (int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
            float xJoint = camera_shift + valueAndLocationMaxHeatmaps[i][2]*outputStride; 
            float yJoint = syInput - valueAndLocationMaxHeatmaps[i][1]*outputStride; 
            gl.glVertex2f(xJoint, yJoint);
        }
        gl.glEnd();
        
        
        gl.glLineWidth(4);
        gl.glColor3f(1, 1, 0);
        gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[7][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[7][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[3][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[3][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[1][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[1][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[0][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[0][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[2][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[2][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[1][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[1][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[5][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[5][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[6][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[6][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[2][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[2][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[4][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[4][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[8][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[8][1]*outputStride);
        gl.glEnd();
        
        gl.glColor3f(1, 0, 1);
        gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[11][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[11][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[9][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[9][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[5][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[5][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[6][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[6][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[10][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[10][1]*outputStride);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[12][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[12][1]*outputStride);
        gl.glEnd();
        
        float scale=MultilineAnnotationTextRenderer.getScale();
        TextRenderer textRenderer=MultilineAnnotationTextRenderer.getRenderer();
        for(int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
            // display only non-NaN names (check only on u, it is the same as v when NaN)
            if (!Float.isNaN(valueAndLocationMaxHeatmaps[i][1])){
               textRenderer.draw3D(jointNames[i], (int)(camera_shift + valueAndLocationMaxHeatmaps[i][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[i][1]*outputStride), 0, scale);
            }
        }
        
        /*
        textRenderer.draw3D("head", (int)(camera_shift + valueAndLocationMaxHeatmaps[0][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[0][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rshoulder", (int)(camera_shift + valueAndLocationMaxHeatmaps[1][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[1][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lshoulder", (int)(camera_shift + valueAndLocationMaxHeatmaps[2][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[2][1]*outputStride), 0, scale);
        textRenderer.draw3D("Relbow", (int)(camera_shift + valueAndLocationMaxHeatmaps[3][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[3][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lelbow", (int)(camera_shift + valueAndLocationMaxHeatmaps[4][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[4][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rhip", (int)(camera_shift + valueAndLocationMaxHeatmaps[5][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[5][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lhip", (int)(camera_shift + valueAndLocationMaxHeatmaps[6][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[6][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rhand", (int)(camera_shift + valueAndLocationMaxHeatmaps[7][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[7][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lhand", (int)(camera_shift + valueAndLocationMaxHeatmaps[8][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[8][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rknee", (int)(camera_shift + valueAndLocationMaxHeatmaps[9][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[9][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lknee", (int)(camera_shift + valueAndLocationMaxHeatmaps[10][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[10][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rfoot", (int)(camera_shift + valueAndLocationMaxHeatmaps[11][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[11][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lfoot", (int)(camera_shift + valueAndLocationMaxHeatmaps[12][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[12][1]*outputStride), 0, scale);
        */
        
        
        // output is heat map
        //renderer.resetAnnotationFrame(0.0f);
        //float[] output = apsDvsNet.getOutputLayer().getActivations();

        
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        if (renderer != null) { // might be null on startup, if initFilter is called from AEChip constructor
            renderer.setExternalRenderer(false);
        }
    }

    /**
     * Resets the filter
     *
     * @param yes true to reset
     */
    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes && renderer != null) {
            renderer.setExternalRenderer(false);
        }
    }

    private float getOutputValue(float[] output, int x, int y) {
       // final int width = 120, height = 90;
       // return output[x * height + y];
       return 0;
    }

    /**
     * @return the camera
     */
    public int getCamera() {
        return camera;
    }
    
    /**
     * @param camera the camera to set
    */
    public void setCamera(int camera) {
        SelectCamera selectedCamera = (SelectCamera)chip.getAeViewer().getJMenuBar().getMenu(7).getItem(0).getAction();
        selectedCamera.setDisplayCamera(camera);
        this.camera=camera;
    }
    
    /**
     * @return the confThreshold
     */
    //public float getConfThreshold() {
    //    return confThreshold;
    //}
    
    /**
     * @param confThreshold the confidence threshold to update joints
    */
    //public void setConfThreshold(float confThreshold) {
    //    this.confThreshold = confThreshold;
    //}
    
    
    
     /**
     * @return the confThresholdUpper
     */
    public float getConfThresholdUpper() {
        return confThresholdUpper;
    }
    
    /**
     * @param confThresholdUpper the confidence threshold to update Upper joints
    */
    public void setConfThresholdUpper(float confThresholdUpper) {
        this.confThresholdUpper = confThresholdUpper;
    }
     /**
     * @return the confThresholdLower
     */
    public float getConfThresholdLower() {
        return confThresholdLower;
    }
    
    /**
     * @param confThresholdLower the confidence threshold to update lower joints
    */
    public void setConfThresholdLower(float confThresholdLower) {
        this.confThresholdLower = confThresholdLower;
    }
    
    
    
    
    
    
    
    
    /**
     * @return the updateCounterMax
     */
    public int getUpdateCounterMax() {
        return updateCounterMax;
    }
    
    /**
     * @param updateCounterMax the max value of frames for which a joint not updated is shown.
    */
    public void setUpdateCounterMax(int updateCounterMax) {
        this.updateCounterMax = updateCounterMax;
    }
    
    private void processDecision(PropertyChangeEvent evt) {
         
        if (resHeatMap != null) {
            
            if(nbTarget > 0){
                xTarget /= nbTarget ;
                yTarget /= nbTarget ;
            }
            else{
                xTarget = -1;
                yTarget = -1;                
            }
            xTarget *= 2;
            yTarget *= 2;
            //System.out.println("xTarget = " + Float.toString(xTarget) + " , yTarget = " + Float.toString(yTarget));
            //log.info(String.format("x : %f, y : %f", xTarget, yTarget));

        }

    }

}
