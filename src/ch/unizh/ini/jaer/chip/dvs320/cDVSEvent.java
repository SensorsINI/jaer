/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import net.sf.jaer.event.TypedEvent;

/**
 * An event from the cDVS chip, which includes DVS temporal contrast events, color change events, and log intensity analog value events.
 *
 * @author Tobi
 */
public class cDVSEvent extends TypedEvent {


    public byte polarity=0;
    
    public enum EventType {

        None(-1), Brighter(0), Darker(1), Redder(2), Bluer(3);
        private final byte type;

        EventType(int type) {
            this.type = (byte) type;
        }

        public int type(){ return type;}

    };

    public EventType eventType=EventType.None;

    public boolean isLogIntensityChangeEvent() {
        return eventType.type<2;
    }

    public boolean isColorChangeEvent() {
        return eventType.type>1;
    }

    @Override
    public int getType() {
        return eventType.type;
    }

    @Override
    public int getNumCellTypes() {
        return 4;
    }
}
