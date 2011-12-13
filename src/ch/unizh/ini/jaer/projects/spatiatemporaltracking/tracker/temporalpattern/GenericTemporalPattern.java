/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram.SimpleHistogram;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;

/**
 *
 * @author matthias
 * 
 * Creates a new temporal pattern based on generic data.
 */
public class GenericTemporalPattern extends AbstractTemporalPattern {
    
    /**
     * Creates a new GenericTemporalPattern.
     * 
     * @param pattern The temporal pattern.
     * @param frequency The frequency of the signal.
     * @param name The name of the signal.
     */
    public GenericTemporalPattern(byte [] pattern, int frequency, String name) {
        this.name = name;
        this.color = color.getColor();
        
        this.histogramOff2On = new SimpleHistogram();
        this.histogramOn2Off = new SimpleHistogram();
        this.signal = new SimpleSignal();
        
        int step = (int)Math.pow(10, 6) / frequency / pattern.length;
        
        int offset = 0;
        while (pattern[offset] == pattern[offset + 1]) {
            offset++;
        }
        offset++;
        
        int last = 0;
        int start = offset;
        for (int i = 0; i <= pattern.length; i++) {
            int end = (i + offset) % pattern.length;
            
            if (pattern[start] != pattern[end]) {
                int diff = (i - last) * step;
                
                if (pattern[end] == 1) {
                    this.histogramOff2On.add(diff);
                }
                else {
                    this.histogramOn2Off.add(diff);
                }
                this.signal.add(i * step, pattern[end]);
                
                start = end;
                last = i;
            }
        }
    }
}
