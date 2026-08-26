/*
 * ChipDataFilePreview.java
 *
 * Created on December 31, 2005, 5:10 PM
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JPanel;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEDZInputStream;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.TextFileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import net.sf.jaer.util.EngineeringFormat;
import prophesee.eventio.MetavisionDatFileInputStream;
import prophesee.eventio.MetavisionRawFileInputStream;

/**
 * Provides preview of recorded AE data file in file dialogs. Renders the
 * chip pixmap with AWT (not a second {@link ChipCanvas}/{@code GLCanvas}):
 * constructing another JOGL canvas while the viewer is already displaying
 * crashes some GPU drivers (Intel Arc {@code igxelpgicd64.dll}) during
 * {@code SetPixelFormat} or {@code TextRenderer} {@code glDrawArrays}.
 *
 * @author tobi
 */
public class ChipDataFilePreview extends JPanel implements PropertyChangeListener {

    JFileChooser chooser;
    EventExtractor2D extractor;
    AEChipRenderer renderer;
    AEChip chip;
    volatile boolean indexFileEnabled = false;
    Logger log = Logger.getLogger("net.sf.jaer");
    /**
     * The time in us of packets by default
     */
    public int packetTimeUs = 40000;
    private File currentFile;
    private boolean newFileSelected = false;
    /** True when the accessory should play events/frames, not only overlay text. */
    private volatile boolean videoPreview = false;
    private BufferedImage previewImage;

