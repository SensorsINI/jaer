package ch.unizh.ini.jaer.projects.laser3d;

import java.util.Arrays;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * histogramData holds a history of data over a certain timeperiod and returns a
 * histogram of this accumulated data
 *
 * @author Thomas Mantel
 */
public class HistogramData {

    private int historySize = 1000;
    private int binSize = 20;
    private int nBins;
    private double maxVal;
    private int binIndx = 0;
    private double[] newData;
    private double[] dataBins;
    private double[][] dataHistory;
    private int sumOfBins;
    EventFilter2D filter;

    /**
     *
     * @param historySize 
     * @param binSize 
     * @param filter
     * @param nBins  
     */
    public HistogramData(int historySize, int binSize, int nBins, EventFilter2D filter) {
        this.filter = filter;
        this.historySize = historySize;
        this.binSize = binSize;
        this.nBins = nBins;
        initHistData();
    }

    /**
     *
     * @param x
     */
    public void addToData(double x) {
        if (isInitialized()) { // TODO catch if not initialized
            int curBin = (int) (x / binSize);

            // check bin to avoid nullpointer exception
            if (curBin >= nBins) {
//                filter.log.warning("bin number to big, omitting event");
            } else if (curBin < 0) {
//                filter.log.warning("bin number to small, omitting event");
            } else {
                // increment bincount
                newData[curBin]++;
            }
        }
    }

    /**
     *
     * @return
     */
    public boolean isInitialized() {
        if (dataBins != null & dataHistory != null) {
            return true;
        }
        return false;
    }

    /**
     *
     */
    public void resetHistData() {
        binIndx = 0;
        sumOfBins = 0;
        Arrays.fill(newData, 0);
        Arrays.fill(dataBins, 0);

        for (int i = 0; i < nBins; i++) {
            Arrays.fill(dataHistory[i], 0);
        }

    }

    /**
     *
     * @return
     */
    public double getSumOfBins() {
        return sumOfBins;
    }
    
    public double getMaxVal() {
        return maxVal;
    }

    /**
     * 
     */
    public void updateBins() {
        maxVal = 0;
        for (int i = 0; i < nBins; i++) {

            // special behaviour if history is full
            if (binIndx == historySize) {
                //                                                       0 1...n-1
                //                                                       -      
                // subtract events that are removed from history    e.g [7 5 3 1]
                dataBins[i] -= dataHistory[i][0];
                sumOfBins -= dataHistory[i][0];
                
                // rewrite history (shift everything) e.g. [7 5 3 1] -> [5 3 1 1]
                for (int j = 0; j < historySize-1; j++) {
                    dataHistory[i][j] = dataHistory[i][j+1];
                }
                //                                                             n-1
                //                                                       0 1...| n
                // write new data to history                       e.g. [5 3 1 2]
                dataHistory[i][binIndx-1] = newData[i];
            } else {
                //                                                       0.. n
                // write new data to history                       e.g. [7 5 3 0]
                dataHistory[i][binIndx] = newData[i];
                
                binIndx++;
            }
                

            // Add new data to dataBin
            dataBins[i] += newData[i];
            sumOfBins += newData[i];
            
            // reset newData
            newData[i] = 0;
            
            if (maxVal < dataBins[i]) {
                maxVal = dataBins[i];
            }
        }
    }

    private void initHistData() {
        newData = new double[nBins];
        dataBins = new double[nBins];
        dataHistory = new double[nBins][historySize];

        resetHistData();
    }
    
    double[] getNormalized() {
        return getNormalized(false);    
    }
    
    double[] getNormalized(boolean subtractAvg) {
        double[] normalizedData = null;
        normalizedData = getNormalized(normalizedData, subtractAvg);
        return normalizedData;             
    }
    
    double[] getNormalized(double[] d) {
        return getNormalized(d,false);
    }
    
    double[] getNormalized(double[] d, boolean subtractAvg) {
        if (sumOfBins == 0) {
            sumOfBins = 1; // avoid divsion by 0
        }
        double avg = sumOfBins/nBins;
        d = Arrays.copyOf(dataBins, nBins);
        for (int i = 0; i < nBins; i++) {
            if (subtractAvg) {
                d[i] -= avg;
            }
            d[i] /= sumOfBins;
        }
        return d;
    }
    
    public double[] getData(double[] d) {
        d = Arrays.copyOf(dataBins, nBins);
        return d;
    }

}
