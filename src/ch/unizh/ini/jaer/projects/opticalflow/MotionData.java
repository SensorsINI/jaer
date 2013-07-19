package ch.unizh.ini.jaer.projects.opticalflow;

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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Random;

import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MotionDataMDC2D;

import com.phidgets.SpatialEventData;

/**
 * Packs data returned from optical flow sensor.
 * 
 * some changes by andstein
 * <ul>
 * <li>added a second global-x/y for comparison between software/hardware
 *     calculated values</li>
 * <li>added boolean filledIn that can be used by child classes to prevent
 *     MotionViewer from filling in motion values a 2nd time</li>
 * </ul>
 * 
 * @author tobi
 */
public abstract class MotionData implements Cloneable{
      
    /** Bit definitions for what this structure holds.
     User sets these bits to tell the hardware interface what data should be acquired for this buffer
     */
    public static final int GLOBAL_X=0x01,
                            GLOBAL_Y=0x02,
                            PHOTO=0x04,
                            UX=0x08,
                            UY=0x10,
                            BIT5=0x20,
                            BIT6=0x40,
                            BIT7=0x80;  

    /** the resolution of the SiLabs_8051F320 ADC */
    public static final int ADC_RESOLUTION_BITS=10;

    // how many past MotionData are stored
    protected static int NUM_PASTMOTIONDATA=0;
    
    private int sequenceNumber;
    
    /** The time in System.currentTimeMillis() that this data was captured */
    private long timeCapturedMs=0;
    /** dt is the precise measurement of time elapsed between two frames as set on firmware (microseconds)*/
    private long dt=0;


    protected float [][][] rawDataPixel; //Array containing the raw data [channel][posX][posY]
    protected float [] rawDataGlobal;

    //HACK to prevent calculation of motion data in MotionViewer when data
    // is already replaced in another thread...
    // is unset to false in setSequenceNumber() and set after collectMotionInfo()
    protected boolean filledIn= false;
    

    /* Motion data is basically represented as receptor outputs, local and global
     * motion in x and y direction. Those are used by the standard display methods.
     * If needed subclasses of MotionData can provide more variables.
     */
    protected float[][] ph; // photoreceptor value, range 0-inf
    protected float[][] ux; // velocity x, centered on 0
    protected float[][] uy; // velocity y, centered on 0
    protected float globalX; // global velocity x, centered on 0
    protected float globalY; // global velocity x, centered on 0
    protected float minph, maxph, minux, maxux, minuy, maxuy;
    // these are used for comparison purposes
    protected float globalX2; // global velocity x, centered on 0
    protected float globalY2; // global velocity x, centered on 0
    protected SpatialEventData phidgetData; // as measured by SpatialPhidget

    // a Chip2DMotion object of the current chip type
    public Chip2DMotion chip;

    // contains the last few MotionData acquired
    protected MotionData[] pastMotionData;


    

    
    /** Bits set in contents show what data has actually be acquired in this buffer.
     @see #GLOBAL_Y
     @see #GLOBAL_X etc
     */
    private int contents=0;
    protected static Random r=new Random();


    
    /** Constructor to be called by non abstract subclasses */
    public MotionData(Chip2DMotion chip) {
        globalX=0; globalY=0;
        globalX2=0; globalY2=0;
        this.chip = chip;
        contents=chip.getCaptureMode();
        rawDataPixel = new float[this.getNumLocalChannels()][chip.getSizeX()][chip.getSizeY()];
        rawDataGlobal = new float[getNumGlobalChannels()];
        ph=new float[chip.getSizeX()][chip.getSizeY()];
        ux=new float[chip.getSizeX()][chip.getSizeY()];
        uy=new float[chip.getSizeX()][chip.getSizeY()];
    }
    


    /* methods tofills the compulsory motion data for display (ph, ux, uy, globalX,
     * globalY minph, maxph, minux, maxux, minuy, maxuy.
     *
     * The methods are abstract and have to be implemented by subclasses depending on
     * what data is collected from the chip and what has to be calculated from
     * the host.
     */
        abstract protected void fillPh(); //fill photoreceptor data to ph
        abstract protected void fillUxUy(); //fill localMotion data
        abstract protected void fillMinMax(); // fill min,max fields
        abstract protected void fillAdditional(); // computes any additional info
        abstract protected void updateContents(); //updates the contents of MotionData


    /* fills the compulsory motion data for display (ph, ux, uy, globalX, globalY
     * minph, maxph, minux, maxux, minuy, maxuy. This method takes care that all
     * computations on the raw data are done.
     * 
     * this method does NOT DO ANYTHING unless you called setSequenceNumber()
     * (or directly setFilledIn(false)) before !
     */
    public final void collectMotionInfo(){
        if (!MotionDataMDC2D.enabled)
            return; // ;)
        if (filledIn == false) {
            fillPh(); //write the photoreceptor data
            fillUxUy(); //write the local and global motion data
            fillMinMax(); //calculate min and max of data
            fillAdditional(); //do additional computations
            updateContents();
            setFilledIn(true);
        }
    }
    

