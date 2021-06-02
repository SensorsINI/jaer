/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

/**
 * @author Dennis Goehlsdorf
 *
 */
public interface ExpressionTreeNodeCreator {
	public ExpressionTreeNode createExpressionTreeNode(ExpressionTreeNode[] arguments) throws IllegalExpressionException;
	public int priority();
	public String symbol();
}
