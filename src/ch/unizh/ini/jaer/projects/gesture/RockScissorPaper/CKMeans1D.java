/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;
import java.util.Arrays;
import java.util.Random;

/**
 *
 * @author Eun Yeong Ahn
 */
public class CKMeans1D {
    double[] data;
    int n;      //# of data
    int K;
    double[] centroids;
    double[] sum = null;
    int[] count = null;

    public double[] KMeans(double[] d, int k){
        //1. initialize
        n = d.length;
        K = k;
        centroids = new double[K];
        sum = new double[K];
        count = new int[K];
        data = d.clone();
        InitCentroidByMaxMin();
        //InitCentroidByKCenter();

        for(int i = 0; i < 5; i++){
            //2. Assign data to each centroid
            AssignCentroid();
            //3. update centroid
            UpdateCentroid();
        }

        return centroids;
    }

    private void AssignCentroid() {
        for(int i = 0; i< K; i++){
            sum[i] = 0;
            count[i] = 0;
        }

        for(int i = 0; i< n; i++){
            // Find the nearest centroid
            double min_dist = 10000;
            int c = -1;             // nearest centroid
            for(int j = 0; j < K; j++){
                double dist = Math.abs(centroids[j] - data[i]);
                if(c == -1 || min_dist > dist){
                    c = j;
                    min_dist = dist;
                }
            }

            //assign the data to the nearest centroid
            sum[c] = sum[c] + data[i];
            count[c] = count[c] + 1;
        }
    }

    private void UpdateCentroid() {
        for(int i = 0; i < K; i++){
            centroids[i] = sum[i] / (double)count[i];
        }
    }

    //##############################################    Only work when K = 2
    private void InitCentroidByKCenter() {
        Random random = new Random();
        int idx = random.nextInt(n);
        centroids[0] = idx;
        int c_idx = -1;             // nearest centroid
        double max_dist = -1;

        for(int i = 0; i< n; i++){
            // Find the nearest centroid
            double dist = Math.abs(centroids[0] - data[i]);
            if(c_idx == -1 || max_dist < dist){
                c_idx = i;
                max_dist = dist;
            }
        }
        centroids[1] = data[c_idx];
    }

    private void Print() {
        System.out.println("Centroids ------------------------ ");
        for(int i = 0; i < K; i++){
            System.out.println(i+ ": "+centroids[i] +" : "+ sum[i] +" : "+ count[i]);
        }
    }

    private void InitCentroidByMaxMin() {
        Arrays.sort(data);
        centroids[0] = data[0];
        centroids[1] = data[data.length-1];
    }

}
