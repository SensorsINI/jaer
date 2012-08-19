/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
    /** Input current */
public final class InputEvent extends PSP
{
    final int ixLayer; 
    int ix_Unit; 
    int time;

    public InputEvent(Spike sp,int ix_Layer)
    {
        super(sp);
        ixLayer=ix_Layer;
    }

    @Override
    public int getHitTime() {
        return sp.time;
    }

}