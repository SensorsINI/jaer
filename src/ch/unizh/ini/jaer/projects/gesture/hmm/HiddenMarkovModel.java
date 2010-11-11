package ch.unizh.ini.jaer.projects.gesture.hmm;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Hidden Markov Model
 * @author Jun Haeng Lee
 */
public class HiddenMarkovModel implements Serializable{
    private static final long serialVersionUID = -717623833232998188L;
    
    /** HMM model type will be used to initialize the probabilities.
     * Ergodic HMM has full mesh transition between states.
     */
    public enum ModelType {
        /**
         * Ergodic model with random probablity matrix.
         * Every state has non-zero transition probablitities to all states (including itself).
         */
        ERGODIC_RANDOM,
        /**
         * Left-Right model with random probablity matrix.
         * i-th state has transition probabilities only to j-th state (j >= i).
         */
        LR_RANDOM,
        /**
         * Left-Right-Banded model with random probablity matrix.
         * i-th state has transition probabilities only to itself and (i+1)-th state.
         */
        LRB_RANDOM,
        /**
         * Left-Right circulating model with random probablity matrix.
         * In addition to LR_RANDOM, state transitions are allowed to ciculate.
         */
        LRC_RANDOM,
        /**
         * Left-Right-Banded circulating model with random probablity matrix.
         * In addition to LRB_RANDOM, state transitions are allowed to ciculate.
         */
        LRBC_RANDOM,
        /**
         * User-defined model
         */
        USER_DEFINED
    };

    /**
     * Name of HMM
     */
    private String name;
    /**
     * HMM model type. one of ModelType {ERGODIC_RANDOM, LR_RANDOM, LRB_RANDOM, LRC_RANDOM, LRBC_RANDOM, USER_DEFINED}
     */
    private ModelType modelType;
    /**
     * default number of states having start probability in LR HMM
     */
    private int numStartStates_LR = 2;
    /**
     * default number of states to which a state can make a direct transition in LR HMM
     */
    private int numStateDirectTransition_LR = 3;
    /**
     * States array
     */
    private ArrayList<String> states = new ArrayList<String>();
    /**
     * Array of observation space
     */
    private ArrayList<String> observationSet = new ArrayList<String>();
    /**
     * start probabilities of states
     */
    private Hashtable<String, Double> startProbability = new Hashtable<String, Double>();
    /**
     * transition probabilities between states
     */
    private Hashtable<String, Hashtable> transitionProbability = new Hashtable<String, Hashtable>();
    /**
     * probabilities of observations at each state
     */
    private Hashtable<String, Hashtable> emissionProbability = new Hashtable<String, Hashtable>();
    /**
     * silent state marker
     */
    private Hashtable<String, Boolean> silentState = new Hashtable<String, Boolean>();
    /**
     * maximum length of cascaded silent states
     */
    private int depthSilentStates = 0;

    /**
     * product of Viterbi algorithm
     */
    private Hashtable<String, Hashtable> delta = new Hashtable<String, Hashtable>();
    /**
     * product of forward algorithm
     */
    private Hashtable<String, Hashtable> alpha = new Hashtable<String, Hashtable>();
    /**
     * product of backward algorithm
     */
    private Hashtable<String, Hashtable> beta = new Hashtable<String, Hashtable>();
    /**
     * used in Baum-Welch algorithm
     */
    private Hashtable<String, Hashtable> gamma = new Hashtable<String, Hashtable>();
    private Hashtable<String, Hashtable> zeta = new Hashtable<String, Hashtable>();
    /**
     * by-product of scaled forward algorithm
     */
    private Hashtable<String, Double> scaleFactor = new Hashtable<String, Double>();


    /**
     * Initial values are saved to be used as the initial condition in each learning
     */
    private Hashtable<String, Double> initialStartProbability = new Hashtable<String, Double>(); // start probabilities of states
    private Hashtable<String, Hashtable> initialTransitionProbability = new Hashtable<String, Hashtable>(); // transition probabilities between states
    private Hashtable<String, Hashtable> initialEmissionProbability = new Hashtable<String, Hashtable>(); // probabilities of observations at each state

    /**
     * Random
     */
    protected static Random random = new Random();
    /**
     * indicate how many times this HMM has been trained
     */
    protected int numTraining = 0;
    /**
     * number of iteration in Baum-Welch Learning algorithm
     */
    private double numIteration;
    /**
     * total probability in log scale before Baum-Welch algorithm
     */
    private double logProbInit;
    /**
     * total probability in log scale after Baum-Welch algorithm
     */
    private double logProbFinal;


    /**
     * HMM Constructor with name
     * Must be followed by initializeHmm(String [] states, String [] obsSet) to initialize HMM.
     * @param name : name of HMM instance
     */
    public HiddenMarkovModel(String name) {
        this.name = new String(name);
    }

    /**
     * Constructor with name, states list, and observation set list
     * @param name : name of HMM instance
     * @param states : states set of HMM
     * @param observationSpace : observation space of HMM
     * @param modelType
     */
    public HiddenMarkovModel(String name, String [] states, String [] observationSpace, ModelType modelType) {
        this(name);
        this.modelType = modelType;
        initializeHmm(states, observationSpace);
    }

    /**
     * Initilize HMM with states and observation space
     * @param states : states set of HMM
     * @param obsSpace : observation space of HMM
     */
    public void initializeHmm(String [] states, String [] obsSpace) {

        if(states == null)
            return;

        for(int i = 0; i<states.length;i++){
            // states
            this.states.add(states[i]);
            setSilentState(states[i], false);

            // start probability
            startProbability.put(states[i], 0.0);

            // transition probability
            Hashtable<String, Double> tp = new Hashtable<String, Double>();
            transitionProbability.put(states[i],tp);
            for(int j = 0; j<states.length;j++)
                tp.put(states[j],new Double(0.0));

            // emission probability
            Hashtable<String, Double> ep = new Hashtable<String, Double>();
            emissionProbability.put(states[i],ep);
            for(int j = 0; j<obsSpace.length;j++){
                ep.put(obsSpace[j],new Double(0.0));
                if(i == 0)
                    observationSet.add(obsSpace[j]);
            }
        }
    }


    /**
     * Set start probability of the specified state
     * @param state : state to set
     * @param prob : start probability
     */
    public void setStartProbability(String state, double prob){
        startProbability.put(state, new Double(prob));
    }

    /**
     * Set start probability of all states
     * @param prob : array of start probability.
     * It follows the order of states which can be seen by using the method 'ArrayList<String> getStates()' or 'String[] getStatesToArray()'
     */
    public void setStartProbability(double [] prob){
        for(int i = 0; i<states.size(); i++)
            setStartProbability(states.get(i), prob[i]);
    }

