/*
 * MotionInputStream.java
 *
 * Created on December 10, 2006, 11:11 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 10, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflow.io;

import ch.unizh.ini.jaer.projects.opticalflow.*;
import ch.unizh.ini.jaer.projects.opticalflow.graphics.MotionViewer;
import net.sf.jaer.eventio.InputDataFileInterface;
import java.beans.*;
import java.io.*;
import java.nio.channels.*;
import java.util.logging.*;

/**
 * An input stream of motion data. This class deserializes MotionData from the input stream.
 
 * @author tobi
 */
public class MotionInputStream extends DataInputStream implements InputDataFileInterface {
    
    private PropertyChangeSupport support=new PropertyChangeSupport(this);
    static final Logger log=Logger.getLogger("MotionInputStream");
    private FileInputStream fileInputStream;
    private FileChannel fileChannel;
    private long fileSize=0; // in bytes
    private long size=0; // size in MotionData units
    private MotionData motionData;
    DataInputStream dataInputStream=null;
    
    /** Creates a new instance of MotionInputStream
     @param is the input stream
     */
    public MotionInputStream(FileInputStream is) throws IOException {
        super(is);
        init(is);
    }
    
    /** Reads and deserializes a MotionData object from the input stream
     @return the data frame
     */
    synchronized public MotionData readData(MotionData motionData) throws IOException{
        int oldPosition=position();
        motionData.read(dataInputStream);
        getSupport().firePropertyChange("position",oldPosition,position());
        return motionData;
    }
    
    // computes various quantities of input file
    private void init(FileInputStream is) {
        fileInputStream=is;
        fileChannel=is.getChannel();
        try{
            fileSize=fileChannel.size();
        }catch(IOException e){
            e.printStackTrace();
            fileSize=0;
        }
        boolean openok=false;
//        reader=new InputStreamReader(fileInputStream);
//
//        try{
//            readHeader();
//        }catch(IOException e){
//            log.warning("couldn't read header");
//        }
        size=fileSize/motionData.getLoggedObjectSize();
        dataInputStream=new DataInputStream(Channels.newInputStream(fileChannel));
        getSupport().firePropertyChange("position",0,position());
        position(1);
    }
    
    
    @Override
    public float getFractionalPosition() {
        return (float)position()/size;
    }
    
    @Override
    public void mark() throws IOException {
    }
    
    @Override
    public int position() {
        try{
            int p= (int)fileChannel.position()/motionData.getLoggedObjectSize();
            return p;
        }catch(IOException e){
            e.printStackTrace();
            return 0;
        }
    }
    
    @Override
   public void position(int n) {
        try{
            fileChannel.position(n*motionData.getLoggedObjectSize());
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    public void rewind() throws IOException {
        fileChannel.position(0);
    }
    
     synchronized  public void setFractionalPosition(float frac) {
        int oldPosition=position();
        position((int)(frac*size));
        int newPosition=position();
        log.info("Set fractional position "+frac+" changed position from "+oldPosition+" to "+newPosition);
    }
    
    /** @return size of data file in MotionData units */
    @Override
    public long size() {
        return size;
    }
    
    @Override
    public void unmark() {
    }
    
    public PropertyChangeSupport getSupport() {
        return support;
    }
    
    public void setSupport(PropertyChangeSupport support) {
        this.support = support;
    }
    
    
}
