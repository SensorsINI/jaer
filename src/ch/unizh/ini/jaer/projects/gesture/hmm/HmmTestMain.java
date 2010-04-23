package ch.unizh.ini.jaer.projects.gesture.hmm;

import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;

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
//        bob_Alice(); // Bob and Alice example in Wikipedia
//        testViterbi(); // Test Viterbi algorithm
//        testBaumWelch(); // Test Baum-Welch learning
//        testSilentState(); // Test forward, backward, and Viterbi algorithm with silent states, Currently, Baum-Welch method does not support silent states.
//        testGesture1(); // Test the performance of gesture recognition module. All possible observations are scanned.
//        testDynamicThreshold(); // Test dynamic threshold model
//        testGesture2(); // Test gesture recognition based on dynamic threshold model
        testGestureWithHandDrawing();
    }

    /**
     * test with hand drawing panel
     */
    public static void testGestureWithHandDrawing(){
        String [] bNames = {"Add gesture", "Reset gesture", "Learn", "Guess"};
        HandDrawingTest hdt = new HandDrawingTest("Feature Extraction Test", bNames);
    }

    static class HandDrawingTest extends TrajectoryDrawingPanel implements ItemListener{
        public final String ADD_GESTURE = "Add gesture";
        public final String RESET_GESTURE = "Reset gesture";
        public final String LEARN = "Learn";
        public final String GUESS = "Guess";

        public HashSet<String> gestureItems = new HashSet<String>();

        Choice gestureChoice;
        TextField newGesture;

        int numState = 8;

        String[] featureVectorSpace = new String[] {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"};
        GestureHmm ghmm = new GestureHmm(featureVectorSpace, true); // use threshold model
        FeatureExtraction fve = new FeatureExtraction(16, 16);

        public HandDrawingTest(String title, String[] buttonNames) {
            super(title, buttonNames);
        }

        @Override
        public void initLayout(String[] componentNames) {
            setLayout(new BorderLayout());
            gestureChoice = new Choice();
            newGesture = new TextField(10);

            // configuration of button panel
            Panel buttonPanel = new Panel();
            buttonPanel.setLayout(new GridLayout(2, componentNames.length+1));
            buttonPanel.add(gestureChoice, "1");
            buttonPanel.add(new Label("             Input new name:"), "2");
            buttonPanel.add(newGesture, "3");
            for(int i = 1; i<= componentNames.length; i++){
                Button newButton = new Button(componentNames[i-1]);
                buttonPanel.add(newButton, ""+(i+3));
                newButton.addActionListener(this);
            }
            Button clearButton = new Button(clearButtonName);
            buttonPanel.add(clearButton, ""+ (2*componentNames.length));
            clearButton.addActionListener(this);
            add(buttonPanel, "South");

            gestureChoice.addItemListener(this);

            setBounds(100, 100, PANEL_SIZE+50, PANEL_SIZE+50);
            setResizable(false);
        }

        @Override
        public void buttonAction(String buttonName) {
            if(buttonName.equals(LEARN)){
                doLearn();
                clearImage();
            } else if(buttonName.equals(ADD_GESTURE)){
                doAddGesture();
            } else if(buttonName.equals(GUESS)){
                doGuess();
                clearImage();
            } else if(buttonName.equals(RESET_GESTURE)){
                doReset();
                clearImage();
            } else {
                
            }
        }
        
        public void doLearn(){
            String gesName = gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }
            
            String[] fv = fve.convToFeatureArray(trajectory);
            if(fv[0] == null){
                System.out.println("Warning: No trajectory is dected.");
                return;
            }
            
            if(ghmm.learnGesture(gesName, fv, true, true, true)){
                if(ghmm.getGestureHmm(gesName).getNumTraining() == 1)
                    System.out.println(gesName+" is properly registered. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(gesName).forward(fv)));
                else if(ghmm.getGestureHmm(gesName).getNumTraining() == 2)
                    System.out.println(gesName+" has been trained twice. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(gesName).forward(fv)));
                else
                    System.out.println(gesName+" has been trained " + ghmm.getGestureHmm(gesName).getNumTraining() + " times. Log{P(O|model)} = " + Math.log10(ghmm.getGestureHmm(gesName).forward(fv)));
                
//                ghmm.printGesture(gesName);
//                ghmm.getGestureHmm(gesName).viterbi(fv);
//                System.out.println("Viterbi path : " + ghmm.getGestureHmm(gesName).getViterbiPathString(fv.length));
            }
        }

        public void doAddGesture(){
            String newGestName = newGesture.getText();
            if(newGestName.equals("")){
                System.out.println("Warning: Gesture name is not specified.");
                return;
            }

            if(!gestureItems.contains(newGestName)){
                gestureItems.add(newGestName);
                gestureChoice.add(newGestName);
                ghmm.addGesture(newGestName, numState);
                ghmm.initializeGestureRandomLeftRightBanded(newGestName);

                System.out.println("A new gesture ("+ newGestName + ") is added.");
            }
            gestureChoice.select(newGestName);
            newGesture.setText("");
        }

        public void doGuess(){
            String gesName = gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }

            String[] fv = fve.convToFeatureArray(trajectory);
            if(fv[0] == null){
                System.out.println("Warning: No trajectory is dected.");
                return;
            }

            System.out.println("Best matching gesture is " + ghmm.getBestMatchingGesture(fv) +". ");
        }

        public void doReset(){
            String gesName = gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }

            ghmm.resetGesture(gesName);
            System.out.println(gesName + " is reset now.");
        }

        public void itemStateChanged(ItemEvent e) {
            if(e.getStateChange() == ItemEvent.SELECTED){
                System.out.println(e.getItem() + " is selected.");
            }
        }
    }

    /**
     * test gesture recognition based on dynamic threshold model
     */
    public static void testGesture2()
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

            if(ghmm.learnGesture(names[i], gesture[i], true, true, true)){
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
                System.out.println("    Likelyhood of " + names[j] + " in " + names[i] + " model : "+ Math.log10(ghmm.getGestureLikelyhood(names[i], gesture[j])));
                System.out.println("    Likelyhood of " + names[j] + " in threshold model : "+ Math.log10(ghmm.getGestureLikelyhoodTM(1.0, gesture[j])));
            }
        }
    }


    /**
     * Test dynamic threshold model
     */
    public static void testDynamicThreshold()
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

        if(ghmm.learnGesture(name, gesture1, true, true, true)){
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
     * Test the performance of gesture recognition module. All possible observations are scanned.
     */
    public static void testGesture1()
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

        if(ghmm.learnGesture(name, gesture1, true, true, true)){
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

    /**
     * Test forward, backward, and Viterbi algorithm with silent states
     */
    public static void testSilentState()
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

    /**
     * Test Baum-Welch learning
     */
    public static void testBaumWelch()
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
        hmm.BaumWelch(observations, 0.00001, 0.0001, true, true, true, true);
        System.out.println("Probability after learning = " + hmm.forward(observations));
        hmm.printAllProbability();
        ret = hmm.viterbi(observations);
        System.out.println("The best path for observation (" + observations[0]+", " + observations[1]+", " + observations[2]+") is "+hmm.getViterbiPathString(observations.length)+" with probability "+((Double) ret[2]).floatValue());
        ret = hmm.viterbi(observations1);
        System.out.println("The best path for observation (" + observations1[0]+", " + observations1[1]+", " + observations1[2]+") is "+hmm.getViterbiPathString(observations1.length)+" with probability "+((Double) ret[2]).floatValue());
        ret = hmm.viterbi(observations2);
        System.out.println("The best path for observation (" + observations2[0]+", " + observations2[1]+", " + observations2[2]+") is "+hmm.getViterbiPathString(observations2.length)+" with probability "+((Double) ret[2]).floatValue());
        
    }

    /**
     * Test Viterbi algorithm
     */
    public static void testViterbi()
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

    /**
     * Bob and Alice in Wikipedia
     */
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

