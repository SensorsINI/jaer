/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5DataTypeInformation;
import ch.systemsx.cisd.hdf5.HDF5FactoryProvider;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;

/**
 * Reads HDF5 AER data files
 * 
 * @author Tobi Delbruck
 */
public class Hdf5AedatFileInputReader  {
    protected static Logger log=Logger.getLogger("Hdf5AedatFileInputReader");
    
    private IHDF5Reader reader=null;
    public Hdf5AedatFileInputReader(File f, AEChip chip) throws IOException  {
        reader = HDF5FactoryProvider.get().openForReading(f);
        HDF5DataSetInformation info = reader.getDataSetInformation("/dvs/data");
        int[] chunckSize = info.tryGetChunkSizes();
        Class<? extends HDF5DataTypeInformation> attrInfo = info.getTypeInformation().getClass();
        byte[] dvs_data = reader.opaque().readArray("/dvs/data");
        reader.close();
    }
    
    public static void main(String[] args){
        try {
            Hdf5AedatFileInputReader r=new Hdf5AedatFileInputReader(new File("rec1500383466.hdf5"),null);
            
        } catch (IOException ex) {
            log.warning("caught "+ex.toString());
        }
        
    }
    
    
    
}
