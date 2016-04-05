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
import java.awt.geom.Point2D;
import java.util.Random;
import net.sf.jaer.util.filter.ParticleFilter.Particle;

public class SimpleParticle implements Particle {
	private double strength;
	public Point2D.Double p = new Point2D.Double();

        public Point2D.Double getP() {
            return p;
        }

        public void setP(Point2D.Double p) {
            this.p = p;
        }

        public double getX() {
            return p.x;
        }

        public double getY() {
            return p.y;
        }
        
        public void setX(double x) {
            p.x = x;
        }
        
        public void setY(double y) {
            p.y = y;
        }
                
	public SimpleParticle(double x, double y) {
		this.p.setLocation(x, y);
	}
	
	public SimpleParticle clone() {
		try {
			return (SimpleParticle) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new Error();
		}
	}
	
	public void addNoise(Random r, double spread) {
		this.p.x += spread*r.nextGaussian();
                this.p.y += spread*r.nextGaussian();
	}

	public double getStrength() {
		return strength;
	}

	public void setStrength(double strength) {
		this.strength = strength;
	}
}
