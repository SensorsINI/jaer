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
import ch.unizh.ini.caviar.eventio.AEFileInputStreamInterface;
import ch.unizh.ini.caviar.eventio.AEInputStream;
import ch.unizh.ini.caviar.eventio.AENetworkInterfaceConstants;
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
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
grant codeBase "http://localhost:8080/jaer/dist/jAER.jar" {
permission java.io.FilePermission "<<ALL FILES>>", "read";
permission java.lang.RuntimePermission "preferences";
permission java.util.PropertyPermission "user.dir", "read";
permission java.awt.AWTPermission "setAppletStub";
  permission java.net.SocketPermission "www.ini.uzh.ch:80", "connect";
  permission java.net.SocketPermission "www.ini.uzh.ch:80", "resolve";
 * };

</pre>
 * 
 * 
 * 
 * @author  tobi/mert
 */
public class JAERAppletViewer extends javax.swing.JApplet {

    EventExtractor2D extractor;
    ChipCanvas canvas;
    AEChipRenderer renderer;
    AEChip chip;
    Logger log = Logger.getLogger("AEViewer");
    EngineeringFormat fmt = new EngineeringFormat();
    volatile String fileSizeString = "";
    File indexFile = null;
    AEPacketRaw aeRaw;
    EventPacket ae;
    AEFileInputStream fis; // file input stream
    AEUnicastInput nis; // network input stream
    AEInputStream his; // url input stream
    private int packetTime = 10000; // in us
    volatile boolean stopflag = false;
    private long frameDelayMs = 20;
    // where data files are stored
//    private String dataFileFolder = "jaer/retina";
    private String dataFileFolder = "H:/Program Files/Apache Software Foundation/Tomcat 6.0/webapps/jaer/retina"; // won't really work because this applet must load files from the server
    private int port = AENetworkInterfaceConstants.DATAGRAM_PORT;
    private final String[] dataFileURLS = {
        "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events20050915T162359%20edmund%20chart%20wide%20dynamic%20range.mat.dat",
        "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events-2006-01-18T12-14-46+0100%20patrick%20sunglasses.dat",
        "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/Tmpdiff128-2006-04-07T14-33-44+0200-0%20sebastian%20high%20speed%20disk.dat",
        "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/Tmpdiff128-2006-02-14T07-53-37-0800-0%20walking%20to%20kripa%20buildings.dat",
        "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events20051219T172455%20driving%20pasa%20freeway.mat.dat",
        "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events20051221T014519%20freeway.mat.dat"
    };

    @Override
    public String getAppletInfo() {
        return "jAER Data Viewer";
    }

    @Override
    public String[][] getParameterInfo() {
        String pinfo[][] = {
            {"fps", "1-100", "frames per second"},
            {"port", "8991", "recieve port for network AE UDP packets"},
            {"datafolder", "url", "data directory for jAER data files"}
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
        renderer.setColorScale(2);
        renderer.setColorMode(AEChipRenderer.ColorMode.GrayLevel);
        initComponents();
        canvasPanel.add(canvas.getCanvas(), BorderLayout.CENTER);
        // it looks like JNLPAppletLauncher doesn't actually pass parameters to this applet from the HTML applet
        try {
            port = Integer.parseInt(getParameter("port"));
        } catch (Exception e) {
            log.warning("while parsing applet port parameter: " + e);
        }
        try {
            frameDelayMs = 1000 / Integer.parseInt(getParameter("fps"));
        } catch (Exception e) {
            log.warning("while parsing applet fps parameter: " + e);
        }
        try {
            dataFileFolder = getParameter("datafolder");
        } catch (Exception e) {
            log.warning("while parsing applet data file folder parameter: " + e);
        }

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
        openNextStreamFile();
//        openNextDataFile();
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
            if(his!=null){
                his.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    int lastFileNumber = 0;

    private void openNextStreamFile() {
        String file = dataFileURLS[new Random().nextInt(dataFileURLS.length)];
        try {
            log.info("opening data file " + file);
            URL url=new URL(file);
            InputStream is= new BufferedInputStream(url.openStream());
            his=new AEInputStream(is);
            statusField.setText(file);
            stopflag = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
   private void openNextDataFile() {
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
            log.info("opening data file " + file);
            fis = new AEFileInputStream(file);
            fileSizeString = fmt.format(fis.size()) + " events " + fmt.format(fis.getDurationUs() / 1e6f) + " s duration";
            statusField.setText("Playing " + file + " with " + fileSizeString);
//            try {
//                showStatus("Playing AE Data file of size " + fileSizeString); // throws null pointer exception in applet viewer in netbeans...??
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
            stopflag = false;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openNetworkInputStream() {
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
                }else if(his!=null){
                    try{
                        aeRaw=his.readAvailablePacket(); //his.readPacketByNumber(10000);
                    }catch (EOFException e) {
                        try {
                            his.close();
                        } catch (IOException ex) {
                            log.warning("closing file on EOF: " + ex);
                        }
                        openNextStreamFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            his.close();
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
            Thread.currentThread().sleep(frameDelayMs);
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
        titleField = new javax.swing.JTextField();
        statusField = new javax.swing.JTextField();

        jTextField2.setText("jTextField2");

        setName("jAERAppletViewer"); // NOI18N
        setStub(null);

        canvasPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        canvasPanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(canvasPanel, java.awt.BorderLayout.CENTER);

        titleField.setEditable(false);
        getContentPane().add(titleField, java.awt.BorderLayout.NORTH);

        statusField.setEditable(false);
        statusField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        getContentPane().add(statusField, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel canvasPanel;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField statusField;
    private javax.swing.JTextField titleField;
    // End of variables declaration//GEN-END:variables
}
