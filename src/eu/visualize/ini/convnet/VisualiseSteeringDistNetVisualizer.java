/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.awt.Color;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import eu.visualize.ini.convnet.DeepLearnCnnNetwork.OutputLayer;
import javax.swing.SwingUtilities;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Extends DavisDeepLearnCnnProcessor to add annotation graphics to show
 * steering decision.
 *
 * @author Tobi
 */
@Description("Displays Visualise steering+distance ConvNet output; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VisualiseSteeringDistNetVisualizer extends DavisDeepLearnCnnProcessor implements PropertyChangeListener {

    private boolean hideOutput = getBoolean("hideOutput", false);
    private TargetLabeler targetLabeler = null;
    private boolean printOutputsEnabled=false;

    private class DecodedTargetLocation {

        float x = Float.NaN, y = Float.NaN;
        boolean visible = false;

        public DecodedTargetLocation(float x, float y, boolean visible) {
            this.x = x;
            this.y = y;
            this.visible = visible;
        }

        public DecodedTargetLocation(DeepLearnCnnNetwork net) {
            if (net == null || net.outputLayer == null || net.outputLayer.activations == null || net.outputLayer.activations.length != 6) {
                throw new RuntimeException("null net or output layer or output wrong type");
            }
            float[] o = net.outputLayer.activations;
            int sx = getChip().getSizeX(), sy = getChip().getSizeY();
            /*
             function [visible,x,y]=decodeTargetLocation(o,width)
             if o(5) > o(6)
             visible=1;
             else
             visible=0;
             end

             x=width/2*(o(2)-o(1))+width/2;
             y=width/2*(o(4)-o(3))+width/2;
             */
            if (o[4] > o[5]) {
                visible = true;
                x = sx / 2 * (o[1] - o[0]) + sx / 2;
                y = sy / 2 * (o[3] - o[2]) + sy / 2;
            } else {
                visible = false;
            }
        }
    }

    public VisualiseSteeringDistNetVisualizer(AEChip chip) {
        super(chip);
        setPropertyTooltip("hideOutput", "hides output units");
        FilterChain chain = new FilterChain(chip);
        targetLabeler = new TargetLabeler(chip); // used to validate whether descisions are correct or not
        chain.add(targetLabeler);
        setEnclosedFilterChain(chain);
        apsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, this);
        dvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, this);
        String visualizer="Visualizer";
        setPropertyTooltip(visualizer, "printOutputsEnabled", "prints to console the network final output values");
        setPropertyTooltip(visualizer, "hideOutput", "hides the network output histogram");
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        targetLabeler.filterPacket(in);
        EventPacket out = super.filterPacket(in);
        return out;
    }

    private Boolean correctDescisionFromTargetLabeler(TargetLabeler targetLabeler, DeepLearnCnnNetwork net) {
        DecodedTargetLocation netLocation = new DecodedTargetLocation(net);
        if (targetLabeler.getTargetLocation() == null) {
            return null; // no location labeled for this time
        }
        Point p = targetLabeler.getTargetLocation().location;
        if (p == null) {

        } else {

        }
        return false;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();

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
        if (hideOutput) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int sy = chip.getSizeY();
        if (apsNet != null && apsNet.outputLayer != null && apsNet.outputLayer.activations != null && isProcessAPSFrames()) {
            drawDecisionOutput(gl, sy, apsNet, Color.RED);
        }
        if (dvsNet != null && dvsNet.outputLayer != null && dvsNet.outputLayer.activations != null && isProcessDVSTimeSlices()) {
            drawDecisionOutput(gl, sy, dvsNet, Color.YELLOW);
        }

    }

    private void drawDecisionOutput(GL2 gl, int sy, DeepLearnCnnNetwork net, Color color) {
        /*
         function showTarget(o,width,color);
         if nargin<3
         color='r';
         end

         [visible,x,y]=decodeTargetLocation(o,width);
         if visible==0, return; end

         r=2*(1+10*(y-width/2)/width); % indicate size of target by vertical position scaled somehow
         hold on;
         x = [x-r x+r x+r x-r];
         y = [y-r y-r y+r y+r];
         t = fill(x,y,color);
         alpha(t,0.2)
         hold off; 
         */
        DecodedTargetLocation t = new DecodedTargetLocation(net);
        if (t.visible == false) {
            return;
        }
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
        float[] cv = color.getColorComponents(null);
        final float brightness = .1f; // brightness scale

        gl.glColor4f(r, g, b,brightness);
        final float rd=chip.getSizeY()/10*(1-5*t.y/chip.getSizeY());
        gl.glRectf(t.x - rd, t.y - rd, t.x + rd, t.y + rd);
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() != DeepLearnCnnNetwork.EVENT_MADE_DECISION) {
            super.propertyChange(evt);
            if(isPrintOutputsEnabled()){
                float[] fa=apsNet.outputLayer.activations;
                System.out.print(targetLabeler.getCurrentFrameNumber()+" ");
                for(float f:fa){
                    System.out.print(String.format("%.5f ",f));
                }
                System.out.print("\n");
            }
        } else {
            DeepLearnCnnNetwork net = (DeepLearnCnnNetwork) evt.getNewValue();

        }
    }

    /**
     * @return the printOutputsEnabled
     */
    public boolean isPrintOutputsEnabled() {
        return printOutputsEnabled;
    }

    /**
     * @param printOutputsEnabled the printOutputsEnabled to set
     */
    public void setPrintOutputsEnabled(boolean printOutputsEnabled) {
        this.printOutputsEnabled = printOutputsEnabled;
    }

}
