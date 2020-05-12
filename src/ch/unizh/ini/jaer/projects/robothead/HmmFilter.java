package ch.unizh.ini.jaer.projects.robothead;

/*
 * HmmFilter.java
 *
 * Created on 1. Januar 2008, 13:33
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

import java.util.Observable;

import java.util.Vector;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author jaeckeld
 */
public class HmmFilter extends EventFilter2D  {

    private int hmmTime = getPrefs().getInt("HmmFilter.hmmTime", 2000);
    private boolean dispVector = getPrefs().getBoolean("HmmFilter.dispVector", false);
    private boolean dispStates = getPrefs().getBoolean("HmmFilter.dispStates", false);

    /**
     * Creates a new instance of HmmFilter
     */
    public HmmFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("hmmTime", "after this amount of time apply viterbi Algo [ms]");

        myHmm.loadHmmData();            // load HMM Model arrays
        myHmm.genSoundLUT();

        vectSize = (int) myHmm.DATA[0][0];
        chMin = (int) myHmm.DATA[0][1];
        chMax = (int) myHmm.DATA[0][2];
        N = (int) myHmm.DATA[0][3];        // number of vertical Bins
        maxVal = (int) myHmm.DATA[0][4];

        resetFilter();
        //dispVector();

    }

    int vectSize;   // temporal width of observation Bin [us]
    int numOfBins;
    int maxVal;
    int chMin;
    int chMax;
    int N;          // number of vertical Bins
    boolean isFirstTs;  // FALSE at the beginning or after reset, TRUE after having used one Ts
    int firstTs;
    int actualVectorLeft[];
    int actualVectorRight[];
    int actualObservationLeft;
    int actualObservationRight;
    int actualStart;
    int actualEnd;
    int wiis[];
    HmmTools myHmm = new HmmTools(maxVal);
    int noTsObservation;
    Vector observationBufferLeft;
    Vector observationBufferRight;
    Vector eventBuffer;
    Vector actualEvents;
    Vector<Integer> indSound;
    Vector oo;

    public EventPacket<?> filterPacket(EventPacket<?> in) {

        if (!isFilterEnabled()) {
            //System.out.print("TEST 2");
            return in;       // only use if filter enabled
        }

        if (in.getSize() == 0) {
            return in;       // do nothing if no spikes came in...., this means empty EventPacket
        }
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();        // Output-Iterator always getString cleared when eventPacket comes in

        for (Object e : in) {

            BasicEvent i = (BasicEvent) e;

            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            if (this.isFirstTs == false) {     // detect first TS
                firstTs = i.timestamp;
                isFirstTs = true;
                actualStart = i.timestamp;
                actualEnd = actualStart + vectSize;
                actualEvents = new Vector(25, 5);
            }

            while (i.timestamp >= actualEnd + vectSize && observationBufferLeft.size() < numOfBins - 1) {        // in this case an observation was jumped because there were no ts
                this.observationBufferLeft.add(noTsObservation);
                this.eventBuffer.add(null);
                //observationBufferRight.add(noTsObservation);
                actualStart = actualEnd;
                actualEnd = actualStart + vectSize;
            }

            if (i.timestamp >= actualEnd) {        // end of an observation

                actualStart = actualEnd;
                actualEnd = actualStart + vectSize;

                // encode observation
                actualObservationLeft = myHmm.getObservation(actualVectorLeft[0], actualVectorLeft[1], actualVectorLeft[2], actualVectorLeft[3], actualVectorLeft[4]);
                this.observationBufferLeft.add(new Integer(actualObservationLeft));

                this.eventBuffer.add(actualEvents);  // and also add saved events to eventBuffer
                //System.out.println(observationBufferLeft.size());
                if (dispVector) {
                    dispVectors();   // display the actual (left) Observation Vector
                    System.out.print("  => " + actualObservationLeft);
                    System.out.println("  => " + actualEvents.size());

                }

                actualVectorLeft = new int[N];       // reset Vectors!
                actualVectorRight = new int[N];

                this.actualEvents = new Vector(25, 5);   //reset Events Vector

            }

            if (observationBufferLeft.size() > numOfBins - 1) {  // in that case the viterbi sequence is completed and Algo can be applied
                int piState = 1;
                // call viterbi
                System.out.println("Stop listening, start calculating..." + i.timestamp);

                double[][] statesLeft = myHmm.viterbi(observationBufferLeft, piState, myHmm.TR_Left, myHmm.EMIS_Left);

                if (dispStates) {
                    dispObservations();
                    System.out.println("Viterbi States: ");
                    dispStates(statesLeft);
                }
                // HERE THE OUTPUT EVENTS HAVE TO BE OVERTAKEN FROM THE eventBuffer !!!!

                indSound = new Vector<Integer>(500, 500);     // index of bins belonging to sound
                int maxState = 0;
                int counter = 0;
                for (int a = 0; a < statesLeft[1].length; a++) {   // look for states belonging to sounds
                    if (statesLeft[1][a] > 0) {
                        indSound.add(new Integer(a));
                    }
                    if (statesLeft[1][a] == 2) {
                        counter++;
                    }
                }
                System.out.println("Number of Sounds Sounds is: " + counter);

                for (int a = 0; a < indSound.size(); a++) {        // write sound events to output Iterator

                    oo = (Vector) eventBuffer.get(indSound.get(a));
                    if (oo != null) {
                        for (int b = 0; b < oo.size(); b++) {

                            BasicEvent o = (BasicEvent) outItr.nextOutput();
                            o.copyFrom((BasicEvent) oo.get(b));
                            //System.out.print("");
                        }
                    }
                }

                // Empty ObservationBuffer:
                this.observationBufferLeft = new Vector(numOfBins - 10, 10);
                this.eventBuffer = new Vector(numOfBins - 10, 10);       //reset eventBuffer

                System.out.println("End Calculating, start listening... ");

                //return out;
            }

            if (i.x + 1 >= chMin && i.x + 1 <= chMax) {      // add ts to Output Vector
                // i.x=channel [0 31] !!!
                if (i.y == 0) {
                    if (this.actualVectorLeft[wiis[i.x + 1 - chMin]] < maxVal) {       // limit Value to maxVal
                        this.actualVectorLeft[wiis[i.x + 1 - chMin]] = this.actualVectorLeft[wiis[i.x + 1 - chMin]] + 1;
                    }
                } else {
                    if (this.actualVectorRight[wiis[i.x + 1 - chMin]] < maxVal) {
                        this.actualVectorRight[wiis[i.x + 1 - chMin]] = this.actualVectorRight[wiis[i.x + 1 - chMin]] + 1;
                    }
                }
            }

            TypedEvent o = new TypedEvent();            // every event has to be copied!!
            o.copyFrom(i);
            actualEvents.add(o);    // add every event to actualEvents
            //System.out.print("");
        }

        return out;

    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
//        System.out.println("reset!");

        this.isFirstTs = false;
        this.actualVectorLeft = new int[N];
        this.actualVectorRight = new int[N];
        this.wiis = this.genWiis(this.chMin, this.chMax, this.N);
        this.numOfBins = 1000 * this.hmmTime / this.vectSize;     //number of observation I have in one viterbi sequence
        //this.observationBuffer = new int[this.numOfBins];
        this.observationBufferLeft = new Vector(numOfBins - 10, 10);
        this.eventBuffer = new Vector(numOfBins - 10, 10);

        myHmm.genCodeArray(maxVal);     // generate array for encoding observations
        this.noTsObservation = myHmm.getObservation(0, 0, 0, 0, 0);
    }

    public void initFilter() {
        System.out.println("init!");
        resetFilter();

    }

    public int gethmmTime() {
        return this.hmmTime;
    }

    public void setHmmTime(int hmmTime) {
        getPrefs().putInt("HmmFilter.hmmTime", hmmTime);
        getSupport().firePropertyChange("hmmTime", this.hmmTime, hmmTime);
        this.hmmTime = hmmTime;
        resetFilter();
    }

    public boolean isDispVector() {
        return this.dispVector;
    }

    public void setDispVector(boolean dispVector) {
        this.dispVector = dispVector;
        getPrefs().putBoolean("HmmFilter.dispVector", dispVector);
    }

    public boolean isdispStates() {
        return this.dispStates;
    }

    public void setdispStates(boolean dispStates) {
        this.dispStates = dispStates;
        getPrefs().putBoolean("HmmFilter.dispStates", dispStates);
    }

    public int[] genWiis(int minCh, int maxCh, int nNumb) {
        int start;
        int numbCh = maxCh - minCh + 1;
        int wiis[];
        wiis = new int[numbCh];
        int widthN = numbCh / nNumb;
        for (int i = 0; i < N; i++) {
            start = i * widthN;
            for (int j = start; j < start + widthN; j++) {
                wiis[j] = i;
            }
        }

        return wiis;
    }

    public void dispVectors() {
        for (int i = 0; i < this.actualVectorLeft.length; i++) {
            System.out.print(this.actualVectorLeft[i] + " ");
        }
        //System.out.println("");
    }

    public void dispObservations() {
        for (int i = 0; i < this.observationBufferLeft.size(); i++) {
            System.out.print(this.observationBufferLeft.get(i) + " ");
        }
        System.out.println("");
    }

    public void dispStates(double[][] states) {
        for (int i = 0; i < states[0].length; i++) {
            System.out.print(states[1][i] + " ");
        }
        System.out.println("");
    }

}
