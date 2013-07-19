/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;


import jspikestack.NetController;
import jspikestack.Network;
import jspikestack.UnitLIF;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author Peter
 */
public class ISIspikeFilter extends SpikeFilter {

    int nOutputs=100;
    
    ISIMapper isiMap;
    
    public ISIspikeFilter(AEChip chip)
    {
        super(chip);
        
        ISIMapper isi=new ISIMapper(64,nOutputs);
        isi.setMinFreqHz(350);
        isi.setMaxFreqHz(1200);
//        isi.setMaxFreqHz(2000);
        
        isiMap=isi;
        
        this.axonType=NetController.AxonTypes.STATIC;
        
        
        
        
        
        
    }
    
    @Override
    public NetMapper makeMapper(Network net) {
        
        
        return isiMap;
    }

    @Override
    public void customizeNet(Network net) {
        
        Network.Initializer ini=new Network.Initializer();
        
        ini.lay(0).nUnits=nOutputs;
        
//        ini.ax(0,0).wMean=-2;
//        ini.ax(0,0).wStd=0;
        
        
        net.buildFromInitializer(ini);
        
                
        UnitLIF.Globals uG=(UnitLIF.Globals)unitGlobs;
        uG.useGlobalThresh=true;
        uG.thresh=5;
        uG.tau=50000;  
        
        net.lay(0).fireInputsTo=true;
        net.lay(0).inputCurrentStrength=.1f;
    }
    
    
    

    @Override
    public String[] getInputNames() {
        return new String[] {"Cochlea"};
    }
    
    //  Getters/setters
    
    public float getMinFreqHz() {
        return isiMap.getMinFreqHz();
    }

    public void setMinFreqHz(float minFreqHz) {
        isiMap.setMinFreqHz(minFreqHz);
    }

    public float getMaxFreqHz() {
        return isiMap.getMaxFreqHz();
    }

    public void setMaxFreqHz(float maxFreqHz) {
        isiMap.setMaxFreqHz(maxFreqHz);
    }
    
    boolean record=false;
    public boolean getRecord()
    {
        return record;
    }
    
    
//    public float getMinFreqHz() {
//        return isiMap.getMinFreqHz();
//    }
//
//    public void setMinFreqHz(float minFreqHz) {
//        isiMap.setMinFreqHz(minFreqHz);
//    }
//    
    
}
