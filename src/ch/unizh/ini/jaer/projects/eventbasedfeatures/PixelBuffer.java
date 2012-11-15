/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;
import java.util.Arrays;
import java.util.AbstractCollection;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.Iterator;
import javax.swing.*;
import net.sf.jaer.Description;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;

/** This class develops an intensity map representation of the incoming AE events. A ring buffer 
 * is maintained at each pixel to keep track of the history of the events at that position.
 * A score is calculated for each pixel position - +1 for every ON event that enters the ring buffer (hence, -1 
 * for every ON event that leaves) and -1 for every OFF event that enters the ring buffer (hence, -1 
 * for every OFF event that leaves)
 *
 * @author Varad
 */

@Description("Creates an intensity map representation of AE events")
public class PixelBuffer extends EventFilter2D {
    
    public int RingBufferSize = getPrefs().getInt("PixelBuffer.RingBufferSize", 1);
    public boolean renderBufferMap = getPrefs().getBoolean("PixelBuffer.renderBufferMap", false);
//    private int dt = getPrefs().getInt("PixelTimestampBuffer.dt", 500);
//    public boolean TimeCriterionEnabled = getPrefs().getBoolean("PixelTimestampBuffer.isTimeCriterionEnabled", false);
    
    public boolean hasKernelImplementor = false;
    public boolean hasConvolutionFeatureDetector = false;
    public boolean hasBinaryFeatureDetector = false;
    
    KernelImplementor kernelimplement;
    ConvolutionFeatureDetector featuredetect;
    BinaryFeatureDetector bindetect;
    
    public float max ;
    public float min ;
        
    public int sizex;
    public int sizey;
    
    public float[][] colorv;    
    public float[][] map;    
    public RingBuffer[][] rbarr;
    
    ImageDisplay disp;
    JFrame frame;
    
    public ArrayList<Point> keypoints;

    
    public PixelBuffer (AEChip chip){
        
        super(chip);
        this.chip = chip;

        final String sz = "Size";                
        
        setPropertyTooltip(sz, "RingBufferSize", "sets size of ring buffer");        
//        setPropertyTooltip(tim, "dt", "Events with less than this delta time in us to neighbors pass through");
        

        sizex = chip.getSizeX();
        sizey = chip.getSizeY();                      
        
        disp = ImageDisplay.createOpenGLCanvas(); // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
        frame = new JFrame("ImageFrame");  // make a JFrame to hold it
        frame.setPreferredSize(new Dimension(sizex, sizey));  // set the window size
        frame.getContentPane().add(disp, BorderLayout.CENTER); // add the GLCanvas to the center of the window
        frame.pack(); // otherwise it wont fill up the display
        
        initFilter();
        resetFilter();
 
    }
    
    synchronized public boolean isRenderBufferMapEnabled(){
        return renderBufferMap;
    }
    
    synchronized public void setRenderBufferMapEnabled( boolean renderBufferMap ) {
        
        this.renderBufferMap = renderBufferMap;        
        getPrefs().putBoolean("PixelBuffer.renderBufferMap", renderBufferMap);
        getSupport().firePropertyChange("renderBufferMap", this.renderBufferMap, renderBufferMap);
        resetFilter();
    }
    
    synchronized public int getRingBufferSize (){
        return RingBufferSize;
    }
    
    synchronized public void setRingBufferSize( int RingBufferSize){
        this.RingBufferSize = RingBufferSize;
        getPrefs().putInt("PixelBuffer.RingBufferSize", RingBufferSize);
        getSupport().firePropertyChange("RingBufferSize", this.RingBufferSize, RingBufferSize);        
        resetFilter();
    }
    
    synchronized public void setKernelImplementor(KernelImplementor kernelimplement){
        
        this.hasKernelImplementor = true;
        this.kernelimplement = kernelimplement;
    }
    
    synchronized public void setConvolutionFeatureDetector(ConvolutionFeatureDetector featuredetect){
        
        this.hasConvolutionFeatureDetector = true;
        this.featuredetect = featuredetect;
    }
    
    synchronized public void setBinaryFeatureDetector(BinaryFeatureDetector bindetect){
        
        this.hasBinaryFeatureDetector = true;
        this.bindetect = bindetect;
    }
    
    synchronized private void checkMaps (){
        if ( rbarr == null || rbarr.length != chip.getSizeX() || rbarr[0].length != chip.getSizeY() )
            resetRingBuffers();        
    }
    
    synchronized private void resetRingBuffers(){
        
        int size = 128;
        disp.setImageSize(size, size); // set dimensions of image      
        frame.setVisible(true); // make the frame visible
        
        max = 1;
        min = 0;
        map = new float[sizex][sizey];        
        rbarr = new RingBuffer[sizex][sizey];
        colorv = new float[sizex][sizey];
        
        for (int a = 0; a < sizex; a++){
            for (int b = 0; b < sizey; b++){
                    rbarr[a][b] = new RingBuffer( getRingBufferSize() );
            }                    
        }        
    }

    
    
