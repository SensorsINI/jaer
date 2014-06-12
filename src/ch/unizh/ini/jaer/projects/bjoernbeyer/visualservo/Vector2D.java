
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.awt.geom.Point2D;
import javax.media.opengl.GL2;
/**
 *
 * @author Bjoern
 */
public class Vector2D extends Point2D.Float {
    
    public Vector2D() { super(); }
    public Vector2D(Vector2D vec){
        super(vec.x,vec.y);
    }
    public Vector2D(Point2D.Float point){
        super(point.x,point.y);
    }
    public Vector2D(float x, float y){
        super(x,y);
    }
    
    public void add(Vector2D vec) { add(vec.x, vec.y); }
    public void add(float x, float y) {
        this.x += x;
        this.y += y;
    }
    
    public void addFraction(Vector2D vec, float factor) {
        add(factor*vec.x, factor*vec.y);
    }
    
    public void sub(Vector2D vec) { sub(vec.x,vec.y); }
    public void sub(float x, float y) {
        this.x -= x;
        this.y -= y;
    }
   
    public void div(float divisor) {div((double) divisor);}
    public void div(double divisor) {
        if(divisor == 0) throw new IllegalArgumentException("Argument 'divisor' is 0");
        this.x /= divisor;
        this.y /= divisor;
    }
    
    public void mult(float mult) {mult((double) mult);}
    public void mult(double mult){
        this.x *= mult;
        this.y *= mult;
    }
    
    public double distance(Vector2D vec){
        return distance(vec.x,vec.y);
    }
    public void setLocation(Vector2D vec) {
        setLocation(vec.x, vec.y);
    }
    public void setDifference(Vector2D vec1, Vector2D vec2) {
        setLocation(vec1.x - vec2.x,vec1.y - vec2.y);
    }
    public void setSum(Vector2D vec1, Vector2D vec2) {
        setLocation(vec1.x + vec2.x,vec1.y + vec2.y);
    }
    public double length() {
        return distance(0, 0);
    }
    public void setLength(double newLength) {
        if(length() == 0) {
            if(newLength == 0) return; // all done, nothing to do
            throw new IllegalStateException("Vector is (0,0). The length can not be set!");
        }
        div(length());
        mult(newLength);
    }
    public double dotProduct(Vector2D vec) {
        if(length() == 0 || vec.length() == 0) return 0; 
        return (x*vec.x + y*vec.y);
    }
    @Override public String toString(){
        return "("+x+","+y+")";
    }
    public void unify() {
        if(length() == 0) throw new IllegalStateException("The length of the vector is 0, thus it can not be unified!");
        if(length() == 1) return; // already unitvector, no need to devide.
        div(length());
    }
    
    public void drawVector(GL2 gl) { drawVector(gl,0,0,1,1); }
    public void drawVector(GL2 gl, float origX, float origY) { drawVector(gl,origX,origY,1,1); }
    public void drawVector(GL2 gl, float origX, float origY, float headlength, float Scale) {
        float startx = origX,                starty = origY;
        float endx   = origX + this.x*Scale, endy   = origY + this.y*Scale;
        float vecx   = endx-startx,          vecy   = endy-starty; // orig vec
        float vx2    = vecy,                 vy2    = -vecx;       // right angles +90 CW
        float arx    = -vecx+vx2,            ary    = -vecy+vy2;   // halfway between pointing back to origin
        float l = (float)Math.sqrt((arx*arx)+(ary*ary)); // length
        arx = (arx/l)*headlength;              ary  = (ary/l)*headlength; // normalize to headlength
        
        gl.glVertex2f(startx,starty);
        gl.glVertex2f(endx,endy);
        // draw arrow (half)
        gl.glVertex2f(endx,endy);
        gl.glVertex2f(endx+arx, endy+ary);
        // other half, 90 degrees
        gl.glVertex2f(endx,endy);
        gl.glVertex2f(endx+ary, endy-arx);
    }
   
    /*returns the angle between this and the argument from 0 to pi
     * 0 meaning vectors have the same direction and pi meaning vectors are orthogonal*/
    public double getAngle(Vector2D vec){
        if(this.length() == 0) throw new IllegalStateException("The length of this vector is 0, the angle can not be computed!");
        if(vec.length() == 0) throw new IllegalStateException("The length of the argument vector is 0, the angle can not be computed!");
        
        double arg = this.dotProduct(vec)/(this.length()*vec.length());
        if(arg > 1) arg = 1; //can happen due to floating point error
        if(arg < -1) arg = -1;

        return Math.acos(arg);
    }
    public double getAbsLengthDiff(Vector2D vec) {
        return Math.abs(this.length()-vec.length());
    }
}
