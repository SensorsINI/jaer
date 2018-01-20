/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.npp.AbstractDavisCNN;
import ch.unizh.ini.jaer.projects.npp.TargetLabeler;
import ch.unizh.ini.jaer.projects.npp.DavisCNNPureJava;
import ch.unizh.ini.jaer.projects.npp.DavisClassifierCNNProcessor;
import java.awt.Color;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.util.ColorHelper;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLException;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/* import can be generated automatically

/**
 * Computes heat map by running CNN using ROI over the frame.
 * @author hongjie & Christian
 */
@Description("Computes heat map by running CNN using ROI over the frame")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class HeatMapCNN extends DavisClassifierCNNProcessor{
    public static final String OUTPUT_AVAILBLE = "outputUpdated";

    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean processROI = getBoolean("processROI", false);
    private boolean showAnalogDecisionOutput = getBoolean("showAnalogDecisionOutput", false);
    private TargetLabeler targetLabeler = null;
    private int totalDecisions = 0, correct = 0, incorrect = 0;
    private float alpha = 0.5f;
    private final int strideX = 16;
    private final int strideY = 16;
    private float[] heatMap;
    private int outputX = 0, outputY = 0;  // Output location
    private double outputProbVal = 0; // The max probablity in the heatmap
    private ParticleFilterTracking tracker;

    public ParticleFilterTracking getTracker() {
        return tracker;
    }

    public void setTracker(ParticleFilterTracking tracker) {
        this.tracker = tracker;
    }
    private int filterx = 0, filtery = 0;  // Output location

//    private final AEFrameChipRenderer renderer;

    public HeatMapCNN(AEChip chip) {
        super(chip);
        setPropertyTooltip("showAnalogDecisionOutput", "shows output units as analog shading");
        setPropertyTooltip("hideOutput", "All the output units are hided");
        setPropertyTooltip("processROI", "Regions of Interest will be processed");

        FilterChain chain = new FilterChain(chip);
        targetLabeler = new TargetLabeler(chip); // used to validate whether descisions are correct or not
        // chain.add(targetLabeler);
        setEnclosedFilterChain(chain);
        int sx = chip.getSizeX()/strideX;
        int sy = chip.getSizeY()/strideY;
        heatMap = new float[15*11];
        Arrays.fill(heatMap, 0.0f);
        apsDvsNet.getSupport().addPropertyChangeListener(DavisCNNPureJava.EVENT_MADE_DECISION, this);
//        dvsNet.getSupport().addPropertyChangeListener(DavisCNNPureJava.EVENT_MADE_DECISION, this);
//        renderer = (AEFrameChipRenderer) chip.getRenderer();
    }
// initialization

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        targetLabeler.filterPacket(in);
        EventPacket out = super.filterPacket(in);
        return out;
    }

    private Boolean correctDescisionFromTargetLabeler(TargetLabeler targetLabeler, DavisCNNPureJava net) {
        if (targetLabeler.getTargetLocation() == null) {
            return null; // no location labeled for this time
        }
        Point p = targetLabeler.getTargetLocation().location;
        if (p == null) {
            if (net.outputLayer.getMaxActivatedUnit() == 3) {
                return true; // no target seen
            }
        } else {
            int x = p.x;
            int third = (x * 3) / chip.getSizeX();
            if (third == net.outputLayer.getMaxActivatedUnit()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        int sx = chip.getSizeX()/strideX;
        int sy = chip.getSizeY()/strideY;
        heatMap = new float[sx*sy];
        Arrays.fill(heatMap, 0.0f);
        totalDecisions = 0;
        correct = 0;
        incorrect = 0;
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes && !targetLabeler.hasLocations()) {
            Runnable r = new Runnable() {

                @Override
                public void run() {
                    targetLabeler.loadLastLocations();
                }
            };
            SwingUtilities.invokeLater(r);
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        targetLabeler.annotate(drawable);
        if (hideOutput) { // All the outputs (direct output and display output will be disabled)
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int third = chip.getSizeX() / 3;
        int sy = chip.getSizeY();
        // if ((apsDvsNet != null) && (apsDvsNet.outputLayer.activations != null) && isProcessAPSFrames()) {
            drawDecisionOutput(third, gl, sy, apsDvsNet, Color.RED);
        // }

//        if (dvsNet != null && dvsNet.outputLayer != null && dvsNet.outputLayer.activations != null && isProcessDVSTimeSlices()) {
//            drawDecisionOutput(third, gl, sy, dvsNet, Color.YELLOW);
//        }

        if (totalDecisions > 0) {
            float errorRate = (float) incorrect / totalDecisions;
            String s = String.format("Error rate %.2f%% (total=%d correct=%d incorrect=%d)\n", errorRate * 100, totalDecisions, correct, incorrect);
            MultilineAnnotationTextRenderer.renderMultilineString(s);
        }

    }

    private void drawDecisionOutput(int third, GL2 gl, int sy, AbstractDavisCNN net, Color color) {

        System.out.printf("max heat value is: %f\n", outputProbVal);
        try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
        }
        catch (final GLException e) {
                e.printStackTrace();
        }
        gl.glColor4f(1f, .1f, .1f, .25f);
        gl.glLineWidth(1f);
        gl.glRectf((int)outputX - 10, (int)outputY - 10, (int)outputX + 12, (int)outputY + 12);
    }

        public double getOutputProbVal() {
            return outputProbVal;
        }

        public int getOutputX() {
            return outputX;
        }

        public int getOutputY() {
            return outputY;
        }
    
    public int getHeatmapIdx(int x, int y){
        // int sizeY = chip.getSizeY()/strideX;
        return y+(x*9);    // Now the row numbers for heatmap is just 9 not 11 or 15, don't know the reason.
    }


    /**
     * @return the hideOutput
     */
    public boolean isHideOutput() {
        return hideOutput;
    }

    /**
     * @param hideOutput the hideOutput to set
     */
    public void setHideOutput(boolean hideOutput) {
        this.hideOutput = hideOutput;
        putBoolean("hideOutput", hideOutput);
    }

    /**
     * @return the showAnalogDecisionOutput
     */
    public boolean isShowAnalogDecisionOutput() {
        return showAnalogDecisionOutput;
    }

    /**
     * @param showAnalogDecisionOutput the showAnalogDecisionOutput to set
     */
    public void setShowAnalogDecisionOutput(boolean showAnalogDecisionOutput) {
        this.showAnalogDecisionOutput = showAnalogDecisionOutput;
        putBoolean("showAnalogDecisionOutput", showAnalogDecisionOutput);
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        if(! filterEnabled) {
            return;
        }
        if (evt.getPropertyName().equals(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE)) {
            if ((apsDvsNet != null) && processAPSFrames) {
                long startTime = 0;
                if (measurePerformance) {
                    startTime = System.nanoTime();
                }
                int dimx2 = apsDvsNet.getInputLayer().getWidth()/2;
                int dimy2 = apsDvsNet.getInputLayer().getHeight()/2;
                int idx = 0;

                tracker = this.getTracker(); // Get the current particle filter                

                // processROI is the flag to indicate the input of the heatMap
                if(processROI){
                   filterx = (int)tracker.getOutputX();
                   filtery = (int)tracker.getOutputY();
                   int processed_num = 4;
                   int [] centerx = new int[2]; 
                   int [] centery = new int[2]; 
                   centerx[0]=filterx-strideX/2; 
                   centerx[1]=filterx+strideX/2;
                   centery[0]=filtery-strideY/2; 
                   centery[1]=filtery+strideY/2;

                   for (int i=0; i< 2; i++ ){
                       for (int j = 0; j< 2; j++){
                           float[] outputs = apsDvsNet.processInputPatchFrame((AEFrameChipRenderer) (chip.getRenderer()), centerx[i], centery[j]);
                           heatMap[idx]=outputs[0];
                           idx++;
                       }
                    }      
                    updateOutput_ROI();
                } else {
                    for(int x = dimx2; x< (chip.getSizeX()-dimx2); x+= strideX){
                        for(int y = dimy2; y< (chip.getSizeY()-dimy2); y+= strideY){
                            float[] outputs = apsDvsNet.processInputPatchFrame((AEFrameChipRenderer) (chip.getRenderer()), x, y);
                            // apsDvsNet.drawActivations();
                            heatMap[idx]=outputs[0];
                            idx++;
                        }
                    }

                    updateOutput(); // Heatmap is updated, the output should also be updated.

                    if (measurePerformance) {
                        long dt = System.nanoTime() - startTime;
                        float ms = 1e-6f * dt;
                        float fps = 1e3f / ms;
                        log.info(String.format("Frame processing time: %.1fms (%.1f FPS)", ms, fps));
                    }
                }
            } else {
                DavisCNNPureJava net = (DavisCNNPureJava) evt.getNewValue();
                Boolean correctDecision = correctDescisionFromTargetLabeler(targetLabeler, net);
                if (correctDecision != null) {
                    totalDecisions++;
                    if (correctDecision) {
                        correct++;
                    } else {
                        incorrect++;
                    }
                }
            }
        }
    }

    public float[] getHeatMap() {
        return heatMap;
    }

    private void updateOutput() {
        int sizeX = chip.getSizeX()/strideX;
        int sizeY = chip.getSizeY()/strideY;
        float max = heatMap[0];
        int max_x_index = 0, max_y_index =0;
        
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                float heat = heatMap[getHeatmapIdx(x,y)];
                float hue = 3f-3f*heat;       
                if(heat > max) {
                    max = heat;  
                    max_x_index = x;
                    max_y_index = y;
                }
            }
        }            
       
        outputX = strideX * (max_x_index + 1) + strideX/2 - 1; 
        outputY = strideY * max_y_index + strideY/2 - 1; 
        outputProbVal = max;
        getSupport().firePropertyChange(HeatMapCNN.OUTPUT_AVAILBLE, null, this);
    }

        private void updateOutput_ROI() {
        int sizeX = chip.getSizeX()/strideX;
        int sizeY = chip.getSizeY()/strideY;
        float max = heatMap[0];
        int max_x_index = 0, max_y_index =0;
        
         for (int i=0; i< 2; i++ ){
                for (int j = 0; j< 2; j++){
                float heat = heatMap[2*i+j];
                float hue = 3f-3f*heat;       
                if(heat > max) {
                    max = heat;  
                    max_x_index = i;
                    max_y_index = j;
                }
            }
        }            
       
        outputX = filterx+ 16*(max_x_index%2)-8; 
        outputY = filtery+ 16*(max_y_index%2)-8;
        outputProbVal = max;
        getSupport().firePropertyChange(HeatMapCNN.OUTPUT_AVAILBLE, null, this);
    }

    /**
     * @return the processROI
     */
    public boolean isProcessROI() {
        return processROI;
    }

    /**
     * @param processROI the processROI to set
     */
    public void setProcessROI(boolean processROI) {
        this.processROI = processROI;
        putBoolean("processROI", processROI);

    }

}
