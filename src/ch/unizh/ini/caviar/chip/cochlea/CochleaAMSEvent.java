package ch.unizh.ini.caviar.chip.cochlea;

import ch.unizh.ini.caviar.event.TypedEvent;


public class CochleaAMSEvent extends TypedEvent{    
    public CochleaAMSEvent(){
        super();
    }
    
    /** Overrides getNumCellTypes to be 4 (2 for left/right ear and 2 for lowpass/bandpass filter)
     @return 4
     */
    @Override
    public int getNumCellTypes() {
        return 4;
    }
    
}