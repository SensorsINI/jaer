/*
 * IPotArray.java
 *
 * Created on September 20, 2005, 9:14 PM
 */

package ch.unizh.ini.caviar.biasgen;

import ch.unizh.ini.caviar.hardwareinterface.*;
import java.beans.*;
import java.util.*;

/**
 * Describes a linear array of IPot's on a chip. This ArrayList is just an ordered list of IPot (hopefully) that maintains the order
 *of devices on a chip's shift register string. {@link #addPot} adds an IPot and registered this as an </code>Observer<code>. 
 *
 * @author tobi
 */
public class IPotArray extends PotArray {
    
    /** Creates a new instance of IPotArray */
    public IPotArray(Biasgen biasgen) {
        super(biasgen);
    }
    
    // provides pots in order of shift register, used in hardware interfaces to load bits
    private class ShiftRegisterIterator implements Iterator<IPot>{
        ArrayList<IPot> list=new ArrayList<IPot>();
        int index=0;
        ShiftRegisterIterator(){
            for(Pot p:pots){
                list.add((IPot)p);
            }
            Collections.sort(list, new ShiftRegisterComparator());
        }
        
        public boolean hasNext() {
            return index<list.size();
        }
        
        public IPot next() {
            return list.get(index++);
        }
        
        public void remove() {
            list.remove(index);
        }
        
    }
    
    // orders pots in order of shift register so that bias at input end of SR is returned last, used in hardware interfaces to load bits
    private class ShiftRegisterComparator implements Comparator<IPot>{
        public int compare(IPot p1, IPot p2){
            if(p1.getShiftRegisterNumber()<p2.getShiftRegisterNumber()) return 1;
            if(p1.getShiftRegisterNumber()==p2.getShiftRegisterNumber()) return 0;
            return -1;
        }
        public boolean equals(IPot p1, IPot p2){
            if(p1.getShiftRegisterNumber()==p2.getShiftRegisterNumber()) return true; else return false;
        }
    }
    
    /** returns an Iterator that iterates over the pots in the order of their shift register location
     */
    public ShiftRegisterIterator getShiftRegisterIterator() {
        return new ShiftRegisterIterator();
    }
}
