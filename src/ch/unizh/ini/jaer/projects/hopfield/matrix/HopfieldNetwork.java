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

import ch.unizh.ini.jaer.projects.hopfield.matrix.exceptions.NeuralNetworkError;


/**
 * HopfieldNetwork: This class implements a Hopfield neural network.
 * A Hopfield neural network is fully connected and consists of a 
 * single layer.  Hopfield neural networks are usually used for 
 * pattern recognition.    
 *  
 * @author Jeff Heaton
 * @version 2.1
 */
public class HopfieldNetwork {

	/**
	 * The weight matrix for this neural network. A Hopfield neural network is a
	 * single layer, fully connected neural network.
	 * 
	 * The inputs and outputs to/from a Hopfield neural network are always
	 * boolean values.
	 */
	private IntMatrix weightMatrix;

	public HopfieldNetwork(final int size) {
		this.weightMatrix = new IntMatrix(size, size);

	}

	/**
	 * Get the weight matrix for this neural network.
	 * 
	 * @return the weight matrix
	 */
	public IntMatrix getMatrix() {
		return this.weightMatrix;
	}

	/**
	 * Get the size of this neural network.
	 * 
	 * @return the size (number of rows)
	 */
	public int getSize() {
		return this.weightMatrix.getRows();
	}

	/**
	 * Present a pattern to the neural network and receive the result.
	 * 
	 * @param pattern
	 *            The pattern to be presented to the neural network.
	 * @return The output from the neural network.
	 * @throws HopfieldException
	 *             The pattern caused a matrix math error.
	 */
	public boolean[] present(final boolean[] pattern) {

		final boolean output[] = new boolean[pattern.length];

		// convert the input pattern into a matrix with a single row.
		// also convert the boolean values to bipolar(-1=false, 1=true)
		final IntMatrix inputMatrix = IntMatrix.createRowMatrix(BiPolarUtil
				.bipolar2int(pattern));

		// Process each value in the pattern
		for (int col = 0; col < pattern.length; col++) {
			IntMatrix columnMatrix = this.weightMatrix.getCol(col);
			columnMatrix = MatrixMath.transpose(columnMatrix);

			// The output for this input element is the dot product of the
			// input matrix and one column from the weight matrix.
			final int dotProduct = MatrixMath.dotProduct(inputMatrix,
					columnMatrix);

			// Convert the dot product to either true or false.
			if (dotProduct > 0) {
				output[col] = true;
			} else {
				output[col] = false;
			}
		}

		return output;
	}

	/**
	 * Train the neural network for the specified pattern. The neural network
	 * can be trained for more than one pattern. To do this simply call the
	 * train method more than once.
	 * 
	 * @param pattern
	 *            The pattern to train on.
	 * @throws HopfieldException
	 *             The pattern size must match the size of this neural network.
	 */
	public void train(final boolean[] pattern) {
		if (pattern.length != this.weightMatrix.getRows()) {
			throw new NeuralNetworkError("Can't train a pattern of size "
					+ pattern.length + " on a hopfield network of size "
					+ this.weightMatrix.getRows());
		}

		// Create a row matrix from the input, convert boolean to bipolar
		final IntMatrix m2 = IntMatrix.createRowMatrix(BiPolarUtil
				.bipolar2int(pattern));
		// Transpose the matrix and multiply by the original input matrix
		final IntMatrix m1 = MatrixMath.transpose(m2);
		final IntMatrix m3 = MatrixMath.multiply(m1, m2);

		// matrix 3 should be square by now, so create an identity
		// matrix of the same size.
		final IntMatrix identity = MatrixMath.identityInt(m3.getRows());

		// subtract the identity matrix
		final IntMatrix m4 = MatrixMath.subtract(m3, identity);

		// now add the calculated matrix, for this pattern, to the
		// existing weight matrix.
		this.weightMatrix = MatrixMath.add(this.weightMatrix, m4);

	}
}