/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Gesture feature extraction. Convert a trajectory to a sequence of features
 * @author Administrator
 */
public class FeatureExtraction{
    // sequence length. Total length of the given trajectory will be divided into this number of sections
    private int seqLength;

    // number of directions used for codewords
    int numDirs;

    // 2pi/numFeatures
    private double deltaAngle;

    /**
     * constructor with number of directions
     * @param numDirs : number of directions used as features
     */
    public FeatureExtraction(int numDirs, int seqLength) {
        deltaAngle = 2.0*Math.PI/numDirs;
        this.seqLength = seqLength;
        this.numDirs = numDirs;
    }

    /**
     * convert a trajectory into a sequence of codewords
     * @param trajectory
     * @return
     */
    public String[] convToFeatureArray(ArrayList<? extends Point2D.Float> trajectory){
        String[] out = new String[seqLength];

        if(trajectory.size() == 0)
            return out;

        double totalTrajLen = calTrajectoryLength(trajectory);
        double deltaTrajLen = totalTrajLen/seqLength;
        Point2D.Float startPosition = trajectory.get(0); // the oldest position
        Point2D.Float prevPosition = startPosition;
        Point2D.Float currPosition = null;
        
        int j = 1;
        double len = 0;
        for(int i=0; i<seqLength; i++){
            while(true){
                currPosition = trajectory.get(j);
                len += distance(prevPosition, currPosition);
                if(len >= (i+1)*deltaTrajLen || j==trajectory.size()-1){
                    len -= distance(prevPosition, currPosition);
                    break;
                } else{
//                    System.out.println("curr pos = ("+currPosition.x+", "+currPosition.y+"), dist = " + distance(prevPosition, currPosition));
                }

                prevPosition = currPosition;
                j++;
            }
//            System.out.print("Required dist = " + ((i+1)*deltaTrajLen - len));

            currPosition = interpolatePosition(prevPosition, currPosition, (i+1)*deltaTrajLen - len);
            len += distance(prevPosition, currPosition);
            
//            System.out.println(", New dist = " + distance(prevPosition, currPosition));
            out[i] = convToFeature(calAngle(startPosition, currPosition));
//            System.out.println("Total length = " + totalTrajLen + ", current length = " + len);

            startPosition = currPosition;
            prevPosition = currPosition;

            if(j==trajectory.size()-1 && Math.abs(len - totalTrajLen) < deltaTrajLen*0.2)
                break;
        }
        
        return out;
    }

    /**
     * calculate angle of movement from p1 to p2
     * @param startPosition
     * @param endPosition
     * @return
     */
    private double calAngle(Point2D.Float startPosition, Point2D.Float endPosition){
        double angle = 0;

        double deltaX = endPosition.x - startPosition.x;
        double deltaY = endPosition.y - startPosition.y;

        if(deltaX == 0){
            if(deltaY == 0)
                angle = 0;
            else if(deltaY > 0)
                angle = Math.PI/2.0;
            else
                angle = -1.0*Math.PI/2.0;
        } else if(deltaX > 0){
            angle = Math.atan(deltaY/deltaX);
        } else{
            angle = Math.atan(deltaY/deltaX) + Math.PI;
        }

//        System.out.println("st pos = ("+startPosition.x+", "+startPosition.y+"), end pos =("+endPosition.x+", "+endPosition.y+"), angle = " + angle);
        return refactorAngle(angle);
    }

    /**
     * find a position with the specified distance from p1 on the line between p1 and p2
     * @param p1
     * @param p2
     * @param len
     * @return
     */
    private Point2D.Float interpolatePosition(Point2D.Float p1, Point2D.Float p2, double len){
        Point2D.Float out = new Point2D.Float();
        double ratio = len/distance(p1, p2);

        out.x = (int) ((1-ratio)*p1.x + ratio*p2.x);
        out.y = (int) ((1-ratio)*p1.y + ratio*p2.y);

        return out;
    }

    /**
     * calculate the total length of the trajectory
     * @param trajectory
     * @return
     */
    private double calTrajectoryLength(ArrayList<? extends Point2D.Float> trajectory){
        double length = 0;

        Point2D.Float prevPosition = null;
        for(Point2D.Float currPosition:trajectory){
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
            }
            prevPosition = currPosition;
        }

        return length;
    }

    public static double distance(Point2D.Float p1, Point2D.Float p2){
        return Math.sqrt(Math.pow(p1.x-p2.x,2.0)+Math.pow(p1.y-p2.y, 2.0));
    }


    /**
     * convert an angle to a codeword
     * @param angle
     * @return
     */
    public String convToFeature(double angle){
        int codeword = (int) (refactorAngle(angle)/deltaAngle + 0.5);
        if(codeword >= numDirs)
            codeword = numDirs - 1;

//        System.out.println("codeword of " + angle +" : " + codeword);

        return ""+codeword;
    }

    /**
     * make angle be between 0 and 2pi
     * @param angle
     * @return
     */
    private double refactorAngle(double angle){
        while(angle < 0)
            angle+=2.0*Math.PI;

        while(angle >= 2.0*Math.PI)
            angle-=2.0*Math.PI;

        return angle;
    }
}
