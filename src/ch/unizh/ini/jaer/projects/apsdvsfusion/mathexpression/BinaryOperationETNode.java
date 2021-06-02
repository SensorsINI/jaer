/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

/**
 * @author Dennis
 *
 */
public abstract class BinaryOperationETNode implements ExpressionTreeNode {

	ExpressionTreeNode left, right;
	
	abstract static class SimpleBinaryOperationCreator implements BinaryOperationETNodeCreator {
		String symbol;
		int priority;
		 
		public SimpleBinaryOperationCreator(String symbol, int priority) {
			this.symbol = symbol;
			this.priority = priority;
		}
		@Override
		public BinaryOperationETNode createExpressionTreeNode(
				ExpressionTreeNode[] arguments) throws IllegalExpressionException {
			if (arguments.length > 2) 
				throw new IllegalExpressionException("Could not evaluate Expression, too many arguments for function "+symbol+"!");
			else if (arguments.length < 2) 
				throw new IllegalExpressionException("Could not evaluate Expression, not enought arguments for function "+symbol+"!");
			return new BinaryOperationETNode(arguments[0], arguments[1]) {
				public double compute(double left, double right) {
					return SimpleBinaryOperationCreator.this.compute(left, right);
				}
			};
		}
		public int priority() {
			return this.priority;
		}
		public String symbol() {
			return this.symbol;
		}
		
		public abstract double compute(double left, double right);
	}
	
	
	/**
	 * 
	 */
	public BinaryOperationETNode(ExpressionTreeNode left, ExpressionTreeNode right) {
		this.left = left;
		this.right = right;
	}

	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTree#evaluate(java.util.HashMap)
	 */
	@Override
	public double evaluate(HashMap<String, Double> values) {
		return compute(left.evaluate(values),right.evaluate(values));
	}
	
	public abstract double compute(double left, double right);

}
