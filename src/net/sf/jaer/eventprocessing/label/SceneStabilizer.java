/*
 * SceneStabilizer.java (formerly MotionCompensator)
 *
 * Created on March 8, 2006, 9:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 8, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.label;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.filter.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
/**
 * Tries to compensate global image motion by using global motion metrics to redirect output events and (optionally) also
a mechanical pantilt unit, shifting them according to motion of input.
Two methods can be used 1) the global translational flow computed from DirectionSelectiveFilter, or 2) the optical gyro outputs from RectangularClusterTracker.
 *
 * @author tobi
 */
public class SceneStabilizer extends EventFilter2D implements FrameAnnotater{
    public static String getDescription(){
        return "Compenstates global scene translation to stabilize scene.";
    }
    /** Classses that compute scene shift.
     */
    public enum PositionComputer{
        RectangularClusterTracker, DirectionSelectiveFilter
    };
    private PositionComputer positionComputer=PositionComputer.valueOf(getPrefs().get("SceneStabilizer.positionComputer","RectangularClusterTracker"));
    

    {
        setPropertyTooltip("positionComputer","specifies which method is used to measure scene motion");
    }
    private float gain=getPrefs().getFloat("SceneStabilizer.gain",1f);


    {
        setPropertyTooltip("gain","gain applied to scene position shift output to affect electronic or mechanical output");
    }
    private DirectionSelectiveFilter dirFilter; // used when using optical flow
    private RectangularClusterTracker clusterTracker; // used when tracking features
    private boolean feedforwardEnabled=getPrefs().getBoolean("SceneStabilizer.feedforwardEnabled",false);


    {
        setPropertyTooltip("feedforwardEnabled","enables motion computation on stabilized output of filter rather than input (only during use of DirectionSelectiveFilter)");
    }
    private boolean panTiltEnabled=getPrefs().getBoolean("SceneStabilizer.panTiltEnabled",false);


    {
        setPropertyTooltip("panTiltEnabled","enables use of pan/tilt servos for camera");
    }
    private boolean electronicStabilizationEnabled=getPrefs().getBoolean("SceneStabilizer.electronicStabilizationEnabled",true);


    {
        setPropertyTooltip("electronicStabilizationEnabled","stabilize by shifting events according to the PositionComputer");
    }
    private boolean rotationEnabled=getPrefs().getBoolean("SceneStabilizer.rotationEnabled",false);


    {
        setPropertyTooltip("rotationEnabled","compensate image rotation - not yet implemented");
    }
    private Point2D.Float shift=new Point2D.Float();
    private HighpassFilter filterX=new HighpassFilter(),  filterY=new HighpassFilter();
    private boolean flipContrast=false;


    {
        setPropertyTooltip("flipContrast","flips contrast of output events depending on x*y sign of motion - should maintain colors of edges");
    }
//    private float rotation=0;
//    private HighpassFilter filterRotation=new HighpassFilter();
    private final int SHIFT_LIMIT=30;
//    private final float PI2=(float)(Math.PI*2);
    private float cornerFreqHz=getPrefs().getFloat("SceneStabilizer.cornerFreqHz",0.1f);


    {
        setPropertyTooltip("cornerFreqHz","sets bandpass corner frequency in Hz - motion slower than this is ignored. Only for use of DirectionSelectiveFilter");
    }
    boolean evenMotion=true;
    private EventPacket ffPacket=null;
    private FilterChain filterChain;
    private boolean annotateEnclosedEnabled=getPrefs().getBoolean("SceneStabilizer.annotateEnclosedEnabled",true);
    

    {
        setPropertyTooltip("annotateEnclosedEnabled","showing tracking or motion filter output annotation of output, for setting up parameters of enclosed filters");
    }
    private PanTilt panTilt=null;

    /** Creates a new instance of SceneStabilizer */
    public SceneStabilizer(AEChip chip){
        super(chip);
        filterChain=new FilterChain(chip);

        dirFilter=new DirectionSelectiveFilter(chip);
        clusterTracker=new RectangularClusterTracker(chip);
        dirFilter.setAnnotationEnabled(false);

        filterChain.add(dirFilter);

        clusterTracker=new RectangularClusterTracker(chip);
        clusterTracker.setOpticalGyroEnabled(true);
        clusterTracker.setAnnotationEnabled(false); // annotation of cluster drawn in unshifted space and hard to see, clutters view.

        filterChain.add(clusterTracker);
        setEnclosedFilterChain(filterChain);

//        chip.getCanvas().addAnnotator(this); // we must remember to add ourselves to the Canvas for annotations
        setPositionComputer(positionComputer); // init filter enabled states
        initFilter(); // init filters for motion compensation
    }

