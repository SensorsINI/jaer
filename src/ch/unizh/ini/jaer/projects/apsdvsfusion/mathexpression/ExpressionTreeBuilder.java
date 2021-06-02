/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * @author Dennis
 *
 */
public class ExpressionTreeBuilder {

	/**
	 * 
	 */
	public ExpressionTreeBuilder() {
		// TODO Auto-generated constructor stub
	}
	
	protected static String parseFunctionArguments(StringTokenizer st, LinkedList<ExpressionTreeNode> result) throws IllegalExpressionException {
//		ArrayList<ExpressionTreeNode> expressions = new ArrayList<ExpressionTreeNode>();
		boolean lastWasComma = true;
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (token.equals(")")) {
				return ")";
//				return (ExpressionTreeNode[])expressions.toArray();
			}
			else if (token.equals(",")) {
				if (lastWasComma)
					throw new IllegalExpressionException("Found two commas in succession or function arguments started with a comma!");
				lastWasComma = true;
			}
			else {
				lastWasComma = false;
			}
		}
		throw new IllegalExpressionException("')' expected!");
		
	}
	
	private static ExpressionTreeNode createExpressionTreeNode(LinkedList<ExpressionTreeNode> expressions, LinkedList<String> operations, int start, int end) throws IllegalExpressionException {
		if (start == end)
			return expressions.get(start);
		else {
			//find most important operation:
			int max = -1;
			int minprio = Integer.MAX_VALUE;
			for (int i = start; i < end; i++) {
				int prio = ExpressionTreeNodeFactory.getOperationPriority(operations.get(i));
				if (prio < minprio) {
					minprio = prio;
					max = i;
				}
			}
			return ExpressionTreeNodeFactory.createBinaryOperationNode(operations.get(max), 
					new ExpressionTreeNode[] {createExpressionTreeNode(expressions,operations, start, max),createExpressionTreeNode(expressions,operations, max+1, end)});
		}
 
	}
	
	private static ExpressionTreeNode negateETN(ExpressionTreeNode etn) {
		final ExpressionTreeNode fetn = etn;
		return new ExpressionTreeNode() {
			@Override
			public double evaluate(HashMap<String, Double> values) {
				return -fetn.evaluate(values);
			}
		};
	}
	
	protected static String parseString(StringTokenizer st, LinkedList<ExpressionTreeNode> result) throws IllegalExpressionException {
		LinkedList<ExpressionTreeNode> expressions = new LinkedList<ExpressionTreeNode>();
		LinkedList<String> operationSymbols = new LinkedList<String>();
		boolean lastTokenWasAnExpression = false;
		boolean fresh = true;
		boolean negateNext = false;
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			// is the token an operation?
			if (ExpressionTreeNodeFactory.existsOperation(token)) {
				if (!lastTokenWasAnExpression) {
					if (token.equals("-"))	
						negateNext = true;
					else throw new IllegalExpressionException("The operation '"+token+"' is missing an argument!");
				}
				else {
					operationSymbols.add(token);
				}
				lastTokenWasAnExpression = false;
			}
			else if (ExpressionTreeNodeFactory.existsFunction(token)) {
				if (!st.hasMoreTokens())
					throw new IllegalExpressionException("Error while parsing arguments of function "+token+"!");
				String next = st.nextToken();
				if (!next.equals("(")) {
					throw new IllegalExpressionException("'(' expected after '"+token+"'!");
				}
				else {
					if (!st.hasMoreTokens())
						throw new IllegalExpressionException("Error while parsing arguments of function "+token+"!");
					LinkedList<ExpressionTreeNode> arguments = new LinkedList<ExpressionTreeNode>();
					String exitChar = parseString(st,arguments);
					while (exitChar != null && exitChar.equals(",")) {
						if (!st.hasMoreTokens())
							throw new IllegalExpressionException("Error while parsing arguments of function "+token+"!");
						exitChar = parseString(st,arguments);
					}
					if (arguments.size() != ExpressionTreeNodeFactory.getFunctionArgumentCount(token))
						throw new IllegalExpressionException("The function '"+token+"' expects "+ExpressionTreeNodeFactory.getFunctionArgumentCount(token)+" arguments instead of "+arguments.size()+"!");
					if (negateNext)
						expressions.add(negateETN(ExpressionTreeNodeFactory.createFunctionNode(token, arguments.toArray(new ExpressionTreeNode[arguments.size()]))));
					else 
						expressions.add(ExpressionTreeNodeFactory.createFunctionNode(token, arguments.toArray(new ExpressionTreeNode[arguments.size()])));
					lastTokenWasAnExpression = true;
					negateNext = false;
				}
			}
			else if (token.equals(",") || token.equals(")")) {
				if (!lastTokenWasAnExpression && !fresh) {
					throw new IllegalExpressionException("Expression ended with a binary operation!");
				}
				// create expression tree from operations and expressions:
				result.add(createExpressionTreeNode(expressions, operationSymbols, 0, operationSymbols.size()));
				return token;
			}
			else if (token.equals("(")) {
				int lengthBefore = expressions.size();
				String exitChar = parseString(st,expressions);
				if (exitChar == null || (!exitChar.equals(")")))
					throw new IllegalExpressionException("')' expected!");
				lastTokenWasAnExpression = true;
				if (negateNext && expressions.size() == lengthBefore+1)
					expressions.add(negateETN(expressions.removeLast()));
				negateNext = false;
			}
			// check for numbers, constants and variables...
			else {
				ExpressionTreeNode e = null;
				try {
					double constant = Double.parseDouble(token);
					e = new ConstantETNode(constant); 
				} catch (NumberFormatException exc) {
					if (ExpressionTreeNodeFactory.existsConstant(token))
						e = ExpressionTreeNodeFactory.getConstant(token);
					else
						e = new VariableETNode(token);
				}
				if (negateNext)
					expressions.add(negateETN(e));
				else 
					expressions.add(e);
				negateNext = false;
				lastTokenWasAnExpression = true;
			}
			fresh = false;
		}
		if (!lastTokenWasAnExpression && !fresh) {
			throw new IllegalExpressionException("Expression ended with a binary operation!");
		}
		result.add(createExpressionTreeNode(expressions, operationSymbols, 0, operationSymbols.size()));
		return null;
	}

	public static ExpressionTreeNode parseString(String s) throws IllegalExpressionException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			// copy whitespaces:
			if (Character.isWhitespace(s.charAt(i)))
				sb.append(" ");
			// make sure brackets are distinct tokens:
			else if (s.charAt(i) == '(' || s.charAt(i) == '[' || s.charAt(i) == '{'  ) {
				sb.append(" ( ");
			} 
			else if (s.charAt(i) == ')' || s.charAt(i) == ']' || s.charAt(i) == '}' ) {
				sb.append(" ) ");
			} 
			else if (s.charAt(i) == ',') {
				sb.append(" , ");
			}
			// separate operation symbols from each other:
			else {
				int endIndex = i+1;
				String part = s.substring(i, endIndex);
				while (ExpressionTreeNodeFactory.matchingOperations(part) > 0 && endIndex <= s.length()) {
					endIndex++;
					part = s.substring(i, endIndex);
				}
				if (ExpressionTreeNodeFactory.matchingOperations(part) == 0) {
					endIndex--;
					part = s.substring(i, endIndex);
				}
				if (ExpressionTreeNodeFactory.matchingOperations(part) == 1) {
					i = endIndex-1;
					sb.append(" "+part+" ");
				}
				else sb.append(s.charAt(i));
			}
		}
		StringTokenizer tokenizer = new StringTokenizer(sb.toString());
		LinkedList<ExpressionTreeNode> result = new LinkedList<ExpressionTreeNode>();
		String exitChar = parseString(tokenizer, result);
		if (exitChar != null)
			throw new IllegalExpressionException("Unexpected ending of expression!");
		else if (result.size() == 0)
			return new ConstantETNode(0);
		else if (result.size() > 1)
			throw new IllegalExpressionException("Found more than one expression!");
		else return result.getFirst();
	}
	
	public static void main(String[] args) {
//		String test = "a*b + c +sin(a)";
//		String test = "a*b + (c +sin(a))";
//		String test = "a*(b) + c +sin(a)";
		String test = "exp(-(b*(-a+c)+100))";
		try {
			ExpressionTreeNode etn = parseString(test);
			HashMap<String, Double> variables = new HashMap<String, Double>();
			variables.put("a", 1.0);
			variables.put("ax", 3.14);
			variables.put("b", 2.0);
			variables.put("c", 3.0);
			System.out.format("Result of this computation was %f!\n", etn.evaluate(variables));
		} catch (IllegalExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
