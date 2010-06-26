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
public class ChipDataFilePreview extends JPanel implements PropertyChangeListener {

    JFileChooser chooser;
    EventExtractor2D extractor;
    ChipCanvas canvas;
    AEChipRenderer renderer;
    JLabel infoLabel;
    AEChip chip;
    volatile boolean indexFileEnabled = false;
    Logger log = Logger.getLogger("AEViewer");
    private int packetTime = 40000;
    private File currentFile;

    /**
     * Creates new form ChipDataFilePreview
     */
    public ChipDataFilePreview(JFileChooser jfc, AEChip chip) {
        canvas = new ChipCanvas(chip);
        canvas.setDisplayMethod(new ChipRendererDisplayMethod(canvas)); // needs a default display method
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
                        packetTime /= 2;
                        break;
                    case KeyEvent.VK_F:
                        packetTime *= 2;
                        break;
                }
            }
        });
    }

    boolean isIndexFile(File f) {
        if (f == null) {
            return false;
        }
        if (f.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.OLD_INDEX_FILE_EXTENSION)){
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

// gets called on property change, possibly with null file
    public void showFile(File file) {
        try {
//            if(fis!=null){ System.out.println("closing "+fis); fis.close();}
            if (file == null) {
                stop = true;
                return;
            }
//            fis=new FileInputStream(file);
            setCurrentFile(file);
            indexFileEnabled = isIndexFile(file);
            if (!indexFileEnabled) {
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
            } else {
                indexFileString = getIndexFileCount(file);
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
//        g2.setColor(Color.black);
//        g2.fillRect(0,0,getWidth(),getHeight()); // rendering method already paints frame black, shouldn't do it here or we get flicker from black to image
        if (!indexFileEnabled) {
            if (ais != null) {
                try {
                    aeRaw = ais.readPacketByTime(packetTime);
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
                    ae = extractor.extractPacket(aeRaw);
                }
            }
            if (ae != null) {
                renderer.render(ae);
                canvas.paintFrame();
            }
        } else {
            fileSizeString = indexFileString;
            g2.clearRect(0,0,getWidth(),getHeight());
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
            String s=null;
            StringBuilder sb=new StringBuilder();
            EngineeringFormat fmt=new EngineeringFormat();
            while ((s=r.readLine()) != null) {
                numFiles++;
                if(s!=null) {
                    try{
                    File f=new File(file.getParent(),s);
                    if(f.canRead()){
                        long l=f.length();
                        sb.append(" "+fmt.format((float)l)+"b");

                    }
                    }catch(Exception e){
                        sb.append(" ? ");
                    }
                }
            }
            return numFiles + " files: "+sb.toString();
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
    }
}
