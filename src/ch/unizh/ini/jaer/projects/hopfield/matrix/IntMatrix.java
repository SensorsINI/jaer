package ch.unizh.ini.jaer.projects.hopfield.matrix;

import java.io.Serializable;

import ch.unizh.ini.jaer.projects.hopfield.matrix.exceptions.MatrixError;


public class IntMatrix implements Cloneable, Serializable {

	/**
	 * Serial id for this class.
	 */
	private static final long serialVersionUID = -7977897210426471675L;

	public static IntMatrix createColumnMatrix(final int input[]) {
		final int d[][] = new int[input.length][1];
		for (int row = 0; row < d.length; row++) {
			d[row][0] = input[row];
		}
		return new IntMatrix(d);
	}

	public static IntMatrix createRowMatrix(final int input[]) {
		final int d[][] = new int[1][input.length];
		System.arraycopy(input, 0, d[0], 0, input.length);
		return new IntMatrix(d);
	}
	
	int matrix[][];

	public IntMatrix(final boolean sourceMatrix[][]) {
		this.matrix = new int[sourceMatrix.length][sourceMatrix[0].length];
		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				if (sourceMatrix[r][c]) {
					this.set(r, c, 1);
				} else {
					this.set(r, c, -1);
				}
			}
		}
	}

	public IntMatrix(final int sourceMatrix[][]) {
		this.matrix = new int[sourceMatrix.length][sourceMatrix[0].length];
		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				this.set(r, c, sourceMatrix[r][c]);
			}
		}
	}

	public IntMatrix(final int rows, final int cols) {
		this.matrix = new int[rows][cols];
	}

	public void add(final int row, final int col, final int value) {
		validate(row, col);
		final int newValue = get(row, col) + value;
		set(row, col, newValue);
	}

	public void clear() {
		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				set(r, c, 0);
			}
		}
	}

	@Override
	public IntMatrix clone() {
		return new IntMatrix(this.matrix);
	}

	public boolean equals(final IntMatrix matrix) {
		return equals(matrix, 10);
	}

	public boolean equals(final IntMatrix matrix, int precision) {

		if (precision < 0) {
			throw new MatrixError("Precision can't be a negative number.");
		}

		final double test = Math.pow(10.0, precision);
		if (Double.isInfinite(test) || (test > Long.MAX_VALUE)) {
			throw new MatrixError("Precision of " + precision
					+ " decimal places is not supported.");
		}

		precision = (int) Math.pow(10, precision);

		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				if ((long) (get(r, c) * precision) != (long) (matrix.get(r, c) * precision)) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * 
	 * @param array
	 * @param index
	 * @return The new index after this matrix has been read.
	 */
	public int fromPackedArray(final Integer[] array, int index) {

		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				this.matrix[r][c] = array[index++];
			}
		}

		return index;
	}

	public int get(final int row, final int col) {
		validate(row, col);
		return this.matrix[row][col];
	}

	public IntMatrix getCol(final int col) {
		if (col > getCols()) {
			throw new MatrixError("Can't get column #" + col
					+ " because it does not exist.");
		}

		final int newMatrix[][] = new int[getRows()][1];

		for (int row = 0; row < getRows(); row++) {
			newMatrix[row][0] = this.matrix[row][col];
		}

		return new IntMatrix(newMatrix);
	}

	public int getCols() {
		return this.matrix[0].length;
	}

	public IntMatrix getRow(final int row) {
		if (row > getRows()) {
			throw new MatrixError("Can't get row #" + row
					+ " because it does not exist.");
		}

		final int newMatrix[][] = new int[1][getCols()];

		for (int col = 0; col < getCols(); col++) {
			newMatrix[0][col] = this.matrix[row][col];
		}

		return new IntMatrix(newMatrix);
	}

	public int getRows() {
		return this.matrix.length;
	}

	public boolean isVector() {
		if (getRows() == 1) {
			return true;
		} else {
			return getCols() == 1;
		}
	}

	public boolean isZero() {
		for (int row = 0; row < getRows(); row++) {
			for (int col = 0; col < getCols(); col++) {
				if (this.matrix[row][col] != 0) {
					return false;
				}
			}
		}
		return true;
	}

	public void ramdomize(final int min, final int max) {
		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				this.matrix[r][c] = (int)(Math.random() * (max - min)) + min;
			}
		}
	}

	public void set(final int row, final int col, final int value) {
		validate(row, col);
		if (Double.isInfinite(value) || Double.isNaN(value)) {
			throw new MatrixError("Trying to assign invalid number to matrix: "
					+ value);
		}
		this.matrix[row][col] = value;
	}

	public int size() {
		return this.matrix[0].length * this.matrix.length;
	}

	public int sum() {
		int result = 0;
		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				result += this.matrix[r][c];
			}
		}
		return result;
	}

	public Integer[] toPackedArray() {
		final Integer result[] = new Integer[getRows() * getCols()];

		int index = 0;
		for (int r = 0; r < getRows(); r++) {
			for (int c = 0; c < getCols(); c++) {
				result[index++] = this.matrix[r][c];
			}
		}

		return result;
	}

	private void validate(final int row, final int col) {
		if ((row >= getRows()) || (row < 0)) {
			throw new MatrixError("The row:" + row + " is out of range:"
					+ getRows());
		}

		if ((col >= getCols()) || (col < 0)) {
			throw new MatrixError("The col:" + col + " is out of range:"
					+ getCols());
		}
	}

}
