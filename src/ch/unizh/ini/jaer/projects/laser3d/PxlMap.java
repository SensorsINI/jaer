/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d;

import java.util.Arrays;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 *
 * @author Thomas
 */
public class PxlMap {

    private int DEFAULT_TIMESTAMP = 0; //Integer.MIN_VALUE;
    private int BUFFER_SIZE = 1000;
    private int mapSizeX;
    private int mapSizeY;
    private int frameNo;
    private int[][] lastPxlActivityIndex;
    private int[][][] pxlActivity;
    private TrackLaserLine filter;

    /**
     * Creates a new instance of PxlMap
     *
     * @param sx width of the map in pixels
     * @param sy heigth of the map in pixels
     * @param filter
     */
    public PxlMap(int sx, int sy, TrackLaserLine filter) {
        this.filter = filter;
        this.mapSizeX = sx;
        this.mapSizeY = sy;
    }

    /**
     * sets the score of each pixel to 0 and resets all saved timestamps
     */
    public final void resetMap() {
        if (lastPxlActivityIndex != null) {
            for (int x = 0; x < mapSizeX; x++) {
                Arrays.fill(lastPxlActivityIndex[x], -1);
                for (int y = 0; y < mapSizeY; y++) {
                    Arrays.fill(pxlActivity[x][y], 0);
                }
            }
        }
        frameNo = 0;
    }

    /**
     * initializes PxlMap (allocates memory)
     */
    public void initMap() {
        allocateMaps();
        resetMap();
    }

    private void allocateMaps() {
        if (mapSizeX > 0 & mapSizeY > 0) {
            pxlActivity = new int[mapSizeX][mapSizeY][BUFFER_SIZE];
            lastPxlActivityIndex = new int[mapSizeX][mapSizeY];
        }
    }

    /**
     * Updates pxlScores using the
     *
     * @see(PolarityEvent) ev
     * @param ev Event to score
     */
    public void processEvent(BasicEvent ev) {
        if (ev.special) {
            frameNo++; // since max(int) = 2^31-1, this should not be a problems
        } else {
            // Update activityMap
            updateMaps(ev);
        }
    }

    private void updateMaps(BasicEvent ev) {
        if (ev.x >= 0 & ev.x < mapSizeX & ev.y >= 0 & ev.y < mapSizeY) {
            if (lastPxlActivityIndex[ev.x][ev.y] == BUFFER_SIZE-1) {
                reWritePxlActivityMap(ev.x,ev.y);
            }  
            lastPxlActivityIndex[ev.x][ev.y]++;
            pxlActivity[ev.x][ev.y][lastPxlActivityIndex[ev.x][ev.y]] = frameNo;
       
          
        }        
    }

    public int getPxlActivity(short x, short y) {
        if (x < 0 | x >= mapSizeX | y < 0 | y >= mapSizeY) {
            filter.log.warning("getPxlActivity called for pixel out of range");
            return 0;
        }
        int activityCount = 0;
        for (int i = lastPxlActivityIndex[x][y]; i >= 0; i--) {
            if (frameNo - pxlActivity[x][y][i] < filter.getWindowSize()) {
                activityCount++;
            } else {
                i = 0;
            }
        }
        return activityCount;
    }

    private void reWritePxlActivityMap(short x, short y) {
        int j = 0;
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (frameNo - pxlActivity[x][y][i] < filter.getWindowSize()) {
                pxlActivity[x][y][j] = pxlActivity[x][y][i];
                j++;
            }
        }
        lastPxlActivityIndex[x][y] = j - 1;

        // write 0 into array (should not be necessary)
//        while (j < BUFFER_SIZE) {
//            pxlActivity[x][y][j] = 0;
//            j++;
//        }
    }

    /**
     *
     * @return
     */
    public int getMapSizeX() {
        return mapSizeX;
    }

    /**
     *
     * @return
     */
    public int getMapSizeY() {
        return mapSizeY;
    }


}
