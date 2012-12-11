/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface FunctionETNodeCreator extends ExpressionTreeNodeCreator {
	public FunctionETNode createExpressionTreeNode(ExpressionTreeNode[] arguments) throws IllegalExpressionException;
	public int getNumberOfArguments();
}
