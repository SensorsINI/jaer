/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;



/**
 *
 * @author oconnorp
 */
public class AERFile {
    
    String startPath=getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../files/aerdata/";
    
    ArrayList<Event> events;
    
    Scanner sc;
    
    File file;
    
    protected MappedByteBuffer byteBuffer = null;
    
    
    
    public static ArrayList<BinaryTransEvent> getRetinaEvents(String filename)
    {
        return retina2spikes(getEvents(filename));
    }
    
    public static ArrayList<Spike> getCochleaEvents(String filename)
    {
        return cochlea2spikes(getEvents(filename));
    }
    
    public static ArrayList<Event> getEvents(String filename)
    {
        AERFile aef=new AERFile();        
        aef.read(filename);
        if (!aef.hasValidFile())
            return null;    
        else 
            return aef.events;
    }
    
    
    public static void resampleSpikes(ArrayList<? extends Spike> spikes, int oldDimx, int oldDimy, int newDimx, int newDimy)
    {
        for(Spike s:spikes)
        {
            int x=s.addr/oldDimy;
            int y=s.addr%oldDimy;
            
            int newx=(x*newDimx)/oldDimx;
            int newy=(y*newDimy)/oldDimy;
            
            s.addr=newy+newDimy*newx;            
        }
           
    }
    
    
    public class Event
    {
        long timestamp;
        int addr;
        
    }
    
    public boolean hasValidFile()
    {
        return ((file!=null)&&file.isFile());
    }
    
    public void read(){
        read(null);
    }
    
    /* Read an aer file, build an array of events from it.*/
    public void read(String url){
        
//        JFrame frm=null;
        
        try {
            
            if (url==null)
                url=startPath;
                        
            File startDir = new File(url);                        
            if (startDir.isFile())
                file=startDir;
            else if (new File(startPath+url).isFile())
                file=new File(startPath+url);
            else
                file=getfile(startDir);
            
            //FileInputStream fis=new FileInputStream(file);
            
            if (file==null)
                return;
            
            
//            
//            frm = new JFrame();
//            JOptionPane.showMessageDialog(null ,"Please Wait... Reading file");
//            frm.setVisible(true);
            
            sc=new Scanner(file,"ISO-8859-1");
            
            
            
            
            int loc=sc.nextLine().length();
            while (sc.hasNext("#"))
            {   loc+=sc.nextLine().length();
            }
//            sc.findWithinHorizon("\\s",1000);
            sc.close();
            events=new ArrayList<Event>();
            try {
                byte[] b=getBytesFromFile(file);
                                
                int k=loc+10;
                
                ByteBuffer bb = ByteBuffer.wrap(b);
                bb.order(ByteOrder.BIG_ENDIAN);
                
//                
//                IntBuffer ib = bb.asIntBuffer();
                
                for(int i=0; i<k; i++)
                    bb.get();
                
                while (bb.hasRemaining())
                {   
                    Event ev=new Event();
                    ev.addr=bb.getInt();
                    ev.timestamp=bb.getInt();
                    events.add(ev);
                }
                
                
            } catch (IOException ex) {
                Logger.getLogger(AERFile.class.getName()).log(Level.SEVERE, null, ex);
            }
                
            
//            
//            FileInputStream fis=new FileInputStream(file);
//            DataInputStream dis=new DataInputStream(fis);
//            
//            
//            try {
//                // Look for start of events!
//                dis.skip(loc+10);
//                                
//                
//                
//                // Now read the events
//                events=new ArrayList<Event>();
//                while (dis.available()>0)
//                {   Event ev=new Event();
//                    ev.addr=dis.readInt();
//                    ev.timestamp=dis.readInt();
//                    events.add(ev);
//                }
//                
//                dis.close();
//                
//            } catch (IOException ex) {
//                Logger.getLogger(AERFile.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            
//            
//            
        } catch (FileNotFoundException ex) {
            events=new ArrayList<Event>();
        }
        
//        if (frm!=null)
//            frm.dispose();
                
    }
    
    
    public static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        // Get the size of the file
        long length = file.length();

        // You cannot create an array using a long type.
        // It needs to be an int type.
        // Before converting to an int type, check
        // to ensure that file is not larger than Integer.MAX_VALUE.
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
            && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }

        // Close the input stream and return bytes
        is.close();
        return bytes;
    }
    
    
    
    /** Convert AEViewer file events to JspikeStack Events */
    public static ArrayList<Spike> cochlea2spikes(ArrayList<AERFile.Event> events)
    {   
        ArrayList<Spike> evts =new ArrayList<Spike>();
        
        for (int i=0; i<events.size(); i++)
        {   AERFile.Event ev=events.get(i);        
            if (ev.addr>255) continue;        
            Spike enew=new Spike((int)(ev.timestamp-events.get(0).timestamp), ev.addr/4, 0);            
            evts.add(enew);
        }
        return evts;
    }
    
    /** Convert AEViewer file events to JspikeStack Events */
    public static ArrayList<BinaryTransEvent> retina2spikes(ArrayList<AERFile.Event> events)
    {   
        ArrayList<BinaryTransEvent> evts =new ArrayList();
        
        for (int i=0; i<events.size(); i++)
        {   AERFile.Event ev=events.get(i); 
        
            int addr=ev.addr>>1;
            int x=127-addr%128;
            int y=127-addr/128;
            addr=y+x*128;
            
            BinaryTransEvent enew=new BinaryTransEvent((int)(ev.timestamp-events.get(0).timestamp), addr, 0,ev.addr%2==1);            
            evts.add(enew);
        }
        return evts;
    }
    
    public static void filterPolarity(ArrayList<BinaryTransEvent> arr,boolean polarity)
    {
        ArrayList<BinaryTransEvent> all=(ArrayList<BinaryTransEvent>) arr.clone();
        
        arr.clear();
        
        for(BinaryTransEvent ev:all)
            if(ev.trans==polarity)
                arr.add(ev);
        
    }
    
    
    // ===== File IO Functions =====    
    static class FileChoice implements Runnable {

        File file;
        File startDir=new File(getClass().getClassLoader().getResource(".").getPath().replaceAll("%20", " ")+"../../files/");
    

        @Override
        public void run() {
            JFileChooser fc;
            fc = new JFileChooser(startDir);
            fc.setDialogTitle("Chose an AER data file");

            fc.showOpenDialog(null);
            file = fc.getSelectedFile();            
        }
    }

    static public File getfile(File startDir) throws FileNotFoundException {

        FileChoice fc = new FileChoice();
        if (startDir!=null && startDir.isDirectory())
            fc.startDir=startDir;
        else if (startDir!=null && startDir.isFile())
            return startDir;
        
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

    
    