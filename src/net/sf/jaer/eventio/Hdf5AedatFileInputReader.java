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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import net.sf.jaer.chip.AEChip;
import ncsa.hdf.hdf5lib.HDFArray;
/**
 * Reads HDF5 AER data files
 * 
 * @author Tobi Delbruck
 */
public class Hdf5AedatFileInputReader  {
    protected static Logger log=Logger.getLogger("Hdf5AedatFileInputReader");
    
    private IHDF5Reader reader=null;
    public Hdf5AedatFileInputReader(File f, AEChip chip) throws IOException  {
       
        int file_id = -1;
        int dataset_id = -1;
        int space = -1;
        int ndims = 0;
        long dims[] = new long[2];
        long maxDims[] = new long[2];
        int memtype = -1;
        int dcpl = -1;
        long chunkDims[] = new long[2];


        // Open file using the default properties.
        try {
            file_id = H5.H5Fopen(f.getName(), HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
            
            // Open dataset using the default properties.
            if (file_id >= 0) {
                dataset_id = H5.H5Dopen(file_id, "/dvs/data", HDF5Constants.H5P_DEFAULT);  
            }
            
            
            if (dataset_id >= 0) {            
                space = H5.H5Dget_space(dataset_id);
                ndims = H5.H5Sget_simple_extent_dims(space, dims, maxDims);
                dcpl = H5.H5Dget_create_plist(dataset_id);
                if( HDF5Constants.H5D_CHUNKED == H5.H5Pget_layout(dcpl)) {
                    H5.H5Pget_chunk(dcpl, ndims, chunkDims);
                }
            }
            
            // Create the memory datatype.
            memtype = H5.H5Tvlen_create(HDF5Constants.H5T_NATIVE_INT8);

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Allocate array of pointers to two-dimensional arrays (the
        // elements of the dataset. String is a variable length array of char.
        long[] offset = {0, 0};
        long[] count = {1, 3};
        long[] stride = {1, 1};
        long[] block = {1, 1};
        int dataArrayLength = (int) dims[0] * (int) dims[1];
        final String[] dataRead = new String[dataArrayLength];  
        int[] dataReadByte = new int[dataArrayLength];

        H5.H5Sselect_hyperslab(space, HDF5Constants.H5S_SELECT_SET, stride, stride, count, block);
 
        try {
            if (space >= 0)
                H5.H5Tdetect_class(memtype, HDF5Constants.H5T_STRING);
                H5.H5Dread(dataset_id, HDF5Constants.H5T_NATIVE_INT8,
                        HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL,
                        HDF5Constants.H5P_DEFAULT, dataReadByte);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args){
        try {
            Hdf5AedatFileInputReader r=new Hdf5AedatFileInputReader(new File("rec1498945830.hdf5"),null);
            
        } catch (IOException ex) {
            log.warning("caught "+ex.toString());
        }
        
    }
    
    
    
}
