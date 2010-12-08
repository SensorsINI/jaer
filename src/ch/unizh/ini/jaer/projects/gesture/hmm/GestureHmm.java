/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * HMM based gesture recognition module
 * @author Jun Haeng Lee
 */
public class GestureHmm implements Serializable{
    static final String START_STATE = "Start";
    static final String FINAL_STATE = "Final";
    static final String DYNAMIC_THRESHOLD_MODEL  = "Dynamic Threshold Model";
    static final String[] THRESHOLD_STATE = {START_STATE, FINAL_STATE};
    static final double[] THRESHOLD_START_PROB = {1.0, 0.0};

    /**
     * threshold options
     */
    public static final int NO_THRESHOLD       = 0x00;
    public static final int GAUSSIAN_THRESHOLD = 0x01;
    public static final int FIXED_THRESHOLD    = 0x02;
    public static final int DYNAMIC_THRESHOLD  = 0x04;

    private static final long serialVersionUID = -2561957221321392504L;

    /**
     * number of defined gestures in a set
     */
    private int numGestures = 0;

    /**
     * gesture names
     */
    HashSet<String> gestureNames = new HashSet<String>();

    /**
     * Hashmap to store HiddenMarkovModels for gestures
     */
    HashMap<String, HiddenMarkovModel> gestureHmms = new HashMap<String, HiddenMarkovModel>();

    /**
     * Hashmap to store minimum likelyhood of learned gestures.
     * This is used as a reference to distinguish the target gesture from gabige gesture when the dynamic threshold model is not used.
     */
    HashMap<String, Double> refLikelyhood= new HashMap<String, Double>();

    /**
     * Hashmap to store gaussian thredhold models.
     * This is used as a reference to distinguish the target gesture from gabige gesture when the dynamic threshold model is not used.
     */
    HashMap<String, GaussianThreshold> gthModels= new HashMap<String, GaussianThreshold>();

    /**
     * HMM for the threshold model
     * Dynamic threshold model was introduced by H. K Lee and J. H. Kim in their paper IEEE Trans. PAMI Oct. 1999.
     */
    HiddenMarkovModel thresholdModel;
    
    /**
     * set of feature vectors
     */
    private ArrayList<String> featureVectorSpace = new ArrayList<String>();

    /**
     * threshold option
     * one of NO_THRESHOLD, GAUSSIAN_THRESHOLD, FIXED_THRESHOLD, DYNAMIC_THRESHOLD, GAUSSIAN_THRESHOLD | DYNAMIC_THRESHOLD
     */
    private int thresholdOption;

    /**
     * Gaussian threshold criterion
     */
    private float GTCriterion = 3.0f;

    /**
     * Constructor with feature vector space
     * @param featureVectorSpace : feature vector space
     * @param thresholdOption : one of NO_THRESHOLD, GAUSSIAN_THRESHOLD, FIXED_THRESHOLD, DYNAMIC_THRESHOLD, GAUSSIAN_THRESHOLD | DYNAMIC_THRESHOLD
     */
    public GestureHmm(String [] featureVectorSpace, int thresholdOption) {
        for(String fv:featureVectorSpace)
            this.featureVectorSpace.add(fv);

        if((thresholdOption&FIXED_THRESHOLD) > 0 && (thresholdOption&DYNAMIC_THRESHOLD) > 0){
            System.out.println("FIXED_THRESHOLD and DYNAMIC_THRESHOLD cannot be used simulateously. DYNAMIC_THRESHOLD will be applied.");
            thresholdOption &= (0xff - FIXED_THRESHOLD);
        }
        this.thresholdOption = thresholdOption;
        
        thresholdModel = new HiddenMarkovModel(DYNAMIC_THRESHOLD_MODEL, THRESHOLD_STATE, getFeatureVectorSpaceToArray(), HiddenMarkovModel.ModelType.USER_DEFINED);
        thresholdModel.initializeProbabilityMatrix(THRESHOLD_START_PROB, null, null);
        // set transition matrix of the final state
        thresholdModel.setTransitionProbability(FINAL_STATE, START_STATE, 1.0);
        thresholdModel.setStateSilent(START_STATE);
        thresholdModel.setStateSilent(FINAL_STATE);
    }

