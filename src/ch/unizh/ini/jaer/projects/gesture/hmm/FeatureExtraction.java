/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Gesture feature extraction. It converts a trajectory to a sequence of features
 * @author Jun Haeng Lee
 */
public class FeatureExtraction{
    /** 
     * sequence length, which is identical to the number of feature sequence.
     * Total length of the given trajectory will be divided into this number of sections to calculate feature sequence.
     */
    public int seqLength;

    /** 
     * number of directions used for codewords
     */
    public int numDirs;

    /** 
     * 2pi/numDirs
     */
    private double deltaAngle;

    /** 
     * array of vector angles which is not quantized. It may be necessary for Continuous HMM or GMM (Gaussian Mixture Model)
     */
    public double[] vectorAngleSeq;

    /**
     * constructor with number of directions
     * @param numDirs
     * @param seqLength
     */
    public FeatureExtraction(int numDirs, int seqLength) {
        deltaAngle = 2.0*Math.PI/numDirs;
        this.seqLength = seqLength;
        this.numDirs = numDirs;
        this.vectorAngleSeq = new double[seqLength];
    }

    /**
     * converts a trajectory into a sequence of codewords
     * @param trajectory
     * @return sequence of codewords
     */
    public String[] convTrajectoryToCodewords(ArrayList<? extends Point2D.Float> trajectory, double totalTrajLen){
        String[] out = new String[seqLength];

        if(trajectory.size() < 2)
            return out;

        if(totalTrajLen < 0)
            totalTrajLen = calTrajectoryLength(trajectory);
        double deltaTrajLen = totalTrajLen/seqLength;
        Point2D.Float startPosition = trajectory.get(0); // the oldest position
        Point2D.Float prevPosition = startPosition;
        Point2D.Float currPosition = null;

//        System.out.println("Total lent = " + totalTrajLen + ", Delta len = " + deltaTrajLen);
        
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
            vectorAngleSeq[i] = calAngle(startPosition, currPosition);
            out[i] = convToCodeword(vectorAngleSeq[i]);
//            System.out.println("Total length = " + totalTrajLen + ", current length = " + len);

            startPosition = currPosition;
            prevPosition = currPosition;

            if(j==trajectory.size()-1 && Math.abs(len - totalTrajLen) < deltaTrajLen*0.2)
                break;
        }
        
