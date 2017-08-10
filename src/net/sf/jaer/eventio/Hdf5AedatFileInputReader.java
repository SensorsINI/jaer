/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5FactoryProvider;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;

/**
 * Reads HDF5 AER data files
 * 
 * @author Tobi Delbruck
 */
public class Hdf5AedatFileInputReader  implements AEFileInputStreamInterface{
    protected static Logger log=Logger.getLogger("Hdf5AedatFileInputReader");
    
    private IHDF5Reader reader=null;
    AEChip chip=null;
    
    public Hdf5AedatFileInputReader(File f, AEChip chip) throws IOException  {
        this.chip=chip;
        reader = HDF5FactoryProvider.get().openForReading(f);
        HDF5DataSetInformation info=reader.getDataSetInformation("/dvs/data");
        reader.close();
    }
    
    public static void main(String[] args){
        try {
            Hdf5AedatFileInputReader r=new Hdf5AedatFileInputReader(new File("rec1500840420.hdf5"),null);
            
        } catch (IOException ex) {
            log.warning("caught "+ex.toString());
        }
        
    }

    @Override
    public AEPacketRaw readPacketByNumber(int n) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public AEPacketRaw readPacketByTime(int dt) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        return false;
    }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
    }

    @Override
    public float getFractionalPosition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long position() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void position(long n) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void rewind() throws IOException {
        if(reader!=null){
//            reader.
        }
    }

    @Override
    public void setFractionalPosition(float frac) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long size() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearMarks() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long setMarkIn() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long setMarkOut() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getMarkInPosition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getMarkOutPosition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isMarkInSet() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isMarkOutSet() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setRepeat(boolean repeat) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isRepeat() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    
}