    /**
     * Creates new form ChipDataFilePreview
     *
     * @param jfc the file chooser
     * @param chip the AEChip to preview.
     */
    public ChipDataFilePreview(JFileChooser jfc, AEChip chip) {
        this.chip = chip;
        this.chooser = jfc;
        extractor = chip.getEventExtractor();
        renderer = chip.getRenderer();
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setOpaque(true);
        setPreferredSize(new Dimension(300, 300));
        setFocusable(true);
        addKeyListener(new KeyAdapter() {

            @Override
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

    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(prop)) {
            showFile(null);
        } else if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(prop)) {
            showFile(chooser.getSelectedFile()); // starts showing selectedFile
        }
    }
    AEFileInputStreamInterface ais;
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
            if (file == null) {
                stop = true;
                return;
            }
            setCurrentFile(file);
            indexFileEnabled = isIndexFile(file);
            videoPreview = false;
            if (!indexFileEnabled) {
                closePreviewStream();
                clearPreviewImage();
                System.gc(); // try to make memory mapped file GC'ed so that user can delete it
                try {
                    String lower = file.getName().toLowerCase();
                    if (lower.endsWith("." + MetavisionRawFileInputStream.DATA_FILE_EXTENSION)) {
                        // Full RAW index is slow; preview shows header only.
                        MetavisionRawFileInputStream.HeaderInfo hi
                                = MetavisionRawFileInputStream.peekHeader(file);
                        if (hi != null && hi.evt3) {
                            fileSizeString = String.format("Metavision RAW EVT3 %dx%d (open to index)",
                                    hi.width, hi.height);
                        } else {
                            fileSizeString = "Metavision RAW (not EVT3 or unreadable header)";
                        }
                    } else if (lower.endsWith("." + MetavisionDatFileInputStream.DATA_FILE_EXTENSION)
                            && MetavisionDatFileInputStream.isMetavisionDatFile(file)) {
                        MetavisionDatFileInputStream.HeaderInfo hi
                                = MetavisionDatFileInputStream.peekHeader(file);
                        if (hi != null) {
                            fileSizeString = String.format("Metavision DAT %dx%d type=%d (open to play)",
                                    hi.width, hi.height, hi.eventType);
                        } else {
                            fileSizeString = "Metavision DAT (unreadable header)";
                        }
                    } else if (lower.endsWith("." + TextFileInputStream.FILE_EXTENSION_CSV)
                            || lower.endsWith("." + TextFileInputStream.FILE_EXTENSION_TXT)) {
                        fileSizeString = summaryFromOpen(new TextFileInputStream(file, chip, null));
                    } else if (lower.endsWith("." + RosbagFileInputStream.DATA_FILE_EXTENSION)) {
                        fileSizeString = summaryFromOpen(new RosbagFileInputStream(file, chip, null));
                    } else if (DsecHdf5AEInputStream.isHdf5Extension(file)
                            && DsecHdf5AEInputStream.isDsecEventsFile(file)) {
                        fileSizeString = summaryFromOpen(new DsecHdf5AEInputStream(file, chip, null));
                    } else {
                        ais = constructPreviewStream(file, chip);
                        ais.rewind();
                        fileSizeString = overlayText(ais);
                        videoPreview = true;
                    }
                } catch (Exception e) {
                    log.warning("Caught " + e.toString() + " trying to open file " + file);
                    fileSizeString = e.getMessage() != null ? e.getMessage() : e.toString();
                    closePreviewStream();
                    videoPreview = false;
                }
            } else {
                closePreviewStream();
                clearPreviewImage();
                indexFileString = getIndexFileCount(file);
            }
            stop = false;
            requestFocusInWindow();
            repaint();  // starts recursive repaint, finishes when paint returns without calling repaint itself
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }
    File indexFile = null;
    AEPacketRaw aeRaw;
    EventPacket ae;

    /**
     * Paints the file preview from the chip pixmap using AWT, not OpenGL.
     *
     * @param g the graphics context
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (stop || deleteIt) {
            try {
                if (ais != null) {
                    closePreviewStream();
                    System.gc();
                    Thread.sleep(200);
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
            } catch (InterruptedException ex) {

            }
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        if (newFileSelected) { // erases old text, otherwise draws over it
            newFileSelected = false;
            clearPreviewImage();
        }

        if (!indexFileEnabled) {
            if (videoPreview && ais != null) {
                try {
                    aeRaw = ais.readPacketByTime(packetTimeUs);
                    extractor = chip.getEventExtractor();
                    PacketBundle bundle = extractor.extractBundle(aeRaw);
                    if (ais instanceof Aedat4FileInputStream a4) {
                        if (bundle == null) {
                            bundle = new PacketBundle();
                        }
                        a4.appendTypedPackets(bundle);
                    }
                    if (bundle != null && !bundle.isEmpty()) {
                        renderer.render(bundle);
                    } else if (aeRaw != null) {
                        ae = extractor.extractPacket(aeRaw);
                        if (ae != null) {
                            renderer.render(ae);
                        }
                    }
                    blitRendererToPreviewImage();
                } catch (EOFException e) {
                    try {
                        if (ais != null) {
                            ais.rewind();
                        }
                    } catch (IOException ioe) {
                        log.warning("IOException on rewind from EOF: " + ioe.getMessage());
                        closePreviewStream();
                    }
                } catch (Exception e) {
                    log.warning("preview read failed: " + e);
                    closePreviewStream();
                    videoPreview = false;
                }
            }
        } else {
            fileSizeString = indexFileString;
            previewImage = null;
        }
        if (previewImage != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(previewImage, 0, 0, getWidth(), getHeight(), this);
        }
        drawOverlay(g2);
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
     * Closes the preview input stream if open. Nulls {@link #ais} first so a
     * concurrent paint cannot keep reading a closed mapped file.
     */
    private void closePreviewStream() {
        AEFileInputStreamInterface toClose = ais;
        ais = null;
        videoPreview = false;
        if (toClose != null) {
            try {
                toClose.close();
            } catch (Exception e) {
                log.warning(String.format("Caught %s", e.toString()));
            }
        }
    }

    /**
     * Clears leftover APS / DVS pixmap so the next recording does not show the
     * previous file's last frame.
     */
    private void clearPreviewImage() {
        ae = null;
        aeRaw = null;
        previewImage = null;
        try {
            if (renderer != null) {
                renderer.resetFrame(renderer.getGrayValue());
            }
        } catch (Exception e) {
            log.fine("preview frame reset: " + e);
        }
    }

    /**
     * Copies the chip renderer pixmap into {@link #previewImage} (AWT, y-up
     * chip coords flipped to image coords).
     */
    private void blitRendererToPreviewImage() {
        if (renderer == null || chip == null) {
            previewImage = null;
            return;
        }
        renderer.ensurePixmapReadyForDisplay();
        float[] pix = renderer.getPixmapArray();
        if (pix == null) {
            previewImage = null;
            return;
        }
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();
        if (sx <= 0 || sy <= 0) {
            previewImage = null;
            return;
        }
        final boolean rgba = renderer instanceof DavisRenderer;
        final int stride = rgba ? 4 : 3;
        final int rowWidth = Math.max(1, renderer.getWidth());
        if (previewImage == null || previewImage.getWidth() != sx || previewImage.getHeight() != sy) {
            previewImage = new BufferedImage(sx, sy, BufferedImage.TYPE_INT_RGB);
        }
        int[] out = ((DataBufferInt) previewImage.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < sy; y++) {
            int dstRow = (sy - 1 - y) * sx;
            int srcRow = y * rowWidth;
            for (int x = 0; x < sx; x++) {
                int idx = stride * (srcRow + x);
                if (idx + 2 >= pix.length) {
                    break;
                }
                out[dstRow + x] = packRgb(pix[idx], pix[idx + 1], pix[idx + 2]);
            }
        }
    }

    private static int packRgb(float rf, float gf, float bf) {
        int r = (int) (rf * 255f);
        int g = (int) (gf * 255f);
        int b = (int) (bf * 255f);
        if (r < 0) {
            r = 0;
        } else if (r > 255) {
            r = 255;
        }
        if (g < 0) {
            g = 0;
        } else if (g > 255) {
            g = 255;
        }
        if (b < 0) {
            b = 0;
        } else if (b > 255) {
            b = 255;
        }
        return (r << 16) | (g << 8) | b;
    }

    /** Opens the production preview stream for AEDAT-4, AEDZ, and legacy AEDAT. */
    static AEFileInputStreamInterface constructPreviewStream(File file, AEChip chip) throws IOException {
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)) {
            return new Aedat4FileInputStream(file, chip);
        }
        if (lower.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDZ)) {
            return new AEDZInputStream(file);
        }
        return new AEFileInputStream(file, chip);
    }

    private String overlayText(AEFileInputStreamInterface stream) {
        String info = stream.getFileInfo();
        if (info != null && !info.isBlank()) {
            return info;
        }
        return fmt.format(stream.size()) + " events " + fmt.format(stream.getDurationUs() / 1e6f) + " s";
    }

    private String summaryFromOpen(AEFileInputStreamInterface stream) {
        try {
            return overlayText(stream);
        } finally {
            try {
                stream.close();
            } catch (Exception e) {
                log.fine("preview summary close: " + e);
            }
        }
    }

    private void drawOverlay(Graphics2D g2) {
        if (g2 == null || fileSizeString == null || fileSizeString.isEmpty()) {
            return;
        }
        g2.setColor(Color.red);
        g2.setFont(g2.getFont().deriveFont(14f));
        int y = 24;
        int lines = 0;
        for (String line : fileSizeString.split("\\r?\\n")) {
            if (line.isEmpty()) {
                y += 16;
                continue;
            }
            g2.drawString(line, 10f, y);
            y += 16;
            if (++lines >= 14) {
                break;
            }
        }
    }

    /**
     * @param currentFile the currentFile to set
     */
    void setCurrentFile(File currentFile) {
        this.currentFile = currentFile;
        newFileSelected = true;
    }
}
