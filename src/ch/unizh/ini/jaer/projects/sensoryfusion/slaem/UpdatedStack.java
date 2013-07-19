/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.lang.reflect.Array;
import java.util.Arrays;

import javax.media.opengl.GLAutoDrawable;

/**
 * Special stack that has always the most recent entry on top. If an element is
 * added following routine gets applied: First an iteration over all elements 
 * happens to check whether the element is already contained in another stack
 * element. If not, the element gets added on top of the stack an the oldest 
 * gets dropped.
 * 
 * @author Christian
 */
public class UpdatedStack<O extends UpdatedStackElement> {
    public int size;
    public O[] stack;
    private Class elementClass=null;
    public int[] stackPointer;
    public int pPointer, sPointer;

    public UpdatedStack(Class<? extends UpdatedStackElement> elementClass, int size){
        this.size = size;
        this.elementClass = elementClass;
        reset();
    }

    public void reset(){
        stack=(O[]) Array.newInstance(elementClass, size);
        stackPointer = new int[size];
        Arrays.fill(stackPointer, -1);
        pPointer = 0;
        sPointer = 0;
        stackPointer[pPointer] = sPointer;
    }

    /*
     * Method to add an element on top of the stack without checking
     */
    
    public void add(O element){
        stackPointer[pPointer] = sPointer;
        stack[stackPointer[pPointer]]=element;
        sPointer = increase(sPointer);
        pPointer = increase(pPointer);
    }

    public boolean containsElement(Object obj, float oriTol, float distTol){
        boolean inserted = false;
        UpdatedStackElement element;
        if(obj instanceof UpdatedStackElement){
            element = (UpdatedStackElement) obj;
        }else{
            System.out.println("wrong object type for updatedStack - needs to implement UpdatedStackElement");
            return inserted;
        }
        int i = decrease(pPointer);
        while(i!=pPointer){
            if(stackPointer[i]>=0){
                if(stack[stackPointer[i]].contains(element, oriTol, distTol)){
                    inserted = true;
                    stackPointer[pPointer]=stackPointer[i];
                    stackPointer[i]=-1;
                    pPointer = increase(pPointer);
                    break;
                }
            }
            i = decrease(i);
        }
        return inserted;
    }

    public O getLastElement(){
        int lastP = decrease(pPointer);
        return stack[stackPointer[lastP]];
    }

    public void draw(GLAutoDrawable drawable){
        for(int i = 0; i<size; i++){
            if(stackPointer[i]>=0 && stack[stackPointer[i]] != null){
                stack[stackPointer[i]].draw(drawable);
            }
        }
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
}