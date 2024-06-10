/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 */
package net.sf.jaer.graphics;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import static javax.swing.Action.ACCELERATOR_KEY;
import javax.swing.ButtonGroup;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.util.SpikeSound;
import javax.swing.JRadioButtonMenuItem;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Superclass for classes that render DVS and other sensor/chip AEs to a memory
 * buffer so that they can be painted on the screen. Note these classes do not
 * actually render to the graphics device; They take AEPacket's and render them
 * to a pixmap memory buffer that later gets painted by a ChipCanvas. The method
 * chosen (by user cycling method from GUI) chooses how the events are painted.
 * In effect the events are histogrammed for most rendering methods except for
 * "color-time", and even there they are histogrammed or averaged. For methods
 * that render polarized events (such as ON-OFF) then ON events increase the
 * rendered value while OFF events decreases it. Thus the rendered image fr can
 * be drawn in 3-d if desired and it will represent a histogram, although the
 * default method using for drawing the rendered frame is to paint the cell
 * brightness.
 *
 * @author tobi
 * @see ChipRendererDisplayMethod
 */
public class AEChipRenderer extends Chip2DRenderer implements PropertyChangeListener {

    public final ToggleFadingAction toggleFadingAction = new ToggleFadingAction();
    public final ToggleAccumulation toggleAccumulationAction = new ToggleAccumulation();
    public final IncreaseContrastAction increaseContrastAction = new IncreaseContrastAction();
    public final DecreaseContrastAction decreaseContrastAction = new DecreaseContrastAction();

    private boolean addedPropertyChangeListener = false;
    public boolean externalRenderer = false;

    private ButtonGroup colorModeButtonGroup = new ButtonGroup();
    private JMenu colorModeMenu = null;
    private HashMap<ColorMode, JMenuItem> colorModeButtonMap = new HashMap();

    private boolean fadingEnabled = prefs.getBoolean("fadingEnabled", false);
    private int fadingFrames = prefs.getInt("fadingFrames", 4);

    public enum ColorMode {

        GrayLevel("Each event causes linear change in brightness", .5f),
        //        Contrast("Each event causes multiplicative change in brightness to produce logarithmic scale"),
        RedGreen("ON events are green; OFF events are red, black background", 0),
        //        FadingActivity("Events are accumulated (without polarity) and are faded away according to color scale", 0),
        //        SlidingWindow("Events are accumulated in overlapping windows", 0),
        ColorTime("Events are colored according to time within displayed slice, with red coding old events and green coding new events", 0f),
        GrayTime("Events are colored according to time within displayed slice, with white coding old events and black coding new events", 1f),
        HotCode("Events counts are colored blue to red, blue=0, red=full scale", 0),
        WhiteBackground("ON events are green; OFF events are red, white background", 1), //		ComplementaryFilter("Events are reconstructed using bandpass event filter")
        ;
        public String description;
        public float backgroundGrayLevel = 0;

        ColorMode(String description, float backgroundGrayLevel) {
            this.description = description;
            this.backgroundGrayLevel = backgroundGrayLevel;
        }

        @Override
        public String toString() {
            return super.toString() + ": " + description;
        }

        public float getBackgroundGrayLevel() {
            return backgroundGrayLevel;
        }
    };

    protected ColorMode[] colorModes = ColorMode.values(); // array of mode enums
    protected ColorMode colorMode;

    {
        ColorMode oldMode;
        try {
            oldMode = ColorMode.valueOf(prefs.get("ChipRenderer.colorMode", ColorMode.GrayLevel.name()));
        } catch (IllegalArgumentException e) {
            oldMode = ColorMode.GrayLevel;
        }
        for (ColorMode c : colorModes) {
            if (c == oldMode) {
                colorMode = c;
            }
        }
    }

    /**
     * perceptually separated hues - as estimated quickly by tobi
     */
    protected static final int[] HUES = {0, 36, 45, 61, 70, 100, 169, 188, 205, 229, 298, 318,};
    /**
     * the number of rendering methods implemented
     */
    public static int NUM_METHODS = 4;
    /**
     * number of colors used to represent time of event
     */
    public static final int NUM_TIME_COLORS = 255;
    /**
     * chip shadows Chip2D's chip to declare it as AEChip
     */
    protected AEChip chip;
    // protected AEPacket2D ae = null;
    protected EventPacket packet = null;
    /**
     * the chip rendered for
     */
    protected boolean ignorePolarityEnabled = false;
    protected Logger log = Logger.getLogger("net.sf.jaer");
    /**
     * The Colors that different cell types are painted. checkTypeColors should
     * populate this array.
     */
    protected Color[] typeColors;
    /**
     * Used for rendering multiple cell types in different RGB colors.
     * checkTypeColors should populate this array of [numTypes][3] size. Each
     * 3-vector are the RGB color components for that cell type.
     */
    protected float[][] typeColorRGBComponents;
    protected SpikeSound spikeSound;
    protected float colorContrastAdditiveStep; // this is step of RGB value used in rendering each event
    protected boolean stereoEnabled = false;
    protected int subsampleThresholdEventCount = prefs.getInt("ChipRenderer.subsampleThresholdEventCount", 50000);
    /**
     * determines subSampling of rendered events (for speed)
     */
    protected boolean subsamplingEnabled = prefs.getBoolean("ChipRenderer.subsamplingEnabled", false);
    protected float[][] timeColors;
    protected int specialCount = 0;

