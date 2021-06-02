package ch.unizh.ini.jaer.projects.elised.dynamicBuffer;

import static java.lang.Math.floor;
import java.lang.reflect.Array;
import java.util.Arrays;


/* A Ringbuffer with simple resizing. An inPointer points to the buffer index right
    after the element added most recently ('after' = one index higher, mod maxSize).
    Resize works by simply changing the parameter 'size'.

    The  buffer also remembers how many elements it really contains; that is, how
    many haven't been taken or are default elements (from ResizeableRingbuffer.fill 
    or from the buffers' initialization).

    It's also possible to automatically run a function implementing 'removalFunction'
    on each element that 'falls out' of the buffer at resize. This only happens 
    with elements counted in elementCount.

    Because  java-modulus works like this:
        m % k returns m+n*k if m is negative and for the smallest integer n 
        so that the result is positive
    , there are a lot of places where maxSize is added before modulo is taken.
*/

/**
 *
 * @author Susi
 */
public class ResizeableRingbuffer<T> {
    
    
    private Class elementClass = null;
    private T[] buffer;
    private int inPointer = 0;
    private int elementCount = 0;
    private int maxSize;         // == buffer.length()
    private int size;           //Changed if buffer is resized. Buffer always begins at index 0.
    private boolean full, empty;
        
        // argument size: maximum capacity and size at initialization
    public ResizeableRingbuffer(Class c, int maxSize, int size){
        if (size < 1)
            size = 1;
        elementClass = c;
        buffer = (T[]) Array.newInstance(elementClass, maxSize);
        //helperBuffer = (T[]) Array.newInstance(c, size);
        this.maxSize = maxSize;
        this.size = size;
        full = false;
        empty = true;
    }
    
        //add to front
    public void add(T t){
        buffer[inPointer] = t;
        inPointer++;
        if(inPointer == maxSize) inPointer = 0;
        if(!isFull()){
            elementCount++;
            if (elementCount==size)
                full = true;
            else if (elementCount==1)
                empty = false;
        }
    }    
    
    public T get(int i){
        return buffer[i%maxSize];}    
    public T getFirst(){  //return newest, the latest pixel added
        return buffer[(inPointer-1+maxSize)%maxSize];}
    public T getLast() {   //returns the oldest element (the one at inPointer-size)
        return buffer[(inPointer-elementCount+maxSize)%maxSize];}
    public T getIth(int i){
        return buffer[(inPointer - i+maxSize)%maxSize];}        //i=1: get first
    public T getIthLast(int i) {
        return buffer[(inPointer-elementCount-1+i+2*maxSize)%maxSize];}   //i=1: get last; i=0, i = size: get first
    
    public T take(){    //take away the oldest element - it's 'gone' from the buffer afterwards. Don't call if buffer is empty!
        T t = getLast();
        elementCount--;
        if(isFull())
            full=false;
        if(elementCount==0)
            empty=true;
        return t;
    }
    
    public int getInPointer(){return inPointer;}
    public int getSize(){return size;}
    public int getElementCount() {return elementCount;}
    
    public void fill(T t){
        for(int i = 0; i<maxSize; i++){ buffer[i] = t;}
    }
    
    public void advance(){
        inPointer++;
        if(inPointer == size) inPointer = 0;
    }
        public void advance(int n){
        inPointer = (inPointer+n)%maxSize;
    }
    
    /*Leaves all elements where they are, but by changing 'size', the oldest element
      returned by getLast() etc. after a resize is a different one than before. 
      If the elements should be deleted via some function, this has to be done 
      manually before calling resize. */
public void resize(int newSize){
    if(!(newSize > maxSize || newSize == size || newSize < 1)) {
        if(size < newSize){
            if( isFull()) full=false;
        }
        else{   //buffer size is decreased
            if(elementCount == newSize)
                full=true;
            else if(elementCount > newSize){
                elementCount = newSize;
                full = true;
            }
            // if(elementCount > newSize), do nothing
        }
        size = newSize;
    }
}
    
    /*runs remover.remove(e) on all newly invalidated elements e if the buffer size
     is decreased */
public void resize(int newSize, RemovalFunction remover){
    if(!(newSize > maxSize || newSize == size || newSize < 1)) {
        if(size < newSize){
            if( isFull()) full=false;
        }
        else{   //buffer size is decreased
            if(elementCount == newSize)
                full=true;
            else if(elementCount > newSize){
                removeOldestFromBuffer(elementCount-newSize, remover);
                elementCount = newSize;
                full = true;
            }
            // if(elementCount > newSize), do nothing
        }
        int a = 5;
        size = newSize;
    }
}

public void removeOldestFromBuffer(int nr, RemovalFunction remover){
    for (int i = 1; i <= nr; i++) {
            T t = getIthLast(i);
            remover.remove(t);
    }
}
    
public void clear() {
    buffer = (T[]) Array.newInstance(elementClass, maxSize);
    full = false;
    empty = true;
    elementCount = 0;
}

    /**
     * @return the full
     */
    public boolean isFull() {
        return full;
    }

    /**
     * @return the empty
     */
    public boolean isEmpty() {
        return empty;
    }
    
    
    
}
