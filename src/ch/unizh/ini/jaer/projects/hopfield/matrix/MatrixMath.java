/**
 * Introduction to Neural Networks with Java, 2nd Edition
 * Copyright 2008 by Heaton Research, Inc. 
 * http://www.heatonresearch.com/books/java-neural-2/
 * 
 * ISBN13: 978-1-60439-008-7  	 
 * ISBN:   1-60439-008-5
 *   
 * This class is released under the:
 * GNU Lesser General Public License (LGPL)
 * http://www.gnu.org/copyleft/lesser.html
 */
package ch.unizh.ini.jaer.projects.hopfield.matrix;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import ch.unizh.ini.jaer.projects.hopfield.matrix.exceptions.MatrixError;


/**
 * MatrixMath: This class can perform many different mathematical
 * operations on matrixes.
 * 
 * @author Jeff Heaton
 * @version 2.1
 */
public class MatrixMath {

	public static IntMatrix add(final IntMatrix a, final IntMatrix b) {
		if (a.getRows() != b.getRows()) {
			throw new MatrixError(
					"To add the matrices they must have the same number of rows and columns.  Matrix a has "
							+ a.getRows()
							+ " rows and matrix b has "
							+ b.getRows() + " rows.");
		}

		if (a.getCols() != b.getCols()) {
			throw new MatrixError(
					"To add the matrices they must have the same number of rows and columns.  Matrix a has "
							+ a.getCols()
							+ " cols and matrix b has "
							+ b.getCols() + " cols.");
		}

		final int result[][] = new int[a.getRows()][a.getCols()];

		for (int resultRow = 0; resultRow < a.getRows(); resultRow++) {
			for (int resultCol = 0; resultCol < a.getCols(); resultCol++) {
				result[resultRow][resultCol] = a.get(resultRow, resultCol)
						+ b.get(resultRow, resultCol);
		
		}
	}

	return new IntMatrix(result);
}
	public static IntMatrix addWithOutput(final IntMatrix a, final IntMatrix b,BufferedImage bimage) {
		if (a.getRows() != b.getRows()) {
			throw new MatrixError(
					"To add the matrices they must have the same number of rows and columns.  Matrix a has "
							+ a.getRows()
							+ " rows and matrix b has "
							+ b.getRows() + " rows.");
		}

		if (a.getCols() != b.getCols()) {
			throw new MatrixError(
					"To add the matrices they must have the same number of rows and columns.  Matrix a has "
							+ a.getCols()
							+ " cols and matrix b has "
							+ b.getCols() + " cols.");
		}
		int max_value = 1;
		final int result[][] = new int[a.getRows()][a.getCols()];

		for (int resultRow = 0; resultRow < a.getRows(); resultRow++) {
			for (int resultCol = 0; resultCol < a.getCols(); resultCol++) {
//				result[resultRow][resultCol] = a.get(resultRow, resultCol)
//						+ b.get(resultRow, resultCol);
				 int rgb = 0xFF00FF00; // green
				 
				int currentResult = result[resultRow][resultCol];
				int newResult = a.get(resultRow, resultCol) + b.get(resultRow, resultCol);
				if(currentResult!=newResult){
					
					result[resultRow][resultCol] = (newResult/max_value);
						int rgbValue = 50+ 2* (Math.abs(newResult) % 103);
						rgb = makeARGB(255,rgbValue,rgbValue,rgbValue);//Integer.parse("0xFF"+ characterRep+characterRep+characterRep);
						if(bimage!=null)
							bimage.setRGB(resultCol, resultRow, rgb);
					
				}
			}
			}

		return new IntMatrix(result);
	}
	private static int makeARGB(int a, int r, int g, int b) {
		return a << 24 | r << 16 | g << 8 | b;
		}

	public static int dotProduct(final IntMatrix a, final IntMatrix b) {
		if (!a.isVector() || !b.isVector()) {
			throw new MatrixError(
					"To take the dot product, both matrices must be vectors.");
		}

		final Integer aArray[] = a.toPackedArray();
		final Integer bArray[] = b.toPackedArray();

		if (aArray.length != bArray.length) {
			throw new MatrixError(
					"To take the dot product, both matrices must be of the same length.");
		}

		int result = 0;
		final int length = aArray.length;

		for (int i = 0; i < length; i++) {
			result += aArray[i] * bArray[i];
		}

		return result;
	}

	
	public static IntMatrix identityInt(final int size) {
		if (size < 1) {
			throw new MatrixError("Identity matrix must be at least of size 1.");
		}

		final IntMatrix result = new IntMatrix(size, size);

		for (int i = 0; i < size; i++) {
			result.set(i, i, 1);
		}

		return result;
	}

	
	public static IntMatrix multiply(final IntMatrix a, final double b) {
		final int result[][] = new int[a.getRows()][a.getCols()];
		for (int row = 0; row < a.getRows(); row++) {
			for (int col = 0; col < a.getCols(); col++) {
				result[row][col] = (int)(a.get(row, col) * b);
			}
		}
		return new IntMatrix(result);
	}
	public static IntMatrix multiply(final IntMatrix a, final IntMatrix b) {
		if (a.getCols() != b.getRows()) {
			throw new MatrixError(
					"To use ordinary matrix multiplication the number of columns on the first matrix must mat the number of rows on the second.");
		}

		final int result[][] = new int[a.getRows()][b.getCols()];

		for (int resultRow = 0; resultRow < a.getRows(); resultRow++) {
			for (int resultCol = 0; resultCol < b.getCols(); resultCol++) {
				int value = 0;

				for (int i = 0; i < a.getCols(); i++) {

					value += a.get(resultRow, i) * b.get(i, resultCol);
				}
				result[resultRow][resultCol] = value;
			}
		}

		return new IntMatrix(result);
	}
	
	
	public static IntMatrix subtract(final IntMatrix a, final IntMatrix b) {
		if (a.getRows() != b.getRows()) {
			throw new MatrixError(
					"To subtract the matrices they must have the same number of rows and columns.  Matrix a has "
							+ a.getRows()
							+ " rows and matrix b has "
							+ b.getRows() + " rows.");
		}

		if (a.getCols() != b.getCols()) {
			throw new MatrixError(
					"To subtract the matrices they must have the same number of rows and columns.  Matrix a has "
							+ a.getCols()
							+ " cols and matrix b has "
							+ b.getCols() + " cols.");
		}

		final int result[][] = new int[a.getRows()][a.getCols()];

		for (int resultRow = 0; resultRow < a.getRows(); resultRow++) {
			for (int resultCol = 0; resultCol < a.getCols(); resultCol++) {
				result[resultRow][resultCol] = a.get(resultRow, resultCol)
						- b.get(resultRow, resultCol);
			}
		}

		return new IntMatrix(result);
	}
	public static IntMatrix transpose(final IntMatrix input) {
		final int inverseMatrix[][] = new int[input.getCols()][input
				.getRows()];

		for (int r = 0; r < input.getRows(); r++) {
			for (int c = 0; c < input.getCols(); c++) {
				inverseMatrix[c][r] = input.get(r, c);
			}
		}

		return new IntMatrix(inverseMatrix);
	}

	
	private MatrixMath() {
	}

}
