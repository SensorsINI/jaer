package ch.unizh.ini.jaer.projects.elised;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author Susi; but to a large part copy-pasted from Jonas Strubels' c++ code.
 *
 * Bug: inSum+outSum should never rise above nrPixelsRemembered, but it does.
 */
public class LineSupport {              //
    
    private final long id;
        
    private float llAngle = 0;              //level line angle; lies between +-180°.
    private float sumAngleUnitX = 0;        //sum cos(pixel.angle)
    private float sumAngleUnitY = 0;        //sum sin(pixel.angle)
    private float sumAngleUnitX90 = 0;
    private float sumAngleUnitY90 = 0;

    private float width = 0, length = 0;
    private float llMagnitude = 0;
    private float llMagnitudeSum = 0;
    private long creationTime = 0;
    private long latestUpdate = 0;
    
    private int m00 = 0;        //number of support pixels
    private int m10 = 0;        //sum over all x-values of support pixels
    private int m01 = 0;        //sum over all y-values
    private int m11 = 0;        //sum over all xi * yi
    private int m20 = 0;        //sum over all x^2
    private int m02 = 0;        //sum over y^2
    
    private HashSet<LevelLinePixel> supportPixels;
    
    private float xEndPoint1, yEndPoint1, xEndPoint2, yEndPoint2;                //current endpoints of the linesegment; in pixels
    private float centerX, centerY;
    private float orientation = 0;          /* lies between +-90°; more exactly, within [-90°,90°( . For moments of inertia, there is no direction of movement, so distinguishing between e.g. 45° and -135°, as for llps, wouldn't  make sense.*/
    private float[] color;
    public ELiSeD elised;
//    public CopyOnWriteArrayList<LevelLinePixel> supportPixels; 

    public LineSupport(long id, ELiSeD elised) {
        this.elised = elised;
//        supportPixels = new CopyOnWriteArrayList<>();
        supportPixels = new HashSet();
        this.id = id;
        float[] c = {(float) Math.random(), (float) Math.random(), (float) Math.random()};
        color = c;
    }

    public void add(LevelLinePixel p) {
        if(!p.isBuffered()){
            ELiSeD.log.warning("cannot add an inactive pixel");
        }
        if(!p.hasLevelLine()){
            ELiSeD.log.warning("cannot add a pixel without level line");
        }
              
        if(supportPixels.contains(p)){  // This might be heck expensive!
            ELiSeD.log.warning("tried to add pixel which is already in support!!");
            return;
        }
        
        if (creationTime == 0) {
            creationTime = p.getTimestamp();
        }
        latestUpdate = p.getTimestamp();
        
//        p.changeSupport(this);
        // Image moment
        m00++;
        m10 += p.getX();
        m01 += p.getY();
        m11 += (p.getX() * p.getY());
        m20 += (p.getX() * p.getX());
        m02 += (p.getY() * p.getY());

        if (elised.isDistinguishOpposingGradients()) {
            sumAngleUnitX += Math.cos(Math.toRadians(p.getAngle()));
            sumAngleUnitY += Math.sin(Math.toRadians(p.getAngle()));
        } else {
            sumAngleUnitX90 += Math.cos(Math.toRadians(p.getAngle90()));
            sumAngleUnitY90 += Math.sin(Math.toRadians(p.getAngle90()));
        }
        llMagnitudeSum += p.getMagnitude();
        llAngle = computeLLAngle();
        llMagnitude = computeLLMagnitude();
        supportPixels.add(p);
        p.setSupport(this);

        updateLineProperties();
    }

    // Updates this lines attributes to state without pixel p, and sets p.assigned to false.
    // This doesn't check wether the pixel actually belongs to this segment.
    public void remove(LevelLinePixel p) {       
        m00--;
        m10 -= p.getX();
        m01 -= p.getY();
        m11 -= (p.getX() * p.getY());
        m20 -= (p.getX() * p.getX());
        m02 -= (p.getY() * p.getY());

        if (elised.isDistinguishOpposingGradients()) {
            sumAngleUnitX -= Math.cos(Math.toRadians(p.getAngle()));
            sumAngleUnitY -= Math.sin(Math.toRadians(p.getAngle()));
        } else {
            sumAngleUnitX90 -= Math.cos(Math.toRadians(p.getAngle90()));
            sumAngleUnitY90 -= Math.sin(Math.toRadians(p.getAngle90()));
        }
        llMagnitudeSum -= p.getMagnitude();
        llAngle = computeLLAngle();
        llMagnitude = computeLLMagnitude();
        supportPixels.remove(p);
        p.setSupport(null);
        
        updateLineProperties();
    }

    // Appends all of the second Segment's pixel to the end of the current one's 
    // support list and updates the attributes. The pixel's isLineSegment references are changed to point to 
    // the current one. The second line segment remains and any reference to it 
    // should be deleted.   Usage in elised:  bestSegment.merge(tmp); best segment = older segment
    public void merge(LineSupport second) {
        HashSet<LevelLinePixel> otherPixels = (HashSet) second.getSupportPixels().clone();
        for (LevelLinePixel llp : otherPixels) {
            if(llp.getSupport() != this){
                if(llp.hasLevelLine()){
                    add(llp);
                }else{
                    llp.setSupport(null);
                }
            }
        }
        llAngle = computeLLAngle();
        llMagnitude = computeLLMagnitude();
        updateLineProperties();
    }

