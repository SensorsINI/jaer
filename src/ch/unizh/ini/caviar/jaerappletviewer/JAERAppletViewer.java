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
import ch.unizh.ini.caviar.eventio.AEDataFile;
import ch.unizh.ini.caviar.eventio.AEFileInputStream;
import ch.unizh.ini.caviar.eventio.AEInputStreamInterface;
import ch.unizh.ini.caviar.eventio.AESocket;
import ch.unizh.ini.caviar.eventio.AEUnicastInput;
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
 * Applet that allows playing events in any browser from a network or file input stream.
 * <p>
 * Note that applets have limited permissions and certain permissions must be granted on the server for this applet to be run.
 * The java.policy file in java/lib/security can be edited on the server to have the following permissions granted for jAER.jar
 * 
 * 
<pre>
 grant codeBase "file:/H:/Program Files/Apache Software Foundation/Tomcat 6.0/webapps/jaer/dist/jAER.jar" {
    permission java.io.FilePermission "<<ALL FILES>>", "read";
    permission java.lang.RuntimePermission "preferences";
    permission java.util.PropertyPermission "user.dir", "read";
    permission java.security.AllPermission;
};
</pre>
 * 
 * 
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
    AEFileInputStream fis; // file input stream
    AEUnicastInput nis; // network input stream
    private int packetTime = 10000; // in us
    volatile boolean stopflag = false;
    private long FRAME_DELAY_MS = 20;

    /** Initializes the applet JAERAppletViewer */
    public void init() {
        chip = new Tmpdiff128();
        renderer = chip.getRenderer();
        extractor = chip.getEventExtractor();
        canvas = chip.getCanvas();
        canvas.setScale(4);
        setLayout(new BorderLayout());
        initComponents();
        getContentPane().add(canvas.getCanvas(), BorderLayout.CENTER);

        //        try {
////        log.info("user.path="+System.getProperty("user.path"));  // print null in applet...
//            log.info("cwd=" + new File(".").getCanonicalPath()); // shows browser home, e.g. c:\mozilla.... if permissions in java.policy allow it
//        } catch (IOException ex) {
//            log.warning(ex.toString());
//        }
        canvas.getCanvas().addKeyListener(new KeyAdapter() {

            public void keyReleased(KeyEvent e) {
//                System.out.println(e+"\n");
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_S:
                        packetTime /= 2;
                        break;
                    case KeyEvent.VK_F:
                        packetTime *= 2;
                        break;
                }
            }
        });

    }

    @Override
    synchronized public void start() {
        super.start();
        log.info("applet start");
        canvas.getCanvas().setSize(getWidth(), getHeight());
//        showFile(new File("H:/Program Files/Apache Software Foundation/Tomcat 6.0/webapps/jaer/retina/juggle.dat"));
        showDataGramInput();
    }

    @Override
    synchronized public void stop() {
        super.stop();
        log.info("applet stop, setting stopflag=true and closing input stream");
        stopflag = true;
        try {
            if (fis != null) {
                fis.close();
                fis = null;
            }
            if(nis!=null){
                nis.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized public void showFile(File file) {
        try {
//            if(fis!=null){ System.out.println("closing "+fis); fis.close();}
            if (file == null) {
                stopflag = true;
                return;
            }
//            fis=new FileInputStream(file);
            if (fis != null) {
//                    System.out.println("closing "+ais);
                fis.close();
                fis = null;
                System.gc(); // try to make memory mapped file GC'ed so that user can delete it
                System.runFinalization();
            // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154
            // http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038
            }
            fis = new AEFileInputStream(file);
            try {
                fis.rewind();
            } catch (IOException e) {
            }
            ;
            fileSizeString = fmt.format(fis.size()) + " events " + fmt.format(fis.getDurationUs() / 1e6f) + " s";
            showStatus("Playing AE Data file of size " + fileSizeString);
            stopflag = false;
            repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized public void showDataGramInput() {
        try {
            if (nis != null) {
                nis.close();
                nis = null;
            }
            nis = new AEUnicastInput();
            nis.setHost("localhost");
            nis.start();

            showStatus("Playing AE Network stream");
            stopflag = false;
            repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized public void paint(Graphics g) {
        super.paint(g);
        if (stopflag) {
            log.info("stop set, not painting again or calling repaint");
            return;
        }
        Graphics2D g2 = (Graphics2D) canvas.getCanvas().getGraphics();
        if (fis != null) {
            try {
                aeRaw = fis.readPacketByTime(packetTime);
//                log.info("read aeRaw=" + aeRaw);
            } catch (EOFException e) {
                try {
                    fis.rewind();
                } catch (IOException ioe) {
                    System.err.println("IOException on rewind from EOF: " + ioe.getMessage());
                    ioe.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    fis.close();
                } catch (Exception e3) {
                    e3.printStackTrace();
                }
            }
        }else if(nis!=null){
//            log.info("reading packet from "+nis);
            aeRaw=nis.readPacket();
        }
        if (aeRaw != null) {
            ae = extractor.extractPacket(aeRaw);
        }
        if (ae != null) {
            float[][][] fr = renderer.render(ae);
            canvas.paintFrame();
        }


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

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("Welcome to the jAER data viewer");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(86, Short.MAX_VALUE)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 234, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(80, 80, 80))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(125, 125, 125)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(147, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables
}
