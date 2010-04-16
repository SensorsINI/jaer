package ch.unizh.ini.jaer.projects.gesture.hmm;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Jun Haeng Lee
 */
public class HmmTestMain
{
    public static void main(String[] args)
    {
//        bob_Alice();
//        example1();
//        example2();
//        example3();
        exampleGesture1();
    }

    public static void exampleGesture1()
    {
        String[] featureVectorSpace = new String[] {"0", "1", "2", "3"};
        String[][] observations = GestureHmm.genCompleteObsSet(featureVectorSpace, 2, false);
        int numState = 5;

        String[] gesture1 = new String[] {"0", "1", "2", "3"};
        String name = "gesture1";
        
//        double [] startProbability = {0.5, 0.5, 0};
//        double [][] transitionProbability = {{0.3, 0.5, 0.2}, {0, 0.5, 0.5}, {0, 0, 0}};
//        double [][] emissionProbability = {{0.4, 0.3, 0.3}, {0.4, 0.3, 0.3}, {0.4, 0.3, 0.3}};

        GestureHmm ghmm = new GestureHmm(featureVectorSpace);
        ghmm.addGesture(name, numState);
//        ghmm.initializeGesture(name, startProbability, transitionProbability, emissionProbability);
//        ghmm.initializeGestureRandomErgodic(name);
//        ghmm.initializeGestureRandomLeftRight(name, 1);
        ghmm.initializeGestureRandomLeftRightBanded(name);
        ghmm.getGestureHmm(name).setStateSilent(name+"_0");
        ghmm.getGestureHmm(name).setStateSilent(name+"_4");

        if(ghmm.learnGesture(name, gesture1)){
            System.out.println(name+" is properly registered. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(name).forward(featureVectorSpace)));
            ghmm.printGesture(name);
            ghmm.getGestureHmm(name).viterbi(featureVectorSpace);
            System.out.println(ghmm.getGestureHmm(name).getViterbiPathString());
        }

        System.out.println(ghmm.getNumGestures()+" guestures are registered.");

        for(String[] obs : observations){
            double dis = calDistance(gesture1, obs, 2);
            System.out.println(dis + ", "+ Math.log10(ghmm.getGestureLikelyhood(name, obs)));
        }
    }

    public static double calDistance(String[]obs1, String[] obs2, int maxDiff){
        double sum = 0;

        for(int i=0; i<Math.min(obs1.length, obs2.length); i++){
            int diff = Math.abs(Integer.parseInt(obs1[i]) - Integer.parseInt(obs2[i]));
            if(diff > maxDiff){
                if(maxDiff%2 == 0)
                    diff = 2*maxDiff - diff;
                else
                    diff = 2*maxDiff + 1 - diff;
            }
            sum += Math.pow(diff, 2.0);
        }

        return Math.sqrt(sum)/obs1.length;
    }

    public static double calDistance2(String[]obs1, String[] obs2){
        double sum = 0;

        for(int i=0; i<Math.min(obs1.length, obs2.length); i++){
            if(!obs1[i].equals(obs2[i]))
                sum += 1.0;
        }

        return sum/obs1.length;
    }

    public static void example3()
    {
        String[] observations1 = new String[] {"o1"};
        String[] observations2 = new String[] {"o3", "o2"};

        String[] observationSet = new String[] {"o1", "o2", "o3"};

        String[] states1 = new String[] {"start", "a1", "a2", "final"};
        double[] startProbability1 = {1, 0, 0, 0};
        double[][] transitionProbability1 = {{0, 1.0, 0, 0}, {0, 0.3, 0.7, 0}, {0, 0, 1.0, 1.0}, {0, 0, 0, 0}};
        double [][] emissionProbability1 = {{0, 0, 0}, {0.1, 0.2, 0.7}, {0.3, 0.5, 0.2}, {0, 0, 0}};

        String[] states2 = new String[] {"a1", "a2"};
        double[] startProbability2 = {1, 0};
        double[][] transitionProbability2 = {{0.3, 0.7}, {0, 1.0}};
        double [][] emissionProbability2 = {{0.1, 0.2, 0.7}, {0.3, 0.5, 0.2}};

        HiddenMarkovModel hmm1 = new HiddenMarkovModel("example3_1", states1, observationSet);
        hmm1.setStartProbability(startProbability1);
        hmm1.setTransitionProbability(transitionProbability1);
        hmm1.setEmissionProbability(emissionProbability1);
        hmm1.printAllProbability();

        HiddenMarkovModel hmm2 = new HiddenMarkovModel("example3_2", states2, observationSet);
        hmm2.setStartProbability(startProbability2);
        hmm2.setTransitionProbability(transitionProbability2);
        hmm2.setEmissionProbability(emissionProbability2);
        hmm2.printAllProbability();

        System.out.println("Forward likelyhood of observation1 in " + hmm1.getName() + " : " + hmm1.forward(observations1));
        System.out.println("Forward likelyhood of observation1 in " + hmm2.getName() + " : " + hmm2.forward(observations1));
        System.out.println("Backward Likelyhood of observation1 in " + hmm1.getName() + " : " + hmm1.backward(observations1));
        System.out.println("Backward Likelyhood of observation1 in " + hmm2.getName() + " : " + hmm2.backward(observations1));
        Object[] objs = hmm1.viterbi(observations1);
        System.out.println("Viterbi of observation1 in " + hmm1.getName() + " : v_path = " + hmm1.getViterbiPathString() + ", v_prob = " + (Double) objs[2]);
        objs = hmm2.viterbi(observations1);
        System.out.println("Viterbi of observation1 in " + hmm2.getName() + " : v_path = " + hmm2.getViterbiPathString() + ", v_prob = " + (Double) objs[2]);

        System.out.println("Forward likelyhood of observation2 in " + hmm1.getName() + " : " + hmm1.forward(observations2));
        System.out.println("Forward likelyhood of observation2 in " + hmm2.getName() + " : " + hmm2.forward(observations2));
        System.out.println("Backward Likelyhood of observation2 in " + hmm1.getName() + " : " + hmm1.backward(observations2));
        System.out.println("Backward Likelyhood of observation2 in " + hmm2.getName() + " : " + hmm2.backward(observations2));
        objs = hmm1.viterbi(observations2);
        System.out.println("Viterbi of observation2 in " + hmm1.getName() + " : v_path = " + hmm1.getViterbiPathString() + ", v_prob = " + (Double) objs[2]);
        objs = hmm2.viterbi(observations2);
        System.out.println("Viterbi of observation2 in " + hmm2.getName() + " : v_path = " + hmm2.getViterbiPathString() + ", v_prob = " + (Double) objs[2]);

    }

    public static void example2()
    {
        String[] observations = new String[] {"o1", "o2", "o3"};
        String[] observations1 = new String[] {"o2", "o1", "o3"};
        String[] observations2 = new String[] {"o3", "o2", "o1"};

        String[] states = new String[] {"a1", "a2", "a3"};
        String[] observationSet = new String[] {"o1", "o2", "o3"};
        double[] startProbability = {1, 0, 0, 0};
        Object[][] transitionProbability = new Object[][] {{"a1", "a1", 0.3}, {"a1", "a2", 0.7}, {"a2", "a2", 0.3}, {"a2", "a3", 0.7}, {"a3", "a3", 0.3}, {"a3", "a1", 0.7}};
        double [][] emissionProbability = {{0.7, 0.2, 0.1}, {0.2, 0.6, 0.2}, {0.1, 0.2, 0.7}};

        HiddenMarkovModel hmm = new HiddenMarkovModel("example2", states, observationSet);
//        hmm.setProbabilityRandomErgodic();
        hmm.setStartProbability(startProbability);
        for(Object[] ob: transitionProbability)
            hmm.setTransitionProbability((String) ob[0], (String) ob[1], ((Double) ob[2]).doubleValue());
        hmm.setEmissionProbability(emissionProbability);
//        hmm.setEmissionProbabilityRandom();


        System.out.println("Probability before learning = " + hmm.forward(observations));
        Object[] ret = hmm.viterbi(observations);
        System.out.println("The best path for observation (" + observations[0]+", " + observations[1]+", " + observations[2]+") is "+(String) ret[1]+" with probability "+((Double) ret[2]).floatValue());
//        hmm.printAllProbability();
        hmm.BaumWelch(observations, 0.00001, 0.0001, true);
        System.out.println("Probability after learning = " + hmm.forward(observations));
        hmm.printAllProbability();
        ret = hmm.viterbi(observations);
        System.out.println("The best path for observation (" + observations[0]+", " + observations[1]+", " + observations[2]+") is "+(String) ret[1]+" with probability "+((Double) ret[2]).floatValue());
        ret = hmm.viterbi(observations1);
        System.out.println("The best path for observation (" + observations1[0]+", " + observations1[1]+", " + observations1[2]+") is "+(String) ret[1]+" with probability "+((Double) ret[2]).floatValue());
        ret = hmm.viterbi(observations2);
        System.out.println("The best path for observation (" + observations2[0]+", " + observations2[1]+", " + observations2[2]+") is "+(String) ret[1]+" with probability "+((Double) ret[2]).floatValue());
        
    }

    public static void example1()
    {
        String[][] observations = new String[][] {{"o1", "o2", "o3"}, {"o2", "o3", "o2"}, {"o1", "o1", "o2"}, {"o3", "o1", "o2"}, {"o2", "o1", "o3"}, {"o3", "o3", "o3"}};

        String[] states = new String[] {"a1", "a2", "a3", "a4", "b1", "b2", "b3", "b4"};
        String[] observationSet = new String[] {"o1", "o2", "o3"};
        double[] startProbability = {0.5, 0, 0, 0, 0.5, 0, 0, 0};
        Object[][] transitionProbability = new Object[][] {{"a1", "a1", 0.3}, {"a1", "a2", 0.7}, {"a2", "a2", 0.3}, {"a2", "a3", 0.7}, {"a3", "a4", 1.0},
                                                            {"b1", "b1", 0.3}, {"b1", "b2", 0.7}, {"b2", "b2", 0.3}, {"b2", "b3", 0.7}, {"b3", "b4", 1.0}};
        double [][] emissionProbability = {{0.7, 0.2, 0.1}, {0.2, 0.6, 0.2}, {0.1, 0.2, 0.7}, {0, 0, 0},
                                           {0.1, 0.8, 0.1}, {0.1, 0.2, 0.7}, {0.2, 0.6, 0.2}, {0, 0, 0}};

        HiddenMarkovModel hmm = new HiddenMarkovModel("example1", states, observationSet);

        // start probability
        hmm.setStartProbability(startProbability);


        // transition_probability
        for(Object[] ob: transitionProbability)
            hmm.setTransitionProbability((String) ob[0], (String) ob[1], ((Double) ob[2]).doubleValue());

        // emission_probability
        hmm.setEmissionProbability(emissionProbability);

        for(int k= 0; k < observations.length; k++){
            Object[] ret = hmm.viterbi(observations[k]);
            System.out.println("The best path for observation (" + observations[k][0]+", " + observations[k][1]+", " + observations[k][2]+") is "+(String) ret[1]+" with probability "+((Double) ret[2]).floatValue());
            System.out.println("a4: v_path="+hmm.getViterbiPathString("a4")+", v_prob="+hmm.getViterbiPathProbability("a4"));
            System.out.println("b4: v_path="+hmm.getViterbiPathString("b4")+", v_prob="+hmm.getViterbiPathProbability("b4"));
            System.out.println();
        }
    }

    public static void bob_Alice()
    {
        String[] observations = new String[] {"walk","shop","clean"};

        String[] states = new String[] {"Rainy","Sunny"};
        double [] startProbability = {0.6, 0.4};
        String[] observationSet = new String[] {"walk","shop","clean"};

        HiddenMarkovModel hmm = new HiddenMarkovModel("Bob and Alice", states, observationSet);

        // start probability
        hmm.setStartProbability(startProbability);

        // transition_probability
        hmm.setTransitionProbability("Rainy", "Rainy", 0.7);
        hmm.setTransitionProbability("Rainy", "Sunny", 0.3);
        hmm.setTransitionProbability("Sunny", "Rainy", 0.4);
        hmm.setTransitionProbability("Sunny", "Sunny", 0.6);

        // emission_probability
        hmm.setEmissionProbability("Rainy", "walk", 0.1);
        hmm.setEmissionProbability("Rainy", "shop", 0.4);
        hmm.setEmissionProbability("Rainy", "clean", 0.5);

        hmm.setEmissionProbability("Sunny", "walk", 0.6);
        hmm.setEmissionProbability("Sunny", "shop", 0.3);
        hmm.setEmissionProbability("Sunny", "clean", 0.1);

        Object[] ret = hmm.viterbi(observations);

        System.out.println("Alice guesses that the weather was "+ hmm.getViterbiPathString() +" with probability "+((Double) ret[2]));

        for(String st: states){
           System.out.println(st+" : v_path="+hmm.getViterbiPathString(st)+", v_prob="+hmm.getViterbiPathProbability(st));
        }
    }
}

