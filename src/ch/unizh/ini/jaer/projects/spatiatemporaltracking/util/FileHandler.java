/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author matthias
 * 
 * Handles various handles to a file and its basic operations.
 */
public class FileHandler {
    
    /** Stores the various file handles. */
    public static Map<String, FileHandler> instances = new HashMap<String, FileHandler>();
    
    /** The path to the file for a particular handle. */
    private String path;
    
    /**
     * Creates a new FileHandler.
     * 
     * @param path The path to the file for the particular handle.
     */
    private FileHandler(String path) {
        this.path = path;
    }
    
    /**
     * Deletes the file at the specified location.
     */
    public void delete() {
        File file = new File(this.path);
        file.delete();
    }
    
    /**
     * Writes the data to the file and adds a new line.
     * 
     * @param data The data to write.
     */
    public void writeLine(String data) {
        File file = new File(this.path);
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(this.path, true));
            out.write(data);
            out.newLine();
            out.close();
        }
        catch(IOException e) {}
     }
    
    /**
     * Writes the data to the file and add for each one a new line.
     * 
     * @param data The data to write.
     */
    public void writeLine(String [] data) {
        for (int i = 0; i < data.length; i++) {
            this.writeLine(data[i]);
        }
    }
    
    /**
     * Writes the data to the file.
     * 
     * @param data The data to write.
     */
    public void write(String data) {
        File file = new File(this.path);
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(this.path, true));
            out.write(data);
            out.close();
        }
        catch(IOException e) {}
    }
    
    /**
     * Reads the file.
     * 
     * @return The content of the file.
     */
    public List<String> readFile() {
        List<String> lines = new ArrayList<String>();
        
        File file = new File(this.path);
        try {
            BufferedReader in = new BufferedReader(new FileReader(this.path));
            while (in.ready()) {
                lines.add(in.readLine());
            }
            in.close();
            return lines;
        } 
        catch (IOException e) {}
        return lines;
    }
    
    /**
     * Gets the file handle corresponding to the given path.
     * 
     * @param path The path of the file corresponding to the required file handle.
     * @return The file handle.
     */
    public static FileHandler getInstance(String path) {
        if (!instances.containsKey(path)) {
            instances.put(path, new FileHandler(path));
        }
        return instances.get(path);
    }
}
