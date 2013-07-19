/*
 * OpenGLCamera.java
 * to move camera view in 3D open gl environment
 * Created on June 12, 2009, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.projects.stereo3D;

//import javax.media.opengl.*;
//import javax.media.opengl.GL;
//import javax.media.opengl.GLAutoDrawable;
//import javax.swing.*;
import javax.media.opengl.glu.GLU;
//
//import java.lang.*;
//import java.awt.*;
//import java.util.*;

//import java.lang.Math;

/**
 * to move camera view in 3D open gl environment
 * @author rogister
 */
public class OpenGLCamera {


    //Position of the Camera in the fixed axis system
  protected Vector3f position = new Vector3f();
  protected Vector3f center = new Vector3f();
  float distance; // distance from position to rotation center for rotation around
/*
 * Orientation of the camera
 * Store x, y, z rotation angles.
 * y angle : turn around upward vector (ie look toward your sides).
 * x angle : turn around sidewward vector (ie look toward the ground or the sky).
 * z angle : ignored
 */
   protected Vector3f orientation = new Vector3f();


    public OpenGLCamera() {
       
    }

    public OpenGLCamera(float x, float y, float z) {
       position = new Vector3f(x,y,z);
    }


    public void setAt(float x, float y, float z) {
         position.set(x,y,z);
         orientation.set(0,0,0);

    }

     public void setAt(float x, float y, float z, float ox, float oy, float oz) {
         position.set(x,y,z);
         orientation.set(ox,oy,oz);

    }

   public void rotate(float angleX, float angleY) {
        float newAngleX = (orientation.getX() + angleX) % 360.0f;    //modulo 360° (1 turn), angles are expressed in degrees here
        float newAngleY = (orientation.getY() + angleY) % 360.0f;    //modulo 360° (1 turn), angles are expressed in degrees here

        orientation.setX(newAngleX);
        orientation.setY(newAngleY);

    }


    public void rotateAround(float angleX, float angleY) {
        float newAngleX = (orientation.getX() + angleX) % 360.0f;    //modulo 360° (1 turn), angles are expressed in degrees here
        float newAngleY = (orientation.getY() + angleY) % 360.0f;    //modulo 360° (1 turn), angles are expressed in degrees here

        orientation.setX(newAngleX);
        orientation.setY(newAngleY);
        rotateAroundY(-angleY);
    }

    public void setRotationCenter( float distance ){

        this.distance = distance;
        center = toVectorInFixedSystem1(0.0f, 0.0f, distance);
        center.add(position);
    }

    public float getRotationCenterX(){
        return center.getX();
    }
    public float getRotationCenterY(){
        return center.getY();
    }
    public float getRotationCenterZ(){
        return center.getZ();
    }

    public void rotateAroundY( float angle ){
          //float x = position.getX();
          //float z = position.getZ();
 
          // should be global or parameter
          
        
          float xF = center.getX();
          float zF = center.getZ();


       
          float newAngleY = (orientation.getY() + 180 + angle) % 360.0f;
          Vector3f newPoint = toVectorInFixedSystem1(0.0f, 0.0f, distance,orientation.getX(),newAngleY);

        
          position.setX(xF+newPoint.getX());
          position.setZ(zF+newPoint.getZ());
//          position.setX((float) ( (Math.cos(Math.toRadians(angle))*(x-xRotationCenter)) -
//                (Math.sin(Math.toRadians(angle))*(z-zRotationCenter)))+xRotationCenter );
//          position.setZ((float) ( (Math.sin(Math.toRadians(angle))*(x-xRotationCenter)) +
//                (Math.cos(Math.toRadians(angle))*(z-zRotationCenter))) +zRotationCenter );
//
    }


