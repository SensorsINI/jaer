/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.util;

import java.lang.reflect.Array;

/**
 *
 * @author Christian
 */
public class RingBuffer<O>{
    
    private Class elementClass=null;
    public O[] buffer;
    public int size;
    public int pointer;
    
    public RingBuffer(Class elementClass, int size){
        this.size = size;
        this.elementClass = elementClass;
        reset();
    }
    
    public void reset(){
        buffer=(O[]) Array.newInstance(elementClass, size);
        pointer = 0;
    }
    
    public O get(){
        return buffer[pointer];
    }
    
    public void add(O element){
        buffer[pointer]=element;
        pointer = increase(pointer);
    }
    
    public O[] getArray(){
        O[] output = buffer.clone();
        return output;
    }
    
    private int increase(int i){
        i++;
        if(i>=size)i=0;
        return i;
    }

}
