/*
 * Info.java
 *
 * Created on September 28, 2007, 7:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright September 28, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventio.AEDataFile;
import ch.unizh.ini.caviar.eventio.AEFileInputStream;
import ch.unizh.ini.caviar.eventio.AEInputStreamInterface;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.graphics.AEPlayerInterface;
import ch.unizh.ini.caviar.graphics.AEViewer;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import com.sun.opengl.util.*;
import java.awt.Graphics2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.io.File;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

/**
 * Annotates the rendered data stream canvas
 with additional information like a
 clock with absolute time, a bar showing instantaneous activity rate,
 a graph showing historical activity over the file, etc. These are enabled by flags of the filter.
 * @author tobi
 */
public class Info extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {
    
    DateFormat timeFormat=DateFormat.getTimeInstance();
    DateFormat dateFormat=DateFormat.getDateInstance();
    Date dateInstance=new Date();
    Calendar calendar=Calendar.getInstance();
    
    private boolean analogClock=getPrefs().getBoolean("Into.analogClock",true);
    {setPropertyTooltip("analogClock","show normal circular clock");}
    private boolean digitalClock=getPrefs().getBoolean("Into.digitalClock",true);
    {setPropertyTooltip("digitalClock","show digital clock");}
    private boolean date=getPrefs().getBoolean("Into.date",true);
    {setPropertyTooltip("date","show date");}
    private boolean absoluteTime=getPrefs().getBoolean("Info.absoluteTime",true);
    {setPropertyTooltip("absoluteTime","enable to show absolute time, disable to show timestmp time (usually relative to start of recording");}
    
    private long dataFileTimestampStartTimeMs=0;
    private long wrappingCorrectionMs=0;
    private long absoluteStartTime=0;
    private long relativeTimeInFileMs=0;
    
