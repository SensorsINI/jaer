package ch.unizh.ini.jaer.projects.gesture.hmm;


import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Jun Haeng Lee
 */
public class HiddenMarkovModel {
    private String name;
    private ArrayList<String> states = new ArrayList<String>();
    private ArrayList<String> observationSet = new ArrayList<String>();
    private Hashtable<String, Double> startProbability = new Hashtable<String, Double>();
    private Hashtable<String, Hashtable> transitionProbability = new Hashtable<String, Hashtable>();
    private Hashtable<String, Hashtable> emissionProbability = new Hashtable<String, Hashtable>();
    private Hashtable<String, Boolean> silentState = new Hashtable<String, Boolean>();

    private Hashtable<String, Object[]> finalT = new Hashtable<String, Object[]>();
    private Hashtable<String, Hashtable> alpha = new Hashtable<String, Hashtable>();
    private Hashtable<String, Hashtable> beta = new Hashtable<String, Hashtable>();
    private Hashtable<String, Hashtable> gamma = new Hashtable<String, Hashtable>();
    private Hashtable<String, Hashtable> zeta = new Hashtable<String, Hashtable>();
    private Hashtable<String, Double> scaleFactor = new Hashtable<String, Double>();


    protected static Random random = new Random();
    protected int numTraining = 0;
    private double pniter, plogprobinit, plogprobfinal;

    public HiddenMarkovModel(String name) {
        this.name = new String(name);
    }

    public HiddenMarkovModel(String name, String [] states, String [] observationSet) {
        this(name);
        initializeHmm(states, observationSet);
    }

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

    public void setStartProbability(String state, double prob){
        startProbability.put(state, new Double(prob));
    }

    public void setStartProbability(double [] prob){
        for(int i = 0; i<states.size(); i++)
            setStartProbability(states.get(i), prob[i]);
    }

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

    public void setTransitionProbability(String state, String nextState, double prob){
        Hashtable<String, Double> tp = transitionProbability.get(state);
        tp.put(nextState ,new Double(prob));
    }

    public void setTransitionProbability(double[][] prob){
        for(int i = 0; i<states.size(); i++)
            for(int j = 0; j<states.size(); j++)
                setTransitionProbability(states.get(i), states.get(j), prob[i][j]);
    }

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

    public void setEmissionProbability(String state, String observation, double prob){
        Hashtable<String, Double> tp = emissionProbability.get(state);
        if(!isSilentState(state)){
            tp.put(observation,new Double(prob));
            setSilentState(state, checkSilentState(state));
        }
    }