    /**
     * Set start probability of all states to random values
     */
    public void setStartProbabilityRandom(){
        double sum = 0, prob;
        for(String state: states){
            prob = random.nextDouble();
            setStartProbability(state, prob);
            sum += prob;
        }

        for(String state: states)
            setStartProbability(state, startProbability.get(state)/sum);
    }

    /**
     * set transition probability from 'state' to 'nextState'
     * @param state : source state
     * @param nextState : target state
     * @param prob : transition probability from source state to target state
     */
    public void setTransitionProbability(String state, String nextState, double prob){
        Hashtable<String, Double> tp = transitionProbability.get(state);
        tp.put(nextState ,new Double(prob));
    }

    /**
     * Set transition probabilities between states (N to N mapping)
     * N : number of states
     * @param prob : 2D array of transition probability
     * It follows the order of states which can be seen by using the method 'ArrayList<String> getStates()' or 'String[] getStatesToArray()'
     */
    public void setTransitionProbability(double[][] prob){
        for(int i = 0; i<states.size(); i++)
            for(int j = 0; j<states.size(); j++)
                setTransitionProbability(states.get(i), states.get(j), prob[i][j]);
    }

    /**
     * Set all transition probabilities to random values
     */
    public void setTransitionProbabilityRandom(){
        double sum, prob;
        for(String sourceState: states) {
            sum = 0;
            for(String targetState: states){
                prob = random.nextDouble();
                setTransitionProbability(sourceState, targetState, prob);
                sum += prob;
            }
            for(String targetState: states)
                setTransitionProbability(sourceState, targetState, getTransitionProbability(sourceState, targetState)/sum);
        }
    }

    /**
     * Set emission probability
     * @param state : state to set
     * @param observation : observation to emit
     * @param prob : emission probability
     */
    public void setEmissionProbability(String state, String observation, double prob){
        Hashtable<String, Double> tp = emissionProbability.get(state);
        if(!isSilentState(state)){
            tp.put(observation,new Double(prob));
            setSilentState(state, checkSilentState(state));
        }
    }

    /**
     * Set all emission probabilities (N to M mapping)
     * N : number of states, M: number of observations in observation space
     * @param prob : 2D array of emission probability
     */
    public void setEmissionProbability(double[][] prob){
        for(int i = 0; i<states.size(); i++)
            if(!isSilentState(states.get(i)))
                for(int j = 0; j<observationSet.size(); j++)
                    setEmissionProbability(states.get(i), observationSet.get(j), prob[i][j]);

        calDepthSilentStates();
    }

    /**
     * Set all emission probabilities to random values
     */
    public void setEmissionProbabilityRandom(){
        double sum, prob;
        for(String state: states) {
            sum = 0;
            if(!isSilentState(state)){
                for(String obs: observationSet){
                    prob = random.nextDouble();
                    setEmissionProbability(state, obs, prob);
                    sum += prob;
                }
                for(String obs: observationSet)
                    setEmissionProbability(state, obs, getEmissionProbability(state, obs)/sum);
            }
        }

        calDepthSilentStates();
    }

    /**
     * Initialize HMM with random probabilities.
     * Ergodic HMM has full mesh transition between states
     */
    public void setProbabilityRandom(){
        switch(modelType){
            case ERGODIC_RANDOM:
                setProbabilityRandomErgodic();
                break;
            case LR_RANDOM:
            case LRC_RANDOM:
                setProbabilityRandomLeftRight();
                break;
            case LRB_RANDOM:
            case LRBC_RANDOM:
                setProbabilityRandomLeftRightBanded();
                break;
            default:
                break;
        }
    }

    /**
     * Initialize HMM to Ergodic one with random probabilities.
     * Ergodic HMM has full mesh transition between states
     */
    private void setProbabilityRandomErgodic(){
        setStartProbabilityRandom();
        setTransitionProbabilityRandom();
        setEmissionProbabilityRandom();
    }

    /**
     * Initialize HMM to Left-Right one with random probabilities.
     * In Left-Right HMM, each state has no transition to previous states.
     * @param numStartStates : number of states which have start probability
     * only first n states will have start probability
     */
    private void setProbabilityRandomLeftRight(){
        setProbabilityRandomErgodic();

        // Transition and Emission probabilities
        double sumSP = 0;
        for(int i=0; i<states.size(); i++){
            if(modelType == ModelType.LR_RANDOM){
                if(i >= numStartStates_LR)
                    setStartProbability(states.get(i), 0);
                else
                    sumSP += getStartProbability(states.get(i));
            } else
                setStartProbability(states.get(i),1.0/getNumStates());

            double sumTP = 0;
            for(int j=0; j<states.size(); j++){
                if(j < i || j >= i+numStateDirectTransition_LR){
                    setTransitionProbability(states.get(i), states.get(j), 0);
                    if(modelType == ModelType.LRC_RANDOM){
                        if(i+numStateDirectTransition_LR > getNumStates() && j+getNumStates()<numStateDirectTransition_LR+i){
                            setTransitionProbability(states.get(i), states.get(j), random.nextDouble());
                            sumTP +=getTransitionProbability(states.get(i), states.get(j));
                        }
                    }
                }else
                    sumTP += getTransitionProbability(states.get(i), states.get(j));
            }

            for(int j=0; j<states.size(); j++){
                if(i <= j || j < i+numStateDirectTransition_LR)
                    setTransitionProbability(states.get(i), states.get(j), getTransitionProbability(states.get(i), states.get(j))/sumTP);
            }
            
//            if(i == states.size()-1)
//                for(int k=0; k<observationSet.size(); k++)
//                    setEmissionProbability(states.get(i), observationSet.get(k), 0);
        }

        if(modelType == ModelType.LR_RANDOM){
            for(int i=0; i<states.size(); i++)
                if(i < numStartStates_LR)
                    setStartProbability(states.get(i), getStartProbability(states.get(i))/sumSP);
        }

        calDepthSilentStates();
    }

    /**
     * Initialize HMM to Left-Right-Banded one with random probabilities.
     * In Left-Right-Banded HMM, each state has transition probabilities only to the very next state and itself.
     */
    private void setProbabilityRandomLeftRightBanded(){
        setProbabilityRandomErgodic();
        
        // Transition and Emission probabilities
        for(int i=0; i<states.size(); i++){
            if(modelType == ModelType.LRB_RANDOM){
                if(i > 0)
                    setStartProbability(states.get(i), 0);
                else
                    setStartProbability(states.get(i), 1.0);
            } else
                setStartProbability(states.get(i),1.0/getNumStates());

            for(int j=0; j<states.size(); j++){
                if(j == i) { /* do nothing */ }
                else if(j == i+1)
                    setTransitionProbability(states.get(i), states.get(j), 1.0 - getTransitionProbability(states.get(i), states.get(i)));
                else
                    setTransitionProbability(states.get(i), states.get(j), 0);
            }
            
        }
        if(modelType == ModelType.LRB_RANDOM)
            setTransitionProbability(states.get(states.size()-1), states.get(states.size()-1), 1.0);
        else{
            double circulatingProb = random.nextDouble();
            setTransitionProbability(states.get(getNumStates()-1), states.get(0), circulatingProb);
            setTransitionProbability(states.get(states.size()-1), states.get(states.size()-1), 1.0 - circulatingProb);
        }

        calDepthSilentStates();
    }

