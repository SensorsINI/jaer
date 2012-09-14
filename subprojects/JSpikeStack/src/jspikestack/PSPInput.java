/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

/**
 *
 * @author Peter
 */
public final class PSPInput extends PSP {
    
    final int targetLayer;
    final int targetUnit;
    
    public PSPInput(int time,int unitIX, int ixLayer)
    {   this(time,unitIX,ixLayer,1);
    }
    
    
    public PSPInput(int time,int unitIX, int ixLayer,int special)
    {   super(new Spike(time,unitIX,ixLayer,special),0);
        targetLayer=ixLayer;
        targetUnit=unitIX;
    }

    @Override
    public void affect(Network net) {
        
        Layer lay=net.lay(targetLayer);
        
        if (lay.fireInputsTo)
           lay.fireTo(this, targetUnit, lay.inputCurrentStrength);
        else
            lay.fireFrom(targetUnit,sp.act);
            
    }
    
    
}
