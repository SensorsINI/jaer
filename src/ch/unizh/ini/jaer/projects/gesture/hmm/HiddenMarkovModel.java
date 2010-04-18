package ch.unizh.ini.jaer.projects.gesture.hmm;


import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Defines Hidden Markov Model
 * @author Jun Haeng Lee
 */
public class HiddenMarkovModel {
    private String name;  // Name of HMM
    private ArrayList<String> states = new ArrayList<String>(); // States array
    private ArrayList<String> observationSet = new ArrayList<String>(); // Array of observation space
    private Hashtable<String, Double> startProbability = new Hashtable<String, Double>(); // start probabilities of states
    private Hashtable<String, Hashtable> transitionProbability = new Hashtable<String, Hashtable>(); // transition probabilities between states
    private Hashtable<String, Hashtable> emissionProbability = new Hashtable<String, Hashtable>(); // probabilities of observations at each state
    private Hashtable<String, Boolean> silentState = new Hashtable<String, Boolean>(); // silent state marker
    private int depthSilentStates = 0; // maximum length of cascaded silent states

    private Hashtable<String, Hashtable> delta = new Hashtable<String, Hashtable>(); // product of Viterbi algorithm
    private Hashtable<String, Hashtable> alpha = new Hashtable<String, Hashtable>(); // product of forward algorithm
    private Hashtable<String, Hashtable> beta = new Hashtable<String, Hashtable>(); // product of backward algorithm
    private Hashtable<String, Hashtable> gamma = new Hashtable<String, Hashtable>(); // used in Baum-Welch algorithm
    private Hashtable<String, Hashtable> zeta = new Hashtable<String, Hashtable>(); // used in Baum-Welch algorithm
    private Hashtable<String, Double> scaleFactor = new Hashtable<String, Double>(); // by-product of scaled forward algorithm


    protected static Random random = new Random();
    protected int numTraining = 0;  // indicate how many times this HMM has been trained
    private double numIteration;   // number of iteration in Baum-Welch Learning algorithm
    private double logProbInit;    // total probability in log scale before Baum-Welch algorithm
    private double logProbFinal;   // total probability in log scale after Baum-Welch algorithm

    /**
     * Constructor with name
     * Must be followed by initializeHmm(String [] states, String [] obsSet)
     * @param name
     */
    public HiddenMarkovModel(String name) {
        this.name = new String(name);
    }

    /**
     * Constructor with name, states list, and observation set list
     * @param name
     * @param states
     * @param observationSet
     */
    public HiddenMarkovModel(String name, String [] states, String [] observationSet) {
        this(name);
        initializeHmm(states, observationSet);
    }

