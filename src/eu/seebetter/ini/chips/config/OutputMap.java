/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import java.util.HashMap;

/**
 *
 * @author Christian
 */
public class OutputMap extends HashMap<Integer, Integer> {

    HashMap<Integer, String> nameMap = new HashMap<Integer, String>();

    void put(int k, int v, String name) {
        put(k, v);
        nameMap.put(k, name);
    }

    void put(int k, String name) {
        nameMap.put(k, name);
    }
}
