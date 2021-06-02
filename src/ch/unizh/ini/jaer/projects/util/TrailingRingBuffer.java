/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import static net.sf.jaer.eventprocessing.EventFilter.log;

/**
 *
 * @author Christian
 */
public class TrailingRingBuffer<O> implements Iterable, Iterator {

    private Class elementClass = null;
    public O[] buffer;
    public int capacity, size;
    public int writeIdx, readIdx, itrIdx;
    public boolean full, empty;

    public TrailingRingBuffer(Class elementClass, int size) {
        this.capacity = size;
        this.elementClass = elementClass;
        buffer = (O[]) Array.newInstance(elementClass, capacity);
        writeIdx = 0;
        readIdx = 0;
        itrIdx = 0;
        full = false;
        empty = true;
    }

    public void clear() {
        buffer = (O[]) Array.newInstance(elementClass, capacity);
        writeIdx = 0;
        readIdx = 0;
        itrIdx = 0;
        full = false;
        empty = true;
    }

    public void add(O element) {
        buffer[writeIdx] = element;
        writeIdx = increase(writeIdx);
        size++;
        if(size>=capacity){
            size=capacity;
            full = true;
        }
        empty=false;
    }

    public Object get() {
        Object result = buffer[readIdx];
        readIdx = increase(readIdx);
        size--;
        full = false;
        if (size == 0) {
            empty = true;
        }
        return result;
    }

    private int increase(int i) {
        i++;
        if (i >= capacity) {
            i = 0;
        }
        return i;
    }

    private int decrease(int i) {
        i--;
        if (i < 0) {
            i = capacity - 1;
        }
        return i;
    }

    @Override
    public boolean hasNext() {
        while (buffer[itrIdx] == null && itrIdx != decrease(readIdx)) {
            itrIdx = decrease(itrIdx);
        }
        return (writeIdx != readIdx && itrIdx != decrease(readIdx));
    }

    @Override
    public Object next() {
        Object result = buffer[itrIdx];
        itrIdx = decrease(itrIdx);
        return result;
    }

    @Override
    public void remove() {
        get();
    }

    @Override
    public Iterator iterator() {
        itrIdx = readIdx;
        return this;
    }
    
    public TrailingRingBuffer<Object> resizeCopy(int newCapacity){
        TrailingRingBuffer<Object> newBuffer = new TrailingRingBuffer<Object>(elementClass,newCapacity);
        while(!isEmpty()){
            newBuffer.add(get());
        }
        return newBuffer;
    }
    
    //more efficien but not yet working
//    public void resize(int newCapacity){
//        if(newCapacity > capacity){
//            O[] newBuffer;
//            if(writeIdx>readIdx){
//                newBuffer = (O[]) Arrays.copyOfRange(buffer, readIdx, writeIdx);
//                newBuffer = (O[]) Arrays.copyOfRange(newBuffer, 0, newCapacity);
//            }else{
//                newBuffer = (O[]) Arrays.copyOfRange(buffer, readIdx, newCapacity+readIdx);
//                System.arraycopy(buffer, 0, newBuffer, capacity-readIdx, writeIdx);
//            }
//            writeIdx=size-1;
//            readIdx=0;
//            buffer = (O[]) newBuffer;
//        }else if(newCapacity < capacity){
//            O[] newBuffer;
//            if(writeIdx>readIdx){
//                if(size>newCapacity){
//                    newBuffer = (O[]) Arrays.copyOfRange(buffer, writeIdx-newCapacity, writeIdx);
//                    readIdx=0;
//                    writeIdx=newCapacity-1;
//                }else{
//                    newBuffer = (O[]) Arrays.copyOfRange(buffer, writeIdx-readIdx, writeIdx);
//                    newBuffer = (O[]) Arrays.copyOfRange(newBuffer, 0, newCapacity);
//                    writeIdx=size-1;
//                    readIdx=0;
//                }
//            }else{
//                if(size>newCapacity){
//                    if(writeIdx>newCapacity){
//                        newBuffer = (O[]) Arrays.copyOfRange(buffer, writeIdx-newCapacity, writeIdx);
//                        writeIdx=newCapacity-1;
//                        readIdx=0;
//                    }else{
//                        newBuffer = (O[]) Arrays.copyOfRange(buffer, readIdx, newCapacity+readIdx);
//                        System.arraycopy(buffer, 0, newBuffer, capacity-readIdx, writeIdx);
//                    }
//                }
//                newBuffer = (O[]) Arrays.copyOfRange(buffer, readIdx, newCapacity+readIdx);
//                System.arraycopy(buffer, 0, newBuffer, capacity-readIdx, writeIdx);
//            }
//            if(size>capacity)size=capacity;
//            buffer = (O[]) newBuffer;
//        }
//        capacity = newCapacity;
//        if(capacity != buffer.length){
//            log.warning("capacity: "+capacity+" does not match buffer length:"+buffer.length);
//        }
//    }

    public boolean isFull() {
        return full;
    }

    public boolean isEmpty() {
        return empty;
    }
    
    public int getSize(){
        return size;
    }
}
