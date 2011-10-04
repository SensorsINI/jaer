package ch.unizh.ini.jaer.projects.labyrinthkalman;

/**
 * @author Tobias Pietzsch, Eero Lehtonen
 * 
 */
public class MatrixOps
{
	public static int rows( final double[] a )
	{
		return a.length;
	}

	public static int rows( final double[][] A )
	{
		return A.length;
	}

	public static int cols( final double[][] A )
	{
		return A[ 0 ].length;
	}

	/**
	 * copy matrix A to B. Dimensions of A and B must match.
	 * 
	 * @param A
	 * @param B
	 */
	public static void copy( final double[][] A, final double[][] B )
	{
		assert rows( A ) == rows( B );
		assert cols( A ) == cols( B );

		final int cols = cols( A );
		final int rows = rows( A );

		for ( int i = 0; i < rows; ++i )
			for ( int j = 0; j < cols; ++j )
				B[ i ][ j ] = A[ i ][ j ];
	}

	/**
	 * copy matrix A to vector b.
	 * 
	 * Dimensions of A and b must match. That is rows(A) == rows(b) and cols(A)
	 * == 1.
	 * 
	 * @param A
	 * @param b
	 */
	public static void copy( final double[][] A, final double[] b )
	{
		assert rows( A ) == rows( b );
		assert cols( A ) == 1;

		final int rows = rows( A );

		for ( int i = 0; i < rows; ++i )
			b[ i ] = A[ i ][ 0 ];
	}

	/**
	 * copy vector a to matrix B.
	 * 
	 * Dimensions of a and B must match. That is rows(a) == rows(B) and cols(B)
	 * == 1.
	 * 
	 * @param a
	 * @param B
	 */
	public static void copy( final double[] a, final double[][] B )
	{
		assert rows( a ) == rows( B );
		assert cols( B ) == 1;

		final int rows = rows( a );

		for ( int i = 0; i < rows; ++i )
			B[ i ][ 0 ] = a[ i ];
	}

	/**
	 * copy vector a to b. Dimensions of a and b must match.
	 * 
	 * @param a from array
	 * @param b to array 
	 */
	public static void copy( double[] a, double[] b )
	{
		assert rows( a ) == rows( b );

		final int rows = rows( a );

		for ( int i = 0; i < rows; ++i )
			b[ i ] = a[ i ];
	}

	/**
	 * set C = A + B. Dimensions of A, B, and C must match.
	 * 
	 * @param A
	 * @param B
	 * @param C
	 */
	public static void add( double[][] A, double[][] B, double[][] C )
	{
		assert ( rows( A ) == rows( B ) ) && ( rows( A ) == rows( C ) );
		assert ( cols( A ) == cols( B ) ) && ( cols( A ) == cols( C ) );

		final int cols = cols( A );
		final int rows = rows( A );

		for ( int i = 0; i < rows; ++i )
			for ( int j = 0; j < cols; ++j )
				C[ i ][ j ] = A[ i ][ j ] + B[ i ][ j ];
	}

	/**
	 * set A = A + B. Dimensions of A and B must match.
	 * 
	 * @param A
	 * @param B
	 */
	public static void increment( double[][] A, double[][] B )
	{
		assert rows( A ) == rows( B );
		assert cols( A ) == cols( B );

		final int cols = cols( A );
		final int rows = rows( A );

		for ( int i = 0; i < rows; ++i )
			for ( int j = 0; j < cols; ++j )
				A[ i ][ j ] += B[ i ][ j ];
	}

	/**
	 * set A = A - B. Dimensions of A and B must match.
	 * 
	 * @param A
	 * @param B
	 */
	public static void decrement( double[][] A, double[][] B )
	{
		assert rows( A ) == rows( B );
		assert cols( A ) == cols( B );

		final int cols = cols( A );
		final int rows = rows( A );

		for ( int i = 0; i < rows; ++i )
			for ( int j = 0; j < cols; ++j )
				A[ i ][ j ] -= B[ i ][ j ];
	}

	/**
	 * set c = a + b. Dimensions of a, b, and c must match.
	 * 
	 * @param a
	 * @param b
	 * @param c
	 */
	public static void add( double[] a, double[] b, double[] c )
	{
		assert ( rows( a ) == rows( b ) ) && ( rows( a ) == rows( c ) );

		final int rows = rows( a );

		for ( int i = 0; i < rows; ++i )
			c[ i ] = a[ i ] + b[ i ];
	}

	/**
	 * set C = A - B. Dimensions of A, B, and C must match.
	 * 
	 * @param A
	 * @param B
	 * @param C
	 */
	public static void subtract( double[][] A, double[][] B, double[][] C )
	{
		assert ( rows( A ) == rows( B ) ) && ( rows( A ) == rows( C ) );
		assert ( cols( A ) == cols( B ) ) && ( cols( A ) == cols( C ) );

		final int cols = cols( A );
		final int rows = rows( A );

		for ( int i = 0; i < rows; ++i )
			for ( int j = 0; j < cols; ++j )
				C[ i ][ j ] = A[ i ][ j ] - B[ i ][ j ];
	}