    public class RingBuffer{
            
        public final int length; // buffer length
        public int[] buffer; // an array of fixed length
        public int leadPointer, trailPointer ;
        public boolean fullflag;
        
        
        public RingBuffer(int cap){                        
            length = cap;
            resetBuffer();               
        }
        
        public void resetBuffer(){
            buffer = new int[length];            
            this.leadPointer = 0;
            this.fullflag = false;            
        }
        
        
        public int incrIndex(int i){
            
            i++;
            if(i>=length)i=0;
            return i;
        }        
        
        public void insert( PolarityEvent e){
            buffer[leadPointer] = e.getType();
            leadPointer = incrIndex(leadPointer);
                   
            if(leadPointer == 0) this.fullflag = true;      
        }
        
        public int getOldest(){
           
            return buffer[leadPointer];   
        }

        
    }    
    
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {                
        
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        
        if ( in == null ){
            return null;
        }        
        if ( enclosedFilter != null ){
            in = enclosedFilter.filterPacket(in);
        }
                
        checkMaps();
                
        if(hasKernelImplementor) kernelimplement.kernel.checkMaps();
        if(hasConvolutionFeatureDetector) featuredetect.kernel.checkMaps();
        if(hasBinaryFeatureDetector){
            bindetect.kernel.checkMaps();
            bindetect.binaryMethod.checkMaps();
            keypoints = new ArrayList<Point>();
        }
        
                
        
        
        for ( Object ein:in ){
    
            PolarityEvent e = (PolarityEvent)ein;
            int type = e.getType();
            int x = e.getX();
            int y = e.getY();
                        
            
            if( !rbarr[x][y].fullflag){   //partially or unfilled buffer
                
                    if( type == 1){ //ON event
                        map[x][y] += 1;    

                        if(hasKernelImplementor && kernelimplement.kernel!= null)
                            kernelimplement.kernel.updateMap(x, y, 1, kernelimplement.RelativeThreshold);
                        
                        if(hasConvolutionFeatureDetector && featuredetect.kernel!= null)
                            featuredetect.kernel.updateMap(x, y, 1, featuredetect.RelativeThreshold);
                        
                        if(hasBinaryFeatureDetector && bindetect.kernel!=null)
                            bindetect.kernel.updateMap(x, y, 1, 0);
                        
                    }                
                    else{           //OFF event
                        map[x][y] -= 1;                    
                        if(hasKernelImplementor && kernelimplement.kernel!= null)
                            kernelimplement.kernel.updateMap(x, y, -1, kernelimplement.RelativeThreshold);
                        
                        if(hasConvolutionFeatureDetector && featuredetect.kernel!= null)
                            featuredetect.kernel.updateMap(x, y, -1, featuredetect.RelativeThreshold);
                        
                        if(hasBinaryFeatureDetector && bindetect.kernel!=null)
                            bindetect.kernel.updateMap(x, y, -1, 0);
                        
                    }
                    checkMax(e);
                    checkMin(e);
            }
            
            
            else{                                //filled to the capacity at least once
                if ( type == rbarr[x][y].getOldest() ){   //if incoming event is same                                                                                        
                                ;                         //as one being pushed out                        ;
                }   
                else{
                    if( type == 1 ){    //ON event
                        map[x][y] += 2;
                        if(hasKernelImplementor && kernelimplement.kernel!= null)
                            kernelimplement.kernel.updateMap(x, y, 2, kernelimplement.RelativeThreshold);
                        
                        if(hasConvolutionFeatureDetector && featuredetect.kernel!= null){
                            featuredetect.kernel.updateMap(x, y, 2, featuredetect.RelativeThreshold);

                        }
                        
                        if(hasBinaryFeatureDetector && bindetect.kernel!=null){
                            bindetect.kernel.updateMap(x, y, 2, 0);

                        }
                        
                    }
                    else{               //OFF event
                        map[x][y] -= 2;
                        if(hasKernelImplementor && kernelimplement.kernel!= null)
                            kernelimplement.kernel.updateMap(x, y, -2, kernelimplement.RelativeThreshold);
                        
                        if(hasConvolutionFeatureDetector && featuredetect.kernel!= null)
                            featuredetect.kernel.updateMap(x, y, -2, featuredetect.RelativeThreshold);
                        
                        if(hasBinaryFeatureDetector && bindetect.kernel!=null)
                            bindetect.kernel.updateMap(x, y, -2, 0);
                    }
                    checkMax(e);
                    checkMin(e);
                }
            }
                

            if(isRenderBufferMapEnabled()){
                colorv[x][y] = (float)((map[x][y] - min)/(max - min));
                disp.checkPixmapAllocation(); // make sure we have a pixmaps (not resally necessary since setting size will allocate pixmap            
                disp.setPixmapRGB(x, y, colorv[x][y], colorv[x][y], colorv[x][y]);            
            }
            rbarr[x][y].insert(e);
            
        }
        
        if(hasKernelImplementor){
            disp.repaint();  
            kernelimplement.kernel.display.setPixmapArray(kernelimplement.kernel.grayvalue);
            kernelimplement.kernel.display.repaint();
            kernelimplement.kernel.updateFeatures(kernelimplement.kernel.keypoints, kernelimplement.RelativeThreshold);
        }
        
        if(hasConvolutionFeatureDetector){
            disp.repaint();  
            featuredetect.kernel.display.setPixmapArray(featuredetect.kernel.grayvalue);
            featuredetect.kernel.display.repaint();
            featuredetect.kernel.updateFeatures(featuredetect.kernel.keypoints, featuredetect.RelativeThreshold);
        }
        
        if(hasBinaryFeatureDetector){
            disp.repaint();  
            bindetect.kernel.display.setPixmapArray(bindetect.kernel.grayvalue);
            bindetect.kernel.display.repaint();
            bindetect.binaryMethod.getFeatures(bindetect.kernel.detectormap); 

        }
        
        return in;        
    }
    
