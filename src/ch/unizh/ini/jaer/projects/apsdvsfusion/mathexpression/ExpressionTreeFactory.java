/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression;

import java.util.HashMap;

/**
 * @author Dennis
 *
 */
public class ExpressionTreeFactory {

	static HashMap<String, ExpressionTreeNodeCreator> map = new HashMap<String, ExpressionTreeNodeCreator>();
	
	static void addOperation(ExpressionTreeNodeCreator creator) {
		map.put(creator.symbol(), creator);
	}
	/**
	 * 
	 */
	public ExpressionTreeFactory() {
		// TODO Auto-generated constructor stub
	}

}
