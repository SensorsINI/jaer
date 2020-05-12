/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
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

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

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
public class HotPixelFilter extends EventFilter2D implements FrameAnnotater {

    private int numHotPixels = getInt("numHotPixels", 30);
    private final HotPixelSet hotPixelSet = new HotPixelSet();
    private boolean showHotPixels = getBoolean("showHotPixels", true);
    private CollectedAddresses collectedAddresses = null;
    private int learnTimeMs = getInt("learnTimeMs", 20);
    private boolean learnHotPixels = false, learningStarted = false;
    private int learningStartedTimestamp = 0;
    protected boolean use2DBooleanArray = getBoolean("use2DBooleanArray", false);
    boolean[][] hotPixelArray = null;

    private static class HotPixel implements Serializable { // static to avoid having this reference to enclosing class
        // in each hotpixel

        /**
         *
         */
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
            return String.format("(%d,%d)", x, y);
        }

        @Override
        public int hashCode() {
            return new Integer(address).hashCode();
        }
    }

    private class HotPixelSet extends HashSet<HotPixel> {

        /**
         *
         */
        private static final long serialVersionUID = -1623414435560460344L;

        boolean contains(final BasicEvent e) {
            return contains(new HotPixel(e));
        }

        void storePrefs() {
            try {
                // Serialize to a byte array
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final ObjectOutput oos = new ObjectOutputStream(bos);
                final Object[] hps = this.toArray();
                oos.writeObject(hps);
                oos.close();
                // Get the bytes of the serialized object
                final byte[] buf = bos.toByteArray();
                getPrefs().putByteArray("HotPixelFilter.HotPixelSet", buf);
            } catch (final Exception e) {
                e.printStackTrace();
            }

        }

        void loadPrefs() {
            try {
                final byte[] bytes = getPrefs().getByteArray("HotPixelFilter.HotPixelSet", null);
                if (bytes != null) {
                    final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                    final Object obj = in.readObject();
                    final Object[] array = (Object[]) obj;
                    clear();
                    for (final Object o : array) {
                        add((HotPixel) o);
                    }
                    in.close();
                    EventFilter.log.info("loaded existing hot pixel array with " + size() + " hot pixels");
                } else {
                    EventFilter.log.info("no hot pixels to load");
                }
            } catch (final Throwable err) {
                EventFilter.log.warning("while loading old HotPixel set, caught Exception or Error; ignoring old hot pixel set");
                // err.printStackTrace();
            }
        }
    }

    private class CollectedAddresses extends HashMap<Integer, HotPixel> {

        /**
         *
         */
        private static final long serialVersionUID = -6475553583527981272L;

        public CollectedAddresses(final int initialCapacity) {
            super(initialCapacity);
        }

    }

    public HotPixelFilter(final AEChip chip) {
        super(chip);
        setPropertyTooltip("numHotPixels", "maximum number of hot pixels");
        setPropertyTooltip("learnTimeMs", "how long to accumulate events during learning of hot pixels");
        setPropertyTooltip("learnHotPixels", "learn which pixels are hot");
        setPropertyTooltip("clearHotPixels", "clear list of hot pixels");
        setPropertyTooltip("showHotPixels", "label the hot pixels graphically");
        setPropertyTooltip("use2DBooleanArray",
                "use a 2D boolean array to filter rather than a Set; more efficient for large numbers of hot pixels");
   }

    @Override
    synchronized public EventPacket<?> filterPacket(final EventPacket<?> in) {
        // checkOutputPacketEventType(in);
        // OutputEventIterator outItr = getOutputPacket().outputIterator();
        for (final BasicEvent e : in) {
            if ((e == null) || e.isSpecial() || e.isFilteredOut() || (e.x >= chip.getSizeX()) || (e.y >= chip.getSizeY())) {
                continue; // don't learn special events
            }
            if (learnHotPixels) {
                if (learningStarted) {
                    // initialize collection of addresses to be filled during learning
                    learningStarted = false;
                    learningStartedTimestamp = e.timestamp;
                    collectedAddresses = new CollectedAddresses(chip.getNumPixels() / 50);

                } else if ((e.timestamp - learningStartedTimestamp) > (learnTimeMs << 10)) { // ms to us is <<10 approx
                    // done collecting hot pixel data, now build lookup table
                    learnHotPixels = false;
                    // find largest n counts and call them hot
                    final Set<Entry<Integer, HotPixel>> hps = collectedAddresses.entrySet();
                    for (int i = 0; i < numHotPixels; i++) {
                        int max = 0;
                        HotPixel hp = null;

                        for (final Entry<Integer, HotPixel> ent : hps) {
                            final HotPixel p = ent.getValue();
                            if (p.count > max) {
                                max = p.count;
                                hp = p;
                            }
                        }
                        if (max < 2) {
                            break;
                        }
                        if (hp != null) {
                            hotPixelSet.add(hp);
                            hp.count = 0; // clear it so next time it is not max
                        }
                    }
                    collectedAddresses = null; // free memory
                    hotPixelSet.storePrefs();
                    if (use2DBooleanArray) {
                        hotPixelArray = new boolean[chip.getSizeX()][chip.getSizeY()];
                        final Object[] hpa = hotPixelSet.toArray();
                        for (final Object o : hpa) {
                            final HotPixel hp = (HotPixel) o;
                            hotPixelArray[hp.x][hp.y] = true;
                        }
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
                e.setFilteredOut(true);
            } else if (use2DBooleanArray && (hotPixelArray != null) && hotPixelArray[e.x][e.y]) {
                e.setFilteredOut(true);
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
        hotPixelSet.loadPrefs();
     }

    /**
     * @return the numHotPixels
     */
    public int getNumHotPixels() {
        return numHotPixels;
    }

    /**
     * @param numHotPixels the numHotPixels to set
     */
    public void setNumHotPixels(final int numHotPixels) {
        this.numHotPixels = numHotPixels;
        putInt("numHotPixels", numHotPixels);
    }

    synchronized public void doLearnHotPixels() {
        learnHotPixels = true;
        learningStarted = true;

    }

    @Override
    public void annotate(final GLAutoDrawable drawable) {
        if (!showHotPixels) {
            return;
        }
        final GL2 gl = drawable.getGL().getGL2();
        try {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
        } catch (final GLException e) {
            e.printStackTrace();
            showHotPixels = false;
        }
        gl.glColor4f(.1f, .1f, 1f, .25f);
        gl.glLineWidth(1f);
        for (final HotPixel p : hotPixelSet) {
            gl.glRectf(p.x - 1, p.y - 1, p.x + 2, p.y + 2);
        }
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
    public void setUse2DBooleanArray(final boolean use2DBooleanArray) {
        this.use2DBooleanArray = use2DBooleanArray;
    }
}