    public void checkMax(PolarityEvent e){
        
        int x = e.getX();
        int y = e.getY(); 
        if ( map[x][y] > max ){
            max = map[x][y];
            
        }
    }
    
    public void checkMin(PolarityEvent e){
        
        int x = e.getX();
        int y = e.getY();
        if ( map[x][y] < min ){
            min = map[x][y];
        }
    }
    
   
    @Override
    public void resetFilter() {
        
        if(!isFilterEnabled()) 
            return;       
        disp.clearImage();              
        resetRingBuffers();                
    }

    @Override
    public void initFilter() {
        resetFilter();
        
    }    
}

//public class RingBuffer extends AbstractList<PolarityEvent> implements RandomAccess {  //or extends Object
//
//    private final int n; // buffer length
//    private final List<PolarityEvent> buf; // a List implementing RandomAccess
//    private int leader = 0;
//    private int size = 0;
//
//
//
//
//    public RingBuffer(int capacity) {   //constructor for the ring buffer with size as argument
//        n = capacity + 1;
//        buf = new ArrayList<PolarityEvent>(Collections.nCopies(n, (PolarityEvent) null));          
//    }    
//
//    private int wrapIndex(int i) {      //implementing circularity
//        int m = i % n;
//        if (m < 0) { 
//            m += n;
//        }
//        return m;
//    }
//
//    @Override
//    public int size() {
//        return this.size;
//    }
//
//    @Override
//    public PolarityEvent get(int i) {   //PolarityEvent is a defined return type
//                                        //this code returns the buffer member at the requested index
//        if (i < 0 || i >= n-1) throw new IndexOutOfBoundsException();
//
//        if(i > size()) throw new NullPointerException("Index is greater than size.");
//
//        return buf.get(wrapIndex(leader + i));
//    }
//
//    @Override
//    public PolarityEvent set(int i, PolarityEvent e) {  //this code sets the buffer member at the requested index
//        if (i < 0 || i >= n-1) {
//            throw new IndexOutOfBoundsException();
//        }
//        if(i == size()) // assume leader's position as invalid (should use insert(e))
//            throw new IndexOutOfBoundsException("The size of the list is " + size() + " while the index was " + i
//                    +"");
//        return buf.set(wrapIndex(leader - size + i), e);
//    }
//
//    public void insert(PolarityEvent e)     //adds a new element
//    {
//        int s = size();     
//        buf.set(wrapIndex(leader), e);
//        leader = wrapIndex(++leader);
//        buf.set(leader, null);
//        if(s == n-1){
//            fullflag[e.x][e.y] = true;
//            return; // we have replaced the eldest element.
//        }
//
//        this.size++;
//
//    }
//
//    public PolarityEvent getOldest(){   //returns oldest member i.e. index 99 of the buffer
//        int i = wrapIndex(leader+1);
//        PolarityEvent a = null;
//
//        for(;;i = wrapIndex(++i)) {
//            if(buf.get(i) != null) break;
//            if(i == leader) //break;
//                throw new IllegalStateException("Cannot remove element."
//                        + " CircularArrayList is empty.");
//        }
////        if( i!= leader )
////            a = buf.get(i);
//        return buf.get(i);
//    }
//    }