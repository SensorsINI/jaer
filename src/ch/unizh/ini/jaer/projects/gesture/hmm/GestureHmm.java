/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

/**
 * HMM based gesture recognition module
 * @author Jun Haeng Lee
 */
public class GestureHmm {
    final static String START_STATE = "Start";
    final static String FINAL_STATE = "Final";
    final static String DYNAMIC_THRESHOLD_MODEL  = "Dynamic Threshold Model";
    final static String[] THRESHOLD_STATE = {START_STATE, FINAL_STATE};
    final static double[] THRESHOLD_START_PROB = {1.0, 0.0};

    // number of defined gestures in a set
    private int numGestures = 0;

    // Hashmap to store HiddenMarkovModels for gestures
    HashMap<String, HiddenMarkovModel> gestureHmms= new HashMap<String, HiddenMarkovModel>();

    // HMM for the threshold model
    HiddenMarkovModel thresholdModel;
    
    // set of feature vectors
    private ArrayList<String> featureVectorSpace = new ArrayList<String>();

    private boolean useDynamicThreshold = false;

    /**
     * Constructor with feature vector space
     * @param featureVectorSpace : feature vector space
     * @param useDynamicThreshold : if true, dynamic threshold model is generated as a reference
     */
    public GestureHmm(String [] featureVectorSpace, boolean useDynamicThreshold) {
        for(String fv:featureVectorSpace)
            this.featureVectorSpace.add(fv);

        this.useDynamicThreshold = useDynamicThreshold;
        if(useDynamicThreshold){
            thresholdModel = new HiddenMarkovModel(DYNAMIC_THRESHOLD_MODEL, THRESHOLD_STATE, getFeatureVectorSpaceToArray());
            thresholdModel.initializeProbabilityMatrix(THRESHOLD_START_PROB, null, null);
            // set transition matrix of the final state
            thresholdModel.setTransitionProbability(FINAL_STATE, START_STATE, 1.0);
            thresholdModel.setStateSilent(START_STATE);
            thresholdModel.setStateSilent(FINAL_STATE);
        }
    }

    /**
     * add a new gesture
     * @param name : gesture name
     * @param numStates : number of states to be used in HMM for this gesture
     * @return : HMM
     */
    public HiddenMarkovModel addGesture(String name, int numStates){
        String[] stateNames = composeStateNamesToArray(name, numStates);
        HiddenMarkovModel hmm = new HiddenMarkovModel(name, stateNames, getFeatureVectorSpaceToArray());

        gestureHmms.put(name, hmm);
        numGestures++;

        return hmm;
    }

    /**
     * add a new gesture
     * @param hmm : hmm of the gesture
     */
    public void addGesture(HiddenMarkovModel hmm){
        gestureHmms.put(hmm.getName(), hmm);
        numGestures++;
    }

    /**
     * remove the specified gesture
     * @param name : gesture name
     */
    public void removeGesture(String name){
        if(useDynamicThreshold)
            removeFromThresholdModel(name);
        gestureHmms.remove(name);
        numGestures--;
    }

    /**
     * initialize a gesture HMM
     * @param name : gesture name
     * @param startProb : start probability of HMM
     * @param transitionProb : transition probability of HMM
     * @param emissionProb : emission probability of HMM
     */
    public void initializeGesture(String name, double[] startProb, double[][] transitionProb, double[][] emissionProb){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        hmm.setStartProbability(startProb);
        hmm.setTransitionProbability(transitionProb);
        hmm.setEmissionProbability(emissionProb);

        if(!name.equals(DYNAMIC_THRESHOLD_MODEL) && useDynamicThreshold){
            updateThresholdModelWith(name);
        }
    }

    /**
     * initialize a gesture with an ergodic HMM
     * @param name : gesture name
     */
    public void initializeGestureRandomErgodic(String name){
        gestureHmms.get(name).setProbabilityRandomErgodic();

        if(!name.equals(DYNAMIC_THRESHOLD_MODEL) && useDynamicThreshold){
            updateThresholdModelWith(name);
        }
    }

    /**
     * initialize a gesture with a left-right HMM
     * @param name : gesture name
     * @param numStartStates : number of states which have start probability
     */
    public void initializeGestureRandomLeftRight(String name, int numStartStates){
        gestureHmms.get(name).setProbabilityRandomLeftRight(numStartStates);

        if(!name.equals(DYNAMIC_THRESHOLD_MODEL) && useDynamicThreshold){
            updateThresholdModelWith(name);
        }
    }

