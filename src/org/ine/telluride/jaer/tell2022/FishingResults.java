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
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import static org.ine.telluride.jaer.tell2022.RodSequence.log;

// collected results
class FishingResults implements Serializable {

    int rodDipTotalCount = 0;
    public int rodDipSuccessCount = 0;
    public int rodDipFailureCount = 0;
    public ArrayList<FishingResult> fishingResultsList = new ArrayList();
    public ArrayList<Float> sucTheta = new ArrayList();
    public ArrayList<Float> failTheta = new ArrayList();
    public ArrayList<Float> sucDelay = new ArrayList();
    public ArrayList<Float> failDelay = new ArrayList();
    public ArrayList<Integer> attemptSucesses = new ArrayList();
    public ArrayList<Integer> attemptFailures = new ArrayList();
    public ArrayList<Integer> tryNumberSuccesses = new ArrayList();
    public ArrayList<Integer> tryNumberFailures = new ArrayList();
    private static final int WINDOW = 100;
    DescriptiveStatistics sucThetaStats = new DescriptiveStatistics(WINDOW), sucDelayStats = new DescriptiveStatistics(WINDOW);
    public float rodThetaOffset = 0;
    public int rodDipDelayMs = 0;

    FishingResults() {
    }

    @Override
    public String toString() {
        return String.format("FishingResults: %d tries, %d successes (%.1f%%). Learned theta=%.1f deg delay=%d ms",
                rodDipTotalCount, rodDipSuccessCount, 100f * rodDipSuccessCount / rodDipTotalCount,
                rodThetaOffset, rodDipDelayMs);
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
        attemptSucesses.clear();
        attemptFailures.clear();
        tryNumberSuccesses.clear();
        tryNumberFailures.clear();
        sucThetaStats.clear();
        sucDelayStats.clear();
        rodThetaOffset = 0;
        rodDipDelayMs = 0;
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
            attemptSucesses.add(1);
            tryNumberSuccesses.add(rodDipTotalCount);
            sucThetaStats.addValue(randomThetaOffsetDeg);
            sucDelayStats.addValue(randomDelayMs);
            rodThetaOffset = (float) sucThetaStats.getPercentile(50);
            rodDipDelayMs = (int) (sucDelayStats.getPercentile(50));
        } else {
            rodDipFailureCount++;
            failTheta.add(randomThetaOffsetDeg);
            failDelay.add((float) randomDelayMs);
            attemptFailures.add(0);
            tryNumberFailures.add(rodDipTotalCount);
        }
    }

    void plot() throws Exception {
        Plot plt = Plot.create(); // see https://github.com/sh0nk/matplotlib4j
        plt.subplot(2, 1, 1);
        plt.xlabel("delay (ms)");
        plt.ylabel("angle (deg)");
        plt.plot().add(sucDelay, sucTheta, "go").linewidth(1).linestyle("None");
        plt.plot().add(failDelay, failTheta, "rx").linewidth(1).linestyle("None");
        plt.legend();

        plt.subplot(2, 1, 2);
        plt.xlabel("Fishing Attempt #");
        plt.ylabel("Sucess(0) Failure(X)");

        plt.plot().add(tryNumberSuccesses, attemptSucesses, "go").linewidth(1).linestyle("None");
        plt.plot().add(tryNumberFailures, attemptFailures, "rx").linewidth(1).linestyle("None");
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
        log.info("loaded " + fishingResults);
        return fishingResults;
    }

}
