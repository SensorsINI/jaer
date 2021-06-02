/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter
 */
public class NetworkArray<NetType extends Network> {
    
    
    public Network[] N;
    int numNets=4;
    Class C;            // Class of
    Remapper R;
    
    /** Retrieve one of the networks */
    Network getNet(int index)
    {   return N[index];        
    }
    
    /** Reset all networks */
    public void reset()
    {   for (Network n:N)
            n.reset();        
    }
    
    /** Define the Remapper object used to map event addresses to network input addresses */
    public void setRemapper(Remapper arr){
        R=arr;
        
        //
        for (Network n:N)
            if (n!=null)
                n.R=arr;
    }
    
    
    public void setTrackHistory(int th)
    {   for (Network n:N)
            n.trackHistory=th;        
    }
    
    public void setOutputIX(int[] outs)
    {   for (Network n:N)
            n.setOutputIX(outs);        
    }
    
    NetworkArray(Class C,int number) throws Exception
    {   // Maybe this is a little strange, but the arguments going into this 
        // constructor are the network class 
        try {
            if (!Network.class.isAssignableFrom(C))
                throw new Exception("The class '"+C.getSimpleName()+"is not a subclass of Network");
            
            N=new Network[number];
            for (int i=0; i<N.length; i++)
                N[i]=(Network)C.newInstance();
                
        } catch (InstantiationException ex) {
            Logger.getLogger(NetworkArray.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(NetworkArray.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(NetworkArray.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(NetworkArray.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(NetworkArray.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(NetworkArray.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void loadFromFile() throws FileNotFoundException, Exception {loadFromFile(null);}
    public void loadFromFile(File fileOrStartDir) throws FileNotFoundException, Exception
    {   // Note: You need to have loaded a remapper already for this to work.
        if (fileOrStartDir==null || !fileOrStartDir.isFile())
            fileOrStartDir=Network.getfile(fileOrStartDir);
        
        if (N.length==0) return;
                
        N[0].readfile(fileOrStartDir);
        
        R=N[0].R; // Copy Remapper reference
        
        for (int i=1; i<N.length; i++)
        {   N[i]=N[0].copy();
        }
        
    }
    
}
