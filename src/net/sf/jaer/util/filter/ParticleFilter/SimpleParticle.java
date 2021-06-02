/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.filter.ParticleFilter;

/**
 *
 * @author minliu and hongjie
 */
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Random;
import net.sf.jaer.util.filter.ParticleFilter.Particle;

public class SimpleParticle implements Particle {
	private double strength;
	private double[] point = new double[2];
        
        public double[] getP() {
            return point;
        }

        public void setP(double[] point) {
            this.point = point;
        }

        public double getX() {
            return point[0];
        }

        public double getY() {
            return point[1];
        }
        
        public void setX(double x) {
            point[0] = x;
        }
        
        public void setY(double y) {
            point[1] = y;
        }
                
	public SimpleParticle(double x, double y) {
		this.point[0] = x;
                this.point[1] = y;
	}
	
	public SimpleParticle clone() {
		try {
                    SimpleParticle copy = (SimpleParticle) super.clone();
                    copy.point = new double[2];
                    copy.point[0] = this.point[0];
                    copy.point[1] = this.point[1];
                    copy.strength = this.strength;
                    return copy;
		} catch(CloneNotSupportedException e) {
			throw new Error();
		}
	}
	
	public void addNoise(Random r, double spread) {
		this.point[0] += spread*r.nextGaussian();
                this.point[1] += spread*r.nextGaussian();
	}

	public double getStrength() {
		return strength;
	}

	public void setStrength(double strength) {
		this.strength = strength;
	}
}