    private EngineeringFormat fmt = new EngineeringFormat();

    public AEChipRenderer(AEChip chip) {
        super(chip);
        if (chip == null) {
            throw new Error("tried to build ChipRenderer with null chip");
        }
        setChip(chip);
        timeColors = new float[NUM_TIME_COLORS][3];
        float s = 1f / NUM_TIME_COLORS;
        for (int i = 0; i < NUM_TIME_COLORS; i++) {
            int rgb = Color.HSBtoRGB((0.66f * (NUM_TIME_COLORS - i)) / NUM_TIME_COLORS, 1f, 1f);
            Color c = new Color(rgb);
            float[] comp = c.getRGBColorComponents(null);
            timeColors[i][0] = comp[0];
            timeColors[i][2] = comp[2];
            timeColors[i][1] = comp[1];
            // System.out.println(String.format("%.2f %.2f %.2f",comp[0],comp[1],comp[2]));
        }
        setColorScale(prefs.getInt("colorScale", 2)); // tobi changed default to 2 events full scale Apr 2013

        colorModeMenu = contructColorModeMenu();
        getSupport().addPropertyChangeListener(this);
    }
    
    

    /**
     * Does the rendering using selected method.
     *
     * @param packet a packet of events (already extracted from raw events)
     * @see #setColorMode
     */
    public synchronized void render(EventPacket packet) {
        if (!addedPropertyChangeListener) {
            if (chip instanceof AEChip) {
                AEChip aeChip = chip;
                if (aeChip.getAeViewer() != null) {
                    aeChip.getAeViewer().getSupport().addPropertyChangeListener(this);
                    addedPropertyChangeListener = true;
                }
            }
        }
        if (packet == null) {
            return;
        }
        colorContrastAdditiveStep = computeColorContrastAdditiveStep();
        this.packet = packet;
        int numEvents = packet.getSize();
        int skipBy = 1;
        if (isSubsamplingEnabled()) {
            while ((numEvents / skipBy) > getSubsampleThresholdEventCount()) {
                skipBy++;
            }
        }
        checkPixmapAllocation();
        float[] f = getPixmapArray();
        float a;
        resetSelectedPixelEventCount(); // init it for this packet
        boolean ignorePolarity = isIgnorePolarityEnabled();
        setSpecialCount(0);
        try {
            if (packet.getNumCellTypes() > 2) {
                checkTypeColors(packet.getNumCellTypes());
                if (resetAccumulationFlag || !accumulateEnabled && !externalRenderer) {
                    resetFrame(0);
                    resetAccumulationFlag = false;
                }
                for (Object obj : packet) {
                    BasicEvent e = (BasicEvent) obj;
                    if (e.isSpecial()) {
                        setSpecialCount(specialCount + 1); // TODO optimize special count increment
                        continue;
                    }
                    int type = e.getType();
                    if ((e.x == xsel) && (e.y == ysel)) {
                        playSpike(type);
                    }
                    int ind = getPixMapIndex(e.x, e.y);
                    // float[] f = fr[e.y][e.x];
                    // setPixmapPosition(e.x, e.y);
                    float[] c = typeColorRGBComponents[type];
                    if ((obj instanceof OrientationEventInterface) && (((OrientationEventInterface) obj).isHasOrientation() == false)) {
                        // if event is orientation event but orientation was not set, just draw as gray level
                        f[ind] += colorContrastAdditiveStep; // if(f[0]>1f) f[0]=1f;
                        f[ind + 1] += colorContrastAdditiveStep; // if(f[1]>1f) f[1]=1f;
                        f[ind + 2] += colorContrastAdditiveStep; // if(f[2]>1f) f[2]=1f;
                    } else {
                        f[ind] += c[0] * colorContrastAdditiveStep; // if(f[0]>1f) f[0]=1f;
                        f[ind + 1] += c[1] * colorContrastAdditiveStep; // if(f[1]>1f) f[1]=1f;
                        f[ind + 2] += c[2] * colorContrastAdditiveStep; // if(f[2]>1f) f[2]=1f;
                    }
                }
            } else {
                switch (colorMode) {
                    case GrayLevel:
                        if (resetAccumulationFlag || !accumulateEnabled && !externalRenderer) {
                            resetFrame(getGrayValue()); // also sets grayValue
                            resetAccumulationFlag = false;
                        }
                        colorContrastAdditiveStep = 2f / (colorScale + 1);
                        // colorScale=1,2,3; colorContrastAdditiveStep = 1, 1/2, 1/3, 1/4, ;
                        // later type-grayValue gives -.5 or .5 for spike value, when
                        // multipled gives steps of 1/2, 1/3, 1/4 to end up with 0 or 1 when colorScale=1 and you have
                        // one event
                        for (Object obj : packet) {
                            BasicEvent e = (BasicEvent) obj;
                            int type = e.getType();
                            if (e.isSpecial()) {
                                setSpecialCount(specialCount + 1); // TODO optimate special count increment
                                continue;
                            }
                            if ((e.x == xsel) && (e.y == ysel)) {
                                playSpike(type);
                            }
                            int ind = getPixMapIndex(e.x, e.y);
                            a = f[ind];
                            if (!ignorePolarity) {
                                a += colorContrastAdditiveStep * (type - grayValue); // type-.5 = -.5 or .5; colorContrastAdditiveStep*type= -.5, .5, (cs=1) or
                                // -.25, .25 (cs=2) etc.
                            } else {
                                a += colorContrastAdditiveStep * (1 - grayValue); // type-.5 = -.5 or .5; colorContrastAdditiveStep*type= -.5, .5, (cs=1) or -.25,
                                // .25 (cs=2) etc.
                            }
                            f[ind] = a;
                            f[ind + 1] = a;
                            f[ind + 2] = a;
                        }
                        break;
//                    case FadingActivity:
//                        checkPixmapAllocation();
//
//                        float fadeby = 1 - 1f / (colorScale + 1);
//                        for (Object obj : packet) {
//                            BasicEvent e = (BasicEvent) obj;
//                            int type = e.getType();
//                            if (e.isSpecial()) {
//                                setSpecialCount(specialCount + 1); // TODO optimate special count increment
//                                continue;
//                            }
//                            if ((e.x == xsel) && (e.y == ysel)) {
//                                playSpike(type);
//                            }
//                            int ind = getPixMapIndex(e.x, e.y);
//                            a = f[ind];
//                            if (!ignorePolarity) {
//                                a = a * fadeby + (type - grayValue) / 2;
//                            } else {
//                                a = a * fadeby * (1 - fadeby) * (1 - grayValue);
//
//                            }
//                            f[ind] = a;
//                            f[ind + 1] = a;
//                            f[ind + 2] = a;
//                        }
//                        break;
//                    case Contrast:
//                        if (!accumulateEnabled && !externalRenderer) {
//                            resetFrame(.5f);
//                        }
//                        float eventContrastRecip = 1 / eventContrast;
//                        for (Object obj : packet) {
//                            BasicEvent e = (BasicEvent) obj;
//
//                            int type = e.getType();
//                            if (e.isSpecial()) {
//                                setSpecialCount(specialCount + 1); // TODO optimate special count increment
//                                continue;
//                            }
//                            if ((e.x == xsel) && (e.y == ysel)) {
//                                playSpike(type);
//                            }
//                            int ind = getPixMapIndex(e.x, e.y);
//                            a = f[ind];
//                            switch (type) {
//                                case 0:
//                                    a *= eventContrastRecip; // off cell divides gray
//                                    break;
//                                case 1:
//                                    a *= eventContrast; // on multiplies gray
//                            }
//                            f[ind] = a;
//                            f[ind + 1] = a;
//                            f[ind + 2] = a;
//                        }
//                        break;
                    case RedGreen:
                        if (resetAccumulationFlag || !accumulateEnabled && !externalRenderer) {
                            resetFrame(getGrayValue());
                            resetAccumulationFlag = false;
                        }
                        colorContrastAdditiveStep = 1f / (colorScale); // cs=1, colorContrastAdditiveStep=1, cs=2, colorContrastAdditiveStep=.5
                        for (Object obj : packet) {
                            BasicEvent e = (BasicEvent) obj;

                            int type = e.getType();
                            if (e.isSpecial()) {
                                setSpecialCount(specialCount + 1); // TODO optimate special count increment
                                continue;
                            }
                            // System.out.println("x: " + e.x + " y:" + e.y);
                            if ((e.x == xsel) && (e.y == ysel)) {
                                playSpike(type);
                            }
                            int ind = getPixMapIndex(e.x, e.y);
                            f[ind + type] += colorContrastAdditiveStep;
                        }
                        break;
                    case ColorTime:
                        if (resetAccumulationFlag || !accumulateEnabled && !externalRenderer) {
                            resetFrame(getGrayValue());
                            resetAccumulationFlag = false;
                        }
                        if (numEvents == 0) {
                            return;
                        }
                        int ts0 = packet.getFirstTimestamp();
                        float dt = packet.getDurationUs();
                        colorContrastAdditiveStep = 1f / (colorScale); // cs=1, colorContrastAdditiveStep=1, cs=2, colorContrastAdditiveStep=.5
                        for (Object obj : packet) {
                            BasicEvent e = (BasicEvent) obj;

                            int type = e.getType();
                            if (e.isSpecial()) {
                                setSpecialCount(getSpecialCount() + 1); // TODO optimate special count increment
                                continue;
                            }
                            if ((e.x == xsel) && (e.y == ysel)) {
                                playSpike(type);
                            }
                            int index = getPixMapIndex(e.x, e.y);
                            int ind = (int) Math.floor(((NUM_TIME_COLORS - 1) * (e.timestamp - ts0)) / dt);
                            if (ind < 0) {
                                ind = 0;
                            } else if (ind >= timeColors.length) {
                                ind = timeColors.length - 1;
                            }
                            if (colorScale > 1) {
                                for (int c = 0; c < 3; c++) {
                                    f[index + c] += timeColors[ind][c] * colorContrastAdditiveStep;
                                }
                            } else {
                                f[index] = timeColors[ind][0];
                                f[index + 1] = timeColors[ind][1];
                                f[index + 2] = timeColors[ind][2];
                            }
                        }
                        break;
                    default:
                        // rendering method unknown, reset to default value
                        log.warning("colorMode " + colorMode + " unknown, reset to default value 0");
                        setColorMode(ColorMode.GrayLevel);
                }
            }
            fadeFrame();
        } catch (ArrayIndexOutOfBoundsException e) {
            if ((chip.getFilterChain() != null)
                    && (chip.getFilterChain().getProcessingMode() != net.sf.jaer.eventprocessing.FilterChain.ProcessingMode.ACQUISITION)) { // only
                // print
                // if
                // real-time
                // mode
                // has
                // not
                // invalidated
                // the
                // packet
                // we
                // are
                // trying
                // render
                e.printStackTrace();
                log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
            }
        }
        pixmap.rewind();
    }