    //(re-)calculate level line angle
    public float computeLLAngle() {
        if (m00 == 0) {
            return 0;
        }
        float theta;
        if (elised.isDistinguishOpposingGradients()) {
            theta = elised.atanHelper.atan2(sumAngleUnitX, sumAngleUnitY);
        } else {
            theta = elised.atanHelper.atan2(sumAngleUnitX90, sumAngleUnitY90);
        }
        return theta;
    }
    
        
    public float computeLLMagnitude() {
        if (m00 == 0) {
            return 0;
        }
        return llMagnitudeSum / (float) m00;
    }

    // Calculate position, length of the main axes, and orientation of the ellipse
    // approximating this line segment. To save work, maybe the average llangle could
    // be used as orientation. Are there lines whose pixels' level lines are not 
    // parallel to the line itself? 
    private void updateLineProperties() {
        if (m00 == 0) {
            length = 0;
        } else {
            centerX = (float) m10 / (float) m00;
            centerY = (float) m01 / (float) m00;

            // covariance matrix entries
            // see for example: 'Elliptic fit of objects in two and three dimensions 
            // by moment of inertia optimization' by Chaudhuri and Samanta and for 
            // the a, b, c part 'Image Moments-Based Structuring and Tracking of 
            // Objects' by Rocha, Velho, Carvalho
            float u20 = (float) m20 / (float) m00 - centerX * centerX;
            float u02 = (float) m02 / (float) m00 - centerY * centerY;
            float u11 = (float) m11 / (float) m00 - centerX * centerY;

            if (u20 != u02) {
                //orientation = (float)Math.toDegrees(0.5 * Math.atan( (2*u11)/(u20 - u02) ));}
                orientation = 0.5f * elised.atanHelper.atan((2.0f * u11) / (u20 - u02));
            } else {
                orientation = 0;
            }

            /*  If Ix = u02 > Iy = u20, then the level line is closer to the y-axis.
             So, abs(2*theta) is bigger than PI/2, the range where atan functions
             correctly. If 2 theta = -Pi + delta, atan returns delta, so the result
             is delta/2 and 90°(PI/2) have to be substracted. If 2*theta were positive,
             PI - delta, we have to add 90° instead. The result is the orientation of the 
             bigger main axis.
             */
            if (u02 > u20) {
                orientation -= 90.0f;     
                if (orientation < -90.0f) // ->> 0 goes to -90°, +90° can't be reached
                {
                    orientation += 180.0f;
                }
            }
            float a = u20;
            float b = 2.0f * u11;
            float c = u02;
            width = (float) Math.sqrt(6.0f * (a + c - Math.sqrt(b * b + (a - c) * (a - c))));
            length = (float) Math.sqrt(6.0f * (a + c + Math.sqrt(b * b + (a - c) * (a - c))));

            updateEndpoints();
        }
    }

    public void updateEndpoints() {
        float unitLineX, unitLineY;
        unitLineX = (float) Math.cos(Math.toRadians(orientation));
        unitLineY = (float) Math.sin(Math.toRadians(orientation));

        float s = length / 2.0f;
        
        float vx = unitLineX * s;
        float vy = unitLineY * s;
        xEndPoint1 = (float) centerX + vx;
        yEndPoint1 = (float) centerY + vy;
        xEndPoint2 = (float) centerX - vx;
        yEndPoint2 = (float) centerY - vy;

    }
    
    public long getLatestUpdate(){
        return latestUpdate;
    }
    
    public boolean isLineSegment(){
        return (m00 >= elised.minLineSupport); // && getDensity() >= elised.getMinDensity());
    }
    
    public int getSupportSize(){
        return supportPixels.size();
    }
    
    public long getCreationTime(){
        return creationTime;
    }
    
    public float getCenterX(){
        return centerX;
    }
    
    public float getCenterY(){
        return centerY;
    }
    
    public float getOrientation(){
        return orientation;
    }
    
    public int getMass(){
        return m00;
    }
    
    public float getWidth(){
        return width;
    }
    
    public float getLength(){
        return length;
    }
    
    public float getEndpointX1(){
        //if(xEndPoint1 <0)return 0;
        //if(xEndPoint1 >= elised.getChip().getSizeX())return elised.getChip().getSizeX();
        return xEndPoint1;
    }
    
    public float getEndpointY1(){
        //if(yEndPoint1 <0)return 0;
        //if(yEndPoint1 >= elised.getChip().getSizeY())return elised.getChip().getSizeY();
        return yEndPoint1;
    }
    
    public float getEndpointX2(){
        //if(xEndPoint2 <0)return 0;
        //if(xEndPoint2 >= elised.getChip().getSizeX())return elised.getChip().getSizeX();
        return xEndPoint2;
    }
    
