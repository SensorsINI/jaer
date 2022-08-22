package org.ine.telluride.jaer.tell2022;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Logger;

public class RodSequence extends ArrayList<RodPosition> implements Serializable {

    static Logger log = Logger.getLogger("GoingFishing");
    static String FILENAME = "GoingFishingRodSequence.ser";

    private int index;
    public long durationMs = 0, timeToMinZMs = 0;
    private int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, minTheta = Integer.MAX_VALUE, maxTheta = Integer.MIN_VALUE;

    public RodSequence(int index) {
        this.index = index;
    }

    public void save() {
        if (size() == 0) {
            log.info("saving sequence of zero length");
        }
        durationMs=0;
        for (RodPosition p : this) {
            durationMs += p.delayMsToNext;
            if (p.zDeg < minZ) {
                minZ = p.zDeg;
                timeToMinZMs = durationMs;
            }
            if (p.zDeg > maxZ) {
                maxZ = p.zDeg;
            }
            if (p.thetaDeg < minTheta) {
                minTheta = p.thetaDeg;
            }
            if (p.thetaDeg > maxTheta) {
                maxTheta = p.thetaDeg;
            }
        }
        FileOutputStream fos = null;
        try {
            String fn = FILENAME + index;
            fos = new FileOutputStream(fn);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this);
            oos.close();
            log.info("saved " + this + " to file " + fn);
        } catch (Exception ex) {
            log.warning("Could not save fishing rod sequence: " + ex.toString());
        }
    }

    public void load(int index) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(FILENAME + index);
        ObjectInputStream ois = new ObjectInputStream(fis);
        RodSequence rodSequence = (RodSequence) ois.readObject();
        ois.close();
        clear();
        for (RodPosition p : rodSequence) {
            add(p);
        }
        this.index = rodSequence.index;
        this.durationMs = rodSequence.durationMs;
        this.maxTheta = rodSequence.maxTheta;
        this.minTheta = rodSequence.minTheta;
        this.maxZ = rodSequence.maxZ;
        this.minZ = rodSequence.minZ;
        this.timeToMinZMs = rodSequence.timeToMinZMs;
    }

    @Override
    public void clear() {
        super.clear();
        durationMs = 0;
        minZ = Integer.MAX_VALUE;
        maxZ = Integer.MIN_VALUE;
        minTheta = Integer.MAX_VALUE;
        maxTheta = Integer.MIN_VALUE;
    }

    @Override
    public boolean add(RodPosition p) {
        durationMs += p.delayMsToNext;
        return super.add(p);
    }

    @Override
    public String toString() {
        return String.format("Fishing rod sequence #%d with %,d positions\n total duration %,d ms; timeToMinZMs=%,d ms\n"
                + "minZ=%d, maxZ=%d, minTheta=%d, maxTheta=%d", 
                index, size(), durationMs, timeToMinZMs,minZ,maxZ,minTheta,maxTheta);
    }

    /**
     * @return the index
     */
    public int getIndex() {
        return index;
    }

}
