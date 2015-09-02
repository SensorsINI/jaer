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
    private int totalDecisions = 0, correct = 0, incorrect = 0;

    public VisualiseSteeringDistNetVisualizer(AEChip chip) {
        super(chip);
        setPropertyTooltip("hideOutput", "hides output units");
        FilterChain chain = new FilterChain(chip);
        targetLabeler = new TargetLabeler(chip); // used to validate whether descisions are correct or not
        chain.add(targetLabeler);
        setEnclosedFilterChain(chain);
        apsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, this);
        dvsNet.getSupport().addPropertyChangeListener(DeepLearnCnnNetwork.EVENT_MADE_DECISION, this);
    }

    @Override
    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
        targetLabeler.filterPacket(in);
        EventPacket out = super.filterPacket(in);
        return out;
    }

    private Boolean correctDescisionFromTargetLabeler(TargetLabeler targetLabeler, DeepLearnCnnNetwork net) {
        if (targetLabeler.getTargetLocation() == null) {
            return null; // no location labeled for this time
        }
        Point p = targetLabeler.getTargetLocation().location;
        if (p == null) {
            if (net.outputLayer.maxActivatedUnit == 3) {
                return true; // no target seen
            }
        } else {
            int x = p.x;
            int third = (x * 3) / chip.getSizeX();
            if (third == net.outputLayer.maxActivatedUnit) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
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
        if (hideOutput) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        int third = chip.getSizeX() / 3;
        int sy = chip.getSizeY();
        if (apsNet != null && apsNet.outputLayer != null && apsNet.outputLayer.activations != null && isProcessAPSFrames()) {
            drawDecisionOutput(third, gl, sy, apsNet, Color.RED);
        }
        if (dvsNet != null && dvsNet.outputLayer != null && dvsNet.outputLayer.activations != null && isProcessDVSTimeSlices()) {
            drawDecisionOutput(third, gl, sy, dvsNet, Color.YELLOW);
        }

        if (totalDecisions > 0) {
            float errorRate = (float) incorrect / totalDecisions;
            String s = String.format("Error rate %.2f%% (total=%d correct=%d incorrect=%d)\n", errorRate * 100, totalDecisions, correct, incorrect);
            MultilineAnnotationTextRenderer.renderMultilineString(s);
        }

    }

    private void drawDecisionOutput(int third, GL2 gl, int sy, DeepLearnCnnNetwork net, Color color) {
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
        float[] a = net.outputLayer.activations;
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
        float[] cv = color.getColorComponents(null);
        final float brightness = .3f; // brightness scale
        for (int i = 0; i < 3; i++) {
            int x0 = third * i;
            int x1 = x0 + third;
            float shade = brightness * net.outputLayer.activations[i];
            gl.glColor3f((shade * r), (shade * g), (shade * b));
            gl.glRecti(x0, 0, x1, sy);
            gl.glRecti(x0, 0, x1, sy);
        }
        float shade = brightness * net.outputLayer.activations[3]; // no target
        gl.glColor3f((shade * r), (shade * g), (shade * b));
        gl.glRecti(0, 0, chip.getSizeX(), sy / 8);
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
        } else {
            DeepLearnCnnNetwork net = (DeepLearnCnnNetwork) evt.getNewValue();
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
