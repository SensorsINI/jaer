/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;

import java.util.ArrayList;

import net.sf.jaer.event.BasicEvent;

/**
 *
 * @author Eun Yeong Ahn
 */
public class C2DDiscreteNew {
    int noOfInstances;
    double data[][]; //transpose of the data
    double disDataCount[][];
    ArrayList disData[][];  //save index of data
    
    void SetData(double[][] d, int noInstances){//row: # of element, col: # of features
        noOfInstances = noInstances;
        data = new double[noOfInstances][2];

        for(int i = 0; i < noInstances; i++){
            for(int j = 0; j < 2; j++){
                data[i][j] = d[i][j];
            }
        }
    }

    void Discretize(int xbins, int ybins){
        disDataCount = new double[ybins][xbins];
        disData = new ArrayList[ybins][xbins];
        for(int i = 0; i< ybins; i++){
            for(int j = 0; j< xbins; j++)
                disData[i][j] = new ArrayList<BasicEvent>();
        }
        for(int i = 0; i < ybins; i++){
            for(int j = 0; j < xbins; j++){
                disDataCount[i][j] = 0;
            }
        }
        double xinterval = 128/xbins, yinterval = 128/ybins;

        //discretize
        int xIdx = -1, yIdx = -1;
        for(int i = 0; i < noOfInstances; i++){// instances
            xIdx = (int) Math.floor(data[i][0] / xinterval);
            if(xIdx >= xbins) xIdx = xbins - 1;

            yIdx = (int) Math.floor(data[i][1] / yinterval);
            if(yIdx >= ybins) yIdx = ybins - 1;

            
            disDataCount[yIdx][xIdx] = disDataCount[yIdx][xIdx] + 1;

            //data
            BasicEvent e = new BasicEvent();
            e.setX((short) data[i][0]);
            e.setY((short) data[i][1]);
            disData[yIdx][xIdx].add(e);
        }

       for(int i = 0; i < ybins; i++){  //for each instance
            for(int j = 0; j < xbins; j++){//for each attribute
                disDataCount[i][j] = disDataCount[i][j]/(double)noOfInstances;
            }
        }
    }

    void DiscretizeSlidingWindow(int xbins, int ybins){
        disDataCount = new double[ybins][xbins];
        disData = new ArrayList[ybins][xbins];
        for(int i = 0; i< ybins; i++){
            for(int j = 0; j< xbins; j++)
                disData[i][j] = new ArrayList<BasicEvent>();
        }
        for(int i = 0; i < ybins; i++){
            for(int j = 0; j < xbins; j++){
                disDataCount[i][j] = 0;
            }
        }
        double xinterval = 128/xbins, yinterval = 128/ybins;

        //discretize
        int xIdx = -1, yIdx = -1;
        for(int i = 0; i < noOfInstances; i++){// instances
            double xremainder = 0, yremainder = 0;

            xIdx = (int) Math.floor(data[i][0] / xinterval);
            if(xIdx >= xbins) xIdx = xbins - 1;
            xremainder = data[i][0] - (xinterval * xIdx);     //sliding window

            yIdx = (int) Math.floor(data[i][1] / yinterval);
            if(yIdx >= ybins) yIdx = ybins - 1;
            yremainder = data[i][1] - (yinterval * yIdx);     //sliding window


            disDataCount[yIdx][xIdx] = disDataCount[yIdx][xIdx] + 1;

            //data
            BasicEvent e = new BasicEvent();
            e.setX((short) data[i][0]);
            e.setY((short) data[i][1]);

            if(yIdx == 0 && xIdx == 0){
                int a = 0;
            }
            disData[yIdx][xIdx].add(e);

            //################################################################ Sliding window
             /* slinding window
              * can be added to more than two bins
              * redundant size = 1/3
              */
             int sidx = (int) Math.floor(xremainder/(xinterval/3));
             if(sidx == 0 && xIdx != 0)
                 disDataCount[yIdx][xIdx - 1] = disDataCount[yIdx][xIdx - 1] + 1;   //disData[yIdx][xIdx - 1].add(e);
             if(sidx == 2 && xIdx < xbins-1)
                 disDataCount[yIdx][xIdx + 1] = disDataCount[yIdx][xIdx + 1] + 1;   //disData[yIdx][xIdx + 1].add(e);

             sidx = (int) Math.floor(yremainder/(yinterval/3));
             if(sidx == 0 && yIdx != 0)
                 disDataCount[yIdx - 1][xIdx] = disDataCount[yIdx - 1][xIdx] + 1;//disData[yIdx - 1][xIdx].add(e);
             if(sidx == 2 && yIdx < ybins-1)
                 disDataCount[yIdx + 1][xIdx] = disDataCount[yIdx + 1][xIdx] + 1;//disData[yIdx + 1][xIdx].add(e);
             //################################################################

        }

       /*for(int i = 0; i < ybins; i++){  //for each instance
            for(int j = 0; j < xbins; j++){//for each attribute
                disDataCount[i][j] = disDataCount[i][j]/(double)noOfInstances;
            }
        }*/
    }

    double[][] GetDisDataCount(){
        return disDataCount.clone();
    }
    ArrayList[][] GetDisData(){
        return disData;
    }
}
