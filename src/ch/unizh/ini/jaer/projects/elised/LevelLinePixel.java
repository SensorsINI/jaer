/*

Class for the level line pixels; as described in Jonas Strubel's Master Thesis.
A pixel consists of:
- angle with the horizontal axis (this is about the line orthogonal to the gradient of timestamps in a pixel)
- magnitude of timestamp gradient 
- lifecount; number of references to the pixel
- timestamp
no more: - reference to the support region it belongs to, or null
- its index in the SupportList array (or -1)
-  x and y coordinates 
- whether it was deleted already and is just a remainder in the unused part of a resized pixelbuffer

*/

package ch.unizh.ini.jaer.projects.elised;

import net.sf.jaer.event.PolarityEvent;


public class LevelLinePixel{
    private int timestamp;
    private int x, y;
    private int events;
    private float angle;        //The level line angle; lies between (just above?) -180 and 180 degrees. Is set in class elised in methods get..PixelFromEvent
    private float angle90;      // angle mod +-90°; used if seperation of opposing gradients is switched off 
    private int polarity;        //-1 = off, 1 = on
    private float magnitude;  
    private LineSupport lineSupport;

    
    // normal constructor
    public LevelLinePixel(int x, int y){
        this.x = x;
        this.y = y;
        events = 0;
        lineSupport = null;
    }
    
    public void addEvent(PolarityEvent e){
        polarity = e.getPolaritySignum();
        timestamp = e.getTimestamp();
        events++;
    }
    
    public void removeEvent(){
        events--;    
    }
    
    public void setLevelLine(float mag, float ori){
        magnitude = mag;
        angle = ori;
        angle90 = mapTo180Deg(angle);
    }
    
    public boolean assigned(){
        return lineSupport != null;
    }
    
    // maps any angle to the range of angles covered by the line segment orientations, [-90°,90°( 
    public final float mapTo180Deg(float original){
        float res;
        if(original < -90.0f)
            res = original +180.0f;
        else if(original >= 90.0f)
            res = original -180.0f;
        else res = original;
        return res;
    }

    public boolean isBuffered(){
        return events > 0;
    }
    
    public boolean hasLevelLine(){
        return events > 0 && magnitude > 0;
    }
    
    public int getX(){
        return x;
    }
    
    public int getY(){
        return y;
    }
    
    public int getPolarity(){
        return polarity;
    }
    
    public int getTimestamp(){
        return timestamp;
    }
    
    public float getMagnitude(){
        return magnitude;
    }
    
    public void setMagnitude(float mag){
        this.magnitude = mag;
    }
    
    public float getAngle(){
        return angle;
    }
    
    public float getAngle90(){
        return angle90;
    }
    
    public void setAngle(float angle){
        this.angle = angle;
    }
    
    public LineSupport getSupport(){
        return lineSupport;
    }
    
    public void setSupport(LineSupport lineSup){
        if(lineSupport == null || !lineSupport.contains(this)){
            this.lineSupport = lineSup;
        }else{
            lineSupport.remove(this);
            this.lineSupport = lineSup;
        }
    }
}