    public float getEndpointY2(){
        //if(yEndPoint2 <0)return 0;
        //if(yEndPoint2 >= elised.getChip().getSizeY())return elised.getChip().getSizeY();
        return yEndPoint2;
    }
    
    public float getLLAngle(){
        return llAngle;
    }
    
    public float getLLMagnitude(){
        return llMagnitude;
    }
    
    public HashSet<LevelLinePixel> getSupportPixels(){
        return supportPixels;
    }
    
    public long getId(){
        return id;
    }
    
    public float[] getColor(){
        return color.clone();
    }
    
    public boolean contains(LevelLinePixel llp){
        return supportPixels.contains(llp);
    }
    
    public float getDensity(){
        return (float)m00 * 4.0f /(width * length * 3.1415926536f);
    }
    
    public float getDensityCombined(LineSupport other){
        if(other==this)
            return 0f;
        float cX = (float) (m10 + other.m10) / (float) (m00 + other.m00);
        float cY = (float) (m01 + other.m01) / (float) (m00 + other.m00);
        float a = (float) (m20 + other.m20) / (float) (m00 + other.m00) - cX * cX;
        float b = 2.0f *((float) (m11 + other.m11) / (float) (m00 + other.m00) - cX * cY);
        float c = (float) (m02 + other.m02) / (float) (m00 + other.m00) - cY * cY;
        float w = (float) Math.sqrt(6.0f * (a + c - Math.sqrt(b * b + (a - c) * (a - c))));
        float l = (float) Math.sqrt(6.0f * (a + c + Math.sqrt(b * b + (a - c) * (a - c))));
        return (float)(m00 + other.m00) * 4.0f /(l * w * 3.1415926536f);
    }
    
    public float getDensityAdded(LevelLinePixel pixel){
        float cX = (float) (m10 + pixel.getX()) / (float) (m00 + 1.0f);
        float cY = (float) (m01 + pixel.getY()) / (float) (m00 + 1.0f);
        float a = (float) (m20 + pixel.getX()*pixel.getX()) / (float) (m00 + 1.0f) - cX * cX;
        float b = 2.0f *((float) (m11 + pixel.getX()*pixel.getY()) / (float) (m00 + 1.0f) - cX * cY);
        float c = (float) (m02 + pixel.getY()*pixel.getY()) / (float) (m00 + 1.0f) - cY * cY;
        float w = (float) Math.sqrt(6.0f * (a + c - Math.sqrt(b * b + (a - c) * (a - c))));
        float l = (float) Math.sqrt(6.0f * (a + c + Math.sqrt(b * b + (a - c) * (a - c))));
        return (float)(m00 + 1.0f) * 4.0f /(l * w * 3.1415926536f);
    }
    
    /* Hasn't been used or tested yet!
       This reduces the width for density calculation by length/128; supposed to 
       decrease width for long segments; maybe the elliptic fit gives bigger widths
       than would be good for a rectangle fit.*/
    public float getCheatDensityCombined(LineSupport other){
        float cX = (float) (m10 + other.m10) / (float) (m00 + other.m00);
        float cY = (float) (m01 + other.m01) / (float) (m00 + other.m00);
        float a = (float) (m20 + other.m20) / (float) (m00 + other.m00) - cX * cX;
        float b = 2.0f *((float) (m11 + other.m11) / (float) (m00 + other.m00) - cX * cY);
        float c = (float) (m02 + other.m02) / (float) (m00 + other.m00) - cY * cY;
        float w = (float) Math.sqrt(6.0f * (a + c - Math.sqrt(b * b + (a - c) * (a - c))));
        float l = (float) Math.sqrt(6.0f * (a + c + Math.sqrt(b * b + (a - c) * (a - c))));
        w = Math.max(1.0f, w*(1 - l / 128.0f));
        return (float)(m00 + other.m00) * 4.0f /(l * w * 3.1415926536f);
    }
    
    public float getCheatDensityAdded(LevelLinePixel pixel){
        float cX = (float) (m10 + pixel.getX()) / (float) (m00 + 1.0f);
        float cY = (float) (m01 + pixel.getY()) / (float) (m00 + 1.0f);
        float a = (float) (m20 + pixel.getX()*pixel.getX()) / (float) (m00 + 1.0f) - cX * cX;
        float b = 2.0f *((float) (m11 + pixel.getX()*pixel.getY()) / (float) (m00 + 1.0f) - cX * cY);
        float c = (float) (m02 + pixel.getY()*pixel.getY()) / (float) (m00 + 1.0f) - cY * cY;
        float w = (float) Math.sqrt(6.0f * (a + c - Math.sqrt(b * b + (a - c) * (a - c))));
        float l = (float) Math.sqrt(6.0f * (a + c + Math.sqrt(b * b + (a - c) * (a - c))));
        w = Math.max(1.0f, w*(1 - l / 128.0f));
        return (float)(m00 + 1.0f) * 4.0f /(l * w * 3.1415926536f);
    }
    
}
