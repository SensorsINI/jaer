/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.filter.AbstractNoiseFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * Cheaply suppresses (filters out) hot pixels from DVS; ie pixels that
 * continuously fire a sustained stream of events. These events are learned on
 * command, e.g. while sensor is stationary, and then the list of hot pixels is
 * filtered from the subsequent output.
 *
 * @author tobi
 */
@Description("Cheaply suppresses (filters out) hot pixels from DVS; ie pixels that continuously fire events when when the visual input is idle.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class HotPixelFilter extends AbstractNoiseFilter implements FrameAnnotater {

    @Preferred
    private int numHotPixelsMax = getInt("numHotPixelsMax", 1000);
    private final HotPixelSet hotPixelSet = new HotPixelSet();
    @Preferred
    private boolean showHotPixels = getBoolean("showHotPixels", true);
    private boolean showHotPixelsNumber = getBoolean("showHotPixelsNumber", true);
    private float showHotPixelsNumberYLocation = getFloat("showHotPixelsNumberYLocation", 0f);
    private float showHotPixelsAlpha = getFloat("showHotPixelsAlpha", .5f);
    private int showHotPixelsRadius = getInt("showHotPixelsRadius", 1);
    private CollectedAddresses collectedAddresses = null;
    @Preferred
    private int learnTimeMs = getInt("learnTimeMs", 500);
    private boolean learnHotPixels = false, learningStarted = false;
    private int learningStartedTimestamp = 0;
    protected boolean use2DBooleanArray = getBoolean("use2DBooleanArray", false);
    boolean[][] hotPixelArray = null;

    /**
     * Stores a single hot pixel, with x,y,address and event count collected
     * during sampling. The hashcode of the pixel is exactly the event address,
     * so users may need to ensure that the address is unique for events that
     * should be different.
     */
    private static class HotPixel implements Serializable { // static to avoid having this reference to enclosing class

        private static final long serialVersionUID = 3283393964235107656L;
        int x, y, address;
        volatile int count;

        HotPixel(final BasicEvent e) {
            count = 1;
            address = e.address;
            x = e.x;
            y = e.y;
        }

        int incrementCount() {
            count++;
            return count;
        }

        /**
         * Determines if this hot pixel equals another HotPixel or an event (by
         * raw address)
         *
         * @param obj BasicEvent or HotPixel
         * @return true if the address is equal (not the x,y address but the raw
         * address, which includes cell type)
         */
        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof BasicEvent) {
                return ((BasicEvent) obj).address == address;
            } else if (obj instanceof HotPixel) {
                return ((HotPixel) obj).address == address;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("(%d,%d), addr=%,d, count=%,d", x, y, address, count);
        }

        @Override
        public int hashCode() {
            return Integer.valueOf(address).hashCode();
        }
    }

    private static class HotPixelSet extends HashSet<HotPixel> {

        /**
         *
         */
        private static final long serialVersionUID = -1623414435560460344L;

        boolean contains(final BasicEvent e) {
            return contains(new HotPixel(e));
        }

        boolean contains(final HotPixel e) {
            return super.contains(e);
        }

        void storePrefs(HotPixelFilter f) {
            try {
                // Serialize to a byte array
                f.putObject("HotPixelSet", this);
            } catch (final Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public boolean add(HotPixel e) {
//            if(contains(e)){
//                log.finer(String.format("already have hot pixel %s",e.toString()));
//                return false;
//            }
            return super.add(e);
        }

        void loadPrefs(HotPixelFilter f) {
            try {
                HotPixelSet hotPixelSet = (HotPixelSet) f.getObject("HotPixelSet", new HotPixelSet());
                if (hotPixelSet.isEmpty()) {
                    f.log.info("no hot pixels loaded");
                } else {
                    clear();
                    for (final HotPixel hotPixel : hotPixelSet) {
                        add(hotPixel);
                    }
                }
            } catch (final Throwable err) {
                f.log.warning("while loading old HotPixel set, caught Exception or Error; ignoring old hot pixel set");
                // err.printStackTrace();
            }
        }
    }

    /**
     * Temporary HashMap to store collected events; maps from address to
     * HotPixel
     */
    private class CollectedAddresses extends HashMap<Integer, HotPixel> {

        private static final long serialVersionUID = -6475553583527981272L;

        public CollectedAddresses(final int initialCapacity) {
            super(initialCapacity);
        }

    }

    public HotPixelFilter(final AEChip chip) {
        super(chip);
        setPropertyTooltip(TT_FILT_CONTROL, "numHotPixelsMax", "maximum number of hot pixels");
        setPropertyTooltip(TT_FILT_CONTROL, "learnTimeMs", "how long to accumulate events during learning of hot pixels");
        setPropertyTooltip(TT_FILT_CONTROL, "doLearnHotPixels", "learn which pixels are hot");
        setPropertyTooltip(TT_FILT_CONTROL, "doClearHotPixels", "clear list of hot pixels");
        setPropertyTooltip(TT_FILT_CONTROL, "use2DBooleanArray", "use a 2D boolean array to filter rather than a Set; more efficient for large numbers of hot pixels");
        setPropertyTooltip(TT_DISP, "showHotPixels", "<html>Label the hot pixels graphically;<br>pixels can have both ON and OFF hot pixels.<br>Only if both are hot is the alpha 0.5");
        setPropertyTooltip(TT_DISP, "showHotPixelsNumber", "Show number of hot pixels and percentage of all cells");
        setPropertyTooltip(TT_DISP, "showHotPixelsFontSize", "Font size for number of hot pixels");
        setPropertyTooltip(TT_DISP, "showHotPixelsNumberYLocation", "y location of text as fraction of array size");
        setPropertyTooltip(TT_DISP, "showHotPixelsAlpha", "Alpha transparency used to draw hot pixels");
        setPropertyTooltip(TT_DISP, "showHotPixelsRadius", "Radius used to render hot pixels (make >0 to show only a few)");
        hideProperty("correlationTimeS");
        hideProperty("sigmaDistPixels");
        hideProperty("subsampleBy");
        hideProperty("letFirstEventThrough");
        hideProperty("antiCasualEnabled");
        hideProperty("adaptiveFilteringEnabled");

    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(final EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in);
        for (final BasicEvent e : in) {
            if ((e == null) || e.isSpecial() || e.isFilteredOut() || (e.x >= chip.getSizeX()) || (e.y >= chip.getSizeY())) {
                continue; // don't learn special events
            }
            totalEventCount++;
            if (learnHotPixels) {
                if (learningStarted) {
                    // initialize collection of addresses to be filled during learning
                    learningStarted = false;
                    learningStartedTimestamp = e.timestamp;
                    collectedAddresses = new CollectedAddresses(chip.getNumCells() / 10);

                } else if ((e.timestamp - learningStartedTimestamp) > (learnTimeMs << 10)) { // ms to us is <<10 approx
                    // done collecting hot pixel data, now build lookup table
                    learnHotPixels = false;
                    // find largest n counts and call them hot
                    final Set<Entry<Integer, HotPixel>> collectedAddressesEntrySet = collectedAddresses.entrySet(); // the entry set are the HotPixels
                    for (int i = 0; i < numHotPixelsMax; i++) {
                        int max = 0;
                        HotPixel hp = null;

                        for (final Entry<Integer, HotPixel> ent : collectedAddressesEntrySet) { // for each collected address
                            final HotPixel p = ent.getValue(); // get the pixel along with count
                            if (p.count > max) {
                                max = p.count; // update the max value if this pixel address has most events
                                hp = p; // set the current hot pixel to this one
                            }
                        }
                        if (max < 2) {
                            break; // if the max num events is only 1, just abort
                        }
                        if (hp != null) {  // if we got a hot pixel
                            if (hotPixelSet.add(hp)) { // add it to the set of hot pixels (if the hot pixel is already in the set, it won't add it
                                log.finer(String.format("added hot pixel %d,%d, addr=%,d", hp.x, hp.y, hp.address));
                            } else {
                                log.finer(String.format("already had hot pixel %d,%d, addr=%,d", hp.x, hp.y, hp.address));
                            }
                            hp.count = 0; // clear it's count so next time we look for max firing pixel, this one is not max anymore
                        }
                    } // look for next not pixel, up to numHotPixelsMax
                    collectedAddresses = null; // free memory
                    hotPixelSet.storePrefs(this);
                    if (use2DBooleanArray) {
                        fillHotPixelArrayFromHotPixelSet();
                    }
                } else {
                    // we're learning now by collecting addresses, store this address
                    // increment count for this address
                    final HotPixel thisPixel = new HotPixel(e);
                    if (collectedAddresses.get(e.address) != null) {
                        collectedAddresses.get(e.address).incrementCount();
                    } else {
                        collectedAddresses.put(e.address, thisPixel);
                    }
                }
            }
            // process event
            if (!use2DBooleanArray && hotPixelSet.contains(e)) {
                filterOut(e);
            } else if (use2DBooleanArray && (hotPixelArray != null) && hotPixelArray[e.x][e.y]) {
                filterOut(e);
            } else {
                filterIn(e);
            }
            // if (e.special || !hotPixelSet.contains(e) ) {
            // if(e.special){
            // // it might be IMUSample, and we need to copy out the fields which won't happen if we treat it as
            // ApsDvsEvent
            // if(e instanceof IMUSample){
            // IMUSample i=(IMUSample)e;
            // outItr.writeToNextOutput(i);
            // }
            // } else {
            // ApsDvsEvent a = (ApsDvsEvent) outItr.nextOutput();
            // a.copyFrom(e);
            // }
            // }
        }
        return in;
        // return getOutputPacket();
    }

    @Override
    synchronized public void resetFilter() {
        learnHotPixels = false;
    }

    synchronized public void doClearHotPixels() {
        hotPixelSet.clear();
        if (hotPixelArray != null) {
            for (final boolean[] ba : hotPixelArray) {
                Arrays.fill(ba, false);
            }
        }
    }

    @Override
    public void initFilter() {
        hotPixelSet.loadPrefs(this);
        if (use2DBooleanArray) {
            fillHotPixelArrayFromHotPixelSet();
        }
        removeNoiseFilterControl();
    }

    /**
     * @return the numHotPixelsMax
     */
    public int getNumHotPixelsMax() {
        return numHotPixelsMax;
    }

    /**
     * @param numHotPixelsMax the numHotPixelsMax to set
     */
    public void setNumHotPixelsMax(final int numHotPixelsMax) {
        this.numHotPixelsMax = numHotPixelsMax;
        putInt("numHotPixelsMax", numHotPixelsMax);
        int n = getNumHotPixelsMax();
        float percent = 100 * (float) n / (chip.getNumCells());
        getFilterPanel().displayTooltip("numHotPixelsMax", String.format("%.1f%% of all event addresses", percent));
    }

    synchronized public void doLearnHotPixels() {
        learnHotPixels = true;
        learningStarted = true;

    }

    @Override
    public String infoString() {
        String s = camelCaseClassname();
        return String.format("%s: numHot=%,d/%,d max", s, getCurrentNumHotPixels(), numHotPixelsMax);
    }

    @Override
    public void annotate(final GLAutoDrawable drawable) {
        super.annotate(drawable);
        final GL2 gl = drawable.getGL().getGL2();
        if (showHotPixels) {
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (final GLException e) {
                e.printStackTrace();
                showHotPixels = false;
            }
            gl.glColor4f(.1f, .1f, 1f, showHotPixelsAlpha);
            gl.glLineWidth(1f);
            for (final HotPixel p : hotPixelSet) { // note that pixels can have ON and OFF hot pixels, only if both are hot then the color is saturated
//            DrawGL.drawCircle(gl, p.x, p.y,3, 16);
                gl.glRectf(p.x - showHotPixelsRadius, p.y - showHotPixelsRadius, p.x + 1 + showHotPixelsRadius, p.y + 1 + showHotPixelsRadius);
            }
        }
        if (showHotPixelsNumber) {
            int n = getCurrentNumHotPixels();
            float percent = 100 * (float) n / (chip.getNumCells());
            String s = String.format("%,d hot pixels (%.1f%%)", n, percent);
            gl.glPushMatrix();
            DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize(), 1, chip.getSizeY() * getShowHotPixelsNumberYLocation(), 0, Color.white, s);
            gl.glPopMatrix();
        }
    }

    protected int getCurrentNumHotPixels() {
        return hotPixelSet.size();
    }

    /**
     * @return the showHotPixels
     */
    public boolean isShowHotPixels() {
        return showHotPixels;
    }

    /**
     * @param showHotPixels the showHotPixels to set
     */
    public void setShowHotPixels(final boolean showHotPixels) {
        this.showHotPixels = showHotPixels;
        putBoolean("showHotPixels", showHotPixels);
    }

    /**
     * @return the learnTimeMs
     */
    public int getLearnTimeMs() {
        return learnTimeMs;
    }

    /**
     * @param learnTimeMs the learnTimeMs to set
     */
    public void setLearnTimeMs(final int learnTimeMs) {
        this.learnTimeMs = learnTimeMs;
        putInt("learnTimeMs", learnTimeMs);
    }

    /**
     * @return the use2DBooleanArray
     */
    public boolean isUse2DBooleanArray() {
        return use2DBooleanArray;
    }

    /**
     * @param use2DBooleanArray the use2DBooleanArray to set
     */
    synchronized public void setUse2DBooleanArray(final boolean use2DBooleanArray) {
        this.use2DBooleanArray = use2DBooleanArray;
        putBoolean("use2DBooleanArray", use2DBooleanArray);
        if (use2DBooleanArray && hotPixelSet != null) {
            fillHotPixelArrayFromHotPixelSet();
        }
    }

    private void fillHotPixelArrayFromHotPixelSet() {
        hotPixelArray = new boolean[chip.getSizeX()][chip.getSizeY()];
        final Object[] hpa = hotPixelSet.toArray();
        for (final Object o : hpa) {
            final HotPixel hp = (HotPixel) o;
            hotPixelArray[hp.x][hp.y] = true;
        }
    }

    /**
     * @return the showHotPixelsAlpha
     */
    public float getShowHotPixelsAlpha() {
        return showHotPixelsAlpha;
    }

    /**
     * @param showHotPixelsAlpha the showHotPixelsAlpha to set
     */
    public void setShowHotPixelsAlpha(float showHotPixelsAlpha) {
        if (showHotPixelsAlpha > 1) {
            showHotPixelsAlpha = 1;
        }
        this.showHotPixelsAlpha = showHotPixelsAlpha;
    }

    /**
     * @return the showHotPixelsRadius
     */
    public int getShowHotPixelsRadius() {
        return showHotPixelsRadius;
    }

    /**
     * @param showHotPixelsRadius the showHotPixelsRadius to set
     */
    public void setShowHotPixelsRadius(int showHotPixelsRadius) {
        this.showHotPixelsRadius = showHotPixelsRadius;
    }

    /**
     * @return the showHotPixelsNumber
     */
    public boolean isShowHotPixelsNumber() {
        return showHotPixelsNumber;
    }

    /**
     * @param showHotPixelsNumber the showHotPixelsNumber to set
     */
    public void setShowHotPixelsNumber(boolean showHotPixelsNumber) {
        this.showHotPixelsNumber = showHotPixelsNumber;
        putBoolean("showHotPixelsNumber", showHotPixelsNumber);
    }

    /**
     * @return the showHotPixelsNumberYLocation
     */
    public float getShowHotPixelsNumberYLocation() {
        return showHotPixelsNumberYLocation;
    }

    /**
     * @param showHotPixelsNumberYLocation the showHotPixelsNumberYLocation to
     * set
     */
    public void setShowHotPixelsNumberYLocation(float showHotPixelsNumberYLocation) {
        if (showHotPixelsNumberYLocation > 1) {
            showHotPixelsNumberYLocation = 1;
        }
        this.showHotPixelsNumberYLocation = showHotPixelsNumberYLocation;
        putFloat("showHotPixelsNumberYLocation", showHotPixelsNumberYLocation);
    }

    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        // do nothing, HotPixelFilter does not rely on timestamp image
    }

}
