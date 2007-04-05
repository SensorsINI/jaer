/*
 * LineTracker.java
 *
 * Created on December 26, 2006, 9:24 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 26, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventprocessing.tracking;

import ch.unizh.ini.caviar.aemonitor.Event;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.*;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.*;
import java.util.*;
import java.util.prefs.Preferences;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.swing.*;

/**
 * Tracks a single line as used for line-following navigation or for lane tracking.
 The line is assumed to originate from the top of the image and extends out to the bottom. Events
 that are within range of the line model push the line around by changing the angle and origin of the line.
 The model creates a dynamic estimate of the best fit line and rejects outlier events by only
 being affected by events near the
 present model position.
 <p>
 Origin of the line is center of image. Angle of line is 0 when vertical and positive for clockwise line rotation.
 <p>
 The line is tracked using an incremental Hough transform method. See
 http://rkb.home.cern.ch/rkb/AN16pp/node122.html for a concise explanation of basic idea of Hough's.
 Or http://en.wikipedia.org/wiki/Hough_transform.
 Or http://www.cs.tu-bs.de/rob/lehre/bv/HNF.html for a good interactive java applet demo.
 
 <p>
 Each point is splatted in its p, theta form into an accumulator array
 * @author tobi
 */
public class HoughLineTracker extends EventFilter2D implements FrameAnnotater {
    
    static Preferences prefs=Preferences.userNodeForPackage(HoughLineTracker.class);
    
//    Line line=new Line();
    private float angleMixingFactor=prefs.getFloat("LineTracker.angleMixingFactor",0.005f);
    private float positionMixingFactor=prefs.getFloat("LineTracker.positionMixingFactor",0.005f);
//    private float lineWidth=prefs.getFloat("LineTracker.lineWidth",10f);
    private float thetaResDeg=prefs.getFloat("LineTracker.thetaResDeg", 6);
    private float rhoResPixels=prefs.getFloat("LineTracker.rhoResPixels",3);
    private boolean showHoughWindow=false;
    private float rhoLimit;
    private float[][] accumArray;
    private int nTheta, nRho;
    private float tauMs=prefs.getFloat("LineTracker.tauMs",10);
    float[] cos=null, sin=null;
    int rhoMaxIndex, thetaMaxIndex;
    float accumMax;
    int[][] accumUpdateTime;
    float sx2, sy2; // half chip size
    private float rhoPixelsFiltered = 0;
    private float thetaDegFiltered = 0;
    LowpassFilter rhoFilter, thetaFilter;
    private int maxNumLines=prefs.getInt("LineTracker.maxNumLines",2);
//    private List<Line> lines=new ArrayList<Line>(maxNumLines);
//    Peak[] peaks=null;
    
    /** Creates a new instance of LineTracker
     @param chip the chip to track for
     */
    public HoughLineTracker(AEChip chip) {
        super(chip);
        initFilter();
        chip.getCanvas().addAnnotator(this);
    }
    
    /** returns the Hough line radius of the last packet's estimate - the closest distance from the middle of the chip image.
     @return the distance in pixels. If the chip size is sx by sy, can range over +-Math.sqrt( (sx/2)^2 + (sy/2)^2).
     This number is positive if the line is above the origin (center of chip)
     */
    synchronized public float getRhoPixels(){
        return (rhoMaxIndex-nRho/2)*rhoResPixels;
    }
    
    /** returns the angle of the last packet's Hough line.
     @return angle in degrees. Ranges from 0 to 180 degrees, where 0 and 180 represent a vertical line and 90 is a horizontal line
     */
    synchronized public float getThetaDeg(){
        return (thetaMaxIndex)*thetaResDeg;
    }
    
    /** returns the angle of the last packet's Hough line.
     @return angle in radians. Ranges from 0 to Pi radians, where 0 and Pi represent a vertical line and Pi/2 is a horizontal line
     */
    public float getThetaRad(){
        return getThetaDeg()/180*3.141592f;
    }
    
