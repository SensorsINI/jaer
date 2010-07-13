/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * Class which reports the upcoming curvature on the track.
 * The curvature is actually the radius of curvature - the radius of the osculating circle in pixels.
 * The tighter the turn, the smaller this radius of curvature. The curvature approaches infinity for straight sections.
 * 
 * @author Michael Pfeiffer
 */
public class UpcomingCurvature {

    /** Vector of upcoming curvatures */
    private float curvature[];

    /** Number of stored curvature objects */
    private int numPoints;

    public UpcomingCurvature(float[] curvature) {
        if ((curvature != null) && (curvature.length > 0)) {
            this.numPoints = curvature.length;
            this.curvature = new float[numPoints];
            for (int i=0; i<numPoints; i++) {
                this.curvature[i] = curvature[i];
            }
        } else {
            this.curvature = new float[1];
            numPoints = 1;
            this.curvature[0] = 0;
        }
    }

    public UpcomingCurvature(float curvature) {
        numPoints = 1;
        this.curvature = new float[1];
        this.curvature[0] = curvature;
    }

    /** Returns the next upcoming curvature */
    public float getNextCurvature() {
        return curvature[0];
    }

    /** Sets the next upcoming curvature */
    public void setNextCurvature(float curvature) {
        this.curvature[0] = curvature;
    }

    /** Returns the number of stored upcoming curvatures */
    public int getNumPoints() {
        return numPoints;
    }

    /** Sets a new array of curvatures */
    public void setCurvature(float[] curvature) {
        if ((curvature != null) && (curvature.length > 0)) {
            numPoints = curvature.length;
            for (int i=0; i<numPoints; i++) {
                this.curvature[i] = curvature[i];
            }
        }
    }

    /** Returns the next upcoming curvatures */
    public float[] getCurvature() {
        return curvature;
    }

    /** Returns the curvature at a given index
     @param idx the index. 0 is the curvature at current location, 1 at next location, etc. TODO fix doc here
     */
    public float getCurvature(int idx) {
        if ((idx >= 0) && (idx < numPoints))
            return curvature[idx];
        else
            return 0.0f;
    }

    /** Sets the curvature at a given index */
    public void setCurvature(int idx, float newCurvature) {
        if ((idx >= 0) && (idx < numPoints))
            curvature[idx] = newCurvature;
    }
}
