package ch.unizh.ini.jaer.chip.cochlea;
/** Created Oct 2008 by tobi */


/** The events that CochleaAMS1b returns. These have a channel (0-63), an ear (left/right), a filter type (LPF, BPF), and a threshold (0-3).
 * Each channel of one ear has 4 LPF cells and 4 BPF cells. Includes methods to find event type from internal TypedEvent type.
 * 
 * @author tobi
 */
public class CochleaAMSEvent extends BinauralCochleaEvent{    
    public CochleaAMSEvent(){
        super();
    }

    /** These chips have different types of ganglion cells, some are lowpass (LPF) and others bandpass (BPF).*/
    public enum FilterType {LPF, BPF};

    /** Overrides getNumCellTypes to be 16 (2 for left/right ear * 2 for lowpass/bandpass filter * 4 cells of each type).
     @return 16
     */
    @Override
    public int getNumCellTypes() {
        return 16;
    }

    /** Returns binaural ear.
     *
     * @return ear of the event.
     */
    @Override
    public Ear getEar(){
        if((type&4)==0) return Ear.LEFT; else return Ear.RIGHT;
    }

    /** Returns ganglion cell type.
     *
     * @return ganglion cell type.
     */
    public FilterType getFilterType(){
        if((type&8)==0) return FilterType.LPF; else return FilterType.BPF;
    }

    /** Returns ganglion cell threshold.
     *
     * @return ganglion cell threshold level - arbitrary meaning that depends on cochlea biasing and construction.
     */
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