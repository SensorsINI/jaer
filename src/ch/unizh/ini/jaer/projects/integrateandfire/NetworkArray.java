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
import net.sf.jaer.event.OutputEventIterator;

/**
 *
 * @author Peter
 */
public class NetworkArray {
    
    
    public Network[] N;
    int numNets=4;
    Class C;            // Class of
    
    Network getNet(int index)
    {   return N[index];        
    }
    
    public void reset()
    {   for (Network n:N)
            n.reset();        
    }
    
    public void setRemapper(Remapper R){
        for (Network n:N)
            if (n!=null)
                n.R=R;
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
    
    public void loadFromFile() throws FileNotFoundException, Exception
    {   
        File file=Network.getfile();
        
        for (Network n:N)
        {   n.readfile(file);
        }
    }
    
}
