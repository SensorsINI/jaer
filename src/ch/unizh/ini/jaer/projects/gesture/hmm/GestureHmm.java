/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

/**
 *
 * @author Jun Haeng Lee
 */
public class GestureHmm {
    final static String FINAL_STATE = "Final";
    final static String[] THRESHOLD_STATE = {FINAL_STATE};
    final static double[] THRESHOLD_TP = {1.0};

    // number of defined gestures in a set
    private int numGestures = 0;

    // Hashmap to store HiddenMarkovModels for gestures
    HashMap<String, HiddenMarkovModel> gestureHmms= new HashMap<String, HiddenMarkovModel>();

    // HMM for the threshold model
    HiddenMarkovModel thresholdModel;
    
    // set of feature vectors
    private ArrayList<String> featureVectorSpace = new ArrayList<String>();

    public GestureHmm(String [] featureVectorSpace) {
        for(String fv:featureVectorSpace)
            this.featureVectorSpace.add(fv);

        thresholdModel = new HiddenMarkovModel("Threshold model", THRESHOLD_STATE, getFeatureVectorSpaceToArray());
        thresholdModel.initializeProbabilityMatrix(THRESHOLD_TP, null, null);
    }

    public HiddenMarkovModel addGesture(String name, int numStates){
        String[] stateNames = getStateNamesToArray(name, numStates);
        HiddenMarkovModel hmm = new HiddenMarkovModel(name, stateNames, getFeatureVectorSpaceToArray());

        gestureHmms.put(name, hmm);
        numGestures++;

        return hmm;
    }

    public void addGesture(HiddenMarkovModel hmm){
        gestureHmms.put(hmm.getName(), hmm);
        numGestures++;
    }

    public void removeGesture(String name){
        gestureHmms.remove(name);
        numGestures--;
    }

    public void initializeGesture(String name, double[] startProb, double[][] transitionProb, double[][] emissionProb){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        hmm.setStartProbability(startProb);
        hmm.setTransitionProbability(transitionProb);
        hmm.setEmissionProbability(emissionProb);
    }

    public void initializeGestureRandomErgodic(String name){
        gestureHmms.get(name).setProbabilityRandomErgodic();
    }

    public void initializeGestureRandomLeftRight(String name, int numStartStates){
        gestureHmms.get(name).setProbabilityRandomLeftRight(numStartStates);
    }

    public void initializeGestureRandomLeftRightBanded(String name){
        gestureHmms.get(name).setProbabilityRandomLeftRightBanded();
    }

    private String[] getStateNamesToArray(String name, int numStates){
        String[] stateNames = new String[numStates];

        for(int i=0; i<numStates; i++)
            stateNames[i] = String.format("%s_%d", name, i);

        return stateNames;
    }

    public HiddenMarkovModel getGestureHmm(String name){
        return gestureHmms.get(name);
    }

    public boolean learnGesture(String name, String[] observation){
        double minProb = 0.00001;
        boolean learningDone = false;

        HiddenMarkovModel hmm = gestureHmms.get(name);

        if(hmm.getNumTraining() == 0){

            double[] sProb = hmm.getStartProbabilityToArray();
            double[][] tProb = hmm.getTransitionProbabilityToArray();
            double [][] eProb = hmm.getEmissionProbabilityToArray();


            while(!(learningDone = hmm.BaumWelch(observation, minProb, 0.001, false)) && minProb < 1){
                hmm.initializeProbabilityMatrix(sProb, tProb, eProb);
                minProb *= 10;
            }

        } else {
            HiddenMarkovModel newHmm = new HiddenMarkovModel(name, hmm.getStatesToArray(), getFeatureVectorSpaceToArray());

            newHmm.initializeProbabilityMatrix(hmm.getStartProbabilityToArray(), hmm.getTransitionProbabilityToArray(), hmm.getEmissionProbabilityToArray());

            while(!(learningDone = newHmm.BaumWelch(observation, minProb, 0.001, false)) && minProb < 1){
                newHmm.initializeProbabilityMatrix(hmm.getStartProbabilityToArray(), hmm.getTransitionProbabilityToArray(), hmm.getEmissionProbabilityToArray());
                minProb *= 10;
            }

            if(learningDone)
                hmm.ensenbleAverage(newHmm);
        }

        if(!learningDone){
            removeGesture(name);
            System.out.println("Learning of "+name+" is failed.");
        }

        return learningDone;
    }


    public static String[][] genCompleteObsSet(String[] featureVectorSpace, int obsLength, boolean showObsSet){
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


    public double getGestureLikelyhood(String name, String[] obs){
        return gestureHmms.get(name).forward(obs);
    }

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

//        System.out.println("Maximum likelyhood = " + maxProb);
        return name;
    }

    public String[] getFeatureVectorSpaceToArray() {
        String[] out = new String[featureVectorSpace.size()];

        for(int i=0; i<featureVectorSpace.size(); i++)
            out[i] = featureVectorSpace.get(i);

        return out;
    }

    public int getNumGestures() {
        return numGestures;
    }

    public void updateThresholdModelWith(String gestureName){
        HiddenMarkovModel hmm = gestureHmms.get(gestureName);

        ArrayList<String> states = thresholdModel.getStates();
        Hashtable<String, Double> sp = thresholdModel.getStartProbability();
        Hashtable<String, Hashtable> tp = thresholdModel.getTransitionProbability();
        Hashtable<String, Hashtable> ep = thresholdModel.getEmissionProbability();

        for(String state : hmm.getStatesToArray()){
            if(!states.contains(state)){
                states.add(state);
                sp.put(state, 0.0);

                Hashtable<String, Double> newTP = new Hashtable<String, Double>();
                tp.put(state, newTP);

                Hashtable<String, Double> newEP = new Hashtable<String, Double>();
                ep.put(state, newEP);
            }

            tp.get(state).put(state, hmm.getTransitionProbability(state, state));
            for(String fv:featureVectorSpace)
                ep.get(state).put(fv, hmm.getEmissionProbability(state, fv));
        }

 //       double tpRelay =


    }

    public void printGesture(String name){
        if(gestureHmms.get(name) == null)
            return;

        System.out.println("+++++++++ Summary of " + name + " +++++++++++");
        System.out.println("Number of states : " + gestureHmms.get(name).getNumStates());
        gestureHmms.get(name).printAllProbability();
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++");
    }
}
