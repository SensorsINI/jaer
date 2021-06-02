/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import static net.sf.jaer.eventio.Jaer3BufferParser.JAER3POLMASK;
import static net.sf.jaer.eventio.Jaer3BufferParser.JAER3POLSHIFT;
import static net.sf.jaer.eventio.Jaer3BufferParser.JAER3XMASK;
import static net.sf.jaer.eventio.Jaer3BufferParser.JAER3XSHIFT;
import static net.sf.jaer.eventio.Jaer3BufferParser.JAER3YMASK;
import static net.sf.jaer.eventio.Jaer3BufferParser.JAER3YSHIFT;

/**
 * Reads HDF5 AER3.1 data files
 *
 * @author Tobi Delbruck and Min Liu
 */
public class Hdf5AedatFileInputReader {

    protected static Logger log = Logger.getLogger("Hdf5AedatFileInputReader");

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
    AEChip chip;
    private ApsDvsEventPacket out;

    enum H5O_type {
        H5O_TYPE_UNKNOWN(-1), // Unknown object type
        H5O_TYPE_GROUP(0), // Object is a group
        H5O_TYPE_DATASET(1), // Object is a dataset
        H5O_TYPE_NAMED_DATATYPE(2), // Object is a named data type
        H5O_TYPE_NTYPES(3); // Number of different object types
        private static final Map<Integer, H5O_type> lookup = new HashMap<Integer, H5O_type>();

        static {
            for (H5O_type s : EnumSet.allOf(H5O_type.class)) {
                lookup.put(s.getCode(), s);
            }
        }
        private int code;

        H5O_type(int layout_type) {
            this.code = layout_type;
        }

        public int getCode() {
            return this.code;
        }

        public static H5O_type get(int code) {
            return lookup.get(code);
        }
    }

