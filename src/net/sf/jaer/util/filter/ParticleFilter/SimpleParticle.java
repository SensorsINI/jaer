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
import java.util.Random;
import net.sf.jaer.util.filter.ParticleFilter.Particle;

public class SimpleParticle implements Particle {
	private double strength;
	public double x;
	
	public SimpleParticle(double x) {
		this.x = x;
	}
	
	public SimpleParticle clone() {
		try {
			return (SimpleParticle) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new Error();
		}
	}
	
	public void addNoise(Random r, double spread) {
		this.x += spread*r.nextGaussian();
	}

	public double getStrength() {
		return strength;
	}

	public void setStrength(double strength) {
		this.strength = strength;
	}
}