    /*
 * What the code do ?
 *
 * This code calculate this formula :
 *   deplacement = [R]deplacement'
 *
 * deplacement : deplacement in the fixed coordinate system
 * deplacement' : deplacement in the camera coordinate system
 * [R] = [Ry][Rx]
 * [Ry] : y rotation matrix (rotate y first)
 * [Rx] : x rotation matrix
 *
 * This kind of deplacement is generally used for free deplacement (ie move everywhere).
 * Take an example, if you're looking at the sky and you want to move forward.
 * The forward deplacement is in direction to the up, so you will fly !
 */
 public Vector3f toVectorInFixedSystem1(float dx, float dy, float dz)
{
      return toVectorInFixedSystem1( dx,  dy,  dz, orientation.getX(), orientation.getY());
}

public Vector3f toVectorInFixedSystem1(float dx, float dy, float dz, float angleX, float angleY)
{
    //Don't calculate for nothing ...
    if(dx == 0.0f & dy == 0.0f && dz == 0.0f)
         return new Vector3f();

    //Convert to Radian : 360° = 2PI
    double xRot = Math.toRadians(angleX);    //Math.toRadians is toRadians in Java 1.5 (static import)
    double yRot = Math.toRadians(angleY);

    //Calculate the formula
    float x = (float)( dx*Math.cos(yRot) + dy*Math.sin(xRot)*Math.sin(yRot) - dz*Math.cos(xRot)*Math.sin(yRot) );
    float y = (float)(              + dy*Math.cos(xRot)           + dz*Math.sin(xRot)           );
    float z = (float)( dx*Math.sin(yRot) - dy*Math.sin(xRot)*Math.cos(yRot) + dz*Math.cos(xRot)*Math.cos(yRot) );

    //Return the vector expressed in the global axis system
    return new Vector3f(x, y, z);
}

/*
 * This kind of deplacement generaly used for player movement (in shooting game ...).
 * The x rotation is 'ignored' for the calculation of the deplacement.
 * This result that if you look upward and you want to move forward,
 * the deplacement is calculated like if your were parallely to the ground.
 */
public Vector3f toVectorInFixedSystem2(float dx, float dy, float dz)
{
    //Don't calculate for nothing ...
    if(dx == 0.0f & dy == 0.0f && dz == 0.0f)
         return new Vector3f();

    //Convert to Radian : 360° = 2PI
    double xRot = Math.toRadians(orientation.getX());
    double yRot = Math.toRadians(orientation.getY());    //Math.toRadians is toRadians in Java 1.5 (static import)

    //Calculate the formula
    float x = (float)( dx*Math.cos(yRot) + 0            - dz*Math.sin(yRot) );
    float y = (float)( 0            + dy*Math.cos(xRot) + 0            );
    float z = (float)( dx*Math.sin(yRot) + 0            + dz*Math.cos(yRot) );

    //Return the vector expressed in the global axis system
    return new Vector3f(x, y, z);
}


public void lookAt(GLU glu, float x, float y, float z)
{
    //Get upward and forward vector, convert vectors to fixed coordinate sstem (similar than for translation 1)
    Vector3f up = toVectorInFixedSystem1(0, 1, 0);        //Note: need to calculate at each frame
    Vector3f forward = toVectorInFixedSystem1(0.0f, 0.0f, 1);
    //Vector3f pos = new Vector3f(x,y,z);
    /*
     * Read Lesson 02 for more explanation of gluLookAt.
     */

    glu.gluLookAt(
        //Position
        position.getX(),
        position.getY(),
        position.getZ(),

        //View 'direction'
        position.getX()+forward.getX(),
        position.getY()+forward.getY(),
        position.getZ()+forward.getZ(),

        //Upward vector
        up.getX(), up.getY(), up.getZ());

    position.add(forward);

}

public void move(GLU glu, float x, float y, float z)
{

     Vector3f forward = toVectorInFixedSystem1(x, y, z);

     position.add(forward.getX(),forward.getY(),forward.getZ());

    

    // System.out.println("position x: "+position.getX()+" y: "+position.getY()+" z: "+position.getZ());
 //    System.out.println("orientation x: "+orientation.getX()+" y: "+orientation.getY()+" z: "+orientation.getZ());


    //Get upward and forward vector, convert vectors to fixed coordinate sstem (similar than for translation 1)
  //  Vector3f up = toVectorInFixedSystem1(0, 1, 0);        //Note: need to calculate at each frame
 //   Vector3f forward = toVectorInFixedSystem1(0.0f, 0.0f, 1);
    //Vector3f pos = new Vector3f(x,y,z);
    /*
     * Read Lesson 02 for more explanation of gluLookAt.
     */

//    glu.gluLookAt(
//        //Position
//        position.getX(),
//        position.getY(),
//        position.getZ(),
//
//        //View 'direction'
//        position.getX()+forward.getX(),
//        position.getY()+forward.getY(),
//        position.getZ()+forward.getZ(),
//
//        //Upward vector
//        up.getX(), up.getY(), up.getZ());



}

public void view(GLU glu)
{


    //Get upward and forward vector, convert vectors to fixed coordinate sstem (similar than for translation 1)
    Vector3f up = toVectorInFixedSystem1(0, 1, 0);        //Note: need to calculate at each frame
    Vector3f forward = toVectorInFixedSystem1(0.0f, 0.0f, 1);
    //Vector3f pos = new Vector3f(x,y,z);
    /*
     * Read Lesson 02 for more explanation of gluLookAt.
     */

  //  System.out.println(up.getX()+" "+up.getY()+" "+up.getZ());

    glu.gluLookAt(
        //Position
        position.getX(),
        position.getY(),
        position.getZ(),

        //View 'direction'
        position.getX()+forward.getX(),
        position.getY()+forward.getY(),
        position.getZ()+forward.getZ(),

        //Upward vector
        up.getX(), up.getY(), up.getZ());

     

}

public String toString(){
    return "position: "+position.toString()+" orientation: "+orientation.toString();

}

private class Vector3f {
     float x;
     float y;
     float z;

      public Vector3f() {
        x = y = z = 0;
    }
        public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
      public Vector3f set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

     public Vector3f add(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }
    public Vector3f add( Vector3f v) {
        this.x += v.getX();
        this.y += v.getY();
        this.z += v.getZ();
        return this;
    }


   public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getZ() {
        return z;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public String toString(){
        return ""+x+" "+y+" "+z;
    }


}



}