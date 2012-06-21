/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;



/**
 *
 * @author oconnorp
 */
public class AERFile {
    
    String startPath="/users/oconnorp/Desktop/Dropbox/MNIST/Cochlea";
    
    ArrayList<Event> events;
    
    Scanner sc;
    
    protected MappedByteBuffer byteBuffer = null;
    
    public class Event
    {
        long timestamp;
        int addr;
        
    }
    
    
    /* Read an aer file, build an array of events from it.*/
    public void read(){
        try {
            
            File startDir = new File(startPath);
            File file=getfile(startDir);
            
            //FileInputStream fis=new FileInputStream(file);
            
            sc=new Scanner(file,"ISO-8859-1");
            
            
            
            
            int loc=sc.nextLine().length();
            while (sc.hasNext("#"))
            {   loc+=sc.nextLine().length();
            }
//            sc.findWithinHorizon("\\s",1000);
            sc.close();
            
            DataInputStream dis=new DataInputStream(new FileInputStream(file));
            
            
            try {
                // Look for start of events!
                dis.skip(loc+10);
                                
                // Now read the events
                events=new ArrayList<Event>();
                while (dis.available()>0)
                {   Event ev=new Event();
                    ev.addr=dis.readInt();
                    ev.timestamp=dis.readInt();
                    events.add(ev);
                }
                
                dis.close();
                
            } catch (IOException ex) {
                Logger.getLogger(AERFile.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
            
        } catch (FileNotFoundException ex) {
            events=new ArrayList<Event>();
        }
                
    }
    
    
    
    
    // ===== File IO Functions =====    
    static class FileChoice implements Runnable {

        File file;
        File startDir;

        @Override
        public void run() {
            JFileChooser fc;
            fc = new JFileChooser(startDir);
            fc.setDialogTitle("Choose network weight XML file (JAER/filterSettings/NeuralNets)");

            fc.showOpenDialog(null);
            file = fc.getSelectedFile();            
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
                System.out.println(ex.toString());
                return null;
            }
        }
        return fc.file;
    }
    
}

    
    