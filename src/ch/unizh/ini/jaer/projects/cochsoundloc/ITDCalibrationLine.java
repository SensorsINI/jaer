/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 * Stores a line for the calibration of ITDs.
 * 
 * @author Holger
 */
public class ITDCalibrationLine {

    private int start = 0;
    private int end = 0;
    private double c = 0;
    private double m = 0;
    private int size = 0;

    /**
     * @return the start
     */
    public int getStart() {
        return start;
    }

    /**
     * @param start the start to set
     */
    public void setStart(int start) {
        this.start = start;
        this.size = this.end - this.start;
    }

    /**
     * @return the end
     */
    public int getEnd() {
        return end;
    }

    /**
     * @param end the end to set
     */
    public void setEnd(int end) {
        this.end = end;
        this.size = this.end - this.start;
    }

    /**
     * @return the c
     */
    public double getC() {
        return c;
    }

    /**
     * @param c the c to set
     */
    public void setC(double c) {
        this.c = c;
    }

    /**
     * @return the m
     */
    public double getM() {
        return m;
    }

    /**
     * @param m the m to set
     */
    public void setM(double m) {
        this.m = m;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return size;
    }
}
