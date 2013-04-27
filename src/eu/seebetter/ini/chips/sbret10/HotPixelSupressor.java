/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.sbret10;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Cheaply suppresses hot pixels from DVS; ie pixels that continuously fire a
 * sustained stream of events.
 *
 * @author tobi
 */
@Description("Cheaply suppresses hot pixels from DVS; ie pixels that continuously fire events when when the visual input is idle.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class HotPixelSupressor extends EventFilter2D implements FrameAnnotater {

    private int numHotPixels = getInt("numHotPixels", 30);
    private HotPixelSet hotPixelSet = new HotPixelSet();
    private boolean showHotPixels = getBoolean("showHotPixels", true);
    private CollectedAddresses collectedAddresses = null;
    private int learnTimeMs = getInt("learnTimeMs", 20);
    private boolean learnHotPixels = false, learningStarted = false;
    private int learningStartedTimestamp = 0;

    private static class HotPixel implements Serializable{  // static to avoid having this reference to enclosing class in each hotpixel

        int x, y, address;
        volatile int count;

        HotPixel(BasicEvent e) {
            this.count = 1;
            address = e.address;
            x = e.x;
            y = e.y;
        }

        int incrementCount() {
            count++;
            return count;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BasicEvent) {
                return ((BasicEvent) obj).address == address;
            } else if (obj instanceof HotPixel) {
                return ((HotPixel) obj).address == address;
            } else {
                return false;
            }
        }

        public String toString() {
            return String.format("(%d,%d)", x, y);
        }

        public int hashCode() {
            return new Integer(address).hashCode();
        }
    }

    private class HotPixelSet extends HashSet<HotPixel> {

        boolean contains(BasicEvent e) {
            return contains(new HotPixel(e));
        }
        
        void storePrefs(){
         try {
            // Serialize to a byte array
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            ObjectOutput oos=new ObjectOutputStream(bos);
            Object[] hps=this.toArray();
            oos.writeObject(hps);
            oos.close();
            // Get the bytes of the serialized object
            byte[] buf=bos.toByteArray();
            getPrefs().putByteArray("HotPixelSupressor.HotPixelSet", buf);
        } catch(Exception e) {
            e.printStackTrace();
        }
          
        }
        
        void loadPrefs(){
           try {
            byte[] bytes=getPrefs().getByteArray("HotPixelSupressor.HotPixelSet", null);
            if(bytes!=null) {
                ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes));
                Object obj=in.readObject();
                Object[] array=(Object[])obj;
                clear();
                for(Object o:array){
                    add((HotPixel)o);
                }
                in.close();
                log.info("loaded existing hot pixel array with "+size()+" hot pixels");
            } else {
                log.info("no hot pixels to load");
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
         
        }
    }

    private class CollectedAddresses extends HashSet<HotPixel> {
    }

    public HotPixelSupressor(AEChip chip) {
        super(chip);
        setPropertyTooltip("numHotPixels", "maximum number of hot pixels");
        setPropertyTooltip("learnTimeMs", "how long to accumulate events during learning of hot pixels");
        setPropertyTooltip("learnHotPixels", "learn which pixels are hot");
        setPropertyTooltip("clearHotPixels", "clear list of hot pixels");
        setPropertyTooltip("showHotPixels", "label the hot pixels graphically");
        hotPixelSet.loadPrefs();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        for (BasicEvent e : in) {
            if (learnHotPixels) {
                if (learningStarted) {
                    learningStarted = false;
                    learningStartedTimestamp = e.timestamp;
                    collectedAddresses = new CollectedAddresses();

                } else if (e.timestamp - learningStartedTimestamp > (learnTimeMs << 10)) { // ms to us is <<10 approx
                    learnHotPixels = false;
                    // find largest n counts and call them hot
                    for (int i = 0; i < numHotPixels; i++) {
                        int max = 0;
                        HotPixel hp = null;
                        for (HotPixel p : collectedAddresses) {
                            if (p.count > max) {
                                max = p.count;
                                hp = p;
                            }
                        }
                        if (max < 2) {
                            break;
                        }
                        hotPixelSet.add(hp);
                        hp.count = 0; // clear it so next time it is not max
                    }
                    collectedAddresses = null; // free memory
                    hotPixelSet.storePrefs();
                } else {
                    // increment count for this address
                    HotPixel thisPixel = new HotPixel(e);
                    if (collectedAddresses.contains(thisPixel)) {
                        for (HotPixel p : collectedAddresses) {
                            if (p.equals(thisPixel)) {
                                p.incrementCount();
                            }
                        }
                    } else {
                        collectedAddresses.add(thisPixel);
                    }
                }
            }
            if (!hotPixelSet.contains(e)) {
                ApsDvsEvent a = (ApsDvsEvent) outItr.nextOutput();
                a.copyFrom(e);
            }
        }
        return out;
    }

    @Override
    synchronized public void resetFilter() {
        learnHotPixels = false;
    }

    synchronized public void doClearHotPixels() {
        hotPixelSet.clear();
    }

    @Override
    public void initFilter() {
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
    public void setNumHotPixels(int numHotPixels) {
        this.numHotPixels = numHotPixels;
        putInt("numHotPixels", numHotPixels);
    }

    synchronized public void doLearnHotPixels() {
        learnHotPixels = true;
        learningStarted = true;

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showHotPixels) {
            return;
        }
        GL gl = drawable.getGL();
        try {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
        } catch (GLException e) {
            e.printStackTrace();
            showHotPixels = false;
        }
        gl.glColor4f(.5f, .5f, .5f, .5f);
        gl.glLineWidth(1f);
        for (HotPixel p : hotPixelSet) {
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
    public void setShowHotPixels(boolean showHotPixels) {
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
    public void setLearnTimeMs(int learnTimeMs) {
        this.learnTimeMs = learnTimeMs;
        putInt("learnTimeMs", learnTimeMs);
    }
}