    /** Creates a new instance of Info for the chip
     @param chip the chip object
     */
    public Info(AEChip chip) {
        super(chip);
        calendar.setLenient(true); // speed up calendar
    }
    
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkPropSupport();
        if(in!=null && in.getSize()>0){
            relativeTimeInFileMs=(in.getLastTimestamp()-dataFileTimestampStartTimeMs)/1000;
        }
        return in;
    }
    boolean addedSupport=false;
    
    void checkPropSupport(){
        if(addedSupport) return;
        AEPlayerInterface player=chip.getAeViewer().getAePlayer();
        if(player!=null){
            AEFileInputStream in=(AEFileInputStream)(player.getAEInputStream());
            if(in!=null){
                in.getSupport().addPropertyChangeListener(this);
                dataFileTimestampStartTimeMs=in.getFirstTimestamp();
                log.info("added ourselves for PropertyChangeEvents from "+in);
                addedSupport=true;
            }
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
    }
    
    public void initFilter() {
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    GLU glu=null;
    GLUquadric wheelQuad;
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled()) return;
        GL gl=drawable.getGL();
//        gl.glColor3f(1,1,1);
//        gl.glRasterPos3f(0,0,0);
//        if(rewound){
//            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_12, "rewound");
//            rewound=false;
//        }else{
//            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_12, String.format("%.3f",timestampTimeSeconds()));
//        }
        long t=relativeTimeInFileMs+wrappingCorrectionMs;
        if(absoluteTime) t+=absoluteStartTime;
        drawClock(gl,t);
    }
    
    private volatile boolean rewound=false;
    
    public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName().equals("rewind")){
            log.info("rewind PropertyChangeEvent received by "+this+" from "+evt.getSource());
            rewound=true;
            wrappingCorrectionMs=0;
            absoluteStartTime=getAbsoluteStartingTimeMsFromFile();
        }else if(evt.getPropertyName().equals("wrappedTime")){
            wrappingCorrectionMs+=(long)(1L<<32L)/1000; // fixme
        }else if(evt.getPropertyName().equals("init")){
            absoluteStartTime=getAbsoluteStartingTimeMsFromFile();
        }
    }
    
    /** @return start of logging time in ms, i.e., in "java" time, since 1970 */
    private long getAbsoluteStartingTimeMsFromFile(){
        File f=chip.getAeViewer().getCurrentFile();
        if(f==null){
            return 0;
        }
        try{
            String fn=f.getName();
            String dateStr=fn.substring(fn.indexOf('-')+1); // guess that datestamp is right after first - which follows Chip classname
            Date date=AEViewer.loggingFilenameDateFormat.parse(dateStr);
            return date.getTime();
        }catch(Exception e){
            log.warning(e.toString());
            return 0;
        }
    }
    
    private void drawClock(GL gl, long t){
        final int radius=20, hourLen=10, minLen=19, secLen=7;
        calendar.setTimeInMillis(t);
        
        gl.glColor3f(1,1,1);
        if(analogClock){
            // draw clock circle
            if(glu==null) glu=new GLU();
            if(wheelQuad==null) wheelQuad = glu.gluNewQuadric();
            gl.glPushMatrix();
            {
                gl.glTranslatef(radius+2, radius+6,0); // clock center
                glu.gluQuadricDrawStyle(wheelQuad,GLU.GLU_FILL);
                glu.gluDisk(wheelQuad,radius,radius+0.5f,24,1);
                
                // draw hour, minute, second hands
                // each hand has x,y components related to periodicity of clock and time
                
                gl.glColor3f(1,1,1);
                
                // second hand
                gl.glLineWidth(2f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0,0);
                double a=2*Math.PI*calendar.get(Calendar.SECOND)/60;
                float x=secLen*(float)Math.sin(a);
                float y=secLen*(float)Math.cos(a);
                gl.glVertex2f(x,y);
                gl.glEnd();
                
                // minute hand
                gl.glLineWidth(4f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0,0);
                int minute=calendar.get(Calendar.MINUTE);
                 a=2*Math.PI*minute/60;
                x=minLen*(float)Math.sin(a);
                y=minLen*(float)Math.cos(a); // y= + when min=0, pointing at noon/midnight on clock
                gl.glVertex2f(x,y);
                gl.glEnd();
                
                // hour hand
                gl.glLineWidth(6f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(0,0);
                  a=2*Math.PI*(calendar.get(Calendar.HOUR)+minute/60.0)/12; // a=0 for midnight, a=2*3/12*pi=pi/2 for 3am/pm, etc
                x=hourLen*(float)Math.sin(a);
                y=hourLen*(float)Math.cos(a);
                gl.glVertex2f(x,y);
                gl.glEnd();
            }
            gl.glPopMatrix();
        }
        
        if(digitalClock){
            gl.glPushMatrix();
            gl.glRasterPos3f(0,0,0);
            GLUT glut=chip.getCanvas().getGlut();
            dateInstance.setTime((long)t);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,timeFormat.format(t));
            if(date){
                glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,dateFormat.format(t));
            }
            gl.glPopMatrix();
        }
    }
    
    public boolean isAnalogClock() {
        return analogClock;
    }
    
    public void setAnalogClock(boolean analogClock) {
        this.analogClock = analogClock;
        getPrefs().putBoolean("Info.analogClock",analogClock);
    }
    
    public boolean isDigitalClock() {
        return digitalClock;
    }
    
    public void setDigitalClock(boolean digitalClock) {
        this.digitalClock = digitalClock;
        getPrefs().putBoolean("Info.digitalClock",digitalClock);
    }
    
    public boolean isDate() {
        return date;
    }
    
    public void setDate(boolean date) {
        this.date = date;
        getPrefs().putBoolean("Info.date",date);
    }

    public boolean isAbsoluteTime() {
        return absoluteTime;
    }

    public void setAbsoluteTime(boolean absoluteTime) {
        this.absoluteTime = absoluteTime;
        getPrefs().putBoolean("Info.absoluteTime",absoluteTime);
   }
    
}
