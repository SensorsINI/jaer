/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.thresholdlearner;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;

/**
 * A 2d+ map of pixel thresholds. In the case of the temporal contrast DVS silicon retina, this map holds the learned pixel temporal contrast thresholds.
 * This map is then used to render the retina output taking into account the various event thresholds.
 * 
 * @author tobi
 */
final public class ThresholdMap implements Observer {

    AEChip chip;
    float[] map;
    int sx, sy, ntypes, ntot;

    public ThresholdMap(AEChip chip) {
        this.chip = chip;
        chip.addObserver(this);
        allocateMap();
    }

    synchronized final void allocateMap() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        ntypes = chip.getNumCellTypes();
        ntot = sx * sy * ntypes;
        if (ntot == 0) {
            return;
        }
        if (map == null || map.length != ntot) {
            map = new float[ntot];
        }
        reset();
    }
    
    public void reset(){
        Arrays.fill(map, 1);
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
    
    public final float getThreshold(int x, int y, int type){
        return map[index(x,y,type)];
    }
    
    public final void setThreshold(int x, int y, int type, float value){
        map[index(x,y,type)]=value;
    }
    
    private int index(int x, int y, int type){
        return type+x*ntypes+y*ntypes*sx;
    }
    
    public void save(){
        
    }
    
    public void load(){
        
    }
}
