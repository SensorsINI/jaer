/*
 * ChipDataFilePreview.java
 *
 * Created on December 31, 2005, 5:10 PM
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.TextFileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.JaerAllowedSubclasses;
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

    private static final int PREVIEW_BUNDLES = 30;
    private static final int EVENTS_PER_BUNDLE = 20_000;
    private static final int PLAY_PERIOD_MS = 40;
    private static final float OVERLAY_FONT_PT = 12f;

    JFileChooser chooser;
    EventExtractor2D extractor;
    /** Dedicated renderer so the live viewer pixmap is not overwritten. */
    AEChipRenderer renderer;
    /** Chip used for extract/render (may be a headless copy of the recording chip). */
    AEChip chip;
    /** Viewer chip when the dialog opened; never replaced on the live viewer. */
    private final AEChip viewerChip;
    private final Map<Class<? extends AEChip>, AEChip> previewChipCache = new HashMap<>();
    private String chipNote = "";
    volatile boolean indexFileEnabled = false;
    Logger log = Logger.getLogger("net.sf.jaer");
    private File currentFile;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Thread loader;
    private final Timer playTimer;
    private int bundlesShown;
    /** True when the accessory should play events/frames, not only overlay text. */
    private volatile boolean videoPreview = false;
    private BufferedImage previewImage;
    private final Object previewLock = new Object();

    /**
     * Creates new form ChipDataFilePreview
     *
     * @param jfc the file chooser
     * @param chip the AEChip to preview.
     */
    public ChipDataFilePreview(JFileChooser jfc, AEChip chip) {
        this.viewerChip = chip;
        this.chip = chip;
        this.chooser = jfc;
        extractor = chip.getEventExtractor();
        renderer = createPreviewRenderer(chip);
        if (renderer instanceof DavisRenderer davisR) {
            davisR.setDisplayEvents(true);
            davisR.setDisplayFrames(true);
        }
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setOpaque(true);
        setPreferredSize(new Dimension(300, 300));
        setFocusable(true);
        playTimer = new Timer(PLAY_PERIOD_MS, this::playTick);
        playTimer.setRepeats(true);
    }

    private static AEChipRenderer createPreviewRenderer(AEChip chip) {
        AEChipRenderer live = chip.getRenderer();
        if (live != null) {
            try {
                Constructor<?> ctor = live.getClass().getConstructor(AEChip.class);
                Object created = ctor.newInstance(chip);
                if (created instanceof AEChipRenderer r) {
                    return r;
                }
            } catch (Exception e) {
                // fall through
            }
            if (live instanceof DavisRenderer) {
                return new DavisRenderer(chip);
            }
        }
        return new AEChipRenderer(chip);
    }

    /**
     * Use a headless instance of the recording's AEChip for extract/render.
     * Does not call {@link AEViewer#setAeChipClass}. On any failure, keep the
     * viewer chip and note it in the overlay.
     */
    private void prepareChipForFile(File file) {
        chipNote = "";
        Class<? extends AEChip> suggested = null;
        try {
            suggested = RecordingChipDetector.detect(file, previewChipClassNames());
        } catch (Throwable t) {
            log.log(Level.WARNING, "Could not detect AEChip for preview of " + file.getName(), t);
        }
        Class<? extends AEChip> viewerClass = viewerChip != null ? viewerChip.getClass() : null;
        if (suggested == null || viewerClass == null
                || suggested.equals(viewerClass)
                || suggested.getSimpleName().equalsIgnoreCase(viewerClass.getSimpleName())) {
            applyPreviewChip(viewerChip);
            return;
        }
        try {
            AEChip instance = previewChipCache.get(suggested);
            if (instance == null) {
                instance = constructHeadlessChip(suggested);
                if (instance == null || instance.getSizeX() <= 0 || instance.getEventExtractor() == null) {
                    throw new IllegalStateException("headless " + suggested.getSimpleName()
                            + " has no size or event extractor");
                }
                previewChipCache.put(suggested, instance);
            }
            applyPreviewChip(instance);
        } catch (Throwable t) {
            log.log(Level.WARNING, String.format(
                    "Preview could not switch to %s for %s; using %s",
                    suggested.getSimpleName(), file.getName(), viewerClass.getSimpleName()), t);
            chipNote = "using " + viewerClass.getSimpleName()
                    + " (could not load " + suggested.getSimpleName() + ")";
            applyPreviewChip(viewerChip);
        }
    }

    private void applyPreviewChip(AEChip c) {
        chip = c != null ? c : viewerChip;
        extractor = chip != null ? chip.getEventExtractor() : null;
        if (chip == viewerChip) {
            renderer = createPreviewRenderer(chip);
        } else {
            AEChipRenderer r = chip.getRenderer();
            renderer = r != null ? r : createPreviewRenderer(chip);
        }
        if (renderer instanceof DavisRenderer davisR) {
            try {
                davisR.setDisplayEvents(true);
                davisR.setDisplayFrames(true);
            } catch (Exception e) {
                log.fine("preview renderer display flags: " + e);
            }
        }
    }

    private AEChip constructHeadlessChip(Class<? extends AEChip> clazz) throws Exception {
        ChipCanvas.beginPreviewHeadless();
        try {
            Constructor<? extends AEChip> ctor = clazz.getConstructor();
            return ctor.newInstance();
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        } finally {
            ChipCanvas.endPreviewHeadless();
        }
    }

    private List<String> previewChipClassNames() {
        try {
            if (viewerChip != null && viewerChip.getAeViewer() != null) {
                List<String> names = viewerChip.getAeViewer().getChipClassNames();
                if (names != null && !names.isEmpty()) {
                    return names;
                }
            }
        } catch (Exception e) {
            log.fine("preview chip names from viewer: " + e);
        }
        try {
            Set<String> allowed = JaerAllowedSubclasses.namesOrNullIfMissing(AEChip.class);
            if (allowed != null && !allowed.isEmpty()) {
                return new ArrayList<>(allowed);
            }
        } catch (Exception e) {
            log.fine("preview chip names from allowlist: " + e);
        }
        if (viewerChip != null) {
            return List.of(viewerChip.getClass().getName());
        }
        return List.of();
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(prop)) {
            showFile(null);
        } else if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(prop)) {
            showFile(chooser.getSelectedFile());
        }
    }
    AEFileInputStreamInterface ais;

    public void deleteCurrentFile() {
        if (indexFileEnabled) {
            log.warning("won't try to delete this index file");
            return;
        }
        abortPreviewWork();
        File f = getCurrentFile();
        if (f != null && f.isFile()) {
            boolean deleted = f.delete();
            if (deleted) {
                log.info("succesfully deleted " + f);
                chooser.rescanCurrentDirectory();
            } else {
                log.warning("couldn't delete file " + f);
            }
        }
    }

    public void renameCurrentFile() {
        log.warning("renaming not implemented yet for " + getCurrentFile());

    }

    /**
     * Shows the file. Open and index run off the EDT so arrow-key scrolling can
     * abort the previous selection.
     *
     * @param file the file to show
     */
    public void showFile(File file) {
        int gen = generation.incrementAndGet();
        abortPreviewWork();
        setCurrentFile(file);
        fileSizeString = "";
        indexFileString = "";
        indexFileEnabled = isIndexFile(file);
        clearPreviewImage();
        repaint();
        if (file == null || !file.isFile()) {
            return;
        }
        final File toOpen = file;
        Thread t = new Thread(() -> loadPreview(toOpen, gen), "jaer-file-preview");
        t.setDaemon(true);
        loader = t;
        t.start();
    }

    private void abortPreviewWork() {
        playTimer.stop();
        videoPreview = false;
        Thread t = loader;
        loader = null;
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
        closePreviewStream();
        bundlesShown = 0;
    }

    private void loadPreview(File file, int gen) {
        if (obsolete(gen)) {
            return;
        }
        try {
            if (indexFileEnabled) {
                String idx = getIndexFileCount(file);
                if (obsolete(gen)) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (obsolete(gen)) {
                        return;
                    }
                    indexFileString = idx;
                    fileSizeString = idx;
                    repaint();
                });
                return;
            }
            String lower = file.getName().toLowerCase();
            prepareChipForFile(file);
            if (lower.endsWith("." + MetavisionRawFileInputStream.DATA_FILE_EXTENSION)) {
                MetavisionRawFileInputStream.HeaderInfo hi
                        = MetavisionRawFileInputStream.peekHeader(file);
                String text;
                if (hi != null && hi.evt3) {
                    text = String.format("Metavision RAW EVT3 %dx%d (open to index)",
                            hi.width, hi.height);
                } else {
                    text = "Metavision RAW (not EVT3 or unreadable header)";
                }
                finishTextOnly(gen, text);
                return;
            }
            if (lower.endsWith("." + MetavisionDatFileInputStream.DATA_FILE_EXTENSION)
                    && MetavisionDatFileInputStream.isMetavisionDatFile(file)) {
                MetavisionDatFileInputStream.HeaderInfo hi
                        = MetavisionDatFileInputStream.peekHeader(file);
                String text;
                if (hi != null) {
                    text = String.format("Metavision DAT %dx%d type=%d (open to play)",
                            hi.width, hi.height, hi.eventType);
                } else {
                    text = "Metavision DAT (unreadable header)";
                }
                finishTextOnly(gen, text);
                return;
            }
            if (lower.endsWith("." + TextFileInputStream.FILE_EXTENSION_CSV)
                    || lower.endsWith("." + TextFileInputStream.FILE_EXTENSION_TXT)) {
                String text = TextFileInputStream.peekPreviewOverlay(file);
                if (chip != null) {
                    text = text + "\n" + chip.getClass().getSimpleName();
                }
                if (chipNote != null && !chipNote.isEmpty()) {
                    text = text + "\n" + chipNote;
                }
                finishTextOnly(gen, text);
                return;
            }
            AEFileInputStreamInterface stream;
            String overlay;
            boolean play;
            if (lower.endsWith("." + RosbagFileInputStream.DATA_FILE_EXTENSION)) {
                stream = new RosbagFileInputStream(file, chip, null);
                overlay = compactSummary(file, stream, false);
                play = false;
            } else if (DsecHdf5AEInputStream.isHdf5Extension(file)
                    && DsecHdf5AEInputStream.isDsecEventsFile(file)) {
                stream = new DsecHdf5AEInputStream(file, chip, null);
                overlay = compactSummary(file, stream, false);
                play = false;
            } else if (lower.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)) {
                Aedat4FileInputStream a4 = new Aedat4FileInputStream(file, chip, null, null, false);
                stream = a4;
                overlay = a4.isIndexComplete() ? compactSummary(file, a4, true) : "";
                play = true;
            } else {
                stream = new AEFileInputStream(file, chip);
                overlay = compactSummary(file, stream, false);
                play = true;
            }
            if (obsolete(gen)) {
                try {
                    stream.close();
                } catch (Exception ignore) {
                }
                return;
            }
            try {
                stream.rewind();
            } catch (IOException e) {
                log.fine("preview rewind: " + e);
            }
            final AEFileInputStreamInterface opened = stream;
            final String overlayText = overlay;
            final boolean playVideo = play;
            SwingUtilities.invokeLater(() -> {
                if (obsolete(gen)) {
                    try {
                        opened.close();
                    } catch (Exception ignore) {
                    }
                    return;
                }
                ais = opened;
                fileSizeString = overlayText;
                videoPreview = playVideo;
                bundlesShown = 0;
                if (playVideo) {
                    playTimer.start();
                } else {
                    try {
                        opened.close();
                    } catch (Exception e) {
                        log.fine("preview summary close: " + e);
                    }
                    ais = null;
                }
                repaint();
            });
        } catch (Exception e) {
            if (obsolete(gen) || Thread.currentThread().isInterrupted()) {
                return;
            }
            if (e.getMessage() != null && e.getMessage().contains("canceled")) {
                return;
            }
            log.warning("Caught " + e.toString() + " trying to open file " + file);
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            finishTextOnly(gen, msg);
        }
    }

    private boolean obsolete(int gen) {
        return gen != generation.get() || Thread.currentThread().isInterrupted();
    }

    private void finishTextOnly(int gen, String text) {
        SwingUtilities.invokeLater(() -> {
            if (obsolete(gen)) {
                return;
            }
            fileSizeString = text;
            videoPreview = false;
            repaint();
        });
    }

    private void playTick(ActionEvent e) {
        synchronized (previewLock) {
            if (!videoPreview || ais == null) {
                return;
            }
            try {
                if (bundlesShown >= PREVIEW_BUNDLES) {
                    ais.rewind();
                    bundlesShown = 0;
                    if (renderer != null) {
                        renderer.resetFrame(renderer.getGrayValue());
                    }
                }
                AEPacketRaw aeRaw = ais.readPacketByNumber(EVENTS_PER_BUNDLE);
                extractor = chip != null ? chip.getEventExtractor() : null;
                PacketBundle bundle = extractor != null ? extractor.extractBundle(aeRaw) : null;
                if (ais instanceof Aedat4FileInputStream a4) {
                    if (bundle == null) {
                        bundle = new PacketBundle();
                    }
                    a4.appendTypedPackets(bundle);
                }
                if (renderer != null) {
                    synchronized (renderer) {
                        if (bundle != null && !bundle.isEmpty()) {
                            renderer.render(bundle);
                        } else if (aeRaw != null && extractor != null) {
                            EventPacket ae = extractor.extractPacket(aeRaw);
                            if (ae != null) {
                                renderer.render(ae);
                            }
                        }
                        blitRendererToPreviewImage();
                    }
                }
                bundlesShown++;
            } catch (EOFException ex) {
                try {
                    ais.rewind();
                    bundlesShown = 0;
                } catch (IOException ioe) {
                    log.warning("IOException on rewind from EOF: " + ioe.getMessage());
                    playTimer.stop();
                    closePreviewStream();
                }
            } catch (Exception ex) {
                log.warning("preview read failed: " + ex);
                playTimer.stop();
                closePreviewStream();
            }
        }
        repaint();
    }

    /**
     * Paints the file preview from the chip pixmap using AWT, not OpenGL.
     *
     * @param g the graphics context
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        BufferedImage img = previewImage;
        if (img != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int pw = getWidth();
            int ph = getHeight();
            int side = Math.min(pw, ph);
            int x = (pw - side) / 2;
            int y = (ph - side) / 2;
            g2.drawImage(img, x, y, side, side, this);
        }
        drawOverlay(g2);
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
        AEFileInputStreamInterface toClose;
        synchronized (previewLock) {
            toClose = ais;
            ais = null;
            videoPreview = false;
        }
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
        previewImage = null;
        try {
            if (renderer != null) {
                synchronized (renderer) {
                    renderer.resetFrame(renderer.getGrayValue());
                }
            }
        } catch (Exception e) {
            log.fine("preview frame reset: " + e);
        }
    }

    /**
     * Copies the chip renderer into a square-cropped {@link #previewImage}.
     * Davis DVS lives in {@code dvsEventsMap} (alpha-tested over APS pixmap),
     * not in {@link AEChipRenderer#getPixmapArray()}.
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
        final boolean davis = renderer instanceof DavisRenderer;
        final DavisRenderer davisR = davis ? (DavisRenderer) renderer : null;
        final float[] dvs = davisR != null ? davisR.getDvsEventsMap().array() : null;
        final boolean displayFrames = davisR == null || davisR.isDisplayFrames();
        final boolean displayEvents = davisR == null || davisR.isDisplayEvents();
        final int stride = davis ? 4 : 3;
        final int rowWidth = Math.max(1, renderer.getWidth());
        final int side = Math.min(sx, sy);
        final int x0 = (sx - side) / 2;
        final int y0 = (sy - side) / 2;
        if (previewImage == null || previewImage.getWidth() != side || previewImage.getHeight() != side) {
            previewImage = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        }
        int[] out = ((DataBufferInt) previewImage.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < side; y++) {
            int chipY = y0 + y;
            int dstRow = (side - 1 - y) * side;
            for (int x = 0; x < side; x++) {
                int chipX = x0 + x;
                int idx = stride * (chipY * rowWidth + chipX);
                if (idx + 2 >= pix.length) {
                    break;
                }
                float r;
                float g;
                float b;
                if (displayFrames) {
                    r = pix[idx];
                    g = pix[idx + 1];
                    b = pix[idx + 2];
                } else {
                    r = g = b = renderer.getGrayValue();
                }
                if (displayEvents && dvs != null && idx + 3 < dvs.length && dvs[idx + 3] > 0f) {
                    r = dvs[idx];
                    g = dvs[idx + 1];
                    b = dvs[idx + 2];
                } else if (!davis && displayEvents) {
                    r = pix[idx];
                    g = pix[idx + 1];
                    b = pix[idx + 2];
                }
                out[dstRow + x] = packRgb(r, g, b);
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

    private String compactSummary(File file, AEFileInputStreamInterface stream, boolean aedat4) {
        fmt.setPrecision(1);
        StringBuilder sb = new StringBuilder();
        sb.append(fmt.format((double) file.length()).trim()).append("B");
        double durS;
        if (stream instanceof Aedat4FileInputStream a4) {
            durS = a4.getDurationUsLong() * 1e-6;
        } else {
            durS = stream.getDurationUs() / 1e6;
        }
        sb.append("  ").append(fmt.format(durS).trim()).append("s\n");
        sb.append(fmt.format((double) stream.size()).trim()).append(" ev");
        if (aedat4 && stream instanceof Aedat4FileInputStream a4) {
            sb.append("  ").append(fmt.format((double) a4.getFrameCount()).trim()).append(" fra");
            sb.append("  ").append(fmt.format((double) a4.getImuSampleCount()).trim()).append(" IMU");
        }
        sb.append('\n');
        if (chip != null) {
            sb.append(chip.getClass().getSimpleName());
        }
        if (chipNote != null && !chipNote.isEmpty()) {
            sb.append('\n').append(chipNote);
        }
        return sb.toString();
    }

    private void drawOverlay(Graphics2D g2) {
        if (g2 == null || fileSizeString == null || fileSizeString.isEmpty()) {
            return;
        }
        g2.setColor(Color.red);
        g2.setFont(g2.getFont().deriveFont(OVERLAY_FONT_PT));
        FontMetrics fm = g2.getFontMetrics();
        int y = fm.getAscent() + 4;
        int lines = 0;
        int maxW = Math.max(8, getWidth() - 8);
        for (String line : fileSizeString.split("\\r?\\n")) {
            if (line.isEmpty()) {
                y += fm.getHeight();
                continue;
            }
            String draw = line;
            while (fm.stringWidth(draw) > maxW && draw.length() > 4) {
                draw = draw.substring(0, draw.length() - 1);
            }
            g2.drawString(draw, 4f, y);
            y += fm.getHeight();
            if (++lines >= 6) {
                break;
            }
        }
    }

    /**
     * @param currentFile the currentFile to set
     */
    void setCurrentFile(File currentFile) {
        this.currentFile = currentFile;
    }
}
