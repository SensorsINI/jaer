/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.util.prefs.Preferences;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;

/**
 * Extends DavisDeepLearnCnnProcessor to add annotation graphics to show
 * steering decision.
 *
 * @author Tobi
 */
@Description("Displays Visualise steering ConvNet results; subclass of DavisDeepLearnCnnProcessor")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class VisualiseSteeringConvNet extends DavisDeepLearnCnnProcessor {

    private boolean hasBlendChecked = false;
    private boolean hasBlend = false;
    private boolean hideOutput = getBoolean("hideOutput", false);
    private boolean showAnalogDecisionOutput = getBoolean("hideOutput", false);

    public VisualiseSteeringConvNet(AEChip chip) {
        super(chip);
        setPropertyTooltip("showAnalogDecisionOutput", "shows output units as analog shading");
        setPropertyTooltip("hideOutput", "hides output units");
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if(hideOutput) return;
        if (net.outputLayer.activations != null) {
            // 0=left, 1=center, 2=right, 3=no target
            int decision = net.outputLayer.maxActivatedUnit;
            GL2 gl = drawable.getGL().getGL2();
            if (!hasBlendChecked) {
                hasBlendChecked = true;
                String glExt = gl.glGetString(GL.GL_EXTENSIONS);
                if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                    hasBlend = true;
                }
            }
            if (hasBlend) {
                try {
                    gl.glEnable(GL.GL_BLEND);
                    gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
                    gl.glBlendEquation(GL.GL_FUNC_ADD);
                } catch (GLException e) {
                    log.warning("tried to use glBlend which is supposed to be available but got following exception");
                    gl.glDisable(GL.GL_BLEND);
                    e.printStackTrace();
                    hasBlend = false;
                }
            }

            int third = chip.getSizeX() / 3;
            int sy = chip.getSizeY();
            if (showAnalogDecisionOutput) {
                for (int i = 0; i < 3; i++) {
                    int x0 = third * i;
                    int x1 = x0 + third;
                    float shade = .5f*net.outputLayer.activations[i];
                    gl.glColor4f(shade, 0, 0, .1f);
                    gl.glRecti(x0, 0, x1, sy);
                }

            } else if (decision < 3) {
                int x0 = third * decision;
                int x1 = x0 + third;
                float shade = .5f;
                gl.glColor4f(shade, 0, 0, .2f);
                gl.glRecti(x0, 0, x1, sy);
            }

        }
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

}
