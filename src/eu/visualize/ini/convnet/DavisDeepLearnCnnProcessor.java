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

    private String lastFileName = getString("lastFileName", "LCRN_cnn.xml");
    private DeepLearnCnnNetwork net = null;
    private ApsFrameExtractor frameExtractor = new ApsFrameExtractor(chip);
    private boolean showOutputLayerHistogram = getBoolean("showOutputLayerHistogram", true);
    private boolean showActivations = getBoolean("showActivations", false);
    private boolean showInput = getBoolean("showInput", false);
    private boolean showOutputAsHistogram = getBoolean("showOutputAsHistogram", true);

    private JFrame imageDisplayFrame = null;
    public ImageDisplay inputDisplay;

    public DavisDeepLearnCnnProcessor(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadCNNNetworkFromXML", "Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m");
        FilterChain chain = new FilterChain(chip);
        chain.add(frameExtractor);
        setEnclosedFilterChain(chain);
        frameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);
        imageDisplayFrame = new JFrame("DavisDeepLearnCnnProcessor");
        imageDisplayFrame.setPreferredSize(new Dimension(200, 200));

        imageDisplayFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                setShowInput(false);
                setShowActivations(false);
            }
        });
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
        net = new DeepLearnCnnNetwork();
        net.loadFromXMLFile(c.getSelectedFile());

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
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // new frame is available, process it
        if (net != null) {
            float[] frame = frameExtractor.getDisplayBuffer(); // TODO currently a clone of vector, index is y * width + x, i.e. it marches along rows and then up
            if (frame == null || frame.length == 0 || frameExtractor.getWidth() == 0) {
                return;
            }
            float[] outputs = net.compute(frame, frameExtractor.getWidth());
        }

    }

    /**
     * @return the showOutputLayerHistogram
     */
    public boolean isShowOutputLayerHistogram() {
        return showOutputLayerHistogram;
    }

    /**
     * @param showOutputLayerHistogram the showOutputLayerHistogram to set
     */
    public void setShowOutputLayerHistogram(boolean showOutputLayerHistogram) {
        this.showOutputLayerHistogram = showOutputLayerHistogram;
        putBoolean("showOutputLayerHistogram", showOutputLayerHistogram);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (showInput) {
            drawInput(drawable);
        }
        if (showActivations) {
            drawActivations(drawable);
        }
        if (showOutputAsHistogram) {
            if(net==null || net.outputLayer==null) return;
            net.outputLayer.draw(gl, chip.getSizeX(), chip.getSizeY());
        }
    }

    private void drawInput(GLAutoDrawable drawable) {
        if (inputDisplay == null) {
            inputDisplay = ImageDisplay.createOpenGLCanvas();
            imageDisplayFrame.getContentPane().add(inputDisplay, BorderLayout.NORTH);
            imageDisplayFrame.pack();
        }
        net.inputLayer.draw(inputDisplay);
        inputDisplay.repaint();
        imageDisplayFrame.setVisible(true);
    }

    private void drawActivations(GLAutoDrawable drawable) {
    }

    /**
     * @return the showActivations
     */
    public boolean isShowActivations() {
        return showActivations;
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

    }

    /**
     * @return the showOutputAsHistogram
     */
    public boolean isShowOutputAsHistogram() {
        return showOutputAsHistogram;
    }

    /**
     * @param showOutputAsHistogram the showOutputAsHistogram to set
     */
    public void setShowOutputAsHistogram(boolean showOutputAsHistogram) {
        this.showOutputAsHistogram = showOutputAsHistogram;
    }

    private void checkDisplayFrame() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
