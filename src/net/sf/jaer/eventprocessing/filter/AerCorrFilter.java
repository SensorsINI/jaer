/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import java.util.Observable;
import java.util.Observer;
import java.util.Arrays;
import java.util.Random;


@Description("Filters out uncorrelated background activity")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class AerCorrFilter extends EventFilter2D implements Observer {

    final int MAX_Ileak = 1000000, MIN_Ileak = 1;
    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;
    
  
    private int Ileak = getInt("Ileak", 100);

    private int subsampleBy = getInt("subsampleBy", 0);

    private double[][] Vcap;
    private double Vth = 1.2;
    int[][] lastTimesMap;
    private double[][] Cap;
    private double[][] Vrs;
    private double [][] IleakReal;
    private int ts = 0; // used to reset filter
    private int sx; 
    private int sy;
    private Random r;
    
    public AerCorrFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        setPropertyTooltip("Ileak", "Set Leaking current for variable dT");
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
   }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (lastTimesMap == null) allocateMaps(chip);

 
        for (Object eIn : in) {
            if(eIn == null) break;  
            BasicEvent e = (BasicEvent) eIn;
            if (e.special) continue;
                        
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            if (x < 0 || x > sx || y < 0 || y > sy) continue;
            
            ts = e.timestamp;
            int lastT = lastTimesMap[x][y];
            int deltaT = (ts - lastT);
            Vcap[x][y] += - IleakReal[x][y]/Cap[x][y] * deltaT;
            if (!(Vcap[x][y] > Vth && lastT != DEFAULT_TIMESTAMP)){
                e.setFilteredOut(true);
            }
            Vcap[x][y]= Vrs[x][y];
             
            lastTimesMap[x][y]=ts;
        }

        return in;
    }
    
    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) {
            resetFilter();
        }
    }

    @Override
    public final void initFilter() {
        r = new Random();
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }
    
    private void allocateMaps(AEChip chip) {
        if (chip != null && chip.getNumCells() > 0) {
            lastTimesMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : lastTimesMap) {
                Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
            }
            
            Vcap = new double[chip.getSizeX()][chip.getSizeY()];
            Cap = new double[chip.getSizeX()][chip.getSizeY()];
             // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (double[] arrayRow : Cap) {
                for (int i = 0; i < arrayRow.length; i++) {
                    arrayRow[i] = 165e-15*(r.nextGaussian()*0.03+1);
                }
            }

           /* Vth = new double[chip.getSizeX()][chip.getSizeY()];
            // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (double[] arrayRow : Vth) {
                for (int i = 0; i < arrayRow.length; i++) {
                    arrayRow[i] = 1.2;
                }
            }*/
            
            Vrs = new double[chip.getSizeX()][chip.getSizeY()];
            // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (double[] arrayRow : Vrs) {
                for (int
                        i = 0; i < arrayRow.length; i++) {
                    arrayRow[i] = (r.nextGaussian()*0.005+1.197);
                }
            }
            IleakReal = new double[chip.getSizeX()][chip.getSizeY()];
            // Initialize two dimensional array, by first getting the rows and then setting their values.
            for (double[] arrayRow : IleakReal) {
                for (int
                        i = 0; i < arrayRow.length; i++) {
                    arrayRow[i] = getIleak()*(r.nextGaussian()*0.04+1.004)*1e-12;//should have different sigma and u for different Ileak values
                }
            }
        }
    }

    public Object getFilterState() {
        return lastTimesMap;
    }
    
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
    public int getSubsampleBy() {
        return subsampleBy;
    }

    /** Sets the number of bits to subsample by when storing events into the 
     * map of past events.
     * Increasing this value will increase the number of events that pass 
     * through and will also allow passing events from small sources that 
     * do not stimulate every pixel. 
     * @param subsampleBy the number of bits, 0 means no subsampling, 
     *        1 means cut event time map resolution by a factor of two in x and in y */
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
    }
    // </editor-fold>

    /**
     * @return the Ileak
     */
    public int getIleak() {
        return (int) Ileak;
    }
    
    public int getMinIleak() {
        return (int) MIN_Ileak;
    }
    
     public int getMaxIleak() {
        return (int) MAX_Ileak;
    }

    /**
     * @param Ileak the Ileak to set
     */
    public void setIleak(int Ileak) {
        this.Ileak = Ileak;
    }

  
    
}