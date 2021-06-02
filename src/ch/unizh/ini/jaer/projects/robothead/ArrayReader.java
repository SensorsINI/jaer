/*
 * ArrayReader.java
 *
 * Created on 3. Januar 2008, 03:18
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Reads an array of doubles from a file.
 *
 * @author jaeckeld
 */
public class ArrayReader {
    
    /**
     * Creates a new instance of ArrayReader
     */
    public ArrayReader() {
    }
    public double[][] readArray(String path, String filename){
        /** returns an array of doubles read from the file in filename
         *  the file contains the matrix A saved in MATLAB by:  save A.txt A -ASCII     */
        
        int colNumb;
        int rowNumb;
        
        WordCount wCounter=new WordCount();     // count lines and words to determine array size!
        System.out.print("Loading following File:  ");
        wCounter.count(path+filename); 
        
        rowNumb=wCounter.numLines;
        colNumb=wCounter.numWords/rowNumb;
        
        
        String [][] numbers = new String [rowNumb][colNumb];
        double[][] doubles = new double [rowNumb][colNumb];
        
        
        File file = new File(path+filename);
        BufferedReader bufRdr = null;
        try {
            bufRdr = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
        String line = null;
        
        int row = 0;
        int col = 0;
        
        try {
            //read each line of text file
            
            while((line = bufRdr.readLine()) != null && row < rowNumb)	{
                StringTokenizer st = new StringTokenizer(line," ");
                while (st.hasMoreTokens()){
                    //get next token and store it in the array
                    numbers[row][col] = st.nextToken();
                    col++;
                }
                col = 0;
                row++;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        try {
            bufRdr.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        // convert to double format:
        
        for (int j=0; j<rowNumb; j++){
            for (int i=0; i<colNumb; i++){
                try {
                    double d = Double.valueOf(numbers[j][i].trim()).doubleValue();
                    doubles[j][i]=d;
                    //System.out.println("double d = " + d);
                } catch (NumberFormatException nfe) {
                    System.out.println("NumberFormatException: " + nfe.getMessage());
                }
            }
        }
        if ((rowNumb<10) && (colNumb<10)){
            System.out.println("Read following array: ");
            for (int j=0; j<rowNumb; j++){
                for (int i=0; i<colNumb; i++){
                    System.out.print(doubles[j][i]+" ");
                }
                System.out.println();
            }
            
        }
        return doubles;
    }
}
