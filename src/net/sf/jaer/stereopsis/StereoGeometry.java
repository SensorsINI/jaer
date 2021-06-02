/*
 * StereoGeometry.java
 *
 * Created on July 24, 2006, 8:07 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 24, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.stereopsis;

import java.util.Observable;
import java.util.Observer;
import java.util.prefs.Preferences;

import net.sf.jaer.chip.AEChip;

/**
 * Encapsulates stereo geometry. The basic geometry for parallel camera (infinite vergence) is shown in the following image.
 <br>
 <img src="doc-files/stereoGeometry.jpg" alt="geometry sketch" />
 
 * @author tobi
 */
public class StereoGeometry implements Observer{
    
    static Preferences prefs=Preferences.userNodeForPackage(StereoGeometry.class);
    
    /** the distance between the two eyes in mm */
    private float baselineMm=prefs.getFloat("StereoGeometry.baselineMm",69);
    
    /** the focal length of the lenses used in mm*/
    private float focalLengthMm=prefs.getFloat("StereoGeometry.focalLengthMm",8);
    
    /** the vergence distance of two eyes in mm. This defines the angle at which the two eyes are verged.
     If the eyes are aimed at infinity the value is Float.POSITIVE_INFINITY.
     */
    private float vergenceDistanceMm=prefs.getFloat("StereoGeometry.vergenceDistanceMm",Float.POSITIVE_INFINITY);
    
    private float pixWidthUm; // will be updated by chip update
    private float zScale; // for computing distance from disparity
    float sx,sy; // midpoints in pixel space of chip
        float xyScale;
        
    private AEChip chip;
    
    /** Creates a new instance of StereoGeometry */
    public StereoGeometry(AEChip chip) {
        this.chip=chip;
        chip.addObserver(this);
        init();
    }
    
    void init(){
        pixWidthUm=chip.getPixelWidthUm();
        zScale=focalLengthMm*baselineMm/pixWidthUm; // units are length in mm^2/um
        sx=(chip.getSizeX())/2;
        sy=(chip.getSizeY())/2; // midpoint of chip
        xyScale=(pixWidthUm*1e-6f)/(focalLengthMm*1e-3f); // scale dimensionless
    }

    /** @return the distance from the viewer in meters given a disparity.
     <strong>
     At present only handles parallel viewers
     </strong>
     @param disparity the total disparity in pixels between the left and right eyes, i.e. the shift required to bring a feature on one of them in registration
     with a feature in the other. (The shift can be in either eye or can consist of a shift in both, either way, the total disparity is the same.)
     */
    public float computeViewerDistanceFromDisparityM(float disparity){
        if(disparity<=0) return Float.POSITIVE_INFINITY;
        return focalLengthMm*baselineMm/(disparity*chip.getPixelWidthUm()*1e-6f);
    }
    
    /** Computes the 3d location of the binocular point where 0,0,0 is the viewer between the eyes and z runs out from viewer,
     x horizontally from left to right in image with 0 at center, and y like x but vertically in image. Units are meters.
     A left or right eye position by itself only defines a line of sight but the combination of two lines of sight that cross at
     a corresponding point defines the corresponding point as a point in 3d space. Therefore the cyclopean point defined
     by a single location in the image plane of the combined binocular image, plus the disparity of this cyclopean point, defines
     a 3d location.
     <strong>
     At present only handles parallel left and right viewing angles that are not verged (i.e. viewing infinity).
     </strong>
     @param disparity the total disparity in pixels between the left and right eyes, i.e. the shift required to bring a feature on one of them in registration
     with a feature in the other. (The shift can be in either eye or can consist of a shift in both, either way, the total disparity is the same.)
     @param px the pixel x position of cyclopean position (mean of x positions from left and right eye)
     @param py the pixel y position of cyclopean position (mean of y positions from left and right eye)
     @param p a 3-vector where results are written
     @see #compute3dVelocityMps
     */
    public void compute3dlocationM(float px, float py, float disparity, float[] p){
        float z; 
        // note disparity is in pixels
        if(disparity<=0) z=Float.POSITIVE_INFINITY;
        else z=zScale/disparity; // Units: mm*mm/um = (e-3m)(e-3m)/1e-6m = meters, z units work out to just meters
        float s=z*xyScale;
        float x=s*(px-sx); // units are meters
        float y=s*(py-sy); // meters
        p[0]=x;
        p[1]=y;
        p[2]=z;
    }
    
    /** Computes the 3d velocity of the binocular point where 0,0,0 is the viewer between the eyes and z runs out from viewer,
     x horizontally from left to right in image with 0 at center, and y like x but vertically in image. Velocities are computed by
     straightforward scaling of image plane distance by distance of point from viewer, e.g. Vx=z/f*Px, where Vx is x velocity in 3d space,
     z is distance from viewer, f is focal length of camera, and Px is velocity in image plane.
     <strong>
     Only handles parallel left right viewing, i.e. foveating infinity, no vergence.
     </strong>
     @param position the x,y,z float 3-vector physical position in meters in eye coordinates as computed by {@link #compute3dlocationM}
     @param disparity the disparity as used as earlier input for position (this saves computation internally)
     @param pvx the cluster's pixel x velocity in pixels per second
     @param pvy the cluster's pixel y velocity
     @param pvd the cluster's pixel disparity velocity
     @param p a 3-vector where results are written as velocity in meters per second in eye coordinate space. 
     If the disparity is negative or zero, indicating infinite distance, all velocities are set to zero.
     @see #compute3dlocationM
     */
    public void compute3dVelocityMps(float[] position, float disparity, float pvx, float pvy, float pvd, float[] p){
        float x=position[0];
        float y=position[1];
        float z=position[2];
        if(z==Float.POSITIVE_INFINITY){
            // 3d location is infinitely far away, we cannot say anything about velocity
            p[0]=0;
            p[1]=0;
            p[2]=0;
            return;
        }
        // x,y,z velocities are given by pixel velocities scaled by distance from viewer
        float s=z*xyScale;
        p[0]=s*pvx; // x velocity, meters per second 
        p[1]=s*pvy;
        p[2]=z*pvd/disparity; // also meters per second
    }
    
    public float getBaselineMm() {
        return baselineMm;
    }
    
    /** The baseline is the distance between the two eyes
     @param baselineMm the distance in mm
     */
    public void setBaselineMm(float baselineMm) {
        this.baselineMm = baselineMm;
        prefs.putFloat("StereoGeometry.baselineMm",baselineMm);
    }
    
    public float getFocalLengthMm() {
        return focalLengthMm;
    }
    
    public void setFocalLengthMm(float focalLengthMm) {
        this.focalLengthMm = focalLengthMm;
        prefs.putFloat("StereoGeometry.focalLengthMm",focalLengthMm);
    }
    
    public float getVergenceDistanceMm() {
        return vergenceDistanceMm;
    }
    
    /** The vergence distance is the distance from the viewer that the two eyes verge. If the views are parallel
     the distance is Float.POSITIVE_INFINITY.
     @param vergenceDistanceMm the distance in mm
     */
    public void setVergenceDistanceMm(float vergenceDistanceMm) {
        this.vergenceDistanceMm = vergenceDistanceMm;
        prefs.putFloat("StereoGeometry.vergenceDistanceMm",vergenceDistanceMm);
    }
    
    public void update(Observable o, Object arg) {
//        System.out.println(this+ " got update from Observable "+o+" with arg="+arg);
        init();
    }
}
