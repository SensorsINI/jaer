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
    private double noise = 0.0;
    private Random r = new Random();

    @Override
    public SimpleParticle evaluate(SimpleParticle p) {
        double x = p.x;
        double error = r.nextDouble()*noise;
        x = x + 0 + error;
        p.x = x;
        return p;
    }    
}
