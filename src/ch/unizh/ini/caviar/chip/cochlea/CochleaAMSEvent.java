package ch.unizh.ini.caviar.chip.cochlea;
/** Created Oct 2008 by tobi */
import ch.unizh.ini.caviar.event.TypedEvent;


/** The events that CochleaAMS1b returns. These have a channel (0-63), an ear (left/right), a filter type (LPF, BPF), and a threshold (0-3).
 * Each channel of one ear has 4 LPF cells and 4 BPF cells.
 * 
 * @author tobi
 */
public class CochleaAMSEvent extends TypedEvent{    
    public CochleaAMSEvent(){
        super();
    }
    
    enum Ear {RIGHT, LEFT};
    enum FilterType {LPF, BPF};
    enum Threshold {Thr0,Thr1,Thr2,Thr3};
 
    /** Overrides getNumCellTypes to be 16 (2 for left/right ear * 2 for lowpass/bandpass filter * 4 cells of each type).
     @return 16
     */
    @Override
    public int getNumCellTypes() {
        return 16;
    }
    
    public Ear getEar(){
        if(type%8==0) return Ear.RIGHT; else return Ear.LEFT;
    }
    
    public FilterType getFilterType(){
        if(type%4==0) return FilterType.LPF; else return FilterType.BPF;
    }
    
    public Threshold getThreshold(){
        int t=type%4;
        switch(t){
            case 0: return Threshold.Thr0;
            case 1: return Threshold.Thr1;
            case 2: return Threshold.Thr2;
            case 3: return Threshold.Thr3;
            default: throw new RuntimeException("type "+type+" does not have a defined Threshold");
        }
    }
}