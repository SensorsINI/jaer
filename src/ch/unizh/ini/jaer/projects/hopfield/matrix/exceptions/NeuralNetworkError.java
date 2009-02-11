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
package ch.unizh.ini.jaer.projects.hopfield.matrix.exceptions;

/**
 * NeuralNetworkError: Used by the neural network classes to 
 * indicate an error.
 * 
 * @author Jeff Heaton
 * @version 2.1
 */
public class NeuralNetworkError extends RuntimeException {
	/**
	 * Serial id for this class.
	 */
	private static final long serialVersionUID = 7167228729133120101L;

	/**
	 * Construct a message exception.
	 * 
	 * @param msg
	 *            The exception message.
	 */
	public NeuralNetworkError(final String msg) {
		super(msg);
	}

	/**
	 * Construct an exception that holds another exception.
	 * 
	 * @param t
	 *            The other exception.
	 */
	public NeuralNetworkError(final Throwable t) {
		super(t);
	}
}
