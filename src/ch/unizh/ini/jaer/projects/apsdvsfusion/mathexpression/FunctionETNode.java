/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

/**
 * @author Dennis
 *
 */
public abstract class FunctionETNode implements ExpressionTreeNode {
	ExpressionTreeNode[] argumentNodes;
	double[] results;
	
	abstract static class SimpleFunctionETNodeCreator implements FunctionETNodeCreator {
		private int arguments;
		private int priority;
		private String symbol;
		public SimpleFunctionETNodeCreator(String symbol, int priority, int arguments) {
			this.priority = priority;
			this.symbol = symbol;
			this.arguments = arguments;
		}
		@Override
		public FunctionETNode createExpressionTreeNode(
				ExpressionTreeNode[] arguments)
				throws IllegalExpressionException {
			if (arguments.length > this.arguments) 
				throw new IllegalExpressionException("Could not evaluate Expression, too many arguments for function "+symbol+"!");
			else if (arguments.length < this.arguments) 
				throw new IllegalExpressionException("Could not evaluate Expression, not enought arguments for function "+symbol+"!");
			return new FunctionETNode(arguments) {
				protected double compute(double[] arguments) {
					return SimpleFunctionETNodeCreator.this.compute(arguments);
				}
			};
		}
		
		public int getNumberOfArguments() {
			return arguments;
		}
		public int priority() {
			return this.priority;
		}
		public String symbol() {
			return this.symbol;
		}
		protected abstract double compute(double[] arguments);
	}
	/**
	 * 
	 */
	public FunctionETNode(ExpressionTreeNode[] argumentNodes) {
		this.argumentNodes = argumentNodes;
		this.results = new double[argumentNodes.length];
	}

	public FunctionETNode(ExpressionTreeNode argumentA, ExpressionTreeNode argumentB, ExpressionTreeNode argumentC) {
		this(new ExpressionTreeNode[] { argumentA, argumentB, argumentC });
	}
	public FunctionETNode(ExpressionTreeNode argumentA, ExpressionTreeNode argumentB) {
		this(new ExpressionTreeNode[] { argumentA, argumentB});
	}
	public FunctionETNode(ExpressionTreeNode argumentA) {
		this(new ExpressionTreeNode[] { argumentA });
	}
	public FunctionETNode() {
		this(new ExpressionTreeNode[] { });
	}
	
	/* (non-Javadoc)
	 * @see ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTreeNode#evaluate(java.util.HashMap)
	 */
	@Override
	public double evaluate(HashMap<String, Double> values) {
		for (int i = 0; i < argumentNodes.length; i++) {
			results[i] = argumentNodes[i].evaluate(values);
		}
		return compute(results);
	}
	
	abstract protected double compute(double[] arguments);

}
