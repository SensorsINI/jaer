package edu.mplab.rubios.node;
/** 
 * DoubleStringBuffer is a thread safe double buffer for writing/reading
 * strings by independent producer/consumer threads.  The class has
 * two buffers, each buffer is LIFO( last in first out).  Read and
 * write operations are locking. Thus when a thread is writing onto a
 * buffer no other thread can access the buffer.  This is why we have
 * a double buffer, so one thread can write into one of the buffers
 * while the other reads the contents of the other buffer.  readIndex
 * determines which of the two buffers is currently assigned to
 * reading operations. The other is assigned to write operations
 * (i.e., receive data).  swithReadBuffer is used to switch which
 * buffer is for reading and which one is for writing. It is the
 * responsibility of the consumer thread to switch buffers before
 * reading. This makes the buffer upon which things were most currently
 * written available for reading.
 *
 * @author Javier R. Movellan 
 * Date April 2006 
 * Copyright UCSD, MPLab, Javier R. Movellan.
 * License GPL
 *
 */
public class DoubleStringBuffer{
    
    SynchronizedStringBuffer[] data = new SynchronizedStringBuffer[2];
    int readIndex =1; // points to the buffer currently assigned for reading
    int size; // number of strings that can be saved per buffer. 

    public DoubleStringBuffer(int n){
	size =n;
	for(int i=0;i<2;i++){
	    data[i] = new SynchronizedStringBuffer(size);
	}
    }

    public DoubleStringBuffer(){
	this(10); // make  10 the default buffer size.
    }
    public  String read(){
	return data[readIndex].read();
    }

    public   void write(String s){
	// we write on buffer not currently used for reading
	data[1-readIndex].write(s); 
    }
    
    public int getReadBufferIndex(){
	return readIndex;
    }
    public void switchReadBuffer(){// switches buffer to read from 
	readIndex = 1- readIndex;
    }
    public boolean notEmpty(){ // whether there is still stuff to read
	return data[readIndex].notEmpty();
    }

    public int getNumRemainingMessages() {
	return data[readIndex].getNumRemainingMessages();
    }

    public boolean notFull(){ // whether there is still space to write
	return data[1-readIndex].notFull();
    }

    public static void main(String[] argv){
	// use main for testing the class
	DoubleStringBuffer buffer = new DoubleStringBuffer(4);
	
	// not a very interesting test, since read and write are on
	// same thread
	
	for( int i=0;i<4;i++){
	    buffer.write("Hello1 "+i);
	    System.out.println("Retrieved "+ buffer.read()+" from slot "+ buffer.getReadBufferIndex());	    
	}
	
	buffer.switchReadBuffer();
	for( int i=0;i<4;i++){
	    buffer.write("Hello2 "+i);
	    System.out.println("Retrieved "+ buffer.read()+" from slot "+ buffer.getReadBufferIndex());	    
	}
	buffer.switchReadBuffer();
	for( int i=0;i<4;i++){
	    buffer.write("Hello2 "+i);
	    System.out.println("Retrieved "+ buffer.read()+" from slot "+ buffer.getReadBufferIndex());	    
	}
	


    }
	
}


class SynchronizedStringBuffer{
    String[] sb=null;
    int size =0;
    int current =-1;



    // Note the read and write operators lock one of the buffers in
    // the double buffer, not the entire buffer. 
    public SynchronizedStringBuffer(int n){
	current=-1;
	size = n;
	sb = new String[size];
    }

    public synchronized boolean notEmpty(){
	if(current< 0) return false;
	else return true;
    }


    public synchronized boolean notFull(){
	if(current< size -1 ) return true;
	else return false;
    }

    public synchronized String read(){
	if(current >-1 ){ 
	    String s = sb[current];
	    current = current-1;
	    return s; 
	}
	else return null;
    }

    public synchronized int getNumRemainingMessages() {
	return current + 1;
    }

    public synchronized  boolean write (String st){
	boolean b= false;
	if(current < size-1){
	    current ++;
	    sb[current] = st;
	    return true;
	}
	else return false;
    }


}