    /**
     * Autoscales frame data so that max value is 1. If autoscale is disabled,
     * then values are just clipped to 0-1 range. If autoscale is enabled, then
     * gray is mapped back to gray and following occurs:
     * <p>
     * Global normalizer is tricky because we want to map max value to 1 OR min
     * value to 0, whichever is greater magnitude, max or min. ALSO, max and min
     * are distances from gray level in positive and negative directions. After
     * global normalizer is computed, all values are divided by normalizer in
     * order to keep gray level constant.
     *
     * @param fr the frame rgb data [y][x][rgb]
     * @param gray the gray level
     */
//    protected void autoScaleFrame(float[][][] fr, float gray) {
//        if (!autoscaleEnabled) {
//            return;
//        }
//        { // compute min and max values and divide to keep gray level constant
//            // float[] mx={Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE},
//            // mn={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE};
//            float max = Float.NEGATIVE_INFINITY, min = Float.POSITIVE_INFINITY;
//            // max=max-.5f; // distance of max from gray
//            // min=.5f-min; // distance of min from gray
//            for (float[][] element : fr) {
//                for (float[] element2 : element) {
//                    for (int k = 0; k < 3; k++) {
//                        float f = element2[k] - gray;
//                        if (f > max) {
//                            max = f;
//                        } else if (f < min) {
//                            min = f;
//                        }
//                    }
//                }
//            }
//            // global normalizer here
//            // this is tricky because we want to map max value to 1 OR min value to 0, whichever is greater magnitude,
//            // max or min
//            // ALSO, max and min are distances from gray level in positive and negative directions
//            float m, b = gray; // slope/intercept of mapping function
//            if (max == min) {
//                return; // if max==min then no need to normalize or do anything, just paint gray
//            }
//            if (max > -min) { // map max to 1, gray to gray
//                m = (1 - gray) / (max);
//                b = gray - (gray * m);
//            } else { // map min to 0, gray to gray
//                m = gray / (-min);
//                b = gray - (gray * m);
//            } // float norm=(float)Math.max(Math.abs(max),Math.abs(min)); // norm is max distance from gray level
//            // System.out.println("norm="+norm);
////            if (colorMode != ColorMode.Contrast) {
////                autoScaleValue = Math.round(Math.max(max, -min) / colorContrastAdditiveStep); // this is value shown to user, colorContrastAdditiveStep was
////                // computed during rendering to be (usually)
////                // 1/colorScale
////            } else {
////                if (max > -min) {
////                    autoScaleValue = 1; // this is value shown to user, colorContrastAdditiveStep was computed during rendering to be
////                    // (usually) 1/colorScale
////                } else {
////                    autoScaleValue = -1; // this is value shown to user, colorContrastAdditiveStep was computed during rendering to be
////                    // (usually) 1/colorScale
////                }
////            }
//            // normalize all channels
//            for (int i = 0; i < fr.length; i++) {
//                for (int j = 0; j < fr[i].length; j++) {
//                    for (int k = 0; k < 3; k++) {
//                        float f = fr[i][j][k];
//                        float f2 = (m * f) + b;
//                        if (f2 < 0) {
//                            f2 = 0;
//                        } else if (f2 > 1) {
//                            f2 = 1; // shouldn't need this
//                        }
//                        fr[i][j][k] = f2;
//                    }
//                }
//            }
//        }
//    }
    private HashMap<Integer, float[][]> typeColorsMap = new HashMap<Integer, float[][]>();

