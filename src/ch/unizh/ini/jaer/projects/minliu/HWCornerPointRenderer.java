/*
 * Copyright (C) 2019 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.minliu;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.HashHeatNoiseFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;

/**
 * To show corners points from FPGA EFAST corner point detector
 *
 * @author Tobi/Min
 */
@Description("<html>This is the a viwer for demo FPGA eFAST as demonstrated in CVPR2019 EventVision Workshop. The java version of eFAST is also implemented here.<br>"
        + "Liu, M., and Kao, W., and Delbruck, T. (2019). <a href=\"http://openaccess.thecvf.com/content_CVPRW_2019/papers/EventVision/Liu_Live_Demonstration_A_Real-Time_Event-Based_Fast_Corner_Detection_Demo_Based_CVPRW_2019_paper.pdf\">Live Demonstration: A Real-Time Event-Based Fast Corner Detection Demo Based on FPGA</a>.<br>.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class HWCornerPointRenderer extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    private ArrayList<BasicEvent> cornerEvents = new ArrayList(1000);
    private ArrayList<BasicEvent> cornerEventsCopy = new ArrayList(1000);   // Backup of cornerEvnets array.
    
    private double[][][] sae_ = null;
    private static final int INNER_SIZE = 16;
    private static final int OUTER_SIZE = 20;

    private static final int circle3_[][] = {{0, 3}, {1, 3}, {2, 2}, {3, 1},
    {3, 0}, {3, -1}, {2, -2}, {1, -3},
    {0, -3}, {-1, -3}, {-2, -2}, {-3, -1},
    {-3, 0}, {-3, 1}, {-2, 2}, {-1, 3}};
    private static final int circle4_[][] = {{0, 4}, {1, 4}, {2, 3}, {3, 2},
    {4, 1}, {4, 0}, {4, -1}, {3, -2},
    {2, -3}, {1, -4}, {0, -4}, {-1, -4},
    {-2, -3}, {-3, -2}, {-4, -1}, {-4, 0},
    {-4, 1}, {-3, 2}, {-2, 3}, {-1, 4}};

    private boolean enFilterOut = getBoolean("enFilterOut", false);
    private boolean enCompareSWandHW = getBoolean("enFilterOut", false);
    private HashHeatNoiseFilter HashHeatNoiseFilterInst = null;

    private int[][] cornerSlice = null;
    private boolean enShowOriginalCorners = getBoolean("enShowOriginalCorners", false);
    private boolean enDeoiseCorners = getBoolean("enDeoiseCorners", false);
    private int threshold = getInt("threshold", 10);

    private int subsample = getInt("subsample", 3);

    private int evCounter = 0;
    private int evCounterThreshold = getInt("evCounterThreshold", 1000);
    private boolean addPropertyChangeListener = false;

    public enum CalcMethod {
        HW_EFAST, SW_EFAST
    };
    private CalcMethod calcMethod = CalcMethod.valueOf(getString("sliceMethod", CalcMethod.SW_EFAST.toString()));

    public HWCornerPointRenderer(AEChip chip) {
        super(chip);
        sae_ = new double[2][500][500];
        cornerSlice = new int[256][256];

//        HashHeatNoiseFilterInst = new HashHeatNoiseFilter(chip);        
//        FilterChain chain = new FilterChain(chip);
//        chain.add(HashHeatNoiseFilterInst);
//        setEnclosedFilterChain(chain);
        String strBasic = "0a: General configuration";
        setPropertyTooltip(strBasic, "calcMethod", "method for getting keypoints; software or by rendering hardware events");
        setPropertyTooltip(strBasic, "enFilterOut", "enable to filter out DVS events and only pass keypoint events");
        setPropertyTooltip(strBasic, "enCompareSWandHW", "enable to compare software and hardware keypoints");
        
        String strDenoise = "0b: Block matching display";
        setPropertyTooltip(strDenoise, "enDeoiseCorners", "enable to denoise the corners");
        setPropertyTooltip(strDenoise, "enShowOriginalCorners", "enable to show the original corners before denoise");   
        setPropertyTooltip(strDenoise, "subsample", "subsample value to determine the area for non-maximum supression");
        setPropertyTooltip(strDenoise, "threshold", "threshold for non-maximum supression");           
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        cornerEvents.clear();
        int wrongCornerNum = 0;
        int falseNegativeNum = 0;
        int falsePositiveNum = 0;

        // Bind property change events. This code should be put after AEViewer is constructed and only bind one time.
        if(chip.getAeViewer() != null && !addPropertyChangeListener)
        {
            addPropertyChangeListener = true;
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);                                
        }
        
        for(int xIdx = 0; xIdx < 100; xIdx++)
        {
            for(int yIdx = 0; yIdx < 100; yIdx++)
            {
                cornerSlice[xIdx][yIdx] = 0;
            }
        }
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }
        
        while (i.hasNext()) {
            Object o = i.next();
            if (o == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
            if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent) o).isApsData()) {
                continue;
            }
 
            PolarityEvent ein = (PolarityEvent) o;
            
            int swCornerRet = 0;          
            if (isEnCompareSWandHW() || getCalcMethod() == CalcMethod.SW_EFAST) {
                swCornerRet = FastDetectorisFeature(ein) ? 1 : 0;
                if(swCornerRet == 1)
                {
                    ein.setAddress(ein.getAddress()|1); // set LSB hack since when event is DVS, then bits 0-9 are zero. TODO messes up APS samples                    
                }
            }
            
            int hwCornerRet = (ein.getAddress() & 1);
            if (isEnCompareSWandHW()) {
                if (hwCornerRet != swCornerRet) {
                    wrongCornerNum++;
                }

                if (swCornerRet == 1 && hwCornerRet == 0) {
                    falseNegativeNum++;
                    //                log.info(String.format("This event is (%d, %d)", e.x, e.y));
                }
                if (hwCornerRet == 1 && swCornerRet == 0) {
                    falsePositiveNum++;
                }
            }
            if (getCalcMethod() == CalcMethod.SW_EFAST) {
                if (swCornerRet == 0) {
                    if (enFilterOut) {
                        ein.setFilteredOut(true);
                    } else {
                        ein.setFilteredOut(false);
                    }
                } else {
                    // corner event
                    cornerEvents.add(ein);
                    cornerEventsCopy.add(ein);
                }
            } else {
                if ((hwCornerRet) == 0) {
                    if (enFilterOut) {
                        ein.setFilteredOut(true);
                    } else {
                        ein.setFilteredOut(false);
                    }
                } else {
                    // corner event
                    cornerEvents.add(ein);
                    cornerEventsCopy.add(ein);
                }
            }
        }
        
        // Remove outlier corners
        if(enDeoiseCorners)
        {
            for(int cornerCnt = 0; cornerCnt < cornerEvents.size(); cornerCnt++)
            {
                int cornerX = cornerEvents.get(cornerCnt).x;
                int cornerY = cornerEvents.get(cornerCnt).y;
                cornerSlice[cornerX >> subsample][cornerY >> subsample] += 1;
            }               

            if (in instanceof ApsDvsEventPacket) {
                i = ((ApsDvsEventPacket) in).fullIterator();
            } else {
                i = ((EventPacket) in).inputIterator();
            }
            while(i.hasNext())
            {
                Object o = i.next();
                if (o == null) {
                    log.warning("null event passed in, returning input packet");
                    return in;
                }
                if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent) o).isApsData()) {
                    continue;
                }

                PolarityEvent ein = (PolarityEvent) o;

                if(ein.timestamp == 295903198)
                {
                    int tmp = 1;
                }  
                            
                if(cornerSlice[(ein.x >> subsample)][(ein.y >> subsample)] < threshold || !cornerEvents.contains(ein))
                {
                    if(cornerEvents.contains(ein))
                    {
                        cornerEvents.remove(ein);                        
                    }
                    if (enFilterOut && !enShowOriginalCorners) {
                        ein.setFilteredOut(true);
                    } else {
                        ein.setFilteredOut(false);
                    }      
                    ein.setAddress(ein.getAddress()&0);
                }
                else
                {
                    ein.setAddress(ein.getAddress()|1);
                }
            }           
        }



            
