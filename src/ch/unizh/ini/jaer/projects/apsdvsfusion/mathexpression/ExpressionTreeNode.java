/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface ExpressionTreeNode {
	public double evaluate(HashMap<String, Double> values);
}
