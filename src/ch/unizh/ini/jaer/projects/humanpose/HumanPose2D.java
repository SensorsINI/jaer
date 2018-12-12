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
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.davis.HotPixelFilter;
import java.beans.PropertyChangeEvent;
import java.io.File;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
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

/**
 * @author Tobi, Gemma, Enrico
 */
@Description("<html>Human pose (joint) estimation with CNN from DHP19 dataset<br>See <a href=\"https://sites.google.com/view/dhp19/home\">DHP19 web site</a>")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class HumanPose2D extends DavisClassifierCNNProcessor implements FrameAnnotater {

    private float annotateAlpha = getFloat("annotateAlpha", 0.5f);
    private int camera = getInt("camera", 0);
    AEChipRenderer renderer = null;
    float xTarget = Float.NaN;
    float nbTarget = 0;
    float yTarget = Float.NaN;
    private ImageDisplay imageDisplay;
    private JFrame activationsFrame = null;
    private BackgroundActivityFilter backgroundActivityFilter;
    private HotPixelFilter hotPixel;
    float [][] valueAndLocationMaxHeatmaps = new float[13][3];

    public HumanPose2D(AEChip chip) {
        super(chip);
        
        //Filter Chain
        FilterChain chain= getEnclosedFilterChain();
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
        ((DvsFramerSingleFrame) chain.get(2)).setFrameCutRight(1038);
        ((DvsFramerSingleFrame) chain.get(2)).setOutputImageHeight(260);
        ((DvsFramerSingleFrame) chain.get(2)).setOutputImageWidth(344);
        ((DvsFramerSingleFrame) chain.get(2)).setShowFrames(true);
        
        //set filter parameters
        setInputLayerName("input_1");
        setOutputLayerName("output0");
        setImageHeight(260);
        setImageWidth(344);
        setSoftMaxOutput(false);
        
        //Add for heatmap (from foosball?)
        setPropertyTooltip("annotation", "annotateAlpha", "Sets the transparency for the heatmap display. ");
        setPropertyTooltip("1. Input", "camera", "Input camera for multicamera filter, otherwise leave it 0");

    }
    
    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        EventPacket<?> in1=backgroundActivityFilter.filterPacket(in);
        EventPacket<?> in2=hotPixel.filterPacket(in1);
        EventPacket<?> in3=dvsFramer.filterPacket(in2);
        
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
            MultilineAnnotationTextRenderer.renderMultilineString(apsDvsNet.getNetname());
            //if (measurePerformance && performanceString != null) {
            //    MultilineAnnotationTextRenderer.renderMultilineString(performanceString);
            //    lastPerformanceString = performanceString;
            //}
            
            valueAndLocationMaxHeatmaps = apsDvsNet.getOutputLayer().getMaxActAndLocPerMap();
            
        }
        
        int camera_shift=346*camera;
        
        gl.glColor3f(1, 1, 0);
        gl.glPointSize(4);
        gl.glBegin(GL.GL_POINTS);
        for (int i = 0; i < valueAndLocationMaxHeatmaps.length; i++) {
            float xJoint = camera_shift + valueAndLocationMaxHeatmaps[i][2]; 
            float yJoint = 260 - valueAndLocationMaxHeatmaps[i][1]; 
            gl.glVertex2f(xJoint, yJoint);
        }
        gl.glEnd();
        
        gl.glLineWidth(4);
        gl.glColor3f(1, 1, 0);
        gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[7][2], 260 - valueAndLocationMaxHeatmaps[7][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[3][2], 260 - valueAndLocationMaxHeatmaps[3][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[1][2], 260 - valueAndLocationMaxHeatmaps[1][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[0][2], 260 - valueAndLocationMaxHeatmaps[0][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[2][2], 260 - valueAndLocationMaxHeatmaps[2][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[1][2], 260 - valueAndLocationMaxHeatmaps[1][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[5][2], 260 - valueAndLocationMaxHeatmaps[5][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[6][2], 260 - valueAndLocationMaxHeatmaps[6][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[2][2], 260 - valueAndLocationMaxHeatmaps[2][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[4][2], 260 - valueAndLocationMaxHeatmaps[4][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[8][2], 260 - valueAndLocationMaxHeatmaps[8][1]);
        gl.glEnd();
        
        gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[11][2], 260 - valueAndLocationMaxHeatmaps[11][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[9][2], 260 - valueAndLocationMaxHeatmaps[9][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[5][2], 260 - valueAndLocationMaxHeatmaps[5][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[6][2], 260 - valueAndLocationMaxHeatmaps[6][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[10][2], 260 - valueAndLocationMaxHeatmaps[10][1]);
            gl.glVertex2f(camera_shift + valueAndLocationMaxHeatmaps[12][2], 260 - valueAndLocationMaxHeatmaps[12][1]);
        gl.glEnd();
        
        float scale=MultilineAnnotationTextRenderer.getScale();
        TextRenderer textRenderer=MultilineAnnotationTextRenderer.getRenderer();
        textRenderer.draw3D("head", (int)(camera_shift + valueAndLocationMaxHeatmaps[0][2]), (int)(260 - valueAndLocationMaxHeatmaps[0][1]), 0, scale);
        textRenderer.draw3D("Rshoulder", (int)(camera_shift + valueAndLocationMaxHeatmaps[1][2]), (int)(260 - valueAndLocationMaxHeatmaps[1][1]), 0, scale);
        textRenderer.draw3D("Lshoulder", (int)(camera_shift + valueAndLocationMaxHeatmaps[2][2]), (int)(260 - valueAndLocationMaxHeatmaps[2][1]), 0, scale);
        textRenderer.draw3D("Relbow", (int)(camera_shift + valueAndLocationMaxHeatmaps[3][2]), (int)(260 - valueAndLocationMaxHeatmaps[3][1]), 0, scale);
        textRenderer.draw3D("Lelbow", (int)(camera_shift + valueAndLocationMaxHeatmaps[4][2]), (int)(260 - valueAndLocationMaxHeatmaps[4][1]), 0, scale);
        textRenderer.draw3D("Rhip", (int)(camera_shift + valueAndLocationMaxHeatmaps[5][2]), (int)(260 - valueAndLocationMaxHeatmaps[5][1]), 0, scale);
        textRenderer.draw3D("Lhip", (int)(camera_shift + valueAndLocationMaxHeatmaps[6][2]), (int)(260 - valueAndLocationMaxHeatmaps[6][1]), 0, scale);
        textRenderer.draw3D("Rhand", (int)(camera_shift + valueAndLocationMaxHeatmaps[7][2]), (int)(260 - valueAndLocationMaxHeatmaps[7][1]), 0, scale);
        textRenderer.draw3D("Lhand", (int)(camera_shift + valueAndLocationMaxHeatmaps[8][2]), (int)(260 - valueAndLocationMaxHeatmaps[8][1]), 0, scale);
        textRenderer.draw3D("Rknee", (int)(camera_shift + valueAndLocationMaxHeatmaps[9][2]), (int)(260 - valueAndLocationMaxHeatmaps[9][1]), 0, scale);
        textRenderer.draw3D("Lknee", (int)(camera_shift + valueAndLocationMaxHeatmaps[10][2]), (int)(260 - valueAndLocationMaxHeatmaps[10][1]), 0, scale);
        textRenderer.draw3D("Rfoot", (int)(camera_shift + valueAndLocationMaxHeatmaps[11][2]), (int)(260 - valueAndLocationMaxHeatmaps[11][1]), 0, scale);
        textRenderer.draw3D("Lfoot", (int)(camera_shift + valueAndLocationMaxHeatmaps[12][2]), (int)(260 - valueAndLocationMaxHeatmaps[12][1]), 0, scale);
        
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
        setAnnotateAlpha(annotateAlpha);
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
     * @return the annotateAlpha
     */
    public float getAnnotateAlpha() {
        return annotateAlpha;
    }

    /**
     * @param annotateAlpha the annotateAlpha to set
     */
    public void setAnnotateAlpha(float annotateAlpha) {
        if (annotateAlpha > 1.0) {
            annotateAlpha = 1.0f;
        }
        if (annotateAlpha < 0.0) {
            annotateAlpha = 0.0f;
        }
        this.annotateAlpha = annotateAlpha;
        if (renderer != null && renderer instanceof AEFrameChipRenderer) {
            AEFrameChipRenderer frameRenderer = (AEFrameChipRenderer) renderer;
            frameRenderer.setAnnotateAlpha(annotateAlpha);
        }
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