    /**
     * adds a new gesture
     * @param name : gesture name
     * @param numStates : number of states to be used in HMM for this gesture
     * @param modelType
     * @return : HMM
     */
    public HiddenMarkovModel addGesture(String name, int numStates, HiddenMarkovModel.ModelType modelType){
        String[] stateNames = composeStateNamesToArray(name, numStates);
        HiddenMarkovModel hmm = new HiddenMarkovModel(name, stateNames, getFeatureVectorSpaceToArray(), modelType);

        gestureNames.add(name);
        gestureHmms.put(name, hmm);
        refLikelyhood.put(name, 0.0);

        boolean doBestMatching = false;
        if(modelType == HiddenMarkovModel.ModelType.LRC_RANDOM ||  modelType == HiddenMarkovModel.ModelType.LRBC_RANDOM)
            doBestMatching = true;
        gthModels.put(hmm.getName(), new GaussianThreshold(featureVectorSpace.size(), 3*Math.PI/180, GaussianThreshold.Type.CIRCULATING_ANGLE, doBestMatching));

        numGestures++;

        return hmm;
    }

    /**
     * adds a new gesture
     * @param hmm : hmm of the gesture
     */
    public void addGesture(HiddenMarkovModel hmm){
        gestureNames.add(hmm.getName());
        gestureHmms.put(hmm.getName(), hmm);
        refLikelyhood.put(hmm.getName(), 0.0);

        boolean doBestMatching = false;
        HiddenMarkovModel.ModelType modelType = hmm.getModelType();
        if(modelType == HiddenMarkovModel.ModelType.LRC_RANDOM ||  modelType == HiddenMarkovModel.ModelType.LRBC_RANDOM)
            doBestMatching = true;
        gthModels.put(hmm.getName(), new GaussianThreshold(featureVectorSpace.size(), 3*Math.PI/180, GaussianThreshold.Type.CIRCULATING_ANGLE, doBestMatching));

        numGestures++;
    }

    /**
     * removes the specified gesture
     * @param name : gesture name
     */
    public void removeGesture(String name){
        removeFromThresholdModel(name);
        gestureHmms.remove(name);
        refLikelyhood.remove(name);
        gthModels.remove(name);
        gestureNames.remove(name);
        numGestures--;
    }

    /**
     * initializes a gesture HMM
     * @param name : gesture name
     * @param startProb : start probability of HMM
     * @param transitionProb : transition probability of HMM
     * @param emissionProb : emission probability of HMM
     */
    public void initializeGesture(String name, double[] startProb, double[][] transitionProb, double[][] emissionProb){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        if(hmm == null)
            return;

       hmm.initializeProbabilityMatrix(startProb, transitionProb, emissionProb);

        if(hmm.getModelType() == HiddenMarkovModel.ModelType.USER_DEFINED)
            hmm.saveInitialProbabilty();

        updateThresholdModelWith(name);
    }

