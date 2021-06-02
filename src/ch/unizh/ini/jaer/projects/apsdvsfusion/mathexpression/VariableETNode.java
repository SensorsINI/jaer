/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

/**
 * @author Dennis Goehlsdorf
 *
 */
public class VariableETNode implements ExpressionTreeNode {

	private String symbol;
	/**
	 * 
	 */
	public VariableETNode(String symbol) {
		this.symbol = symbol;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTreeNode#evaluate(java.util.HashMap)
	 */
	@Override
	public double evaluate(HashMap<String, Double> values) {
		if (values.containsKey(symbol))
			return values.get(symbol);
		else throw new RuntimeException("The variable "+symbol+" was not defined!");
	}

}
