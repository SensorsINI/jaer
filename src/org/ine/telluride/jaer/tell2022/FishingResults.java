/*
 * Copyright (C) 2022 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.ine.telluride.jaer.tell2022;

import com.github.sh0nk.matplotlib4j.Plot;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import static org.ine.telluride.jaer.tell2022.RodSequence.log;

// collected results
class FishingResults implements Serializable{

    int rodDipTotalCount = 0;
    public int rodDipSuccessCount = 0;
    public int rodDipFailureCount = 0;
    public ArrayList<FishingResult> fishingResultsList = new ArrayList();
    public ArrayList<Float> sucTheta = new ArrayList();
    public ArrayList<Float> failTheta = new ArrayList();
    public ArrayList<Float> sucDelay = new ArrayList();
    public ArrayList<Float> failDelay = new ArrayList();

    FishingResults() {
    }

    void clear() {
        rodDipTotalCount = 0;
        rodDipSuccessCount = 0;
        rodDipFailureCount = 0;
        fishingResultsList.clear();
        sucTheta.clear();
        failTheta.clear();
        sucDelay.clear();
        failDelay.clear();
    }

    /**
     * add fishing result
     *
     * @param b true for success, false for failure
     * @param randomThetaOffsetDeg
     * @param randomDelayMs
     */
    void add(boolean b, float randomThetaOffsetDeg, int randomDelayMs) {
        rodDipTotalCount++;
        fishingResultsList.add(new FishingResult(b, randomThetaOffsetDeg, randomDelayMs));
        if (b) {
            rodDipSuccessCount++;
            sucTheta.add(randomThetaOffsetDeg);
            sucDelay.add((float) randomDelayMs);
        } else {
            rodDipFailureCount++;
            failTheta.add(randomThetaOffsetDeg);
            failDelay.add((float) randomDelayMs);
        }
    }

    void plot() throws Exception {
        Plot plt = Plot.create(); // see https://github.com/sh0nk/matplotlib4j
        plt.subplot(1, 1, 1);
        plt.title("Fishing results");
        plt.xlabel("delay (ms)");
        plt.ylabel("angle (deg)");
        plt.plot().add(sucDelay, sucTheta, "go").linewidth(1).linestyle("None");
        plt.plot().add(failDelay, failTheta, "rx").linewidth(1).linestyle("None");
        plt.legend();
        plt.show();
    }

    public static void save(FishingResults r, File file) throws FileNotFoundException, IOException {
        FileOutputStream fos = null;
        fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(r);
        oos.close();
        log.info("saved " + r + " to file " + file);
    }

    public static FishingResults load(File file) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        FishingResults fishingResults = (FishingResults) ois.readObject();
        ois.close();
        log.info("loaded "+fishingResults);
        return fishingResults;
    }

}
