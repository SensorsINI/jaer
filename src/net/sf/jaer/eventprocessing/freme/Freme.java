/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.freme;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A freme is the event driven equivalent to a frame. It is a 2D set of values
 * (or objects) that get updated on the arrival of an event. 
 *
 * @author Christian
 */
public class Freme<O> implements Iterable{ 
    private Object[] elements;
    private int sizeX, sizeY, size;
    
    public Freme(){
        this(0, 0);
    }
    
    public Freme(int sX, int sY){
        if (sX < 0 || sY <0)
            throw new IllegalArgumentException("Illegal sizes: size x = "+sX+", sY = ");
        sizeX = sX;
        sizeY = sY;
        size = sizeX*sizeY;
        this.elements = new Object[size];
    }
    
    /**
     * Replaces the freme element at the specified position in this list 
     * with the specified element
     *
     * @param idx index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public O set(int idx, O element){
        rangeCheck(idx);
        O oldValue = (O) elements[idx];
	elements[idx] = element;
	return oldValue;
    }
    
    /**
     * Replaces the freme element at the specified position in this list 
     * with the specified element
     *
     * @param x x coordinate of the element to replace
     * @param y x coordinate of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public O set(int x, int y, O element){
        int idx = getIndex(x,y);
        rangeCheck(idx);
        O oldValue = (O) elements[idx];
	elements[idx] = element;
	return oldValue;
    }
    
    /**
     * Returns the element at the specified position in the freme .
     *
     * @param  index index of the element to return
     * @return the element at the specified position in the freme
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public O get(int index) {
	rangeCheck(index);

	return (O) elements[index];
    }
    
    /**
     * Returns the element at the specified position in the freme .
     *
     * @param  x x coordinate of the element to return
     * @param  y y coordinate of the element to return
     * @return the element at the specified position in the freme
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public O get(int x, int y) {
	int index = getIndex(x, y);
        rangeCheck(index);

	return (O) elements[index];
    }
    
    /** Subclasses implement this method to ensure that the freme has the right 
     * size and is not empty.
     */
    public void fill(O element){
        Arrays.fill(elements, element);
    }
    
    /** Calculates the freme index of a coordinate
     @param x x coordinate 
     @param y y coordinate
     @return the index of the according coordinate
     */
    public int getIndex(int x, int y){
        return (y*sizeX+x);
    }
    
    /**
     * Checks if the given index is in range.  If not, throws an appropriate
     * runtime exception.  This method does *not* check if the index is
     * negative: It is always used immediately prior to an array access,
     * which throws an ArrayIndexOutOfBoundsException if index is negative.
     */
    public void rangeCheck(int index) {
	if (index >= size)
	    throw new IndexOutOfBoundsException(
		"Index: "+index+", Size: "+size);
    }
    
    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
	return size;
    }
    
    /**
     * Returns <tt>true</tt> if this list contains no elements.
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    public boolean isEmpty() {
	return size == 0;
    }
    
    public Object[] getFreme(){
        return Arrays.copyOf(elements, size);
    }
    
    public <T> T[] getFreme(T[] a) {
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            return (T[]) Arrays.copyOf(elements, size, a.getClass());
	System.arraycopy(elements, 0, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }
    
    // Iterators
    protected transient int modCount = 0;
    /**
     * Returns an iterator over the elements in this freme in proper sequence.
     *
     * @return an iterator over the elements in this freme in proper sequence
     *
     * @see #modCount
     */
    public Iterator<O> iterator() {
	return new Itr();
    }

    private class Itr implements Iterator<O> {
	/**
	 * Index of element to be returned by subsequent call to next.
	 */
	int cursor = 0;

	/**
	 * Index of element returned by most recent call to next or
	 * previous.  Reset to -1 if this element is deleted by a call
	 * to remove.
	 */
	int lastRet = -1;

	/**
	 * The modCount value that the iterator believes that the backing
	 * List should have.  If this expectation is violated, the iterator
	 * has detected concurrent modification.
	 */
	int expectedModCount = modCount;

	public boolean hasNext() {
            return cursor != size();
	}

	public O next() {
            checkForComodification();
	    try {
		O next = get(cursor);
		lastRet = cursor++;
		return next;
	    } catch (IndexOutOfBoundsException e) {
		checkForComodification();
		throw new NoSuchElementException();
	    }
	}

	public void remove() {
	    throw new UnsupportedOperationException();
	}

	final void checkForComodification() {
	    if (modCount != expectedModCount)
		throw new ConcurrentModificationException();
	}
    }
}
