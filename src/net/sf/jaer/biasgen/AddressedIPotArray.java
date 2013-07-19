/*
 * IPotArray.java
 *
 * Created on September 20, 2005, 9:14 PM
 */

package net.sf.jaer.biasgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Describes a linear array of IPot's on a chip. This ArrayList is just an ordered list of IPot (hopefully) that maintains the order
 *of devices on a chip's shift register string. {@link #addPot} adds an IPot and registered this as an </code>Observer<code>. 
 *
 * @author tobi
 */
public class AddressedIPotArray extends PotArray implements Iterable<AddressedIPot>{
    
    /** Creates a new instance of IPotArray */
    public AddressedIPotArray(Biasgen biasgen) {
        super(biasgen);
    }

    /** Returns an iterator in shift register order, starting from far end of shift register, 
     * since bits are loaded so that first bit loaded ends up at this position.
     * 
     * @return the ShiftRegisterIterator
     */
    @Override
    public Iterator<AddressedIPot> iterator() {
        return new ShiftRegisterIterator();
    }
    
    /** Provides pots in order of loading to the shift register, used in hardware interfaces to load bits.
     */
    protected class ShiftRegisterIterator implements Iterator<AddressedIPot>{
        ArrayList<AddressedIPot> list=new ArrayList<AddressedIPot>();
        int index=0;
        ShiftRegisterIterator(){
            for(Pot p:pots){
                list.add((AddressedIPot)p);
            }
            Collections.sort(list, new ShiftRegisterComparator());
        }
        
        public boolean hasNext() {
            return index<list.size();
        }
        
        public AddressedIPot next() {
            return list.get(index++);
        }
        
        public void remove() {
            list.remove(index);
        }
        
    }
    
    /** orders pots in order of shift register so that bias at input end of SR is returned last, used in hardware interfaces to load bits.
     * The shift register index should be numbered so that lower numbers correspond to "closer to the input end" where the bits are loaded.
     */
    protected class ShiftRegisterComparator implements Comparator<AddressedIPot>{
        /** Compares two pots.
         * 
         * @param p1 first pot
         * @param p2 second pot
         * @return 1 if pot2 has larger shift register index than pot1, 0 if they have the same index, or -1 if pot1 has a larger index.
         */
        public int compare(AddressedIPot p1, AddressedIPot p2){
            if(p1.getAddress()<p2.getAddress()) return 1;
            if(p1.getAddress()==p2.getAddress()) return 0;
            return -1;
        }
        public boolean equals(AddressedIPot p1, AddressedIPot p2){
            if(p1.getAddress()==p2.getAddress()) return true; else return false;
        }
    }
    
    /** Returns an Iterator that iterates over the pots in the order of their shift register location, ordered from low to high,
     * starting with the far end of the shift register, furthest from the bit input. 
     * @return the iterator
     */
    public ShiftRegisterIterator getShiftRegisterIterator() {
        return new ShiftRegisterIterator();
    }
}
