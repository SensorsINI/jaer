/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.acceleration;

import java.util.List;

/**
 *
 * @author matthias
 */
public interface AccelerationStructureItem<Element> {
    
    public List<SpatialElement<Element>> getSpatialElements();
}