    /**
     * Initializes HMM with given probabilities
     * @param startProb : start probabilities
     * @param transitionProb : transition probabilities
     * @param emissionProb : emission probabilities
     */
    public void initializeProbabilityMatrix(double[] startProb, double[][] transitionProb, double[][] emissionProb){
        if(startProb != null)
            setStartProbability(startProb);
        if(transitionProb != null)
            setTransitionProbability(transitionProb);
        if(emissionProb != null)
            setEmissionProbability(emissionProb);
    }


    /**
     * Calculates Viterbi algorithm.
     * This method supports silent states
     * @param obs : observation sequence
     * @return {double totalProb, ArrayList<String> ViterbiPath, double probViterbiPath}
     */
    public Object[] viterbi(String[] obs)
    {
        /* value of delta = {prob, v_path, v_prob}
         * prob : the total probability of all paths from the start to the current state (constrained by the observations)
         * v_path : the Viterbi path up to the current state
         * v_prob : the probability of the Viterbi path up to the current state
         */
        Hashtable<String, Object[]> deltaInit = new Hashtable<String, Object[]>();

        int T =  obs.length;

        // Initial delta
        for (String state : states){
            ArrayList<String> pathList = new ArrayList<String>();
            pathList.add(state);
            double prob = 0;
            if(isSilentState(state))
                prob = getStartProbability(state);
            else
                prob  = getStartProbability(state)*getEmissionProbability(state, obs[0]);

            deltaInit.put(state, new Object[]{prob, pathList, prob});
        }

        // consider silent states
        for(int k=0; k<depthSilentStates; k++){
            for (String nextState : states)
            {
                double total = 0;
                ArrayList<String> argmax = new ArrayList<String>();
                double valmax = 0;

                boolean update = false;
                for (String sourceState : states)
                {
                    if(isSilentState(sourceState) && getTransitionProbability(sourceState, nextState) !=0){
                        Object[] objs = deltaInit.get(sourceState);
                        double prob = (Double) objs[0];
                        ArrayList<String> v_path = (ArrayList<String>) objs[1];
                        double v_prob = (Double) objs[2];

                        double p = 0;
                        if(isSilentState(nextState))
                            p = getTransitionProbability(sourceState, nextState);
                        else
                            p = getEmissionProbability(nextState, obs[0])*getTransitionProbability(sourceState, nextState);

                        prob *= p;
                        v_prob *= p;
                        total += prob;
                        if (v_prob > valmax)
                        {
                            argmax.clear();
                            argmax.addAll(v_path);
                            argmax.add(nextState);
                            valmax = v_prob;
                            update = true;
                        }
                    }
                }
                if(update){
                    deltaInit.put(nextState, new Object[]{total,argmax,valmax});
                }
            }
        }
        for(String state:states)
            if(isSilentState(state))
                deltaInit.put(state, new Object[]{0.0, new ArrayList<String>(), 0.0});
        
        delta.put(new String("1"), deltaInit);

        // loop for observations
        for (int t=2; t<=T; t++)
        {
            Hashtable<String, Object[]> deltaNext = new Hashtable<String, Object[]>();
            String prevObs = new String(""+(t-1));
            String currObs = new String(""+t);

            for (String nextState : states)
            {
                double total = 0;
                ArrayList<String> argmax = new ArrayList<String>();
                double valmax = 0;

                for (String sourceState : states)
                {
                    Object[] objs = getDelta(prevObs, sourceState);
                    double prob = (Double) objs[0];
                    ArrayList<String> v_path = (ArrayList<String>) objs[1];
                    double v_prob = (Double) objs[2];

                    double p = 0;
                    if(isSilentState(nextState))
                        p = getTransitionProbability(sourceState, nextState);
                    else
                        p = getEmissionProbability(nextState, obs[t-1])*getTransitionProbability(sourceState, nextState);

                    prob *= p;
                    v_prob *= p;
                    total += prob;
                    if (v_prob > valmax)
                    {
                        argmax.clear();
                        argmax.addAll(v_path);
                        argmax.add(nextState);
                        valmax = v_prob;
                    }
                }
                deltaNext.put(nextState, new Object[]{total,argmax,valmax});
            }

            // consider silent states
            for(int k=0; k<depthSilentStates; k++){
                for (String nextState : states)
                {
                    double total = 0;
                    ArrayList<String> argmax = new ArrayList<String>();
                    double valmax = 0;

                    boolean update = false;
                    for (String sourceState : states)
                    {
                        if(isSilentState(sourceState) && getTransitionProbability(sourceState, nextState) !=0){
                            Object[] objs = deltaNext.get(sourceState);
                            double prob = (Double) objs[0];
                            ArrayList<String> v_path = (ArrayList<String>) objs[1];
                            double v_prob = (Double) objs[2];

                            double p = 0;
                            if(isSilentState(nextState))
                                p = getTransitionProbability(sourceState, nextState);
                            else
                                p = getEmissionProbability(nextState, obs[t-1])*getTransitionProbability(sourceState, nextState);

                            prob *= p;
                            v_prob *= p;
                            total += prob;
                            if (v_prob > valmax)
                            {
                                argmax.clear();
                                argmax.addAll(v_path);
                                argmax.add(nextState);
                                valmax = v_prob;
                                update = true;
                            }
                        }
                    }
                    if(update){
                        deltaNext.put(nextState, new Object[]{total,argmax,valmax});
                    }
                }
            }
            for(String state:states)
                if(isSilentState(state))
                    deltaNext.put(state, new Object[]{0.0, new ArrayList<String>(), 0.0});

            delta.put(currObs, deltaNext);
        }

        double total = 0;
        ArrayList<String> argmax = null;
        double valmax = 0;

        for (String state : states)
        {
            if(!isSilentState(state)){
                Object[] objs = getDelta(new String(""+T), state);
                double prob = (Double) objs[0];
                ArrayList<String> v_path = (ArrayList<String>) objs[1];
                double v_prob = (Double) objs[2];
                total += prob;
                if (v_prob > valmax)
                {
                    argmax = v_path;
                    valmax = v_prob;
                }
            }
        }
        return new Object[]{total, argmax, valmax};
    }