    synchronized public void resetFilter() {
        sx2=chip.getSizeX()/2;
        sy2=chip.getSizeY()/2;
        nTheta=(int)(180/thetaResDeg); // theta spans only 0 to Pi
        rhoLimit=(float)Math.ceil(Math.sqrt(sx2*sx2+sy2*sy2)); // rho can span this +/- limit after hough transform of event coordinate which shifted so that middle of chip is zero
        nRho=(int)(2*rhoLimit/rhoResPixels);
        accumArray=new float[nTheta][nRho];
//        accumUpdateTime=new int[nTheta][nRho];
        accumMax=Float.NEGATIVE_INFINITY;
        cos=new float[nTheta];
        sin=new float[nTheta];
        for(int i=0;i<cos.length;i++){
            cos[i]=(float)Math.cos(thetaResDeg*(i)/180*Math.PI); // cos[i] is the cos of the i'th angle, runs from approx 0 to 2 Pi rad
            sin[i]=(float)Math.sin(thetaResDeg*(i)/180*Math.PI);
        }
        rhoFilter=new LowpassFilter();
        thetaFilter=new LowpassFilter();
        rhoFilter.setTauMs(tauMs);
        thetaFilter.setTauMs(tauMs);
//        lines.clear();
//        peaks=new Peak[maxNumLines];
//        for(int i=0;i<maxNumLines;i++){
//            lines.add(new Line());
//            peaks[i]=new Peak();
//        }
    }
    
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        resetAccumArray();
        checkAccumFrame();
        for(BasicEvent e:in){
            addEvent(e);
//            line.addEvent(e);
        }
        findPeaks();
        rhoPixelsFiltered=rhoFilter.filter(getRhoPixels(),in.getLastTimestamp());
        thetaDegFiltered=thetaFilter.filter(getThetaDeg(),in.getLastTimestamp());
//        System.out.println(String.format("theta=%.2f rho=%.1f",thetaMax*thetaResDeg,rhoMax*rhoResPixels));
        if(showHoughWindow) accumCanvas.repaint();
        return in;
    }
    
    // http://rkb.home.cern.ch/rkb/AN16pp/node122.html
    private void addEvent(BasicEvent e) {
        float x=e.x-sx2;
        float y=e.y-sy2;
        for(int thetaNumber=0;thetaNumber<nTheta;thetaNumber++){
            float rho=((x*cos[thetaNumber]+y*sin[thetaNumber]));
            int rhoNumber=(int)((rho+rhoLimit)/rhoResPixels);
            if(rhoNumber<0 || rhoNumber>=nRho){
//                log.warning(String.format("e.x=%d, e.y=%d, x=%f, y=%f, rho=%f, rhoNumber=%d",e.x,e.y,x,y,rho,rhoNumber));
                continue;
            }
            uodateHoughAccum(thetaNumber,rhoNumber);
        }
    }
    
    /** Uses a reset array and just counts events in each bin to determine the peak locations of the lines
     @param thetaNumber the index of the theta of the line. thetaNumber=0 means theta=0 which is a horizontal line, theta=nTheta/2 is a vertical line.
     @param rhoNumber the rho (radius) number. rho is spaced by rhoResPixels.
     */
    private void uodateHoughAccum(int thetaNumber, int rhoNumber) {
        float a=(accumArray[thetaNumber][rhoNumber]++); // update the accumulator
        // now we need to determine the peaks in the accumulator array and match them with the existing lines.
        // we do this on each event to reduce computational cost of determining peaks.
        // the maxNumLines peaks are determined here
        // first the max peak is determined, then second peak, etc.
        // the Peak's maintain the peak values and locations
        // they are ordered with peaks[0] being the highest peak, peaks[1] the next highest, and so on
//        for(int i=0;i<peaks.length;i++){
//
//        }
        if(a>accumMax){
            accumMax=a;
            thetaMaxIndex=thetaNumber;
            rhoMaxIndex=rhoNumber;
        }
    }
    
    class Peak{
        int accumMax, thetaMaxIndex, rhoMaxIndex;
        Peak(){
        }
        Peak(int accumMax, int thetaMaxIndex, int rhoMaxIndex){
            this.accumMax=accumMax;
            this.thetaMaxIndex=thetaMaxIndex;
            this.rhoMaxIndex=rhoMaxIndex;
        }
        void set(int accumMax, int thetaMaxIndex, int rhoMaxIndex){
            this.accumMax=accumMax;
            this.thetaMaxIndex=thetaMaxIndex;
            this.rhoMaxIndex=rhoMaxIndex;
        }
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        final float LINE_WIDTH=5f; // in pixels
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        gl.glLineWidth(LINE_WIDTH);
        gl.glColor3f(0,0,1);
        gl.glBegin(GL.GL_LINES);
        double thetaRad=thetaDegFiltered/180*Math.PI;
        double cosTheta=Math.cos(thetaRad);
        double sinTheta=1-cosTheta*cosTheta;
        if(thetaRad>Math.PI/4 && thetaRad<3*Math.PI/4){
            gl.glVertex2d(0,  yFromX(0, cosTheta, sinTheta));
            gl.glVertex2d(sx2*2, yFromX(sx2*2, cosTheta, sinTheta));
        }else{
            gl.glVertex2d(xFromY(0, cosTheta, sinTheta),0);
            gl.glVertex2d(xFromY(sy2*2, cosTheta, sinTheta),sy2*2);
        }
        gl.glEnd();
    }
    
    // returns chip y from chip x using present fit
    private double yFromX(float x, double cosTheta, double sinTheta){
        double xx=x-sx2;
        double yy=(rhoPixelsFiltered-xx*cosTheta)/sinTheta;
        double y=yy+sy2;
//        if(y>sy2*2) y=sy2*100; else if(y<0) y=-sy2*100;
        return y;
    }
    
    // returns chip x from chip y using present fit
    private double xFromY(float y, double cosTheta, double sinTheta){
        double yy=y-sy2;
        double xx=(rhoPixelsFiltered-yy*sinTheta)/cosTheta;
        double x=xx+sx2;
        return x;
    }
    
    
    
    
    void checkAccumFrame(){
        if(showHoughWindow && (accumFrame==null || (accumFrame!=null && !accumFrame.isVisible()))) createAccumFrame();
    }
    
    JFrame accumFrame=null;
    GLCanvas accumCanvas=null;
    GLU glu=null;
