/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.util;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author matthias
 * 
 * Reads a file and stores the values in a matrix.
 */
public class MatrixParser {
    
    /**
     * Parses the given file. The result is a list of doubles for each line.
     * 
     * @param path The file to parse.
     * @param separator The separator used in the file.
     * @return A list of doubles for each line.
     */
    public static List<List<Double>> parse(String path, String separator) {
        List<String> lines = FileHandler.getInstance(path).readFile();
        
        List<List<Double>> results = new ArrayList<List<Double>>();
        for (String line : lines) {
            String[] values = line.split(separator);
            
            List<Double> result = new ArrayList<Double>();
            for (int i = 0; i < values.length; i++) result.add(Double.parseDouble(values[i]));
            
            results.add(result);
        }
        return results;
    }
}