    /**
     * Creates colors for each cell type (e.g. orientation) so that they are
     * spread over hue space in a manner to attempt to be maximally different in
     * hue.
     *
     * <p>
     * Subclasses can override this method to customize the colors drawn but the
     * subclasses should check if the color have been created since
     * checkTypeColors is called on every rendering cycle. This method should
     * first check if typeColorRGBComponents already exists and has the correct
     * number of elements. If not, allocate and populate typeColorRGBComponents
     * so that type t corresponds to typeColorRGBComponents[t][0] for red,
     * typeColorRGBComponents[t][1] for green, and typeColorRGBComponents[t][3]
     * for blue. It should also populate the Color[] typeColors.
     *
     * new code should use the #makeTypeColors method which caches the colors in
     * a HashMap by numbers of cell types
     *
     * @param numCellTypes the number of colors to generate
     * @see #typeColors
     * @see #typeColorRGBComponents
     */
    protected void checkTypeColors(int numCellTypes) {
        if ((typeColorRGBComponents == null) || (typeColorRGBComponents.length != numCellTypes)) {
            typeColorRGBComponents = new float[numCellTypes][3];
            setTypeColors(new Color[numCellTypes]);
            StringBuffer b = new StringBuffer("cell type rendering colors (type: rgb):\n");
            for (int i = 0; i < typeColorRGBComponents.length; i++) {
                int hueIndex = (int) Math.floor(((float) i / typeColorRGBComponents.length) * HUES.length);
                // float hue=(float)(numCellTypes-i)/(numCellTypes);
                float hue = HUES[hueIndex] / 255f;
                // hue=hue*hue;
                // Color c=space.fromCIEXYZ(comp);
                Color c = Color.getHSBColor(hue, 1, 1);
                getTypeColors()[i] = c;
                typeColorRGBComponents[i][0] = (float) c.getRed() / 255;
                typeColorRGBComponents[i][1] = (float) c.getGreen() / 255;
                typeColorRGBComponents[i][2] = (float) c.getBlue() / 255;
                JButton but = new JButton(" "); // TODO why is this button here? maybe to be used by some subclasses or
                // users?
                but.setBackground(c);
                but.setForeground(c);
                b.append(String.format("type %d: %.2f, %.2f, %.2f\n", i, typeColorRGBComponents[i][0], typeColorRGBComponents[i][1],
                        typeColorRGBComponents[i][2]));
            }
            log.info(b.toString());
        }
    }

