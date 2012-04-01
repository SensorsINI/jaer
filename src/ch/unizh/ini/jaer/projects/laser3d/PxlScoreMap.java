/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.laser3d;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * PxlScoreMap holds a score for each pixel.
 *
 * @author Thomas
 */
public class PxlScoreMap {
    
    private int mapSizeX;
    private int mapSizeY;
    private double sumOfScores;
    // pxlScore is moving average over historySize scores
    private double[][] pxlScore;
    private double[][][] pxlScoreHistory;
    private FilterLaserline filter;
    private int historySize;

    /**
     * Creates a new instance of PxlMap
     *
     * @param sx width of the map in pixels
     * @param sy heigth of the map in pixels
     * @param filter invoking EventFilter2D for loging
     */
    public PxlScoreMap(int sx, int sy, FilterLaserline filter) {
        this.filter = filter;
        this.mapSizeX = sx;
        this.mapSizeY = sy;
        this.historySize = filter.getPrefs().getInt("FilterLaserline.pxlScoreHistorySize", 5);
    }

    /**
     * sets the score of each pixel to 0
     */
    public final void resetMap() {
        if (pxlScore != null) {
            for (int x = 0; x < mapSizeX; x++) {
                Arrays.fill(pxlScore[x], 0d);
                for (int y = 0; y < mapSizeY; y++) {
                    Arrays.fill(pxlScoreHistory[x][y],0d);
                }
            }
        }
        sumOfScores = 0;
    }

    /**
     * initializes PxlScoreMap (allocates memory)
     */
    public void initMap() {
        allocateMaps();
        resetMap();
    }

    private void allocateMaps() {
        if (mapSizeX > 0 & mapSizeY > 0) {
            pxlScore = new double[mapSizeX][mapSizeY];
            pxlScoreHistory = new double[mapSizeX][mapSizeY][historySize+1];
        }
    }
    
    /**
     * Get score of specific pixel
     * @param x
     * @param y
     * @return score of pixel (x,y)
     */
    public double getScore(short x, short y) {
        if (x >= 0 & x <= mapSizeX & y >= 0 & y <= mapSizeY) {
            return pxlScore[x][y];
        } else {
            filter.log.warning("PxlScoreMap.getScore(): pixel not on chip!");
        }
        return 0;
    }

    /**
     * set score of pixel (x,y).
     * 
     * probably #addToSCore is better choice
     * @param x
     * @param y
     * @param score
     */
    public void setScore(short x, short y, double score) {
        if (x >= 0 & x <= mapSizeX & y >= 0 & y <= mapSizeY) {
            pxlScoreHistory[x][y][0] = score;
        } else {
            filter.log.warning("PxlScoreMap.setScore(): pixel not on chip!");
        }
    }
    
    /**
     * update score 
     * @param x
     * @param y
     * @param score
     */
    public void addToScore(short x, short y, double score) {
        if (x >= 0 & x <= mapSizeX & y >= 0 & y <= mapSizeY) {
            pxlScoreHistory[x][y][0] += score;
        } else {
            filter.log.warning("PxlScoreMap.addToScore(): pixel not on chip!");
        }
    }
    
    /**
     * 
     */
    public void updatePxlScore() {
        // apply moving average on score history
        sumOfScores = 0;
        for (short x = 0; x < mapSizeX; x++) {
            for (short y = 0; y < mapSizeY; y++) {
                if (historySize > 0) {
                    pxlScore[x][y] -= pxlScoreHistory[x][y][historySize]/historySize;
                    pxlScore[x][y] += pxlScoreHistory[x][y][0]/historySize;
                    /* shift history */
                    for (int i = historySize; i > 0; i--) {
                        pxlScoreHistory[x][y][i] = pxlScoreHistory[x][y][i-1];
                    }
                } else {                
                    pxlScore[x][y] = pxlScoreHistory[x][y][0];
                }
                // dump small scores
                if (Math.abs(pxlScore[x][y]) < 1e-3) pxlScore[x][y] = 0;
                /* reset pxlScore */
                pxlScoreHistory[x][y][0] = 0;
                sumOfScores += pxlScore[x][y];
//                if (Double.isNaN(pxlScore[x][y])) {
//                    filter.log.info("NaN!");
//                }
            }
        }
    }
    

    /*
     * Update laserline
     * rewrites the arraylist with updated pixels classified as on pixel
     * 
     * @param laserline
     * 
     * @return laserline
     */
    ArrayList updateLaserline(ArrayList laserline) {
        laserline.clear();
        double threshold = findThreshold();
        for (short x = 0; x < mapSizeX; x++) {
            for (short y = 0; y < mapSizeY; y++) {
                float[] pxl;
                float sumScores = 0;
                float sumWeightedCoord = 0;
                while (pxlScore[x][y] > threshold)  {
                    sumWeightedCoord += (y*pxlScore[x][y]);
                    sumScores += pxlScore[x][y];
                    y++;
                    if (y >= mapSizeY) {
                        break;  
                    }
                }
                if (sumScores > 0) {
                    pxl = new float[2];
                    pxl[0] = x;
                    pxl[1] = sumWeightedCoord/sumScores;
                    laserline.add(pxl);
                }
            }
        }
        return laserline;
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
    
    /**
     * 
     * @return
     */
    public double findThreshold() {
        double threshold = filter.getPxlScoreThreshold();
        
//        double[] allScores = new double[mapSizeX*mapSizeY];
//        int i = 0;
//        for (short x = 0; x < mapSizeX; x++) {
//            for (short y = 0; y < mapSizeY; y++) {
//                allScores[i] = pxlScore[x][y];
//                i++;
//            }
//        }
//        Arrays.sort(allScores);
        
        return threshold;
    }
}
