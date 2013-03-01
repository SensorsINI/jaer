/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config;

import java.util.HashMap;

/**
 *
 * @author Christian
 */
public class OutputMap extends HashMap<Integer, Integer> {

    public HashMap<Integer, String> nameMap = new HashMap<Integer, String>();

    public void put(int k, int v, String name) {
        put(k, v);
        nameMap.put(k, name);
    }

    public void put(int k, String name) {
        nameMap.put(k, name);
    }
}
