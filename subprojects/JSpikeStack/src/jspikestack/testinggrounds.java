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
import java.util.Observable;
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
//        try {
            //        float[] arr={1,4,5,3};
                    
            //        Float[] arr=new Float[4];
            //        arr[0]=1f;
            //        arr[1]=2f;
            //        arr[2]=3f;
            //        arr[3]=4f;
            //        
            //        Float[] yarr=new Float[4];
            //                
            //        yarr[0]=arr[3];
            //        yarr[1]=arr[2];
            //        yarr[2]=arr[1];
            //        yarr[3]=arr[0];
            //        
            //        arr[2]=6f;
            //        
            //        float[] aa={1,2,3,4};
            //        float[] bb={5, 3, 4, 2};
            //
            //        float[] cc;
            //        cc=aa+bb;
                    
                    JFrame frm=new JFrame();
                    
                    
                    TestControl t=new TestControl();
                    
                    GeneralController g=new GeneralController();
                    
                    g.addController(t);
                    
                    TestControl t2=new TestControl();
                    g.addController(t2);
                    
                    g.setControlsVisible(true);
                    
                    frm.add(g);
                    
//                    frm.setPreferredSize(new Dimension(300,300));
                    frm.pack();
                    frm.setVisible(true);
                    
                    
                    
//                    Method m[]=t.getClass().getDeclaredMethods();
                    
//                    m[0].invoke(t, 1);
                    
            //        BeanInfo inf ;
            //        try {
            //            inf = Introspector.getBeanInfo(t.getClass(),Introspector.USE_ALL_BEANINFO);
            //        } catch (IntrospectionException ex) {
            //        }
            //        }
//        } catch (IllegalAccessException ex) {
//            Logger.getLogger(testinggrounds.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (IllegalArgumentException ex) {
//            Logger.getLogger(testinggrounds.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (InvocationTargetException ex) {
//            Logger.getLogger(testinggrounds.class.getName()).log(Level.SEVERE, null, ex);
//        }
        
        
    }
    
    
    
    /**
 *
 * @author Peter
 */
public static class TestControl extends NetController {
    
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
