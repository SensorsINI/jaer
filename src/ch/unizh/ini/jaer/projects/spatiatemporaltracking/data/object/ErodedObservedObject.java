/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.object;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.morphology.Binary;

/**
 *
 * @author matthias
 * 
 * This class represents an observed object. It tries to simplfy the object
 * and to improve the quality of the shape by removing noise.
 */
public class ErodedObservedObject extends AbstractObservedObject {

    /**
     * Creates a new ErodedObservedObject.
     * 
     * @param size The maximal size of the object.
     */
    public ErodedObservedObject(int size) {
        super(size);
    }
    
    @Override
    public void setShape(int[][] shape) {
        byte[][] temp = new byte[shape.length][shape.length];
        for (int x = 0; x < shape.length; x++) {
            for (int y = 0; y < shape.length; y++) {
                if (shape[x][y] > 0) {
                    temp[x][y] = 1;
                }
                else {
                    temp[x][y] = 0;
                }
            }
        }
        this.setShape(temp);
    }

    @Override
    public void setShape(byte[][] shape) {
        this.shape = Binary.dilation(shape, Binary.getInstance(Binary.StructeringElementTypes.neighbours8));
        this.shape = Binary.erosion(shape, Binary.getInstance(Binary.StructeringElementTypes.neighbours8));
        super.setShape(this.shape);
    }
}
