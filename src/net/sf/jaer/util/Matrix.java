/* Matrix.java
 *
 * Created on 23. Mai 2006, 17:33 */

package net.sf.jaer.util;

import java.util.logging.Logger;

/**
 * 
// Matrix.java
// solve, invert, etc. last argument is output
// void solve(float A[][], float Y[], float X[]);          X=A^-1*Y
// void invert(float A[][]);                                 A=A^-1
// float determinant(float A[]);                            d=det A
// void eigenvalues(float A[][], float V[][], float Y[]);  V,Y=eigen A
// void checkeigen(float A[][], V[][], float Y[]);          printout
// void multiply(float A[][], float B[][], float C[][]);   C=A*B
// void add(float A[][], float B[][], float C[][]);        C=A+B
// void subtract(float C[][], float A[][], float B[][]);   C=A-B
// float norm1(float A[][]);                                d=norm1 A
// float norm2(float A[][]);  sqrt largest eigenvalue A^T*A d=norm2 A
// float normFro(float A[][]); Frobenius                    d=normFro A
// float normInf(float A[][]);                              d=normInf A
// void identity(float A[][]);                               A=I
// void zero(float A[][]);                                   A=0
// void copy(float A[][], floatB[][]);                      B=A
// void boolean equals(float A[][], floatB[][]);            B==A
// void print(float A[][]);                                  A
// void multiply(float A[][], float X[], float Y[]);       Y=A*X
// void add(float X[], float Y[], float Z[]);              Z=X+Y
// void subtract(float X[], float Y[], float Z[]);         Z=X-Y
// float norm1(float X[]);                                  d=norm1 X
// float norm2(float X[]);                                  d=norm2 X
// float normInf(float X[]);                                d=normInf X
// void unitVector(float X[], int i);                        X[i]=1 else 0
// void zero(float X[]);                                     X=0
// void copy(float X[], float Y[]);                         Y=X
// void boolean equals(float X[], floatY[]);                X==Y
// void print(float X[]);                                    X

 @author Janick Cardinale
 */