    /**
     * Initilize HMM with states and observation set
     * @param states
     * @param obsSet
     */
    public void initializeHmm(String [] states, String [] obsSet) {

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
            for(int j = 0; j<obsSet.length;j++){
                ep.put(obsSet[j],new Double(0.0));
                if(i == 0)
                    observationSet.add(obsSet[j]);
            }
        }
    }

    /**
     * Set start probability of the specified state
     * @param state
     * @param prob
     */
    public void setStartProbability(String state, double prob){
        startProbability.put(state, new Double(prob));
    }

    /**
     * Set start probability of all states
     * @param prob : array of start probability
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
     * @param state
     * @param nextState
     * @param prob
     */
    public void setTransitionProbability(String state, String nextState, double prob){
        Hashtable<String, Double> tp = transitionProbability.get(state);
        tp.put(nextState ,new Double(prob));
    }

    /**
     * Set transition probabilities between states
     * @param prob : 2D array of transition probability
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
     * @param state
     * @param observation
     * @param prob
     */
    public void setEmissionProbability(String state, String observation, double prob){
        Hashtable<String, Double> tp = emissionProbability.get(state);
        if(!isSilentState(state)){
            tp.put(observation,new Double(prob));
            setSilentState(state, checkSilentState(state));
        }
    }

    /**
     * Set all emission probabilities
     * @param prob
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
     * Initialize HMM to Ergodic one with random probabilities.
     * Ergodic HMM has full mesh transition between states
     */
    public void setProbabilityRandomErgodic(){
        setStartProbabilityRandom();
        setTransitionProbabilityRandom();
        setEmissionProbabilityRandom();
    }

    /**
     * Initialize HMM to Left-Right one with random probabilities.
     * In Left-Right HMM, each state has transition probabilities only to all next states and itself.
     * @param numStartStates
     */
    public void setProbabilityRandomLeftRight(int numStartStates){
        setProbabilityRandomErgodic();

        // Transition and Emission probabilities
        double sumSP = 0;
        for(int i=0; i<states.size(); i++){
            if(i >= numStartStates)
                setStartProbability(states.get(i), 0);
            else
                sumSP += getStartProbability(states.get(i));

            double sumTP = 0;
            for(int j=0; j<states.size(); j++){
                if(i > j)
                    setTransitionProbability(states.get(i), states.get(j), 0);
                else
                    sumTP += getTransitionProbability(states.get(i), states.get(j));
            }

            for(int j=0; j<states.size(); j++){
                if(i <= j)
                    setTransitionProbability(states.get(i), states.get(j), getTransitionProbability(states.get(i), states.get(j))/sumTP);
                
                if(i == states.size()-1 && j == states.size()-1)
                    setTransitionProbability(states.get(i), states.get(j), 0);
            }
            
//            if(i == states.size()-1)
//                for(int k=0; k<observationSet.size(); k++)
//                    setEmissionProbability(states.get(i), observationSet.get(k), 0);
        }

        for(int i=0; i<states.size(); i++)
            if(i < numStartStates)
                setStartProbability(states.get(i), getStartProbability(states.get(i))/sumSP);

        calDepthSilentStates();
    }

    /**
     * Initialize HMM to Left-Right-Banded one with random probabilities.
     * In Left-Right-Banded HMM, each state has transition probabilities only to the very next state and itself.
     */
    public void setProbabilityRandomLeftRightBanded(){
        setProbabilityRandomErgodic();
        
        // Transition and Emission probabilities
        for(int i=0; i<states.size(); i++){
            if(i > 0)
                setStartProbability(states.get(i), 0);
            else
                setStartProbability(states.get(i), 1.0);

            for(int j=0; j<states.size(); j++){
                if(j == i) { /* do nothing */ }
                else if(j == i+1)
                    setTransitionProbability(states.get(i), states.get(j), 1.0 - getTransitionProbability(states.get(i), states.get(i)));
                else
                    setTransitionProbability(states.get(i), states.get(j), 0);
            }
            setTransitionProbability(states.get(states.size()-1), states.get(states.size()-1), 1.0);
        }

        calDepthSilentStates();
    }

    /**
     * Initialize HMM with given probabilities
     * @param startProb
     * @param transitionProb
     * @param emissionProb
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
     * Calculate Viterbi algorithm
     * @param obs
     * @return {double totalProb, ArrayList<String> bestPath, double probBestPath}
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

        for (String state : states){
            ArrayList<String> pathList = new ArrayList<String>();
            pathList.add(state);
            double prob  = getStartProbability(state)*getEmissionProbability(state, obs[0]);

            deltaInit.put(state, new Object[]{prob, pathList, prob});
        }
        delta.put(new String("1"), deltaInit);

        for (int t=2; t<=T; t++)
        {
            Hashtable<String, Object[]> deltaNext = new Hashtable<String, Object[]>();
            String prevObs = new String(""+(t-1));
            String currObs = new String(""+t);

            for (String nextState : states)
            {
                double total = 0; // The total probability of a given next state, which is obtained by adding up the probabilities of all paths reaching that state
                ArrayList<String> argmax = new ArrayList<String>();
                double valmax = 0;

                double prob = 1;
                ArrayList<String> v_path; // The Viterbi path is computed as the corresponding argmax of that maximization, by extending the Viterbi path that leads to the current state with the next state.
                double v_prob = 1;

                for (String sourceState : states)
                {
                    Object[] objs = getDelta(prevObs, sourceState);
                    prob = (Double) objs[0];
                    v_path = (ArrayList<String>) objs[1];
                    v_prob = (Double) objs[2];

                    // Get the multiplication of the emission probability of the current observation and the transition probability from the source state to the next state
                    double p = getEmissionProbability(nextState, obs[t-1])*getTransitionProbability(sourceState, nextState);

                    prob *= p;
                    v_prob *= p;
                    total += prob;
                    if (v_prob >= valmax)
                    {
                        argmax.clear();
                        argmax.addAll(v_path);
                        argmax.add(nextState);
                        valmax = v_prob;
                    }
                }
                deltaNext.put(nextState, new Object[]{total,argmax,valmax});
            }
            delta.put(currObs, deltaNext);
        }

        double total = 0;
        ArrayList<String> argmax = null;
        double valmax = 0;

        double prob;
        ArrayList<String> v_path;
        double v_prob;

        for (String state : states)
        {
            if(!isSilentState(state)){
                Object[] objs = getDelta(new String(""+T), state);
                prob = (Double) objs[0];
                v_path = (ArrayList<String>) objs[1];
                v_prob = (Double) objs[2];
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

    /** Calculate the depth of silent states
     *
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
     * Calculate forward algorithm. Silent states are considered.
     * @param obs
     * @return
     */
    public double forward(String[] obs)
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
        // Consider silent states in calculating the initial probability
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState : states)
            {
                double sum = getStartProbability(nextState);
                boolean update = false;
                for (String sourceState : states){
                    if(isSilentState(sourceState)){
                        sum += alphaInit.get(sourceState)*getTransitionProbability(sourceState, nextState);
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
                for (String sourceState : states)
                    sum += getAlpha(prevObs, sourceState)*getTransitionProbability(sourceState, nextState);

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
                            sum2 += alphaNext.get(sourceState2)*getTransitionProbability(sourceState2, nextState2);
                            update = true;
                        }else
                            sum2 += getAlpha(prevObs, sourceState2)*getTransitionProbability(sourceState2, nextState2);
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

    private double forwardScale(String[] obs)
    {
        int T =  obs.length;

        scaleFactor.clear();

        // initializeHmm
        double sf = 0.0;
        alpha.clear();

        // Consider silent states in calculating the initial probability
        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        // Consider silent states in calculating the initial probability
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState : states)
            {
                double sum = getStartProbability(nextState);
                boolean update = false;
                for (String sourceState : states){
                    if(isSilentState(sourceState)){
                        sum += alphaInit.get(sourceState)*getTransitionProbability(sourceState, nextState);
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
                for (String sourceState : states)
                    sum += getAlpha(prevObs, sourceState)*getTransitionProbability(sourceState, nextState);

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
                            sum2 += alphaNext.get(sourceState2)*getTransitionProbability(sourceState2, nextState2);
                            update = true;
                        }else
                            sum2 += getAlpha(prevObs, sourceState2)*getTransitionProbability(sourceState2, nextState2);
                    }

                    if(update){
                        if(!isSilentState(nextState2))
                            sum2 *= getEmissionProbability(nextState2, obs[t-1]);

                        alphaNext.put(nextState2, sum2);
                    }
                }
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
                // repeat until all silent states are considered properly.
                double sum = 0;
                for (String currentState : states){
                    if(isSilentState(currentState))
                        sum += getBeta(currObs, currentState)*getTransitionProbability(prevState, currentState);
                    else
                        sum += getBeta(currObs, currentState)*getTransitionProbability(prevState, currentState)*getEmissionProbability(currentState, obs[t-1]);
                }

                betaBefore.put(prevState, sum);
            }

            // repeat until all silent states are considered properly.
            for(int i=0; i<depthSilentStates; i++){
                for (String prevState : states){
                    double sum = 0;
                    boolean update = false;
                    for (String currentState : states){
                        if(isSilentState(currentState)){
                            sum += betaBefore.get(currentState)*getTransitionProbability(prevState, currentState);
                            update = true;
                        } else
                            sum += getBeta(currObs, currentState)*getTransitionProbability(prevState, currentState)*getEmissionProbability(currentState, obs[t-1]);
                    }

                    if(update)
                        betaBefore.put(prevState, sum);
                }
            }
            beta.put(prevObs, betaBefore);
        }

        // Consider silent states in calculating the initial probability
        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        // Consider silent states in calculating the initial probability
        for(int i=0; i<depthSilentStates; i++){
            for (String nextState : states)
            {
                double sum = getStartProbability(nextState);
                boolean update = false;
                for (String sourceState : states){
                    if(isSilentState(sourceState)){
                        sum += alphaInit.get(sourceState)*getTransitionProbability(sourceState, nextState);
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
                // repeat until all silent states are considered properly.
                double sum = 0;
                for (String currentState : states){
                    if(isSilentState(currentState))
                        sum += getBeta(currObs, currentState)*getTransitionProbability(prevState, currentState);
                    else
                        sum += getBeta(currObs, currentState)*getTransitionProbability(prevState, currentState)*getEmissionProbability(currentState, obs[t-1]);
                }

                betaBefore.put(prevState, sum);
            }

            // repeat until all silent states are considered properly.
            for(int i=0; i<depthSilentStates; i++){
                for (String prevState : states){
                    double sum = 0;
                    boolean update = false;
                    for (String currentState : states){
                        if(isSilentState(currentState)){
                            sum += betaBefore.get(currentState)*getTransitionProbability(prevState, currentState);
                            update = true;
                        } else
                            sum += getBeta(currObs, currentState)*getTransitionProbability(prevState, currentState)*getEmissionProbability(currentState, obs[t-1]);
                    }

                    if(update)
                        betaBefore.put(prevState, sum);
                }
            }

            for (String state : states)
                betaBefore.put(state, betaBefore.get(state)/scaleFactor.get(prevObs));

            beta.put(prevObs, betaBefore);
        }
    }

    
    public boolean BaumWelch(String[] obs, double minProb, double DELTA, boolean showResult)
    {
        int itrNum = 0;

        double logprobf, delta, logprobprev;
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

            // reestimate transitionProbability matrix and emissionProbability matrix in each state
            for (String sourceState : states) {
                gammaSum = 0.0;
                for (int t = 1; t <= T - 1; t++)
                    gammaSum += getGamma(new String(""+t), sourceState);

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

            logprobf = forwardScale(obs);
            backwardScale(obs);
            computeZeta(obs);
            computeGamma(obs);

            // compute difference between log probability of two iterations
            delta = logprobf - logprobprev;
            logprobprev = logprobf;
            itrNum++;

        } while (delta > DELTA);

        numIteration = itrNum;
	logProbFinal = logprobf; /* log P(O|estimated model) */

        if(showResult){
            if(delta > 0){
                System.out.println("Baum-Welch learning of HMM_"+name+" is done.");
                System.out.println("Number of iterations: "+numIteration);
                System.out.println("Delta: "+delta);
                System.out.println("log P(obs | init model): "+logProbInit);
                System.out.println("log P(obs | estimated model): "+logProbFinal);
            }
            else
                System.out.println("Baum-Welch learning of HMM_"+name+" is failed.");
        }

        if(delta < 0)
            return false;
        else{
            if(numTraining == 0)
                numTraining = 1;
            return true;
        }
    }


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
                    setTransitionProbability(sourceState, obs, getEmissionProbability(sourceState, obs)*w1 + targetHmm.getEmissionProbability(sourceState, obs)*w2);
    }



    public String getName() {
        return name;
    }

    public int getNumTraining() {
        return numTraining;
    }

    public String[] getStatesToArray() {
        String[] out = new String[states.size()];

        for(int i=0; i<states.size(); i++)
            out[i] = states.get(i);

        return out;
    }

    public ArrayList<String> getStates() {
        return states;
    }

    public String getFinalState(){
        return states.get(states.size()-1);
    }

    public int getNumStates(){
        return states.size();
    }

    public boolean containsState(String state){
        return states.contains(state);
    }

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

    private Object[] getDelta(String obs, String state){
        return ((Hashtable<String, Object[]>) delta.get(obs)).get(state);
    }

    private double getAlpha(String obs, String state){
        return ((Hashtable<String, Double>) alpha.get(obs)).get(state);
    }

    private double getBeta(String obs, String state){
        return ((Hashtable<String, Double>) beta.get(obs)).get(state);
    }

    private double getGamma(String obs, String state){
        return ((Hashtable<String, Double>) gamma.get(obs)).get(state);
    }

    private double getZeta(String obs, String sourceState, String targetState){
        return ((Hashtable<String, Double>) ((Hashtable<String, Hashtable>) zeta.get(obs)).get(sourceState)).get(targetState);
    }

    public double getStartProbability(String state){
        checkState(state);
        return startProbability.get(state);
    }

    public double[] getStartProbabilityToArray(){
        double[] out = new double[startProbability.size()];

        for(int i=0; i<startProbability.size(); i++)
            out[i] = getStartProbability(states.get(i));

        return out;
    }

    public double getTransitionProbability(String state, String nextState){
        checkState(state);
        checkState(nextState);

        Hashtable<String, Double> tp = transitionProbability.get(state);

        return tp.get(nextState);
    }

    public double[][] getTransitionProbabilityToArray(){
        double[][] out = new double[getNumStates()][getNumStates()];

        for(int i=0; i<getNumStates(); i++)
            for(int j=0; j<getNumStates(); j++)
                out[i][j] = getTransitionProbability(states.get(i), states.get(j));

        return out;
    }

    public double getEmissionProbability(String state, String observation){
        checkState(state);

        Hashtable<String, Double> tp = emissionProbability.get(state);

        return tp.get(observation);
    }

    public double[][] getEmissionProbabilityToArray(){
        double[][] out = new double[getNumStates()][observationSet.size()];

        for(int i=0; i<getNumStates(); i++)
            for(int j=0; j<observationSet.size(); j++)
                out[i][j] = getEmissionProbability(states.get(i), observationSet.get(j));

        return out;
    }


    public Hashtable<String, Hashtable> getEmissionProbability() {
        return emissionProbability;
    }

    public Hashtable<String, Double> getStartProbability() {
        return startProbability;
    }

    public Hashtable<String, Hashtable> getTransitionProbability() {
        return transitionProbability;
    }

    public double getViterbiPathProbability(int t, String state){
        Object[] objs = getDelta(new String(""+t), state);

        return ((Double) objs[2]);
    }


    public ArrayList<String> getViterbiPath(int t){
        ArrayList<String> argmax = null;
        double valmax = 0;

        for (String state : states)
        {
            Object[] objs = getDelta(new String(""+t), state);
            ArrayList<String> v_path = (ArrayList<String>) objs[1];
            double v_prob = (Double) objs[2];
            if (v_prob > valmax)
            {
                argmax = v_path;
                valmax = v_prob;
            }
        }

        return  argmax;
    }

    public String getViterbiPathString(int t){
        ArrayList<String> path = getViterbiPath(t);
        String out = "";

        for(String element : path)
            out = out + "," +element;

        return out;
    }

    public String[] getViterbiPathToArray(int t){
        ArrayList<String> path = getViterbiPath(t);
        String[] out = new String[path.size()];

        for(int i = 0; i<path.size(); i++)
            out[i] = path.get(i);

        return out;
    }

    public ArrayList<String> getViterbiPath(int t, String state){
        Object[] objs = getDelta(new String(""+t), state);

        return  (ArrayList<String>) objs[1];
    }

    public String getViterbiPathString(int t, String state){
        Object[] objs = getDelta(new String(""+t), state);
        ArrayList<String> path = (ArrayList<String>) objs[1];
        String out = "";

        for(String element : path)
            out = out + "," +element;

        return out;
    }

    public String[] getViterbiPathToArray(int t, String state){
        Object[] objs = getDelta(new String(""+t), state);
        ArrayList<String> path = (ArrayList<String>) objs[1];
        String[] out = new String[path.size()];

        for(int i = 0; i<path.size(); i++)
            out[i] = path.get(i);

        return out;
    }

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

    public void printTransitionProbability(){
        System.out.println("transitionProbability = ");
        for(String sourceState: states){
            System.out.print("          ");
            for(String targetState: states)
                System.out.print(((Hashtable <String, Double>) transitionProbability.get(sourceState)).get(targetState)+" ");
            System.out.println("");
        }
    }

    public void printEmissionProbability(){
        System.out.println("emissionProbability = ");
        for(String state: states){
            System.out.print("          ");
            for(String obs: observationSet)
                System.out.print(((Hashtable <String, Double>) emissionProbability.get(state)).get(obs)+" ");
            System.out.println("");
        }
    }

    public void printAllProbability(){
        printStartProbability();
        printTransitionProbability();
        printEmissionProbability();
    }

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

    private void setSilentState(String state, boolean set){
        silentState.put(state, set);
        calDepthSilentStates();
    }

    public boolean isSilentState(String state){
        return silentState.get(state);
    }

    public void setStateSilent(String state){
        for(String obs : observationSet)
            setEmissionProbability(state, obs, 0);

        setSilentState(state, true);
    }

}
