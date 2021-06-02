package ch.unizh.ini.jaer.chip.cochlea;
/**
 * Events from the CochleaAERb chip have this type.
 */
public class CochleaAERbEvent extends BinauralCochleaEvent{
    /** This chip uses bit 0 for the binaural ear. */
    @Override
    public Ear getEar (){
        if ( ( type & 1 ) == 0 ){
            return Ear.RIGHT;
        } else{
            return Ear.LEFT;
        }
    }
}
