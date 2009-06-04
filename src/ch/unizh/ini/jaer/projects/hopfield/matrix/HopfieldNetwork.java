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
import java.util.Vector;

import ch.unizh.ini.jaer.projects.hopfield.matrix.exceptions.NeuralNetworkError;


/**
 * HopfieldNetwork: This class implements a Hopfield neural network.
 * A Hopfield neural network is fully connected and consists of a 
 * single layer.  Hopfield neural networks are usually used for 
 * pattern recognition.    
 *  
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
	private int trainCounter;
	private boolean[][] trainedDatas;

	private double[] baseLikelihood;
	public HopfieldNetwork(final int size) {
		this.weightMatrix = new IntMatrix(size, size);
		trainedDatas = new boolean[5][size];
		baseLikelihood = new double[size];
		trainCounter = 0;
		names = new Vector<String>();
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
	public boolean[] present(boolean[] pattern) {

		final boolean output[] = new boolean[pattern.length];

		// convert the input pattern into a matrix with a single row.
		// also convert the boolean values to bipolar(-1=false, 1=true)
		final IntMatrix inputMatrix = IntMatrix.createRowMatrix(BiPolarUtil
				.bipolar2int(pattern));

		boolean settled = false;
		int iteration_counter = 0;
		// Process each value in the pattern
		while(!settled&&iteration_counter < 64){
			
			settled = true;
			for (int col = 0; col < pattern.length; col++) {
				IntMatrix columnMatrix = this.weightMatrix.getCol(col);
				columnMatrix = MatrixMath.transpose(columnMatrix);

				// The output for this input element is the dot product of the
				// input matrix and one column from the weight matrix.
				final int dotProduct = MatrixMath.dotProduct(inputMatrix,columnMatrix);
				boolean new_value = false;
				// Convert the dot product to either true or false.

				if (dotProduct > 0) {
					new_value = true;
				} else {
					new_value = false;
				}
				if(new_value != pattern[col]){
					output[col] = new_value;
					settled = false;
				}
			}
			pattern = output.clone();
			iteration_counter++;
		}
		return output;
	}
	public double calculateLikeliHood(boolean[] pattern1, boolean[] pattern2){
		int likelihood = 0;
		for(int i = 0; i<(pattern1.length);i++){
			if(pattern1[i] == pattern2[i]){
				likelihood++;
			}
		}
		return (float)likelihood/(float)pattern1.length;
	}
	public double[] classify(final boolean[] pattern) {

		//		boolean calculatedOutput[] = this.present(pattern);
		//double likelihoods[] = new double[trainCounter];
		double classifyResults[] = new double[trainCounter];
		for(int i = 0;i<trainCounter;i++){
			double likelihood = calculateLikeliHood(trainedDatas[i], pattern);
			classifyResults[i] = (likelihood - baseLikelihood[i])/(1-baseLikelihood[i]);
			if(classifyResults[i]<0)
				classifyResults[i] = 0;
			//}
		}
		return classifyResults;
	}

	
	Vector<String> names;
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

	
	public void trainForClassification(final boolean[] pattern, String name) {
		names.add(name);
		trainedDatas[trainCounter] = pattern.clone();

		//calculate possible output for all black!
		boolean all_black[] = new boolean[pattern.length];
		baseLikelihood[trainCounter] = calculateLikeliHood(pattern,all_black)/2;
	
		trainCounter++;
		}
	
	public String getNameOfClass(int classID){
		return names.get(classID);
	}
	public void trainWithImage(final boolean[] pattern,BufferedImage bimage) {
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
		//		this.weightMatrix = MatrixMath.add(this.weightMatrix, m4);

		this.weightMatrix = MatrixMath.addWithOutput(this.weightMatrix, m4, bimage);
	}

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

		//this.weightMatrix = MatrixMath.addWithPrinting(this.weightMatrix, m4, out);
	}
}