        return out;
    }

    /**
     * converts angles to codeswords
     * @param angles
     * @return
     */
    public String[] convAnglesToCodewords(double[] angles){
        String[] out = new String[angles.length];

        for(int i=0; i<angles.length; i++)
            out[i] = convToCodeword(angles[i]);

        return out;
    }


    /**
     * calculates the vector angle between p1 to p2
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
     * finds a position with the specified distance from p1 on the line between p1 and p2
     * @param p1
     * @param p2
     * @param len : distance from point 1
     * @return
     */
    private Point2D.Float interpolatePosition(Point2D.Float p1, Point2D.Float p2, double len){
        Point2D.Float out = new Point2D.Float();
        double ratio = len/distance(p1, p2);

        out.x = (float) ((1-ratio)*p1.x + ratio*p2.x);
        out.y = (float) ((1-ratio)*p1.y + ratio*p2.y);

        return out;
    }

    public double getAverageAngle(double startRatio, double endRatio){
        double av = 0;
        int startPos = (int)(vectorAngleSeq.length*startRatio);
        int endPos = (int)(vectorAngleSeq.length*endRatio);
        for(int i = startPos; i <= endPos; i++){
            av += vectorAngleSeq[i];
        }
        av /= (double)(endPos - startPos + 1);

        return av;
    }

    /**
     * calculates the total length of the trajectory
     * @param trajectory
     * @return
     */
    public static double calTrajectoryLength(ArrayList<? extends Point2D.Float> trajectory){
        double length = 0;

        Point2D.Float prevPosition = null;
        for(int i=0; i<trajectory.size(); i++){
            Point2D.Float currPosition  = trajectory.get(i);
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
            }
            prevPosition = currPosition;
        }

        return length;
    }

    public static double calTrajectoryLengthFrom(ArrayList<? extends Point2D.Float> trajectory, int offset){
        double length = 0;

        Point2D.Float prevPosition = null;
        for(int i=offset; i<trajectory.size(); i++){
            Point2D.Float currPosition  = trajectory.get(i);
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
            }
            prevPosition = currPosition;
        }

        return length;
    }

    public static double calTrajectoryLengthTo(ArrayList<? extends Point2D.Float> trajectory, int offset){
        double length = 0;

        Point2D.Float prevPosition = null;
        for(int i=0; i<Math.min(offset, trajectory.size()); i++){
            Point2D.Float currPosition  = trajectory.get(i);
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
            }
            prevPosition = currPosition;
        }

        return length;
    }

    public static double calTrajectoryLengthFromTo(ArrayList<? extends Point2D.Float> trajectory, int fromPos, int toPos){
        double length = 0;

        Point2D.Float prevPosition = null;
        for(int i=fromPos; i<Math.min(toPos, trajectory.size()); i++){
            Point2D.Float currPosition  = trajectory.get(i);
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
            }
            prevPosition = currPosition;
        }

        return length;
    }

    /**
     * returns the index of targer position from the head of trajectory
     *
     * @param trajectory
     * @param targetLength
     * @return
     */
    public static int getTrajectoryPositionForward(ArrayList<? extends Point2D.Float> trajectory, double targetLength){
        int pos = 0;
        double length = 0;

        Point2D.Float prevPosition = null;
        for(Point2D.Float currPosition:trajectory){
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
                pos++;
            }

            if(length >= targetLength)
                break;
            
            prevPosition = currPosition;
        }

        if(pos > trajectory.size()-1)
            pos = trajectory.size()-1;

        return pos;
    }

    /**
     * returns the index of targer position from the tail of trajectory
     *
     * @param trajectory
     * @param targetLength
     * @return
     */
    public static int getTrajectoryPositionBackward(ArrayList<? extends Point2D.Float> trajectory, double targetLength){
        int pos = trajectory.size()-1;
        double length = 0;

        Point2D.Float prevPosition = null;
        for(int i=trajectory.size()-1; i>=0; i--){
            Point2D.Float currPosition = trajectory.get(i);
            if(prevPosition != null){
                length += distance(prevPosition, currPosition);
                pos--;
            }

            if(length >= targetLength)
                break;

            prevPosition = currPosition;
        }

        if(pos < 0)
            pos = 0;

        return pos;
    }

    /** returns the distance between two points
     *
     * @param p1
     * @param p2
     * @return
     */
    public static double distance(Point2D.Float p1, Point2D.Float p2){
        return Math.sqrt(Math.pow(p1.x-p2.x,2.0)+Math.pow(p1.y-p2.y, 2.0));
    }


    /**
     * converts an angle to a codeword
     * @param angle
     * @return the codeword
     */
    public String convToCodeword(double angle){
        int codeword = (int) (refactorAngle(angle)/deltaAngle + 0.5);
        if(codeword >= numDirs)
            codeword = numDirs - 1;

//        System.out.println("codeword of " + angle +" : " + codeword);

        return ""+codeword;
    }

    /**
     * makes angle be between 0 and 2pi
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


    /**
     * converts angles to trajectory
     * @param startPos
     * @param angles
     * @param sectionLength
     * @return
     */
    public static ArrayList<Point2D.Float> convAnglesToTrajectory(Point2D.Float startPos, double[] angles, double sectionLength){
        ArrayList<Point2D.Float> outTrj = new ArrayList<Point2D.Float>(angles.length+1);
        Point2D.Float prevPos = startPos;

        outTrj.add(startPos);
        for(int i=0; i<angles.length; i++){
            Point2D.Float nextPos = new Point2D.Float();

            nextPos.x = prevPos.x + (float) (sectionLength*Math.cos(angles[i]));
            nextPos.y = prevPos.y + (float) (sectionLength*Math.sin(angles[i]));

            outTrj.add(nextPos);
            prevPos = nextPos;
        }

        return outTrj;
    }


    /**
     * converts angles to trajectory in the scaled area
     * @param center
     * @param size
     * @param angles
     * @return
     */
    public static ArrayList<Point2D.Float> convAnglesToTrajectoryInScaledArea(Point2D.Float center, float size, double[] angles){
        ArrayList<Point2D.Float> outTrj = new ArrayList<Point2D.Float>(angles.length+1);
        float minX, minY, maxX, maxY;

        outTrj = convAnglesToTrajectory(new Point2D.Float(0.0f, 0.0f), angles, 1.0);

        minX = maxX = outTrj.get(0).x;
        minY = maxY = outTrj.get(0).y;

        for(Point2D.Float nextPos:outTrj){
            if(nextPos.x < minX)
                minX = nextPos.x;
            else if(nextPos.x > maxX)
                maxX = nextPos.x;

            if(nextPos.y < minY)
                minY = nextPos.y;
            else if(nextPos.y > maxY)
                maxY = nextPos.y;
        }

        float width = maxX - minX;
        float height = maxY - minY;
        float scale;

        if(width > height)
            scale = size/width;
        else
            scale = size/height;

        Point2D.Float startPos = new Point2D.Float();
        startPos.x = center.x + (0.0f - (minX+maxX)/2)*scale;
        startPos.y = center.y + (0.0f - (minY+maxY)/2)*scale;

        outTrj = convAnglesToTrajectory(startPos, angles, scale);

        return outTrj;
    }

}
