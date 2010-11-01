/*
 * MotionData.java
 *
 * Created on November 24, 2006, 6:58 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright November 24, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflowMDC2D.chip;

import java.io.*;
import java.util.Random;

/**
 * Packs data returned from optical flow sensor.
 Values are floats that which are normalized as follows: all values are returned in range 0-1 float. Motion signals are centered around 0.5f.
 Global outputs are not - they are just from corner of chip.
 
 * @author tobi
 */
public class MotionDataMDC2D {
    
    private static final long serialVersionUID = 0L;
    
    private int tobi=0;
    /** Bit definitions for what this structure holds.
     User sets these bits to tell the hardware interface what data should be acquired for this buffer
     */
    public static final int RECEP=0x01, LMC1=0x02, LMC2=0x04;
    
    /** the resolution of the SiLabs_8051F320 ADC */
    public static final int ADC_RESOLUTION_BITS=10;
    
    private int sequenceNumber;
    private float globalX;
    private float globalY;
    
    /** The time in System.currentTimeMillis() that this data was captured */
    private long timeCapturedMs=0;
    
    private float[][] recep; // photoreceptor value, range 0-inf
    private float[][] lmc1; // velocity x, centered on 0
    private float[][] lmc2; // velocity y, centered on 0
    
    private float minph, maxph, minux, maxus, minuy, maxuy;
    
    /** Bits set in contents show what data has actually be acquired in this buffer.
     @see #GLOBAL_Y
     @see #GLOBAL_X etc
     */
    private int contents=0;
    private static Random r=new Random();
    
    /** Creates a new instance of MotionData */
    public MotionDataMDC2D() {
        globalX=0; globalY=0;
        recep=new float[MDC2D.NUM_COLUMNS][MDC2D.NUM_ROWS];
        lmc1=new float[MDC2D.NUM_COLUMNS][MDC2D.NUM_ROWS];
        lmc2=new float[MDC2D.NUM_COLUMNS][MDC2D.NUM_ROWS];
        // debug
//        randomizeArray(ph,0,1);
//        randomizeArray(ux,-1,1);
//        randomizeArray(uy,-1,1);
//        globalX=-1+2*r.nextFloat();
//        globalY=-1+2*r.nextFloat();
    }
    
    private void randomizeArray(float[][] f, float min, float max){
        for(int i=0;i<f.length;i++){
            float[] g=f[i];
            for(int j=0;j<g.length;j++){
                g[j]=min+r.nextFloat()*(max-min);
            }
        }
    }
    /** @return total number of independent data */
    final  static public int getLength(){
        return 3*(MDC2D.NUM_COLUMNS*MDC2D.NUM_ROWS);
    }
    
    /** returns the sequence number of this data
     @return the sequence number, starting at 0 with the first data captured by the reader
     */
    final  public int getSequenceNumber() {
        return sequenceNumber;
    }
    
    final   public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
    
    /*final  public float getGlobalX() {
        return globalX;
    }
    
    final   public void setGlobalX(float globalX) {
        this.globalX = globalX;
    }
    
    final  public float getGlobalY() {
        return globalY;
    }
    
    final   public void setGlobalY(float globalY) {
        this.globalY = globalY;
    }

     */
    
    final public float[][] getPh() {
        return recep;
    }
    
    final public void setPh(float[][] ph) {
        this.recep = ph;
    }
    
    final public float[][] getUx() {
        return lmc1;
    }
    
    final public void setUx(float[][] ux) {
        this.lmc1 = ux;
    }
    
    final public float[][] getUy() {
        return lmc2;
    }
    
    final public void setUy(float[][] uy) {
        this.lmc2 = uy;
    }
    
    /** returns the contents field, whose bits show what data is valid in this buffer */
    final public int getContents() {
        return contents;
    }
    
    final public void setContents(int contents) {
        this.contents = contents;
    }
    
    public float getMinph() {
        return minph;
    }
    
    public void setMinph(float minph) {
        this.minph = minph;
    }
    