    /**
     * initialize a gesture with a left-right-banded HMM
     * @param name : gesture name
     */
    public void initializeGestureRandomLeftRightBanded(String name){
        gestureHmms.get(name).setProbabilityRandomLeftRightBanded();

        if(!name.equals(DYNAMIC_THRESHOLD_MODEL) && useDynamicThreshold){
            updateThresholdModelWith(name);
        }
    }

    /**
     * composed state names of a gesture HMM
     * @param name : gesture name
     * @param numStates : number of states used in HMM
     * @return
     */
    private String[] composeStateNamesToArray(String name, int numStates){
        String[] stateNames = new String[numStates];

        for(int i=0; i<numStates; i++)
            stateNames[i] = String.format("%s_%d", name, i);

        return stateNames;
    }

    /**
     * get gesture HMM
     * @param name : gesture name
     * @return : HMM
     */
    public HiddenMarkovModel getGestureHmm(String name){
        return gestureHmms.get(name);
    }

    /**
     * learn gesture with an observation sequence
     * @param name : gesture name
     * @param obsSeq : observation sequence
     * @return
     */
    public boolean learnGesture(String name, String[] obsSeq){
        double minProb = 0.00001;
        boolean learningDone = false;

        HiddenMarkovModel hmm = gestureHmms.get(name);

        // when this is the first training
        if(hmm.getNumTraining() == 0){

            double[] sProb = hmm.getStartProbabilityToArray();
            double[][] tProb = hmm.getTransitionProbabilityToArray();
            double [][] eProb = hmm.getEmissionProbabilityToArray();


            while(!(learningDone = hmm.BaumWelch(obsSeq, minProb, 0.001, false)) && minProb < 1){
                hmm.initializeProbabilityMatrix(sProb, tProb, eProb);
                minProb *= 10;
            }

        } else { // when this is not the first training. All training results are ensenble averaged
            HiddenMarkovModel newHmm = new HiddenMarkovModel(name, hmm.getStatesToArray(), getFeatureVectorSpaceToArray());

            newHmm.initializeProbabilityMatrix(hmm.getStartProbabilityToArray(), hmm.getTransitionProbabilityToArray(), hmm.getEmissionProbabilityToArray());

            while(!(learningDone = newHmm.BaumWelch(obsSeq, minProb, 0.001, false)) && minProb < 1){
                newHmm.initializeProbabilityMatrix(hmm.getStartProbabilityToArray(), hmm.getTransitionProbabilityToArray(), hmm.getEmissionProbabilityToArray());
                minProb *= 10;
            }

            if(learningDone)
                hmm.ensenbleAverage(newHmm);
        }

        if(!learningDone){
            removeGesture(name);
            System.out.println("Learning of "+name+" is failed.");
        } else {
            if(!name.equals(DYNAMIC_THRESHOLD_MODEL) && useDynamicThreshold){
                updateThresholdModelWith(name);
            }
        }

        return learningDone;
    }

    /**
     * generate complete set of observation seqeunces with the specified length
     * @param featureVectorSpace : feature vector space
     * @param obsLength : observation sequence length
     * @param showObsSet : if true, the result will be shown on the screen
     * @return
     */
    public static String[][] genCompleteObsSeqSet(String[] featureVectorSpace, int obsLength, boolean showObsSet){
        String[][] out = new String[(int) Math.pow((double) featureVectorSpace.length, (double) obsLength)][obsLength];


        for(int j=obsLength-1; j>=0; j--){
            int i = 0;
            while(i<out.length)
                for(int a=0; a<featureVectorSpace.length; a++)
                   for(int k=0; k<(int) Math.pow(featureVectorSpace.length, obsLength - 1 - j); k++){
                       out[i][j] = featureVectorSpace[a];
                       i++;
                   }
        }

        if(showObsSet){
            for(int i=0; i<out.length; i++){
                for(int j=0; j<out[0].length; j++)
                    System.out.print(out[i][j] + ", ");
                System.out.println();
            }
        }

        return out;
    }


    /**
     * get the likelyhood of observation sequence in the given gesture
     * @param name : gesture name
     * @param obs : observation sequence
     * @return : likelyhood
     */
    public double getGestureLikelyhood(String name, String[] obs){
        return gestureHmms.get(name).forward(obs);
    }

    /**
     * get the likelyhood of a gesture in the threshold model
     * @param obs
     * @return
     */
    public double getGestureLikelyhoodTM(double scaleFactor, String[] obs){
        return thresholdModel.forward(obs)*scaleFactor;
    }