    /**
     * Calculates the depth of silent states
     */
    private void calDepthSilentStates(){
        ArrayList<String> silentStatesList = new ArrayList<String>();

        depthSilentStates = 0;
        for(String state : states)
            if(isSilentState(state))
                silentStatesList.add(state);

        while(!silentStatesList.isEmpty()){
            depthSilentStates++;

            ArrayList<String> newSilentStatesList = new ArrayList<String>();

            for(String sourceState:silentStatesList){
                for(String targetState : states){
                    // self transition of silent states must be not considered to avoid infinite loop
                    if(!sourceState.equals(targetState) && getTransitionProbability(sourceState, targetState) != 0)
                        if(isSilentState(targetState))
                            newSilentStatesList.add(targetState);
                }
            }
            silentStatesList.clear();
            silentStatesList.addAll(newSilentStatesList);
        }
    }



    /**
     * Calculates forward algorithm.
     * Silent states are considered.
     * @param obs : observation sequence
     * @return : probability to observe the target sequence in this HMM
     */
/*    public double forward(String[] obs)
    {
        int T =  obs.length;
        // initializeHmm
        alpha.clear();

        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        // Consider silent states
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState : states)
            {
                double sum = getStartProbability(nextState);
                boolean update = false;
                for (String sourceState : states){
                    if(isSilentState(sourceState)){
                        double tp = getTransitionProbability(sourceState, nextState);
                        if(tp != 0) // to reduce the computational cost
                            sum += alphaInit.get(sourceState)*tp;
                        update = true;
                    }
                }
                if(update){
                    if(!isSilentState(nextState))
                        sum *= getEmissionProbability(nextState, obs[0]);
                    alphaInit.put(nextState, sum);
                }
            }
        }
        alpha.put(new String("1"), alphaInit);
        
        for (int t = 2; t <= T; t++)
        {
            Hashtable<String, Double> alphaNext = new Hashtable<String, Double>();
            String prevObs = new String(""+(t-1));
            String currObs = new String(""+t);

            for (String nextState : states)
            {
                double sum = 0;
                for (String sourceState : states){
                    double tp = getTransitionProbability(sourceState, nextState);
                    if(tp != 0) // to reduce the computational cost
                        sum += getAlpha(prevObs, sourceState)*tp;
                }

                if(!isSilentState(nextState))
                    sum *= getEmissionProbability(nextState, obs[t-1]);

                alphaNext.put(nextState, sum);
            }
            // Consider silent states
            for(int i=0; i<depthSilentStates; i++){
                for (String nextState2 : states)
                {
                    double sum2 = 0;
                    boolean update = false;
                    for (String sourceState2 : states){
                        if(isSilentState(sourceState2)){
                            double tp = getTransitionProbability(sourceState2, nextState2);
                            if(tp != 0) // to reduce the computational cost
                                sum2 += alphaNext.get(sourceState2)*tp;
                            update = true;
                        }else{
                            double tp = getTransitionProbability(sourceState2, nextState2);
                            if(tp != 0) // to reduce the computational cost
                                sum2 += getAlpha(prevObs, sourceState2)*tp;
                        }
                    }

                    if(update){
                        if(!isSilentState(nextState2))
                            sum2 *= getEmissionProbability(nextState2, obs[t-1]);

                        alphaNext.put(nextState2, sum2);
                    }
                }
            }
            
            alpha.put(currObs, alphaNext);
        }

        double retSum = 0;
        for (String state : states)
            if(!isSilentState(state))
                retSum += getAlpha(new String(""+T), state);

        return retSum;
    }
*/
    public double forward(String[] obs)
    {
        int T =  obs.length;
        // initializeHmm
        alpha.clear();

        Hashtable<String, Double> alphaInit = forwardInitialize(obs[0]);
        alpha.put(new String("1"), alphaInit);

        Hashtable<String, Double> alphaNext = alphaInit;
        for (int t = 2; t <= T; t++)
        {
            String currObs = new String(""+t);
            alphaNext = forwardUpdate(alphaNext, obs[t-1]);
            alpha.put(currObs, alphaNext);
        }

        double retSum = 0;
        for (String state : states)
            if(!isSilentState(state))
                retSum += getAlpha(new String(""+T), state);

        return retSum;
    }

