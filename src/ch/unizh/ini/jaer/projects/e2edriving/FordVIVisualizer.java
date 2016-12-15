/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.e2edriving;

import com.jogamp.opengl.GLAutoDrawable;
import eu.visualize.ini.convnet.DavisDeepLearnCnnProcessor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Reads Ford VI (vehicle interface) log files to display vehicle data over
 * recording
 *
 * @author tobi, jbinas
 */
public class FordVIVisualizer extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private long recordingStartTimeMs = 0;
    private File fordViFile = null;
    String lastFordVIFile = getString("lastFordVIFile", null);
    private boolean addedPropertyChangeListener=false;
    BufferedInputStream fordViInputStream=null;

    public FordVIVisualizer(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!addedPropertyChangeListener){
            addedPropertyChangeListener=true;
            chip.getAeViewer().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        }
        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return in;
        }
        if (!in.isEmpty()) {
            int lastTs = in.getLastTimestamp();
        }
        return in; // only annotates
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }

   
    synchronized public void doOpenFordVIFile() {
        JFileChooser c = new JFileChooser(lastFordVIFile);
        FileFilter filt = new FileNameExtensionFilter("dat file", "dat");
        c.addChoosableFileFilter(filt);
        c.setFileFilter(filt);
        c.setSelectedFile(new File(lastFordVIFile));
        int ret = c.showOpenDialog(chip.getAeViewer());
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastFordVIFile = c.getSelectedFile().toString();
        putString("lastFordVIFile", lastFordVIFile);
        try {
            fordViFile = c.getSelectedFile();
            fordViInputStream=new BufferedInputStream(new FileInputStream(fordViFile));
        } catch (Exception ex) {
            Logger.getLogger(DavisDeepLearnCnnProcessor.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(chip.getAeViewer().getFilterFrame(), "Couldn't load from this file, caught exception " + ex + ". See console for logging.", "Bad data file", JOptionPane.WARNING_MESSAGE);
        }

    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        if(pce.getPropertyName()==AEViewer.EVENT_FILEOPEN){
            int fileStartTs=chip.getAeInputStream().getFirstTimestamp();
        }
        
    }

}
