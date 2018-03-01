/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;
import java.util.ArrayList;
import java.util.Iterator;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Eun Yeong Ahn
 */
public class C1DDiscrete {
    int trow, tcol;
    int max = -1, min = -1;
    ArrayList[] disData;
    ArrayList[] slidingData;
    int[] disDataCount;
    int noOfBins;

    //Packet Discretize
    void Discretize(EventPacket<?> in, EBaseAxis base, int bin){//row: # of element, col: # of features
        if(in.getSize() == 0){
            return;
        }

        noOfBins = bin;
        if(base == EBaseAxis.X)
            max = in.getFirstEvent().x;
        else if(base == EBaseAxis.Y)
            max = in.getFirstEvent().y;

        min = max;

         for(BasicEvent ev:in){
             if(base == EBaseAxis.X){
                 if(ev.x > max) max = ev.x;
                 if(ev.x < min) min = ev.x;
             }else if(base == EBaseAxis.Y){
                 if(ev.y > max) max = ev.y;
                 if(ev.y < min) min = ev.y;
             }
             
         }

         //interval
         double interval = (max-min) / noOfBins;
         double remainder = 0;

         disData = new ArrayList[noOfBins];
         slidingData = new ArrayList[noOfBins]; //for sliding window
         disDataCount = new int[noOfBins];
         for(int i = 0; i< noOfBins; i++){
             disData[i] = new ArrayList<BasicEvent>();
             slidingData[i] = new ArrayList<BasicEvent>();
             disDataCount[i] = 0;
         }


         for(BasicEvent ev:in){
             int idx = 0;
             if(base == EBaseAxis.X){
                 idx = (int) Math.floor((ev.x - min)/interval);
                 remainder = ev.x - min - (interval * idx);     //sliding window
             }else if(base == EBaseAxis.Y){
                 idx = (int) Math.floor((ev.y - min)/interval);
                 remainder = ev.y - min - (interval * idx);     //sliding window
             }

             if(idx >= noOfBins){
                 idx = noOfBins -1;
             }

             disData[idx].add(ev);

             //################################################################ Sliding window
             /* slinding window
              * can be added to more than two bins
              * redundant size = 1/3
              */
             slidingData[idx].add(ev);
             int sidx = (int) Math.floor(remainder/(interval/3));
             if(sidx == 0 && idx != 0)
                 slidingData[idx-1].add(ev);; //disData[idx-1].add(ev);
             if(sidx == 2 && idx < noOfBins-1)
                 slidingData[idx+1].add(ev);; //disData[idx+1].add(ev);
             //################################################################
         }
    }

    //List Discretize
    void FixedMaxMinDiscretize(ArrayList<BasicEvent> inList, EBaseAxis base, int bins){//row: # of element, col: # of features
        noOfBins = bins;
        max = 128; min = 0;
        double interval = (max-min) / noOfBins;

         disData = new ArrayList[noOfBins];
         for(int i = 0; i< noOfBins; i++){
             disData[i] = new ArrayList<BasicEvent>();
         }

         Iterator iter = inList.iterator();
         while(iter.hasNext()){
             BasicEvent ev = (BasicEvent) iter.next();
             int idx = 0;
             if(base == EBaseAxis.X){
                 idx = (int) Math.floor((ev.x - min)/interval);
             }else if(base == EBaseAxis.Y){
                 idx = (int) Math.floor((ev.y - min)/interval);
             }

             if(idx >= noOfBins){
                 idx = noOfBins -1;
             }

             disData[idx].add(ev);
         }
    }

    //List Discretize
    void DiscretizeSlidingWindow(ArrayList<BasicEvent> inList, EBaseAxis base, int bins){//row: # of element, col: # of features
        noOfBins = bins;
        max = 128; min = 0;
        double interval = (max-min) / noOfBins;

         disData = new ArrayList[noOfBins];
         disDataCount = new int[noOfBins];  //count
         for(int i = 0; i< noOfBins; i++){
             disData[i] = new ArrayList<BasicEvent>();
         }

         Iterator iter = inList.iterator();
         while(iter.hasNext()){
             BasicEvent ev = (BasicEvent) iter.next();
             int idx = 0;
             double remainder = 0;
             if(base == EBaseAxis.X){
                 idx = (int) Math.floor((ev.x - min)/interval);
                 remainder = ev.x - min - (interval * idx);     //sliding window
             }else if(base == EBaseAxis.Y){
                 idx = (int) Math.floor((ev.y - min)/interval);
                 remainder = ev.y - min - (interval * idx);     //sliding window
             }

             if(idx >= noOfBins){
                 idx = noOfBins -1;
             }

             disData[idx].add(ev);
             disDataCount[idx] = disDataCount[idx] + 1;

             //################################################################ Sliding window
             /* slinding window
              * can be added to more than two bins
              * redundant size = 1/3
              */
             int sidx = (int) Math.floor(remainder/(interval/3));
             if(sidx == 0 && idx != 0)
                 disDataCount[idx-1] = disDataCount[idx-1] + 1; //disData[idx-1].add(ev);
             if(sidx == 2 && idx < noOfBins-1)
                 disDataCount[idx+1] = disDataCount[idx+1] + 1; //disData[idx+1].add(ev);
             //################################################################

         }
    }

    void Print(){
        for(int i = 0; i < noOfBins; i++){
            Iterator iter = disData[i].iterator();
            while(iter.hasNext()){
                BasicEvent ev = (BasicEvent) iter.next();
                System.out.print(ev.x + "   ");
            }
            System.out.println();
        }
    }

    ArrayList[] GetDiscreteData() {
        return disData;
    }

    ArrayList[] GetSlidingData() {
        return slidingData;
    }
     int[] GetDiscreteDataCount() {
        return disDataCount;
    }
}
