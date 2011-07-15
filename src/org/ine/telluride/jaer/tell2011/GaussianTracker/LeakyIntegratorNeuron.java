/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ine.telluride.jaer.tell2011.GaussianTracker;

/**
 * A leaky integrator neuron model, which does not send out spikes.
 * @author Michael Pfeiffer
 */
public class LeakyIntegratorNeuron {

    private float tau;   // Time constant
    private float Vleak; // Leak voltage
    private float Vreset; // Reset potential
    private float V;      // Membrane potential

    private float[] w;   // Synaptic weights

    private float timestamp;  // Time of last input spike

    private float meanActivation; // mean activation of the neuron
    private float lambda;         // Update rate for mean activation

    public LeakyIntegratorNeuron(float tau, float Vleak, float Vreset) {
        this.tau = tau;
        this.Vleak = Vleak;
        this.Vreset = Vreset;

        V = Vreset;  // Initialize membrane potential
        timestamp = 0.0f;

        this.meanActivation = Vleak*Vleak;
        this.lambda = 0.01f;

        w = null;
    }

        /** Simplified exponential function for performance reasons. */
    private float simple_exp(float x) {
        return (1+x+x*x/2.0f);
    }

    /** Update membrane potential */
    public float update(float delta_v, float ts) {
        // Timestamps are in micro-seconds
        // System.out.println("EXP " + simple_exp(-tau*(ts-timestamp)/1000000f));
        
        // V = Vleak + delta_v + (V-Vleak)*simple_exp(tau*(ts-timestamp)/1000000f);
        V = Vleak + delta_v + (V-Vleak)*simple_exp(-tau*(ts-timestamp)/(1000000f));
        timestamp = ts;
        // System.out.println(V);

        meanActivation = (1-lambda)*meanActivation + lambda*V;

        return V;
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

    /** Returns the membrane potential at a given timepoint (assuming no spikes in between) */
    public float getV(float ts) {
        // return Vleak + (V-Vleak)*simple_exp(tau*(ts-timestamp)/1000000f);
        return V;
    }

    public float getLambda() {
        return lambda;
    }

    public void setLambda(float lambda) {
        this.lambda = lambda;
    }

    public float getMeanActivation() {
        return meanActivation;
    }

    public void setMeanActivation(float meanActivation) {
        this.meanActivation = meanActivation;
    }

}
