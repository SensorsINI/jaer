/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data;

/**
 *
 * @author matthias
 * 
 * The class Matrix represents a matrix to do some basic linear algebra
 * calculatios.
 */
public class Matrix {
    /** Stores the coefficent of each dimension */
    private float [][] values;
    
    /**
     * The number of rows.
     */
    private int m;
    
    /**
     * The number of columns.
     */
    private int n;
    
    /**
     * Creates a new matrix.
     * 
     * @param m The number of rows.
     * @param n The number of columns.
     */
    public Matrix(int m, int n) {
        this.m = m;
        this.n = n;
        
        this.values = new float[m][n];
    }
    
    /**
     * Sets a value of the matrix.
     * 
     * @param i Specifies the row.
     * @param j Specifies the column.
     * @param value The value to set.
     */
    public void set(int i, int j, float value) {
        this.values[i][j] = value;
    }
    
    /**
     * Gets a value of the matrix.
     * 
     * @param i Specifies the row.
     * @param j Specifies the column.
     * @return The value of the matrix at i,j.
     */
    public float get(int i, int j) {
        return this.values[i][j];
    }
    
    /**
     * Multiplies the given vector with the matrix.
     * 
     * @param v The vector for the multiplication.
     * @return The multiplied vector.
     */
    public Vector multiply(Vector v) {
        Vector r = Vector.getDefault(v.getDimension());
        
        for (int i = 0; i < this.m; i++) {
            float s = 0;
            for (int j = 0; j < this.n; j++) {
                s += this.get(i, j) * v.get(j);
            }
            r.set(i, s);
        }
        return r;
    }
    
    /**
     * Gets a 2-dimensional rotation matrix. The rotation is around the
     * origin.
     * 
     * @param angle The angle of the rotation.
     * @return  The 2-dimensional rotation matrix.
     */
    public static Matrix getRotation2D(float angle) {
        Matrix m = new Matrix(2, 2);
        
        m.set(0, 0, (float) Math.cos(angle));
        m.set(0, 1, (float)-Math.sin(angle));
        m.set(1, 0, (float) Math.sin(angle));
        m.set(1, 1, (float) Math.cos(angle));
        
        return m;
    }
    
    @Override
    public String toString() {
        String s = "";
        
        for (int i = 0; i < this.m; i++) {
            for (int j = 0; j < this.n - 1; j++) {
                s += (this.get(i, j) + "  ");
            }
            s += this.get(i, this.n - 1) + "  \n";
        }
        
        return s;
    }
}
