/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jspikestack;

import java.awt.Dimension;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Control;
import javax.swing.JFrame;

/**
 *
 * @author oconnorp
 */
public class testinggrounds {
    
    public static void main(String[] args)
    {   
        
        System.out.println(Math.floor(-2.5));
        
        
        ArrayList arr=new ArrayList();
        
       
                
//        
//        MultiReaderQueue<Spike> q=new MultiReaderQueue();
//        
//        Reader r1=new Reader();
//        Reader r2=new Reader();
//        
//        q.add(new Spike(1,2,3));
//        q.add(new Spike(1,2,3));
//        
        
    }
    
    
    
    public static class Reader
    {
        
    }
    
    /**
 *
 * @author Peter
 */
public static class TestControl extends Controllable {
    
    private boolean aaa;
    
    private float bbb;

    /**
     * @return the aaa
     */
    public boolean isAaa() {
        return aaa;
    }

    /**
     * @param aaa the aaa to set
     */
    public void setAaa(boolean aaa) {
        this.aaa = aaa;
    }

        @Override
        public String getName() {
            return "Helllloo world";
        }

        /**
         * @return the bbb
         */
        public float getBbb() {
            return bbb;
        }

        /**
         * @param bbb the bbb to set
         */
        public void setBbb(float bbb) {
            this.bbb = bbb;
        }
    
    
    
    
}
    
    
    
    
    
    
    
    public static class Test extends Observable
    {
        private boolean aaa;

        /**
         * @return the info
         */
        public boolean isAaa() {
            return aaa;
        }

        /**
         * @param info the info to set
         */
        public void setAaa(boolean info) {
            this.aaa = info;
        }
        
        
        
        
        
    }
    
    
    
}