    public void setEmissionProbability(double[][] prob){
        for(int i = 0; i<states.size(); i++)
            if(!isSilentState(states.get(i)))
                for(int j = 0; j<observationSet.size(); j++)
                    setEmissionProbability(states.get(i), observationSet.get(j), prob[i][j]);
    }

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
    }

    public void setProbabilityRandomErgodic(){
        setStartProbabilityRandom();
        setTransitionProbabilityRandom();
        setEmissionProbabilityRandom();
    }

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
    }

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
    }

    public void initializeProbabilityMatrix(double[] startProb, double[][] transitionProb, double[][] emissionProb){
        if(startProb != null)
            setStartProbability(startProb);
        if(transitionProb != null)
            setTransitionProbability(transitionProb);
        if(emissionProb != null)
            setEmissionProbability(emissionProb);
    }


    /* Original source: Wikipedia
     *
     */
    public Object[] viterbi(String[] obs)
    {
        /* T = {prob, v_path, v_prob}
         * prob : the total probability of all paths from the start to the current state (constrained by the observations)
         * v_path : the Viterbi path up to the current state
         * v_prob : the probability of the Viterbi path up to the current state
         */
        Hashtable<String, Object[]> T = new Hashtable<String, Object[]>();

        /* Initialize T to the starting probabilies
         */
        for (String state : states){
            ArrayList<String> pathList = new ArrayList<String>();
            pathList.add(state);
            T.put(state, new Object[]{getStartProbability(state), pathList, getStartProbability(state)});
        }



        /* The main loop considers the observations from obs in sequence
         *
         */
        for (String output : obs)
        {
            /* T holds the information of time t, U holds the similar of t+1
             *
             */
            Hashtable<String, Object[]> U = new Hashtable<String, Object[]>();
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
                    Object[] objs = T.get(sourceState);
                    prob = (Double) objs[0];
                    v_path = (ArrayList<String>) objs[1];
                    v_prob = (Double) objs[2];

                    // Get the multiplication of the emission probability of the current observation and the transition probability from the source state to the next state
                    double p;
                    if(isSilentState(sourceState))
                        p = getTransitionProbability(sourceState, nextState);
                    else
                        p = getEmissionProbability(sourceState, output)*getTransitionProbability(sourceState, nextState);

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
                U.put(nextState, new Object[]{total,argmax,valmax});
            }
            T = U;
        }

        // clean out finalT and load new results into it.
        finalT.clear();
        finalT.putAll(T);

        double total = 0;
        ArrayList<String> argmax = null;
        double valmax = 0;

        double prob;
        ArrayList<String> v_path;
        double v_prob;

        for (String state : states)
        {
            Object[] objs = T.get(state);
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
        return new Object[]{total, argmax, valmax};
    }

    private void propagateSilentStatesForward(ArrayList<String> stateNoObs, Hashtable<String, Double> out, String obs){
        ArrayList<String> newStateNoObs = new ArrayList<String>();
        if(!stateNoObs.isEmpty()){
            for(String sourceState:stateNoObs)
                for(String targetState : states)
                    if(getTransitionProbability(sourceState, targetState) != 0){
                        if(isSilentState(targetState)){
                            out.put(targetState, out.get(targetState) + out.get(sourceState)*getTransitionProbability(sourceState, targetState));
                            newStateNoObs.add(targetState);
                        } else
                            out.put(targetState, out.get(targetState) + out.get(sourceState)*getTransitionProbability(sourceState, targetState)*getEmissionProbability(targetState, obs));
                    }
        }

        stateNoObs.clear();
        stateNoObs.addAll(newStateNoObs);
    }

    public double forward(String[] obs)
    {
        int T =  obs.length - 1;
        // initializeHmm
        alpha.clear();

        // Consider silent states in calculating the initial probability
        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        ArrayList<String> stateNoObs = new ArrayList<String>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
                stateNoObs.add(state);
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        while(!stateNoObs.isEmpty())
            propagateSilentStatesForward(stateNoObs, alphaInit, obs[0]);
        

        alpha.put(obs[0], alphaInit);

        for (int t = 1; t <= T; t++)
        {
            Hashtable<String, Double> alphaNext = new Hashtable<String, Double>();

            for (String nextState : states)
            {
                double sum = 0;
                for (String sourceState : states)
                    if(!isSilentState(sourceState)) // Consider silent states
                        sum += getAlpha(obs[t-1], sourceState)*getTransitionProbability(sourceState, nextState);

                if(isSilentState(nextState)){ // Consider silent states
                    alphaNext.put(nextState, sum);
                    stateNoObs.add(nextState);
                } else
                    alphaNext.put(nextState, sum*getEmissionProbability(nextState, obs[t]));
            }
            // Consider silent states
            while(!stateNoObs.isEmpty())
                propagateSilentStatesForward(stateNoObs, alphaNext, obs[t]);
            
            alpha.put(obs[t], alphaNext);
        }

        double retSum = 0;
        for (String state : states)
            if(!isSilentState(state))
                retSum += getAlpha(obs[T], state);

        return retSum;
    }

    private double forwardScale(String[] obs)
    {
        int T =  obs.length - 1;

        scaleFactor.clear();

        // initializeHmm
        double sf = 0.0;
        alpha.clear();

        // Consider silent states in calculating the initial probability
        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        ArrayList<String> stateNoObs = new ArrayList<String>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
                stateNoObs.add(state);
            }else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        while(!stateNoObs.isEmpty())
            propagateSilentStatesForward(stateNoObs, alphaInit, obs[0]);

        for(String state : states)
            sf += alphaInit.get(state);
        scaleFactor.put(obs[0], sf);
        for(String state : states)
            alphaInit.put(state, alphaInit.get(state)/sf);

        alpha.put(obs[0], alphaInit);

        for (int t = 1; t <= T; t++)
        {
            Hashtable<String, Double> alphaNext = new Hashtable<String, Double>();

            sf = 0;
            for (String nextState : states)
            {
                double sum = 0;
                for (String source_state : states)
                    if(!isSilentState(nextState)) // Consider silent states
                        sum += getAlpha(obs[t-1], source_state)*getTransitionProbability(source_state, nextState);

                if(isSilentState(nextState)){
                    alphaNext.put(nextState, sum);
                    stateNoObs.add(nextState);
                }else
                    alphaNext.put(nextState, sum*getEmissionProbability(nextState, obs[t]));
                
            }
            // Consider silent states
            while(!stateNoObs.isEmpty())
                propagateSilentStatesForward(stateNoObs, alphaNext, obs[t]);

            for (String nextState : states)
                sf += alphaNext.get(nextState);
            scaleFactor.put(obs[t], sf);
            for (String state : states)
                alphaNext.put(state, alphaNext.get(state)/sf);

            alpha.put(obs[t], alphaNext);
        }

        double retSum = 0;
        for (String ob:obs)
            retSum += Math.log(scaleFactor.get(ob));

        return retSum;
    }

    public double backward(String[] obs)
    {
        int T = obs.length - 1;

        // initializeHmm
        beta.clear();
        Hashtable<String, Double> betaFinal = new Hashtable<String, Double>();
        for(String state : states)
            betaFinal.put(state, 1.0);
        beta.put(obs[T], betaFinal);

        for (int t = T; t > 0; t--)
        {
            Hashtable<String, Double> betaBefore = new Hashtable<String, Double>();

            for (String prevState : states)
            {
                // repeat until all silent states are considered properly. This requirement is met when sum is not updated anumore.
                double sum = 0, prevSum = -1;
                while(sum != prevSum){
                    prevSum = sum;
                    sum = 0;
                    for (String currentState : states)
                        sum += getBeta(obs[t], currentState)*getTransitionProbability(prevState, currentState)*getEmissionProbability(currentState, obs[t]);
                }
                betaBefore.put(prevState, sum);
            }
            beta.put(obs[t-1], betaBefore);
        }

        // Consider silent states in calculating the initial probability
        Hashtable<String, Double> alphaInit = new Hashtable<String, Double>();
        ArrayList<String> stateNoObs = new ArrayList<String>();
        for(String state : states){
            if(isSilentState(state)){
                alphaInit.put(state, getStartProbability(state));
                stateNoObs.add(state);
            }
            else
                alphaInit.put(state, getStartProbability(state)*getEmissionProbability(state, obs[0]));
        }
        while(!stateNoObs.isEmpty())
            propagateSilentStatesForward(stateNoObs, alphaInit, obs[0]);

        // Calculate total probability
        double retSum = 0;
        for (String state : states)
            if(!isSilentState(state))
                retSum += alphaInit.get(state)*getBeta(obs[0], state);

        return retSum;
    }

    private void backwardScale(String[] obs)
    {
        int T = obs.length - 1;

        // initializeHmm
        beta.clear();
        Hashtable<String, Double> betaFinal = new Hashtable<String, Double>();
        for(String state : states)
            betaFinal.put(state, 1.0/scaleFactor.get(obs[T]));
        beta.put(obs[T], betaFinal);

        for (int t = T; t > 0; t--)
        {
            Hashtable<String, Double> betaBefore = new Hashtable<String, Double>();

            for (String prevState : states)
            {
                double sum = 0;
                for (String sourceState : states){
                    // repeat until all silent states are considered properly. This requirement is met when sum is not updated anumore.
                    double prevSum = -1;
                    while(sum != prevSum){
                        prevSum = sum;
                        sum = 0;
                        for (String currentState : states)
                            sum += getBeta(obs[t], currentState)*getTransitionProbability(prevState, currentState)*getEmissionProbability(currentState, obs[t]);
                    }
                    sum += getBeta(obs[t], sourceState)*getTransitionProbability(prevState, sourceState)*getEmissionProbability(sourceState, obs[t]);
                }

                betaBefore.put(prevState, sum/scaleFactor.get(obs[t-1]));
            }
            beta.put(obs[t-1], betaBefore);
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
        plogprobinit = logprobf;
        // calculate beta, this should be called after forwardScale
	backwardScale(obs);
        computeZeta(obs);
	computeGamma(obs);
	logprobprev = logprobf;

        int T = obs.length - 1;
        do  {
            
            // update start probability
            sum = 0;
            for(String state : states){
                if(getStartProbability(state) != 0)
                    setStartProbability(state, minProb+(1-minProb)*getGamma(obs[0], state));
                sum += getStartProbability(state);
            }
            // normalize elements to make sum of them equal to 1
            if(sum != 0)
                for(String state : states)
                    setStartProbability(state, getStartProbability(state)/sum);

            // reestimate transitionProbability matrix and emissionProbability matrix in each state
            for (String sourceState : states) {
                gammaSum = 0.0;
                for (int t = 0; t <= T - 1; t++)
                    gammaSum += getGamma(obs[t], sourceState);

                sum = 0;
                for (String targetState : states) {
                    zetaSum = 0.0;
                    for (int t = 0; t <= T - 1; t++)
                        zetaSum += getZeta(obs[t], sourceState, targetState);

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
                    for (int t = 0; t <= T - 1; t++) {
                        if (obs[t].equals(obsSet))
                            gammaSumOt += getGamma(obs[t], sourceState);
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

        pniter = itrNum;
	plogprobfinal = logprobf; /* log P(O|estimated model) */

        if(showResult){
            if(delta > 0){
                System.out.println("Baum-Welch learning of HMM_"+name+" is done.");
                System.out.println("Number of iterations: "+pniter);
                System.out.println("Delta: "+delta);
                System.out.println("log P(obs | init model): "+plogprobinit);
                System.out.println("log P(obs | estimated model): "+plogprobfinal);
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
        int T = obs.length - 1;
	double sum;

        zeta.clear();
	for (int t = 0; t <= T - 1; t++) {
            sum = 0.0;
            Hashtable<String, Hashtable> firstLevelElement = new Hashtable<String, Hashtable>();
            for (String sourceState : states){
                Hashtable<String, Double> secondLevelElement = new Hashtable<String, Double>();
                for (String targetState : states) {
                    double value = getAlpha(obs[t], sourceState);
                    if(isSilentState(targetState))
                        value *= getBeta(obs[t], targetState);
                    else
                        value *= getBeta(obs[t+1], targetState);
                    value *= getTransitionProbability(sourceState, targetState);
                    if(!isSilentState(targetState))
                        value *= getEmissionProbability(targetState, obs[t+1]);
                    else
                        value *= getEmissionProbability(targetState, obs[t+1]);
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
            zeta.put(obs[t], firstLevelElement);
        }
    }

    private void computeGamma(String[] obs)
    {
        int T = obs.length - 1;

	for (int t = 0; t <= T - 1; t++) {
            Hashtable<String, Double> gammaElement = new Hashtable<String, Double>();
            for (String sourceState : states) {
                double gammaValue = 0;
                Hashtable<String, Double> zetaElement = (Hashtable<String, Double>) ((Hashtable<String, Hashtable>) zeta.get(obs[t])).get(sourceState);
                for (String targetState : states)
                    gammaValue += zetaElement.get(targetState);
                gammaElement.put(sourceState, gammaValue);
            }
            gamma.put(obs[t], gammaElement);
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

    public double getViterbiPathProbability(String state){
        Object[] objs = finalT.get(state);

        return ((Double) objs[2]);
    }


    public ArrayList<String> getViterbiPath(){
        ArrayList<String> argmax = null;
        double valmax = 0;

        for (String state : states)
        {
            Object[] objs = finalT.get(state);
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

    public String getViterbiPathString(){
        ArrayList<String> path = getViterbiPath();
        String out = "";

        for(String element : path)
            out = out + "," +element;

        return out;
    }

    public String[] getViterbiPathToArray(){
        ArrayList<String> path = getViterbiPath();
        String[] out = new String[path.size()];

        for(int i = 0; i<path.size(); i++)
            out[i] = path.get(i);

        return out;
    }

    public ArrayList<String> getViterbiPath(String state){
        Object[] objs = finalT.get(state);

        return  (ArrayList<String>) objs[1];
    }

    public String getViterbiPathString(String state){
        Object[] objs = finalT.get(state);
        ArrayList<String> path = (ArrayList<String>) objs[1];
        String out = "";

        for(String element : path)
            out = out + "," +element;

        return out;
    }

    public String[] getViterbiPathToArray(String state){
        Object[] objs = finalT.get(state);
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
    }

    public boolean isSilentState(String state){
        return silentState.get(state);
    }

    public void setStateSilent(String state){
        setSilentState(state, true);
        for(String obs : observationSet)
            setEmissionProbability(state, obs, 0);
    }
}
