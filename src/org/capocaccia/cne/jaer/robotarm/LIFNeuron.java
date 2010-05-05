/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.capocaccia.cne.jaer.robotarm;

/**
 *
 * @author Michael Pfeiffer, Alex Russell
 */
public class LIFNeuron {

    private float tau;   // Time constant
    private float Vleak; // Leak voltage
    private float Vreset; // Reset potential
    private float thresh;  // firing threshold
    private float V;      // Membrane potential

    private float[] w;   // Synaptic weights

    private float timestamp;  // Time of last input spike

    public LIFNeuron(float tau, float Vleak, float Vreset, float thresh) {
        this.tau = tau;
        this.Vleak = Vleak;
        this.Vreset = Vreset;
        this.thresh = thresh;

        V = Vreset;  // Initialize membrane potential
        timestamp = 0.0f;

        w = null;
    }

        /** Simplified exponential function for performance reasons. */
    private float simple_exp(float x) {
        return (1+x+x*x/2.0f);
    }

    /** Update membrane potential */
    public int update(float delta_v, float ts) {
        // Timestamps are in micro-seconds
        // System.out.println(delta_v);
        V = Vleak + delta_v + (V-Vleak)*simple_exp(tau*(ts-timestamp)/1000000f);
        timestamp = ts;
//        System.out.println(V);
        if (V >= thresh) {
            V = Vreset;
            return 1;
        }
        else return 0;
    }


    /** Reset the membrane potentials of the neurons */
    public void reset_neuron() {
        V = Vreset;
        timestamp = 0.0f;
    }

    /** Set the input weights */
    public void setweights(float[] w) {
        if (this.w == null) {
            this.w = new float[w.length];
        }
        for (int i=0; i<w.length; i++)
            this.w[i] = w[i];
    }

    public float getVleak() {
        return Vleak;
    }

    public void setVleak(float Vleak) {
        this.Vleak = Vleak;
    }

    public float getVreset() {
        return Vreset;
    }

    public void setVreset(float Vreset) {
        this.Vreset = Vreset;
    }

    public float getTau() {
        return tau;
    }

    public void setTau(float tau) {
        this.tau = tau;
    }

    public float getThresh() {
        return thresh;
    }

    public void setThresh(float thresh) {
        this.thresh = thresh;
    }



}
