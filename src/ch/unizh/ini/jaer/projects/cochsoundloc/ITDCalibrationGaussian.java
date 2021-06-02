/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 * Stores a gaussian for the calibration of ITDs.
 * 
 * @author Holger
 */
public class ITDCalibrationGaussian {

    public int chan=0;
    private int bin=0;
    private double mu=0;
    private double sigma=0;

    /**
     * @return the chan
     */
    public int getChan() {
        return chan;
    }

    /**
     * @param chan the chan to set
     */
    public void setChan(int chan) {
        this.chan = chan;
    }

    /**
     * @return the bin
     */
    public int getBin() {
        return bin;
    }

    /**
     * @param bin the bin to set
     */
    public void setBin(int bin) {
        this.bin = bin;
    }

    /**
     * @return the mu
     */
    public double getMu() {
        return mu;
    }

    /**
     * @param mu the mu to set
     */
    public void setMu(double mu) {
        this.mu = mu;
    }

    /**
     * @return the sigma
     */
    public double getSigma() {
        return sigma;
    }

    /**
     * @param sigma the sigma to set
     */
    public void setSigma(double sigma) {
        this.sigma = sigma;
    }

}
