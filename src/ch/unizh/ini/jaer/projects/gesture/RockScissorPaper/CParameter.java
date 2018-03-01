/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;

/**
 *
 * @author Eun Yeong Ahn
 */
public class CParameter {
    static int cvalue = 3;  //1: rock, 2: scissor, 3: Paper
    static int noOfInstances = 15;

    //Grid Filter
    //static int GridFilterBin = 10;
    //static int GridFilterXBin = 10;
    //static int GridFilterYBin = 10;
    //static double GridFilterThreshold = 0.1;
    //static boolean GridFilterAdaptiveInterval = true;

    static int YDistFilterBin = 5;
    static EBaseAxis DistAxis= EBaseAxis.XY;
    //static int YDistAvgBin = 0;
    //static int MovingThreshold = 3000;

    //
   // static boolean CollectData = false;
    static boolean Prediction = true;
    static String ClearArff = "./data/Clearing.arff";
    static String TrainArff = "./data/Training.arff";
}
