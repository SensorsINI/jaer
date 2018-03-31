/*
 * EyeTracker.java
 *
 * Created on February 18, 2006, 3:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright February 18, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.projects.eyetracker;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilterDataLogger;
import net.sf.jaer.graphics.EyeTarget;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * A filter whose underlying model rims (pupil and iris) with a position and
 * radius and rimThickness, which is pushed around by events.
 *
 * @author tobi
 */
@Description("Experimental pupil eye tracker")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class EyeTracker extends EventFilter2D implements Observer, FrameAnnotater {

//    static Preferences prefs=Preferences.userNodeForPackage(EyeTracker.class);
//    ChipRenderer renderer;
    private float pupilRadius = getPrefs().getFloat("EyeTracker.pupilRadius", 10f);
    private float irisRadius = getPrefs().getFloat("EyeTracker.irisRadius", 20f);
//    private float eyeLidDistance=prefs.getFloat("EyeTracker.eyeLidDistance",20f);
    private float rimThickness = getPrefs().getFloat("EyeTracker.rimThickness", 4f);
    private float positionMixingFactor = getPrefs().getFloat("EyeTracker.positionMixingFactor", 0.005f); // amount each event moves COM of cluster towards itself
    private float scalingMixingFactor = getPrefs().getFloat("EyeTracker.scalingMixingFactor", 0.001f);

//    private float blinkMixingFactor=prefs.getFloat("EyeTracker.blinkMixingFactor",0.05f);
    private boolean scalingEnabled = getPrefs().getBoolean("EyeTracker.scalingEnabled", false);

//    private float blinkThreshold=prefs.getFloat("EyeTracker.blinkThreshold",0.9f);
    private float qualityMixingFactor = getPrefs().getFloat("EyeTracker.qualityMixingFactor", 0.05f);
    private float qualityThreshold = getPrefs().getFloat("EyeTracker.qualityThreshold", 0.15f);

    private float acquisitionMultiplier = getPrefs().getFloat("EyeTracker.acquisitionMultiplier", 2f); // rim thickness multiplied by this when quality of tracking degrades
    private boolean dynamicAcquisitionSize = getPrefs().getBoolean("EyeTracker.dynamicAcquisitionSize", false);

    private boolean logDataEnabled = false;
//    private float eyelidRejectionAngleDeg=getPrefs().getFloat("EyeTracker.eyelidRejectionAngleDeg",45);

    private boolean showEyeTarget = false;

    private Point2D.Float position = new Point2D.Float(); // the eye position in pixels
    float minX, minY, maxX;

    private StatComputer statComputer;

    private float maxY;

    private float chipArea = 1, modelAreaRatio = 1;

    private final float PI2 = (float) (Math.PI * 2);

    private Distance distance = new Distance();

//    private BlinkDetector blinkDetector;
    private TrackingQualityDetector trackingQualityDetector;

    private GLU glu;
    private boolean hasBlendChecked = false;
    private boolean hasBlend = false;

    /**
     * Creates a new instance of EyeTracker
     *
     * @param chip the chip we are eye tracking on
     */
    public EyeTracker(AEChip chip) {
        super(chip);
        chip.addObserver(this);
//        blinkDetector=new BlinkDetector();
        trackingQualityDetector = new TrackingQualityDetector();
        statComputer = new StatComputer();
        initFilter();
        setPropertyTooltip("pupilRadius", "The pupil radius in pixels");
        setPropertyTooltip("irisRadius", "The iris radius in pixels");
        setPropertyTooltip("rimThickness", "The pupil and iris disc thicknesses in pixels");
        setPropertyTooltip("positionMixingFactor", "The mixing factor for eye position, increase to track faster and noisier");
        setPropertyTooltip("scalingMixingFactor", "The mixing factor for scaling the model radius, increase to scale faster");
        setPropertyTooltip("scalingEnabled", "Enables dynamic scaling of model radius");
        setPropertyTooltip("qualityMixingFactor", "The mixing factor for quality metric, increase to speed up quality measurement");
        setPropertyTooltip("qualityThreshold", "The threshold quality (ratio events inside/outside model) for decreasing rim thickness");
        setPropertyTooltip("acquisitionMultiplier", "The factor to increase radius when tracking is lost");
        setPropertyTooltip("dynamicAcquisitionSize", "Enables dynamic acquisition scaling");
        setPropertyTooltip("logDataEnabled", "logs eye tracker data to the startup folder file called EyeTracker.txt");
        setPropertyTooltip("showEyeTarget", "shows a fixation target window with random positions for possible calibration");
        setPropertyTooltip("targetSpeed", "the speed of the eye target positions; this is actually the mean time in ms for Poisson-distributed intervals for changes in target position");
    }

    // used for holding distance metrics
    class Distance {

        float r, theta;
        float dx, dy;
    }

//    /** detects blinks by measuring dynamically the ratio of events inside the model (the disk) to events outside.
//     */
//    class BlinkDetector{
//        float fractionInside=.5f;
//        boolean blinkOccurring=false;
//        /** call this with a flag marking event inside (true) or outside (false) the eye model
//         */
//        public void processEvent(boolean insideModel){
//            if(insideModel){
//                fractionInside=(1-blinkMixingFactor)*fractionInside+blinkMixingFactor;
//            }else{
//                fractionInside=(1-blinkMixingFactor)*fractionInside;
//            }
////            System.out.println("fractionInside="+fractionInside);
//            blinkOccurring= fractionInside<blinkThreshold;
//        }
//        public boolean isBlink(){
//            return blinkOccurring;
//        }
//    }
    /**
     * detects quality of tracking by measuring dynamically the ratio of events
     * inside the model (the disk) to events outside. This value is normalized
     * by the relative area of the model (the iris disk) to the chip area.
     * Uniformly distributed events should produce just exactly the area ratio
     * of events inside the model, resulting in a measure "1"; if more events
     * are inside the model than predicted by its area then the quality measure
     * will be larger than 1.
     * <p>
     * If the ratio inside becomes too low, we increase the model area (the disk
     * rim thicknesses)
     */
    class TrackingQualityDetector {

        float fractionInside = 0;
        float quality = 1;

        /**
         * call this with a flag marking event inside (true) or outside (false)
         * the eye model
         */
        void processEvent(boolean insideModel) {
            if (insideModel) {
                fractionInside = (1 - qualityMixingFactor) * fractionInside + qualityMixingFactor;
            } else {
                fractionInside = (1 - qualityMixingFactor) * fractionInside;
            }
            quality = fractionInside / modelAreaRatio;
        }

        boolean isTrackingOK() {
            return quality > qualityThreshold;
        }

        float getQuality() {
            return quality;
        }
    }

    /**
     * Computes statisitics of tracking, in order to give a measure of gaze
     */
    class StatComputer {

        float maxX = 0, minX = chip.getSizeX();
        float maxY = 0, minY = chip.getSizeY();

        void reset() {
            minX = chip.getSizeX();
            maxX = 0;
            minY = chip.getSizeY();
            maxX = 0;
        }

        void processPosition(Point2D.Float p) {
            if (p.x < minX) {
                minX = p.x;
            } else if (p.x > maxX) {
                maxX = p.x;
            }
            if (p.y < minY) {
                minY = p.y;
            } else if (p.y > maxY) {
                maxY = p.y;
            }
        }

        float getGazeX() {
            return (position.x - minX) / (maxX - minX);
        }

        float getGazeY() {
            return (position.y - minY) / (maxY - minY);
        }
    }

    public void initFilter() {
//        state=State.INITIAL;
        // when chip is assigned in AERetina, it doesn't really exist yet, therefore sizes will not be correct
//        position.x=chip.getSizeX()/2;
//        position.y=chip.getSizeY()/2;
        chipArea = chip.getSizeX() * chip.getSizeY();
        pupilRadius = getPrefs().getFloat("EyeTracker.pupilRadius", 5f);
        irisRadius = getPrefs().getFloat("EyeTracker.irisRadius", 20f);
        rimThickness = getPrefs().getFloat("EyeTracker.rimThickness", 3f);
        statComputer.reset();
        computeModelArea();
//        setEyeRadius(getEyeRadius());
    }

    synchronized public EventPacket filterPacket(EventPacket in) {
        // for each event, see which cluster it is closest to and appendCopy it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        supportCount=0; outCount=0; inCount=0;
        for (Object o : in) {
            BasicEvent ev = (BasicEvent) o;
            if (ev.isSpecial()) {
                continue;
            }
            processCircleEvent(ev, pupilRadius);
            processCircleEvent(ev, irisRadius);
            scaleModel(distance.r);
        }
        if (isLogDataEnabled()) {
            if (target == null) {
                dataLogger.log(String.format("%d %f %f %f", in.getLastTimestamp(), position.x, position.y, trackingQualityDetector.getQuality()));
            } else {
                if (target.getMousePosition() != null) {
                    dataLogger.log(String.format("%d %f %f %f %f %f %d %d", in.getLastTimestamp(), position.x, position.y, trackingQualityDetector.getQuality(), target.getTargetX(), target.getTargetY(), target.getMousePosition().x, target.getMousePosition().y));
                } else {
                    dataLogger.log(String.format("%d %f %f %f %f %f %d %d", in.getLastTimestamp(), position.x, position.y, trackingQualityDetector.getQuality(), target.getTargetX(), target.getTargetY(), 0, 0));
                }
            }
        }
        return in;
    }

    /**
     * @param ev the event to process
     * @param radius the radius of the present circle
     */
    void processCircleEvent(BasicEvent ev, float radius) {
        computeDistanceFromEyeToEvent(ev); // leaves result in distance, points from eye to ev

        float outer, inner;

        float rim = getEffectiveRimThickness(); // this is disk thickness including quality metric

        // we update eye position here.
        // we know the polar r distance from the eye position to the event along with theta and dx, dy
        // we update the eye position depending on location of event relative to its closest distance to center of eye.
        // if the event lies outside the doughnut rim, we ignore it
        outer = radius + rim;
        inner = radius - rim;

        if (inner < 0) {
            inner = 0;
        }
        if (distance.r > outer || distance.r < inner) {
            return;
        }

//        blinkDetector.processEvent(true); // events inside count against blink
//        if(blinkDetector.isBlink()){
//            // if there is blink, don't update position or size
//            return;
//        }
//        if(distance.r>radius){
//            outCount++;
//        }else if(distance.r<radius){
//            inCount++;
//        }
        // the event is in the doughnut rim.
        // we update the eye position by shifting the eye position in the direction theta by the positionMixingFactor times
        // the distance from the event to the middle of the rim.
        // we compute the location on the rim of the intersectin of the line connecting the eye position to the event
        // actually we directly compute the distance dxrim and dyrim from the event to the rim
//        float deg=(float)(distance.theta*180/Math.PI);
//        if( deg>90-eyelidRejectionAngleDeg && deg<90+eyelidRejectionAngleDeg)
//            return;
        float dxrim = (float) (distance.dx - Math.cos(distance.theta) * radius);
        float dyrim = (float) (distance.dy - Math.sin(distance.theta) * radius);

//        System.out.print("eye pos old x,y="+position.x+","+position.y);
        setX(position.x + dxrim * positionMixingFactor);
        setY(position.y + dyrim * positionMixingFactor);
        statComputer.processPosition(position);

    }

    final float maxPupil = 15, maxIris = 25;

    /**
     * scale model size based on event and tracking quality
     */
    void scaleModel(float eventDistance) {
        if (!scalingEnabled) {
            return;
        }
        // If event outside iris model, scale up iris.
        // If event outside pupil but inside iris, scale up pupil and scale down iris.
        // If event inside pupil scale down pupil model.

        if (eventDistance > irisRadius && eventDistance < irisRadius + rimThickness) {
            irisRadius = scaleRadius(eventDistance, irisRadius);
            if (irisRadius > maxIris) {
                irisRadius = maxIris;
            }
            computeModelArea();
            return;
        }
        if (eventDistance < pupilRadius) {
            pupilRadius = scaleRadius(eventDistance, pupilRadius);
            return;
        }
        float avg = (irisRadius + pupilRadius) / 2;
        if (eventDistance > avg) {
            irisRadius = scaleRadius(eventDistance, irisRadius);
            computeModelArea();
        } else {
            pupilRadius = scaleRadius(eventDistance, pupilRadius);
            if (pupilRadius > maxPupil) {
                pupilRadius = maxPupil;
            }
        }
    }

    // computes ratio of outer to inner model areas, used for dynamic scaling
    float outerToInnerAreaRatio(float radius) {
//        return 1;
        float thickness = rimThickness / 2;
        float inner = radius - rimThickness / 2;
        if (inner < 0) {
            inner = radius / 2;
        }
        float outer = radius + thickness;
        float ratio = outer / inner; // this is >1
        return ratio;
    }

    private float scaleRadius(float eventDistance, float oldRadius) {
        // Each scaling is adjusted to account for relative area of model outside and inside the radius;
        // the area outside is larger and will tend to getString more events anyhow, so the scaling is multiplied by the ratio
        // outsideArea/insideArea for inside events and by the ratio insideArea/outsideArea for outside events
        float r = outerToInnerAreaRatio(oldRadius);
        float newRadius;
        if (eventDistance > oldRadius) {
            newRadius = oldRadius + scalingMixingFactor * (eventDistance - oldRadius) / r;
        } else {
            newRadius = oldRadius + scalingMixingFactor * (eventDistance - oldRadius) * r;
        }
        if (newRadius <= 1) {
            newRadius = 1;
        }
        return newRadius;
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
        initFilter();
        position.x=chip.getSizeX()/2;
        position.y=chip.getSizeY()/2;
    }

    public void update(Observable o, Object arg) {
        initFilter();
    }

    GLUquadric eyeQuad;

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        // blend may not be available depending on graphics mode or opengl version.
        float rim = getEffectiveRimThickness();
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        gl.glLineWidth(3);
        if (glu == null) {
            glu = new GLU();
        }
        if (eyeQuad == null) {
            eyeQuad = glu.gluNewQuadric();
        }

        gl.glPushMatrix();
        {
            gl.glTranslatef(position.x, position.y, 0);

            // draw disk
            if (!trackingQualityDetector.isTrackingOK()) {
                gl.glColor4f(1, 0, 0, .3f);
            } else {
                gl.glColor4f(0, 0, 1, .3f);
            }
            glu.gluQuadricDrawStyle(eyeQuad, GLU.GLU_FILL);
            glu.gluDisk(eyeQuad, pupilRadius - rim, pupilRadius + rim, 16, 1);

            // draw pupil rim
            gl.glColor4f(0, 0, 1, .7f);
            glu.gluQuadricDrawStyle(eyeQuad, GLU.GLU_FILL);
            glu.gluDisk(eyeQuad, pupilRadius - 1, pupilRadius + 1, 16, 1);

            // draw iris disk and rim
            // draw disk
            if (!trackingQualityDetector.isTrackingOK()) {
                gl.glColor4f(1, 0, 0, .3f);
            } else {
                gl.glColor4f(0, 0, 1, .3f);
            }
            glu.gluQuadricDrawStyle(eyeQuad, GLU.GLU_FILL);
            glu.gluDisk(eyeQuad, irisRadius - rim, irisRadius + rim, 16, 1);

            gl.glColor4f(0, 0, 1, .7f);
            glu.gluQuadricDrawStyle(eyeQuad, GLU.GLU_FILL);
            glu.gluDisk(eyeQuad, irisRadius - 1, irisRadius + 1, 16, 1);

//            // text annotations
//            //Write down the frequence and amplitude
//            int font = GLUT.BITMAP_HELVETICA_18;
//            gl.glColor3f(1,0,0);
//            gl.glTranslatef(0,0,0);
//            gl.glRasterPos3f(1,4,0);
//            chip.getCanvas().getGlut().glutBitmapString(font, String.format("fractionOutside=%.3f",1-blinkDetector.fractionInside));
        }
        gl.glPopMatrix();

        // show quality as bar that shows event-averaged fraction of events inside the model.
        // the red part shows eventInsideRatio is less than threshold, green shows good tracking
        gl.glLineWidth(5);
        gl.glBegin(GL2.GL_LINES);
        {
            final float SCREEN_FRAC_THRESHOLD_QUALITY = 0.1f;
            if (!trackingQualityDetector.isTrackingOK()) {
                // tracking bad, draw actual quality in red only
                gl.glColor3f(1, 0, 0);
                gl.glVertex2f(0, 1);
                gl.glVertex2f(trackingQualityDetector.quality * chip.getSizeX() * SCREEN_FRAC_THRESHOLD_QUALITY, 1);
            } else {
                // tracking is good, draw quality in red up to threshold, then in green for excess
                gl.glColor3f(1, 0, 0); // red bar up to qualityThreshold
                gl.glVertex2f(0, 1);
                float f = qualityThreshold * chip.getSizeX() * SCREEN_FRAC_THRESHOLD_QUALITY;
                gl.glVertex2f(f, 1); // 0 to threshold in green
                gl.glColor3f(0, 1, 0); // green for rest of bar
                gl.glVertex2f(f, 1); // threshold to quality in green
                gl.glVertex2f((qualityThreshold + trackingQualityDetector.quality) * chip.getSizeX() * SCREEN_FRAC_THRESHOLD_QUALITY, 1);
            }
        }
        gl.glEnd();

        if (isShowEyeTarget()) {
            gl.glPushMatrix();
            {
                float gazeX = statComputer.getGazeX() * chip.getSizeX();
                float gazeY = statComputer.getGazeY() * chip.getSizeY();
                gl.glTranslatef(gazeX, gazeY, 0);
                gl.glColor4f(0, 1, 0, .5f);
                glu.gluQuadricDrawStyle(eyeQuad, GLU.GLU_FILL);
                glu.gluDisk(eyeQuad, 0, 5, 16, 1);
            }
            gl.glPopMatrix();
            if (targetFrame != null) {
                target.display();
            }
        }
        if (hasBlend) {
            gl.glDisable(GL.GL_BLEND);
        }
    }

    /**
     * computes distance vector from eye position to event, i.e., dx>0, dy=0 and
     * theta (angle) will be zero if ev is to straight right of eye position
     *
     * @param ev the event
     */
    void computeDistanceFromEyeToEvent(BasicEvent ev) {
        distance.dx = ev.x - position.x;
        distance.dy = ev.y - position.y;
        distance.r = (float) Math.sqrt(distance.dx * distance.dx + distance.dy * distance.dy);
        distance.theta = (float) Math.atan2(distance.dy, distance.dx);
        if (distance.r > irisRadius + rimThickness || distance.r < pupilRadius - rimThickness || (distance.r > pupilRadius + rimThickness && distance.r < irisRadius - rimThickness)) {
            trackingQualityDetector.processEvent(false);
        } else {
            trackingQualityDetector.processEvent(true);
        }
    }

