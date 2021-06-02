/*
 * Stereopsis.java
 *
 * Created on March 14, 2006, 3:10 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 14, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.stereopsis;

/**
 * General helper methods and static values for stereopsis.
 * @author tobi
 */
public class Stereopsis {
    
    /** Creates a new instance of Stereopsis */
    public Stereopsis() {
    }
    
    static final int bit=15;
    
    /** the bit mask used for constructing a right eye event from a raw event */
    public static  final int MASK_RIGHT_ADDR=(int)(1<<bit);
    
    
    /** @return true if raw address signals left eye event */
    public static final boolean isLeftRawAddress(int addr){
        return ((addr & MASK_RIGHT_ADDR))==0;
    }
    
    /** @return true if raw address signals right eye event */
    public static final  boolean isRightRawAddress(int addr){
        return ((addr & MASK_RIGHT_ADDR))==MASK_RIGHT_ADDR;
    }
    
    
    public static final byte MASK_RIGHT_TYPE=(byte)(1<<7);
    
    /** @return true of type of event signals left eye */
    public static final boolean isLeftType(byte type){
        return ((type&MASK_RIGHT_TYPE)==0);
    }
    
    /** return true if type signals right eye */
    public static final boolean isRightType(byte type){
        return ((type&MASK_RIGHT_TYPE)!=0);
    }
    
    public static final byte setRightType(byte type){
        return (byte)(type|MASK_RIGHT_TYPE);
    }
    
    public static final byte setLeftType(byte type){
        return (byte)(type&(~MASK_RIGHT_TYPE));
    }
    
    public static final byte stripEye(byte type){
        return (byte)(type&(~MASK_RIGHT_TYPE));
    }
}