    /**
     * initializes a gesture with random probabilities
     * @param name : gesture name
     */
    public void initializeGestureRandom(String name){
        HiddenMarkovModel hmm = getGestureHmm(name);

        if(hmm == null)
            return;

        hmm.setProbabilityRandom();

        updateThresholdModelWith(name);
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
     * returns gesture HMM
     * @param name : gesture name
     * @return : HMM
     */
    public HiddenMarkovModel getGestureHmm(String name){
        return gestureHmms.get(name);
    }

    /**
     * learns gesture with an observation sequence
     * @param name : gesture name
     * @param obsSeq : observation sequence
     * @param updateStartProb
     * @param updateEmissionProb
     * @param updateTranstionProb
     * @return
     */
    public boolean learnGesture(String name, String[] obsSeq, boolean updateStartProb, boolean updateTranstionProb, boolean updateEmissionProb){
        double minProb = 0.00001;
        boolean learningDone = false;

        HiddenMarkovModel hmm = gestureHmms.get(name);

        if(hmm == null)
            return false;

        // when this is the first training
        if(hmm.getNumTraining() == 0){
            while(!(learningDone = hmm.BaumWelch(obsSeq, minProb, 0.001, updateStartProb, updateTranstionProb, updateEmissionProb, false)) && minProb < 1){
                if(hmm.getModelType() == HiddenMarkovModel.ModelType.USER_DEFINED)
                    hmm.initializeProbabilityMatrix(hmm.getInitialStartProbabilityToArray(), hmm.getInitialTransitionProbabilityToArray(), hmm.getInitialEmissionProbabilityToArray());
                else
                    hmm.setProbabilityRandom();
                minProb *= 10;
            }

            if(learningDone){
                // if the HMM model type is LRC or LRBC,
                if(hmm.getModelType() == HiddenMarkovModel.ModelType.LRC_RANDOM || hmm.getModelType() == HiddenMarkovModel.ModelType.LRBC_RANDOM)
                    hmm.saveInitialProbabilty();
            } else {
                removeGesture(name);
                System.out.println("Learning of "+name+" is failed.");
            }

        } else { // when this is not the first training. All training results are ensenble averaged
            HiddenMarkovModel newHmm = new HiddenMarkovModel(name, hmm.getStatesToArray(), getFeatureVectorSpaceToArray(), hmm.getModelType());
            if(hmm.getModelType() == HiddenMarkovModel.ModelType.USER_DEFINED || hmm.getModelType() == HiddenMarkovModel.ModelType.LRC_RANDOM || hmm.getModelType() == HiddenMarkovModel.ModelType.LRBC_RANDOM)
                newHmm.initializeProbabilityMatrix(hmm.getInitialStartProbabilityToArray(), hmm.getInitialTransitionProbabilityToArray(), hmm.getInitialEmissionProbabilityToArray());
            else
                newHmm.setProbabilityRandom();

            while(!(learningDone = newHmm.BaumWelch(obsSeq, minProb, 0.001, updateStartProb, updateTranstionProb, updateEmissionProb, false)) && minProb < 1){
                if(hmm.getModelType() == HiddenMarkovModel.ModelType.USER_DEFINED)
                    newHmm.initializeProbabilityMatrix(hmm.getInitialStartProbabilityToArray(), hmm.getInitialTransitionProbabilityToArray(), hmm.getInitialEmissionProbabilityToArray());
                else
                    newHmm.setProbabilityRandom();

                minProb *= 10;
            }

            if(learningDone){
                hmm.ensenbleAverage(newHmm);

                // if the HMM model type is LRC or LRBC,
                if(hmm.getModelType() == HiddenMarkovModel.ModelType.LRC_RANDOM || hmm.getModelType() == HiddenMarkovModel.ModelType.LRBC_RANDOM)
                    hmm.saveInitialProbabilty();
            }else
                System.out.println("Learning of "+name+" is failed.");

        }

        if(learningDone){
            // Save minimum value
            if(refLikelyhood.get(name) > hmm.forward(obsSeq))
                refLikelyhood.put(name, hmm.forward(obsSeq));

            updateThresholdModelWith(name);
        }

        return learningDone;
    }

    /**
     * learns gesture with an observation sequence and law feature vectors
     * @param name : gesture name
     * @param obsSeq : observation sequence
     * @param rawAngles : not quantized angle sequence
     * @param updateStartProb
     * @param updateEmissionProb
     * @param updateTranstionProb
     * @return
     */
    public boolean learnGesture(String name, String[] obsSeq, double[] rawAngles, boolean updateStartProb, boolean updateTranstionProb, boolean updateEmissionProb){
        boolean learningSuccess = learnGesture(name, obsSeq, updateStartProb, updateTranstionProb, updateEmissionProb);

        if(learningSuccess){
            GaussianThreshold gth = gthModels.get(name);

            gth.addSample(rawAngles);
        }

        return learningSuccess;
    }

    /** resets the specified gesture
     *
     * @param name
     */
    public void resetGesture(String name){
        HiddenMarkovModel hmm = getGestureHmm(name);

        if(hmm == null)
            return;

        hmm.setNumTraining(0);
        if(hmm.getModelType() == HiddenMarkovModel.ModelType.USER_DEFINED)
            hmm.initializeProbabilityMatrix(hmm.getInitialStartProbabilityToArray(), hmm.getInitialTransitionProbabilityToArray(), hmm.getInitialEmissionProbabilityToArray());
        else
            hmm.setProbabilityRandom();

        refLikelyhood.put(name, 0.0);
        gthModels.get(name).reset();
    }

    /**
     * generates complete set of observation seqeunces with the specified length
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
     * returns the likelyhood of observation sequence in the given gesture
     * @param name : gesture name
     * @param obs : observation sequence
     * @return : likelyhood
     */
    public double getGestureLikelyhood(String name, String[] obs){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        if(hmm == null)
            return 0.0;

        return hmm.forward(obs);
    }

    /**
     * returns the likelyhood of a gesture in the threshold model
     * Dynamic threshold model was introduced by H. K Lee and J. H. Kim in their paper IEEE Trans. PAMI Oct. 1999.
     * @param scaleFactor 
     * @param obs
     * @return
     */
    public double getGestureLikelyhoodTM(double scaleFactor, String[] obs){
        return thresholdModel.forward(obs)*scaleFactor;
    }
    

    /**
     * returns the name of the best matching gesture
     * @param obs : observation sequence
     * @return : gesture name
     */
    public String getBestMatchingGesture(String[] obs){
        String name = null;
        double maxProb = 0.0;

        // finds a gesture having maximum likelyhood.
        for(HiddenMarkovModel hmm : gestureHmms.values()){
            double prob = hmm.forward(obs);
            if(prob > maxProb){
                name = hmm.getName();
                maxProb = prob;
            }
        }

        // checks state transition for LRB and LRBC types. 
        // If the viterbi pathes doesn't include all states, this may not be a proper guess.
        HiddenMarkovModel.ModelType modelType = getGestureHmm(name).getModelType();
        if(modelType == HiddenMarkovModel.ModelType.LRB_RANDOM || modelType == HiddenMarkovModel.ModelType.LRBC_RANDOM){
            getGestureHmm(name).viterbi(obs);
            ArrayList<String> viterbiPath = getGestureHmm(name).getViterbiPath(obs.length);
            HashSet<String> visitedStates = new HashSet<String>();
            for(int i=0; i<viterbiPath.size(); i++){
                String state = viterbiPath.get(i);
                visitedStates.add(state);
            }
            if(visitedStates.size() != getGestureHmm(name).getStates().size())
                return null;
        }

        // checks with dynamic threshold.
        if((thresholdOption&DYNAMIC_THRESHOLD)>0){
            if(maxProb < getGestureLikelyhoodTM(1.0, obs))
                name = null;
        } else if ((thresholdOption&FIXED_THRESHOLD)>0) {
            if(maxProb < refLikelyhood.get(name))
                name = null;
        }

//        System.out.println("Maximum likelyhood = " + maxProb);
        return name;
    }
    
    /**
     * returns the name of the best matching gesture
     * @param obs : observation sequence
     * @param rawAngles : raw angle sequence
     * @return : gesture name
     */
    public String getBestMatchingGesture(String[] obs, double[] rawAngles){
        String name = null;
        double maxProb = 0.0;

        // finds a gesture having maximum likelyhood.
        for(HiddenMarkovModel hmm : gestureHmms.values()){
            double prob = hmm.forward(obs);
            if(prob > maxProb){
                name = hmm.getName();
                maxProb = prob;
            }
        }

        if(name == null)
            return null;

        // checks state transition for LRB and LRBC types.
        // If the viterbi pathes doesn't include all states, this may not be a proper guess.
        HiddenMarkovModel.ModelType modelType = getGestureHmm(name).getModelType();
        if(modelType == HiddenMarkovModel.ModelType.LRB_RANDOM || modelType == HiddenMarkovModel.ModelType.LRBC_RANDOM){
            getGestureHmm(name).viterbi(obs);
            ArrayList<String> viterbiPath = getGestureHmm(name).getViterbiPath(obs.length);
            HashSet<String> visitedStates = new HashSet<String>();
            for(int i=0; i<viterbiPath.size(); i++){
                String state = viterbiPath.get(i);
                visitedStates.add(state);
            }
            if(visitedStates.size() != getGestureHmm(name).getStates().size()){
//                System.out.println("Not full state transition on HMM");
                return null;
            }
        }

        // checks with Gaussian threshold
        if((thresholdOption&GAUSSIAN_THRESHOLD)>0){
            GaussianThreshold gth = gthModels.get(name);
            if(gth.numSamples > 0){
                if(!gth.isAboveThreshold2(rawAngles, GTCriterion)){
//                    System.out.println("Blocked by GTM");
                    return null;
                }
            }
        }

        // checks with dynamic threshold.
        if((thresholdOption&DYNAMIC_THRESHOLD)>0){
            if(maxProb < getGestureLikelyhoodTM(1.0, obs))
                name = null;
        } else if ((thresholdOption&FIXED_THRESHOLD)>0) {
            if(maxProb < refLikelyhood.get(name))
                name = null;
        }

//        System.out.println("Maximum likelyhood = " + maxProb);
        return name;
    }

    /**
     *  tries an observation to a specific gesture
     *
     * @param gestureName
     * @param obs
     * @param rawAngles
     * @return true if the observation satisfies all the criterion
     */
    public boolean tryGesture(String gestureName, String[] obs, double[] rawAngles){
        boolean ret = true;

        // checks state transition for LRB and LRBC types.
        // If the viterbi pathes doesn't include all states, this may not be a proper guess.
        HiddenMarkovModel.ModelType modelType = getGestureHmm(gestureName).getModelType();
        if(modelType == HiddenMarkovModel.ModelType.LRB_RANDOM || modelType == HiddenMarkovModel.ModelType.LRBC_RANDOM){
            getGestureHmm(gestureName).viterbi(obs);
            ArrayList<String> viterbiPath = getGestureHmm(gestureName).getViterbiPath(obs.length);
            HashSet<String> visitedStates = new HashSet<String>();
            for(int i=0; i<viterbiPath.size(); i++){
                String state = viterbiPath.get(i);
                visitedStates.add(state);
            }
            if(visitedStates.size() != getGestureHmm(gestureName).getStates().size()){
                ret = false;
            }
        }

        // checks with Gaussian threshold
        if((thresholdOption&GAUSSIAN_THRESHOLD)>0){
            GaussianThreshold gth = gthModels.get(gestureName);
            if(gth.numSamples > 0)
                if(!gth.isAboveThreshold2(rawAngles, GTCriterion))
                    ret = false;
        }

        // checks with dynamic threshold.
        if((thresholdOption&DYNAMIC_THRESHOLD)>0){
            double prob = gestureHmms.get(gestureName).forward(obs);
            if(prob < getGestureLikelyhoodTM(1.0, obs))
                ret = false;
        } else if ((thresholdOption&FIXED_THRESHOLD)>0) {
            double prob = gestureHmms.get(gestureName).forward(obs);
            if(prob < refLikelyhood.get(gestureName))
                ret = false;
        }

        return ret;
    }

    /**
     * returns the feature vector space in array
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
     * returns gesture names
     * 
     * @return
     */
   public HashSet<String> getGestureNames(){
       return gestureNames;
   }    

    /**
     * returns the average features in Array
     * @param gestureName
     * @return
     */
    public double[] getAverageFeaturesToArray(String gestureName){
        GaussianThreshold gth = gthModels.get(gestureName);

        if(gth == null)
            return null;

        return gth.getMuToArray();
    }

    public GaussianThreshold getGTModel(String name){
        return gthModels.get(name);
    }

    public float getGTCriterion() {
        return GTCriterion;
    }

    public void setGTCriterion(float GTcriterion) {
        this.GTCriterion = GTcriterion;
    }



    /**
     * updates threshold model with the specified gesture
     * Dynamic threshold model was introduced by H. K Lee and J. H. Kim in their paper IEEE Trans. PAMI Oct. 1999.
     * @param gestureName : gesture name
     */
    public void updateThresholdModelWith(String gestureName){
        HiddenMarkovModel hmm = gestureHmms.get(gestureName);

        if(hmm == null)
            return;

        ArrayList<String> states = thresholdModel.getStates();
        HashMap<String, Double> startProb = thresholdModel.getStartProbability();
        HashMap<String, HashMap> transitionProb = thresholdModel.getTransitionProbability();
        HashMap<String, HashMap> emissionProb = thresholdModel.getEmissionProbability();

        for(String state : hmm.getStatesToArray()){
            // if not contained
            if(states.contains(state))
               states.remove(state);

            // add state
            states.add(state);
            // set the added state as a non-silent state
            thresholdModel.setStateNonSilent(state);

            // set start probability zero
            startProb.put(state, 0.0);

            // add a new row in the transition probability matirx
            HashMap<String, Double> newTransitionProb = new HashMap<String, Double>();
            for(String nextState:hmm.getStates())  // create zero transition matrix
                newTransitionProb.put(nextState, 0.0);
            transitionProb.put(state, newTransitionProb);

            // add a new row in the emission probability matrix
            HashMap<String, Double> newEmissionProb = new HashMap<String, Double>();
            emissionProb.put(state, newEmissionProb);

            // update transition probability
            for(String ss:states){
                transitionProb.get(ss).put(state, 0.0);
                transitionProb.get(state).put(ss, 0.0);
            }

            // set transition probability
            transitionProb.get(state).put(state, hmm.getTransitionProbability(state, state)); // add self transition
            transitionProb.get(state).put(FINAL_STATE, 1.0 - hmm.getTransitionProbability(state, state)); // add transition to final state

            // set emission probability
            for(String fv:featureVectorSpace)
                emissionProb.get(state).put(fv, hmm.getEmissionProbability(state, fv));
        }

        pruneIdenticalStatesInThresholdModel();
    }

    /**
     * re-calculates the transition probabilities of Start state
     */
    private void recalculateStartStateTransitionProb(){
        ArrayList<String> states = thresholdModel.getStates();
        HashMap<String, HashMap> transitionProb = thresholdModel.getTransitionProbability();
        for(String state:states)
            if(!state.equals(START_STATE) && !state.equals(FINAL_STATE))
                transitionProb.get(START_STATE).put(state, 1.0/(thresholdModel.getNumStates()-3.0));
    }


    /**
     * removes states of deleted guesture from the threshold model
     * Dynamic threshold model was introduced by H. K Lee and J. H. Kim in their paper IEEE Trans. PAMI Oct. 1999.
     * @param name
     */
    private void removeFromThresholdModel(String name){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        if(hmm == null)
            return;

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

        // re-calculates the transition probabilities of Start state
        recalculateStartStateTransitionProb();
    }

    /**
     * returns the symmetric relative entropy between states i and j based on emmision probabilities
     * Symmetric relative entropy was introduced by H. K Lee and J. H. Kim in their paper IEEE Trans. PAMI Oct. 1999.
     * @param state_i
     * @param state_j
     * @return
     */
    private double relatveEntropy(String state_i, String state_j){
        double rEntropy = 0;
        double epi_p, epj_p, tmp1, tmp2;

        HashMap<String, Double> epi = thresholdModel.getEmissionProbability().get(state_i);
        HashMap<String, Double> epj = thresholdModel.getEmissionProbability().get(state_j);

        for(String codeword:featureVectorSpace){
            epi_p = epi.get(codeword);
            epj_p = epj.get(codeword);

            if(epi_p == 0 || epj_p == 0){
                if(epi_p == 0 && epj_p == 0){
                    // do nothing
                }else{
                    rEntropy = Double.POSITIVE_INFINITY;
                    break;
                }
            }else{
                rEntropy += (epi_p*Math.log(epi_p/epj_p)+epj_p*Math.log(epj_p/epi_p));
            }
        }

        return rEntropy/2;
    }

    /**
     * prunes duplicate states in the dynamic threshold model
     */
    public void pruneIdenticalStatesInThresholdModel(){
        ArrayList<String> stateList = thresholdModel.getStates();
        ArrayList<String> pruneList = new ArrayList<String>();

        // do loop except Start and Final states
        for(int i=2; i<stateList.size()-1; i++){
            for(int j=i+1; j<stateList.size(); j++){
                if(relatveEntropy(stateList.get(i), stateList.get(j)) == 0)
                    pruneList.add(stateList.get(j));
            }
        }

        stateList.removeAll(pruneList);
        
        // re-calculates the transition probabilities of Start state
        recalculateStartStateTransitionProb();
    }

    /**
     * prints the summay of a gesture on the screen
     * @param name : gesture name
     */
    public void printGesture(String name){
        HiddenMarkovModel hmm = gestureHmms.get(name);

        if(hmm == null)
            return;

        System.out.println("+++++++++ Summary of " + name + " +++++++++++");
        System.out.println("Number of states : " + hmm.getNumStates());
        System.out.print("States : ");
        for(String state:hmm.getStates())
            System.out.print(state+", ");
        System.out.println();
        hmm.printAllProbability();
        System.out.println("+++++++++++++++++++++++++++++++++++++++++++");
    }

    /**
     * prints the summay of the dynamic threshold model on the screen
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
