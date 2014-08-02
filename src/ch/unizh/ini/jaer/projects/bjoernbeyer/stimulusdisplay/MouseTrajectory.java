
package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author Bjoern
 */
public class MouseTrajectory extends ArrayList<MouseTrajectoryPoint> implements Serializable{ 
    long startTime, lastTime;

    public void start() { start(System.nanoTime()); }
    public void start(long startTime) {
        if(!isEmpty()) super.clear();
        this.startTime = startTime;
        this.lastTime = startTime;
    }

    public void add(float x, float y) {
        if (isEmpty()) start();
        long now = System.nanoTime();
        add(new MouseTrajectoryPoint(now-startTime, now-lastTime, x, y));
        lastTime = now;
    }

    @Override public void clear() {
        super.clear();
    }
    
    public void paintPath(Graphics g,Color LineColor,int NumberPoints,int width,int height) {
        if (isEmpty()) return;

        int n = size();
        int[] x = new int[n], y = new int[n];
        int lastPoints = Math.max(0,n-NumberPoints);
        //If there are more than X points, only draw the newest X ones so that the screen is not all cluttered.
        for (int i = 0; i < Math.min(n,NumberPoints); i++) { 
            x[i] = (int) ((get(i+lastPoints).getX()+1)*width);
            y[i] = (int) ((get(i+lastPoints).getY()+1)*height);
        }
        g.setColor(LineColor);
        g.drawPolyline(x, y, Math.min(n,NumberPoints));
    }  
    
    @Override public String toString() {
        String res = "";
        for (int i = 0; i < size(); i++) { 
            res = res +" ("+ get(i).getX()+","+get(i).getY()+" @"+get(i).getDifferenceTimeNanos()+"ns) ";
        }
        return res;
    }
}