/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.atc.jaer.chips.NodeBoard;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Correlates input events to each pixel cell to compute Reichardt type correlation. Depends on delayed input mapped to cell.
 * @author alinares
 */
public class Nodeboard_Framegrabber extends EventFilter2D {

    private int scan = 0;
    //private int corrTimeMin = getPrefs().getInt("EMDMotionCorrelator.corrTimeMin", 500);
    //private int corrDistance = getPrefs().getInt("EMDMotionCorrelator.corrDistance", 16);
    private int[][] eventTimes;
        private int firstFrameTs = 0;
        private short[] countX;
        private short[] countY;
        private int pixCnt=65535; // TODO debug
        boolean ignore = true;
    private int frameTime;


    {
        //setPropertyTooltip("corrTimeMax", "Max time in us that events are correlated in the pixel");
        //setPropertyTooltip("corrTimeMin", "Min time in us that events are correlated in the pixel");
        //setPropertyTooltip("corrDistance", "Distance in pixels between correlated pixels to detec motion direction");
    }

    public Nodeboard_Framegrabber(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        checkArrays();

       checkOutputPacketEventType(PolarityEvent.class); // always do this check to make sure you have a valid output packet, unless you are just returning back the input events**

        OutputEventIterator outItr = out.outputIterator();
            int diffts=1000;
            int lastts=0;
            int tsz=0,kk=0,t=0;
            int sz=in.getSize();

        for (Object o : in) {
            BasicEvent ei = (BasicEvent) o;
            kk++;
            // at this point the raw data from the USB IN packet has already been digested to extract timestamps, including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
                int data = ei.address;

                if(((data & 0x7FFF) == 0x7FFF) && (diffts>=0) ) {
                        PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                        ignore = false;
                        System.out.println("SOF - pixcount: "+pixCnt);
                        resetCounters();
                        frameTime = ei.timestamp - firstFrameTs;
                        firstFrameTs = ei.timestamp;
                        diffts=ei.timestamp-lastts;
                        lastts=ei.timestamp;
                        //e.startOfFrame=true;
                        e.address=0;
                        e.x=0;
                        e.y=0;
                        pixCnt=0;
                    } else if (!ignore) {
                        if (((data%256)>0) && ((data%256)<64)) {
                            for (t=0;t<(data%128);t++){
                                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                                //e.startOfFrame=false;
                                //e.setAdcSample(-1);//(short)data);//(data & 0xFF)*32); //data_l*16
                                e.timestamp = (ei.timestamp);
                                e.address = pixCnt; //(data & 0xFF)*32; //data_l*16
                                //e.polarity= PolarityADCSampleEvent.Polarity.On;
                                e.x= (short)(pixCnt/256); //countX[0];
                                e.y= (short)(pixCnt%256); //countY[0];
                            }
                        } else if (((data%256)>127) && ((data%256)<192)) {
                            for (t=0;t<(data%128);t++){
                                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                                //e.startOfFrame=false;
                                //e.setAdcSample(-1);//(short)data);//(data & 0xFF)*32); //data_l*16
                                e.timestamp = (ei.timestamp);
                                e.address = pixCnt; //(data & 0xFF)*32; //data_l*16
                                //e.polarity= PolarityADCSampleEvent.Polarity.Off;
                                e.x= (short)(pixCnt/256); //countX[0];
                                e.y= (short)(pixCnt%256); //countY[0];
                            }
                        }
                        pixCnt++;
                        if (((data/256)>0) && ((data/256)<64)) {
                            for (t=0;t<((data/256)%128);t++) {
                                PolarityEvent e1 = (PolarityEvent) outItr.nextOutput();
                                //e1.setAdcSample(-1); //(short)(data/256)); //((data & 0xFF00)/256)*32); //data_h*16
                                e1.timestamp = (ei.timestamp);
                                e1.address = pixCnt;//((data & 0xFF00)/256)*32; //data_h*16
                                e1.polarity= PolarityEvent.Polarity.On;
                                //e1.readoutType = PolarityADCSampleEvent.Type.A;
                                e1.x= (short)(pixCnt/256); //countX[0];
                                e1.y= (short)(pixCnt%256); //countY[0];
                                //e1.startOfFrame=false;
                            }
                        } else if (((data/256)>127) && ((data/256)<192)) {
                            for (t=0;t<((data/256)%128);t++) {
                                PolarityEvent e1 = (PolarityEvent) outItr.nextOutput();
                                //e1.setAdcSample(-1); //(short)(data/256)); //((data & 0xFF00)/256)*32); //data_h*16
                                e1.timestamp = (ei.timestamp);
                                e1.address = pixCnt;//((data & 0xFF00)/256)*32; //data_h*16
                                e1.polarity= PolarityEvent.Polarity.Off;
                                //e1.readoutType = PolarityADCSampleEvent.Type.A;
                                e1.x= (short)(pixCnt/256); //countX[0];
                                e1.y= (short)(pixCnt%256); //countY[0];
                                //e1.startOfFrame=false;
                            }
                        }

                        //System.out.println("New ADC event: type "+sampleType+", x "+e.x+", y "+e.y);
                        pixCnt++;
                        if (pixCnt>=65535) {
                            ignore=true;
                            tsz=in.getSize();
                            return out;
                        }
                    }
                    diffts=ei.timestamp-lastts;
                    lastts=ei.timestamp;

                }
        return out;
    }

    @Override
    public synchronized boolean isFilterEnabled(){
            return this.filterEnabled; // force active
        }

    //@Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    private void checkArrays() {
        if (eventTimes == null) {
            eventTimes = new int[chip.getSizeX()][chip.getSizeY()];
        }
    }

    private void resetCounters() {
            pixCnt=0;
        }
}
