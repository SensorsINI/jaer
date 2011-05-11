/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.thresholdlearner;

import ch.unizh.ini.jaer.projects.robothead.WordCount;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;

/**
 * A 2d+ values of pixel thresholds. In the case of the temporal contrast DVS silicon retina, this values holds the learned pixel temporal contrast thresholds.
 * This values is then used to render the retina output taking into account the various event thresholds.
 * 
 * @author tobi
 */
final public class ThresholdMap implements Observer {

    AEChip chip;
    float[] values;
    int sx, sy, ntypes, ntot;

    public ThresholdMap(AEChip chip) {
        this.chip = chip;
        chip.addObserver(this);
        allocateMap();
    }
    
    public void checkMapAllocation(){
        int n=chip.getNumCells();
        if(values==null || values.length!=n) allocateMap();
    }

    synchronized final void allocateMap() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        ntypes = chip.getNumCellTypes();
        ntot = sx * sy * ntypes;
        if (ntot == 0) {
            return;
        }
        if (values == null || values.length != ntot) {
            values = new float[ntot];
        }
        reset();
    }
    Random random = new Random();

    public void reset() {
        Arrays.fill(values, 1);
//        for (int i = 0; i < values.length; i++) {
//            float r = (float) random.nextGaussian();
//            r=r/3+.5f;
//            if(r<0) r=0; else if(r>1) r=1;
//
//            values[i] = r;
//        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if (arg instanceof String) {
                String evt = (String) arg;
                if (evt == AEChip.EVENT_NUM_CELL_TYPES || evt == AEChip.EVENT_SIZEX || evt == AEChip.EVENT_SIZEY) {
                    allocateMap();
                }
            }
        }
    }

    public final float getThreshold(int x, int y, int type) {
        return values[index(x, y, type)];
    }

    public final void setThreshold(int x, int y, int type, float value) {
        values[index(x, y, type)] = value;
    }

    private int index(int x, int y, int type) {
        return type + x * ntypes + y * ntypes * sx;
    }

    public void save(File file) throws IOException {
    }

    public void load(File file) throws IOException {

        loadType(0, new File(file.getPath().toString() + "_n.txt"));
        loadType(1, new File(file.getPath().toString() + "_p.txt"));
    }

    private void loadType(int type, File file) throws IOException {
        /** returns an array of doubles read from the file in filename
         *  the file contains the matrix A saved in MATLAB by:  save A.txt A -ASCII     */
        Logger log = Logger.getAnonymousLogger();

        int colNumb;
        int rowNumb;

        WordCount wCounter = new WordCount();     // count lines and words to determine array size!
        wCounter.count(file.getPath().toString());

        if(wCounter.numWords!=length()){
            throw new IOException("number of words in file ("+wCounter.numWords+") does not match required number of elements in map ("+length()+")");
        }
        rowNumb = wCounter.numLines;
        colNumb = wCounter.numWords / rowNumb;

        BufferedReader bufRdr = null;
        bufRdr = new BufferedReader(new FileReader(file));
        String line = null;

        int row = sy;
        int col = 0;

        //read each line of text file

        while ((line = bufRdr.readLine()) != null && row >0) {
            StringTokenizer st = new StringTokenizer(line, " ");
            while (st.hasMoreTokens()) {
                //get next token and store it in the array
                try {
                    setThreshold(col, row, type, Float.parseFloat(st.nextToken().trim()));
                    col++;
                } catch (Exception nfe) {
                    log.warning("Exception " + nfe.getMessage() + " at line(row)=" + row + " col=" + col);
                }
            }
            col = 0;
            row--;
        }
        bufRdr.close();
    }

    public int length() {
        return ntot;
    }

    public int getSizeX() {
        return sx;
    }

    public int getSizeY() {
        return sy;
    }

    public int getNumTypes() {
        return ntypes;
    }

}