//    GLUT glut=null;
    void createAccumFrame(){
        accumFrame=new JFrame("Hough accumulator");
        accumFrame.setPreferredSize(new Dimension(200,200));
        accumCanvas=new GLCanvas();
        accumCanvas.addGLEventListener(new GLEventListener(){
            public void init(GLAutoDrawable drawable) {
            }
            
            synchronized public void display(GLAutoDrawable drawable) {
                if(accumArray==null) return;
                GL gl=drawable.getGL();
                gl.glLoadIdentity();
                gl.glScalef(drawable.getWidth()/nTheta,drawable.getHeight()/nRho,1);
                gl.glClearColor(0,0,0,0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                for(int i=0;i<nTheta;i++){
                    for(int j=0;j<nRho;j++){
                        float f=accumArray[i][j]/accumMax;
                        gl.glColor3f(f,f,f);
                        gl.glRectf(i,j,i+1,j+1);
                    }
                }
                gl.glPointSize(6);
                gl.glColor3f(1,0,0);
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(thetaMaxIndex, rhoMaxIndex);
                gl.glEnd();
//                if(glut==null) glut=new GLUT();
                int error=gl.glGetError();
                if(error!=GL.GL_NO_ERROR){
                    if(glu==null) glu=new GLU();
                    log.warning("GL error number "+error+" "+glu.gluErrorString(error));
                }
            }
            
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl=drawable.getGL();
                final int B=10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
                gl.glOrtho(-B,drawable.getWidth()+B,-B,drawable.getHeight()+B,10000,-10000);
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0,0,width,height);
            }
            
            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
            }
        });
        accumFrame.getContentPane().add(accumCanvas);
        accumFrame.pack();
        accumFrame.setVisible(true);
    }
    
    public Object getFilterState() {
        return null;
    }
    
    
    public void initFilter() {
        resetFilter();
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
//    /** Holds description of a single line */
//    public class Line{
//
//        /** Creates a new line with default characteristics */
//        public Line(){}
//
//        int rhoMaxIndex, thetaMaxIndex;
//        float accumMax=0;
//        float rhoPixelsFiltered=0, thetaDegFiltered=0;
//        LowpassFilter rhoFilter=new LowpassFilter(), thetaFilter=new LowpassFilter();
//
//        /** returns the Hough line radius of the last packet's estimate - the closest distance from the middle of the chip image.
//         @return the distanc in pixels. If the chip size is sx by sy, can range over +-Math.sqrt( (sx/2)^2 + (sy/2)^2).
//         This number is positive if the line is above the origin (center of chip)
//         */
//        synchronized public float getRhoPixels(){
//            return (rhoMaxIndex-nRho/2)*rhoResPixels;
//        }
//
//        /** returns the angle of the last packet's Hough line.
//         @return angle in degrees. Ranges from 0 to 180 degrees, where 0 and 180 represent a vertical line and 90 is a horizontal line
//         */
//        synchronized public float getThetaDeg(){
//            return (thetaMaxIndex)*thetaResDeg;
//        }
//
//        /** returns the angle of the last packet's Hough line.
//         @return angle in radians. Ranges from 0 to Pi radians, where 0 and Pi represent a vertical line and Pi/2 is a horizontal line
//         */
//        public float getThetaRad(){
//            return getThetaDeg()/180*3.141592f;
//        }
//    }
    
    
    public float getThetaResDeg() {
        return thetaResDeg;
    }
    
    synchronized public void setThetaResDeg(float thetaResDeg) {
        this.thetaResDeg = thetaResDeg;
        prefs.putFloat("LineTracker.thetaResDeg",thetaResDeg);
        resetFilter();
    }
    
    public float getRhoResPixels() {
        return rhoResPixels;
    }
    
    synchronized public void setRhoResPixels(float rhoResPixels) {
        this.rhoResPixels = rhoResPixels;
        prefs.putFloat("LineTracker.rhoResPixels",rhoResPixels);
        resetFilter();
    }
    
    public float getTauMs() {
        return tauMs;
    }
    
    synchronized public void setTauMs(float tauMs) {
        this.tauMs = tauMs;
        prefs.putFloat("LineTracker.tauMs",tauMs);
        rhoFilter.setTauMs(tauMs);
        thetaFilter.setTauMs(tauMs);
    }
    
    private void resetAccumArray() {
        accumMax=0;
        for(int i=0;i<nTheta;i++){
            float[] f=accumArray[i];
            Arrays.fill(f,0);
        }
    }
    
    public boolean isShowHoughWindow() {
        return showHoughWindow;
    }
    
    synchronized public void setShowHoughWindow(boolean showHoughWindow) {
        this.showHoughWindow = showHoughWindow;
    }
    
    public int getMaxNumLines() {
        return maxNumLines;
    }
    
    /** @param maxNumLines the maximum number of peaks tracked of Hough space tracked.
     */
    public void setMaxNumLines(int maxNumLines) {
        this.maxNumLines = maxNumLines;
        prefs.putInt("LineTracker.maxNumLines",maxNumLines);
    }
    
//    /** @return list of lines that are being tracked */
//    public List<Line> getLines() {
//        return lines;
//    }
    
//    public void setLines(List<Line> lines) {
//        this.lines = lines;
//    }
    
    private void findPeaks() {
    }
    
    /** returns the filtered Hough line radius estimate - the closest distance from the middle of the chip image.
     @return the distance in pixels. If the chip size is sx by sy, can range over +-Math.sqrt( (sx/2)^2 + (sy/2)^2).
     This number is positive if the line is above the origin (center of chip)
     */
    public float getRhoPixelsFiltered() {
        return rhoPixelsFiltered;
    }
    
    /**
     returns the filtered angle of the line.
     @return angle in degrees. Ranges from 0 to 180 degrees, where 0 and 180 represent a vertical line and 90 is a horizontal line
     */
    public float getThetaDegFiltered() {
        return thetaDegFiltered;
    }
    
    
    
}
//    // returns chip y from chip x using present fit
//    private float yFromX(float x, double cosTheta, double sinTheta){
//        float xx=x-sx2;
//
//        float yy=(rhoPixelsFiltered-xx*cos[thetaMaxIndex])/sin[thetaMaxIndex];
////        float yy=((rhoMaxIndex-nRho/2)*rhoResPixels-xx*cos[thetaMaxIndex])/sin[thetaMaxIndex];
//        float y=yy+sy2;
////        System.out.println(String.format("  x=%.1f xx=%.1f yy=%.1f y=%.1f rhoLimit=%.1f",x,xx,yy,y,rhoLimit));
//        return y;
//    }

