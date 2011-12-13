/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.morphology;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 *
 * @author matthias
 * 
 * The class provides morphological operations on binary images.
 */
public class Binary {
    
    /**
     * Erodes the given binary image using the given structering element.
     * 
     * @param image The binary image to erode.
     * @param element The structuering element used to erode the image.
     * 
     * @return The eroded image.
     */
    public static byte[][] erosion(byte[][] image, StructeringElement element) {
        byte[][] result = new byte[image.length][image[0].length];
        for (int x = 0; x < result.length; x++) Arrays.fill(result[x], (byte)0);
        
        for (int x = element.x; x < image.length - (element.x); x++) {
            for (int y = element.y; y < image[x].length - (element.y); y++) {
                boolean match = true;
                
                for (int dx = 0; match && dx < element.mask.length; dx++) {
                    for (int dy = 0; match && dy < element.mask[dx].length; dy++) {
                        if (element.mask[dx][dy] == 1 && image[x - element.x + dx][y - element.y + dy] == 0) {
                            match = false;
                        }
                    }
                }
                if (match) {
                    result[x][y] = 1;
                }
            }
        }
        return result;
    }
    
    /**
     * Dilates the given binary image using the given structering element.
     * 
     * @param image The binary image to erode.
     * @param element The structuering element used to dilate the image.
     * 
     * @return The dilated image.
     */
    public static byte[][] dilation(byte[][] image, StructeringElement element) {
        byte[][] result = new byte[image.length][image[0].length];
        for (int x = 0; x < result.length; x++) Arrays.fill(result[x], (byte)0);
        
        for (int x = element.x; x < image.length - (element.x); x++) {
            for (int y = element.y; y < image[x].length - (element.y); y++) {
                boolean match = false;
                
                for (int dx = 0; !match && dx < element.mask.length; dx++) {
                    for (int dy = 0; !match && dy < element.mask[dx].length; dy++) {
                        if (element.mask[dx][dy] == 1 && image[x - element.x + dx][y - element.y + dy] == 1) {
                            match = true;
                        }
                    }
                }
                if (match) {
                    result[x][y] = 1;
                }
            }
        }
        return result;
    }
    
    /**
     * Represents a structering element used for the morphological operations.
     */
    public static class StructeringElement {
        /**
         * The binary mask of the element.
         */
        public byte[][] mask;
        
        /** The x position of the center of the element. */
        public int x;
        
        /** The y position of the center of the element. */
        public int y;
        
        /**
         * Creates a new StructeringElement.
         * 
         * @param mask The binary mask of the element.
         * @param x The x position of the center of the element.
         * @param y The y position of the center of the element.
         */
        public StructeringElement(byte[][] mask, int x, int y) {
            this.mask = mask;
            this.x = x;
            this.y = y;
        }
    }
    
    /** Stores the allready generated structering element. */
    private static Map<StructeringElementTypes, StructeringElement> elements = new EnumMap<StructeringElementTypes, StructeringElement>(StructeringElementTypes.class);
    
    /**
     * Generates a predefined structering element.
     * 
     * @param type The type of the structering element.
     */
    public static StructeringElement getInstance(StructeringElementTypes type) {
        if (!elements.containsKey(type)) {
            switch(type) {
                case neighbours4:
                    byte[][] neighbours4 = {{0,1,0}, {1,1,1}, {0,1,0}};
                    elements.put(type, new StructeringElement(neighbours4, 1, 1));
                    break;
                case neighbours8:
                    byte[][] neighbours8 = {{1,1,1}, {1,1,1}, {1,1,1}};
                    elements.put(type, new StructeringElement(neighbours8, 1, 1));
                    break;
            }
        }
        return elements.get(type);
    }
    
    /**
     * Defines the default structering elements available.
     */
    public enum StructeringElementTypes {
        neighbours4,
        neighbours8
    }
}
