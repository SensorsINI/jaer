/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram.SimpleHistogram;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

/**
 *
 * @author matthias
 * 
 * Creates a new temporal pattern based on the extracted signal.
 */
public class SignalBasedTemporalPattern extends AbstractTemporalPattern {
    
    /**
     * Creates a new SignalBasedTemporalPattern.
     * 
     * @param extractable The object containing all informations extracted from
     * the observed signal.
     */
    public SignalBasedTemporalPattern(Signal signal) {
        this.name = "signal (" + identifier++ + ")";
        this.color = Color.getColor();
        
        this.histogramOff2On = new SimpleHistogram();
        this.histogramOn2Off = new SimpleHistogram();
        this.signal = new SimpleSignal(signal);
        
        for (int i = 0; i < this.signal.getSize(); i++) {
            int diff = this.signal.getTransition(i).time - this.signal.getTransition(i - 1).time;
            switch(this.signal.getTransition(i).state) {
                case 1:
                    this.histogramOff2On.add(diff);
                    break;
                case 0:
                    this.histogramOn2Off.add(diff);
                    break;
            }
        }
    }
}
