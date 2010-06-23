/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

/**
 * Test module for FeatureExtraction class
 * @author Jun Haeng Lee
 */
public class FeatureExtractionTestMain{


    /**
     * Main
     * @param args
     */
    public static void main(String[] args)
    {
//        test1(); // Test with simple patterns
        test2(); // Test with hand drawing panel
    }

    /**
     * test with hand drawing panel
     */
    public static void test2(){
        String [] bNames = {"Get codewords"};
        HandDrawingTest hdt = new HandDrawingTest("Feature Extraction Test", bNames);
    }

    static class HandDrawingTest extends TrajectoryDrawingPanel{
        public final String GET_TRJ = "Get codewords";
        FeatureExtraction fve = new FeatureExtraction(16, 16);
        
        public HandDrawingTest(String title, String[] buttonNames) {
            super(title, 500, 500, buttonNames);
        }

        @Override
        public void buttonAction(String buttonName) {
            if(buttonName.equals(GET_TRJ)){
                String[] fv = fve.convTrajectoryToCodewords(trajectory);

                int i = 1;
                System.out.print("Codewords sequence : ");
                for(String fvEle:fv){
                    System.out.print(i+"("+fvEle+", " +fve.vectorAngleSeq[i-1]+ "), ");
                    i++;
                }
                System.out.println();
            }
        }

        @Override
        public void menuAction(String menuName) {
            // do nothing
        }
    }

    /**
     * test with simple patterns
     */
    public static void test1(){
        FeatureExtraction fve = new FeatureExtraction(16, 12);
        SampleTrajectory st = new SampleTrajectory();

        String[] fv = fve.convTrajectoryToCodewords(st.getSampleTrajetory(SampleTrajectory.SAMPLE_TRJ_TYPE.CIRCLE, 200, 128, 128, 10));

        int i = 1;
        System.out.print("Codewords sequence : ");
        for(String fvEle:fv){
            System.out.print(fvEle+"("+i+"), ");
            i++;
        }
        System.out.println();
    }
}