    /** Using DirectionSelectiveFilter, the shift is computed by pure
    integration of the motion signal followed by a highpass filter to remove long term DC offsets.
    Using RectangularClusterTracker, the shift is computed by the optical gyro which tracks clusters and measures
    scene translation from a consensus of the tracked clusters.

    @param in
     */
    private void computeShift(EventPacket in){
        float shiftx=0, shifty=0;
        switch(positionComputer){
            case DirectionSelectiveFilter:
                Point2D.Float f=dirFilter.getTranslationVector(); // this is 'instantaneous' motion signal (as filtered by DirectionSelectiveFilter)
                int t=in.getLastTimestamp();
                float dtSec=in.getDurationUs()*1e-6f; // duration of this slice
                if(Math.abs(f.x)>Math.abs(f.y)){
                    evenMotion=f.x>0; // used to flip contrast
                }else{
                    evenMotion=f.y>0;
                }
                shiftx+=-(float)(gain*f.x*dtSec); // this is integrated shift
                shifty+=-(float)(gain*f.y*dtSec);
                shift.x=(filterX.filter(shiftx,t)); // these are highpass filtered shifts
                shift.y=(filterY.filter(shifty,t));
                break;
            case RectangularClusterTracker:
                Point2D.Float s=clusterTracker.getOpticalGyroValue();
                shift.x=filterX.filter(-s.x,in.getLastTimestamp());
                shift.y=filterY.filter(-s.y,in.getLastTimestamp()); // shift is negative of gyro value.
        }
    }

    float limit(float nsx){
        if(nsx>SHIFT_LIMIT){
            nsx=SHIFT_LIMIT;
        }else if(nsx<-SHIFT_LIMIT){
            nsx=-SHIFT_LIMIT;
        }
        return nsx;
    }