    /**
     * get methods: These are public and return the content of private objects
     */

    /** @return total number of independent data */
    final  public int getLength(){
        return 3*(1+chip.getSizeX()*chip.getSizeY());
    }

    /** returns the sequence number of this data
     @return the sequence number, starting at 0 with the first data captured by the reader
     */
    final  public int getSequenceNumber() {
        return sequenceNumber;
    }

     /** returns the contents field, whose bits show what data is valid in this buffer */
    final public int getContents() {
        return contents;
    }

     /** gets the system time that the data was captured
     @return the time in ms as returned by System.currentTimeMillis()
     */
    public long getTimeCapturedMs() {
        return timeCapturedMs;
    }

    /**
     * time elapsed between two frames in microseconds. this value 
     * @return dt between adjacacent frames [us]
     */
    public long getDt() {
        return dt;
    }

    /**
     * see @getDt
     * @param dt 
     */
    public void setDt(long dt) {
        this.dt = dt;
    }


    final public float[][][] getRawDataPixel(){
        return this.rawDataPixel;
    }

    final public float[] getRawDataGlobal(){
        return this.rawDataGlobal;
    }


    final  public float getGlobalX() {
        return globalX;
    }
    
    final  public float getGlobalY() {
        return globalY;
    }
    
    final public float[][] getPh() {
        return ph;
    }
    
    final public float[][] getUy() {
        return uy;
    }
    
    final public float[][] getUx() {
        return ux;
    }
    
    public float getMaxuy() {
        return maxuy;
    }
    
    public float getMinuy() {
        return minuy;
    }
    
    public float getMaxux() {
        return maxux;
    }
    
    public float getMinux() {
        return minux;
    }
    
    public float getMaxph() {
        return maxph;
    }
    
    public float getMinph() {
        return minph;
    }

    // get the very last MotionData
    public MotionData getLastMotionData() {
        return this.pastMotionData[0];
    }

    //get the array of pastMotionData.
    public MotionData[] getPastMotionData(){
        return this.pastMotionData;
    }

    //get the number of global channels. This is computed by analizing the frame
    //descriptor
    public int getNumGlobalChannels(){
        int numGlob = contents & GLOBAL_X + (contents & GLOBAL_Y)>>1; // first two bits(from LSB) are
        return numGlob;
    }

    //get the number of local channels. Analize framedescriptor
    public int getNumLocalChannels(){
        int numLoc = 0;
        int f = contents&0xFC;
        for(int i=0;i<8;i++){
            numLoc += (f>>i)&0x01;
        }
        return numLoc;
    }


    
    public boolean hasGlobalX(){
        return (contents&GLOBAL_X)!=0;
    }

    public boolean hasGlobalY(){
        return (contents&GLOBAL_Y)!=0;
    }

    public boolean hasPhoto(){
        return (contents&PHOTO)!=0;
    }

    public boolean hasLocalX(){
        return (contents&UX)!=0;
    }

    public boolean hasLocalY(){
        return (contents&UY)!=0;
    }



   /** set Methods*/

    final public void setRawDataGlobal(float[] data){
        this.rawDataGlobal = data;
    }

    final public void setRawDataPixel(float[][][] data){
        this.rawDataPixel = data;
    }

   final   public void setSequenceNumber(int sequenceNumber) {
       if (sequenceNumber != this.sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            setFilledIn(false);
       }
    }

   final   public void setGlobalX(float globalX) {
        this.globalX = globalX;
    }

   final public  void setGlobalY(float globalY) {
        this.globalY = globalY;
    }

    final public void setPh(float[][] ph) {
        this.ph = ph;
    }
     
    final public void setUx(float[][] ux) {
        this.ux = ux;
    }

    final public void setUy(float[][] uy) {
        this.uy = uy;
    }
 
    final public void setContents(int contents) {
        this.contents = contents;
    }
    
    public void setMinph(float minph) {
        this.minph = minph;
    }

    public void setMaxph(float maxph) {
        this.maxph = maxph;
    }
    
    public void setMinux(float minux) {
        this.minux = minux;
    }
    
    public void setMaxus(float maxux) {
        this.maxux = maxux;
    }
    
    public void setMinuy(float minuy) {
        this.minuy = minuy;
    }
    
    public void setMaxuy(float maxuy) {
        this.maxuy = maxuy;
    }
    
    public void setTimeCapturedMs(long timeCapturedMs) {
        this.timeCapturedMs = timeCapturedMs;
    }
    
    public float getGlobalX2() {
        return globalX2;
    }

    public float getGlobalY2() {
        return globalY2;
    }

    public void setGlobalX2(float globalX2) {
        this.globalX2 = globalX2;
    }

    public void setGlobalY2(float globalY2) {
        this.globalY2 = globalY2;
    }