    /**
     * Creates colors for each cell type (e.g. orientation) so that they are
     * spread over hue space in a manner to attempt to be maximally different in
     * hue.
     * <p>
     * Subclasses can override this method to customize the colors drawn but the
     * subclasses should check if the color have been created since
     * checkTypeColors is called on every rendering cycle. This method should
     * first check if typeColorRGBComponents already exists and has the correct
     * number of elements. If not, allocate and populate typeColorRGBComponents
     * so that type t corresponds to typeColorRGBComponents[t][0] for red,
     * typeColorRGBComponents[t][1] for green, and typeColorRGBComponents[t][3]
     * for blue. It should also populate the Color[] typeColors.
     *
     * @param numCellTypes the number of colors to generate
     * @return the float[][] of colors, each row of which is an RGB color
     * triplet in float 0-1 range for a particular cell type
     * @see #typeColors
     * @see #typeColorRGBComponents
     */
    public float[][] makeTypeColors(int numCellTypes) {
        float[][] colors = typeColorsMap.get(numCellTypes);
        if (colors == null) {
            colors = new float[numCellTypes][3];
            setTypeColors(new Color[numCellTypes]);
            for (int i = 0; i < numCellTypes; i++) {
                int hueIndex = (int) Math.floor(((float) i / numCellTypes) * HUES.length);
                float hue = HUES[hueIndex] / 255f;
                Color c = Color.getHSBColor(hue, 1, 1);
                colors[i][0] = (float) c.getRed() / 255;
                colors[i][1] = (float) c.getGreen() / 255;
                colors[i][2] = (float) c.getBlue() / 255;
            }
            typeColorsMap.put(numCellTypes, colors);
            return colors;
        }
        return typeColorsMap.get(numCellTypes);
    }

    /**
     * go on to next or previous rendering method
     *
     * @param forwards true for forwards, false for backwards
     */
    public synchronized void cycleColorMode(boolean forwards) {
        int m = colorMode.ordinal();
        if (forwards) {
            if (++m >= colorModes.length) {
                m = 0;
            }
        } else {
            if (--m < 0) {
                m = colorModes.length - 1;
            }
        }
        setColorMode(colorModes[m]);
        // method++;
        // if (method > NUM_METHODS-1) method = 0;
        // setColorMode(method); // store preferences
    }