//    public float getX(){
//        return position.x;
//    }
//
//    public float getY(){
//        return position.y;
//    }
    public void setX(float x) {
        if (x < 0) {
            x = 0;
        } else if (x > chip.getSizeX()) {
            x = chip.getSizeX();
        }
        position.x = x;
    }

    public void setY(float y) {
        if (y < 0) {
            y = 0;
        } else if (y > chip.getSizeY()) {
            y = chip.getSizeY();
        }
        position.y = y;
    }

    public float getPupilRadius() {
        return pupilRadius;
    }

    /**
     * Sets initial value of eye (pupil or iris) radius
     *
     * @param eyeRadius the radius in pixels
     */
    public void setPupilRadius(float eyeRadius) {
        if (eyeRadius < 1f) {
            eyeRadius = 1f;
        } else if (eyeRadius > chip.getMaxSize() / 2) {
            eyeRadius = chip.getMaxSize() / 2;
        }
        this.pupilRadius = eyeRadius;
        getPrefs().putFloat("EyeTracker.pupilRadius", pupilRadius);
    }

    public float getRimThickness() {
        return rimThickness;
    }

    /**
     * sets thickness of disk of sensitivity around model circles; affects both
     * pupil and iris model. This number is summed and subtracted from radius to
     * give disk of sensitivity.
     *
     * @param rimThickness the thickness in pixels
     */
    public void setRimThickness(float rimThickness) {
        this.rimThickness = rimThickness;
        if (rimThickness < 1) {
            rimThickness = 1;
        } else if (rimThickness > pupilRadius) {
            rimThickness = pupilRadius;
        }
        getPrefs().putFloat("EyeTracker.rimThickness", rimThickness);
    }

    /**
     * @return effective rim thickness given quality of tracking
     */
    float getEffectiveRimThickness() {
        final float MAX_RIM_THICKNESS = 10;
        if (trackingQualityDetector.isTrackingOK()) {
            return rimThickness;
        } else {
            if (!dynamicAcquisitionSize) {
                return rimThickness * acquisitionMultiplier;
            } else {
                float r = rimThickness * acquisitionMultiplier * qualityThreshold / trackingQualityDetector.quality;
                if (r > MAX_RIM_THICKNESS) {
                    r = MAX_RIM_THICKNESS;
                }
                return r;
            }
        }
    }

    public float getPositionMixingFactor() {
        return positionMixingFactor;
    }

    float m1 = 1 - positionMixingFactor;

    /**
     * Sets mixing factor for eye position
     *
     * @param mixingFactor 0-1
     */
    public void setPositionMixingFactor(float mixingFactor) {
        if (mixingFactor < 0) {
            mixingFactor = 0;
        }
        if (mixingFactor > 1) {
            mixingFactor = 1f;
        }
        this.positionMixingFactor = mixingFactor;
        m1 = 1 - mixingFactor;
        getPrefs().putFloat("EyeTracker.positionMixingFactor", mixingFactor);
    }

    /**
     * gets "mixing factor" for dynamic scaling of model size. Setting a small
     * value means model size updates slowly
     *
     * @return scalingMixingFactor 0-1 value, 0 means no update, 1 means
     * immediate update
     */
    public float getScalingMixingFactor() {
        return scalingMixingFactor;
    }

    /**
     * sets mixing factor for dynamic resizing of eye
     *
     * @param factor mixing factor 0-1
     */
    public void setScalingMixingFactor(float factor) {
        this.scalingMixingFactor = factor;
        getPrefs().putFloat("EyeTracker.scalingMixingFactor", scalingMixingFactor);
    }

    public float getIrisRadius() {
        return irisRadius;
    }

    /**
     * Sets radius of outer ring of eye model (the iris)
     *
     * @param irisRadius in pixels
     */
    public void setIrisRadius(float irisRadius) {
        this.irisRadius = irisRadius;
        computeModelArea();
        getPrefs().putFloat("EyeTracker.irisRadius", irisRadius);
    }

