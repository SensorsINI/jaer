package net.sf.jaer.graphics;
import javax.swing.SpinnerNumberModel;

/** Spinner model with octave increments/decrements.
 *
 * @author tobi
 */
public class OctaveSpinnerNumberModel extends SpinnerNumberModel{
    public OctaveSpinnerNumberModel (int value,int minimum,int maximum,int stepSize){
        super(value,minimum,maximum,stepSize);
    }

    @Override
    public Object getNextValue (){
        return 2 * (Integer)getValue();
    }

    @Override
    public Object getPreviousValue (){
        return (Integer)getValue() / 2;
    }
}
