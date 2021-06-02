/*
 * Angle.java
 *
 * Created on 5. Dezember 2007, 11:49
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead;



/**
 *
 * @author Administrator
 */
 
public class Angle {
    
    final int schall=344; // [m/s]
    int radius;
    int[] ang=new int[181];
    double[] angrad=new double[181];
    double a1;
    double a2;
    double[] delay=new double[181];
    double dist = 0.11; // distance between microphones / 2
    
    /** Creates a new instance of Angle */
    public Angle(int radius) {
        this.radius=radius;
        
        for (int i=0; i<181; i++){
            ang[i]=i-90;
            angrad[i]=(ang[i]*Math.PI/2)/90;
            
            a1=Math.sqrt(Math.pow(Math.sin(angrad[i])*radius-dist,2)+Math.pow(Math.cos(angrad[i])*radius,2));
            a2=Math.sqrt(Math.pow(Math.sin(angrad[i])*radius+dist,2)+Math.pow(Math.cos(angrad[i])*radius,2));
            delay[i]=1000000*(a1-a2)/schall;    // delay in [us]
        }
    }
    public int getAngle(double del){
        
        double minDiff=1000;
        int indMinDiff=0;
        double diff;
        for (int i=0; i<delay.length; i++){
            diff=del-delay[i];
            if (Math.abs(diff)<minDiff){
                minDiff=Math.abs(diff);
                indMinDiff=i;
            }
        }
        return ang[indMinDiff];
    }
    
    
    public void dispAngle(){
        for (int i=0; i<180; i++){
            System.out.print(ang[i]+" ");
        }
        System.out.print(" \n");
        for (int i=0; i<180; i++){
            System.out.print(angrad[i]+" ");
        }
        
        System.out.print(" \n");
        for (int i=0; i<180; i++){
            System.out.print(delay[i]+" ");
        }
        
    }
    
    public int[] getAngArray(){
        return ang;
    }
    
    public void setDist(double dist){
        this.dist=dist;
    }
    public double getDist(){
        return this.dist;
    }        
    
}
