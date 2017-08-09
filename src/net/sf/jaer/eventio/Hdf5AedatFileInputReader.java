/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import ch.systemsx.cisd.hdf5.HDF5DataClass;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5DataTypeInformation;
import ch.systemsx.cisd.hdf5.HDF5FactoryProvider;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.BitSet;
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
        HDF5DataClass dataClass = info.getTypeInformation().getDataClass();
        HDF5DataTypeInformation typeInfo = info.getTypeInformation();
        int[] chunckSize = info.tryGetChunkSizes();
        byte[] dvs_data = reader.opaque().readArray("/dvs/data");
        ByteBuffer buffer = ByteBuffer.allocate(dvs_data.length);   
        buffer.put(dvs_data, 0, dvs_data.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.flip();//need flip 
        buffer.rewind();
        LongBuffer lb = ((ByteBuffer) buffer.rewind()).asLongBuffer();
        long[] dvs = new long[lb.remaining()];
        lb.get(dvs);
        reader.close();
    }
    
    public static void main(String[] args){
        try {
            Hdf5AedatFileInputReader r=new Hdf5AedatFileInputReader(new File("rec1498945830.hdf5"),null);
            
        } catch (IOException ex) {
            log.warning("caught "+ex.toString());
        }
        
    }
    
    
    
}
