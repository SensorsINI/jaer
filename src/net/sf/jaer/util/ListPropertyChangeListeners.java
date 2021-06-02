/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;

/**
 * Utility static method to debug listeners of PropertyChangeSupport
 * @author Tobi
 */
public class ListPropertyChangeListeners {
    Logger log=Logger.getLogger("Debug");
    public static String listListeners(PropertyChangeSupport support){
        if(support==null) return "null support";
        StringBuilder sb=new StringBuilder();
        for(PropertyChangeListener l:support.getPropertyChangeListeners()){
            sb.append(l==null?"null":l.toString()+"\n");
        }
        return sb.toString();
    }
    
}
