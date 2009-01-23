/*
 * UioCameraRenderer.java
 *
 * Created on 16. mai 2006, 10:19
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dollbrain;

import net.sf.jaer.chip.AEChip;
//import ch.unizh.ini.caviar.aemonitor.AEPacket2D;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.*;

/**
 * This class overrides the render(AEPacket2D ae) to display an image
 * with gray levels on black. The number of spikes that a packet needs to
 * saturate a pixels is given by the colorScale variable which is adjustable
 * with the up and down arrow keys (i.e. contrast).
 *
 * Future plans (TODO's):
 * - The contrast step height should be normalized by the packet size and
 * time-frame to give a more stable image at high rendering speed and
 * to keep contrast when swapping packetsize.
 * - Implement a fixed-pattern noise canceler
 * @author hansbe@ifi.uio.no
 */
public class Dollbrain1Renderer extends AEChipRenderer {

//    public enum ColorMode {PositiveGrayLevelOnBlack};
    /** Creates a new instance of UioCameraRenderer
     * @param chip the chip we're rendering for
     */
    public Dollbrain1Renderer(AEChip chip) {
        super(chip);

        nextframe = new int[chip.getSizeY()][5][2];
    }
    private int frameStart;
    private int lastTimestamp;
    private int firstTimestamp = 0;
    private boolean firstSpike = false;
    private int[][][] nextframe;
    private int colorMax = 170;
    private int colorMin = 110;
    private boolean face;
    private boolean FFR;
    private boolean FFL;

//    private float scaleA=0.1175f;
//    private int scaleB=400000;
//
//    public int increaseContrast()
//    {
//        scaleB+=50000;
//        scaleA=scaleA * 2f;
//
//        log.info("scaleA "+ scaleA + " scaleB " + scaleB);
//
//        return this.getColorScale();
//    }
//
//    public int decreaseContrast()
//    {
//        scaleB-=50000;
//        scaleA=scaleA * 0.5f;
//
//        log.info("scaleA "+ scaleA + " scaleB " + scaleB);
//
//        return this.getColorScale();
//    }
    /**
     * Does the rendering (only one colorMode is available)
     *
     * @return reference to the frame   [y][x][rgb]
     */
    public synchronized void render(EventPacket packet) {
        if (packet == null) {
            return;
        }
        this.packet = packet;
//        int numEvents = packet.getSize();
        //  System.out.println("packet size: " + numEvents);

        int tt;
//        checkFr();
        float[] f = getPixmapArray();

        try {
            for (Object obj : packet) {
                ColorEvent e = (ColorEvent) obj;

                if (e.getX() == 7 && e.getY() == 3) {
                    float val;
                    //System.out.println("FrameStart received " + frameStart+" lastTimestamp "+ lastTimestamp);

                    if (lastTimestamp > frameStart) {
                        lastTimestamp = lastTimestamp - frameStart;
                    } else {
                        log.warning("FrameStart " + frameStart + " bigger than lastTimestamp " + lastTimestamp);
                    }

                    frameStart = e.getTimestamp();
                    //     System.out.println("lts "+lastTimestamp);
                    for (int i = 0; i < chip.getSizeY(); i++) {
                        for (int j = 0; j < 5; j++) {
                            //        System.out.println("nextframe "+ i + " " + j + " :"+nextframe[i][j]);
                            // val=0.5f * ((float) this.lastTimestamp -(float) this.firstTimestamp) / (float)(nextframe[i][j][0]+1);

                            if (this.isAutoscaleEnabled()) {
                                val = 1 - (float) nextframe[i][j][0] / (float) lastTimestamp;
                            } else //val=(float) this.getColorScale()/128f * 0.1175f * (float)Math.log (500001f/(float)( nextframe[i][j][0]+1));
                            {
                                val = 0.2f * (float) Math.log((float) (200001 + this.getColorScale() * 1000) / (float) (nextframe[i][j][0] + 1));
                            }

                            //System.out.println("val: "+ val + " colorScale" + this.getColorScale());
                            int ind = getPixMapIndex(3 - i, j);
                            f[ind] = val;//(0.4f * val) + (0.6f * (float)nextframe[i][j][1]/(float)256);
                            f[ind + 1] = val;
                            f[ind + 2] = val; //(float)  (0.4 * val + 0.6* (float)nextframe[i][j][1]/(float)256);

                            ind = getPixMapIndex(0, 6);
                            if (face) {
                                f[ind] = 1;
                            } else {
                                f[ind] = 0;
                            }

                            ind = getPixMapIndex(2, 5);
                            if (FFL) {
                                f[ind + 1] = 1;
                            } else {
                                f[ind + 1] = 0;
                            }

                            ind = getPixMapIndex(2, 7);
                            if (FFR) {
                                f[ind + 1] = 1;
                            } else {
                                f[ind + 1] = 0;
                            }

                            if (nextframe[i][j][1] > colorMax) {
                                log.warning("color out of range " + nextframe[i][j][1]);
                            } else if (nextframe[i][j][1] < 0) {
                                log.warning("color out of range " + nextframe[i][j][1]);
                            }

                            ind = getPixMapIndex(3 - i, j + 8);
                            f[ind] = (float) nextframe[i][j][1] / (float) colorMax;
                            f[ind + 1] = (float) nextframe[i][j][1] / (float) colorMax;
                            f[ind + 2] = (float) nextframe[i][j][1] / (float) colorMax;
                        }
                    }

                    this.firstSpike = false;
                } else {
                    tt = e.getTimestamp();

                    nextframe[e.y][e.x][0] = tt - frameStart;
                    nextframe[e.y][e.x][1] = e.color - colorMin;

                    this.face = (0x04 & e.type) > 0;
                    this.FFR = (0x02 & e.type) > 0;
                    this.FFL = (0x01 & e.type) > 0;

                    //  log.info("type "  +e.type);
                    // log.info("color " + nextframe[e.y][e.x][1]);

                    if (!this.firstSpike) {
                        firstTimestamp = tt - frameStart;
                        this.firstSpike = true;
                    }

                    lastTimestamp = tt;

//                        if (e.color > colorMax)
//                        {
//                            colorMax=e.color;
//                            log.info("TypeMax = " + colorMax);
//                        }
//                        if (e.color < colorMin)
//                        {
//                            colorMin=e.color;
//                            log.info("TypeMin = " + colorMin);
//                        }
                //  System.out.println("x " + e.x + " y " + e.y + " type" + (e.type+128) );

                }
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            log.warning(e.getCause() + ": ChipRenderer.render(), some event out of bounds for this chip type?");
        }
    }
}
