package ch.unizh.ini.jaer.chip.cochlea;
/** Created Oct 2008 by tobi */
import net.sf.jaer.event.TypedEvent;


/** The events that a generic binarual cochlea returns. 
 * 
 * @author tobi
 */
public class BinauralCochleaEvent extends TypedEvent{    
    public BinauralCochleaEvent(){
        super();
    }
    
    public enum Ear {RIGHT, LEFT};
 
    /** Overrides getNumCellTypes to be 2, one for each ear. 0=right ear, 1=left ear.
     @return 2
     */
    @Override
    public int getNumCellTypes() {
        return 2;
    }
    
    public Ear getEar(){
        if((type&2)==0) return Ear.RIGHT; else return Ear.LEFT;
    }
    
}