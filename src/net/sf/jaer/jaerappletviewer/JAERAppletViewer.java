/*
 * JAERAppletViewer.java
 *
 * Created on September 25, 2008, 10:45 PM
 */
package net.sf.jaer.jaerappletviewer;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Random;
import java.util.logging.Logger;

import javax.swing.border.TitledBorder;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastSettings;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.EngineeringFormat;
import ch.unizh.ini.jaer.chip.retina.Tmpdiff128;

/**
 * Applet that plays events in a web browser from
 * a network and file input streams.
 * <p>
 * Note that applets have limited permissions and certain permissions
 * must be granted on the server for this applet to be run.
 * Either the applet jar must be signed and have permission granted to run the code
 * by the browser, or the java.policy file in java/lib/security can be edited on
 * the server to have the following permissions granted for jAER.jar
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
 * @author  tobi delbruck/mert yentur
 */
public class JAERAppletViewer extends javax.swing.JApplet {

    AEChip liveChip, recordedChip;
    ChipCanvas liveCanvas, recordedCanvas;
    Logger log = Logger.getLogger("JAERAppletViewer");
    EngineeringFormat fmt = new EngineeringFormat();
    volatile String fileSizeString = "";
//    File indexFile = null;
    AEInputStream aeRecordedInputStream; // file input stream
    AEUnicastInput aeLiveInputStream; // network input stream
    AEInputStream his; // url input stream
    private int packetTime = 40000; // in us
    volatile boolean stopflag = false;
    private long frameDelayMs = 40;
    // where data files are stored
//    private String dataFileFolder = "jaer/retina";
//    private String dataFileFolderPath = "/home/lcdctrl/public_html/propaganda/retina/retina"; // won't really work unless server has the files on itself in this particular folder, because this applet must load files from the server
    private int unicastInputPort = AEUnicastSettings.ARC_TDS_STREAM_PORT+1; // TODO make this applet but applet param doesn't work with jnlp, right now set to ARC data 20020 plus 1, since we will stream from DVSActApplet
    private String dataFileListURL = "http://www.ini.uzh.ch/~tobi/propaganda/retina/dataFileURLList.txt";
//    private String defaultDataFileListURL = "file:retina/dataFileURLList.txt";
    /*    private final String[] dataFileURLS = {
    "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events20050915T162359%20edmund%20chart%20wide%20dynamic%20range.mat.dat",
    "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events-2006-01-18T12-14-46+0100%20patrick%20sunglasses.dat",
    "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/Tmpdiff128-2006-04-07T14-33-44+0200-0%20sebastian%20high%20speed%20disk.dat",
    "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/Tmpdiff128-2006-02-14T07-53-37-0800-0%20walking%20to%20kripa%20buildings.dat",
    "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events20051219T172455%20driving%20pasa%20freeway.mat.dat",
    "http://www.ini.uzh.ch/~tobi/jaerapplet/retina/events20051221T014519%20freeway.mat.dat"
    };
     */

    Random random=new Random();
    @Override
    public String getAppletInfo() {
        return "jAERAppletViewer";
    }

    /** Initializes the applet JAERAppletViewer */
    @Override
	synchronized public void init() {
        log.info("applet init");
        liveChip = new Tmpdiff128();
        liveChip.setName("Live DVS");
        liveCanvas = liveChip.getCanvas();
        liveChip.getRenderer().setColorScale(2);
        liveChip.getRenderer().setColorMode(AEChipRenderer.ColorMode.GrayLevel);

        recordedChip = new Tmpdiff128();
        recordedChip.setName("Recorded DVS");
        recordedCanvas = recordedChip.getCanvas();
        recordedChip.getRenderer().setColorScale(2);
        recordedChip.getRenderer().setColorMode(AEChipRenderer.ColorMode.GrayLevel);

        initComponents();

        livePanel.add(liveCanvas.getCanvas(), BorderLayout.CENTER);
        recordedPanel.add(recordedCanvas.getCanvas(), BorderLayout.CENTER);
    }

