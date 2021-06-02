package ch.unizh.ini.jaer.projects.elised.dynamicBuffer;

import java.lang.reflect.Array;


// A Ringbuffer.
public class Ringbuffer<T> {
    
    final private T[] buffer;       //Take away final if buffer[] should be made truly resizable.
    private T[] helperBuffer;
    public int inPointer = 0;
    public int maxSize;         // == buffer.length()
    private int size;           //Changed if buffer is resized. Buffer always begins at index 0.
    
    public Ringbuffer(Class<T> c, int size){
        buffer = (T[]) Array.newInstance(c, size);
        helperBuffer = (T[]) Array.newInstance(c, size);
        this.size = this.maxSize = size;
    }
    
    //add to front
    public void add(T t){
        buffer[inPointer] = t;
        inPointer++;
        if(inPointer == size) inPointer = 0;
    }
    
    public T get(int i){
//        if(inPointer >= size)
//            System.out.println("In RIngbuffer-get: InPointer is >= size!!!");
        return buffer[i%size];}     
    public T getLast() {
//        if(inPointer >= size)
//            System.out.println("In RIngbuffer-getLast: InPointer is >= size!!!");
        return buffer[inPointer];}                      
    //java-modulus: m % k returns m+n*k if m is negative and for the smallest integer n so that the result is positive.
    public T getIthLast(int i) {
//        if(inPointer >= size)
//            System.out.println("In RIngbuffer-getIthLast: InPointer is >= size!!!");
        return buffer[(inPointer-1+i+size)%size];}   //i=1: get last; i=0, i = size: get first
    public T getIth(int i){return buffer[(inPointer - i+size)%size];}        //i=1: get first
    public int getInPointer(){return inPointer;}
    public int getSize(){return size;}
    
    public void fill(T t){
        for(int i = 0; i<size; i++){ buffer[i] = t;}
    }
    
    public void advance(){
        inPointer++;
        if(inPointer == size) inPointer = 0;
    }
        public void advance(int n){
        inPointer = (inPointer+n)%size;
    }
    
    
    /*
    A resize operation that, if it makes the buffer smaller, shifts the newest objects into the first 
    newSize fields of the buffer (the newest at [inPointer-1]) and sets the inPointer to 0. The order remains the same. 
    If used to make the buffer smaller, lost llp's should be removed from their line segments.
    Will delete (make inaccessible) the oldest (old size) - (new size) elements. I hope.
    */      // Doesn't work yet!    
     public void resizeInPlace(int newSize){
        if(newSize > maxSize || newSize == size || newSize < 1) return;
        else if(newSize < size) {
            int lastElementPosition = inPointer - newSize;
            if(lastElementPosition > 0){    //shift remaining (=newest) elements, copy from left to right because indexDiff is > 0
                for(int i = 0; i<newSize; i++)  buffer[i] = buffer[i+lastElementPosition];
                System.out.println("pixels at position "+lastElementPosition+" to "+(lastElementPosition+newSize-1)+" moved to positions 0 to"+(newSize-1));
                inPointer = 0;
            }   
            // to improve: this can be done faster if inPointer is not set to 0 afterwards; only shift overhang
            else if (lastElementPosition < 0 && lastElementPosition%size > newSize-1) {   //shift remaining elements, first shift the elements within the 
                                                    //remaining buffer space to the rigth, starting behind the inPointer
                //lastElementPosition = lastElementPosition + size;
                for (int i = inPointer - 1; i >= 0; i--) {
                    buffer[newSize - (inPointer - 1) + i] = buffer[i];
                }
                int overhang = newSize - inPointer;    //how much is left at the right end of the buffer from the newSize newest elements
                for (int i = 0; i < overhang; i++) {    //second: shift the overhanging elements to the beginning
                    buffer[i] = buffer[size - 1 - overhang + i];
                }
                inPointer = 0;
            }
            //shift left all overhanging pixel that should remain
            else if(lastElementPosition < 0 && lastElementPosition%size < newSize){
                lastElementPosition = lastElementPosition + size;
                for(int i = lastElementPosition; i<size; i++){
                    buffer[inPointer -lastElementPosition+i] = buffer[i];
                }
            }
            
        }
        size = newSize;
    }
    
    public void resize(int newSize){
        if(newSize > maxSize || newSize == size || newSize < 1) return;
        else if(newSize < size) {
            for(int i = 0; i<size; i++)
                helperBuffer[i] = buffer[i];
            //System.arraycopy(buffer, 0, helperBuffer, 0, size);
            for(int i = 0; i<newSize; i++)
                buffer[i] = helperBuffer[(inPointer - i - 1 + size)%size];
            inPointer = 0;
        }   //if newSize > size:
          size = newSize;
    }
    
    
    
}
