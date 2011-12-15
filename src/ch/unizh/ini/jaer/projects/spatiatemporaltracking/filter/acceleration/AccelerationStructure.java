/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.util.Coord;
import java.util.List;
import java.util.Set;

/**
 *
 * @author matthias
 */
public interface AccelerationStructure<Element> {
    
    public void add(Coord coord, Element e);
    
    public Element getClosest(Coord coord);
    
    public AccelerationStructureItem get(Coord coord);
    public List<AccelerationStructureItem> getNeighbours(Coord coord);
    
    public List<AccelerationStructureItem> getItems();
    public Set<Element> getElements();
    
    public void remove(Element e);
    public void clear();
}
