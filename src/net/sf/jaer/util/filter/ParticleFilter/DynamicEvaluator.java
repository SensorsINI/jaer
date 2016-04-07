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
public class DynamicEvaluator implements ParticleEvaluator<SimpleParticle, SimpleParticle> {
    private double noise = 1;
    private Random r = new Random();

    @Override
    public SimpleParticle evaluate(SimpleParticle p) {
        double x = p.getX();
        double y = p.getY();
        double errorX = r.nextGaussian() * noise;
        double errorY = r.nextGaussian() * noise;
        x = x + 0 + errorX;
        y = y + 0 + errorY;
        p.setX(x);
        p.setY(y);
        return p;
    }    
}
