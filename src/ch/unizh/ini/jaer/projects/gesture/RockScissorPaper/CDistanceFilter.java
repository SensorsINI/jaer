/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;
/*
 * &&From

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import weka.core.Instance;
 * 
 * &&To
 */

/**
 *
 * @author Eun Yeong Ahn
 */
public class CDistanceFilter {
    /*
     * &&From

    ArrayList[] discreteData;
    double[] distData;
    double[] avgData;
    EventPacket<?> hand = new EventPacket();
    EventPacket<?> finger = new EventPacket();
    EBaseAxis m_base;
    int binSize = 0;

     public void Filter(EventPacket<?> in, EBaseAxis base, int bin){
        //discretization
         binSize = bin;
        C1DDiscrete discretization = new C1DDiscrete();
        m_base = base;

        if(m_base == EBaseAxis.Y)
            discretization.Discretize(in, EBaseAxis.X, bin);
        else if(m_base == EBaseAxis.X)
            discretization.Discretize(in, EBaseAxis.Y, bin);

        discreteData = discretization.GetDiscreteData();
        distData = new double[bin];

        double sum = 0;
     
        for(int i = 0; i < bin; i++){
            // Filter within a bin
            //distData[i] = GetDistByKMeans(tmpList);
            //distData[i] = GetDist(tmpList);
            if(discreteData[i] == null || discreteData[i].size() == 0){
                distData[i] = 0;
            }else{
                ArrayList<BasicEvent> tmpList = (ArrayList<BasicEvent>) discreteData[i].clone();
                distData[i] = GetStd(tmpList);
            }

            sum = sum + distData[i];
        }

        //interpolation
        for(int i = 0; i < bin; i++){
            if(discreteData[i] == null || discreteData[i].size() == 0){
                double up = 0, down = 0;
                 for(int j = i+1; j < bin; j++){
                    if(discreteData[j] != null && discreteData[j].size() != 0){
                        //distData[i] = Math.floor(distData[j]);
                        up = distData[j];
                        break;
                    }
                }
                for(int j = i; j >= 0; j--){
                    if(discreteData[j] != null && discreteData[j].size() != 0){
                        //distData[i] = Math.floor(distData[j]);
                        down = distData[j];
                        break;
                    }
                }
                distData[i] = Math.floor((up+down)/2);
            }
        }

        //Normalize
        for(int i = 0; i < bin; i++){
            distData[i] = distData[i]/ sum;
        }
     }

     //**************************************************************************
     // * Remove wrist
     // *************************************************************************
     public void HandDiscretization(EventPacket<?> in, EBaseAxis base, int bin, int decreaseThreshold, boolean dualDirection){
         binSize = bin;
         ArrayList inPacket = new ArrayList<BasicEvent>();
         for(BasicEvent ev:in){
            inPacket.add(ev);
         }
        //discretization
        C1DDiscrete discretization = new C1DDiscrete();
        m_base = base;

        if(m_base == EBaseAxis.Y)
            discretization.Discretize(in, EBaseAxis.X, bin);
        else if(m_base == EBaseAxis.X)
            discretization.Discretize(in, EBaseAxis.Y, bin);

        discreteData = discretization.GetDiscreteData();
        ArrayList[] slidingData = discretization.GetSlidingData();  //for sliding window
        distData = new double[bin];

        // Filtering & get distance
        for(int i = 0; i < bin; i++){
            //distData[i] = GetDistByKMeans(tmpList);
            //distData[i] = GetDist(tmpList);
            if(discreteData[i] == null || discreteData[i].size() == 0)
                distData[i] = 0;
            else
                distData[i] = GetStd((ArrayList<BasicEvent>) slidingData[i].clone());//for sliding window
                //distData[i] = GetStd((ArrayList<BasicEvent>) discreteData[i].clone());
        }

        //interpolation
        for(int i = 0; i < bin; i++){
            if(discreteData[i] == null || discreteData[i].size() == 0){
                double up = 0, down = 0;
                for(int j = i+1; j < bin; j++){
                    if(discreteData[j] != null && discreteData[j].size() != 0){
                        //distData[i] = Math.floor(distData[j]);
                        up = distData[j];
                        break;
                    }
                }
                for(int j = i; j >= 0; j--){
                    if(discreteData[j] != null && discreteData[j].size() != 0){
                        //distData[i] = Math.floor(distData[j]);
                        down = distData[j];
                        break;
                    }
                }
                distData[i] = Math.floor((up+down)/2);
            }
        }

        //######################################################################
        // Find wrist
        int turningPoint = -1;
        double preDistData = -1;
        int increaseCount= 0;
        int r2lTurningPoint = bin-1;
        //right -> left
        for(int i = bin-1 ; i >= 0 ; i--){
            if(preDistData > distData[i]){
                increaseCount = 0;
            }else{
                increaseCount++;
            }
            preDistData = distData[i];

            if(increaseCount == decreaseThreshold){
                r2lTurningPoint = i;
                break;
            }
        }
        
        //left -> right
        //(1) find max
        int maxIdx = -1; double max = -1;
        for(int i = 0; i < bin ; i++){
            if(max < distData[i]){
                max = distData[i];
                maxIdx = i;
            }
        }

        if(dualDirection){
            preDistData = -1;
            int l2rTurningPoint = 0, decreaseCount = 0;
            for(int i = 0; i < bin; i++){
                if(preDistData < distData[i]){
                    decreaseCount = 0;
                }else{
                    decreaseCount++;
                }
                preDistData = distData[i];

                if(decreaseCount == decreaseThreshold){
                    l2rTurningPoint = i;
                    break;
                }
            }
            turningPoint = (int) Math.floor((r2lTurningPoint + l2rTurningPoint)/2);
        }
        else
            turningPoint = r2lTurningPoint;
        
       // System.out.println(l2rTurningPoint+ "-" + r2lTurningPoint + "-" + turningPoint);
        ArrayList handPacketArray = new ArrayList<BasicEvent>();
        ArrayList fingerPacketArray = new ArrayList<BasicEvent>();
         for(int i = 0; i <= turningPoint; i++){
            handPacketArray.addAll((ArrayList<BasicEvent>) discreteData[i].clone());

            if(i < maxIdx)
                fingerPacketArray.addAll((ArrayList<BasicEvent>) discreteData[i].clone());
        }

        Array2EventPacket convertor = new Array2EventPacket();
        hand = convertor.Convert(handPacketArray);
        finger = convertor.Convert(fingerPacketArray);
     }

     EventPacket<?> GetHand(){
         return hand;
     }
     EventPacket<?> GetFinger(){
         return finger;
     }

     //***************************************************************************
     //* Getting distance
     // * (1) max - min
     // * (2) variance
     // * (3) clustering
     // * (4) averaging data within the each tails (10%), and obtain the difference
     //***************************************************************************

    private double GetDist(ArrayList<BasicEvent> arrayList) {
        double max = -1, min = -1;

        Iterator iter = arrayList.iterator();
        if(iter.hasNext()){
            BasicEvent ev= (BasicEvent) iter.next();
            if(m_base == EBaseAxis.Y)
                max = ev.y;
            else if(m_base == EBaseAxis.X)
                max = ev.x;
            min = max;
        }

        while(iter.hasNext()){
            BasicEvent ev= (BasicEvent) iter.next();
            if(m_base == EBaseAxis.Y){
                if(max < ev.y) max = ev.y;
                if(min > ev.y) min = ev.y;
            }else if(m_base == EBaseAxis.X){
                if(max < ev.x) max = ev.x;
                if(min > ev.x) min = ev.x;
            }

        }
        return max - min;
    }

    private double GetStd(ArrayList<BasicEvent> arrayList) {
        Iterator iter = arrayList.iterator();
        int n = arrayList.size();
        double sum = 0;
        double sqrSum = 0;

        while(iter.hasNext()){
            BasicEvent ev= (BasicEvent) iter.next();
            double tmp = 0;
            if(m_base == EBaseAxis.Y)
                tmp = ev.y;
            else if(m_base == EBaseAxis.X)
                tmp = ev.x;

            sum = sum + tmp;
            sqrSum = sqrSum + Math.pow(tmp, 2);
        }

        double var = sqrSum/n - Math.pow(sum/n, 2);

        return Math.sqrt(var);
    }

    private double GetAvg(ArrayList<BasicEvent> arrayList) {
        double sum = 0;
        Iterator iter = arrayList.iterator();
        while(iter.hasNext()){
            BasicEvent ev= (BasicEvent) iter.next();
            if(m_base == EBaseAxis.Y)
                sum = sum + ev.y;
            else if(m_base == EBaseAxis.X)
                sum = sum + ev.x;
        }

        return sum/arrayList.size();
    }

    private double GetDistByKMeans(ArrayList<BasicEvent> arrayList){
        double dist = 0;
        int idx = 0;
        double[] data = new double[arrayList.size()];
        Iterator iter = arrayList.iterator();
        while(iter.hasNext()){
            BasicEvent ev = (BasicEvent) iter.next();
            if(m_base == EBaseAxis.X){
                data[idx++] = ev.x;
            }else if(m_base == EBaseAxis.Y){
                data[idx++] = ev.y;
            }
        }

        CKMeans1D kmeans = new CKMeans1D();
        double[] centroids = kmeans.KMeans(data, 2);
        dist = Math.abs(centroids[0] - centroids[1]);
        System.out.println(centroids[0] + "," + centroids[1]);
        return dist;
    }



    Instance GetInstance(int cLabel){
        double[] newvals = new double[CParameter.YDistFilterBin + 1];
        int idx = 0;
        for(int i = 0; i< CParameter.YDistFilterBin; i++)
            newvals[idx++] = distData[i];

        newvals[idx++] = cLabel;
        Instance new_instance = new Instance(1.0, newvals);

        return new_instance;
    }

    double[] GetFeatures(){
        return distData;
    }

    //***************************************************************************
    // * For testing
    // ***************************************************************************

    void Print(){
        for(int i = 0; i< binSize; i ++){
            System.out.print(distData[i] + "   ");
        }
        System.out.println();
    }

    void PrintList(ArrayList<BasicEvent> list){
        try {
            String fname = "./data/test.txt";
            BufferedWriter writer = null;
            writer = new BufferedWriter(new FileWriter(fname));
            Iterator iter = list.iterator();
            while (iter.hasNext()) {
                BasicEvent ev = (BasicEvent) iter.next();
                writer.write(ev.x + "," + ev.y+"\n");
                writer.flush();
            }
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(CDistanceFilter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    * &&To
    */
}
