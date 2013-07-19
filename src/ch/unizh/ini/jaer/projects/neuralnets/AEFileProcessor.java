/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAERb;

/**
 *
 * This allows you to load an AEDat file, feed it into an event-filter, and let
 * 'er rip.
 * 
 * To do this, just run this file.  You'll be prompted to select an aedata file. 
 * Select it and go!
 * 
 * @author Peter
 */
public class AEFileProcessor {
    
    public static void main(String[] args)
    {
                
        CochleaAERb chip=new CochleaAERb();
//        Extractor ex=chip.new Extractor(chip);
        chip.setEventExtractor(chip.new Extractor(chip));
        
        ISIspikeFilter filt=new ISIspikeFilter(chip);
        AERProcessor aerp=new AERProcessor(filt);
        aerp.loadFile();
        
        if (aerp.aeFile==null)
            return;
        
        
        filt.initializeNetwork();
        filt.plotNet(false);
        filt.nc.setRecordingState(true);
        filt.nc.addAllControls();
        filt.nc.view.realTime=false;
                
        aerp.run(); // Run the events through the filter!
        
        filt.nc.saveRecording();
        
        
        
        
        
    }
    
    
}


/** This class reads aedat files, feeds them to networks */
class AERProcessor
{
    SpikeFilter filt;
    
    File aeFile;
    
    public AERProcessor(SpikeFilter sf)
    {
        filt=sf;        
     
        filt.initFilter();
        
    }
    
    public void loadFile()
    {
        JFileChooser fc=new JFileChooser("Select Cochlea Spike File!");
        fc.addChoosableFileFilter(new FileNameExtensionFilter("AER Data File", "aedat"));
        fc.showOpenDialog(null);
        aeFile=fc.getSelectedFile();
        
        
        
    }
    
    public void run()
    {   AEFileInputStream fis=null;
    
    
        try {
            
            fis=new AEFileInputStream(aeFile);
                    
//            AEPacketRaw pack=new AEPacketRaw();
            
            
            
            AEPacketRaw pack=fis.readPacketByNumber((int)fis.size());
//            AEPacketRaw pack=data.readPacketByTime(Integer.MAX_VALUE/2-1);
            
            
            
//            EventPacket dat=new EventPacket();
//            dat.setRawPacket(pack);
            EventPacket dat=filt.getChip().getEventExtractor().extractPacket(pack);
            
            filt.filterPacket(dat);
            
            
        } catch (IOException ex) {
            Logger.getLogger(AERProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        
                
    }
    
    
    
    
    
    
    
}
