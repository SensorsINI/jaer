/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Computes CNN from DAVIS APS frames.
 *
 * @author tobi
 */
@Description("Computes CNN from DAVIS APS frames")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisDeepLearnCnnProcessor extends EventFilter2D implements PropertyChangeListener, FrameAnnotater{

    private String lastFileName = getString("lastFileName", "LCRN_cnn.xml");
    private DeepLearnCnnNetwork net = null;
    private ApsFrameExtractor frameExtractor=new ApsFrameExtractor(chip);
    private boolean showOutputLayerHistogram=getBoolean("showOutputLayerHistogram", true);
    private boolean showActivations=getBoolean("showActivations",false);

    public DavisDeepLearnCnnProcessor(AEChip chip) {
        super(chip);
        setPropertyTooltip("loadCNNNetworkFromXML", "Load an XML file containing a CNN exported from DeepLearnToolbox by cnntoxml.m");
        FilterChain chain=new FilterChain(chip);
        chain.add(frameExtractor);
        setEnclosedFilterChain(chain);
        frameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);
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
        if(net!=null){
            float[] frame=frameExtractor.getDisplayBuffer(); // TODO currently a clone of vector, index is y * width + x, i.e. it marches along rows and then up
            if(frame==null || frame.length==0 || frameExtractor.getWidth()==0) return;
            float[] outputs=net.compute(frame, frameExtractor.getWidth());
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    

}
