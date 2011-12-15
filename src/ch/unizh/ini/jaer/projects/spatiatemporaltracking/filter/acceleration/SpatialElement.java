/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.util.Coord;

/**
 *
 * @author matthias
 */
public interface SpatialElement<Element> {
    public Coord getCoord();
    public Element getElement();
}
