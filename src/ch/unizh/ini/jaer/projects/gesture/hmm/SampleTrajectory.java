/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Random;

/**
 * Generate a sample trajectory with simple pattern
 * @author Jun Haeng Lee
 */
public class SampleTrajectory {
    /**
     * random
     */
    protected static Random random = new Random();
    /**
     * definition of sample patterns
     */
    public enum SAMPLE_TRJ_TYPE {LEFT, RIGHT, UP, DOWN, CIRCLE};

    /**
     * returns a sample trajectory
     *
     * @param type
     * @param totalLenPixels
     * @param maxXPixels
     * @param maxYPixels
     * @param minNumPoints
     * @return
     */
    public synchronized ArrayList<Point2D.Float> getSampleTrajetory(SAMPLE_TRJ_TYPE type, int totalLenPixels, int maxXPixels, int maxYPixels, int minNumPoints){
        ArrayList<Point2D.Float> trajectory = new ArrayList<Point2D.Float>();
        Point2D.Float startPos = null;
        Point2D.Float currPos = null;
        Point2D.Float prevPos = null;
        double length = 0;

        switch(type){
            case RIGHT:
                startPos = getPointRandom(0, 0, maxXPixels - totalLenPixels, maxYPixels);
                prevPos = startPos;
                trajectory.add(startPos);
                do{
                    currPos = getPointRandom((int) prevPos.x, (int) prevPos.y, totalLenPixels/minNumPoints, 0);
                    if(currPos.x > maxXPixels)
                        currPos.x = maxXPixels;
                    length += FeatureExtraction.distance(prevPos, currPos);
                    trajectory.add(currPos);
                    prevPos = currPos;
                }while(length < totalLenPixels);
                break;
            case LEFT:
                startPos = getPointRandom(totalLenPixels, 0, maxXPixels, maxYPixels);
                prevPos = startPos;
                trajectory.add(startPos);
                do{
                    currPos = getPointRandom((int) prevPos.x, (int) prevPos.y, -1*totalLenPixels/minNumPoints, 0);
                    if(currPos.x < 0)
                        currPos.x = 0;
                    length += FeatureExtraction.distance(prevPos, currPos);
                    trajectory.add(currPos);
                    prevPos = currPos;
                }while(length < (double) totalLenPixels);
                break;
            case UP:
                startPos = getPointRandom(0, 0, maxXPixels, maxYPixels - totalLenPixels);
                prevPos = startPos;
                trajectory.add(startPos);
                do{
                    currPos = getPointRandom((int) prevPos.x, (int) prevPos.y, 0, totalLenPixels/minNumPoints);
                    if(currPos.y > maxYPixels)
                        currPos.y = maxYPixels;
                    length += FeatureExtraction.distance(prevPos, currPos);
                    trajectory.add(currPos);
                    prevPos = currPos;
                }while(length < totalLenPixels);
                break;
            case DOWN:
                startPos = getPointRandom(0, totalLenPixels, maxXPixels, maxYPixels);
                prevPos = startPos;
                trajectory.add(startPos);
                do{
                    currPos = getPointRandom((int) prevPos.x, (int) prevPos.y, 0, -1*totalLenPixels/minNumPoints);
                    if(currPos.y < 0)
                        currPos.y = 0;
                    length += FeatureExtraction.distance(prevPos, currPos);
                    trajectory.add(currPos);
                    prevPos = currPos;
                }while(length < totalLenPixels);
                break;
            case CIRCLE:
                int radius = (int) (totalLenPixels/2.0/Math.PI);
                Point2D.Float center = getPointRandom(radius, radius, maxXPixels-radius, maxYPixels-radius);
                float offsetAngle = (float) (random.nextFloat()*2.0*Math.PI - Math.PI);
                float prevAngle = 0f;
                trajectory.add(getPointAngle(center, prevAngle + offsetAngle, radius));
                boolean doLoop = true;
                do{
                    float currAngle = prevAngle + (float) (random.nextFloat()*2.0*Math.PI/minNumPoints);
                    if(currAngle > (float) (2.0*Math.PI)){
                        currAngle = (float) (2.0*Math.PI);
                        doLoop = false;
                    }
                    trajectory.add(getPointAngle(center, currAngle + offsetAngle, radius));
                    prevAngle = currAngle;
                }while(doLoop);
                break;
            default:
                break;
        }

        System.out.print("Trajectory : ");
        for(Point2D.Float pos:trajectory)
            System.out.print("("+(int)pos.x+", "+(int)pos.y+"), ");
        System.out.println();
        
        return trajectory;
    }

    private Point2D.Float getPointRandom(int minX, int minY, int xLen, int yLen){
        Point2D.Float out = new Point2D.Float();

        out.x = minX + (int) (random.nextFloat()*xLen);
        out.y = minY + (int) (random.nextFloat()*yLen);

        return out;
    }

    private Point2D.Float getPointAngle(Point2D.Float center, float angle, float radius){
        Point2D.Float out = new Point2D.Float();

        out.x = center.x + (int) (radius*Math.cos(angle));
        out.y = center.y + (int) (radius*Math.sin(angle));

        return out;
    }
}