    /**
     * Constructor.
     *
     * @param f: The Hdf5 file to be read.
     * @param chip
     * @throws IOException
     */
    public Hdf5AedatFileInputReader(File f, AEChip chip) throws IOException {

        fileName = f.getName();
        this.chip = chip;

        // An event packet is a row in the dataset. A row consists of 3 columns.
        // It's ordered like this: timestamp_system | packet header | dvs data.
        eventPktDimsM[0] = 1;
        eventPktDimsM[1] = 3;

        eventPktData = new String[(int) (eventPktDimsM[0] * eventPktDimsM[1])];
        // Create the memory datatype.
        try {
            memtype = H5.H5Tvlen_create(HDF5Constants.H5T_NATIVE_UCHAR);
            try {
                eventCamPktSpace_id = H5.H5Screate_simple(2, eventPktDimsM, null);
            } catch (HDF5Exception ex) {
                Logger.getLogger(Hdf5AedatFileInputReader.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException ex) {
                Logger.getLogger(Hdf5AedatFileInputReader.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (HDF5LibraryException ex) {
            Logger.getLogger(Hdf5AedatFileInputReader.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("could not construct Hdf5AedatFileInputReader: ",ex);
        }
    }

    /**
     * Open the dataset in the file.
     *
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
                int count = (int) H5.H5Gn_members(file_id, "/dvs");
                String[] oname = new String[count];
                int[] otype = new int[count];
                int[] ltype = new int[count];
                long[] orefs = new long[count];
                H5.H5Gget_obj_info_all(file_id, "/dvs", oname, otype, ltype, orefs, HDF5Constants.H5_INDEX_NAME);

                // Get type of the object and display its name and type.
                for (int indx = 0; indx < otype.length; indx++) {
                    switch (H5O_type.get(otype[indx])) {
                        case H5O_TYPE_GROUP:
                            System.out.println("  Group: " + oname[indx]);
                            break;
                        case H5O_TYPE_DATASET:
                            System.out.println("  Dataset: " + oname[indx]);
                            break;
                        case H5O_TYPE_NAMED_DATATYPE:
                            System.out.println("  Datatype: " + oname[indx]);
                            break;
                        default:
                            System.out.println("  Unknown: " + oname[indx]);
                    }
                }
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
     *
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
            if (HDF5Constants.H5D_CHUNKED == H5.H5Pget_layout(dcpl)) {
                H5.H5Pget_chunk(dcpl, chunknDims, retDims);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retDims;
    }

    /**
     * This function is used to read a row data from the file. Every row in the
     * ddd-17 consists of 3 columns: timestamp, header, data.
     *
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
            if (dataset_id >= 0) {
                H5.H5Dclose(dataset_id);
            }

            // Close the memory data space.
            if (eventCamPktSpace_id >= 0) {
                H5.H5Sclose(eventCamPktSpace_id);
            }

            // Close the file data space.
            if (wholespace_id >= 0) {
                H5.H5Sclose(wholespace_id);
            }

            // Close the file.
            if (file_id >= 0) {
                H5.H5Fclose(file_id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert the String to Int Array. Such as ["20", "30"] to [20, 30].
     *
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

    public int fourIntsToInt(int[] dataArray, int index) {
        return (dataArray[index + 3] << 24) + (dataArray[index + 2] << 16) + (dataArray[index + 1] << 8) + dataArray[index];
    }

    public int getRowPktFirstTs(int rowNum) {
        int[] pktData = stringToIntArray(readRowData(rowNum)[2]);
        return fourIntsToInt(pktData, 4);
    }

    public int getRowPktLastTs(int rowNum) {
        int[] pktData = stringToIntArray(readRowData(rowNum)[2]);
        int length = pktData.length;
        return fourIntsToInt(pktData, length - 4);
    }

    public EventPacket extractPacket(int rowNum) {
        int[] pktHeader = stringToIntArray(eventPktData[1]);
        int[] pktData = stringToIntArray(eventPktData[2]);

        if (out == null) {
            out = new ApsDvsEventPacket(ApsDvsEvent.class); // In order to be general, we make the packet's event ApsDvsEvent.
        } else {
            out.clear();
        }
//        final OutputEventIterator outItr = out.outputIterator();
//
//        int eventSize = fourIntsToInt(pktHeader, 4);
//        int eventTsOffset = fourIntsToInt(pktHeader, 8);
//        int eventNum = fourIntsToInt(pktHeader, 20);
//        
//        EventType etype = EventType.values()[pktHeader[0]];
//
//        switch (etype){
//            case PolarityEvent:                  
//                for(int i = 0; i < eventNum; i++) {
//                     readDVS(outItr, fourIntsToInt(pktData, i * eventSize), fourIntsToInt(pktData, i * eventSize + eventTsOffset));                    
//                }
//                break;
//            default:
//                System.out.println("Not supported yet.");
//        }
        return out;
    }

    /**
     * Extractor the DVS events.
     *
     * @param outItr the iterator of the output stream
     * @param data data, for DVS, it's the address
     * @param timestamp timestamp of the event
     */
    protected void readDVS(final OutputEventIterator outItr, final int data, final int timestamp) {
        final int sx1 = chip.getSizeX() - 1;
        final ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
        e.reset();

        e.address = data;
        e.timestamp = timestamp;
        e.polarity = ((data & JAER3POLMASK) >> JAER3POLSHIFT) == (JAER3POLMASK >> JAER3POLSHIFT) ? ApsDvsEvent.Polarity.On
                : ApsDvsEvent.Polarity.Off;
        e.type = 0;
        e.x = (short) (sx1 - ((data & JAER3XMASK) >>> JAER3XSHIFT));
        e.y = (short) ((data & JAER3YMASK) >>> JAER3YSHIFT);

        e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);

        // autoshot triggering
        // autoshotEventsSinceLastShot++; // number DVS events captured here
    }

    public static void main(String[] args) {
        try {
            int[] libversion = new int[3];
            String[] test = new String[3];
            int rowNum = 0;
            H5.H5get_libversion(libversion);
            System.out.println("The hdf5 library version is: "
                    + String.valueOf(libversion[0]) + "." + String.valueOf(libversion[1]) + "."
                    + String.valueOf(libversion[2]) + ".");
            try {
                Hdf5AedatFileInputReader r = new Hdf5AedatFileInputReader(new File("rec1498945830.hdf5"), null);
                r.openDataset("/dvs/polarity/data");
                r.creatWholeFileSpace();
                test = r.readRowData(rowNum);
                r.extractPacket(rowNum);
                System.out.println("The packet header in row " + rowNum + " is: " + test[1]);
                System.out.println("The packet data in row " + rowNum + " is: " + test[2]);
                System.out.println("The first timestamp in row" + rowNum + " is: " + r.getRowPktFirstTs(rowNum));
                System.out.println("The last timestamp in row" + rowNum + " is: " + r.getRowPktLastTs(rowNum));
            } catch (IOException ex) {
                log.warning("caught " + ex.toString());
            }
        } catch (HDF5LibraryException ex) {
            Logger.getLogger(Hdf5AedatFileInputReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
