/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;

/**
 *  A virtual ball that generates events and model physics of ball movement.
 * @author Tobi
 */
public class LabyrinthVirtualBall extends EventFilter2DMouseAdaptor implements Observer {

    public static final String getDescription() {
        return "Virtual ball for labyrinth game";
    }
    LabyrinthMap map = null;
    LabyrinthBallController controller = null;
    VirtualBall ball = new VirtualBall();
    private float slowDownFactor = getFloat("slowDownFactor", 1);
    protected float staticEventRate = getFloat("staticEventRate", 1000);
    Random random = new Random();
    private boolean emitTCEvents = getBoolean("emitTCEvents", true);
    private float backgroundEventRate = getFloat("backgroundEventRate", 10000);

//    public LabyrinthVirtualBall(AEChip chip) {
//        super(chip);
//    }
    public LabyrinthVirtualBall(AEChip chip, LabyrinthGame game) {
        super(chip);
        controller = game.controller;
        map = controller.tracker.map;
        checkOutputPacketEventType(BasicEvent.class);
        setPropertyTooltip("slowDownFactor", "slow down real time by this factor");
        setPropertyTooltip("staticEventRate", "event rate when emitting events statically");
        setPropertyTooltip("emitTCEvents", "emit temporal contrast events on movement of virtual ball, intead of statically emitting events always");
        chip.addObserver(this);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        ball.update(in.getLastTimestamp());
        return out;
    }

    @Override
    synchronized public void resetFilter() {

        ball.reset();
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the slowDownFactor
     */
    public float getSlowDownFactor() {
        return slowDownFactor;
    }

    /**
     * @param slowDownFactor the slowDownFactor to set
     */
    public void setSlowDownFactor(float slowDownFactor) {
        this.slowDownFactor = slowDownFactor;
        putFloat("slowDownFactor", slowDownFactor);
    }

    /**
     * Get the value of staticEventRate
     *
     * @return the value of staticEventRate
     */
    public float getStaticEventRate() {
        return staticEventRate;
    }

    /**
     * Set the value of staticEventRate
     *
     * @param staticEventRate new value of staticEventRate
     */
    public void setStaticEventRate(float eventRate) {
        this.staticEventRate = eventRate;
        putFloat("staticEventRate", eventRate);
    }

    /**
     * @return the emitTCEvents
     */
    public boolean isEmitTCEvents() {
        return emitTCEvents;
    }

    /**
     * @param emitTCEvents the emitTCEvents to set
     */
    public void setEmitTCEvents(boolean emitTCEvents) {
        this.emitTCEvents = emitTCEvents;
        putBoolean("emitTCEvents", emitTCEvents);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof AEChip) {
            if (arg instanceof String) {
                String s = (String) arg;
                if (s.equals(AEChip.EVENT_SIZEY) || s.equals(AEChip.EVENT_SIZEX)) {
                    if (chip.getNumPixels() > 0) {
                        ball.reset();
                    }
                }
            }
        }
    }

    public class VirtualBall {

        public Point2D.Float posPixels = new Point2D.Float();
        public Point2D.Float velPPS = new Point2D.Float(0, 0);
        public float radiusPixels = 3;
        long lastUpdateTimeUs = 0;
        boolean initialized = false;

        public VirtualBall() {
        }

        public VirtualBall(Point2D pos) {
            posPixels.setLocation(pos);
        }

        public void update(int timeUs) {
            if (initialized) {

                Point2D.Float tiltsRad = controller.getTiltsRad();
                long tNowUs = System.nanoTime() >> 10;
                long dtUs = tNowUs - lastUpdateTimeUs;
                if (dtUs < 0 || dtUs > 100000) {
                    lastUpdateTimeUs = tNowUs;
                    return;
                }
                float dtSec = AEConstants.TICK_DEFAULT_US * 1e-6f * dtUs * slowDownFactor;
                float gfac = dtSec * controller.gravConstantPixPerSec2;
                velPPS.x += tiltsRad.x * gfac;
                velPPS.y += tiltsRad.y * gfac;
                float dx = velPPS.x * dtSec;
                float dy = velPPS.y * dtSec;
                posPixels.x += dx;
                posPixels.y += dy;
                if (posPixels.x < 0) {
                    posPixels.x = 0;
                    velPPS.x = 0;
                } else if (posPixels.x >= chip.getSizeX()) {
                    posPixels.x = chip.getSizeX() - 1;
                    velPPS.x = 0;
                }
                if (posPixels.y < 0) {
                    posPixels.y = 0;
                    velPPS.y = 0;
                } else if (posPixels.y >= chip.getSizeY()) {
                    posPixels.y = chip.getSizeY() - 1;
                    velPPS.y = 0;
                }


                int sx = chip.getSizeX(), sy = chip.getSizeY();
                int n;
                if (emitTCEvents) {
                    n = (int) (dtSec * staticEventRate * velPPS.distance(0, 0));
                } else {
                    n = (int) (dtSec * staticEventRate);
                }
                if (n > 10000) {
                    n = 10000;
                }
                int bg = 0;
                if (backgroundEventRate > 1) {
                    bg = (int) (dtSec * backgroundEventRate);
                }
                if (bg > 10000) {
                    bg = 10000;
                }

                if (n + bg > 0) {
                    float smalldt = dtUs / (n + bg);
                    float frac = (float) n / (n + bg);

                    OutputEventIterator i = out.outputIterator();
                    for (int k = 0; k < n + bg; k++) {
                        BasicEvent e = i.nextOutput();
                        float r = random.nextFloat();
                        if (r < frac) {
                            e.x = (short) Math.floor(posPixels.x);
                            e.y = (short) Math.floor(posPixels.y);
                            e.x = jitter(e.x, sx);
                            e.y = jitter(e.y, sy);
                        } else {
                            e.x = (short) random.nextInt(sx);
                            e.y = (short) random.nextInt(sy);
                        }
                        e.timestamp = (int) (lastUpdateTimeUs + (long) (k * smalldt));
                    }
                }

                lastUpdateTimeUs = tNowUs;

            } else {
                lastUpdateTimeUs = System.nanoTime() >> 10;
                initialized = true;
            }
        }

        private void reset() {
            posPixels.setLocation(chip.getSizeX() / 2, chip.getSizeY() / 2);
            velPPS.setLocation(0, 0);
            ball.lastUpdateTimeUs = System.nanoTime() >> 10;
        }
    }

    short jitter(int k, int lim) {
        int r = random.nextInt(5) - 2;
        int j = k + r;
        if (j < 0) {
            j = 0;
        } else if (j >= lim) {
            j = lim - 1;
        }
        return (short) j;
    }

    /**
     * @return the backgroundEventRate
     */
    public float getBackgroundEventRate() {
        return backgroundEventRate;
    }

    /**
     * @param backgroundEventRate the backgroundEventRate to set
     */
    public void setBackgroundEventRate(float backgroundEventRate) {
        this.backgroundEventRate = backgroundEventRate;
        putFloat("backgroundEventRate", backgroundEventRate);
    }
}
