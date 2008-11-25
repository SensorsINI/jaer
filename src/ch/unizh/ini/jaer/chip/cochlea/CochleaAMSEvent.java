package ch.unizh.ini.jaer.chip.cochlea;
/** Created Oct 2008 by tobi */


/** The events that CochleaAMS1b returns. These have a channel (0-63), an ear (left/right), a filter type (LPF, BPF), and a threshold (0-3).
 * Each channel of one ear has 4 LPF cells and 4 BPF cells.
 * 
 * @author tobi
 */
public class CochleaAMSEvent extends BinauralCochleaEvent{    
    public CochleaAMSEvent(){
        super();
    }
    
    public enum FilterType {LPF, BPF};
    public byte threshold;
 
    /** Overrides getNumCellTypes to be 16 (2 for left/right ear * 2 for lowpass/bandpass filter * 4 cells of each type).
     @return 16
     */
    @Override
    public int getNumCellTypes() {
        return 16;
    }
    
    public Ear getEar(){
        if((type&4)==0) return Ear.LEFT; else return Ear.RIGHT;
    }
    
    public FilterType getFilterType(){
        if((type%8)==0) return FilterType.LPF; else return FilterType.BPF;
    }
    
    public byte getThreshold(){
        return (byte)(type%4);
//        switch(t){
//            case 0: return Threshold.Thr0;
//            case 1: return Threshold.Thr1;
//            case 2: return Threshold.Thr2;
//            case 3: return Threshold.Thr3;
//            default: throw new RuntimeException("type "+type+" does not have a defined Threshold");
//        }
    }
}