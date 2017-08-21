/*
 * ChipDataFilePreview.java
 *
 * Created on December 31, 2005, 5:10 PM
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.Hdf5AedatFileInputReader;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Provides preview of recorded AE data file in file dialogs. It uses the
 * default renderer, extractor and display method for the AEChip to generate a
 * preview and the 2d histograms of event activity over (by default) 40ms time
 * windows.
 *
 * @author tobi
 */
public class ChipDataFilePreview extends JPanel implements PropertyChangeListener {

    JFileChooser chooser;
    EventExtractor2D extractor;
    ChipCanvas canvas;
    AEChipRenderer renderer;
    JLabel infoLabel;
    AEChip chip;
    volatile boolean indexFileEnabled = false;
    Logger log = Logger.getLogger("AEViewer");
    /**
     * The time in us of packets by default
     */
    public int packetTimeUs = 40000;
    private File currentFile;
    private boolean newFileSelected = false;
    private boolean hdf5FileEnabled = false;

    /**
     * Creates new form ChipDataFilePreview
     *
     * @param jfc the file chooser
     * @param chip the AEChip to preview.
     */
    public ChipDataFilePreview(JFileChooser jfc, AEChip chip) {
        this.chip = chip;
        canvas = new ChipCanvas(chip);
//        if(chip.getCanvas().getDisplayMethod()==null){
//            canvas.setDisplayMethod(new ChipRendererDisplayMethod(canvas)); // needs a default display method
//        }else{
        canvas.setDisplayMethod(chip.getPreferredDisplayMethod()); // construct a new private display method to avoid using objects in different OpenGL contexts, e.g. TextRenderer's, which cause Error 1281 GL errors
//        }
        setLayout(new BorderLayout());
        this.chooser = jfc;
        extractor = chip.getEventExtractor();
        renderer = chip.getRenderer();
        canvas.getCanvas().setSize(300, 300);
        add(canvas.getCanvas(), BorderLayout.CENTER);
        canvas.getCanvas().addKeyListener(new KeyAdapter() {

            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_S:
                        packetTimeUs /= 2;
                        break;
                    case KeyEvent.VK_F:
                        packetTimeUs *= 2;
                        break;
                }
            }
        });
    }

    boolean isIndexFile(File f) {
        if (f == null) {
            return false;
        }
        if (f.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.OLD_INDEX_FILE_EXTENSION)) {
            return true;
        } else {
            return false;
        }
    }
    
    boolean isHdf5File (File f) {
        if (f == null) {
            return false;
        }
        if (f.getName().endsWith(".hdf5") || f.getName().endsWith(".h5")) {
            return true;
        } else {
            return false;
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(prop)) {
            showFile(null);
        } else if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(prop)) {
            showFile(chooser.getSelectedFile()); // starts showing selectedFile
        }
    }
    AEFileInputStream ais;
    Hdf5AedatFileInputReader hafir;
    volatile boolean deleteIt = false;

    public void deleteCurrentFile() {
        if (indexFileEnabled) {
            log.warning("won't try to delete this index file");
            return;
        }
        deleteIt = true;
    }

    public void renameCurrentFile() {
        log.warning("renaming not implemented yet for " + getCurrentFile());

    }
    volatile boolean stop = false;

    /**
     * Shows the file.
     *
     * @param file the file to show
     */
    public void showFile(File file) { //  gets called on property change, possibly with null file
        try {
//            if(fis!=null){ System.out.println("closing "+fis); fis.close();}
            if (file == null) {
                stop = true;
                return;
            }
//            fis=new FileInputStream(file);
            setCurrentFile(file);
            indexFileEnabled = isIndexFile(file);
            hdf5FileEnabled = isHdf5File(file);
            if (!indexFileEnabled && !hdf5FileEnabled) {
                if (ais != null) {
//                    System.out.println("closing "+ais);
                    ais.close();
                    ais = null;
                    System.gc(); // try to make memory mapped file GC'ed so that user can delete it
                    System.runFinalization();
                    // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154
                    // http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038
                }
                ais = new AEFileInputStream(file,chip);
                try {
                    ais.rewind();
                } catch (IOException e) {
                }
                fileSizeString = fmt.format(ais.size()) + " events " + fmt.format(ais.getDurationUs() / 1e6f) + " s";
            } else {
                if(hdf5FileEnabled) {
                    if (hafir != null) {
                        hafir.closeResources();
                        hafir = null;
                        System.gc(); // try to make memory mapped file GC'ed so that user can delete it
                        System.runFinalization();
                    }
                    hafir = new Hdf5AedatFileInputReader(file, chip);
                    hafir.openDataset("/dvs/data");
                    hafir.creatWholeFileSpace();
                } else {
                    indexFileString = getIndexFileCount(file);
                }
            }
//        infoLabel.setText(fmt.format((int)ais.size()));
            stop = false;
            repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }
    File indexFile = null;
    AEPacketRaw aeRaw;
    EventPacket ae;

    /**
     * Paints the file preview using {@link ChipCanvas#paintFrame() }.
     *
     * @param g the graphics context
     */
    @Override
    public void paintComponent(Graphics g) {
        if (stop || deleteIt) {
//            System.out.println("stop="+stop+" deleteIt="+deleteIt);
            try {
                if (ais != null) {
//                    System.out.println("closing "+ais);
                    ais.close();
                    ais = null;
                    System.gc();
                }
                if (deleteIt) {
                    deleteIt = false;
                    if (getCurrentFile() != null && getCurrentFile().isFile()) {
                        boolean deleted = getCurrentFile().delete();
                        if (deleted) {
                            log.info("succesfully deleted " + getCurrentFile());
                            chooser.rescanCurrentDirectory();
                        } else {
                            log.warning("couldn't delete file " + getCurrentFile());
                        }
                    }
                }
            } catch (IOException e) {
            }
            return;
        }
        Graphics2D g2 = (Graphics2D) canvas.getCanvas().getGraphics();
        if (newFileSelected) { // erases old text, otherwise draws over it
            newFileSelected = false;
            g2.clearRect(0, 0, getWidth(), getHeight());
        }

//        g2.setColor(Color.black);
//        g2.fillRect(0,0,getWidth(),getHeight()); // rendering method already paints frame black, shouldn't do it here or we get flicker from black to image
        if (!indexFileEnabled && !hdf5FileEnabled) {
            if (ais != null) {
                try {
                    aeRaw = ais.readPacketByTime(packetTimeUs);
                } catch (EOFException e) {
                    try {
                        ais.rewind();
                    } catch (IOException ioe) {
                        log.warning("IOException on rewind from EOF: " + ioe.getMessage());
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
                    extractor = chip.getEventExtractor();  // Desipte extrator is initiliazed at first, if jAER 3.0 file used, then Jaer3BufferParser
                                                           // will update the extrator, so we need to update this value here.
                    ae = extractor.extractPacket(aeRaw);
                }
            }
            if (ae != null) {
                renderer.render(ae);
                ArrayList<FrameAnnotater> annotators = canvas.getDisplayMethod().getAnnotators();
                canvas.getDisplayMethod().setAnnotators(null);
                canvas.paintFrame();
                canvas.getDisplayMethod().setAnnotators(annotators);
            }
        } else {
            if (hdf5FileEnabled) {
                String[] pktData = hafir.readRowData(0);
                ArrayList<FrameAnnotater> annotators = canvas.getDisplayMethod().getAnnotators();
                canvas.getDisplayMethod().setAnnotators(null);
                canvas.paintFrame();
                canvas.getDisplayMethod().setAnnotators(annotators);
            } else {
                fileSizeString = indexFileString;
                g2.clearRect(0, 0, getWidth(), getHeight());                
            }

        }
        g2.setColor(Color.red);
        g2.setFont(g2.getFont().deriveFont(17f));
        g2.drawString(fileSizeString, 30f, 30f);
//        infoLabel.repaint();

        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
        }
        repaint(); // recurse
    }
    EngineeringFormat fmt = new EngineeringFormat();
    volatile String fileSizeString = "";
    volatile String indexFileString = "";

    String getIndexFileCount(File file) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(file));
            int numFiles = 0;
            String s = null;
            StringBuilder sb = new StringBuilder();
            EngineeringFormat fmt = new EngineeringFormat();
            while ((s = r.readLine()) != null) {
                numFiles++;
                if (s != null) {
                    try {
                        File f = new File(file.getParent(), s);
                        if (f.canRead()) {
                            long l = f.length();
                            sb.append(" " + fmt.format((float) l) + "b");

                        }
                    } catch (Exception e) {
                        sb.append(" ? ");
                    }
                }
            }
            return numFiles + " files: " + sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * @return the currentFile
     */
    File getCurrentFile() {
        return currentFile;
    }

    /**
     * @param currentFile the currentFile to set
     */
    void setCurrentFile(File currentFile) {
        this.currentFile = currentFile;
        newFileSelected = true;
    }
}
