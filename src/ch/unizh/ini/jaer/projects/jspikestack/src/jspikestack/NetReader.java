/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;



/**
 *
 * @author oconnorp
 */
public class NetReader implements SpikeStack.NetworkReader {

    @Override
    public void readFromXML(SpikeStack net)
    {   readFromXML(net,null);
    }
        
    /* Read in a network from XML */
    public static void readFromXML(SpikeStack net, File file) {

        EasyXMLReader netRead = new EasyXMLReader(file);

        //NodeList layList = netRead.get("Layer");
        
        //int nLayers=layList.getLength();
        int nLayers=netRead.getNodeCount("Layer");

        // Build the skeleton
        for (int i = 0; i < nLayers; i++) {
            net.addLayer(i);
            net.lay(i).initializeUnits(netRead.getNode("Layer",i).getInt("nUnits"));
//            net.lay(i).units=new SpikerLayer.Unit[netRead.getNode("Layer",i).getInt("nUnits")];
            
        }

        for (int i = 0; i < nLayers; i++) {

            EasyXMLReader layRead=netRead.getNode("Layer",i);
            
            net.lay(i).Lin=new ArrayList();
            for (int j=0; j<nLayers; j++)
            {   Integer layjtarg=netRead.getNode("Layer",j).getInt("targ");
                if (layjtarg!=null && layjtarg==i)
                    net.lay(i).Lin.add(net.lay(j));                
            }
            
            Integer outTarg=layRead.getInt("targ");
            if (outTarg!=null)
                net.lay(i).Lout=net.lay(layRead.getInt("targ"));

            float[] thresholds=layRead.getBase64FloatArr("thresh");
            float[] weights=layRead.getBase64FloatArr("W");

            for (int j=0; j<net.lay(i).units.length; j++)
            {   int nOuts=net.lay(i).Lout==null?0:net.lay(i).Lout.units.length;
//                net.lay(i).units[j] = net.lay(i).new Unit(j);
                net.lay(i).units[j] = net.lay(i).makeNewUnit(j);
                if (nOuts>0)
                    net.lay(i).units[j].setWout(Arrays.copyOfRange(weights,j*nOuts,(j+1)*nOuts));
                net.lay(i).units[j].thresh=(thresholds.length==1)?thresholds[0]:thresholds[j];                    
            }

        }

    }
}
