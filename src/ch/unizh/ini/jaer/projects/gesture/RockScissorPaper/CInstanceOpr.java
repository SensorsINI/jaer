/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;

import ch.unizh.ini.jaer.projects.gesture.GawiBawiBo2.*;
import weka.core.Instance;

/**
 *
 * @author юнео PC
 */
public class CInstanceOpr {
    Instance MergeInstance(Instance a, Instance b){
        int numAttr = a.numAttributes() + b.numAttributes() - 1;
        double[] newvals = new double[numAttr];
        int idx = 0;
        for(int i = 0; i < a.numAttributes()-1; i++){
            newvals[idx++] = a.value(i);
        }
        for(int i = 0; i < b.numAttributes(); i++){
            newvals[idx++] = b.value(i);
        }
                
        Instance new_instance = new Instance(1.0, newvals);
        return new_instance;
    }
}