	/**
	 * set c = a - b. Dimensions of a, b, and c must match.
	 * 
	 * @param a
	 * @param b
	 * @param c
	 */
	public static void subtract( double[] a, double[] b, double[] c )
	{
		assert ( rows( a ) == rows( b ) ) && ( rows( a ) == rows( c ) );

		final int rows = rows( a );

		for ( int i = 0; i < rows; ++i )
			c[ i ] = a[ i ] - b[ i ];
	}

	/**
	 * set C = A * B.
	 * 
	 * Dimensions of A, B, and C must match. That is, cols(A) == rows(B),
	 * rows(C) == rows(A), and cols(C) == cols(B).
	 * 
	 * @param A
	 * @param B
	 * @param C
	 */
	public static void mult( double[][] A, double[][] B, double[][] C )
	{
		assert cols( A ) == rows( B );
		assert ( rows( C ) == rows( A ) ) && ( cols( C ) == cols( B ) );

		final int cols = cols( C );
		final int rows = rows( C );
		final int Acols = cols( A );

		for ( int i = 0; i < rows; ++i )
		{
			for ( int j = 0; j < cols; ++j )
			{
				double sum = 0;
				for ( int k = 0; k < Acols; ++k )
					sum += A[ i ][ k ] * B[ k ][ j ];
				C[ i ][ j ] = sum;
			}
		}
	}

	/**
	 * set c = A * b.
	 * 
	 * Dimensions of a, B, and c must match. That is, cols(A) == rows(b), and
	 * rows(c) == rows(A).
	 * 
	 * @param A
	 * @param B
	 * @param c product
	 */
	public static void mult( double[][] A, double[] b, double[] c )
	{
		assert cols( A ) == rows( b );
		assert rows( c ) == rows( A );

		final int rows = rows( c );
		final int Acols = cols( A );

		for ( int i = 0; i < rows; ++i )
		{
			double sum = 0;
			for ( int k = 0; k < Acols; ++k )
				sum += A[ i ][ k ] * b[ k ];
			c[ i ] = sum;
		}
	}

	/**
	 * set cT = aT * B.
	 * 
	 * Dimensions of a, B, and c must match. That is, cols(aT) == rows(B),
	 * cols(cT) == cols(B)
	 * 
	 * @param a vector
	 * @param B matrix
	 * @param C vector product
	 */
	public static void mult( double[] a, double[][] B, double[] c )
	{
		assert rows( a ) == rows( B );
		assert rows( c ) == cols( B );

		final int rows = rows( B );
		final int cols = cols( B );

		for ( int j = 0; j < cols; ++j )
		{
			double sum = 0;
			for ( int k = 0; k < rows; ++k )
				sum += a[ j ] * B[ k ][ j ];
			c[ j ] = sum;
		}
	}

	/**
	 * compute dot product a * b.
	 * 
	 * Dimensions of a and b must match.
	 * 
	 * @param a
	 * @param b
	 */
	public static double dot( double[] a, double[] b )
	{
		assert rows( a ) == rows( b );

		final int rows = rows( a );

		double sum = 0;
		for ( int i = 0; i < rows; ++i )
			sum += a[ i ] * b[ i ];

		return sum;
	}

	/**
	 * set B = A^T.
	 * 
	 * Dimensions of A and B must match. That is rows(B) == cols(A), and cols(B)
	 * == rows(A).
	 * 
	 * @param A
	 * @param B
	 */
	public static void transpose( double[][] A, double[][] B )
	{
		assert rows( B ) == cols( A );
		assert cols( B ) == rows( A );

		int rows = rows( B );
		int cols = cols( B );

		for ( int i = 0; i < rows; i++ )
			for ( int j = 0; j < cols; j++ )
				B[ i ][ j ] = A[ j ][ i ];
	}

	private static double[] tmp = new double[ 100 ];

	/**
	 * set C = A * B * A^T (where B and C is are square matrices).
	 * 
	 * Dimensions of A, B, and C must match. That is, cols(A) == rows(B) ==
	 * cols(B), rows(C) == cols(C) == rows(A).
	 * 
	 * @param A
	 * @param B
	 * @param C
	 */
	public static void multABAT( double[][] A, double[][] B, double[][] C )
	{
		assert cols( A ) == rows( B );
		assert rows( A ) == rows( C );
		assert cols( B ) == rows( B );
		assert cols( C ) == rows( C );

		final int rows = rows( A );
		final int cols = cols( A );

		if ( tmp.length < cols )
			tmp = new double[ cols ];

		for ( int i = 0; i < rows; ++i )
		{
			// compute tmp^T = A.row(i) * B
			for ( int k = 0; k < cols; ++k )
			{
				double sum = 0;
				for ( int j = 0; j < cols; ++j )
					sum += A[ i ][ j ] * B[ j ][ k ];
				tmp[ k ] = sum;
			}

			// compute i-th row of C
			for ( int k = 0; k < rows; ++k )
			{
				double sum = 0;
				for ( int j = 0; j < cols; ++j )
					sum += tmp[ j ] * A[ k ][ j ];
				C[ i ][ k ] = sum;
			}
		}
	}

