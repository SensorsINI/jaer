/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.filter.util.Coord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author matthias
 */
public class Block<Element> implements AccelerationStructure<Element> {
    private Map<Element, SpatialElement<Element>> elements = new HashMap<Element, SpatialElement<Element>>();
    
    private Map<SpatialElement<Element>, AccelerationStructureItem> map = new HashMap<SpatialElement<Element>, AccelerationStructureItem>();
    private List<AccelerationStructureItem> items = new ArrayList<AccelerationStructureItem>();
    private BlockNode[][] organized;
    private int resolution;

    public class BlockNode<Element> implements AccelerationStructureItem<Element> {
        private List<SpatialElement<Element>> elements = new ArrayList<SpatialElement<Element>>();
        
        public Coord coord;
        
        public BlockNode(int x, int y) {
            this(new Coord(x, y));
        }
        
        public BlockNode(Coord coord) {
            this.coord = coord;
        }
        
        @Override
        public List<SpatialElement<Element>> getSpatialElements() {
            return this.elements;
        }
    }
    
    public class BlockElement<Element> implements SpatialElement<Element> {

        public Coord coord;
        public Element element;
        
        public BlockElement(Coord coord, Element element) {
            this.coord = coord;
            this.element = element;
        }
        
        @Override
        public Coord getCoord() {
            return this.coord;
        }

        @Override
        public Element getElement() {
            return this.element;
        }
    }
    
    public Block(int x, int y, int resolution) {
        this.resolution = resolution;
        this.organized = new BlockNode[x / this.resolution + 1][y / this.resolution + 1];
        
        for (int i = 0; i < this.organized.length; i++) {
            for (int j = 0; j < this.organized[i].length; j++) {
                this.organized[i][j] = new BlockNode(i, j);
                this.items.add(this.organized[i][j]);
            }
        }
    }
    
    @Override
    public void add(Coord coord, Element e) {
        if (this.elements.containsKey(e)) return;
        
        SpatialElement<Element> s = new BlockElement<Element>(coord, e);
        
        AccelerationStructureItem item = this.get(coord);
        item.getSpatialElements().add(s);
        
        this.elements.put(e, s);
        this.map.put(s, item);
    }
    
    @Override
    public Element getClosest(Coord coord) {
        List<AccelerationStructureItem> neighbours = this.getNeighbours(coord);
        
        Element r = null;
        double min = Double.MAX_VALUE;
        for (AccelerationStructureItem item : neighbours) {
            for (Object o : item.getSpatialElements()) {
                SpatialElement<Element> e = (SpatialElement<Element>)o;
                
                double distance = Math.pow(e.getCoord().x - coord.x, 2.0) +
                                  Math.pow(e.getCoord().y - coord.y, 2.0);
                if (min > distance) {
                    min = distance;
                    r = e.getElement();
                }
            }
        }
        if (min < 100) {
            return r;
        }
        return null;
    }
    
    @Override
    public AccelerationStructureItem get(Coord coord) {
        int x = coord.getX() / this.resolution;
        int y = coord.getY() / this.resolution;
        
        AccelerationStructureItem r = this.organized[x][y];
        return r;
    }
    
    @Override
    public List<AccelerationStructureItem> getNeighbours(Coord coord) {
        int x = coord.getX() / this.resolution;
        int y = coord.getY() / this.resolution;
        
        List<AccelerationStructureItem> t = new ArrayList<AccelerationStructureItem>();
        for (int dx = Math.max(x - 1, 0); dx < Math.min(x + 1, Block.this.organized.length); dx++) {
            for (int dy = Math.max(y - 1, 0); dy < Math.min(y + 1, Block.this.organized.length); dy++) {
                t.add(this.organized[dx][dy]);
            }
        }
        return t;
    }

    @Override
    public List<AccelerationStructureItem> getItems() {
        return this.items;
    }
    
    @Override
    public Set<Element> getElements() {
        return this.elements.keySet();
    }
    
    @Override
    public void remove(Element e) {
        if (this.elements.containsKey(e)) {
            SpatialElement s = this.elements.get(e);
            this.map.get(s).getSpatialElements().remove(s);
            
            this.map.remove(s);
            this.elements.remove(e);
        }
    }
    
    @Override
    public void clear() {
        for (AccelerationStructureItem i : this.items) {
            i.getSpatialElements().clear();
        }
        this.elements.clear();
        this.map.clear();
    }
    
    public static void main(String [] args) {
        int tests = 10000;
        int counter = 0;
        
        AccelerationStructure<Object> a = new Block<Object>(256, 256, 4);
        for (int i = 0; i < tests; i++) {
            Object t = new Object();
            int x = (int)(Math.random() * 256);
            int y = (int)(Math.random() * 256);
            a.add(new Coord(x, y), t);
            
            boolean found = false;
            AccelerationStructureItem s = a.get(new Coord(x, y));
            
            for (Object o : s.getSpatialElements()) {
                SpatialElement<Object> e = (SpatialElement<Object>)o;
                if (t.equals(e.getElement())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("not found");
            }
            else {
                counter++;
            }
        }
        System.out.println(counter + " / " + tests + " successfull...");
    }
}
