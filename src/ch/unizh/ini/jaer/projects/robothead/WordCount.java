/*
 * WordCount.java
 *
 * Created on 3. Januar 2008, 19:24
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead;

/**
 *  
 *
 * @author jaeckeld
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Command line program to count lines, words and characters
 * in files or from standard input, similar to the wc
 * utility.
 * Run like that: java WordCount FILE1 FILE2 ... or
 * like that: java WordCount < FILENAME.
 * @author Marco Schmidt
 */
public class WordCount {
        public WordCount(){
            
        }
        public int numLines = 0;
        public int numWords = 0;
        public int numChars = 0;
        
        
	/**
	 * Count lines, words and characters in given input stream
	 * and print stream name and those numbers to standard output.
	 * @param name name of input source
	 * @param in stream to be processed
	 * @throws IOException if there were I/O errors
	 */
	public void count(String name, BufferedReader in) throws
	IOException {
                

		String line;
		do {
			line = in.readLine();
			if (line != null)
			{
				numLines++;
				numChars += line.length();
				numWords += countWords(line);
			}
		}
		while (line != null);
		System.out.println(name + "\t" + numLines + "\t" + 
			numWords + "\t" + numChars);
                //out[0]=numLines;
                //out[1]=numWords;
	}

	/**
	 * Open file, count its words, lines and characters 
	 * and print them to standard output.
	 * @param fileName name of file to be processed
	 */
	public void count(String fileName) {
		BufferedReader in = null;
		try {
			FileReader fileReader = new FileReader(fileName);
			in = new BufferedReader(fileReader);
			count(fileName, in);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		}
	}

	/**
	 * Count words, lines and characters of given input stream
	 * and print them to standard output.
	 * @param streamName name of input stream (to print it to stdout)
	 * @param input InputStream to read from
	 */
	public void count(String streamName, InputStream input) {
		try {
			InputStreamReader inputStreamReader = new InputStreamReader(input);
			BufferedReader in = new BufferedReader(inputStreamReader);
			count(streamName, in);
			in.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	/**
	 * Determine the number of words in the argument line.
	 * @param line String to be examined, must be non-null
	 * @return number of words, 0 or higher
	 */
	public long countWords(String line) {
		long numWords = 0;
		int index = 0;
		boolean prevWhitespace = true;
		while (index < line.length()) {
			char c = line.charAt(index++);
			boolean currWhitespace = Character.isWhitespace(c);
			if (prevWhitespace && !currWhitespace) {
				numWords++;
			}
			prevWhitespace = currWhitespace;
		}
		return numWords;
	}

}
