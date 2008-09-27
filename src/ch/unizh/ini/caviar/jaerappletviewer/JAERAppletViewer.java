/*
 * JAERAppletViewer.java
 *
 * Created on September 25, 2008, 10:45 PM
 */
package ch.unizh.ini.caviar.jaerappletviewer;

import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.chip.EventExtractor2D;
import ch.unizh.ini.caviar.chip.retina.Tmpdiff128;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventio.AEFileInputStream;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.graphics.AEChipRenderer;
import ch.unizh.ini.caviar.graphics.ChipCanvas;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.*;
import java.awt.event.KeyAdapter;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.logging.*;
import javax.swing.JLabel;

/**
 * Applet that allows playing events in any browser from a network input stream.
 * 
 * @author  tobi/mert
 */
public class JAERAppletViewer extends javax.swing.JApplet {

    EventExtractor2D extractor;
    int oldScale = 4;
    ChipCanvas canvas;
    AEChipRenderer renderer;
    JLabel infoLabel;
    AEChip chip;
    volatile boolean indexFileEnabled = false;
    Logger log = Logger.getLogger("AEViewer");
    EngineeringFormat fmt = new EngineeringFormat();
    volatile String fileSizeString = "";
    File indexFile = null;
    AEPacketRaw aeRaw;
    EventPacket ae;
    AEFileInputStream ais;
    private int packetTime=10000; // in us
    volatile boolean stop = false;
    private long FRAME_DELAY_MS=200;

    /** Initializes the applet JAERAppletViewer */
    public void init() {
        chip = new Tmpdiff128();
        extractor=chip.getEventExtractor();
        canvas = new RetinaCanvas(chip);
        renderer=chip.getRenderer();
        setLayout(new BorderLayout());
       initComponents();
       canvas.getCanvas().setSize(getWidth(),getHeight());
        getContentPane().add(canvas.getCanvas(), BorderLayout.CENTER);
        
 //        try {
////        log.info("user.path="+System.getProperty("user.path"));  // print null in applet...
//            log.info("cwd=" + new File(".").getCanonicalPath()); // shows browser home, e.g. c:\mozilla.... if permissions in java.policy allow it
//        } catch (IOException ex) {
//            log.warning(ex.toString());
//        }
        canvas.getCanvas().addKeyListener(new KeyAdapter(){
            public void keyReleased(KeyEvent e) {
//                System.out.println(e+"\n");
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

//        try {
//            java.awt.EventQueue.invokeAndWait(new Runnable() {
//                public void run() {
//                }
//            });
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
    }

    @Override
    synchronized public void start() {
        super.start();
        log.info("applet start");
        showFile(new File("H:/Program Files/Apache Software Foundation/Tomcat 6.0/webapps/jaer/retina/juggle.dat"));
    }

    @Override
    synchronized public void stop() {
        super.stop();
        log.info("applet stop, setting stop=true");
        stop=true;
    }
    
// gets called on property change, possibly with null file
    synchronized public void showFile(File file) {
        try {
//            if(fis!=null){ System.out.println("closing "+fis); fis.close();}
            if (file == null) {
                stop = true;
                return;
            }
//            fis=new FileInputStream(file);
            if (ais != null) {
//                    System.out.println("closing "+ais);
                ais.close();
                ais = null;
                System.gc(); // try to make memory mapped file GC'ed so that user can delete it
                System.runFinalization();
            // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154
            // http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038
            }
            ais = new AEFileInputStream(file);
            try {
                ais.rewind();
            } catch (IOException e) {
            }
            ;
            fileSizeString = fmt.format(ais.size()) + " events " + fmt.format(ais.getDurationUs() / 1e6f) + " s";
            showStatus(fmt.format((int) ais.size()));
            stop = false;
            repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized public void paint(Graphics g) {
        super.paint(g);
        if (stop) {
            log.info("stop set, closing input stream");
            try {
                if (ais != null) {
                    ais.close();
                    ais = null;
                    System.gc();
                }
            } catch (IOException e) {
            }
            return;
        }
        Graphics2D g2 = (Graphics2D) canvas.getCanvas().getGraphics();
//        g2.setColor(Color.black);
//        g2.fillRect(0,0,getWidth(),getHeight()); // rendering method already paints frame black, shouldn't do it here or we get flicker from black to image
        if (ais != null) {
            try {
                aeRaw = ais.readPacketByTime(packetTime);
                log.info("read aeRaw="+aeRaw);
            } catch (EOFException e) {
                try {
                    ais.rewind();
                } catch (IOException ioe) {
                    System.err.println("IOException on rewind from EOF: " + ioe.getMessage());
                    ioe.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    ais.close();
                    if (ais != null) {
                        try {
                            ais.close();
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }
                    }
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
            }
            if (aeRaw != null) {
                ae = extractor.extractPacket(aeRaw);
            }
            if (ae != null) {
                float[][][] fr = renderer.render(ae);
//                log.info("canvas.paintFrame");
                canvas.getCanvas().paint(g);
            }
        }
        g2.setColor(Color.red);
        g2.setFont(g2.getFont().deriveFont(20f));
        g2.drawString(fileSizeString, 30f, 30f);
//        infoLabel.repaint();

        try {
            Thread.currentThread().sleep(FRAME_DELAY_MS);
        } catch (InterruptedException e) {
        }
        repaint(); // recurse



    }

    /** This method is called from within the init() method to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();

        jLabel1.setText("Welcome to the jAER data viewer");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(92, Short.MAX_VALUE)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 234, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(74, 74, 74))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(131, 131, 131)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(141, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
}
