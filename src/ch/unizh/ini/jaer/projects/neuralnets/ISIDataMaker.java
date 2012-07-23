/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.io.File;
import net.sf.jaer.chip.AEChip;
import org.ine.telluride.jaer.tell2009.ISIHistogrammer;

/**
 *
 * @author Peter
 */
public class ISIDataMaker extends ISIHistogrammer {
    
    File outputFile;
    
    public ISIDataMaker(AEChip chip)
    {
        super(chip);        
        
    }
    
    
    public void doSelectOutputFile()
    {
        
    }
    
    
}
