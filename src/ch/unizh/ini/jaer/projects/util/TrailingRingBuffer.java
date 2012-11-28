/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.util;

import java.lang.reflect.Array;
import java.util.Iterator;

/**
 *
 * @author Christian
 */
public class TrailingRingBuffer<O> implements Iterable, Iterator{
    
    private Class elementClass=null;
    public O[] buffer;
    public int size;
    public int lead,trail,itIdx;
    
    public TrailingRingBuffer(Class elementClass, int size){
        this.size = size;
        this.elementClass = elementClass;
        reset();
    }
    
    public void reset(){
        buffer=(O[]) Array.newInstance(elementClass, size);
        lead = 0;
        trail = 0;
    }
    
    public void add(O element){
        //System.out.println("add: "+lead);
        buffer[lead]=element;
        if(decrease(trail)==lead)trail=increase(trail);
        lead = increase(lead);
    }
    
    public int increase(int i){
        i++;
        if(i>=size)i=0;
        return i;
    }

    public int decrease(int i){
        i--;
        if(i<0)i=size-1;
        return i;
    }
    
    public void resetItr(){
        itIdx = decrease(lead);
        //System.out.println("resetItr: "+itIdx);
    }
    
    @Override
    public boolean hasNext() {
        while(buffer[itIdx] == null && itIdx != decrease(trail)){
            itIdx = decrease(itIdx);
        }
        return (lead != trail && itIdx != decrease(trail));
    }

    @Override
    public Object next() {
        //System.out.println("itIdx: "+itIdx);
        Object result = buffer[itIdx];
        itIdx = decrease(itIdx);
        return result;
    }

    @Override
    public void remove() {
        buffer[increase(itIdx)] = null;
    }

    @Override
    public Iterator iterator() {
        resetItr();
        
        return this;
    }

}