	/**
	 * set C = A * B^T.
	 * 
	 * Dimensions of A, B, and C must match. That is, cols(A) == cols(B),
	 * rows(C) == rows(A), and cols(C) == rows(B).
	 * 
	 * @param A
	 * @param B
	 * @param C
	 */
	public static void multABT( double[][] A, double[][] B, double[][] C )
	{
		assert cols( A ) == cols( B );
		assert ( rows( C ) == rows( A ) ) && ( cols( C ) == rows( B ) );

		final int cols = cols( C );
		final int rows = rows( C );
		final int Acols = cols( A );

		for ( int i = 0; i < rows; ++i )
		{
			for ( int j = 0; j < cols; ++j )
			{
				double sum = 0;
				for ( int k = 0; k < Acols; ++k )
					sum += A[ i ][ k ] * B[ j ][ k ];
				C[ i ][ j ] = sum;
			}
		}
	}

	/**
	 * computes R = inv (A) if det(A) != 0, else R = 0. A and R must be 2x2
	 * matrices.
	 */
	public static void invert2by2Matrix( double[][] A, double[][] R )
	{
		assert ( rows( A ) == 2 ) && ( cols( A ) == 2 );
		assert ( rows( R ) == 2 ) && ( cols( R ) == 2 );

		double detA = A[ 0 ][ 0 ] * A[ 1 ][ 1 ] - A[ 0 ][ 1 ] * A[ 1 ][ 0 ];
		if ( detA == 0 )
		{
			R[ 0 ][ 0 ] = 0;
			R[ 0 ][ 1 ] = 0;
			R[ 1 ][ 0 ] = 0;
			R[ 1 ][ 1 ] = 0;
		}
		else
		{
			R[ 0 ][ 0 ] = ( 1 / detA ) * A[ 1 ][ 1 ];
			R[ 0 ][ 1 ] = -( 1 / detA ) * A[ 0 ][ 1 ];
			R[ 1 ][ 0 ] = -( 1 / detA ) * A[ 1 ][ 0 ];
			R[ 1 ][ 1 ] = ( 1 / detA ) * A[ 0 ][ 0 ];
		}
	}

	public static String toString( double[][] A )
	{
		return toString( A, "%6.3f " );
	}

	public static String toString( double[][] A, String format )
	{
		final int rows = rows( A );
		final int cols = cols( A );

		String result = "";
		for ( int i = 0; i < rows; ++i )
		{
			for ( int j = 0; j < cols; ++j )
				result += String.format( format, A[ i ][ j ] );
			result += "\n";
		}
		return result;
	}

	public static String toString( double[] a )
	{
		return toString( a, "%6.3f " );
	}

	public static String toString( double[] a, String format )
	{
		final int rows = rows( a );

		String result = "";
		for ( int i = 0; i < rows; ++i )
			result += String.format( format, a[ i ] );
		result += "\n";
		return result;
	}

	/*
	 * A is an upper triangular matrix, result: R=A*B
	 */
	/*
	 * public static void upperTriangularMatrixMultiplication( double[][] A,
	 * double[][] B, double[][] R ) { int Arow = A[ 0 ].length; int Acol =
	 * A.length;
	 * 
	 * int Brow = B[ 0 ].length; // int Bcol = B.length;
	 * 
	 * for ( int i = 0; i < Acol; i++ ) { for ( int j = 0; j < Brow; j++ ) {
	 * double sum = 0; for ( int k = i; k < Arow; k++ ) { sum += A[ i ][ k ] *
	 * B[ k ][ j ]; } R[ i ][ j ] = sum; } } }
	 */

	/*
	 * B is a lower triangular matrix, result: R=A*B
	 */
	/*
	 * public static void lowerTriangularMatrixMultiplication( double[][] A,
	 * double[][] B, double[][] R ) { int Arow = A[ 0 ].length; int Acol =
	 * A.length;
	 * 
	 * int Brow = B[ 0 ].length; int Bcol = B.length;
	 * 
	 * for ( int i = 0; i < Acol; i++ ) { for ( int j = 0; j < Brow; j++ ) {
	 * double sum = 0; for ( int k = 0; k <= i; k++ ) { sum += A[ i ][ k ] * B[
	 * k ][ j ]; } R[ i ][ j ] = sum; } } }
	 */

}
