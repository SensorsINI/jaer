/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;
/*
 * &&From
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
 * import weka.core.Instance;
 * &&To
 */


/**
 *
 * @author Eun Yeong Ahn
 */
public class CXYRatioFilter {
    /*
     * &&From

    double ratio = 0;

    public void Filter(EventPacket<?> in){
        double ymin = -1, ymax = -1, xmin=-1, xmax=-1;
        Boolean first = true;
        for(BasicEvent ev:in){
            if(first){
                xmin = ev.x; xmax = ev.x;
                ymin = ev.y; ymax = ev.y;
                first = false;
            }else{
                if(ev.x < xmin)
                    xmin = ev.x;
                else if(ev.x > xmax)
                    xmax = ev.x;

                if(ev.y < ymin)
                    ymin = ev.y;
                else if(ev.y > ymax)
                    ymax = ev.y;
            }
        }

        ratio = (double)(ymax - ymin)/(double)(xmax - xmin);
    }

    public void FilterByVariance(EventPacket<?> in){
        int n = in.getSize();
        double sumX = 0, sumY =0, sqrSumX = 0, sqrSumY = 0;

        for(BasicEvent ev:in){
            sumX = sumX + ev.x;
            sqrSumX = sqrSumX + Math.pow(ev.x, 2);
            sumY = sumY + ev.y;
            sqrSumY = sqrSumY + Math.pow(ev.y, 2);
        }

        double varX = sqrSumX/n - Math.pow(sumX, 2);
        double varY = sqrSumY/n - Math.pow(sumY, 2);
        ratio = (double)varY/(double)varX;
    }

    Instance GetInstance(int cLabel){
        double[] newvals = new double[2];
        newvals[0] = ratio;
        newvals[1] = cLabel;
        Instance new_instance = new Instance(1.0, newvals);

        return new_instance;
    }
     &&To
     */
}
