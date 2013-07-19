/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.eventbasedfeatures;

import java.awt.Point;

/** This class contains classes and methods which are common to all detectors and descriptors.
 *
 * @author Varad
 */
public class FeatureMethod {        

    public class KeyPoint extends Point{     //since KeyPoint needs to be accessed by both the detector and descriptor        
        public int x, y;
//        public boolean isOnMap;              
        public boolean hasDescriptor;
        public DescriptorScheme desc;
        public boolean[] descriptorString;
        
        public KeyPoint(int x, int y){
            this.x = x;
            this.y = y;
//            this.isOnMap = false;
            this.hasDescriptor = false;
        }
    }
}
