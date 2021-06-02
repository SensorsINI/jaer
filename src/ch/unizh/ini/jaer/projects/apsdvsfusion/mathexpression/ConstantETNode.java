/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

/**
 * @author Dennis
 *
 */
public class ConstantETNode implements ExpressionTreeNode {
	double value;
	/**
	 * 
	 */
	public ConstantETNode(double value) {
		this.value = value;
	}
	@Override
	public double evaluate(HashMap<String, Double> values) {
		return value;
	}

}