//    public float getEyeLidDistance() {
//        return eyeLidDistance;
//    }
//
//    public void setEyeLidDistance(float eyeLidDistance) {
//        this.eyeLidDistance = eyeLidDistance;
//        prefs.putFloat("EyeTracker.eyeLidDistance",eyeLidDistance);
//    }
    public boolean isScalingEnabled() {
        return scalingEnabled;
    }

    /**
     * Enables/disables dynamic scaling. If scaling is enabled, then all sizes
     * are dynamically estimated and updated. The <code>scale</code>
     * <p>
     * @param scalingEnabled true to enable
     */
    public void setScalingEnabled(boolean scalingEnabled) {
        this.scalingEnabled = scalingEnabled;
        getPrefs().putBoolean("EyeTracker.scalingEnabled", scalingEnabled);
    }

//    public float getBlinkThreshold() {
//        return blinkThreshold;
//    }
//
//    /** If the fraction of events outside the model becomes too high, we call it a blink and don't update the eye model position
//     @param blinkThreshold the threshold 0-1, higher to make it harder to see a blink
//     */
//    public void setBlinkThreshold(float blinkThreshold) {
//        if(blinkThreshold<0) blinkThreshold=0; else if(blinkThreshold>1) blinkThreshold=1;
//        this.blinkThreshold = blinkThreshold;
//        prefs.putFloat("EyeTracker.blinkThreshold",blinkThreshold);
//    }
//
//    public float getBlinkMixingFactor() {
//        return blinkMixingFactor;
//    }
//
//    public void setBlinkMixingFactor(float blinkMixingFactor) {
//        if(blinkMixingFactor>1) blinkMixingFactor=1; else if(blinkMixingFactor<0) blinkMixingFactor=0;
//        this.blinkMixingFactor = blinkMixingFactor;
//        prefs.putFloat("EyeTracker.blinkMixingFactor",blinkMixingFactor);
//    }
    public boolean isShowEyeTarget() {
        return showEyeTarget;
    }

    EyeTarget target;
    JFrame targetFrame;

    /**
     * If enabled, shows a measure of gaze based on statistics of measured eye
     * position
     *
     * @param showGazeEnabled true to enable gaze point
     */
    public void setShowEyeTarget(boolean showGazeEnabled) {
        this.showEyeTarget = showGazeEnabled;
        if (showGazeEnabled) {
            checkTarget();
            targetFrame.setVisible(true);
            target.display();

        } else {
            targetFrame.setVisible(false);
        }
    }

    void checkTarget() {
        if (target == null) {
            target = new EyeTarget();
            targetFrame = new JFrame("EyeTarget");
            targetFrame.getContentPane().add(target);
            targetFrame.pack();
        }
    }

    public void setTargetSpeed(float speed) {
        target.setTargetSpeed(speed);
    }

    public float getTargetSpeed() {
        checkTarget();
        return target.getTargetSpeed();
    }

    /**
     * Returns the present location of the eye in pixels
     *
     * @return the position in pixels
     */
    public Point2D.Float getPosition() {
        return position;
    }

    public float getQualityThreshold() {
        return qualityThreshold;
    }

    /**
     * Sets the threshold for good tracking. If fraction of events inside the
     * model (the small-sized optimal model) falls below this number then
     * tracking is regarded as poor.
     *
     * @param qualityThreshold the fraction inside model for good tracking
     */
    public void setQualityThreshold(float qualityThreshold) {
//        if(qualityThreshold>1) qualityThreshold=1; else if(qualityThreshold<0) qualityThreshold=0;
        this.qualityThreshold = qualityThreshold;
        getPrefs().putFloat("EyeTracker.qualityThreshold", qualityThreshold);
    }

    public boolean isAnnotationEnabled() {
        return true;
    }

    public float getAcquisitionMultiplier() {
        return acquisitionMultiplier;
    }

    /**
     * Sets the factor by which the model area is increased when low quality
     * tracking is detected. Depends on setting of
     * {@link #setDynamicAcquisitionSize} method
     *
     * @param acquisitionMultiplier the ratio of areas; the ratio between disk
     * radius during bad tracking to that during good tracking.
     */
    public void setAcquisitionMultiplier(float acquisitionMultiplier) {
        if (acquisitionMultiplier < 1) {
            acquisitionMultiplier = 1;
        }
        this.acquisitionMultiplier = acquisitionMultiplier;
        getPrefs().putFloat("EyeTracker.acquisitionMultiplier", acquisitionMultiplier);
    }

    public float getQualityMixingFactor() {
        return qualityMixingFactor;
    }

    /**
     * Sets the "mixing factor" for tracking quality measure. A low value means
     * that the metric updates slowly -- is more lowpassed. If the mixing factor
     * is set to 1 then the measure is instantaneous.
     *
     * @param qualityMixingFactor the mixing factor 0-1
     */
    public void setQualityMixingFactor(float qualityMixingFactor) {
        if (qualityMixingFactor < 0) {
            qualityMixingFactor = 0;
        } else if (qualityMixingFactor > 1) {
            qualityMixingFactor = 1;
        }
        this.qualityMixingFactor = qualityMixingFactor;
        getPrefs().putFloat("EyeTracker.qualityMixingFactor", qualityMixingFactor);
    }

    public boolean isDynamicAcquisitionSize() {
        return dynamicAcquisitionSize;
    }

    /**
     * Sets whether to use dynamic resizing of acquisition model size, depending
     * on quality of tracking. If tracking quality is poor (support for model is
     * small fraction of total events (defined by qualityThreshold)), then model
     * area is increased proportionally.
     *
     * @param dynamicAcquisitionSize true to enable dynamic proportional
     * resizing of model
     */
    public void setDynamicAcquisitionSize(boolean dynamicAcquisitionSize) {
        this.dynamicAcquisitionSize = dynamicAcquisitionSize;
        getPrefs().putBoolean("EyeTracker.dynamicAcquisitionSize", dynamicAcquisitionSize);
    }

//    public float getEyelidRejectionAngleDeg() {
//        return eyelidRejectionAngleDeg;
//    }
//
//    public void setEyelidRejectionAngleDeg(float eyelidRejectionAngleDeg) {
//        if(eyelidRejectionAngleDeg>120) eyelidRejectionAngleDeg=120;
//        this.eyelidRejectionAngleDeg = eyelidRejectionAngleDeg;
//        prefs.putFloat("EyeTracker.eyelidRejectionAngleDeg",eyelidRejectionAngleDeg);
//    }
    private void computeModelArea() {
        modelAreaRatio = irisRadius * irisRadius * 3.1415f / chipArea;
    }

    EventFilterDataLogger dataLogger;

    public boolean isLogDataEnabled() {
        return logDataEnabled;
    }

    synchronized public void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if (dataLogger == null) {
            dataLogger = new EventFilterDataLogger(this, "# lasttimestamp eye.x eye.y quality target.x target.y targetMouse.x targetMouse.y ");
        }
        dataLogger.setEnabled(logDataEnabled);
    }

} // EyeTracker
