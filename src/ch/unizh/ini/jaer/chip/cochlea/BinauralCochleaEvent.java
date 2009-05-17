package ch.unizh.ini.jaer.chip.cochlea;
/** Created Oct 2008 by tobi */
import net.sf.jaer.event.TypedEvent;


/** The events that a generic binaurual cochlea returns.
 * 
 * @author tobi
 */
public class BinauralCochleaEvent extends TypedEvent{    
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

    /** Which ear does the event come from.
     *
     * @return ear
     */
    public Ear getEar(){
        // TODO: Shouldn't this be ((type&1)==0) ???
        if((type&2)==0) return Ear.RIGHT; else return Ear.LEFT;
    }
    
}