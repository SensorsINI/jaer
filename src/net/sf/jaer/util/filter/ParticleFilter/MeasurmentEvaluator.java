/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.filter.ParticleFilter;

import java.util.Random;

/**
 *
 * @author minliu and hongjie
 */
public class MeasurmentEvaluator implements ParticleEvaluator<SimpleParticle, Double> {
	double[] muX, muY = new double[3];
	double	sigma	= 3;
        boolean[] visibleCluster = new boolean[3];

        public boolean[] getVisibleCluster() {
            return visibleCluster;
        }

        public void setVisibleCluster(boolean[] visibleClusterNum) {
            this.visibleCluster = visibleClusterNum;
        }


	double noise = 0.0;
	int type = 0;
	Random r = new Random();

        public void setMu(double[] x, double[] y) {
            this.muX = x;
            this.muY = y;
        }

        public double[] getMuX() {
            return muX;
        }
        
        public double[] getMuY() {
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
		case 0: result = gaussian(x, y, muX, muY, sigma); break;
//		case 1: result = Math.max(gaussian(x, mu, sigma), gaussian(x, -mu, sigma)); break;
//		case 2: result = Math.max(gaussian(x, mu, sigma), 0.9*gaussian(x, -mu, sigma)); break;
//		case 3: result = Math.max(((Math.abs(mu-x))<sigma/5)?1:0, (Math.abs(-mu-x)<sigma/5)?0.5:0); break;
		}
		return result + error; 
	}
	
	public static double gaussian(double x, double y, double[] muX, double[] muY, double sigma) {
		double[] d2 = new double[4];
                double[] measurementWeight = new double[4];
                measurementWeight[0] = 1;
                measurementWeight[1] = 1;
                measurementWeight[2] = 1;
                measurementWeight[3] = 10;

                double evaluateVal = 0;
                int visibleCount = 0;
                for(int i = 0; i < 3; i ++) {
                    //if(visibleFlg[i]) {
                        d2[i]= (x - muX[i]) * (x - muX[i]) + (y - muY[i]) * (y - muY[i]);
                        evaluateVal += measurementWeight[i] * Math.exp(-d2[i] / (2* sigma * sigma));     
                        visibleCount += 1;
                    //}
                }
                if(visibleCount != 0) {
                    return evaluateVal/visibleCount;                    
                } else {
                    return 0;
                }
	}
	
}
