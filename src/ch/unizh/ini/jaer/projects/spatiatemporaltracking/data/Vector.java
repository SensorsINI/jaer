/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data;

import java.util.Arrays;

/**
 *
 * @author matthias
 * 
 * Stores the position of a d-dimensional vector.
 */
public class Vector {
    /** Stores the coefficent of each dimension */
    private float [] values;
    
    /**
     * Creates a new instance of a Vector.
     * 
     * @param dimension The dimension of the vector.
     */
    public Vector(int dimension) {
        this.values = new float[dimension];
        
        Arrays.fill(this.values, 0);
    }
    
    /*
     * Gets the value corresponding to the given dimension.
     * 
     * @param index The dimension of the value.
     * @return The value corresponding to the given dimension.
     */
    public float get(int index) {
        return this.values[index];
    }
    
    /*
     * Sets the value corresponding to the given dimension.
     * 
     * @param index The dimension of the value.
     * @param value The value to set.
     */
    public void set(int index, float value) {
        this.values[index] = value;
    }
    
    /*
     * Gets the number of dimensions of the vector.
     * 
     * @return The number of dimensions.
     */
    public int getDimension() {
        return values.length;
    }
    
    /*
     * Resets the vector.
     */
    public void reset() {
        Arrays.fill(this.values, 0);
    }
    
    /**
     * Gets a copy of the current position.
     * 
     * @return A copy of the current position.
     */
    public Vector copy() {
        Vector v = new Vector(this.getDimension());
        for (int i = 0; i < this.getDimension(); i++) {
            v.set(i, this.get(i));
        }
        return v;
    }
    
    /**
     * Computes the squared norm of the vector.
     * 
     * @return The squared norm of the vector.
     */
    public double squaredNorm() {
        double s = 0;
        for (int i = 0; i < this.getDimension(); i++) {
            s += Math.pow(this.get(i), 2);
        }
        return s;
    }
    
    /**
     * Computes the norm of the vector.
     * 
     * @return The norm of the vector.
     */
    public double norm() {
        return Math.sqrt(this.squaredNorm());
    }
    
    /**
     * Computes the dot product between this and the given vector. The two
     * vectors are normalized before.
     * 
     * @param v The other vector for the computation of the dot product.
     * @return The dot product between this and the given vector.
     */
    public float normalizedDot(Vector v) {
        double s1 = this.norm();
        double s2 = v.norm();
        
        if (s1 == 0 || s2 == 0) return 0;
        
        float d = 0;
        for (int i = 0; i < this.getDimension(); i++) {
            d += (this.get(i) / s1) * (v.get(i) / s2);
        }
        return d;
    }
    
    /**
     * Multiplies the vector with the given scalar.
     * 
     * @param a The scalar to multiply with.
     * @return This vector.
     */
    public Vector multiply(float a) {
        for (int i = 0; i < this.getDimension(); i++) {
            this.set(i, a * this.get(i));
        }
        return this;
    }
    
    /**
     * Adds the given vector to this vector.
     * 
     * @param v The vector to add.
     * @return This vector.
     */
    public Vector add(Vector v) {
        for (int i = 0; i < this.getDimension(); i++) {
            this.set(i, v.get(i) + this.get(i));
        }
        return this;
    }
    
    /**
     * Substract the given vector from this vector.
     * 
     * @param v The vector to substract.
     * @return This vector.
     */
    public Vector substract(Vector v) {
        for (int i = 0; i < this.getDimension(); i++) {
            this.set(i, v.get(i) - this.get(i));
        }
        return this;
    }
    
    public Vector redimension(int dimension) {
        this.values = Arrays.copyOf(this.values, dimension);
        
        return this;
    }
    
    /**
     * Gets the default vector in the specified d-dimensional space.
     * 
     * @param dimension The number of dimensions of the vector.
     * @return The default vector in the d-dimensional space.
     */
    public static Vector getDefault(int dimension) {
        return new Vector(dimension);
    }
    
    /**
     * Gets the d-diemsnional vector aligned to x-axis.
     * 
     * @param dimension The number of dimensions of the vector.
     * @return The vector alinged to x-axis.
     */
    public static Vector getXAxis(int dimension) {
        Vector v = Vector.getDefault(dimension);
        v.set(0, 1);
        return v;
    }
    
    @Override
    public String toString() {
        String s = "";
        
        for (int i = 0; i < this.getDimension() - 1; i++) {
            s += this.get(i) + " / ";
        }
        s += this.get(this.getDimension() - 1);
        
        return s;
    }
}
