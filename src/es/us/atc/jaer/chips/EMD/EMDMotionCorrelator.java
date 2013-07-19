/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.atc.jaer.chips.EMD;





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
public class EMDMotionCorrelator extends EventFilter2D {

    private int corrTimeMax = getPrefs().getInt("EMDMotionCorrelator.corrTimeMax", 1000);
    private int corrTimeMin = getPrefs().getInt("EMDMotionCorrelator.corrTimeMin", 500);
    private int corrDistance = getPrefs().getInt("EMDMotionCorrelator.corrDistance", 16);
    private int[][] eventTimes;
    

    {
        setPropertyTooltip("corrTimeMax", "Max time in us that events are correlated in the pixel");
        setPropertyTooltip("corrTimeMin", "Min time in us that events are correlated in the pixel");
        setPropertyTooltip("corrDistance", "Distance in pixels between correlated pixels to detec motion direction");
    }

    public EMDMotionCorrelator(AEChip chip) {
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
        for (Object o : in) {
            BasicEvent e = (BasicEvent) o;
            if (e.y==32) { // If passed the motion is detected
                if (e.x < (63 - corrDistance) && e.x > corrDistance) {
                    int oldtsr = eventTimes[e.x + corrDistance][e.y+1];
                    int oldtsl = eventTimes[e.x - corrDistance][e.y+1];
                    int mr = e.timestamp - oldtsr;
                    int ml = e.timestamp - oldtsl;
                    if (mr < corrTimeMax && mr > corrTimeMin) {
                        if (ml > corrTimeMax || ml < corrTimeMin) {
                          PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                          oe.copyFrom(e); // copy the BasicEvent fields from the input event
                          oe.polarity=PolarityEvent.Polarity.On;
                    } } else 
                    if (ml < corrTimeMax && ml > corrTimeMin) {
                        if (mr > corrTimeMax || mr < corrTimeMin) {
                            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                            oe.copyFrom(e); // copy the BasicEvent fields from the input event
                            oe.polarity=PolarityEvent.Polarity.Off;
                        }
                    } 
                }
            }
/*            if (e.x==32) { // If passed the motion is detected
                if (e.y < (63 - corrDistance) && e.y > corrDistance) {
                    int oldtsu = eventTimes[e.x][e.y - corrDistance];
                    int oldtsd = eventTimes[e.x][e.y + corrDistance];
                    int mu = e.timestamp - oldtsu;
                    int md = e.timestamp - oldtsd;
                    if (mu < corrTimeMax && mu > corrTimeMin) {
                        if (md > corrTimeMax || md < corrTimeMin) {
                          PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                          oe.copyFrom(e); // copy the BasicEvent fields from the input event
                          oe.polarity=PolarityEvent.Polarity.On;
                          oe.y=48;
                    } } else 
                    if (md < corrTimeMax && md > corrTimeMin) {
                        if (mu > corrTimeMax || mu < corrTimeMin) {
                            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                            oe.copyFrom(e); // copy the BasicEvent fields from the input event
                            oe.polarity=PolarityEvent.Polarity.Off;
                            oe.y=16;
                        }
                    } 
                }
            }
            if (e.x==e.y) { // If passed the motion is detected
                if (e.y < (63 - corrDistance) && e.y > corrDistance && e.x < (63 - corrDistance) && e.x > corrDistance) {
                    int oldtsur = eventTimes[e.x + corrDistance][e.y + corrDistance];
                    int oldtsdl = eventTimes[e.x - corrDistance][e.y - corrDistance];
                    int mur = e.timestamp - oldtsur;
                    int mdl = e.timestamp - oldtsdl;
                    if (mur < corrTimeMax && mur > corrTimeMin) {
                        if (mdl > corrTimeMax || mdl < corrTimeMin) {
                          PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                          oe.copyFrom(e); // copy the BasicEvent fields from the input event
                          oe.polarity=PolarityEvent.Polarity.On;
                          oe.x=48;
                          oe.y=48;
                    } } else 
                    if (mdl < corrTimeMax && mdl > corrTimeMin) {
                        if (mur > corrTimeMax || mur < corrTimeMin) {
                            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                            oe.copyFrom(e); // copy the BasicEvent fields from the input event
                            oe.polarity=PolarityEvent.Polarity.Off;
                            oe.x=16;
                            oe.y=16;
                        }
                    } 
                }
            }
            if ((63-e.x)==e.y) { // If passed the motion is detected
                if (e.y < (63 - corrDistance) && e.y > corrDistance && e.x < (63 - corrDistance) && e.x > corrDistance) {
                    int oldtsul = eventTimes[e.x + corrDistance][e.y - corrDistance];
                    int oldtsdr = eventTimes[e.x - corrDistance][e.y + corrDistance];
                    int mul = e.timestamp - oldtsul;
                    int mdr = e.timestamp - oldtsdr;
                    if (mul < corrTimeMax && mul > corrTimeMin) {
                        if (mdr > corrTimeMax || mdr < corrTimeMin) {
                          PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                          oe.copyFrom(e); // copy the BasicEvent fields from the input event
                          oe.polarity=PolarityEvent.Polarity.On;
                          oe.x=16;
                          oe.y=48;
                    } } else 
                    if (mdr < corrTimeMax && mdr > corrTimeMin) {
                        if (mul > corrTimeMax || mul < corrTimeMin) {
                            PolarityEvent oe = (PolarityEvent) outItr.nextOutput();  // make an output event
                            oe.copyFrom(e); // copy the BasicEvent fields from the input event
                            oe.polarity=PolarityEvent.Polarity.Off;
                            oe.x=48;
                            oe.y=16;
                        }
                    } 
                }
            }*/
            eventTimes[e.x][e.y] = e.timestamp;
        }
        return out;
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

    public int getCorrTimeMax() {
        return corrTimeMax;
    }

    public void setCorrTimeMax(int corrTime) {
        this.corrTimeMax = corrTime;
        getPrefs().putInt("EMDMotionCorrelator.corrTimeMax", corrTime);
    }

    public int getCorrTimeMin() {
        return corrTimeMin;
    }

    public void setCorrTimeMin(int corrTime) {
        this.corrTimeMin = corrTime;
        getPrefs().putInt("EMDMotionCorrelator.corrTimeMin", corrTime);
    }

    public int getCorrDistance() {
        return corrDistance;
    }

    public void setCorrDistance(int corrDist) {
        this.corrDistance = corrDist;
        getPrefs().putInt("EMDMotionCorrelator.corrDistance", corrDist);
    }

    private void checkArrays() {
        if (eventTimes == null) {
            eventTimes = new int[chip.getSizeX()][chip.getSizeY()];
        }
    }
}
