package ch.unizh.ini.jaer.chip.cochlea;
/** Created Oct 2008 by tobi */


/** 
 * Events that a generic binaurual cochlea returns. This abstract class should be subclassed by
 * any chip that uses these events and the getEar method should be overridden to properly extract the
 * Ear from the event.
 * 
 * @author tobi
 */
abstract public class BinauralCochleaEvent extends CochleaEvent {
    public BinauralCochleaEvent(){
        super();
    }

    /** The left or right ear cochlea. */
    public enum Ear {RIGHT, LEFT};
 
    /** Overrides getNumCellTypes to be 2, one for each ear. 0=right ear, 1=left ear.
     @return 2
     */
    @Override
    public int getNumCellTypes() {
        return 2;
    }

    /** Returns which ear the event come from.
     *
     * @return ear
     */
    abstract public Ear getEar();
//    {
//        // TODO: Shouldn't this be ((type&1)==0) ???
//        if((type&2)==0) return Ear.RIGHT; else return Ear.LEFT;
//    }
    
}