    /**
     * get the name of the best matching gesture
     * @param obs : observation sequence
     * @return : gesture name
     */
    public String getBestMatchingGesture(String[] obs){
        String name = null;
        double maxProb = 0.0;

        for(HiddenMarkovModel hmm : gestureHmms.values()){
            double prob = hmm.forward(obs);
            if(prob > maxProb){
                name = hmm.getName();
                maxProb = prob;
            }
        }

        if(maxProb < getGestureLikelyhoodTM(0.1, obs))
            name = null;

//        System.out.println("Maximum likelyhood = " + maxProb);
        return name;
    }

    /**
     * get the feature vector space in array
     * @return
     */
    public String[] getFeatureVectorSpaceToArray() {
        String[] out = new String[featureVectorSpace.size()];

        for(int i=0; i<featureVectorSpace.size(); i++)
            out[i] = featureVectorSpace.get(i);

        return out;
    }

    /**
     * get the number of gestures registered.
     * @return
     */
    public int getNumGestures() {
        return numGestures;
    }

    /**
     * update threshold model with the specified gesture
     * @param gestureName : gesture name
     */
    public void updateThresholdModelWith(String gestureName){
        HiddenMarkovModel hmm = gestureHmms.get(gestureName);

        ArrayList<String> states = thresholdModel.getStates();
        Hashtable<String, Double> startProb = thresholdModel.getStartProbability();
        Hashtable<String, Hashtable> transitionProb = thresholdModel.getTransitionProbability();
        Hashtable<String, Hashtable> emissionProb = thresholdModel.getEmissionProbability();

        boolean updateStartState = false;
        for(String state : hmm.getStatesToArray()){
            // if not contained
            if(!states.contains(state)){
                // add state
                states.add(state);
                // set the added state as a non-silent state
                thresholdModel.setStateNonSilent(state);

                // set start probability zero
                startProb.put(state, 0.0);

                // add a new row in the transition probability matirx
                Hashtable<String, Double> newTransitionProb = new Hashtable<String, Double>();
                for(String nextState:hmm.getStates())  // create zero transition matrix
                    newTransitionProb.put(nextState, 0.0);
                transitionProb.put(state, newTransitionProb);

                // add a new row in the emission probability matrix
                Hashtable<String, Double> newEmissionProb = new Hashtable<String, Double>();
                emissionProb.put(state, newEmissionProb);

                // update transition probability
                for(String ss:states){
                    transitionProb.get(ss).put(state, 0.0);
                    transitionProb.get(state).put(ss, 0.0);
                }

                updateStartState = true;
            }

            // set transition probability
            transitionProb.get(state).put(state, hmm.getTransitionProbability(state, state)); // add self transition
            transitionProb.get(state).put(FINAL_STATE, 1.0 - hmm.getTransitionProbability(state, state)); // add transition to final state

            // set emission probability
            for(String fv:featureVectorSpace)
                emissionProb.get(state).put(fv, hmm.getEmissionProbability(state, fv));
        }
        if(updateStartState)
            for(String state:states)
                if(!state.equals(START_STATE) && !state.equals(FINAL_STATE))
                    transitionProb.get(START_STATE).put(state, 1.0/(thresholdModel.getNumStates()-3.0));
    }

    private void removeFromThresholdModel(String name){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        for(String state:hmm.getStates()){
            // remove all states from the threshold model
            thresholdModel.getStates().remove(state);

            // remove transition matrix
            thresholdModel.getTransitionProbability().remove(state);

            // remove emission matrix
            thresholdModel.getEmissionProbability().remove(state);

            // modify start state
            thresholdModel.getTransitionProbability().get(START_STATE).remove(state);
            thresholdModel.getStartProbability().remove(state);

            // modify final state
            thresholdModel.getTransitionProbability().get(FINAL_STATE).remove(state);
        }
    }

    /**
     * print the summay of a gesture on the screen
     * @param name : gesture name
     */
    public void printGesture(String name){
        if(gestureHmms.get(name) == null)
            return;

        System.out.println("+++++++++ Summary of " + name + " +++++++++++");
        System.out.println("Number of states : " + gestureHmms.get(name).getNumStates());
        System.out.print("States : ");
        for(String state:gestureHmms.get(name).getStates())
            System.out.print(state+", ");
        System.out.println();
        gestureHmms.get(name).printAllProbability();
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++");
    }

    /**
     * print the summay of the dynamic threshold model on the screen
     */
    public void printThresholdModel(){
        System.out.println("+++++++++ Summary of Threshold model +++++++++++");
        System.out.println("Number of states : " + thresholdModel.getNumStates());
        System.out.print("States : ");
        for(String state:thresholdModel.getStates())
            System.out.print(state+", ");
        System.out.println();
        thresholdModel.printAllProbability();
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++");
    }
}