    public float getMaxph() {
        return maxph;
    }
    
    public void setMaxph(float maxph) {
        this.maxph = maxph;
    }
    
    public float getMinux() {
        return minux;
    }
    
    public void setMinux(float minux) {
        this.minux = minux;
    }
    
    public float getMaxus() {
        return maxus;
    }
    
    public void setMaxus(float maxus) {
        this.maxus = maxus;
    }
    
    public float getMinuy() {
        return minuy;
    }
    
    public void setMinuy(float minuy) {
        this.minuy = minuy;
    }
    
    public float getMaxuy() {
        return maxuy;
    }
    
    public void setMaxuy(float maxuy) {
        this.maxuy = maxuy;
    }
    /*
    public boolean hasGlobalX(){
        return (contents&GLOBAL_X)!=0;
    }
    
    public boolean hasGlobalY(){
        return (contents&GLOBAL_Y)!=0;
    }

     */
    
    public boolean hasPhoto(){
        return (contents&RECEP)!=0;
    }
    
    public boolean hasLocalX(){
        return (contents&LMC1)!=0;
    }
    
    public boolean hasLocalY(){
        return (contents&LMC2)!=0;
    }
    
    /** gets the system time that the data was captured
     @return the time in ms as returned by System.currentTimeMillis()
     */
    public long getTimeCapturedMs() {
        return timeCapturedMs;
    }
    
    public void setTimeCapturedMs(long timeCapturedMs) {
        this.timeCapturedMs = timeCapturedMs;
    }
    
    /** The serialized size in bytes of a MotionData instance */
    public static int OBJECT_SIZE=4+4+8+3*4*(MDC2D.NUM_COLUMNS*MDC2D.NUM_ROWS);
//    static {
//        MotionData obj=new MotionData();
//        byte[] ba = null;
//        
//        try {
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            ObjectOutputStream    oos  = new ObjectOutputStream( baos );
//            oos.writeObject( obj );
//            oos.close();
//            ba = baos.toByteArray();
//            baos.close();
//            baos=null;
//            oos=null;
//        } catch ( IOException ioe ) {
//            ioe.printStackTrace();
//            OBJECT_SIZE=0;
//        }
//        OBJECT_SIZE= ba.length;
//        obj=null;
//        ba=null;
//    }
//    
    public String toString(){
        return "MotionData sequenceNumber="+sequenceNumber+" timeCapturedMs="+timeCapturedMs;
    }
    
    /** Implements the Externalizable writer in conjuction with the reader. 
     Each MotionData object is written as follows:
     <br>
     
        out.writeInt(contents);<br>
        out.writeInt(sequenceNumber);<br>
        out.writeLong(timeCapturedMs);<br>
        write2DArray(out,ph);<br>
        write2DArray(out,ux);<br>
        write2DArray(out,uy);<br>
      
     @param out the output
     */
    public void write(DataOutput out) throws IOException {
        out.writeInt(contents);
        out.writeInt(sequenceNumber);
        out.writeLong(timeCapturedMs);
        write2DArray(out,recep);
        write2DArray(out,lmc1);
        write2DArray(out,lmc2);
    }

    /** Implements the reader half of the Externalizable interface
     @param in the ObjectInput interface
     */
    public void read(DataInput in) throws IOException {
        contents=in.readInt();
        sequenceNumber=in.readInt();
        timeCapturedMs=in.readLong();
        read2DArray(in,recep);
        read2DArray(in,lmc1);
        read2DArray(in,lmc2);
    }
    
    private void write2DArray(DataOutput out, float[][] f) throws IOException {
        for(int i=0;i<f.length;i++){
            float[] g=f[i];
            for(int j=0;j<g.length;j++){
                out.writeFloat(g[j]);
            }
        }
    }
    
    private void read2DArray(DataInput in, float[][] f) throws IOException {
        for(int i=0;i<f.length;i++){
            float[] g=f[i];
            for(int j=0;j<g.length;j++){
                g[j]=in.readFloat();
            }
        }
    }
}
