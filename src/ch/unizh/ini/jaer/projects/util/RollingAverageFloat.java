/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.util;

/**
 *
 * @author Christian
 */
public class RollingAverageFloat{
    int size;
    float average, sum;
    RingBuffer<Float> buffer;
    
    public RollingAverageFloat(int capacity){
        buffer = new RingBuffer<Float>(Float.class, capacity);
        size=0;
        average = 0.0f;
        sum = 0.0f;
    }
    
    public float addValue(float value){
        if(size < buffer.size){
            sum += value;
            size++;
            buffer.add(value);
        }else{
            sum -= buffer.get();
            sum += value;
            buffer.add(value);
        }
        average = sum/(float)size;
        return average;
    }
    
    public float getAverage(){
        return average;
    }
}
