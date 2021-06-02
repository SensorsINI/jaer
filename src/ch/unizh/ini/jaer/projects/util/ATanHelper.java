package ch.unizh.ini.jaer.projects.util;

/**
 * At creation, sets up a lookup table for atan(x) -values with precision better
 or equal resolution. The worst precision is reached for angles closer than
 maxThetaDiff to a theta of 0° or very close to +- 90°. Apart from that,
 precision improves from 0° towards higher angles. Steps are aequidistant in x
 (as in "atan(x)"). Look at a tangens graph and draw this if unsure.

 Should work like atan2; that is, returns angles between -180° and 180°.

 There is a middle entry, 0°, and an entry for +-90° each.

 Helper funciton atan: Every atan-result bigger than 90°-resolution/2 is
 returned as 90°, and the same for results close to -90°.
 *
 * @author Susi
 */
public class ATanHelper {

    private float resolution; //in degrees
    private float stepLength;
    private float highestTanCoveredByTable;     //or rather its absolute value
    private int nrOfEntries;    //without both border cases
    private float[] atanTable;
    private boolean useTable;

    public ATanHelper(float resolution) {
        useTable = true;
        this.resolution = resolution;
        initTable();
    }
    
    public ATanHelper(float resolution, boolean useTabl) {
        useTable = useTabl;
        this.resolution = resolution;
        initTable();
    }
    
    private void initTable(){
        //step length between two table entries:
        stepLength = 2.0f * (float) Math.tan(Math.toRadians(resolution / 2.0f));
        float maxTan = (float) Math.tan(Math.toRadians(90.0f - resolution / 2.0f));
        //round up the number of times stepLength fits between maxTan and "minTan" = stepLength/2
        int nrOfEntriesEntirelyOnOneSide = (int) ((maxTan - stepLength / 2.0f) / stepLength) + 1;
        nrOfEntries = 2 * nrOfEntriesEntirelyOnOneSide + 1;
        highestTanCoveredByTable = (float) (nrOfEntriesEntirelyOnOneSide + 0.5f) * stepLength;

        atanTable = new float[nrOfEntries];
        for (int i = 0; i < nrOfEntries; i++) {
            atanTable[i] = (float) Math.toDegrees(Math.atan(-(float) nrOfEntriesEntirelyOnOneSide * stepLength + i * stepLength));
        }
    }

    //like Math.atan2, but returns degrees. Returns an angle in the range )-180°,180°].
    public float atan2(float x, float y) {
        if (!useTable) {
            return (float) Math.toDegrees(Math.atan2(y, x));
        }
        if (x == 0) {
            if (y == 0) {
                return 0.0f;
            } else if (y > 0) {
                return 90;
            } else if (y < 0) {
                return -90;
            }
        }
        float theta = atan(y / x);

        if (x < 0) {
            if (theta <= 0) //gives the interval )-180°,180°]
            {
                return theta + 180;
            } else if (theta > 0) {
                return theta - 180;
            }
        }
        return theta;
    }

    //like Math.atan()
    public float atan(float z) {   // z = y/x
        if (!useTable) {
            return (float) Math.toDegrees(Math.atan(z));
        }
        if (z > highestTanCoveredByTable) {
            return 90.0f;
        }
        if (z < -highestTanCoveredByTable) {
            return -90.0f;
        }

        int index = (int) ((z + highestTanCoveredByTable) / stepLength);
        return atanTable[index];
    }

    public void printTable() {
        for (int y = -50; y < 150; y++) {
            System.out.println("atan of x/y, x = 10, y = " + y + ": " + atan2(10.0f, y));
        }
    }
    
    public int getNrOfEntries() {
        return nrOfEntries;
    }

    public boolean isUseTable(){
        return useTable;
    }
    
    public void setUseTable(boolean b) {
        useTable = b;
    }
    
    public float getResolution(){
        return resolution;
    }
    
    public void setResolution(float resolution) {
        this.resolution = resolution;
        initTable();
    }
}
