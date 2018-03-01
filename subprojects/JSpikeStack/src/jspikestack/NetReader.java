/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 *
 * @author oconnorp
 */
public class NetReader<NetType extends Network> implements Network.NetworkReader {

    File file;  // Keeps track of the last file used.
    NetType net;        
    
    // Default Directory 
    File startDir;
//    File startDir=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../files/nets");
    
    public NetReader(NetType network)
    {   net=network;        
    
        try{
            startDir=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../files/nets");
    
        }   
        catch (Exception e)
        {
            System.out.println("Failed to load default start directory.  That's ok, no big deal.");
        }
        
    }
    
    @Override
    public void readFromXML(Network net)
    {   readFromXML(net,null);
    }
        
    /** Read in a network from XML 
     * TODO: modify this to deal with multiple output layers!
     *      
     */
    public void readFromXML(Network net, File infile) {

        if (infile==null)
            infile=startDir;
        
        EasyXMLReader netRead;
        netRead = new EasyXMLReader(infile);
        
        if (!netRead.hasFile())
        {   System.out.println("No File was selected");
            return;
        }
        
        file=netRead.getFile();
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
            
            
            
//            net.lay(i).Lin=new ArrayList();
//            for (int j=0; j<nLayers; j++)
//            {   Integer layjtarg=netRead.getNode("Layer",j).getInt("targ");
//                if (layjtarg!=null && layjtarg==i)
//                    net.lay(i).Lin.add(net.lay(j));                
//            }
            
            Integer outTarg=layRead.getInt("targ");
            if (outTarg!=null)
                net.addAxon(net.lay(i),net.lay(layRead.getInt("targ")));
//                net.lay(i).Lout=;

            float[] thresholds=layRead.getBase64FloatArr("thresh");
            float[] weights=layRead.getBase64FloatArr("W");

            for (int j=0; j<net.lay(i).units.length; j++)
            {   int nOuts=net.lay(i).nAxons()==0?0:net.lay(i).ax(0).postLayer.nUnits();
                
//                if net.lay(i).nAxons()
//                
//                int nOuts=net.lay(i).ax(0).postLayer.nUnits();
//                net.lay(i).units[j] = net.lay(i).new Unit(j);
//                net.lay(i).units[j] = net.lay(i).makeNewUnit(j);
                if (nOuts>0)
                    net.lay(i).ax(0).setWout(j,Arrays.copyOfRange(weights,j*nOuts,(j+1)*nOuts));
                net.lay(i).units[j].thresh=(thresholds.length==1)?thresholds[0]:thresholds[j];                    
            }

        }
        
//        net.init();

    }

    /** Serialize a network */
    public byte[] serialize()
    {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(net);
            out.close();
            
            
            return bos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(NetReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
    }
            
    /** De-serialize from byte array */
    public static <NetType extends Network> NetType deserialize(byte[] ser)
    {
        
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(ser);
            ObjectInput in = new ObjectInputStream(bis);
            NetType o = (NetType)in.readObject();
                        
            return  o;
            
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(NetReader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(NetReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
        
    }
    
    public NetType copy()
    {   throw new UnsupportedOperationException("Somme systems have problem with this... need to solve.");
//        return this.net;
//
//        Below line mysteriously gives problems on SOME systems.
//        C:\Documents and Settings\Tobi\My Documents\jaer\host\java\subprojects\JSpikeStack\src\jspikestack\NetReader.java:139: type parameters of <NetType>NetType cannot be determined; no unique maximal instance exists for type variable NetType with upper bounds NetType,jspikestack.SpikeStack
//        return deserialize(serialize());

//        return neet;
    }
            
            
            

}
