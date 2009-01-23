/*
 * ChipDataFilePreview.java
 *
 * Created on December 31, 2005, 5:10 PM
 */

package net.sf.jaer.graphics;

import net.sf.jaer.aemonitor.*;
import net.sf.jaer.chip.*;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.*;
import net.sf.jaer.eventio.*;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.EngineeringFormat;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.util.logging.*;
import javax.swing.*;

/**
 * Provides preview of recorded AE data selectedFile
 *
 *
 * @author tobi
 */
public class ChipDataFilePreview extends JPanel implements PropertyChangeListener{
    JFileChooser chooser;
    EventExtractor2D extractor;
    int oldScale=4;
    ChipCanvas canvas;
    AEChipRenderer renderer;
    JLabel infoLabel;
    AEChip chip;
    volatile boolean indexFileEnabled=false;
    Logger log=Logger.getLogger("AEViewer");
    private int packetTime=40000;
    
    /**
     * Creates new form ChipDataFilePreview
     */
    public ChipDataFilePreview(JFileChooser jfc, AEChip chip) {
        canvas=new RetinaCanvas(chip);
        setLayout(new BorderLayout());
        this.chooser=jfc;
        extractor=chip.getEventExtractor();
        renderer=chip.getRenderer();
        canvas.setScale(2);
        add(canvas.getCanvas(),BorderLayout.CENTER);
        canvas.getCanvas().addKeyListener(new KeyAdapter(){
            public void keyReleased(KeyEvent e) {
                switch(e.getKeyCode()){
                    case KeyEvent.VK_S:
                        packetTime/=2;
                        break;
                    case KeyEvent.VK_F:
                        packetTime*=2;
                        break;
                }
            }
        });
    }
    
    File selectedFile, currentFile;
    boolean isIndexFile(File f){
        if(f==null) return false;
        if(f.getName().endsWith(".index")) return true; else return false;
    }
    
    public void propertyChange(PropertyChangeEvent evt) {
//            System.out.println(evt);
        selectedFile=chooser.getSelectedFile();
//        System.out.println("evt.getPropertyName()="+evt.getPropertyName());
//        System.out.println("evt.getNewValue()="+evt.getNewValue());
        if(evt.getPropertyName()=="SelectedFileChangedProperty"){
            showFile(selectedFile); // starts showing selectedFile
        }
    }
    AEFileInputStream ais;
    
    volatile boolean deleteIt=false;
    
    public void deleteCurrentFile(){
        if(indexFileEnabled){
            log.warning("won't try to delete this index file");
            return;
        }
        deleteIt=true;
    }
    
    public void renameCurrentFile(){
        log.warning("renaming not implemented yet for "+currentFile);
        
    }
    
    volatile boolean stop=false;
    
// gets called on property change, possibly with null file
    public void showFile(File file) {
        try {
//            if(fis!=null){ System.out.println("closing "+fis); fis.close();}
            if(file==null) {stop=true; return;}
//            fis=new FileInputStream(file);
            currentFile=file;
            indexFileEnabled=isIndexFile(file);
            if(!indexFileEnabled){
                if(ais!=null) {
//                    System.out.println("closing "+ais);
                    ais.close();
                    ais=null;
                    System.gc(); // try to make memory mapped file GC'ed so that user can delete it
                    System.runFinalization();
                    // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154
                    // http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038
                }
                ais=new AEFileInputStream(new FileInputStream(file));
                try{ ais.rewind();}catch(IOException e){};
                fileSizeString=fmt.format(ais.size())+" events "+fmt.format(ais.getDurationUs()/1e6f)+" s";
            }else{
                indexFileString=getIndexFileCount(file);
            }
//        infoLabel.setText(fmt.format((int)ais.size()));
            stop=false;
            repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    File indexFile=null;
    AEPacketRaw aeRaw;
    EventPacket ae;
    
    public void paint(Graphics g){
        if(stop || deleteIt) {
//            System.out.println("stop="+stop+" deleteIt="+deleteIt);
            try{
                if(ais!=null){
//                    System.out.println("closing "+ais);
                    ais.close();
                    ais=null;
                    System.gc();
                }
                if(deleteIt){
                    deleteIt=false;
                    if(currentFile!=null && currentFile.isFile()){
                        boolean deleted=currentFile.delete();
                        if(deleted){
                            log.info("succesffully deleted "+currentFile);
                            chooser.rescanCurrentDirectory();
                        }else{
                            log.warning("couldn't delete file "+currentFile);
                        }
                    }
                }
            }catch(IOException e){}
            return;
        }
        Graphics2D g2=(Graphics2D)canvas.getCanvas().getGraphics();
//        g2.setColor(Color.black);
//        g2.fillRect(0,0,getWidth(),getHeight()); // rendering method already paints frame black, shouldn't do it here or we get flicker from black to image
       if(!indexFileEnabled){
            if(ais!=null){
                try{
                    aeRaw=ais.readPacketByTime(packetTime);
                }catch(EOFException e){
                    try{
                        ais.rewind();
                    }catch(IOException ioe){
                        System.err.println("IOException on rewind from EOF: "+ioe.getMessage());
                        ioe.printStackTrace();
                    }
                }catch(IOException e){
                    e.printStackTrace();
                    try{
                        ais.close();
                        if(ais!=null){
                            try {
                                ais.close();
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    }catch(Exception e3){
                        e3.printStackTrace();
                    }
                }
                if(aeRaw!=null) ae=extractor.extractPacket(aeRaw);
            }
            if(ae!=null){
                renderer.render(ae);
                canvas.paintFrame();
            }
        }else{
            fileSizeString=indexFileString;
        }
         g2.setColor(Color.red);
        g2.setFont(g2.getFont().deriveFont(20f));
        g2.drawString(fileSizeString,30f,30f);
//        infoLabel.repaint();
        
        try{
            Thread.currentThread().sleep(15);
        }catch(InterruptedException e){}
        repaint(); // recurse
        
        
        
    }
    
    
    EngineeringFormat fmt=new EngineeringFormat();
    volatile String fileSizeString="";
    volatile String indexFileString="";
    
    String getIndexFileCount(File file){
        try{
            BufferedReader r=new BufferedReader(new FileReader(file));
            int numFiles=0;
            while(r.readLine()!=null) numFiles++;
            return numFiles+" files";
        }catch(Exception e){
            return "";
        }
    }
    
    
}
