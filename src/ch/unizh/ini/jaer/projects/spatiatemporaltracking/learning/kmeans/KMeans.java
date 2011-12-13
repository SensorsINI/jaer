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
 * The class is used to perform a k-mean clustering of the given data.
 */
public class KMeans {
    
    /** Stores the instance of the class. */
    private static KMeans instance = null;
    
    /**
     * Gets the instance of this class. It uses the singelton principle.
     * 
     * @return The instance of this class.
     */
    public static KMeans getInstance() {
        if (instance == null) {
            instance = new KMeans();
        }
        return instance;
    }
    
    /**
     * Performs the k-mean clustering according to the given data.
     * 
     * @param data The data used.
     * @param bound The error bound.
     * @return The clusters computed by k-mean clustering.
     */
    public List<KMeanCluster> getMeans(List<Integer> data, int bound) {
        return this.getMeans(data.toArray(new Integer[0]), bound);
    }
    
    /**
     * Performs the k-mean clustering according to the given data. As soon as
     * the overall error exceeds the given bound the algorithm creates 
     * automatically new clusters.
     * 
     * @param data The data used.
     * @param bound The error bound.
     * @return The clusters computed by k-mean clustering.
     */
    public List<KMeanCluster> getMeans(Integer [] data, int bound) {
        // initialize
        List<KMeanCluster> means = new ArrayList<KMeanCluster>();
        
        int worstAssignment = 0;
        double worstAssignmentCost = 0;
        double oldCost = Double.MAX_VALUE;
        double newCost = Double.MAX_VALUE;
        do {
            /*
             * creates a new cluster.
             */
            means.add(new KMeanCluster(data[worstAssignment]));
            do {
                worstAssignment = 0;
                worstAssignmentCost = 0;
                
                oldCost = newCost;
                newCost = 0;

                /*
                 * assigns the object to the nearest mean.
                 */
                for (KMeanCluster cluster : means) {
                    cluster.clear();
                }
                
                for (int i = 0; i < data.length; i++) {
                    double min = means.get(0).cost(data[i]);
                    KMeanCluster best = means.get(0);
                    
                    for (int j = 1; j < means.size(); j++) {
                        double cost = means.get(j).cost(data[i]);

                        if (min > cost) {
                            min = cost;
                            best = means.get(j);
                        }
                    }
                    best.assign(data[i]);
                    
                    if (worstAssignmentCost < min) {
                        worstAssignment = i;
                    }
                }

                /*
                 * minimizes the error by setting the cluster to the mean
                 * position of all assigned objects.
                 */
                double cost = 0;
                for (KMeanCluster cluster : means) {
                    cluster.minimize();
                    newCost += cluster.cost();
                }
                /*
                 * computes if a improvment exists with the current number of 
                 * clusters
                 */
            } while (oldCost - newCost > 1);
            /*
             * computes if the error is below the given bound.
             */
        } while (newCost > bound);
        
        return means;
    }
    
    /*
     * The KMeanCluster is used by the k-mean clustering algorithm and 
     * represents a cluster.
     */
    public class KMeanCluster {
        
        /** The position of the cluster. */
        private float location;
        
        /** The list of assigned objects. */
        private List<Integer> assigned;
        
        /**
         * Creates a new KMeanCluster.
         * 
         * @param location The initial location of the cluster.
         */
        public KMeanCluster(int location) {
            this.location = location;
            this.assigned = new ArrayList<Integer>();
        }
        
        /**
         * Minimizes the error by setting the position of the cluster to the
         * mean of all assigned objects.
         */
        public void minimize() {
            this.location = 0;
            for (Integer item : this.assigned) {
                this.location += item;
            }
            this.location /= this.assigned.size();
        }
        
        /**
         * Assigns the given item to the cluster.
         * 
         * @param item The item to assign to the cluster.
         */
        public void assign(int item) {
            this.assigned.add(item);
        }
        
        /**
         * Clears the cluster.
         */
        public void clear() {
            this.assigned.clear();
        }
        
        /**
         * Computes the cost by measuring the distance between the position of
         * the given item and the position of the cluster.
         * 
         * @param item The item from which the distance to the cluster has to
         * be computed.
         * @return The cost of the given item.
         */
        public double cost(int item) {
            return Math.pow((item - this.location) / 1000.0, 2.0);
        }
        
        /**
         * Computes the cost by summing up the cost from each assigned item.
         * 
         * @return The cost of all assigned items.
         */
        public double cost() {
            double cost = 0;
            for (Integer item : this.assigned) {
                cost += this.cost(item);
            }
            return cost;
        }
        
        /**
         * Gets the number of the assigned objects.
         * 
         * @return The number of the assigned objects.
         */
        public int getSize() {
            return this.assigned.size();
        }
        
        /**
         * Gets the position of the cluster.
         * 
         * @return The position of the cluster.
         */
        public float getLocation() {
            return this.location;
        }
    }
}
