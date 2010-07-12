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

    public void reset() {
        Arrays.fill(valueCounts, 0);
        Arrays.fill(suitCounts, 0);
    }

    public final void incValue(int value) {
        valueCounts[value]++;
    }

    public final void incSuit(int suit) {
        suitCounts[suit]++;
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
}
