/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.learning.kmeans;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author matthias
 * 
 * Tests the class KMeans and its provided k-mean clustering algorithm,
 */
public class TestKMeans {
    public static boolean control(List<KMeans.KMeanCluster> found, List<Float> expected) {
        if (found.size() != expected.size()) {
            System.out.println("FATAL ERROR: wrong number of clusters.");
            System.out.println("\t expected " + expected.size() + " cluster and found " + found.size());
            return false;
        }
        for (KMeans.KMeanCluster f : found) {
            boolean isHere = false;
            for (Float e : expected) {
                if (f.getLocation() == e) {
                    isHere = true;
                    break;
                }
            }
            if (!isHere) {
                System.out.println("FATAL ERROR: cluster location not found.");
                System.out.println("\t no cluster found for location " + f.getLocation());
            }
        }
        
        return true;
    }
    
    public static void main(String [] args) {
        List<Float> expected = new ArrayList<Float>();
        
        List<Integer> values = new ArrayList<Integer>();
        values.add(50000);
        expected.add(50000.0f);
        
        if (!control(KMeans.getInstance().getMeans(values, 100), expected)) return;
        System.out.println("Test 1 terminated succesfully...");
        
        values.clear();
        values.add(50000);
        values.add(60000);
        expected.clear();
        expected.add(55000.0f);
        
        if (!control(KMeans.getInstance().getMeans(values, 100), expected)) return;
        System.out.println("Test 2 terminated succesfully...");
        
        values.clear();
        values.add(50000);
        values.add(60000);
        values.add(200000);
        expected.clear();
        expected.add(55000.0f);
        expected.add(200000.0f);
        
        if (!control(KMeans.getInstance().getMeans(values, 100), expected)) return;
        System.out.println("Test 3 terminated succesfully...");
        
        values.clear();
        values.add(55000);
        values.add(60000);
        values.add(65000);
        values.add(200000);
        values.add(300000);
        expected.clear();
        expected.add(60000.0f);
        expected.add(200000.0f);
        expected.add(300000.0f);
        
        if (!control(KMeans.getInstance().getMeans(values, 100), expected)) return;
        System.out.println("Test 4 terminated succesfully...");
        
        System.out.println("All tests terminated succesfully...");
    }
}
