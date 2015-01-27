/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * Computes CNN from DAVIS APS frames.
 *
 * @author tobi
 */
@Description("Computes CNN from DAVIS APS frames")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class DavisDeepLearnCnnProcessor extends EventFilter2D implements PropertyChangeListener, FrameAnnotater {

    protected DeepLearnCnnNetwork net = null;
    private String lastFileName = getString("lastFileName", "LCRN_cnn.xml");
    private ApsFrameExtractor frameExtractor = new ApsFrameExtractor(chip);
    private boolean showActivations = getBoolean("showActivations", false);
    private boolean showInput = getBoolean("showInput", false);
    private boolean showKernels = getBoolean("showKernels", false);
    private boolean showOutputAsBarChart = getBoolean("showOutputAsBarChart", true);
    private float uniformWeight = getFloat("uniformWeight", 0);
    private float uniformBias = getFloat("uniformBias", 0);

    private JFrame imageDisplayFrame = null;
    public ImageDisplay inputImageDisplay;

    public DavisDeepLearnCnnProcessor(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(frameExtractor);
        setEnclosedFilterChain(chain);
        frameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);
        setPropertyTooltip("loadCNNNetworkFromXML", "Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m");
        setPropertyTooltip("setNetworkToUniformValues", "sets previously-loaded net to uniform values for debugging");

        initFilter();
    }

    /**
     * Loads a convolutional neural network (CNN) trained using DeapLearnToolbox
     * for Matlab (https://github.com/rasmusbergpalm/DeepLearnToolbox) that was
     * exported using Danny Neil's XML Matlab script cnntoxml.m.
     *
     */
    public void doLoadCNNNetworkFromXML() {
        JFileChooser c = new JFileChooser(lastFileName);
        FileFilter filt = new FileNameExtensionFilter("XML File", "xml");
        c.addChoosableFileFilter(filt);
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFileName = c.getSelectedFile().toString();
        putString("lastFileName", lastFileName);
        if (net == null) {
            net = new DeepLearnCnnNetwork();
        }
        net.loadFromXMLFile(c.getSelectedFile());

    }

    public void doSetNetworkToUniformValues() {
        if (net != null) {
            net.setNetworkToUniformValues(uniformWeight, uniformBias);
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        frameExtractor.filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        // if net was loaded before, load it now
        if (lastFileName != null) {
            File f = new File(lastFileName);
            if (f.exists() && f.isFile()) {
                net = new DeepLearnCnnNetwork();
                net.loadFromXMLFile(f);
            }
        }

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // new activationsFrame is available, process it
        if (net != null) {
            double[] frame = frameExtractor.getNewFrame(); // TODO currently a clone of vector, index is y * width + x, i.e. it marches along rows and then up
            if (frame == null || frame.length == 0 || frameExtractor.getWidth() == 0) {
                return;
            }
            float[] outputs = net.compute(frame, frameExtractor.getWidth());
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (showActivations) {
            if (net != null) {
                net.drawActivations();
            }
        }
        if (showKernels) {
            if (net != null) {
                net.drawKernels();
            }
        }
        if (showOutputAsBarChart) {
            drawOutput(gl);
        }
    }

    private void drawOutput(GL2 gl) {
        if (net == null || net.outputLayer == null) {
            return;
        }
        net.outputLayer.annotateHistogram(gl, chip.getSizeX(), chip.getSizeY());
//        System.out.println("maxActivatedUnit=" + net.outputLayer.maxActivatedUnit + " maxActivation=" + net.outputLayer.maxActivation);
    }

    private void drawActivations() {
    }

    /**
     * @return the showActivations
     */
    public boolean isShowActivations() {
        return showActivations;
    }

    /**
     * @return the showKernels
     */
    public boolean isShowKernels() {
        return showKernels;
    }

    /**
     * @param showKernels the showKernels to set
     */
    public void setShowKernels(boolean showKernels) {
        this.showKernels = showKernels;
    }

    /**
     * @param showActivations the showActivations to set
     */
    public void setShowActivations(boolean showActivations) {
        this.showActivations = showActivations;
    }

    /**
     * @return the showInput
     */
    public boolean isShowInput() {
        return showInput;
    }

    /**
     * @param showInput the showInput to set
     */
    public void setShowInput(boolean showInput) {
        this.showInput = showInput;
        getSupport().firePropertyChange("showInput", null, showInput);
    }

    /**
     * @return the showOutputAsBarChart
     */
    public boolean isShowOutputAsBarChart() {
        return showOutputAsBarChart;
    }

    /**
     * @param showOutputAsBarChart the showOutputAsBarChart to set
     */
    public void setShowOutputAsBarChart(boolean showOutputAsBarChart) {
        this.showOutputAsBarChart = showOutputAsBarChart;
        putBoolean("showOutputAsBarChart", showOutputAsBarChart);
    }

    private void checkDisplayFrame() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * @return the uniformWeight
     */
    public float getUniformWeight() {
        return uniformWeight;
    }

    /**
     * @param uniformWeight the uniformWeight to set
     */
    public void setUniformWeight(float uniformWeight) {
        this.uniformWeight = uniformWeight;
        putFloat("uniformWeight", uniformWeight);
    }

    /**
     * @return the uniformBias
     */
    public float getUniformBias() {
        return uniformBias;
    }

    /**
     * @param uniformBias the uniformBias to set
     */
    public void setUniformBias(float uniformBias) {
        this.uniformBias = uniformBias;
        putFloat("uniformBias", uniformBias);
    }

}