    /**
     * returns the last packet rendered
     *
     * @return the last packet that was rendered
     */
    public EventPacket getPacket() {
        return packet;
    }

    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    public AEChip getChip() {
        return chip;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public int getSubsampleThresholdEventCount() {
        return subsampleThresholdEventCount;
    }

    public boolean isIgnorePolarityEnabled() {
        return ignorePolarityEnabled;
    }

    protected boolean isMethodMonochrome() {
        if ((colorMode == ColorMode.GrayLevel) /*|| (colorMode == ColorMode.Contrast)*/) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isStereoEnabled() {
        return stereoEnabled;
    }

    public boolean isSubsamplingEnabled() {
        return subsamplingEnabled;
    }

    /**
     * Plays a single spike click and increments the selectedPixelEventCount
     * counter
     *
     * @param type 0 to play left, 1 to play right
     */
    protected void playSpike(int type) {
        if (spikeSound == null) {
            spikeSound = new SpikeSound();
        }
        spikeSound.play(type);
        selectedPixelEventCount++;
    }

    /**
     * Sets whether an external renderer adds data to the array and resets it
     *
     * @param extRender
     */
    public void setExternalRenderer(boolean extRender) {
        boolean old = this.externalRenderer;
        externalRenderer = extRender;
        getSupport().firePropertyChange(EVENT_EXTERNAL_RENDERER, old, extRender);
    }

    /**
     * Sets whether to ignore event polarity when rendering so that all event
     * types increase brightness
     *
     * @param ignorePolarityEnabled true to ignore
     */
    public void setIgnorePolarityEnabled(boolean ignorePolarityEnabled) {
        this.ignorePolarityEnabled = ignorePolarityEnabled;
    }

    private class ColorModeMenuItem extends JRadioButtonMenuItem {

        ColorMode mode;

        ColorModeMenuItem(ColorMode mode) {
            this.mode = mode;
            colorModeButtonGroup.add(this);
            setName(mode.name());
            setText(mode.name());
            setToolTipText(mode.description);
            addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info(String.format("Setting color mode to %s", mode.toString()));
                    setColorMode(mode);
                }
            });
        }
    }

    /**
     * Construct the popup menu to select color mode from all possible modes.
     *
     * @return the menu
     */
    private JMenu contructColorModeMenu() {
        JMenu menu = new JMenu("Color rendering mode");
        for (ColorMode m : colorModes) {
            JMenuItem item = new ColorModeMenuItem(m);
            menu.add(item);
            colorModeButtonMap.put(m, item);
            if (colorMode == m) {
                item.setSelected(true);
            }
        }
        return menu;
    }

    /**
     * Return the popup menu to select color mode
     */
    public JMenu getColorModeMenu() {
        return colorModeMenu;
    }

    /**
     * @param colorMode the rendering method, e.g. gray, red/green, time
     * encoded.
     */
    public synchronized void setColorMode(ColorMode colorMode) {
        ColorMode old = this.colorMode;
        this.colorMode = colorMode;
        colorModeButtonMap.get(colorMode).setSelected(true);
        prefs.put("ChipRenderer.colorMode", colorMode.name());
        log.info(this.getClass().getSimpleName() + ": colorMode=" + colorMode);
        getSupport().firePropertyChange(EVENT_COLOR_MODE_CHANGE, old, colorMode);
        setGrayValue(this.colorMode.backgroundGrayLevel);
    }

    /**
     * set the color scale. 1 means a single event is full scale, 2 means a
     * single event is half scale, etc. only applies to some rendering methods.
     *
     * @param colorScale the new color scale.
     */
    public void setColorScale(int colorScale) {
        if (colorScale < 1) {
            colorScale = 1;
        }
        if (colorScale > 32) {
            colorScale = 32;
        }
        this.colorScale = colorScale;
        // we set eventContrast so that colorScale events takes us from .5 to 1, i.e., .5*(eventContrast^cs)=1, so eventContrast=2^(1/cs)
        eventContrast = (float) (Math.pow(2, 1.0 / colorScale)); // e.g. cs=1, eventContrast=2, cs=2, eventContrast=2^0.5, etc
        prefs.putInt("colorScale", colorScale);
    }

    /**
     * @return current color scale, full scale in events
     */
    public int getColorScale() {
        if (!autoscaleEnabled) {
            return colorScale;
        } else {
            return autoScaleValue;
        }
    }

    public void setStereoEnabled(boolean stereoEnabled) {
        this.stereoEnabled = stereoEnabled;
    }

    public void setSubsampleThresholdEventCount(int subsampleThresholdEventCount) {
        prefs.putInt("ChipRenderer.subsampleThresholdEventCount", subsampleThresholdEventCount);
        this.subsampleThresholdEventCount = subsampleThresholdEventCount;
    }

    public void setSubsamplingEnabled(boolean subsamplingEnabled) {
        this.subsamplingEnabled = subsamplingEnabled;
        prefs.putBoolean("ChipRenderer.subsamplingEnabled", subsamplingEnabled);
    }

    /**
     * @see AEChipRenderer#typeColorRGBComponents
     * @return a 2-d float array of color components. Each row of the array is a
     * 3-vector of RGB color components for rendering a particular cell type.
     */
    public float[][] getTypeColorRGBComponents() {
        checkTypeColors(chip.getNumCellTypes()); // should be efficient
        return typeColorRGBComponents;
    }

    /**
     * @see AEChipRenderer#typeColorRGBComponents
     */
    public void setTypeColorRGBComponents(float[][] typeColors) {
        this.typeColorRGBComponents = typeColors;
    }

    /**
     * @return @see AEChipRenderer#typeColors
     */
    public Color[] getTypeColors() {
        return typeColors;
    }

    /**
     * @param typeColors
     * @see AEChipRenderer#typeColors
     */
    public void setTypeColors(Color[] typeColors) {
        Color[] old = this.typeColors;
        this.typeColors = typeColors;
        getSupport().firePropertyChange(EVENT_TYPE_COLORS, old, this.typeColors);

    }

    /**
     * @return the specialCount
     */
    public int getSpecialCount() {
        return specialCount;
    }

    /**
     * @param specialCount the specialCount to set
     */
    public void setSpecialCount(int specialCount) {
        this.specialCount = specialCount;
    }

    public void incrementSpecialCount(int specialCountInc) {
        this.specialCount += specialCountInc;
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
//        log.info(pce.toString());
        if (pce.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFrame(grayValue);
        } else if (pce.getPropertyName() == EVENT_COLOR_MODE_CHANGE) {
            resetFrame(getGrayValue());
        } else if (pce.getPropertyName() == EVENT_SET_GRAYLEVEL) {
            resetFrame(getGrayValue());
        } else if (pce.getPropertyName() == EVENT_SET_BACKGROUND) {
            resetFrame(getGrayValue());
        }
    }

    /**
     * fades frame data
     *
     * @param fr the frame rgb data pixmap
     */
    protected void fadeFrame() {
        if (!isFadingEnabled()) {
            return;
        }
        float fadeBy = computeFadingFactor();

        float[] fr = getPixmapArray();
        for (int i = 0; i < fr.length; i++) {

            fr[i] = fadeToGray(fr[i], fadeBy, grayValue); //fadeBy * fr[i]);
        }
    }

    /**
     * Set true to make the rendering accumulate and fade or false if should
     * just render last slice from gray level
     *
     * @param fadingEnabled the fadingEnabled to set
     */
    public void setFadingEnabled(boolean fadingEnabled) {
        log.info(String.format("Set fading=%s", fadingEnabled));
        this.fadingEnabled = fadingEnabled;
        prefs.putBoolean("fadingEnabled", fadingEnabled);
    }

    /**
     * Returns true if the rendering should accumulate and fade or false if just
     * last slice is rendered
     *
     * @return the fadingEnabled
     */
    public boolean isFadingEnabled() {
        return fadingEnabled;
    }

    /**
     * @return the fadingFrames
     */
    public int getFadingFrames() {
        return fadingFrames;
    }

    /**
     * @param fadingFrames the fadingFrames to set
     */
    public void setFadingFrames(int fadingFrames) {
        if (fadingFrames < 1) {
            fadingFrames = 1;
        } else if (fadingFrames > 64) {
            fadingFrames = 64;
        }
        this.fadingFrames = fadingFrames;
        log.info(String.format("Set fading frames to %d which multiples past frame by %.2f", fadingFrames, computeFadingFactor()));
        prefs.putInt("fadingFrames", fadingFrames);
    }

    /**
     * Computes how much to fade the old rendering values
     *
     * @return 1 if fadingEnabled=false, otherwise how much to fade current
     * values by, depending on fadingFrames. If fadingFrames=1, then previous
     * frame is multiplied by 1-1/2=.5, if 2, then 1-1/3=.76, if 3, then
     * 1-1/4=.75 etc.
     */
    protected float computeFadingFactor() {
        if (!isFadingEnabled()) {
            return 1;
        }
        if (chip.getAeViewer() == null || chip.getAeViewer().isPaused()) {
            return 1;
        }
        float fadeTo = 1 - 1f / (fadingFrames + .25f); // TODO make it really depend on rendering rate
        return fadeTo;
    }

    /**
     * Computes the IIR tau of the current fading based on measured actual
     * rendering rate
     *
     * @return tau in seconds
     */
    protected float computeFadingTauS() {
        float renderingFps = chip.getAeViewer().getFrameRater().getAverageFPS();
        if (Float.isNaN(renderingFps)) {
            return Float.NaN;
        }
        float tau = 1 / ((1 - computeFadingFactor()) * renderingFps);
        return tau;
    }

    private String getFadingDescription() {
        return String.format("Fading level %d with tau=%ss", getFadingFrames(), fmt.format(computeFadingTauS()));
    }

    /**
     * Computes how much to step R,G,and B values for methods that show linear
     * contrast like gray and red/green
     *
     * @return the step size, computed from step = 1 - 1f / (getColorScale() +
     * 1)
     */
    protected float computeColorContrastAdditiveStep() {
        if (isFadingEnabled()) {
            return 1;
        }
        if (chip.getAeViewer() == null) {
            return 1;
        }
        if (chip.getAeViewer().isPaused()) {
            return 1;
        }
        float step = 2f / (getColorScale() + 1); // TODO make it really depend on rendering rate
        return step;
    }

    /**
     * Fades existing RGB rendering value towards gray by the amount fadeBy (1
     * to not fade, 0 to fade completely to gray)
     *
     * @param v current R,G, or B value
     * @param fadeBy factor by which to fade, 1 to not fade, 0 to completely
     * fade
     * @param gray the gray level to fade towards
     * @return the new pixel RGB value
     */
    protected final float fadeToGray(float v, float fadeBy, float gray) {
        v = (v - gray) * fadeBy + gray;
//        if (Math.abs(v - gray) < 1 / 255f) {
//            v = gray;
//        }
        return v;
    }

    abstract public class MyAction extends AbstractAction {

        protected final String path = "/net/sf/jaer/graphics/icons/";

        public MyAction() {
            super();
        }

        public MyAction(String name) {
            putValue(Action.NAME, name);
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, name);
        }

        public MyAction(String name, String tooltip) {
            putValue(Action.NAME, name);
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, tooltip);
        }

        protected void showAction() {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().showActionText((String) getValue(Action.SHORT_DESCRIPTION));
            }
        }

        protected void showAction(String s) {
            if (chip.getAeViewer() != null && s != null) {
                chip.getAeViewer().showActionText(s);
            }
        }
    }

    final public class ToggleAccumulation extends MyAction {

        public ToggleAccumulation() {
            super("Accumulate", "<html>Toggles continuously accumulating events without resetting)");
            putValue(ACCELERATOR_KEY, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, 0));
            putValue(Action.SELECTED_KEY, isAccumulateEnabled());
        }

        @Override
        public void actionPerformed(ActionEvent e) {

            setAccumulateEnabled(!isAccumulateEnabled());
            putValue(Action.SELECTED_KEY, isAccumulateEnabled());
            showAction();
        }

        @Override
        protected void showAction() {
            if (!isAccumulateEnabled()) {
                showAction("Accumulation disabled");
            } else {
                showAction("Accumulation enabled");
            }
        }

    }

    final public class ToggleFadingAction extends MyAction {

        public ToggleFadingAction() {
            super("Fade", "<html>Fade away display of past frames according to color scale."
                    + "<p>To change the amount of fading,<br>  increase or decrease contrast with the UP and DOWN arrow keys.");
            putValue(ACCELERATOR_KEY, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK));
            putValue(Action.SELECTED_KEY, isFadingEnabled());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (isFadingEnabled()) {
                setFadingEnabled(false);
            } else {
                setFadingEnabled(true);
            }
            putValue(Action.SELECTED_KEY, isFadingEnabled());
            showAction();
        }

        @Override
        protected void showAction() {
            if (!isFadingEnabled()) {
                showAction("Fading disabled");
            } else {
                float fadingTauS = computeFadingTauS();
                showAction(getFadingDescription());
            }
        }

    }

    final public class IncreaseContrastAction extends MyAction {

        public IncreaseContrastAction() {
            super("Increase contrast", "<html>Increase constrast or decrease fading of display");
            putValue(ACCELERATOR_KEY, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (isFadingEnabled()) {
                setFadingFrames(getFadingFrames() - 1);
            } else {
                setColorScale(getColorScale() - 1);
            }
            showAction();
        }

        @Override
        protected void showAction() {
            if (!isFadingEnabled()) {
                showAction(String.format("Increase DVS contrast to %d events full scale", getColorScale()));
            } else {
                showAction(String.format("Shorten fading to %s", getFadingDescription()));
            }
        }
    }

    final public class DecreaseContrastAction extends MyAction {

        public DecreaseContrastAction() {
            super("Decrease contrast", "<html>Decrease constrast or increase fading of display");
            putValue(ACCELERATOR_KEY, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0));

        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (isFadingEnabled()) {
                setFadingFrames(getFadingFrames() + 1);
            } else {
                setColorScale(getColorScale() + 1);
            }
            showAction();
        }

        @Override
        protected void showAction() {
            if (!isFadingEnabled()) {
                showAction(String.format("Decrease DVS contrast to %d events full scale", getColorScale()));
            } else {
                showAction(String.format("Lengthen fading to %s", getFadingDescription()));
            }
        }
    }

}
