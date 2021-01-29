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
import net.sf.jaer.graphics.DavisRenderer;
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
    
    private boolean showHeatmapNotSkeleton = getBoolean("showHeatmapNotSkeleton", false);
    private int showWhichHeatmap = getInt("showWhichHeatmap", -1);
    private boolean displaySkeletonEdges = getBoolean("displaySkeletonEdges", true);
        
    private int updateCounterMax = getInt("updateCounterMax", 5);
    private int camera = getInt("camera", 0);
    
    protected float heatmapAlpha = getFloat("heatmapAlpha", 0.8f);
    
    AEChipRenderer renderer = null;
//    DavisRenderer frameRenderer = null; // needed for display heatmap?
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
    
    int cameraShift=sxInput*camera; // for multicam setup
    
    // to display heatmap.
    float [][] heatmapJoints = new float[syOutput][sxCNNOutput];
    //int [][] heatmapInt = new int[syOutput][sxCNNOutput];
    //float [][] upsampledHeatmapJoints = new float[syInput][sxInput];
    float minValueOfMaxHeatmap = 1.5f;
    float MAXHEATMAPVAL;
    
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

        setPropertyTooltip("1. Input", "camera", "Input camera for multicamera filter, otherwise leave it 0.");

        setPropertyTooltip("2. Display", "showHeatmapNotSkeleton", "Display the heatmap instead of the skeleton.");
        setPropertyTooltip("2. Display", "showWhichHeatmap", "Select the heatmap to display. -1: sum of all.");

        
        //setPropertyTooltip("3a. Annotation", "confThreshold", "Sets the confidence threshold for joints update.");
        setPropertyTooltip("3a. Skeleton annotation", "displaySkeletonEdges", "Display or hide the skeleton edges.");
        setPropertyTooltip("3a. Skeleton annotation", "confThresholdUpper", "Sets the confidence threshold for UPPER joints update.");
        setPropertyTooltip("3a. Skeleton annotation", "confThresholdLower", "Sets the confidence threshold for LOWER joints update.");                
        setPropertyTooltip("3a. Skeleton annotation", "updateCounterMax", "Number of frames after which joint is no longer displayed if not updated.");
        
        setPropertyTooltip("3b. Heatmap annotation", "heatmapAlpha", "Set alpha value for the heatmap.");

        
        // initialize the final prediction to zero, both position and confidence.
        for (int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
            for (int j = 0; j < valueAndLocationMaxHeatmaps[0].length; j++) {
            valueAndLocationMaxHeatmaps[i][j] = Float.NaN; //0.0f;
            }
        }
    
    }
    
    @Override
    public synchronized EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        EventPacket<? extends BasicEvent> in1 = backgroundActivityFilter.filterPacket(in);
        EventPacket<? extends BasicEvent> in2 = hotPixel.filterPacket(in1);
        EventPacket<? extends BasicEvent> in3 = dvsFramer.filterPacket(in2);
        
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
        
        DavisRenderer frameRenderer = (DavisRenderer) chip.getRenderer(); // for heatmap
        
        
        if (apsDvsNet != null && apsDvsNet.getNetname() != null) {
            // display CNN latency/FPS. Commented out network name.
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 1f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            //MultilineAnnotationTextRenderer.renderMultilineString(apsDvsNet.getNetname());
            if ((measurePerformance == true) && (performanceString != null)) {
                MultilineAnnotationTextRenderer.renderMultilineString(performanceString);
                lastPerformanceString = performanceString;
            }
            
            apsDvsNet.setShowHeatmapNotSkeleton(showHeatmapNotSkeleton);
            
            if (showHeatmapNotSkeleton){
                heatmapJoints = apsDvsNet.getOutputLayer().getHeatmapCNNSize();
                // fill the upsampled map.
                
                // upsampledHeatmapJoints 
                
                // render the image. 
                //for (int yy=0; yy<syInput; yy++){
                //    for (int xx=0; xx<sxInput; xx++){ 
                        
                //    }
                //}
                
                
                // put the heatmap to INT
                /*for (int yy=0; yy<syOutput; yy++){
                    for (int xx=0; xx<sxCNNOutput; xx++){ 
                        if (heatmapJoints[yy][xx] > MAXHEATMAPVAL){
                            heatmapJoints[yy][xx] = MAXHEATMAPVAL;
                        }
                        heatmapInt[yy][xx] = (int) (heatmapJoints[yy][xx] / MAXHEATMAPVAL * 255.0f);
                    }
                }*/
                
                
            }
            else{ // for skeleton
                instValueAndLocationMaxHeatmaps = apsDvsNet.getOutputLayer().getMaxActAndLocPerMap();

                // apply confidence threshold to CNN instantaneous predictions.
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
                            for (int j = 1; j < valueAndLocationMaxHeatmaps[0].length; j++) {
                                valueAndLocationMaxHeatmaps[i][j] = Float.NaN;
                            }
                        }
                    }
                }
            }
        }
        

        
        if (showHeatmapNotSkeleton){
            // put to nan the skeleton?
            
            // output is heatmap
            
            
            ///////////////////////////////////////
        //if (isShowHeatmapNotSkeleton() ){//&& apsDvsNet.tmpMaxActAndLocPerMap != null) {

        //int sx = chip.getSizeX(), sy = chip.getSizeY();

        frameRenderer.setDisplayAnnotation(true);
        frameRenderer.setAnnotateAlpha(1);

        
        // Initialization of pixel alphas.
        for (int y = 0; y < syInput; y++) {
            for (int x = 0; x < sxInput; x++) { 
                frameRenderer.setAnnotateAlpha(x, y, heatmapAlpha);
                //frameRenderer.setAnnotateAlpha(x, y, 1f);
                //frameRenderer.setAnnotateAlpha(x, y, 1f);
                //frameRenderer.setAnnotateAlpha(x, y, 1f);
                
                //frameRenderer.setAnnotateAlpha(x, y, 0.4f);
            }
        }

        
        
        MAXHEATMAPVAL=0.0f; // hack to clamp max heatmap 
        for (int y = 0; y < syOutput; y++) {
            for (int x = 0; x < sxCNNOutput; x++) {
                if (heatmapJoints[y][x]>MAXHEATMAPVAL) {
                    MAXHEATMAPVAL = heatmapJoints[y][x];
                }
            }
        }      
        if (MAXHEATMAPVAL < minValueOfMaxHeatmap){
            MAXHEATMAPVAL = minValueOfMaxHeatmap;
        } // end hack
        
        
        //for (int y = 0; y < syInput; y++) {
        //    for (int x = 0; x < sxInput; x++) { 
        for (int y = 0; y < syOutput; y++) {
            for (int x = 0; x < sxCNNOutput; x++) {

                //int k = apsFrameExtractor.getIndex(x, y);

                int xFull0 = x*outputStride, yFull0 = y*outputStride;
                int xFull1 = xFull0+1, yFull1 = yFull0+1;
                int xFull2 = xFull0+2, yFull2 = yFull0+2;
                int xFull3 = xFull0+3, yFull3 = yFull0+3;


                int j0 = frameRenderer.getPixMapIndex(xFull0, yFull0);
                int j1 = frameRenderer.getPixMapIndex(xFull1, yFull1);
                int j2 = frameRenderer.getPixMapIndex(xFull2, yFull2);
                int j3 = frameRenderer.getPixMapIndex(xFull3, yFull3);

                //int j0 = frameRenderer.getPixMapIndex(x, y), j1 = frameRenderer.getPixMapIndex(x, y), j2 = frameRenderer.getPixMapIndex(x, y), j3 = frameRenderer.getPixMapIndex(x, y);

                //log.info(String.format("x : %d", j0));



                //log.info(String.format("x : %f, y : %f", xFull, yFull));

                //frameRenderer.setAnnotateValue(j, (heatmapJoints[y][x] / MAXHEATMAPVAL * 255.0f));

                frameRenderer.setAnnotateValue(j0, (heatmapJoints[syOutput-y-1][x]));// / MAXHEATMAPVAL));// * 255.0f));
                frameRenderer.setAnnotateValue(j1, (heatmapJoints[syOutput-y-1][x]));// / MAXHEATMAPVAL));// * 255.0f));
                frameRenderer.setAnnotateValue(j2, (heatmapJoints[syOutput-y-1][x]));// / MAXHEATMAPVAL));// * 255.0f));
                frameRenderer.setAnnotateValue(j3, (heatmapJoints[syOutput-y-1][x]));// / MAXHEATMAPVAL));// * 255.0f));



                /*if (blockedPixels[k]) {
                    frameRenderer.setAnnotateColorRGBA(j, filtered);
                } else {
                    frameRenderer.setAnnotateColorRGBA(j, unfiltered);
                }*/
            }
        }
        
        ///////////////////////////////////////////
            
        }
        else{
            
            frameRenderer.setDisplayAnnotation(false);
            
        
        //renderer.resetAnnotationFrame(0.0f);
        //float[] output = apsDvsNet.getOutputLayer().getActivations();
            
            
            // TODO: 
            // display skeleton only if head is visible;
            // display only those joints that have high enough confidence.

            gl.glColor3f(1, 1, 0);
            gl.glPointSize(4);
            gl.glBegin(GL.GL_POINTS);
            for (int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
                float xJoint = cameraShift + valueAndLocationMaxHeatmaps[i][2]*outputStride; 
                float yJoint = syInput - valueAndLocationMaxHeatmaps[i][1]*outputStride; 
                gl.glVertex2f(xJoint, yJoint);
            }
            gl.glEnd();

            if (displaySkeletonEdges){
                gl.glLineWidth(4);
                gl.glColor3f(1, 1, 0);
                gl.glBegin(GL.GL_LINE_STRIP);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[7][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[7][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[3][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[3][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[1][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[1][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[0][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[0][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[2][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[2][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[1][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[1][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[5][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[5][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[6][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[6][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[2][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[2][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[4][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[4][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[8][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[8][1]*outputStride);
                gl.glEnd();

                gl.glColor3f(1, 0, 1);
                gl.glBegin(GL.GL_LINE_STRIP);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[11][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[11][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[9][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[9][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[5][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[5][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[6][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[6][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[10][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[10][1]*outputStride);
                    gl.glVertex2f(cameraShift + valueAndLocationMaxHeatmaps[12][2]*outputStride, syInput - valueAndLocationMaxHeatmaps[12][1]*outputStride);
                gl.glEnd();
            }

            float scale=MultilineAnnotationTextRenderer.getScale();
            TextRenderer textRenderer=MultilineAnnotationTextRenderer.getRenderer();
            for(int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
                // display only non-NaN names (check only on u, it is the same as v when NaN)
                if (!Float.isNaN(valueAndLocationMaxHeatmaps[i][1])){
                   textRenderer.draw3D(jointNames[i], (int)(cameraShift + valueAndLocationMaxHeatmaps[i][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[i][1]*outputStride), 0, scale);
                }
            }

        }
        
        /*
        textRenderer.draw3D("head", (int)(cameraShift + valueAndLocationMaxHeatmaps[0][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[0][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rshoulder", (int)(cameraShift + valueAndLocationMaxHeatmaps[1][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[1][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lshoulder", (int)(cameraShift + valueAndLocationMaxHeatmaps[2][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[2][1]*outputStride), 0, scale);
        textRenderer.draw3D("Relbow", (int)(cameraShift + valueAndLocationMaxHeatmaps[3][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[3][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lelbow", (int)(cameraShift + valueAndLocationMaxHeatmaps[4][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[4][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rhip", (int)(cameraShift + valueAndLocationMaxHeatmaps[5][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[5][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lhip", (int)(cameraShift + valueAndLocationMaxHeatmaps[6][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[6][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rhand", (int)(cameraShift + valueAndLocationMaxHeatmaps[7][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[7][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lhand", (int)(cameraShift + valueAndLocationMaxHeatmaps[8][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[8][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rknee", (int)(cameraShift + valueAndLocationMaxHeatmaps[9][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[9][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lknee", (int)(cameraShift + valueAndLocationMaxHeatmaps[10][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[10][1]*outputStride), 0, scale);
        textRenderer.draw3D("Rfoot", (int)(cameraShift + valueAndLocationMaxHeatmaps[11][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[11][1]*outputStride), 0, scale);
        textRenderer.draw3D("Lfoot", (int)(cameraShift + valueAndLocationMaxHeatmaps[12][2]*outputStride), (int)(syInput - valueAndLocationMaxHeatmaps[12][1]*outputStride), 0, scale);
        */      
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
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter / Min-Max for --heatmapAlpha--">
    /**
     * alpha for displaying heatmap
     *
     * @return alpha value for the summed heatmap.
     */
    public float getHeatmapAlpha() {
        return this.heatmapAlpha;
    }

    /**
     * sets the alpha for displaying heatmap. If param is larger then getMaxHeatmapAlpha() or
     * smaller getMinHeatmapAlpha() the boundary value are used instead of param.
     * <p>
     * Fires a PropertyChangeEvent "heatmapAlpha"
     *
     * @see #getHeatmapAlpha
     * @param alpha for heatmap
     */
    public void setHeatmapAlpha(final float heatmapAlpha) {
        float setValue = heatmapAlpha;
        if (heatmapAlpha < getMinHeatmapAlpha()) {
            setValue = getMinHeatmapAlpha();
        }
        if (heatmapAlpha > getMaxHeatmapAlpha()) {
            setValue = getMaxHeatmapAlpha();
        }

        putFloat("heatmapAlpha", setValue);
        getSupport().firePropertyChange("heatmapAlpha", this.heatmapAlpha, setValue);
        this.heatmapAlpha = setValue;
    }

    public float getMinHeatmapAlpha() {
        return 0.0f;
    }

    public float getMaxHeatmapAlpha() {
        return 1.0f;
    }
    // </editor-fold>
    
    
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
     * @return the displaySkeletonEdges
     */
    public boolean isDisplaySkeletonEdges() {
        return displaySkeletonEdges;
    }

    /**
     * @param displaySkeletonEdges to display the skeleton edges.
     */
    public void setDisplaySkeletonEdges(boolean displaySkeletonEdges) {
        this.displaySkeletonEdges = displaySkeletonEdges;
    }
    
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
    
    
    
            
    /**
     * @return the showHeatmapNotSkeleton
     */
    public boolean isShowHeatmapNotSkeleton() {
        return showHeatmapNotSkeleton;
    }

    /**
     * @param showHeatmapNotSkeleton shows the heatmap instead of skeleton
     */
    public void setShowHeatmapNotSkeleton(boolean showHeatmapNotSkeleton) {
        this.showHeatmapNotSkeleton = showHeatmapNotSkeleton;
        //putBoolean("showHeatmapNotSkeleton", showHeatmapNotSkeleton);
    }

    
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter / Min-Max for --heatmapAlpha--">
    /**
     * Select which heatmap to display
     *
     * @return the index of heatmap to display. -1 for all.
     */
    public int getShowWhichHeatmap() {
        return this.showWhichHeatmap;
    }

    /**
     * sets the value to select the heatmap to display. 
     * If param is larger then getMaxHeatmapAlpha() or
     * smaller getMinHeatmapAlpha() the boundary value are used instead of param.
     * <p>
     * Fires a PropertyChangeEvent "heatmapAlpha"
     *
     * @see #getHeatmapAlpha
     * @param alpha for heatmap
     */
    public void setShowWhichHeatmap(final int showWhichHeatmap) {
        int setValue = showWhichHeatmap;
        if (showWhichHeatmap < getMinShowWhichHeatmap()) {
            setValue = getMinShowWhichHeatmap();
        }
        if (showWhichHeatmap > getMaxShowWhichHeatmap()) {
            setValue = getMaxShowWhichHeatmap();
        }

        putFloat("showWhichHeatmap", setValue);
        getSupport().firePropertyChange("showWhichHeatmap", this.showWhichHeatmap, setValue);
        this.showWhichHeatmap = setValue;
    }

    public int getMinShowWhichHeatmap() {
        return -1;
    }

    public int getMaxShowWhichHeatmap() {
        return 13;
    }
    // </editor-fold>
    
    

}