//        HashHeatNoiseFilterInst.setEventCountWindow(cornerEvents.size());
//        in = getEnclosedFilterChain().filterPacket(in);
         
        if (isEnCompareSWandHW()) {
            if (wrongCornerNum != 0) {
                //            log.warning(String.format("The packet contained %d events and detected %d FPN corners and %d FNN corners.", in.getSize(), falsePositiveNum, falseNegativeNum));            
                //            log.warning(String.format("The sw detected %d corners and the hw detected %d corners.", swCornerRet, hwCornerRet));
            }
        }

        return in;
    }

    @Override
    public void resetFilter() {
//        sae_ = new double[2][500][500];
        if (sae_ == null) {
            return;  // on reset maybe chip is not set yet
        }

        for (double[][] b : sae_) {
            for (double[] row : b) {
                Arrays.fill(row, (double) 0);
            }
        }
    }

    @Override
    public void initFilter() {
        sae_ = new double[2][500][500];
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        try {
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_CONSTANT_ALPHA);
            gl.glBlendEquation(GL2.GL_FUNC_ADD); // use additive color here to just brighten the pixels already there
            gl.glBlendColor(1, 1, 1, 1);
        } catch (final GLException e) {
            e.printStackTrace();
        }
        gl.glColor4f(1f, 0, 0, 1.0f);
        for (BasicEvent e : cornerEvents) {
            gl.glPushMatrix();
            DrawGL.drawBox(gl, e.x, e.y, 4, 4, 0);
            gl.glPopMatrix();
        }
        gl.glDisable(GL2.GL_BLEND);
    }

    boolean FastDetectorisFeature(PolarityEvent ein) {
        boolean found_streak = false;

        int pix_x = ein.x;
        int pix_y = ein.y;
        int timesmp = ein.timestamp;
        Polarity polarity = ein.polarity;
//        if (polarity.equals(Polarity.Off)) {
//            found_streak = false;
//            return found_streak;
//        }

        final int max_scale = 1;
        // only check if not too close to border
        final int cs = max_scale * 6;
        if (pix_x < cs || pix_x >= this.getChip().getSizeX() - cs - 4
                || pix_y < cs || pix_y >= this.getChip().getSizeY() - cs - 4) {
            found_streak = false;
            return found_streak;
        }

        final int pol = polarity.equals(Polarity.Off) ? 0 : 1;

        // update SAE
        sae_[pol][pix_x][pix_y] = timesmp;

        found_streak = false;

        isFeatureOutterLoop:
        for (int i = 0; i < 16; i++) {
            FastDetectorisFeature_label2:
            for (int streak_size = 3; streak_size <= 6; streak_size++) {
                // check that streak event is larger than neighbor
                if ((sae_[pol][pix_x + circle3_[i][0]][pix_y + circle3_[i][1]]) < (sae_[pol][pix_x + circle3_[(i - 1 + 16) % 16][0]][pix_y + circle3_[(i - 1 + 16) % 16][1]])) {
                    continue;
                }

                // check that streak event is larger than neighbor
                if (sae_[pol][pix_x + circle3_[(i + streak_size - 1) % 16][0]][pix_y + circle3_[(i + streak_size - 1) % 16][1]] < sae_[pol][pix_x + circle3_[(i + streak_size) % 16][0]][pix_y + circle3_[(i + streak_size) % 16][1]]) {
                    continue;
                }

                // find the smallest timestamp in corner min_t
                double min_t = sae_[pol][pix_x + circle3_[i][0]][pix_y + circle3_[i][1]];
                FastDetectorisFeature_label1:
                for (int j = 1; j < streak_size; j++) {
                    final double tj = sae_[pol][pix_x + circle3_[(i + j) % 16][0]][pix_y + circle3_[(i + j) % 16][1]];
                    if (tj < min_t) {
                        min_t = tj;
                    }
                }

                //check if corner timestamp is higher than corner
                boolean did_break = false;
                FastDetectorisFeature_label0:
                for (int j = streak_size; j < 16; j++) {
                    final double tj = sae_[pol][pix_x + circle3_[(i + j) % 16][0]][pix_y + circle3_[(i + j) % 16][1]];

                    if (tj >= min_t) {
                        did_break = true;
                        break;
                    }
                }

                if (!did_break) {
                    found_streak = true;
                    break;
                }
            }

            if (found_streak) {
                break;
            }
        }

        if (found_streak) {
            found_streak = false;

            FastDetectorisFeature_label6:
            for (int i = 0; i < 20; i++) {
                FastDetectorisFeature_label5:
                for (int streak_size = 4; streak_size <= 8; streak_size++) {
                    // check that first event is larger than neighbor
                    if (sae_[pol][pix_x + circle4_[i][0]][pix_y + circle4_[i][1]] < sae_[pol][pix_x + circle4_[(i - 1 + 20) % 20][0]][pix_y + circle4_[(i - 1 + 20) % 20][1]]) {
                        continue;
                    }

                    // check that streak event is larger than neighbor
                    if (sae_[pol][pix_x + circle4_[(i + streak_size - 1) % 20][0]][pix_y + circle4_[(i + streak_size - 1) % 20][1]] < sae_[pol][pix_x + circle4_[(i + streak_size) % 20][0]][pix_y + circle4_[(i + streak_size) % 20][1]]) {
                        continue;
                    }

                    double min_t = sae_[pol][pix_x + circle4_[i][0]][pix_y + circle4_[i][1]];
                    FastDetectorisFeature_label4:
                    for (int j = 1; j < streak_size; j++) {
                        final double tj = sae_[pol][pix_x + circle4_[(i + j) % 20][0]][pix_y + circle4_[(i + j) % 20][1]];
                        if (tj < min_t) {
                            min_t = tj;
                        }
                    }

                    boolean did_break = false;
                    FastDetectorisFeature_label3:
                    for (int j = streak_size; j < 20; j++) {
                        final double tj = sae_[pol][pix_x + circle4_[(i + j) % 20][0]][pix_y + circle4_[(i + j) % 20][1]];
                        if (tj >= min_t) {
                            did_break = true;
                            break;
                        }
                    }

                    if (!did_break) {
                        found_streak = true;
                        break;
                    }
                }
                if (found_streak) {
                    break;
                }
            }

        }

        return found_streak;
    }

    public CalcMethod getCalcMethod() {
        return calcMethod;
    }

    public void setCalcMethod(CalcMethod calcMethod) {
        CalcMethod old = this.calcMethod;
        this.calcMethod = calcMethod;
        putString("calcMethod", calcMethod.toString());
        getSupport().firePropertyChange("calcMethod", old, this.calcMethod);
    }
    
    public boolean isEnShowOriginalCorners() {
        return enShowOriginalCorners;
    }

    public void setEnShowOriginalCorners(boolean enShowOriginalCorners) {
        boolean old = this.enShowOriginalCorners;
        this.enShowOriginalCorners = enShowOriginalCorners;           
        putBoolean("enShowOriginalCorners", enShowOriginalCorners);
        getSupport().firePropertyChange("enShowOriginalCorners", old, this.enShowOriginalCorners);
    }

    public boolean isEnDeoiseCorners() {
        return enDeoiseCorners;
    }

    public void setEnDeoiseCorners(boolean enDeoiseCorners) {
        if(enDeoiseCorners == false)
        {
            this.setEnShowOriginalCorners(false);
        }
        this.enDeoiseCorners = enDeoiseCorners;
        putBoolean("enDeoiseCorners", enDeoiseCorners);
    }

    public boolean isEnCompareSWandHW() {
        return enCompareSWandHW;
    }

    public void setEnCompareSWandHW(boolean enCompareSWandHW) {
        this.enCompareSWandHW = enCompareSWandHW;
        putBoolean("enCompareSWandHW", enCompareSWandHW);
    }

    public boolean isEnFilterOut() {
        return enFilterOut;
    }

    public void setEnFilterOut(boolean enFilterOut) {
        this.enFilterOut = enFilterOut;
        putBoolean("enFilterOut", enFilterOut);
    }


    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        int old = this.threshold;
        this.threshold = threshold;    
        putInt("threshold", threshold);
        getSupport().firePropertyChange("threshold", old, this.threshold);
    }
    
    public int getSubsample() {
        return subsample;
    }

    public void setSubsample(int subsample) {
        int old = this.subsample;
        this.subsample = subsample;
        putInt("sliceMaxValue", subsample);
        getSupport().firePropertyChange("subsample", old, this.subsample); 
    }

    public int getEvCounterThreshold() {
        return evCounterThreshold;
    }

    public void setEvCounterThreshold(int evCounterThreshold) {
        int old = this.evCounterThreshold;
        this.evCounterThreshold = evCounterThreshold;
        putInt("evCounterThreshold", evCounterThreshold);
        getSupport().firePropertyChange("evCounterThreshold", old, this.evCounterThreshold); 
    }
        
}
