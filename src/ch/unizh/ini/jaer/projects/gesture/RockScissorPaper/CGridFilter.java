/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Eun Yeong Ahn
 */
public class CGridFilter {

    double xMax = -1, xMin = -1, yMax = -1, yMin = -1;    
    double[][] mData;
    int mFeature = 2;
    int mNoOfInstances;

    public CGridFilter(EventPacket<?> in) {
        // change to double array
        mNoOfInstances = in.getSize();
        mFeature = 2;
        mData = new double[mNoOfInstances][mFeature];
        int idx = 0;
        for(BasicEvent ev:in){
            mData[idx][0] = ev.getX();
            mData[idx][1] = ev.getY();
            idx++;
        }
    }
    public EventPacket Filter(int xbins, int ybins, double threshold){
        //discretization
        /*C2DDiscrete discretize = new C2DDiscrete();
        discretize.SetData(mData.clone(), mNoOfInstances, mFeature);
        discretize.Discretize(CParameter.GridFilterBin);*/

        C2DDiscreteNew discretize = new C2DDiscreteNew();
        discretize.SetData(mData.clone(), mNoOfInstances);
        discretize.DiscretizeSlidingWindow(xbins,ybins);
        int noThreshold = (int) Math.ceil(mNoOfInstances * threshold);

        double[][] count = discretize.GetDisDataCount().clone();
        ArrayList[][] data = discretize.GetDisData().clone();

        //filtering
        //EventPacket newPacket = new EventPacket();
        ArrayList packetArray = new ArrayList<BasicEvent>();
        for(int i = 0; i < ybins; i++){
            for(int j = 0; j< xbins; j++){
                if(count[i][j] > noThreshold){
                    packetArray.addAll((Collection) data[i][j].clone());
                }
            }
        }

        //event packet
        EventPacket newEventPacket = new EventPacket();

        BasicEvent[] tmp = new BasicEvent[packetArray.size()];
        Iterator iter = packetArray.iterator();
        int i = 0;
        while(iter.hasNext()){
            tmp[i++] = (BasicEvent) iter.next();
        }

        newEventPacket.setElementData(tmp);
        newEventPacket.setSize(i);

        return newEventPacket;
    }

    public EventPacket FilterXYRatio(int xbins, int ybins, double threshold){
        //discretization
        C2DDiscreteNew discretize = new C2DDiscreteNew();
        discretize.SetData(mData.clone(), mNoOfInstances);
        discretize.DiscretizeSlidingWindow(xbins,ybins);

        double[][] count = discretize.GetDisDataCount().clone();
        ArrayList[][] data = discretize.GetDisData().clone();

        //filtering
        /* ####################################################################
         * Along the X Axis, remove data points
         */
        for(int i = 0; i < ybins; i++){
            double sum = 0;
            for(int j = 0; j < xbins; j++){
                sum = sum + count[i][j];
            }
            for(int j = 0; j < xbins; j++){
                if(count[i][j]/sum < threshold){
                    data[i][j].clear();
                    count[i][j] = 0;
                }
            }
        }
        // Along the Y Axis
        for(int j = 0; j < xbins;j++){
            double sum = 0;
            for(int i = 0; i < ybins; i++){
                sum = sum + count[i][j];
            }
            for(int i = 0; i < ybins; i++){
                if(count[i][j]/sum < threshold){
                    data[i][j].clear();
                    count[i][j] = 0;
                }
            }
        }
        // ####################################################################

        // Combine all points
        ArrayList packetArray = new ArrayList<BasicEvent>();
        for(int i = 0; i < ybins; i++){
            for(int j = 0; j< xbins; j++){
                packetArray.addAll((Collection) data[i][j].clone());
            }
        }

        //event packet
        EventPacket newEventPacket = new EventPacket();

        BasicEvent[] tmp = new BasicEvent[packetArray.size()];
        Iterator iter = packetArray.iterator();
        int i = 0;
        while(iter.hasNext()){
            tmp[i++] = (BasicEvent) iter.next();
        }

        newEventPacket.setElementData(tmp);
        newEventPacket.setSize(i);

        return newEventPacket;
    }

}