    public void annotate(GLAutoDrawable drawable){
        if(!isFilterEnabled()){
            return;
        }
        GL gl=drawable.getGL();
        if(gl==null){
            return;
        }

        if(annotateEnclosedEnabled){
            if(!isElectronicStabilizationEnabled()){
                clusterTracker.annotate(drawable);
            }else{
                gl.glPushMatrix();
                gl.glTranslatef(shift.x,shift.y,0);
                clusterTracker.annotate(drawable);
                gl.glPopMatrix();
            }
        }
        if(isElectronicStabilizationEnabled()){
            gl.glPushMatrix();
            gl.glColor3f(1,0,0);
            gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
            gl.glLineWidth(.3f);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0,0);
            gl.glVertex2f(shift.x,shift.y);
            gl.glEnd();
            gl.glPopMatrix();

            gl.glPushMatrix(); // go back to origin

            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(shift.x,shift.y);
            gl.glVertex2f(chip.getSizeX()+shift.x,shift.y);
            gl.glVertex2f(chip.getSizeX()+shift.x,chip.getSizeY()+shift.y);
            gl.glVertex2f(shift.x,chip.getSizeY()+shift.y);
            gl.glEnd();

            // xhairs - these are drawn in unshifted frame to allow monitoring of stability of image
            gl.glBegin(GL.GL_LINES);
            gl.glColor3f(0,0,1);
            // vert xhair line
            gl.glVertex2f(chip.getSizeX()/2,0);
            gl.glVertex2f(chip.getSizeX()/2,chip.getSizeY());

            // horiz xhair line
            gl.glVertex2f(0,chip.getSizeY()/2);
            gl.glVertex2f(chip.getSizeX(),chip.getSizeY()/2);

            gl.glEnd();

            gl.glPopMatrix();
        }
    }

    public void annotate(Graphics2D g){
    }

    public void annotate(float[][][] frame){
    }

    public float getGain(){
        return gain;
    }

    public void setGain(float gain){
        if(gain<0){
            gain=0;
        }else if(gain>100){
            gain=100;
        }
        this.gain=gain;
        getPrefs().putFloat("SceneStabilizer.gain",gain);
    }

    public void setCornerFreqHz(float freq){
        cornerFreqHz=freq;
        filterX.set3dBFreqHz(freq);
        filterY.set3dBFreqHz(freq);
        getPrefs().putFloat("SceneStabilizer.cornerFreqHz",freq);
    }

    public float getCornerFreqHz(){
        return cornerFreqHz;
    }

    public Object getFilterState(){
        return null;
    }

    public void resetFilter(){
        dirFilter.resetFilter();
        clusterTracker.resetFilter();
        setCornerFreqHz(cornerFreqHz);
        filterX.setInternalValue(0);
        filterY.setInternalValue(0);
        if(out==null){
            out=new EventPacket(TypedEvent.class);
        }
        shift.x=0;
        shift.y=0;
        if(isPanTiltEnabled()){
            try{
                panTilt.setPanTiltValues(.5f,.5f);
            }catch(HardwareInterfaceException ex){
                log.warning(ex.toString());
                panTilt.close();
            }
        }
    }

    public void initFilter(){
        panTilt=new PanTilt();
        resetFilter();
    }

    public boolean isFlipContrast(){
        return flipContrast;
    }

    public void setFlipContrast(boolean flipContrast){
        this.flipContrast=flipContrast;
    }

    public EventPacket filterPacket(EventPacket in){
        if(in==null){
            return null;
        }
        if(!filterEnabled){
            return in;
        }
//        int lastTimeStamp=in.getLastTimestamp();
        if(feedforwardEnabled&&isElectronicStabilizationEnabled()){
            if(ffPacket==null){
                ffPacket=new EventPacket(in.getEventClass());
            }
            int sx=chip.getSizeX(), sy=chip.getSizeY();
            int dx=Math.round(shift.x), dy=Math.round(shift.y);
            OutputEventIterator oi=ffPacket.outputIterator();
            for(Object o:in){
                PolarityEvent e=(PolarityEvent)o;
                int x=(e.x+dx);
                if(x<0|x>=sx){
                    continue;
                }
                int y=(e.y+dy);
                if(y<0||y>=sy){
                    continue;
                }
                PolarityEvent oe=(PolarityEvent)oi.nextOutput();
                oe.copyFrom(e);
            }
            in=ffPacket;
        }
        getEnclosedFilterChain().filterPacket(in);
//        switch(positionComputer){
//            case DirectionSelectiveFilter:
//                dir=enclosedFilter.filterPacket(in);
//                break;
//            case RectangularClusterTracker:
//                dir=clusterTracker.filterPacket(in);
//        }

        computeShift(in);

        if(isElectronicStabilizationEnabled()){
            int dx=Math.round(shift.x), dy=Math.round(shift.y);
            int sizex=chip.getSizeX()-1;
            int sizey=chip.getSizeY()-1;
            checkOutputPacketEventType(in);
            int n=in.getSize();
            short nx, ny;
            OutputEventIterator outItr=out.outputIterator();
            for(Object o:in){
                PolarityEvent ev=(PolarityEvent)o;
                nx=(short)(ev.x+dx);
                if(nx>sizex||nx<0){
                    continue;
                }
                ny=(short)(ev.y+dy);
                if(ny>sizey||ny<0){
                    continue;
                }
                ev.x=nx;
                ev.y=ny;
                if(!flipContrast){
                    outItr.nextOutput().copyFrom(ev);
                }else{
                    if(evenMotion){
                        ev.type=(byte)(1-ev.type); // don't let contrast flip when direction changes, try to stabilze contrast  by flipping it as well
                        ev.polarity=ev.polarity==PolarityEvent.Polarity.On?PolarityEvent.Polarity.Off:PolarityEvent.Polarity.On;
                    }
                    outItr.nextOutput().copyFrom(ev);
                }
            }
        }

        if(isPanTiltEnabled()){ // mechanical pantilt
            try{
                // mechanical pantilt
                // assume that pan of 1 takes us 180 degrees and that the sensor has 45 deg FOV,
                // then 1 pixel will require only 45/180/size pan
                final float lensFocalLengthMm=8;
                final float factor=(float)(chip.getPixelWidthUm()/1000/lensFocalLengthMm/Math.PI);
                panTilt.setPanTiltValues(.5f-shift.x*getGain()*factor,.5f-shift.y*getGain()*factor);
            }catch(HardwareInterfaceException ex){
                log.warning("setting pantilt: "+ex);
                panTilt.close();
            }
        }

        if(isElectronicStabilizationEnabled()){
            return out;
        }else{
            return in;
        }
    }

    @Override
    public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        setPositionComputer(positionComputer); // reflag enabled/disabled state of motion computation
    }

    public boolean isFeedforwardEnabled(){
        return feedforwardEnabled;
    }

    /** true to apply current shift values to input packet events. This does a kind of feedback compensation
     */
    public void setFeedforwardEnabled(boolean feedforwardEnabled){
        this.feedforwardEnabled=feedforwardEnabled;
        getPrefs().putBoolean("SceneStabilizer.feedforwardEnabled",feedforwardEnabled);
    }

    public boolean isRotationEnabled(){
        return rotationEnabled;
    }

    public void setRotationEnabled(boolean rotationEnabled){
        this.rotationEnabled=rotationEnabled;
        getPrefs().putBoolean("SceneStabilizer.rotationEnabled",rotationEnabled);
    }

    /** Method used to compute shift.
     * @return the positionComputer
     */
    public PositionComputer getPositionComputer(){
        return positionComputer;
    }

    /**
    Chooses how the current position of the scene is compuated.
     * @param positionComputer the positionComputer to set
     */
    synchronized public void setPositionComputer(PositionComputer positionComputer){
        this.positionComputer=positionComputer;
        getPrefs().put("SceneStabilizer.positionComputer",positionComputer.toString());
        switch(positionComputer){
            case DirectionSelectiveFilter:
                dirFilter.setFilterEnabled(true);
                clusterTracker.setFilterEnabled(false);
                break;
            case RectangularClusterTracker:
                clusterTracker.setFilterEnabled(true);
                dirFilter.setFilterEnabled(false);
        }
    }

    /** The global translational shift applied to output, computed by enclosed FilterChain.
     * @return the x,y shift
     */
    public Point2D.Float getShift(){
        return shift;
    }

    /**
     * @param shift the shift to set
     */
    public void setShift(Point2D.Float shift){
        this.shift=shift;
    }

    /**
     * @return the annotateEnclosedEnabled
     */
    public boolean isAnnotateEnclosedEnabled(){
        return annotateEnclosedEnabled;
    }

    /**
     * @param annotateEnclosedEnabled the annotateEnclosedEnabled to set
     */
    public void setAnnotateEnclosedEnabled(boolean annotateEnclosedEnabled){
        this.annotateEnclosedEnabled=annotateEnclosedEnabled;
        getPrefs().putBoolean("SceneStabilizer.annotateEnclosedEnabled",annotateEnclosedEnabled);
    }

    /**
     * @return the panTiltEnabled
     */
    public boolean isPanTiltEnabled(){
        return panTiltEnabled;
    }

    /** Enables use of pan/tilt servo controller for camera for mechanical stabilization.

     * @param panTiltEnabled the panTiltEnabled to set
     */
    public void setPanTiltEnabled(boolean panTiltEnabled){
        this.panTiltEnabled=panTiltEnabled;
        getPrefs().putBoolean("SceneStabilizer.panTiltEnabled",panTiltEnabled);
        if(!panTiltEnabled){
            try{
                panTilt.setPanTiltValues(.5f,.5f);
            }catch(HardwareInterfaceException ex){
                log.warning(ex.toString());
                panTilt.close();
            }
        }
    }

    /**
     * @return the electronicStabilizationEnabled
     */
    public boolean isElectronicStabilizationEnabled(){
        return electronicStabilizationEnabled;
    }

    /**
     * @param electronicStabilizationEnabled the electronicStabilizationEnabled to set
     */
    public void setElectronicStabilizationEnabled(boolean electronicStabilizationEnabled){
        this.electronicStabilizationEnabled=electronicStabilizationEnabled;
        getPrefs().putBoolean("SceneStabilizer.electronicStabilizationEnabled",electronicStabilizationEnabled);
    }
}
