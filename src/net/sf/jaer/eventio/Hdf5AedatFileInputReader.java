/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import ch.systemsx.cisd.hdf5.IHDF5Reader;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import net.sf.jaer.chip.AEChip;
/**
 * Reads HDF5 AER data files
 * 
 * @author Tobi Delbruck and Min Liu
 */
public class Hdf5AedatFileInputReader  {
    protected static Logger log=Logger.getLogger("Hdf5AedatFileInputReader");
    
    private IHDF5Reader reader=null;
    public Hdf5AedatFileInputReader(File f, AEChip chip) throws IOException  {
       
        int file_id = -1;
        int dataset_id = -1;
        int eventPktSpace_id = -1;
        int wholespace_id = -1;
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
                wholespace_id = H5.H5Dget_space(dataset_id);
                ndims = H5.H5Sget_simple_extent_dims(wholespace_id, dims, maxDims);
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
        

        long[] offset = {0, 0};
        long[] count = {1, 3};
        long[] stride = {1, 1};
        long[] block = {1, 1};
        int dataArrayLength = (int) dims[0] * (int) dims[1];
        
        // Create a new event packet data space to store one event packet.
        eventPktSpace_id = H5.H5Screate_simple(2, new long[]{1,3}, null); 
        long[] eventPktSpace_dims = new long[2];
        H5.H5Sget_simple_extent_dims(eventPktSpace_id, eventPktSpace_dims, null);
        
        // Allocate array of pointers to two-dimensional arrays (the
        // elements of the dataset. String is a variable length array of char.
        final String[] eventPktData = new String[(int)(eventPktSpace_dims[0] * eventPktSpace_dims[1])]; 
        
        // Select the row data to read.
        H5.H5Sselect_hyperslab(wholespace_id, HDF5Constants.H5S_SELECT_SET, offset, stride, count, block);
        
        try {
            if (wholespace_id >= 0)
                H5.H5Tdetect_class(memtype, HDF5Constants.H5T_STRING);
                H5.H5DreadVL(dataset_id, memtype,
                        HDF5Constants.H5S_ALL, wholespace_id,
                        HDF5Constants.H5P_DEFAULT, eventPktData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // End access to the dataset and release resources used by it.
        try {
            if (dataset_id >= 0)
                H5.H5Dclose(dataset_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            if (eventPktSpace_id >= 0)
                H5.H5Sclose(eventPktSpace_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            if (wholespace_id >= 0)
                H5.H5Sclose(wholespace_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Close the file.
        try {
            if (file_id >= 0)
                H5.H5Fclose(file_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }        
    }
    
    public static void main(String[] args){
        int[] libversion = new int[3];
        H5.H5get_libversion(libversion);
        System.out.println("The hdf5 library version is: " + 
                String.valueOf(libversion[0]) + "." + String.valueOf(libversion[1]) + "."
                + String.valueOf(libversion[2]) + ".");
        try {
            Hdf5AedatFileInputReader r=new Hdf5AedatFileInputReader(new File("rec1498945830.hdf5"),null);
        } catch (IOException ex) {
            log.warning("caught "+ex.toString());
        }
        
    }
    
    
    
}
