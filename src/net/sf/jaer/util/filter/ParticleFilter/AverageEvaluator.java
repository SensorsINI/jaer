/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.filter.ParticleFilter;

import java.awt.geom.Point2D;

/**
 *
 * @author minliu
 */
public class AverageEvaluator implements ParticleEvaluator<SimpleParticle, Point2D.Double>{

    @Override
    public Point2D.Double evaluate(SimpleParticle p) {
        return p.getP();
    }
}
