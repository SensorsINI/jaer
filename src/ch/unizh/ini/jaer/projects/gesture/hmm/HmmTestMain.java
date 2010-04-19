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
//        exampleGesture1();
//        exampleGesture2();
        exampleGesture3();
    }

    /**
     * test gesture recognition based on dynamic threshold model
     */
    public static void exampleGesture3()
    {
        String[] featureVectorSpace = new String[] {"0", "1", "2", "3"};
        int numState = 4;

        String[][] gesture = {{"0", "1", "2", "3"}, {"1", "3", "2", "1"}, {"3", "1", "1", "2"}, {"2", "1", "0", "0"}};
        String[] names = {"gesture1", "gesture2", "gesture3", "gesture4"};

        GestureHmm ghmm = new GestureHmm(featureVectorSpace, true); // use threshold model
        for(int i=0; i<names.length; i++){
            ghmm.addGesture(names[i], numState);
//          ghmm.initializeGestureRandomErgodic(names[i]);
            ghmm.initializeGestureRandomLeftRight(names[i], 2);
//          ghmm.initializeGestureRandomLeftRightBanded(names[i]);

            if(ghmm.learnGesture(names[i], gesture[i])){
                System.out.println(names[i]+" is properly registered. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(names[i]).forward(gesture[i])));
                ghmm.printGesture(names[i]);
                ghmm.getGestureHmm(names[i]).viterbi(gesture[i]);
                System.out.println("Viterbi path : " + ghmm.getGestureHmm(names[i]).getViterbiPathString(gesture[i].length));
            }
        }

        System.out.println(ghmm.getNumGestures()+" guestures are registered.");

        ghmm.printThresholdModel();

        for(int i=0; i<gesture.length; i++){
            System.out.println("Best matching gesture of " + names[i] + " is the " + ghmm.getBestMatchingGesture(gesture[i]) +". ");
            for(int j=0; j<gesture.length; j++){
                System.out.println("Likelyhood of " + names[j] + " in " + names[i] + " model : "+ Math.log10(ghmm.getGestureLikelyhood(names[i], gesture[j])));
                System.out.println("Likelyhood of " + names[j] + " in threshold model : "+ Math.log10(ghmm.getGestureLikelyhoodTM(1.0, gesture[j])));
            }
        }
    }


    /**
     * test dynamic threshold model
     */
    public static void exampleGesture2()
    {
        String[] featureVectorSpace = new String[] {"0", "1", "2", "3"};
        int numState = 4;

        String[] gesture1 = new String[] {"0", "1", "2", "3"};
        String name = "gesture1";

        String[] gesture2 = new String[] {"0", "3", "2", "1"};

        GestureHmm ghmm = new GestureHmm(featureVectorSpace, true);
        ghmm.addGesture(name, numState);
//        ghmm.initializeGestureRandomErgodic(name);
        ghmm.initializeGestureRandomLeftRight(name, 2);
//        ghmm.initializeGestureRandomLeftRightBanded(name);

        if(ghmm.learnGesture(name, gesture1)){
            System.out.println(name+" is properly registered. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(name).forward(gesture1)));
            ghmm.printGesture(name);
            ghmm.getGestureHmm(name).viterbi(gesture1);
            System.out.println(ghmm.getGestureHmm(name).getViterbiPathString(gesture1.length));
        }

        System.out.println(ghmm.getNumGestures()+" guestures are registered.");

        ghmm.printGesture(name);
        ghmm.printThresholdModel();

        System.out.println("Likelyhood of gesture1 in gesture model : "+ Math.log10(ghmm.getGestureLikelyhood(name, gesture1)));
        System.out.println("Likelyhood of gesture1 in threshold model : "+ Math.log10(ghmm.getGestureLikelyhoodTM(0.1, gesture1)));
        System.out.println("Likelyhood of gesture2 in gesture model : "+ Math.log10(ghmm.getGestureLikelyhood(name, gesture2)));
        System.out.println("Likelyhood of gesture2 in threshold model : "+ Math.log10(ghmm.getGestureLikelyhoodTM(0.1, gesture2)));
    }

    /**
     * Test the performance of gesture recognition module
     */
    public static void exampleGesture1()
    {
        String[] featureVectorSpace = new String[] {"0", "1", "2", "3"};
        String[][] observations = GestureHmm.genCompleteObsSeqSet(featureVectorSpace, 4, false);
        int numState = 4;

        String[] gesture1 = new String[] {"0", "1", "2", "3"};
        String name = "gesture1";
        
        GestureHmm ghmm = new GestureHmm(featureVectorSpace, false);
        ghmm.addGesture(name, numState);
//        ghmm.initializeGestureRandomErgodic(name);
        ghmm.initializeGestureRandomLeftRight(name, 2);
//        ghmm.initializeGestureRandomLeftRightBanded(name);

        if(ghmm.learnGesture(name, gesture1)){
            System.out.println(name+" is properly registered. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(name).forward(gesture1)));
            ghmm.printGesture(name);
            ghmm.getGestureHmm(name).viterbi(gesture1);
            System.out.println(ghmm.getGestureHmm(name).getViterbiPathString(gesture1.length));
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
        String[] observations1 = new String[] {"o1", "o3", "o3", "o1", "o2"};
        String[] observations2 = new String[] {"o3", "o1", "o2", "o1", "o1", "o2", "o1", "o1", "o3"};

        String[] observationSet = new String[] {"o1", "o2", "o3"};

        String[] states1 = new String[] {"start", "a1", "a2", "final"};
        double[] startProbability1 = {1, 0, 0, 0};
        double[][] transitionProbability1 = {{0, 1.0, 0, 0}, {0, 0.3, 0.7, 0}, {0, 0, 0.5, 0.5}, {1.0, 0, 0, 0}};
        double [][] emissionProbability1 = {{0, 0, 0}, {0.1, 0.2, 0.7}, {0.3, 0.5, 0.2}, {0, 0, 0}};

        String[] states2 = new String[] {"a1", "a2"};
        double[] startProbability2 = {1, 0};
        double[][] transitionProbability2 = {{0.3, 0.7}, {0.5, 0.5}};
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

        Object[] objs;
        System.out.println("Forward likelyhood of observation1 in " + hmm1.getName() + " : " + hmm1.forward(observations1));
        System.out.println("Forward likelyhood of observation1 in " + hmm2.getName() + " : " + hmm2.forward(observations1));
        System.out.println("Backward Likelyhood of observation1 in " + hmm1.getName() + " : " + hmm1.backward(observations1));
        System.out.println("Backward Likelyhood of observation1 in " + hmm2.getName() + " : " + hmm2.backward(observations1));
        objs = hmm1.viterbi(observations1);
        System.out.println("Viterbi of observation1 in " + hmm1.getName() + " : v_path = " + hmm1.getViterbiPathString(observations1.length) + ", v_prob = " + (Double) objs[2]);
        objs = hmm2.viterbi(observations1);
        System.out.println("Viterbi of observation1 in " + hmm2.getName() + " : v_path = " + hmm2.getViterbiPathString(observations1.length) + ", v_prob = " + (Double) objs[2]);

        System.out.println("Forward likelyhood of observation2 in " + hmm1.getName() + " : " + hmm1.forward(observations2));
        System.out.println("Forward likelyhood of observation2 in " + hmm2.getName() + " : " + hmm2.forward(observations2));
        System.out.println("Backward Likelyhood of observation2 in " + hmm1.getName() + " : " + hmm1.backward(observations2));
        System.out.println("Backward Likelyhood of observation2 in " + hmm2.getName() + " : " + hmm2.backward(observations2));
        objs = hmm1.viterbi(observations2);
        System.out.println("Viterbi of observation2 in " + hmm1.getName() + " : v_path = " + hmm1.getViterbiPathString(observations2.length) + ", v_prob = " + (Double) objs[2]);
        objs = hmm2.viterbi(observations2);
        System.out.println("Viterbi of observation2 in " + hmm2.getName() + " : v_path = " + hmm2.getViterbiPathString(observations2.length) + ", v_prob = " + (Double) objs[2]);

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
        System.out.println("The best path for observation (" + observations[0]+", " + observations[1]+", " + observations[2]+") is "+hmm.getViterbiPathString(observations.length)+" with probability "+((Double) ret[2]).floatValue());
//        hmm.printAllProbability();
        hmm.BaumWelch(observations, 0.00001, 0.0001, true);
        System.out.println("Probability after learning = " + hmm.forward(observations));
        hmm.printAllProbability();
        ret = hmm.viterbi(observations);
        System.out.println("The best path for observation (" + observations[0]+", " + observations[1]+", " + observations[2]+") is "+hmm.getViterbiPathString(observations.length)+" with probability "+((Double) ret[2]).floatValue());
        ret = hmm.viterbi(observations1);
        System.out.println("The best path for observation (" + observations1[0]+", " + observations1[1]+", " + observations1[2]+") is "+hmm.getViterbiPathString(observations1.length)+" with probability "+((Double) ret[2]).floatValue());
        ret = hmm.viterbi(observations2);
        System.out.println("The best path for observation (" + observations2[0]+", " + observations2[1]+", " + observations2[2]+") is "+hmm.getViterbiPathString(observations2.length)+" with probability "+((Double) ret[2]).floatValue());
        
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
            System.out.println("The best path for observation (" + observations[k][0]+", " + observations[k][1]+", " + observations[k][2]+") is "+hmm.getViterbiPathString(observations[k].length)+" with probability "+((Double) ret[2]).floatValue());
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

        System.out.println("Alice guesses that the weather was "+ hmm.getViterbiPathString(observations.length) +" with probability "+((Double) ret[2]));

        for(String st: states){
           System.out.println(st+" : v_path="+hmm.getViterbiPathString(observations.length, st)+", v_prob="+hmm.getViterbiPathProbability(observations.length, st));
        }
    }
}