public strictfp class Matrix {
    
    static final Logger log=Logger.getLogger("Matrix");
    
/**
 *         // solve real linear equations for X where Y = A * X
        // method: Gauss-Jordan elimination using maximum pivot
        // usage:  Matrix.solve(A,Y,X);
        //    Translated to java by : Jon Squire , 26 March 2003
        //    First written by Jon Squire December 1959 for IBM 650, translated to
        //    other languages  e.g. Fortran converted to Ada converted to C
        //    converted to java

 * @param A
 * @param Y
 * @param X
 */    
    public static void solve(final float A[][], final float Y[],
            float X[]) {
        int n=A.length;
        int m=n+1;
        float B[][]=new float[n][m];  // working matrix
        int row[]=new int[n];           // row interchange indicies
        int hold , I_pivot;             // pivot indicies
        float pivot;                   // pivot element value
        float abs_pivot;
        if(A[0].length!=n || Y.length!=n || X.length!=n) {
            log.warning("Error in Matrix.solve, inconsistent array sizes.");
        }
        // build working data structure
        for(int i=0; i<n; i++) {
            for(int j=0; j<n; j++) {
                B[i][j] = A[i][j];
            }
            B[i][n] = Y[i];
        }
        // set up row interchange vectors
        for(int k=0; k<n; k++) {
            row[k] = k;
        }
        //  begin main reduction loop
        for(int k=0; k<n; k++) {
            // find largest element for pivot
            pivot = B[row[k]][k];
            abs_pivot = Math.abs(pivot);
            I_pivot = k;
            for(int i=k; i<n; i++) {
                if(Math.abs(B[row[i]][k]) > abs_pivot) {
                    I_pivot = i;
                    pivot = B[row[i]][k];
                    abs_pivot = Math.abs(pivot);
                }
            }
            // have pivot, interchange row indicies
            hold = row[k];
            row[k] = row[I_pivot];
            row[I_pivot] = hold;
            // check for near singular
            if(abs_pivot < 1.0E-10) {
                for(int j=k+1; j<n+1; j++) {
                    B[row[k]][j] = 0.0f;
                }
                log.warning("redundant row (singular) "+row[k]);
            } // singular, delete row
            else {
                // reduce about pivot
                for(int j=k+1; j<n+1; j++) {
                    B[row[k]][j] = B[row[k]][j] / B[row[k]][k];
                }
                //  inner reduction loop
                for(int i=0; i<n; i++) {
                    if( i != k) {
                        for(int j=k+1; j<n+1; j++) {
                            B[row[i]][j] = B[row[i]][j] - B[row[i]][k] * B[row[k]][j];
                        }
                    }
                }
            }
            //  finished inner reduction
        }
        //  end main reduction loop
        //  build  X  for return, unscrambling rows
        for(int i=0; i<n; i++) {
            X[i] = B[row[i]][n];
        }
    } // end solve
    
    public static final void invert(float A[][]) {
        int n = A.length;
        int row[] = new int[n];
        int col[] = new int[n];
        float temp[] = new float[n];
        int hold , I_pivot , J_pivot;
        float pivot, abs_pivot;
        
        if(A[0].length!=n) {
            log.warning("Error in Matrix.invert, inconsistent array sizes.");
        }
        // set up row and column interchange vectors
        for(int k=0; k<n; k++) {
            row[k] = k ;
            col[k] = k ;
        }
        // begin main reduction loop
        for(int k=0; k<n; k++) {
            // find largest element for pivot
            pivot = A[row[k]][col[k]] ;
            I_pivot = k;
            J_pivot = k;
            for(int i=k; i<n; i++) {
                for(int j=k; j<n; j++) {
                    abs_pivot = Math.abs(pivot) ;
                    if(Math.abs(A[row[i]][col[j]]) > abs_pivot) {
                        I_pivot = i ;
                        J_pivot = j ;
                        pivot = A[row[i]][col[j]] ;
                    }
                }
            }
            if(Math.abs(pivot) < 1.0E-10) {
                log.warning("Matrix is singular !");
                return;
            }
            hold = row[k];
            row[k]= row[I_pivot];
            row[I_pivot] = hold ;
            hold = col[k];
            col[k]= col[J_pivot];
            col[J_pivot] = hold ;
            // reduce about pivot
            A[row[k]][col[k]] = 1.0f / pivot ;
            for(int j=0; j<n; j++) {
                if(j != k) {
                    A[row[k]][col[j]] = A[row[k]][col[j]] * A[row[k]][col[k]];
                }
            }
            // inner reduction loop
            for(int i=0; i<n; i++) {
                if(k != i) {
                    for(int j=0; j<n; j++) {
                        if( k != j ) {
                            A[row[i]][col[j]] = A[row[i]][col[j]] - A[row[i]][col[k]] *
                                    A[row[k]][col[j]] ;
                        }
                    }
                    A[row[i]][col [k]] = - A[row[i]][col[k]] * A[row[k]][col[k]] ;
                }
            }
        }
        // end main reduction loop
        
        // unscramble rows
        for(int j=0; j<n; j++) {
            for(int i=0; i<n; i++) {
                temp[col[i]] = A[row[i]][j];
            }
            for(int i=0; i<n; i++) {
                A[i][j] = temp[i] ;
            }
        }
        // unscramble columns
        for(int i=0; i<n; i++) {
            for(int j=0; j<n; j++) {
                temp[row[j]] = A[i][col[j]] ;
            }
            for(int j=0; j<n; j++) {
                A[i][j] = temp[j] ;
            }
        }
    } // end invert
    
    public static final float determinant(final float A[][]) {
        int n=A.length;
        float D = 1.0f;                 // determinant
        float B[][]=new float[n][n];  // working matrix
        int row[]=new int[n];             // row interchange indicies
        int hold , I_pivot;             // pivot indicies
        float pivot;                   // pivot element value
        float abs_pivot;
        
        if(A[0].length!=n) {
            log.warning("Error in Matrix.determinant, inconsistent array sizes.");
        }
        // build working matrix
        for(int i=0; i<n; i++)
            for(int j=0; j<n; j++)
                B[i][j]=A[i][j];
        // set up row interchange vectors
        for(int k=0; k<n; k++) {
            row[k]= k;
        }
        // begin main reduction loop
        for(int k=0; k<n-1; k++) {
            // find largest element for pivot
            pivot = B[row[k]][k];
            abs_pivot = Math.abs(pivot);
            I_pivot = k;
            for(int i=k; i<n; i++) {
                if( Math.abs(B[row[i]][k]) > abs_pivot ) {
                    I_pivot = i;
                    pivot = B[row[i]][k];
                    abs_pivot = Math.abs(pivot);
                }
            }
            // have pivot, interchange row indicies
            if(I_pivot != k) {
                hold = row[k];
                row[k] = row[I_pivot];
                row[I_pivot] = hold;
                D = - D;
            }
            // check for near singular
            if(abs_pivot < 1.0E-10) {
                return 0.0f;
            } else {
                D = D * pivot;
                // reduce about pivot
                for(int j=k+1; j<n; j++) {
                    B[row[k]][j] = B[row[k]][j] / B[row[k]][k];
                }
                //  inner reduction loop
                for(int i=0; i<n; i++) {
                    if(i != k) {
                        for(int j=k+1; j<n; j++) {
                            B[row[i]][j] = B[row[i]][j] - B[row[i]][k]* B[row[k]][j];
                        }
                    }
                }
            }
            //  finished inner reduction
        }
        // end of main reduction loop
        return D * B[row[n-1]][n-1];
    } // end determinant
    
/**
 *         // cyclic Jacobi iterative method of finding eigenvalues
        // advertized for symmetric real

 * @param A the matrix
 * @param V eigenvectors
 * @param Y the vector of eigenvalues
 */    
    public static final void eigenvalues(final float A[][],
            float V[][], float Y[]) {
        int n=A.length;
        float AA[][] = new float[n][n];
        float norm;
        float c[] = new float[1];
        float s[] = new float[1];
        if(A[0].length!=n || V.length!=n || V[0].length!=n || Y.length!=n) {
            log.warning("Error in Matrix.eigenvalues, inconsistent array sizes.");
        }
        c[0] = 1.0f;
        s[0] = 0.0f;
        for(int i=0; i<n; i++) // start V as identity matrix
        {
            for(int j=0; j<n; j++) V[i][j]=0.0f;
            V[i][i]=1.0f;
        }
        copy(A, AA);
        for(int k=0; k<n; k++) {
            
            norm=norm4(AA);
            for(int i=0; i<n-1; i++) {
                for(int j=i+1; j<n; j++) {
                    schur2(AA, i, j, c, s);
                    mat44(i, j, c, s, AA, V);
                }
            } // end one iteration
        }
        norm = norm4(AA); // final quality check if desired
        for(int i=0; i<n; i++) // copy eigenvalues back to caller
            Y[i] = AA[i][i];
    } // end eigenvalues
    
 /**
  *      // check  A * X = lambda X  lambda=Y[i] X=V[i]
  * @param A
  * @param V
  * @param Y
  */  
    public static final void eigenCheck(final float A[][], final float V[][],
            float Y[]) {
        int n=A.length;
        float X[] = new float[n];
        float Z[] = new float[n];
        float T[] = new float[n];
        float norm;
        
        if(A[0].length!=n || V.length!=n || V[0].length!=n || Y.length!=n) {
            log.warning("Error in Matrix.eigenCheck, inconsistent array sizes.");
        }
        for(int i=0; i<n; i++) {
            for(int j=0; j<n; j++) {
                X[j]=V[j][i];
            }
            multiply(A, X, T);
            for(int j=0; j<n; j++) {
                Z[j]=T[j]-Y[i]*X[j];
            }
            norm = norm2(Z);
            log.warning("check for near zero norm of Z["+i+"]="+Z[i]);
        }
    } // end eigenCheck
    
    static void schur2(final float A[][], final int p, final int q,
            float c[], float s[]) {
        float tau;
        float t;
        
        if(A[0].length!=A.length || c.length!=1 || s.length!=1) {
            log.warning("Error in schur2 of jacobi, inconsistent array sizes.");
        }
        if(A[p][q]!=0.0) {
            tau=(A[q][q]-A[p][p])/(2.0f*A[p][q]);
            if(tau>=0.0)
                t=1.0f/(tau+(float)Math.sqrt(1.0f+tau*tau));
            else
                t=-1.0f/((-tau)+(float)Math.sqrt(1.0f+tau*tau));
            c[0]=1.0f/(float)Math.sqrt(1.0f+t*t);
            s[0]=t * c[0];
        } else {
            c[0]=1.0f;
            s[0]=0.0f;
        }
    } // end schur2
    
    static void mat22(final float c[], final float s[], final float A[][],
            float B[][]) {
        if(A.length!=2 || A[0].length!=2 || B.length!=2 || B[0].length!=2) {
            log.warning("Error in mat22 of Jacobi, not both 2 by 2");
        }
        float T[][] = new float[2][2];
        
        T[0][0] = c[0] * A[0][0] - s[0] * A[0][1] ;
        T[0][1] = s[0] * A[0][0] + c[0] * A[0][1] ;
        T[1][0] = c[0] * A[1][0] - s[0] * A[1][1] ;
        T[1][1] = s[0] * A[1][0] + c[0] * A[1][1] ;
        
        B[0][0] = c[0] * T[0][0] - s[0] * T[1][0] ;
        B[0][1] = c[0] * T[0][1] - s[0] * T[1][1] ;
        B[1][0] = s[0] * T[0][0] + c[0] * T[1][0] ;
        B[1][1] = s[0] * T[0][1] + c[0] * T[1][1] ;
    } // end mat2
    
    static void mat44(final int p, final int q, final float c[], final float s[],
            final float A[][], float V[][]) {
        int n = A.length;
        float B[][] = new float[n][n];
        float J[][] = new float[n][n];
        if(s.length!=1 || c.length!=1) {
            log.warning("Error in mat44 of Jacobi, s or c not length 1");
        }
        if(A[0].length!=n || V.length!=n || V[0].length!=n) {
            log.warning("Error in mat44 of Jacobi, A or V not same and square");
        }
        for(int i=0; i<n; i++) {
            for(int j=0; j<n; j++) {
                J[i][j]=0.0f;
            }
            J[i][i]=1.0f;
        }
        J[p][p]=c[0]; /* J transpose */
        J[p][q]=-s[0];
        J[q][q]=c[0];
        J[q][p]=s[0];
        multiply(J, A, B);
        J[p][q]=s[0];
        J[q][p]=-s[0];
        multiply(B, J, A);
        multiply(V, J, B);
        copy(B, V);
    } // end mat44
    
    static float norm4(final float A[][]) // for Jacobi
    {
        int n=A.length;
        int nr=A[0].length;
        float nrm=0.0f;
        if(n!=nr) {
            log.warning("Error in norm4, non square A["+n+"]["+nr+"]");
        }
        for(int i=0; i<n-1; i++) {
            for(int j=i+1; j<n; j++) {
                nrm=nrm+Math.abs(A[i][j])+Math.abs(A[j][i]);
            }
        }
        return nrm/(n*n-n);
    } // end norm4

    /** Multiplies matrices A times B to give C.
     * C=AB.
     *
     * Each matrix is M[rows][columns].
     *
     * The number of rows of A must equal the number of rows of C.
     *
     * The number of columns of
     * A must equal the number of rows of B.
     *
     * The number of columns of C must equal the number of columns of B.
     * @param A
     * @param B
     * @param C
     */
    public static final void multiply(final float A[][], final float B[][], float C[][]) {
        int ni = A.length;
        int nk = A[0].length;
        int nj = B[0].length;
        if(B.length!=nk || C.length!=ni || C[0].length!=nj) {
            log.warning("Error in Matrix.multiply, incompatible sizes in operation A*B=C: A.col("+nk+")==B.row("+B.length+") , C.row("+C.length+")==A.row("+ni+") , C.col("+C[0].length+")==B.col("+nj+")");
        }
        for(int i=0; i<ni; i++)
            for(int j=0; j<nj; j++) {
            C[i][j] = 0.0f;
            for(int k=0; k<nk; k++)
                C[i][j] = C[i][j] + A[i][k] * B[k][j];
            }
    } // end multiply
    
    public static final void add(final float A[][], final float B[][], float C[][]) {
        int ni=A.length;
        int nj=A[0].length;
        if(B.length!=ni || C.length!=ni || B[0].length!=nj || C[0].length!=nj) {
            log.warning("Error in Matrix.add, incompatible sizes");
        }
        for(int i=0; i<ni; i++)
            for(int j=0; j<nj; j++)
                C[i][j] = A[i][j] + B[i][j];
    } // end add
    public final static float[][] addMatrix(float [][] a, float[][] b){
        int ra = a.length; int ca = a[0].length;
        int rb = b.length; int cb = b[0].length;
        if(ca != cb || ra != rb){ log.warning("Matrix dimensions do not agree"); return null;}
        float[][] m = new float[ra][cb];
        for(int i = 0; i < ra;i++)
            for(int j = 0; j < cb; j++)
                m[i][j] = a[i][j]+b[i][j];
        return m;
    }
    public static final void subtract(final float A[][], final float B[][], float C[][]) {
        int ni=A.length;
        int nj=A[0].length;
        if(B.length!=ni || C.length!=ni || B[0].length!=nj || C[0].length!=nj) {
            log.warning("Error in Matrix.subtract, incompatible sizes");
        }
        for(int i=0; i<ni; i++)
            for(int j=0; j<nj; j++)
                C[i][j] = A[i][j] - B[i][j];
    } // end subtract
    
    public static final float norm1(final float A[][]) {
        float norm=0.0f;
        float colSum;
        int ni=A.length;
        int nj=A[0].length;
        for(int j=0; j<nj; j++) {
            colSum = 0.0f;
            for(int i=0; i<ni; i++)
                colSum = colSum + Math.abs(A[i][j]);
            norm = Math.max(norm, colSum);
        }
        return norm;
    } // end norm1
    
    public static final float normInf(final float A[][]) {
        float norm=0.0f;
        float rowSum;
        int ni=A.length;
        int nj=A[0].length;
        for(int i=0; i<ni; i++) {
            rowSum = 0.0f;
            for(int j=0; j<nj; j++)
                rowSum = rowSum + Math.abs(A[i][j]);
            norm = Math.max(norm, rowSum);
        }
        return norm;
    } // end normInf
    
    public static final void identity(float A[][]) {
        int n=A.length;
        if(A[0].length!=n) {
            log.warning("Error in Matrix.identity, not square");
        }
        for(int i=0; i<n; i++) {
            for(int j=0; j<n; j++) A[i][j]=0.0f;
            A[i][i]=1.0f;
        }
    } // end identity
    
    public static final void zero(float A[][]) {
        int ni=A.length;
        int nj=A[0].length;
        for(int i=0; i<ni; i++)
            for(int j=0; j<nj; j++) A[i][j]=0.0f;
    } // end zero
    
    public static final float normFro(final float A[][]) {
        float norm=0.0f;
        int n=A.length;
        for(int i=0; i<n; i++)
            for(int j=0; j<n; j++)
                norm = norm + A[i][j]*A[i][j];
        return (float)Math.sqrt(norm);
    } // end normFro
    
    
    public static final float norm2(final float A[][]) {
        float r=0.0f; // largest eigenvalue
        int n=A.length;
        float B[][] = new float[n][n];
        float V[][] = new float[n][n];
        float BI[] = new float[n];
        for(int i=0; i<n; i++)  // B = A^T * A
        {
            for(int j=0; j<n; j++) {
                B[i][j]=0.0f;
                for(int k=0; k<n; k++)
                    B[i][j] = B[i][j] + A[k][i]*A[k][j];
            }
        }
        eigenvalues(B, V, BI);
        for(int i=0; i<n; i++) r=Math.max(r,BI[i]);
        return (float)Math.sqrt(r);
    } // end norm2
    
    public static final void copy(final float A[][], float B[][]) {
        int ni = A.length;
        int nj = A[0].length;
        if(B.length!=ni || B[0].length!=nj) {
            log.warning("Error in Matrix.copy,"+
                    " incompatible sizes.");
        }
        for(int i=0; i<ni; i++)
            for(int j=0; j<nj; j++)
                B[i][j] = A[i][j];
    } // end copy
    
    public static final boolean equals(final float A[][], final float B[][]) {
        int ni = A.length;
        int nj = A[0].length;
        boolean same = true;
        if(B.length!=ni || B[0].length!=nj) {
            log.warning("Error in Matrix.equals,"+
                    " incompatible sizes.");
        }
        for(int i=0; i<ni; i++)
            for(int j=0; j<nj; j++)
                same = same && (A[i][j] == B[i][j]);
        return same;
    } // end equals
    
    public static final void print(float A[][]) {
        int rows = A.length;
        int cols = A[0].length;
        for(int i=0; i<rows; i++){
            for(int j=0; j<cols; j++) {
                System.out.print(A[i][j] + "  ");
            }
            System.out.println("");
        }
        System.out.println("");
    } // end print
    
    public static final void print(float A[][], int precision) {
        int rows = A.length;
        int cols = A[0].length;
        for(int i=0; i<rows; i++){
            for(int j=0; j<cols; j++) {
                System.out.printf("% "+(precision+6)+"."+precision+"f ", A[i][j]);
            }
            System.out.println("");
        }
        System.out.println("");
    } // end print
                    
    public static final void multiply(float A[][], float B[], float C[]) {
        int rows=A.length;
        int cols=B.length;
        if(A[0].length != cols)
            throw new RuntimeException("Matrix dimensions do not agree in AB: A has "+A[0].length+" columns and B has "+cols+" rows");
        for(int i=0; i<rows; i++){
            C[i] = 0.0f;
            for(int j=0; j<cols; j++){
                C[i] = C[i] + A[i][j] * B[j];
            }
        }
    } // end multiply
    public final static float[][] multMatrix(float[][] a, float s){
        int ra = a.length; int ca = a[0].length;
        float[][] m = new float[ra][ca];
        for(int i = 0; i < ra;i++)
            for(int j = 0; j < ca; j++)
                m[i][j] = a[i][j]*s;
        return m;
    }
    
    public final static float[][] multMatrix(float[][] a, float[][] b){
        int ra = a.length; int ca = a[0].length;
        int rb = b.length; int cb = b[0].length;
        if(ca != rb){ log.warning("Matrix dimensions do not agree"); return null;}
        float[][] m = new float[ra][cb];
        for(int i = 0; i < ra;i++)
            for(int j = 0; j < cb; j++){
            m[i][j] =0;
            for(int k = 0; k < ca;k++)
                m[i][j] += a[i][k]*b[k][j];
            }
        return m;
    }
    public final static float[] multMatrix(float[][] a, float[] x){
        int ra = a.length; int ca = a[0].length;
        if(ca != x.length){ log.warning("Matrix dimensions do not agree"); return null;}
        float[] m = new float[ra];
        for(int i = 0; i < ra; i++){
            m[i] =0;
            for(int k = 0; k < ca;k++)
                m[i] += a[i][k]*x[k];
        }
        return m;
    }
    public static final void add(float X[], float Y[], float Z[]) {
        int n=X.length;
        if(Y.length!=n || Z.length!=n) {
            log.warning("Error in Matrix.add,"+
                    " incompatible sizes.");
        }
        for(int i=0; i<n; i++) Z[i] = X[i] + Y[i];
    } // end add
    
    public static final void subtract(float X[], float Y[], float Z[]) {
        int n=X.length;
        if(Y.length!=n || Z.length!=n) {
            log.warning("Error in Matrix.subtract,"+
                    " incompatible sizes.");
        }
        for(int i=0; i<n; i++) Z[i] = X[i] - Y[i];
    } // end subtract
    
    public final static float[][] transposeMatrix(float[][] a){
        int ra = a.length; int ca = a[0].length;
        float[][] m = new float[ca][ra];
        for(int i = 0; i< ra;i++){
            for(int j = 0; j < ca; j++)
                m[j][i] = a[i][j];
        }
        return m;
    }
    public static final float norm1(float X[]) {
        float norm=0.0f;
        int n=X.length;
        for(int i=0; i<n; i++)
            norm = norm + Math.abs(X[i]);
        return norm;
    } // end norm1
    
    public static final float norm2(float X[]) {
        float norm=0.0f;
        int n=X.length;
        for(int i=0; i<n; i++)
            norm = norm + X[i]*X[i];
        return (float)Math.sqrt(norm);
    } // end norm2
    
    public static final float normInf(float X[]) {
        float norm=0.0f;
        int n=X.length;
        for(int i=0; i<n; i++)
            norm = Math.max(norm, Math.abs(X[i]));
        return norm;
    } // end normInf
    
    public static final void unitVector(float X[], int j) {
        int n=X.length;
        for(int i=0; i<n; i++) X[i]=0.0f;
        X[j]=1.0f;
    } // end unitVector
    
    public static final void zero(float X[]) {
        int n=X.length;
        for(int i=0; i<n; i++) X[i]=0.0f;
    } // end zero
    
    public static final void copy(float X[], float Y[]) {
        int n = X.length;
        if(Y.length!=n) {
            log.warning("Error in Matrix.copy,"+
                    " incompatible sizes.");
        }
        for(int i=0; i<n; i++) Y[i] = X[i];
    } // end copy
    
    public static final boolean equals(final float X[], final float Y[]) {
        int n = X.length;
        boolean same = true;
        if(Y.length!=n) {
            log.warning("Error in Matrix.equals,"+
                    " incompatible sizes.");
        }
        for(int i=0; i<n; i++)
            same = same && (X[i] == Y[i]);
        return same;
    } // end equals
    
    public static final void print(float X[]) {
        int n = X.length;
        for(int i=0; i<n; i++)
            System.out.println("X["+i+"]="+X[i]);
    } // end print
    
} // end class Matrix


