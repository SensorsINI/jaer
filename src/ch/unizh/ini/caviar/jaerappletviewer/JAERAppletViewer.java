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
import ch.unizh.ini.caviar.util.DATFileFilter;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.*;
import java.awt.event.KeyAdapter;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Random;
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
    // where data files are stored
    private String dataFileFolder = "H:/Program Files/Apache Software Foundation/Tomcat 6.0/webapps/jaer/retina";

    @Override
    public String getAppletInfo() {
        return "jAER Data Viewer Applet";
    }

    @Override
    public String[][] getParameterInfo() {
        String pinfo[][] = {
            {"fps", "1-100", "frames per second"},
            {"port", "8991", "recieve port for network AE UDP packets"},
            {"data", "url", "data directory for jAER data files"}
        };

        return pinfo;
    }

    /** Initializes the applet JAERAppletViewer */
    public void init() {
        chip = new Tmpdiff128();
        renderer = chip.getRenderer();
        extractor = chip.getEventExtractor();
        canvas = chip.getCanvas();
        canvas.setScale(4);
        initComponents();
        canvasPanel.add(canvas.getCanvas(), BorderLayout.CENTER);

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
        openNextDataFile();
        openNetworkInputStream();
        repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
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
            if (nis != null) {
                nis.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    int lastFileNumber = 0;
    File currentFile = null;

    synchronized public void openNextDataFile() {
        File dir = new File(dataFileFolder);
        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return name != null && name.toString().endsWith(DATFileFilter.EXTENSION);
            }
        };
        File[] files = dir.listFiles(filter);
        if (files == null || files.length == 0) {
            log.warning("no data files in " + dataFileFolder);
            return;
        }
        File file = files[new Random().nextInt(files.length)];
        try {
            fis = new AEFileInputStream(file);
            fileSizeString = fmt.format(fis.size()) + " events " + fmt.format(fis.getDurationUs() / 1e6f) + " s";
            //            showStatus("Playing AE Data file of size " + fileSizeString); // throws null pointer exception in applet viewer in netbeans...??
            stopflag = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized public void openNetworkInputStream() {
        try {
            if (nis != null) {
                nis.close();
            }
            nis = new AEUnicastInput();
            nis.setHost("localhost");
            nis.start();

            stopflag = false;
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
        if (nis != null) {
            aeRaw = nis.readPacket();
            if (aeRaw == null || (aeRaw != null && aeRaw.getNumEvents() == 0)) {
                if (fis != null) {
                    try {
                        aeRaw = fis.readPacketByTime(packetTime);
                    } catch (EOFException e) {
                        try {
                            fis.close();
                        } catch (IOException ex) {
                            log.warning("closing file on EOF: " + ex);
                        }
                        openNextDataFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            fis.close();
                        } catch (Exception e3) {
                            e3.printStackTrace();
                        }
                    }
                }
            }
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

        jTextField2 = new javax.swing.JTextField();
        canvasPanel = new javax.swing.JPanel();
        jTextField1 = new javax.swing.JTextField();
        jTextField3 = new javax.swing.JTextField();

        jTextField2.setText("jTextField2");

        setStub(null);

        canvasPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        javax.swing.GroupLayout canvasPanelLayout = new javax.swing.GroupLayout(canvasPanel);
        canvasPanel.setLayout(canvasPanelLayout);
        canvasPanelLayout.setHorizontalGroup(
            canvasPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 327, Short.MAX_VALUE)
        );
        canvasPanelLayout.setVerticalGroup(
            canvasPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 283, Short.MAX_VALUE)
        );

        jTextField1.setEditable(false);
        jTextField1.setText("jTextField1");

        jTextField3.setEditable(false);
        jTextField3.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jTextField3.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField3.setText("jTextField3");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTextField1, javax.swing.GroupLayout.DEFAULT_SIZE, 329, Short.MAX_VALUE)
            .addComponent(jTextField3, javax.swing.GroupLayout.DEFAULT_SIZE, 329, Short.MAX_VALUE)
            .addComponent(canvasPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(canvasPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel canvasPanel;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    // End of variables declaration//GEN-END:variables
}