    @Override
    synchronized public void start() {
        super.start();
        log.info("applet starting with dataFileListURL="+dataFileListURL+" unicastInputPort="+unicastInputPort);
//        canvas.getCanvas().setSize(getWidth(), getHeight());
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
            if (aeRecordedInputStream != null) {
                aeRecordedInputStream.close();
                aeRecordedInputStream = null;
            }
            if (aeLiveInputStream != null) {
                aeLiveInputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    int lastFileNumber = 0;
    BufferedReader dataFileListReader = null;
    private boolean printedMissingDataFileListWarningAlready = false;

    private String getNextFileName() {
        String fileName = null;
        try {
            if (dataFileListReader == null) {
                dataFileListReader = new BufferedReader(new InputStreamReader(new URL(dataFileListURL).openStream()));
                log.info("opened dataFileListReader from "+dataFileListURL);
                dataFileListReader.mark(3000); // char limit on data file URL list
            }
            int n = random.nextInt(20);
            for (int i = 0; i < n; i++) {
                try {
                    fileName = dataFileListReader.readLine();
                    log.info("read next data file line " + fileName);
                    if(fileName==null) {
						throw new EOFException("null filename");
					}
                } catch (EOFException eof) {
                    dataFileListReader.reset();
                }
            }
        } catch (IOException e2) {
            if (!printedMissingDataFileListWarningAlready) {
                log.warning("while opening list of data file URLs " + dataFileListURL + " : " + e2.toString());
                printedMissingDataFileListWarningAlready = true;
            }
        }
        return fileName;
    }

    private void openNextStreamFile() {
        log.info("opening next data file from URL stream");
        String fileName = getNextFileName();
        //        String file = dataFileURLS[new Random().nextInt(dataFileURLS.length)];
        if (fileName == null) {
//            log.warning("next file was null");
            return;
        }
        try {
            log.info("opening data file url input stream from " + fileName);
            URL url = new URL(fileName);
            InputStream is = new BufferedInputStream(url.openStream());
            aeRecordedInputStream = new AEInputStream(is);
            stopflag = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // opens local data files, but not useful for files served from server

//    private void openNextDataFile() {
//        log.info("opening next data file");
//        File dir = new File(dataFileFolderPath);
//        FilenameFilter filter = new FilenameFilter() {
//
//            public boolean accept(File dir, String name) {
//                return name != null && name.toString().endsWith(DATFileFilter.EXTENSION);
//            }
//        };
//        File[] files = dir.listFiles(filter);
//        if (files == null || files.length == 0) {
//            log.warning("no data files in " + dataFileFolderPath);
//            return;
//        }
//        File file = files[new Random().nextInt(files.length)];
//        try {
//            log.info("opening data file " + file);
//            if (aeRecordedInputStream != null) {
//                aeRecordedInputStream.close();
//            }
//            aeRecordedInputStream = new AEFileInputStream(file);
//            fileSizeString = fmt.format(aeRecordedInputStream.size()) + " events " + fmt.format(aeRecordedInputStream.getDurationUs() / 1e6f) + " s duration";
////            statusField.setText("Playing " + file + " with " + fileSizeString);
////            try {
////                showStatus("Playing AE Data file of size " + fileSizeString); // throws null pointer exception in applet viewer in netbeans...??
////            } catch (Exception e) {
////                e.printStackTrace();
////            }
//            stopflag = false;
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    private void openNetworkInputStream() {
        try {
            if (aeLiveInputStream != null) {
                aeLiveInputStream.close();
            }
            aeLiveInputStream = new AEUnicastInput(liveChip);
            aeLiveInputStream.setPort(unicastInputPort);
            aeLiveInputStream.set4ByteAddrTimestampEnabled(AEUnicastSettings.ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS);
            aeLiveInputStream.setAddressFirstEnabled(AEUnicastSettings.ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED);
            aeLiveInputStream.setSequenceNumberEnabled(AEUnicastSettings.ARC_TDS_SEQUENCE_NUMBERS_ENABLED);
            aeLiveInputStream.setSwapBytesEnabled(AEUnicastSettings.ARC_TDS_SWAPBYTES_ENABLED);
            aeLiveInputStream.setTimestampMultiplier(AEUnicastSettings.ARC_TDS_TIMESTAMP_MULTIPLIER);

            aeLiveInputStream.open();
            log.info("opened AEUnicastInput " + aeLiveInputStream);

            stopflag = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    EventPacket emptyPacket = new EventPacket();

    @Override
	synchronized public void paint(Graphics g) {
        super.paint(g);
        if (stopflag) {
            log.info("stop set, not painting again or calling repaint");
            return;
        }
        if (aeLiveInputStream != null) {
            AEPacketRaw aeRaw = aeLiveInputStream.readPacket();
            if (aeRaw != null) {
                EventPacket ae = liveChip.getEventExtractor().extractPacket(aeRaw);
                if (ae != null) {
                    liveChip.getRenderer().render(ae);
                    liveChip.getCanvas().paintFrame();
                    ((TitledBorder) livePanel.getBorder()).setTitle("Live: " + aeRaw.getNumEvents() + " events");
                } else {
                    ((TitledBorder) livePanel.getBorder()).setTitle("Live: " + "null packet");
                }
            }
        }
        if (aeRecordedInputStream != null) {
            try {
                AEPacketRaw aeRaw = aeRecordedInputStream.readPacketByTime(packetTime);
                if (aeRaw != null) {
                    EventPacket ae = liveChip.getEventExtractor().extractPacket(aeRaw);
                    if (ae != null) {
                        recordedChip.getRenderer().render(ae);
                        recordedChip.getCanvas().paintFrame();
                        ((TitledBorder) recordedPanel.getBorder()).setTitle("Recorded: " + aeRaw.getNumEvents() + " events");
                    }
                }
            } catch (EOFException eof) {
                log.info("EOF on file " + aeRecordedInputStream);
                openNextStreamFile();
//                openNextDataFile();
            } catch (IOException e) {
                log.warning(e.toString());
            }
        } else {
            recordedChip.getRenderer().render(emptyPacket);
            recordedChip.getCanvas().paintFrame();
        }
        try {
            Thread.currentThread();
			Thread.sleep(frameDelayMs);
        } catch (InterruptedException e) {
        }
        repaint(); // recurse
    }
    /*
    if (his == null) {
    openNextStreamFile();
    }
    AEPacketRaw aeRaw = null;
    if (his != null) {
    try {
    aeRaw = his.readPacketByTime(packetTime); // readAvailablePacket(); //his.readPacketByNumber(10000);
    } catch (EOFException e) {
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
    openNextStreamFile();
    }
    EventPacket ae = recordedChip.getEventExtractor().extractPacket(aeRaw);
    recordedChip.getRenderer().render(ae);
    recordedChip.getCanvas().paintFrame();
    } else {
    recordedChip.getRenderer().render(emptyPacket);
    recordedChip.getCanvas().paintFrame();
    }

     */

    /** This method is called from within the init() method to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTextField2 = new javax.swing.JTextField();
        livePanel = new javax.swing.JPanel();
        recordedPanel = new javax.swing.JPanel();

        jTextField2.setText("jTextField2");

        setBackground(new java.awt.Color(0, 0, 0));
        setName("jAERAppletViewer"); // NOI18N
        setStub(null);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
			public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridLayout(2, 1));

        livePanel.setBackground(new java.awt.Color(0, 0, 0));
        livePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Live - the INI kitchen", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
        livePanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(livePanel);

        recordedPanel.setBackground(new java.awt.Color(0, 0, 0));
        recordedPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Recorded DVS data from various sources", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 11), new java.awt.Color(255, 255, 255))); // NOI18N
        recordedPanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(recordedPanel);
    }// </editor-fold>//GEN-END:initComponents

private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
//        livePanel.setSize(getWidth(), getHeight() / 2);
//        recordedPanel.setSize(getWidth(), getHeight() / 2);
}//GEN-LAST:event_formComponentResized
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField jTextField2;
    private javax.swing.JPanel livePanel;
    private javax.swing.JPanel recordedPanel;
    // End of variables declaration//GEN-END:variables
}
