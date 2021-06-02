/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

/**
 * @author Dennis
 *
 */
public interface BinaryOperationETNodeCreator extends ExpressionTreeNodeCreator {
	public BinaryOperationETNode createExpressionTreeNode(ExpressionTreeNode[] arguments) throws IllegalExpressionException;
}