    /**
     * Calculates initial forward probability with the first observation.
     * Silent states are considered.
     * @param obs : observation
     * @return
     */
    public Hashtable<String, Double> forwardInitialize(String obs)
    {
        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs));
        }
        // Consider silent states
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState : states)
            {
                double sum = getStartProbability(nextState);
                boolean update = false;
                for (String sourceState : states){
                    if(isSilentState(sourceState)){
                        double tp = getTransitionProbability(sourceState, nextState);
                        if(tp != 0) // to reduce the computational cost
                            sum += alphaInit.get(sourceState)*tp;
                        update = true;
                    }
                }
                if(update){
                    if(!isSilentState(nextState))
                        sum *= getEmissionProbability(nextState, obs);
                    alphaInit.put(nextState, sum);
                }
            }
        }

        return alphaInit;
    }


    /**
     * Updates forward probability with one additional observation.
     * Silent states are considered.
     * @param obs : observation
     * @return
     */
    public Hashtable<String, Double> forwardUpdate(Hashtable<String, Double> alphaPrev, String obs)
    {
        Hashtable<String, Double> alphaNext = new Hashtable<String, Double>();

        for (String nextState : states)
        {
            double sum = 0;
            for (String sourceState : states){
                double tp = getTransitionProbability(sourceState, nextState);
                if(tp != 0) // to reduce the computational cost
                    sum += alphaPrev.get(sourceState)*tp;
            }

            if(!isSilentState(nextState))
                sum *= getEmissionProbability(nextState, obs);

            alphaNext.put(nextState, sum);
        }
        // Consider silent states
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState2 : states)
            {
                double sum2 = 0;
                boolean update = false;
                for (String sourceState2 : states){
                    if(isSilentState(sourceState2)){
                        double tp = getTransitionProbability(sourceState2, nextState2);
                        if(tp != 0) // to reduce the computational cost
                            sum2 += alphaNext.get(sourceState2)*tp;
                        update = true;
                    }else{
                        double tp = getTransitionProbability(sourceState2, nextState2);
                        if(tp != 0) // to reduce the computational cost
                            sum2 += alphaPrev.get(sourceState2)*tp;
                    }
                }

                if(update){
                    if(!isSilentState(nextState2))
                        sum2 *= getEmissionProbability(nextState2, obs);

                    alphaNext.put(nextState2, sum2);
                }
            }
        }

        return alphaNext;
    }

    /**
     * Calculates forward algorithm with normailzation.
     * This method is used for Baum-Welch algorithm.
     * Silent states are NOT considered.
     * @param obs : observation sequence
     * @return : probability to observe the target sequence in this HMM
     */
    private double forwardScale(String[] obs)
    {
        int T =  obs.length;

        scaleFactor.clear();

        // initializeHmm
        double sf = 0.0;
        alpha.clear();

        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        for(String state : states)
            alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        
        for(String state : states)
            sf += alphaInit.get(state);
        scaleFactor.put(new String("1"), sf);
        for(String state : states)
            alphaInit.put(state, alphaInit.get(state)/sf);

        alpha.put(new String("1"), alphaInit);

        for (int t = 2; t <= T; t++)
        {
            Hashtable<String, Double> alphaNext = new Hashtable<String, Double>();
            String prevObs = new String(""+(t-1));
            String currObs = new String(""+t);

            sf = 0;
            for (String nextState : states)
            {
                double sum = 0;
                for (String sourceState : states){
                    double tp = getTransitionProbability(sourceState, nextState);
                    if(tp != 0) // to reduce the computational cost
                        sum += getAlpha(prevObs, sourceState)*tp;
                }

                sum *= getEmissionProbability(nextState, obs[t-1]);
                alphaNext.put(nextState, sum);
            }

            for (String nextState : states)
                sf += alphaNext.get(nextState);
            scaleFactor.put(currObs, sf);
            for (String state : states)
                alphaNext.put(state, alphaNext.get(state)/sf);

            alpha.put(currObs, alphaNext);
        }

        double retSum = 0;
        for (int t=1; t<=T; t++)
            retSum += Math.log(scaleFactor.get(new String(""+t)));

        return retSum;
    }

    /**
     * Calculates backward algorithm.
     * Silent states are considered.
     * @param obs : observation sequence
     * @return : probability to observe the target sequence in this HMM
     */
    public double backward(String[] obs)
    {
        int T = obs.length;

        // initializeHmm
        beta.clear();
        Hashtable<String, Double> betaFinal = new Hashtable<String, Double>();
        for(String state : states)
            betaFinal.put(state, 1.0);
        beta.put(new String(""+T), betaFinal);

        for (int t = T; t >= 1; t--)
        {
            Hashtable<String, Double> betaBefore = new Hashtable<String, Double>();
            String prevObs = new String(""+(t-1));
            String currObs = new String(""+t);

            for (String prevState : states)
            {
                double sum = 0;
                for (String currentState : states){
                    if(isSilentState(currentState)){
                        double tp = getTransitionProbability(prevState, currentState);
                        if(tp != 0) // to reduce the computational cost
                            sum += getBeta(currObs, currentState)*tp;
                    }else{
                        double tp = getTransitionProbability(prevState, currentState);
                        if(tp != 0) // to reduce the computational cost
                            sum += getBeta(currObs, currentState)*tp*getEmissionProbability(currentState, obs[t-1]);
                    }
                }

                betaBefore.put(prevState, sum);
            }

            // Consider silent states
            for(int i=0; i<depthSilentStates; i++){
                for (String prevState : states){
                    double sum = 0;
                    boolean update = false;
                    for (String currentState : states){
                        if(isSilentState(currentState)){
                            double tp = getTransitionProbability(prevState, currentState);
                            if(tp != 0) // to reduce the computational cost
                                sum += betaBefore.get(currentState)*tp;
                            update = true;
                        } else{
                            double tp = getTransitionProbability(prevState, currentState);
                            if(tp != 0) // to reduce the computational cost
                                sum += getBeta(currObs, currentState)*tp*getEmissionProbability(currentState, obs[t-1]);
                        }
                    }

                    if(update)
                        betaBefore.put(prevState, sum);
                }
            }
            beta.put(prevObs, betaBefore);
        }

        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        // Consider silent states
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState : states)
            {
                double sum = getStartProbability(nextState);
                boolean update = false;
                for (String sourceState : states){
                    if(isSilentState(sourceState)){
                        double tp = getTransitionProbability(sourceState, nextState);
                        if(tp != 0) // to reduce the computational cost
                            sum += alphaInit.get(sourceState)*tp;
                        update = true;
                    }
                }
                if(update){
                    if(!isSilentState(nextState))
                        sum *= getEmissionProbability(nextState, obs[0]);
                    alphaInit.put(nextState, sum);
                }
            }
        }

        // Calculate total probability
        double retSum = 0;
        for (String state : states){
            if(!isSilentState(state)){
                retSum += alphaInit.get(state)*getBeta("1", state);
            }
        }

        return retSum;
    }

    /**
     * Calculates backward algorithm with normailzation.
     * This method is used for Baum-Welch algorithm.
     * Silent states are NOT considered.
     * @param obs : observation sequence
     */
    private void backwardScale(String[] obs)
    {
        int T = obs.length;

        // initializeHmm
        beta.clear();
        Hashtable<String, Double> betaFinal = new Hashtable<String, Double>();
        for(String state : states)
            betaFinal.put(state, 1.0/scaleFactor.get(new String(""+T)));
        beta.put(new String(""+T), betaFinal);

        for (int t = T; t > 1; t--)
        {
            Hashtable<String, Double> betaBefore = new Hashtable<String, Double>();
            String prevObs = new String(""+(t-1));
            String currObs = new String(""+t);

            for (String prevState : states)
            {
                double sum = 0;
                for (String currentState : states){
                    double tp = getTransitionProbability(prevState, currentState);
                    if(tp != 0) // to reduce the computational cost
                        sum += getBeta(currObs, currentState)*tp*getEmissionProbability(currentState, obs[t-1]);
                }

                betaBefore.put(prevState, sum);
            }

            for (String state : states)
                betaBefore.put(state, betaBefore.get(state)/scaleFactor.get(prevObs));

            beta.put(prevObs, betaBefore);
        }
    }

    /**
     * Baum-Welch algorithm.
     * This method is used to make HMM learn a pattern.
     * Silent states are NOT considered.
     * @param obs : observation sequence to learn
     * @param minProb : parameter used for computational issue. Must be as small number as possile but greater than zero
     * @param stopDelta : Condition to stop learning (Learning stops when the improvement of iteration is smaller than this value)
     * @param updateStartProb
     * @param showResult : if true, it shows the result of learning
     * @param updateTranstionProb 
     * @param updateEmissionProb
     * @return : if true, learning is succeeded. Otherwise, it is failed.
     */
    public boolean BaumWelch(String[] obs, double minProb, double stopDelta, boolean updateStartProb, boolean updateTranstionProb, boolean updateEmissionProb, boolean showResult)
    {
        int itrNum = 0;

        double logprobf, probDelta, logprobprev;
        double	zetaSum, gammaSum, gammaSumOt;
        double sum;

        // calculate alpha
	logprobf = forwardScale(obs);  // log P(obs, init model)
        logProbInit = logprobf;
        // calculate beta, this should be called after forwardScale
	backwardScale(obs);
        computeZeta(obs);
	computeGamma(obs);
	logprobprev = logprobf;

        int T = obs.length;
        do  {
            
            // update start probability
            if(updateStartProb){
                sum = 0;
                for(String state : states){
                    if(getStartProbability(state) != 0)
                        setStartProbability(state, minProb+(1-minProb)*getGamma("1", state));
                    sum += getStartProbability(state);
                }
                // normalize elements to make sum of them equal to 1
                if(sum != 0)
                    for(String state : states)
                        setStartProbability(state, getStartProbability(state)/sum);
            }

            // reestimate transitionProbability matrix and emissionProbability matrix in each state
            for (String sourceState : states) {
                gammaSum = 0.0;
                if(updateTranstionProb || updateEmissionProb){
                    for (int t = 1; t <= T - 1; t++)
                        gammaSum += getGamma(new String(""+t), sourceState);
                }

                if(updateTranstionProb){
                    sum = 0;
                    for (String targetState : states) {
                        zetaSum = 0.0;
                        for (int t = 1; t <= T - 1; t++)
                            zetaSum += getZeta(new String(""+t), sourceState, targetState);

                        if(getTransitionProbability(sourceState, targetState) != 0){
                            if(zetaSum == 0)
                                setTransitionProbability(sourceState, targetState, minProb);
                            else
                                setTransitionProbability(sourceState, targetState, minProb+(1-minProb)*zetaSum/gammaSum);
                        }
                        sum += getTransitionProbability(sourceState, targetState);
                    }
                    // normalize elements to make sum of them equal to 1
                    if(sum != 0)
                        for (String targetState : states)
                            setTransitionProbability(sourceState, targetState, getTransitionProbability(sourceState, targetState)/sum);
                }

                if(updateEmissionProb){
                    sum = 0;
                    for (String obsSet : observationSet) {
                        gammaSumOt = 0.0;
                        for (int t = 1; t <= T - 1; t++) {
                            if (obs[t-1].equals(obsSet))
                                gammaSumOt += getGamma(new String(""+t), sourceState);
                        }

                        if(getEmissionProbability(sourceState, obsSet) != 0){
                            if(gammaSumOt == 0)
                                setEmissionProbability(sourceState, obsSet, minProb);
                            else
                                setEmissionProbability(sourceState, obsSet, minProb+(1-minProb)*gammaSumOt/gammaSum);
                        }
                        sum += getEmissionProbability(sourceState, obsSet);
                    }
                    // normalize elements to make sum of them equal to 1
                    if(sum != 0)
                        for (String obsSet : observationSet)
                            setEmissionProbability(sourceState, obsSet, getEmissionProbability(sourceState, obsSet)/sum);
                }
            }

            logprobf = forwardScale(obs);
            backwardScale(obs);
            computeZeta(obs);
            computeGamma(obs);

            // compute difference between log probability of two iterations
            probDelta = logprobf - logprobprev;
            logprobprev = logprobf;
            itrNum++;

        } while (probDelta > stopDelta);

        numIteration = itrNum;
	logProbFinal = logprobf; /* log P(O|estimated model) */

        if(showResult){
            if(probDelta > 0){
                System.out.println("Baum-Welch learning of HMM_"+name+" is done.");
                System.out.println("Number of iterations: "+numIteration);
                System.out.println("Delta: "+probDelta);
                System.out.println("log P(obs | init model): "+logProbInit);
                System.out.println("log P(obs | estimated model): "+logProbFinal);
            }
            else
                System.out.println("Baum-Welch learning of HMM_"+name+" is failed.");
        }

        if(probDelta < 0)
            return false;
        else{
            if(numTraining == 0)
                numTraining = 1;
            return true;
        }
    }


    /**
     * Used for Baum-Welch algorithm
     * @param obs : observation sequence
     */
    private void computeZeta(String[] obs)
    {
        int T = obs.length;
	double sum;

        zeta.clear();
	for (int t = 1; t <= T - 1; t++) {
            sum = 0.0;
            Hashtable<String, Hashtable> firstLevelElement = new Hashtable<String, Hashtable>();
            for (String sourceState : states){
                Hashtable<String, Double> secondLevelElement = new Hashtable<String, Double>();
                for (String targetState : states) {
                    double value = getAlpha(new String(""+t), sourceState);
                    if(isSilentState(targetState))
                        value *= getBeta(new String(""+t), targetState);
                    else
                        value *= getBeta(new String(""+(t+1)), targetState);
                    value *= getTransitionProbability(sourceState, targetState);
                    if(!isSilentState(targetState))
                        value *= getEmissionProbability(targetState, obs[t]);
                    else
                        value *= getEmissionProbability(targetState, obs[t]);
                    secondLevelElement.put(targetState, value);
                    sum += value;
                }
                firstLevelElement.put(sourceState, secondLevelElement);
            }

            for (String sourceState : states){
                for (String targetState : states){
                    double value = ((Hashtable<String, Double> )firstLevelElement.get(sourceState)).get(targetState);
                    ((Hashtable<String, Double> )firstLevelElement.get(sourceState)).put(targetState, value/sum);
                }
            }
            zeta.put(new String(""+t), firstLevelElement);
        }
    }


    /**
     * Used for Baum-Welch algorithm
     * @param obs : observation sequence
     */
    private void computeGamma(String[] obs)
    {
        int T = obs.length;

	for (int t = 1; t <= T - 1; t++) {
            Hashtable<String, Double> gammaElement = new Hashtable<String, Double>();
            for (String sourceState : states) {
                double gammaValue = 0;
                Hashtable<String, Double> zetaElement = (Hashtable<String, Double>) ((Hashtable<String, Hashtable>) zeta.get(new String(""+t))).get(sourceState);
                for (String targetState : states)
                    gammaValue += zetaElement.get(targetState);
                gammaElement.put(sourceState, gammaValue);
            }
            gamma.put(new String(""+t), gammaElement);
	}
    }

    /**
     * makes ensenble average of two HMMs
     * @param targetHmm : target HMM to merge
     */
    public void ensenbleAverage(HiddenMarkovModel targetHmm){
        if(!getName().equals(targetHmm.getName()))
            return;

        double w1 = ((double) numTraining)/((double) numTraining + (double) targetHmm.getNumTraining());
        double w2 = 1-w1;

        numTraining += targetHmm.getNumTraining();

        // calculate ensenble average of startProbability
        for(String state : states)
            setStartProbability(state, getStartProbability(state)*w1 + targetHmm.getStartProbability(state)*w2);

        // calculate ensenble average of transitionProbability
        for(String sourceState : states)
            for(String targetState : states)
                setTransitionProbability(sourceState, targetState, getTransitionProbability(sourceState, targetState)*w1 + targetHmm.getTransitionProbability(sourceState, targetState)*w2);

        // calculate ensenble average of emissionProbability
        for(String sourceState : states)
            for(String obs : observationSet)
                if(!isSilentState(sourceState))
                    setEmissionProbability(sourceState, obs, getEmissionProbability(sourceState, obs)*w1 + targetHmm.getEmissionProbability(sourceState, obs)*w2);
    }



    /**
     * returns name of HMM instance
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * returns how many times this HMM has been trained
     * @return : number of training
     */
    public int getNumTraining() {
        return numTraining;
    }

    /**
     * sets how many times this HMM has been trained
     *
     * @param numTraining
     */
    public void setNumTraining(int numTraining) {
        this.numTraining = numTraining;
    }

    /**
     * returns states set in String array format
     * @return array of states
     */
    public String[] getStatesToArray() {
        String[] out = new String[states.size()];

        for(int i=0; i<states.size(); i++)
            out[i] = states.get(i);

        return out;
    }

    /**
     * returns states set in ArrList<String> format
     * @return
     */
    public ArrayList<String> getStates() {
        return states;
    }


    /**
     * returns number of states
     * @return number of states
     */
    public int getNumStates(){
        return states.size();
    }

    /**
     * checks if the specified state is used in HMM
     * @param state
     * @return true if state used
     */
    public boolean containsState(String state){
        return states.contains(state);
    }

    /**
     * checks if the specified state is used in HMM
     * @param state
     * @return true if state used, otherwise throws an Exception and prints the stack trace, and then returns false
     */
    protected boolean checkState(String state){
        if(!containsState(state)){
            // warning
            try{
                throw new Exception(state  + " is not defined state in HMM("+name+").");
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        return containsState(state);
    }

    /**
     * returns the product of Viterbi algorithm. Method 'viterbi()' must be excuted in advance before using this method
     * @param seqNum : observation sequence number
     * @param state : state to check
     * @return delta
     */
    private Object[] getDelta(String seqNum, String state){
        return ((Hashtable<String, Object[]>) delta.get(seqNum)).get(state);
    }


    /**
     * returns the product of forward algorithm. 'forward()' or 'forwardScale()' must be excuted in advance before using this method
     * @param seqNum : observation sequence number
     * @param state : state to check
     * @return alpha
     */
    private double getAlpha(String seqNum, String state){
        return ((Hashtable<String, Double>) alpha.get(seqNum)).get(state);
    }

    /**
     * returns the product of backward algorithm. 'backward()' or 'backwardScale()' must be excuted in advance before using this method
     * @param seqNum : observation sequence number
     * @param state : state to check
     * @return
     */
    private double getBeta(String seqNum, String state){
        return ((Hashtable<String, Double>) beta.get(seqNum)).get(state);
    }

    /**
     * returns gamma.
     * Used for Baum-Welch algorithm
     * @param obs
     * @param state
     * @return the gamma value
     */
    private double getGamma(String obs, String state){
        return ((Hashtable<String, Double>) gamma.get(obs)).get(state);
    }

    /**
     * returns zeta
     * Used for Baum-Welch algorithm
     * @param obs
     * @param sourceState
     * @param targetState
     * @return the zeta value
     */
    private double getZeta(String obs, String sourceState, String targetState){
        return ((Hashtable<String, Double>) ((Hashtable<String, Hashtable>) zeta.get(obs)).get(sourceState)).get(targetState);
    }

    /**
     * returns the start probability of the specified state
     * @param state
     * @return probability
     */
    public double getStartProbability(String state){
        checkState(state);
        return startProbability.get(state);
    }

    /**
     * returns all start probabilities in array format
     * @return
     */
    public double[] getStartProbabilityToArray(){
        double[] out = new double[startProbability.size()];

        for(int i=0; i<startProbability.size(); i++)
            out[i] = getStartProbability(states.get(i));

        return out;
    }

    /**
     * returns the transition probability from 'state' to 'nextState'
     * @param state
     * @param nextState
     * @return probability of transition from state to nextState
     */
    public double getTransitionProbability(String state, String nextState){
        checkState(state);
        checkState(nextState);

        Hashtable<String, Double> tp = transitionProbability.get(state);

        return tp.get(nextState);
    }

    /**
     * returns transition probabilities in 2D array format
     * @return
     */
    public double[][] getTransitionProbabilityToArray(){
        double[][] out = new double[getNumStates()][getNumStates()];

        for(int i=0; i<getNumStates(); i++)
            for(int j=0; j<getNumStates(); j++)
                out[i][j] = getTransitionProbability(states.get(i), states.get(j));

        return out;
    }

    /**
     * returns emission probability of the specified observation in the specified state
     * @param state
     * @param observation
     * @return probabiliy of emisssion from the state to the observation
     */
    public double getEmissionProbability(String state, String observation){
        checkState(state);

        Hashtable<String, Double> tp = emissionProbability.get(state);

        return tp.get(observation);
    }

    /**
     * returns emission probabilities in 2D array format
     * @return array of emission probabilities, first index is state, second index is observation
     */
    public double[][] getEmissionProbabilityToArray(){
        double[][] out = new double[getNumStates()][observationSet.size()];

        for(int i=0; i<getNumStates(); i++)
            for(int j=0; j<observationSet.size(); j++)
                out[i][j] = getEmissionProbability(states.get(i), observationSet.get(j));

        return out;
    }


    /**
     * returns emission probabilities in Hashtable format
     * @return probability map, from codeword to probability
     */
    public Hashtable<String, Hashtable> getEmissionProbability() {
        return emissionProbability;
    }

    /**
     * returns start probabilities in Hashtable format
     * @return probability map, from codeword to probability
     */
    public Hashtable<String, Double> getStartProbability() {
        return startProbability;
    }

    /**
     * returns transition probabilities in Hashtable format
     * @return
     */
    public Hashtable<String, Hashtable> getTransitionProbability() {
        return transitionProbability;
    }

    /**
     * returns probability of Viterbi path. 'viterbi()' must be excuted before using this method
     * @param seqNum : observation sequence number to check
     * @param state
     * @return probabilities
     */
    public double getViterbiPathProbability(int seqNum, String state){
        Object[] objs = getDelta(new String(""+seqNum), state);

        return ((Double) objs[2]);
    }

    /**
     * returns viterbi path in ArrayList format
     * @param seqNum : observation sequence number to check
     * @return path
     */
    public ArrayList<String> getViterbiPath(int seqNum){
        ArrayList<String> argmax = null;
        double valmax = 0;

        for (String state : states)
        {
            if(!isSilentState(state)){
                Object[] objs = getDelta(new String(""+seqNum), state);
                ArrayList<String> v_path = (ArrayList<String>) objs[1];
                double v_prob = (Double) objs[2];
                if (v_prob > valmax)
                {
                    argmax = v_path;
                    valmax = v_prob;
                }
            }
        }

        return  argmax;
    }

    /**
     * returns viterbi path in String format
     * @param seqNum : observation sequence number to check
     * @return
     */
    public String getViterbiPathString(int seqNum){
        ArrayList<String> path = getViterbiPath(seqNum);
        String out = "";

        for(String element : path)
            out = out + "," +element;

        return out;
    }

    /**
     * returns viterbi path in String[] format
     * @param seqNum : observation sequence number to check
     * @return
     */
    public String[] getViterbiPathToArray(int seqNum){
        ArrayList<String> path = getViterbiPath(seqNum);
        String[] out = new String[path.size()];

        for(int i = 0; i<path.size(); i++)
            out[i] = path.get(i);

        return out;
    }

    /**
     * get viterbi path which is ended with the specified state in ArrayList format
     * @param seqNum : observation sequence number to check
     * @param state : final state
     * @return
     */
    public ArrayList<String> getViterbiPath(int seqNum, String state){
        Object[] objs = getDelta(new String(""+seqNum), state);

        return  (ArrayList<String>) objs[1];
    }

    /**
     * returns viterbi path which is ended with the specified state in String format
     * @param seqNum : observation sequence number to check
     * @param state : final state
     * @return
     */
    public String getViterbiPathString(int seqNum, String state){
        Object[] objs = getDelta(new String(""+seqNum), state);
        ArrayList<String> path = (ArrayList<String>) objs[1];
        String out = "";

        for(String element : path)
            out = out + "," +element;

        return out;
    }

    /**
     * returns viterbi path which is ended with the specified state in String[] format
     * @param seqNum : observation sequence number to check
     * @param state : final state
     * @return
     */
    public String[] getViterbiPathToArray(int seqNum, String state){
        Object[] objs = getDelta(new String(""+seqNum), state);
        ArrayList<String> path = (ArrayList<String>) objs[1];
        String[] out = new String[path.size()];

        for(int i = 0; i<path.size(); i++)
            out[i] = path.get(i);

        return out;
    }

    /**
     * prints out start probabilies on the screen
     */
    public void printStartProbability(){
        System.out.print("startProbability = (");
        for(int i = 0; i<states.size(); i++){
            if(i == states.size()-1)
                System.out.print(startProbability.get(states.get(i)));
            else
                System.out.print(startProbability.get(states.get(i))+", ");
        }
        System.out.println(")");
    }

    /**
     * prints out transition probabilies on the screen
     */
    public void printTransitionProbability(){
        System.out.println("transitionProbability = ");
        for(String sourceState: states){
            System.out.print("          ");
            for(String targetState: states)
                System.out.print(((Hashtable <String, Double>) transitionProbability.get(sourceState)).get(targetState)+" ");
            System.out.println("");
        }
    }

    /**
     * prints out emission probabilies on the screen
     */
    public void printEmissionProbability(){
        System.out.println("emissionProbability = ");
        for(String state: states){
            System.out.print("          ");
            for(String obs: observationSet)
                System.out.print(((Hashtable <String, Double>) emissionProbability.get(state)).get(obs)+" ");
            System.out.println("");
        }
    }

    /**
     * prints out all probabilies (start, transition, emission) on the screen
     */
    public void printAllProbability(){
        printStartProbability();
        printTransitionProbability();
        printEmissionProbability();
    }

    /**
     * checks if the specified state is silent or not
     * @param state
     * @return
     */
    private boolean checkSilentState(String state){
        boolean out= true;
        for(String obs:observationSet){
            if(getEmissionProbability(state, obs) != 0){
                out = false;
                break;
            }
        }

        return out;
    }


    /**
     * sets the specified state as a silent state or non-silent state
     * @param state
     * @param set
     */
    private void setSilentState(String state, boolean set){
        silentState.put(state, set);
        calDepthSilentStates();
    }

    /**
     * checks if the specified state is silent or not
     * @param state
     * @return
     */
    public boolean isSilentState(String state){
        return silentState.get(state);
    }

    /**
     * sets the specified state as a silent state
     * @param state
     */
    public void setStateSilent(String state){
        for(String obs : observationSet)
            setEmissionProbability(state, obs, 0);

        setSilentState(state, true);
    }

    /**
     * sets the specified state as a non-silent state
     * @param state
     */
    public void setStateNonSilent(String state){
        silentState.put(state, false);
    }

    /**
     * returns the number of states having non-zero start probability in LR model
     * @return
     */
    public int getNumStartStates_LR() {
        return numStartStates_LR;
    }

    /**
     * sets the number of states having non-zero start probability in LR model
     * @param numStartStates_LRcase
     */
    public void setNumStartStates_LR(int numStartStates_LRcase) {
        this.numStartStates_LR = numStartStates_LRcase;
    }

    /**
     * returns the number of states having non-zero transition probability in LR model
     * @return
     */
    public int getNumStateDirectTransition_LR() {
        return numStateDirectTransition_LR;
    }

    /**
     * sets the number of states having non-zero transition probability in LR model
     * @param numStateDirectTransition_LR
     */
    public void setNumStateDirectTransition_LR(int numStateDirectTransition_LR) {
        this.numStateDirectTransition_LR = numStateDirectTransition_LR;
    }

    /**
     * returns emission probability matix saved by saveInitialProbabilty()
     * @return
     */
    public double[][] getInitialEmissionProbabilityToArray() {
        double[][] out = new double[getNumStates()][observationSet.size()];

        for(int i=0; i<getNumStates(); i++)
            for(int j=0; j<observationSet.size(); j++)
                out[i][j] = ((Hashtable<String, Double>) initialEmissionProbability.get(states.get(i))).get(observationSet.get(j));

        return out;
    }

    /**
     * returns start probability matix saved by saveInitialProbabilty()
     * @return
     */
    public double[] getInitialStartProbabilityToArray() {
        double[] out = new double[initialStartProbability.size()];

        for(int i=0; i<initialStartProbability.size(); i++)
            out[i] = getStartProbability(states.get(i));

        return out;
    }

    /**
     * returns transition probability matix saved by saveInitialProbabilty()
     * @return
     */
    public double[][] getInitialTransitionProbabilityToArray() {
        double[][] out = new double[getNumStates()][getNumStates()];

        for(int i=0; i<getNumStates(); i++)
            for(int j=0; j<getNumStates(); j++)
                out[i][j] = ((Hashtable<String, Double>) initialTransitionProbability.get(states.get(i))).get(states.get(j));

        return out;
    }

    /**
     * save current prbability matrix
     */
    public void saveInitialProbabilty(){
        initialStartProbability.putAll(startProbability);
        initialTransitionProbability.putAll(transitionProbability);
        initialEmissionProbability.putAll(emissionProbability);
    }

    /**
     * returns HMM model type
     * @return
     */
    public ModelType getModelType() {
        return modelType;
    }

    /**
     * sets HMM model type
     * @param modelType
     */
    public void setModelType(ModelType modelType) {
        this.modelType = modelType;
    }
}
