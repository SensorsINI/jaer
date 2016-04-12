/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.filter.ParticleFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 *
 * @author minliu and hongjie
 */
public class MeasurmentEvaluator implements ParticleEvaluator<SimpleParticle, Double> {
	List<Float> muX = new ArrayList<Float>(), muY = new ArrayList<Float>();
	double	sigma	= Math.sqrt(20);
        List<Boolean> visibleCluster = new ArrayList<Boolean>();
        List<Double> measurementWeight = new ArrayList<Double>();

        public List<Double> getMeasurementWeight() {
            return measurementWeight;
        }

        public void setMeasurementWeight(List<Double> measurementWeight) {
            this.measurementWeight = measurementWeight;
        }

        public List<Boolean> getVisibleCluster() {
            return visibleCluster;
        }

        public void setVisibleCluster(List<Boolean> visibleClusterNum) {
            this.visibleCluster = visibleClusterNum;
        }


	double noise = 0.0;
	int type = 0;
	Random r = new Random();

        public void setMu(List<Float> x, List<Float> y) {
            this.muX = x;
            this.muY = y;
        }

        public List<Float> getMuX() {
            return muX;
        }
        
        public List<Float> getMuY() {
            return muY;
        }
        public double getSigma() {
            return sigma;
        }

        public void setSigma(double sigma) {
            this.sigma = sigma;
        }       
	public Double  evaluate(SimpleParticle p) {
		double x = p.getX();
                double y = p.getY();
		double error = r.nextDouble()*noise;
		double result = 0;
		switch(type) {
		case 0: result = gaussian(x, y, muX, muY, measurementWeight, sigma); break;
//		case 1: result = Math.max(gaussian(x, mu, sigma), gaussian(x, -mu, sigma)); break;
//		case 2: result = Math.max(gaussian(x, mu, sigma), 0.9*gaussian(x, -mu, sigma)); break;
//		case 3: result = Math.max(((Math.abs(mu-x))<sigma/5)?1:0, (Math.abs(-mu-x)<sigma/5)?0.5:0); break;
		}
		return result + error; 
	}
	
	public static double gaussian(double x, double y, List<Float> muX, List<Float> muY, List<Double> measurementWeight, double sigma) {
		List<Double> d2 = new ArrayList<Double>();

                double evaluateVal = 0;
                int visibleCount = 0;
                for(int i = 0; i < muX.size(); i ++) {
                    // if(visibleFlg[i]) {
                        double d = (x - muX.get(i)) * (x - muX.get(i)) + (y - muY.get(i)) * (y - muY.get(i));
                        d2.add(i, d*measurementWeight.get(i));                        
                        visibleCount += 1;
                    // }
                }
                
                Collections.sort(d2); // Sort the distance list from small to big, smaller means closer
                // d2.set(0, d2.get(0) / 10); // Make the closest measurment has the biggest weight 
                
                for(int i = 0; i < muX.size(); i ++) {
                    evaluateVal +=  (Math.exp(-d2.get(i) / (2* sigma * sigma)));    
                }
                
                if(visibleCount != 0) {
                    return evaluateVal/visibleCount;                    
                } else {
                    return 0;
                }
	}
	
}
