/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.BinaryOperation.BinaryOperationCreator;

/**
 * @author Dennis
 *
 */
public abstract class FunctionNode implements ExpressionTreeNode {
	ExpressionTreeNode[] argumentNodes;
	double[] results;
	
	static {
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("pow",200, 2) {
			@Override protected double compute(double[] arguments) { return Math.pow(arguments[0], arguments[1]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("min",200, 2) {
			@Override protected double compute(double[] arguments) { return Math.min(arguments[0], arguments[1]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("max",200, 2) {
			@Override protected double compute(double[] arguments) { return Math.max(arguments[0], arguments[1]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("exp",200, 1) {
			@Override protected double compute(double[] arguments) { return Math.exp(arguments[0]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("cos",200, 1) {
			@Override protected double compute(double[] arguments) { return Math.cos(arguments[0]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("sin",200, 1) {
			@Override protected double compute(double[] arguments) { return Math.sin(arguments[0]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("acos",200, 1) {
			@Override protected double compute(double[] arguments) { return Math.acos(arguments[0]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("asin",200, 1) {
			@Override protected double compute(double[] arguments) { return Math.asin(arguments[0]);	}	});
		ExpressionTreeFactory.addOperation(new FunctionNodeCreator("tan",200, 1) {
			@Override protected double compute(double[] arguments) { return Math.tan(arguments[0]);	}	});
	}
	
	abstract static class FunctionNodeCreator implements ExpressionTreeNodeCreator {
		private int arguments;
		private int priority;
		private String symbol;
		public FunctionNodeCreator(String symbol, int priority, int arguments) {
			this.priority = priority;
			this.symbol = symbol;
			this.arguments = arguments;
		}
		@Override
		public ExpressionTreeNode createExpressionTreeNode(
				ExpressionTreeNode[] arguments)
				throws IllegalExpressionException {
			if (arguments.length > this.arguments) 
				throw new IllegalExpressionException("Could not evaluate Expression, too many arguments for function "+symbol+"!");
			else if (arguments.length < this.arguments) 
				throw new IllegalExpressionException("Could not evaluate Expression, not enought arguments for function "+symbol+"!");
			return new FunctionNode(arguments) {
				protected double compute(double[] arguments) {
					return FunctionNodeCreator.this.compute(arguments);
				}
			};
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
	public FunctionNode(ExpressionTreeNode[] argumentNodes) {
		this.argumentNodes = argumentNodes;
		this.results = new double[argumentNodes.length];
	}

	public FunctionNode(ExpressionTreeNode argumentA, ExpressionTreeNode argumentB, ExpressionTreeNode argumentC) {
		this(new ExpressionTreeNode[] { argumentA, argumentB, argumentC });
	}
	public FunctionNode(ExpressionTreeNode argumentA, ExpressionTreeNode argumentB) {
		this(new ExpressionTreeNode[] { argumentA, argumentB});
	}
	public FunctionNode(ExpressionTreeNode argumentA) {
		this(new ExpressionTreeNode[] { argumentA });
	}
	public FunctionNode() {
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