    public SpatialEventData getPhidgetData() {
        return phidgetData;
    }

    public void setPhidgetData(SpatialEventData phidgetData) {
        this.phidgetData = phidgetData;
    }
    

    /* shifts pastMotionData array by one position. The oldest data fall out. At
     * position 0 the most recent data is inserted
     */
    public void setLastMotionData(MotionData lastData){
        if (this.pastMotionData==null) {
            this.pastMotionData= new MotionData[NUM_PASTMOTIONDATA];
        }
        for(int i=NUM_PASTMOTIONDATA;i>1;i--){
            this.pastMotionData[i-1]=this.pastMotionData[i-2]; //shift oldest Data
        }
        //lastData.pastMotionData[0]=null;
        //lastData.pastMotionData[1]=null;
        this.pastMotionData[0]=lastData; //write newest
        //pastMotionData[0].setPastMotionData(null); // set the pastMotionData of the element 0 (the newest) in the array to null. --> No past MotionData in the pastMotionData objects to avoid useless usage of memory
    }

    public void setPastMotionData(MotionData[] pastData){
        this.pastMotionData=pastData;
    }


    // returns a 2Darray corresponding to one channel of raw data (e.g. photoreceptors)
        public float[][] extractRawChannel(int channelNumber){
        int maxX=this.chip.NUM_COLUMNS;
        int maxY=this.chip.NUM_ROWS;
        float[][] channelData =new float[maxX][maxY] ;
        for(int x=0;x<maxX;x++){
            for(int y=0;y<maxY;y++){
                channelData[y][x]=this.rawDataPixel[channelNumber][y][x]; 
            }
        }
        return channelData;
    }

    public void setFilledIn(boolean filledIn) {
        this.filledIn = filledIn;
    }


   //returns an array with random numbers between -1 and 1
   public float[][] randomizeArray(float[][] f, float min, float max){
        for(int i=0;i<f.length;i++){
            float[] g=f[i];
            for(int j=0;j<g.length;j++){
                g[j]=min+r.nextFloat()*(max-min);
            }
            f[i]=g;
        }
        return f;
    }

   //clone the MotionData object. (ie make shallow copy)
    public MotionData clone() {
	try {
	    return (MotionData) super.clone();
	} catch (CloneNotSupportedException cnse) {
	    return null;
	}
    }


    
    public String toString(){
        return "MotionData sequenceNumber="+sequenceNumber+" timeCapturedMs="+timeCapturedMs;
    }

    
    /** Implements the Externalizable writer in conjuction with the reader. 
     Each MotionData object is written as follows:
     <br>
     
        out.writeInt(contents);<br>
        out.writeInt(sequenceNumber);<br>
        out.writeLong(timeCapturedMs);<br>
        out.writeFloat(globalX);<br>
        out.writeFloat(globalY);<br>
        write2DArray(out,ph);<br>
        write2DArray(out,ux);<br>
        write2DArray(out,uy);<br>
        write2DArray(out,rawDataPixel[chan0]<br>
//        .
        .
        .
        write2DArray(out,rawDataPixel[chanN]<br>
     @param out the output
     */
    public void write(DataOutput out) throws IOException {
        contents=chip.getCaptureMode();
        out.writeInt(contents);
        out.writeInt(sequenceNumber);
        out.writeLong(timeCapturedMs);
        out.writeFloat(globalX);
        out.writeFloat(globalY);
        write2DArray(out,ph);
        this.fillUxUy();
        write2DArray(out,ux);
        write2DArray(out,uy);
        for(int i=0;i<getNumLocalChannels();i++){ 
            write2DArray(out,rawDataPixel[i]);
        }
    }

    /** Implements the reader half of the Externalizable interface
     @param in the ObjectInput interface
     */
    public void read(DataInput in) throws IOException {
        contents=in.readInt();
        sequenceNumber=in.readInt();
        timeCapturedMs=in.readLong();
        globalX=in.readFloat();
        globalY=in.readFloat();
        read2DArray(in,ph);
        read2DArray(in,ux);
        read2DArray(in,uy);
        rawDataPixel=new float[getNumLocalChannels()][chip.NUM_ROWS][chip.NUM_COLUMNS];
        for(int i=0;i<getNumLocalChannels();i++){
            read2DArray(in,rawDataPixel[i]);
        }
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


    /** The serialized size in bytes of a MotionData instance */
    public int getLoggedObjectSize(){
        int size =      4  // int contents
                      + 4   //int sequenceNumber
                      + 8  //long timecaptured
                      + 4 * 2 // float global ux, global uy     // float is 32bit (4byte)
                      + 4 * 3 * (chip.getSizeX()*chip.getSizeY())// float arrays ph,ux,uy
                      + 4 * getNumLocalChannels()* (chip.getSizeX()*chip.getSizeY()); // 2D float arrays for each channel
//        size=4+4+8+3*4*(chip.getSizeX()*chip.getSizeY());
        return size;
    }
}




