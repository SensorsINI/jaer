/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import net.sf.jaer.chip.AEChip;
/**
 * Reads HDF5 AER3.1 data files
 * 
 * @author Tobi Delbruck and Min Liu
 */
public class Hdf5AedatFileInputReader  {
    protected static Logger log=Logger.getLogger("Hdf5AedatFileInputReader");
    
    private String fileName;
    
    private int file_id = -1;     
    private int dataset_id = -1;
    private int eventCamPktSpace_id = -1;  // Memory data space for event camera data.
    private int wholespace_id = -1;        // The whole file data space.

    private int memtype = -1;              // Memeory data type
            
    // Create a new event packet data memory space.
    long[] eventPktDimsM = new long[2];

    // Allocate array to stor one event packet.
    // String is a variable length array of char.
    final String[] eventPktData;       
     
    /**
     * Constructor.
     * @param f: The Hdf5 file to be read.
     * @param chip
     * @throws IOException
     */
    public Hdf5AedatFileInputReader(File f, AEChip chip) throws IOException  {
        fileName = f.getName();
        
        // An event packet is a row in the dataset. A row consists of 3 columns.
        // It's ordered like this: timestamp_system | packet header | dvs data.
        eventPktDimsM[0] = 1;
        eventPktDimsM[1] = 3;
        
        eventPktData = new String[(int)(eventPktDimsM[0] * eventPktDimsM[1])]; 
        // Create the memory datatype.
        memtype = H5.H5Tvlen_create(HDF5Constants.H5T_NATIVE_UCHAR);       
        eventCamPktSpace_id = H5.H5Screate_simple(2, eventPktDimsM, null); 
    }
    
    /**
     * Open the dataset in the file.
     * @param datasetPath: the path of the dataset that will be opened.
     * @return. True if open successfully, otherwise false.
     */
    public boolean openDataset(String datasetPath) {
        boolean retVal = false;
        
        // Open file using the default properties.
        try {
            file_id = H5.H5Fopen(fileName, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
            
            // Open dataset using the default properties.
            if (file_id >= 0) {
                dataset_id = H5.H5Dopen(file_id, datasetPath, HDF5Constants.H5P_DEFAULT);  
                retVal = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retVal;
    }

    /**
     * Create the whole file data space.
     */
    public void creatWholeFileSpace() {
        try {
            wholespace_id = H5.H5Dget_space(dataset_id);            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Read the file's data space dimensions.
     * @return: the data space dimensions.
     */
    public long[] getFileDims() {    
        long[] retDims = {0, 0};  
        try {            
            H5.H5Sget_simple_extent_dims(wholespace_id, retDims, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return retDims;
    }
    
    /**
     *
     * @return the chunk's dimensions.
     */
    public long[] getChunkDims() {
        long[] retDims = {0, 0};
        int dcpl = -1;
        int chunknDims = 2;
        try {            
            dcpl = H5.H5Dget_create_plist(dataset_id);
            if( HDF5Constants.H5D_CHUNKED == H5.H5Pget_layout(dcpl)) {
                H5.H5Pget_chunk(dcpl, chunknDims, retDims);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }  
        return retDims;
    }

    /**
     * This function is used to read a row data from the file. Every row in the ddd-17
     * consists of 3 columns: timestamp, header, data.
     * @param rowNum: the number of the row that will be read.
     * @return: the data in the rowNumth row.
     */
    public String[] readRowData(int rowNum) {
               
        // Select the row data to read.      
        long[] offset = {0, 0};
        long[] count = {1, 3};
        long[] stride = {1, 1};        
        long[] block = {1, 1};   
    
        offset[0] = rowNum;
        try { 
            if (wholespace_id >= 0) {
        
                H5.H5Sselect_hyperslab(wholespace_id, HDF5Constants.H5S_SELECT_SET, offset, stride, count, block);

                /*
                 * Define and select the second part of the hyperslab selection,
                 * which is subtracted from the first selection by the use of
                 * H5S_SELECT_NOTB
                 */
                 // H5.H5Sselect_hyperslab (wholespace_id, HDF5Constants.H5S_SELECT_NOTB, offset, stride, count,
                 //               block);    
                 
                H5.H5DreadVL(dataset_id, memtype,
                    eventCamPktSpace_id, wholespace_id,
                    HDF5Constants.H5P_DEFAULT, eventPktData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return eventPktData;
    }
    
    /**
     * Release all the resources.
     */
    public void closeResources() {
        
        // End access to the dataset and release resources used by it.
        try {
            // Close the dataset. 
            if (dataset_id >= 0)
                H5.H5Dclose(dataset_id);
        
            // Close the memory data space.
            if (eventCamPktSpace_id >= 0)
                H5.H5Sclose(eventCamPktSpace_id);
            
            // Close the file data space.
            if (wholespace_id >= 0)
                H5.H5Sclose(wholespace_id);
            
            // Close the file.
            if (file_id >= 0)
                H5.H5Fclose(file_id);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert the String to Int Array. Such as ["20", "30"] to [20, 30].
     * @param str the String will be converted.
     * @return
     */
    public int[] stringToIntArray(String str) {
        String[] items = str.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s", "").split(",");

        int[] results = new int[items.length];

        for (int i = 0; i < items.length; i++) {
            try {
                results[i] = Integer.parseInt(items[i]);
            } catch (NumberFormatException nfe) {
                //NOTE: write something here if you need to recover from formatting errors
            };
        }
        return results;
    }
    
    public static void main(String[] args){
        int[] libversion = new int[3];
        String[] test = new String[3]; 
        int rowNum = 0;
        H5.H5get_libversion(libversion);
        System.out.println("The hdf5 library version is: " + 
                String.valueOf(libversion[0]) + "." + String.valueOf(libversion[1]) + "."
                + String.valueOf(libversion[2]) + ".");
        try {
            Hdf5AedatFileInputReader r = new Hdf5AedatFileInputReader(new File("rec1498945830.hdf5"),null);
            r.openDataset("/dvs/data");
            r.creatWholeFileSpace();
            test = r.readRowData(rowNum);
            System.out.println("The packet header in row " + rowNum + " is: " + test[1]);
            System.out.println("The packet data in row " + rowNum + " is: " + test[2]);            
        } catch (IOException ex) {
            log.warning("caught "+ex.toString());
        }        
    }    
}
