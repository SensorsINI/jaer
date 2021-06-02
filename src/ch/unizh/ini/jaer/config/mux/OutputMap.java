/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.mux;

import java.util.HashMap;

/**
 *
 * @author Christian
 */
public class OutputMap extends HashMap<Integer, Integer> {

	/**
	 *
	 */
	private static final long serialVersionUID = 4190195765688203249L;
	public HashMap<Integer, String> nameMap = new HashMap<Integer, String>();

	public void put(final int k, final int v, final String name) {
		put(k, v);
		nameMap.put(k, name);
	}

	public void put(final int k, final String name) {
		nameMap.put(k, name);
	}
}
