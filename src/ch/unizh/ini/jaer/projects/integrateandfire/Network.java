/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import net.sf.jaer.event.BasicEvent;

/**
 * // This exists to provide a common interface for all all networks containing 
 * the following features:
 * 1) An indexed set of units
 * 2) Some kind of viewable signal from those units.
 * 
 * @author Peter
 */
public abstract class Network {
    
    Remapper R; // Maps incoming events to destinations
    
    static final Logger log = Logger.getLogger("Network");
    int[] outputIX={};
    float[] windex={};
    int trackHistory=0; // History tracking in winner return
    
    
    
    public void setOutputIX(int[] outs)
    {   outputIX=outs;   
        windex=new float[outs.length];
    }
        
    /* Copy the parameters (copying of units will be left to implementing class);
     * Notes: Remapper R is copied by referance, rather than being cloned
     * 
     */
    public void copyParamsInto(Network newNet)
    {   newNet.R=R; // Remapper is just linked, not copied!!!
        newNet.outputIX=outputIX.clone();
        newNet.windex=new float[outputIX.length];
        newNet.trackHistory=trackHistory;
    }
    
    public int getWinningIndex()
    {   /* Returns the index of the Unit with the highest activation signal 
         * (as determined by getAsig)
         * Special cases:
         * No output elements specified: returns -1
         * Tie:  returns -2
         * 
        */
        
        float keep=0, thro=1;
        
        if (trackHistory>0)
        {   keep=(1-1f/(trackHistory));
            thro=1-keep;
        }
        
        float max=Float.NEGATIVE_INFINITY;
        int maxix=-1;
        for (int i=0; i<outputIX.length;i++)
        {   float curr;
                        
            curr=U[outputIX[i]].getAsig()*thro+windex[i]*keep;
            windex[i]=curr;
            
            if (curr>max)
            {   max=curr;
                maxix=outputIX[i];
            }
            else if (curr==max)
            {   maxix=-2;                
            }
        }
        return maxix;
    }
    
    public String getWinningTag(){
        int ix=getWinningIndex();
        if (ix==-1)
            return "(no outputs)";
        else if (ix==-2) return "(tie)";
        else return U[ix].getName();
    }
    
    public interface Unit
    {   // Standardizes methods for viewing different "neurons".  
        // Neuron implementations of subclasses should implement this class
        
        // Membrane-Voltage signal of neuron at some index.
        public float getVsig(int timestamp);    

        // Activation singal of neuron at some index
        public float getAsig();

        // Name of unit
        public String getName();
        
        // Return basic info on state of unit
        public String getInfo();
        
    }
    Unit[] U; // Link to list of units.  IMPORTANT, however you implement your array of units, make sure this property links to them
    
    
    // ===== Actual functions =====
    public int eventIndex(BasicEvent ev)
    {   return R.xy2ind(ev.x, ev.y);        
    }
    
    public void setRemapper(Remapper Rnew)
    {   R=Rnew;        
    }
    
    public Remapper getRemapper()
    {   return R;
    }
//    abstract public void eatEvent(BasicEvent ev);
//    {   int ix=eventIndex(ev);
//        propagate(ix,0,ev.timestamp);
//    }
    
//    abstract public void eatEvent(BasicEvent ev,OutputEventIterator outItr);
//    {   int ix=eventIndex(ev);
//        propagate(ix,0,ev.timestamp,outItr);
//    }    
    
    // ===== Network Activity Functions =====

    abstract public void reset();
    
    // ===== Network Observation Functions =====
    public Unit getUnit(int index){return U[index];}
    
    public int nUnits(){return U.length;}
    
    // Return fanout connections
    abstract public int[] getConnections(int index);

    // Return fanout weigths
    abstract public float[] getWeights(int index);
    
    // Copy network
    abstract public Network copy();
    
    // ===== File IO Functions =====    
    static class FileChoice implements Runnable {

        File file;
        File startDir;

        @Override
        public void run() {
            /*
            try {
                URL classpath=new URL(getClass().getClassLoader().getResource(".").getPath());
                classpath.
            } catch (MalformedURLException ex) {
                Logger.getLogger(Network.class.getName()).log(Level.SEVERE, null, ex);
            }*/
            //File initloc=new File(getClass().getClassLoader().getResource(".").getPath());
            
            //JFileChooser fc = new JFileChooser();
            JFileChooser fc;
                //fc = new JFileChooser(initloc.getAbsolutePath());
                fc = new JFileChooser(startDir);
                
//                javax.swing.filechooser.FileFilter filt=fc.getFileFilter();
                
                //fc.setCurrentDirectory(new File(initloc.getAbsolutePath()));
                fc.setDialogTitle("Choose network weight XML file (JAER/filterSettings/NeuralNets)");
                        
                fc.showOpenDialog(null);
                file = fc.getSelectedFile();
           
            //fc.setSelectedFile(file);
            //fc.setCurrentDirectory(initloc);
            
            //FileSystemView.getFileSystemView().getRoots()[0];
              
            
        }
    }

    
    
    static public File getfile(File startDir) throws FileNotFoundException {

        FileChoice fc = new FileChoice();
        
        if (startDir!=null && startDir.isDirectory())
            fc.startDir=startDir;
        
        if (SwingUtilities.isEventDispatchThread()) {
            fc.run();
            
            
        } else {
            try {
                SwingUtilities.invokeAndWait(fc);
            } catch (Exception ex) {
                log.warning(ex.toString());
                return null;
            }
            
        }
        return fc.file;
    }

    abstract public void readfile(File file) throws FileNotFoundException, Exception;
    
}