//    private void updateAccum(int thetaNumber, int rhoNumber,int timestamp) {
//        int dt=timestamp-accumUpdateTime[thetaNumber][rhoNumber];
//        if(dt<0) return; // ignore negative times that can create exponentially big values
//        float a=accumArray[thetaNumber][rhoNumber];
//        a=1+a*(float)Math.exp(-dt/1000./tauMs); // decay present value like RC and add event
//        accumArray[thetaNumber][rhoNumber]=a;
//        accumUpdateTime[thetaNumber][rhoNumber]=timestamp;
//        if(a>accumMax){
//            accumMax=a;
//            rhoMaxIndex=rhoNumber;
//            thetaMaxIndex=thetaNumber;
//        }
//    }


//    class Line{
//
//        private Point2D.Float farPoint=new Point2D.Float(), nearPoint=new Point2D.Float(), joiningVector=new Point2D.Float();
////        private float curvature=0;
//        private int lastEventTime=0;
//        private int numEvents=0;
////        private float width=LineTracker.this.getLineWidth();
//        private float A=1, B=0, C=1, n; // parameters of line, n is norm of normal vector
//        final int NUM_EVENTS=100;
//        BasicEvent[] events=new BasicEvent[NUM_EVENTS];
//        int eventPointer=0;
//
//        Line(){
//            farPoint.setLocation(chip.getSizeX()/2,chip.getSizeY());
//            nearPoint.setLocation(chip.getSizeX()/2, 0);
//            computeLineParameters();
//        }
//
//        private void computeLineParameters() {
//            joiningVector.setLocation(farPoint.x-nearPoint.x, farPoint.y-nearPoint.y);
//            A=-C*(farPoint.y-nearPoint.y)/(nearPoint.x*farPoint.y-farPoint.x*nearPoint.y);
//            B=-C*(farPoint.x-nearPoint.x)/(nearPoint.y*farPoint.x-farPoint.y*nearPoint.x);
//            n=(float)Math.sqrt(A*A+B*B);
//        }
//
//        void addEvent(BasicEvent e){
//            float d=line.normalDistanceTo(e);
//            if(Math.abs(d)>lineWidth) return;
////            if(d>0) System.out.print("+"); else System.out.print("-");
//            numEvents++;
//            lastEventTime=e.timestamp;
//            events[eventPointer++]=e;
//            if(eventPointer==NUM_EVENTS) eventPointer=0;
//            nearPoint.x+=positionMixingFactor*d;
//            computeLineParameters();
//        }
//
//        /** @return true if line has sufficient support */
//        boolean isSupported(){
//            return true;
//        }
//
//        float normalDistanceTo(BasicEvent event){
////            event=new BasicEvent();
////            event.x=64;
////            event.y=64;
//            float d=-(A*event.x+B*event.y+C)/n; // distance is now positive to right
////            System.out.println(String.format("d=%.2f",d));
//            return d;
//        }
//
//        public void annotate(GLAutoDrawable drawable) {
//            if(!isFilterEnabled()) return;
//            final float LINE_WIDTH=5f; // in pixels
//            GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
//            gl.glLineWidth(LINE_WIDTH);
//            gl.glColor3i(255,255,255);
//            gl.glBegin(GL.GL_LINES);
//            gl.glVertex2f(farPoint.x,farPoint.y);
//            gl.glVertex2f(nearPoint.x,nearPoint.y);
//            gl.glEnd();
//
//        }
//    }

//    public float getAngleMixingFactor() {
//        return angleMixingFactor;
//    }
//
//    public void setAngleMixingFactor(float angleMixingFactor) {
//        if(angleMixingFactor<0) angleMixingFactor=0; else if(angleMixingFactor>1) angleMixingFactor=1;
//        this.angleMixingFactor = angleMixingFactor;
//        prefs.putFloat("LineTracker.angleMixingFactor",angleMixingFactor);
//    }
//
//    public float getPositionMixingFactor() {
//        return positionMixingFactor;
//    }
//
//    public void setPositionMixingFactor(float positionMixingFactor) {
//        if(positionMixingFactor<0) positionMixingFactor=0; else if(positionMixingFactor>1) positionMixingFactor=1;
//        this.positionMixingFactor = positionMixingFactor;
//        prefs.putFloat("LineTracker.positionMixingFactor",positionMixingFactor);
//    }
//
//    public float getLineWidth() {
//        return lineWidth;
//    }
//
//    public void setLineWidth(float lineWidth) {
//        this.lineWidth = lineWidth;
//        prefs.putFloat("LineTracker.lineWidth",lineWidth);
//    }
