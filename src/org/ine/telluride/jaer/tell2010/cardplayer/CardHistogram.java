/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2010.cardplayer;

import java.util.Arrays;

import net.sf.jaer.util.networking.UDPMesssgeSender;

/**
 * Histogram that holds card statistics.
 *
 * @author tobi
 */
public class CardHistogram {

    int[] valueCounts = new int[15];  // values including less than 1 pip and more than 13 (king)
    int[] suitCounts = new int[5]; // suits, including bin 0 which is "don't know"
    final char SEP = UDPMesssgeSender.SEP;
    final String header = "retina"+SEP+"cardstats"+SEP;
    private int maxValueBin=0, maxSuitBin=4;

    public void reset() {
        Arrays.fill(valueCounts, 0);
        Arrays.fill(suitCounts, 0);
        maxValueBin=0;
        maxSuitBin=4;
    }

    public final void incValue(int value) {
        valueCounts[value]++;
        if(valueCounts[value]>valueCounts[maxValueBin]) maxValueBin=value;    
    }

    //Method added by DLM to set the value of one of the card value hist bins
    //This is an additional method I added
    public final void setValue(int card_value, int num) {
        valueCounts[card_value] = num;
        if(valueCounts[card_value]>valueCounts[maxValueBin]) maxValueBin=card_value;
    }

    public final void incSuit(int suit) {
        suitCounts[suit]++;
         if(suitCounts[suit]>suitCounts[maxSuitBin]) maxSuitBin=suit;
   }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(header);
        for (int i : valueCounts) {
            sb.append(i);
            sb.append(SEP);
        }
        for (int i : suitCounts) {
            sb.append(i);
            sb.append(SEP);
        }
        return sb.toString();
    }

    /**
     * @return the maxValueBin
     */
    public int getMaxValueBin() {
        return maxValueBin;
    }

    /**
     * @return the maxSuitBin
     */
    public int getMaxSuitBin() {
        return maxSuitBin;
    }
}
