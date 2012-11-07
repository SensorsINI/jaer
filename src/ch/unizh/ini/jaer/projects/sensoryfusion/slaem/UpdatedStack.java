/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.lang.reflect.Array;
import java.util.Arrays;
import javax.media.opengl.GLAutoDrawable;

/**
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

    public void add(O element){
        stackPointer[pPointer] = sPointer;
        stack[stackPointer[pPointer]]=element;
        sPointer = increase(sPointer);
        pPointer = increase(pPointer);
    }

    public boolean addElement(Object obj, float oriTol, float distTol){
        boolean inserted = false;
        UpdatedStackElement element;
        if(obj.getClass()==UpdatedStackElement.class){
            element = (UpdatedStackElement) obj;
        }else{
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
            if(stackPointer[i]>=0){
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