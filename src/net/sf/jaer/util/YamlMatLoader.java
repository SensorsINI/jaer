package net.sf.jaer.util;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/** Taken from https://stackoverflow.com/questions/34297676/how-to-load-opencv-matrices-saved-with-filestorage-in-java
 * 
 * @author https://stackoverflow.com/users/3303546/cecilia
 */
public class YamlMatLoader {
    
    private static final Logger log = Logger.getLogger("YamlMatLoader");

    // This nested class specifies the expected variables in the file
    // Mat cannot be used directly because it lacks rows and cols variables
    protected static class MatStorage {
        public int rows;
        public int cols;
        public String dt;
        public List<String> data;

        // The empty constructor is required by YamlReader
        public MatStorage() {
        }

        public double[] getData() {
            double[] dataOut = new double[data.size()];
            for (int i = 0; i < dataOut.length; i++) {
                dataOut[i] = Double.parseDouble(data.get(i));
            }

            return dataOut;
        }
    }

    // Loading function
    /** Reads a Mat from a YAML file
     * 
     * @param path path to YAML file
     * @return the Mat
     */
    public Mat getMatYml(String path) {
        try {  
            YamlReader reader = new YamlReader(new FileReader(path));

            // Set the tag "opencv-matrix" to process as MatStorage
            // I'm not sure why the tag is parsed as
            // "tag:yaml.org,2002:opencv-matrix"
            // rather than "opencv-matrix", but I determined this value by
            // debugging
            reader.getConfig().setClassTag("tag:yaml.org,2002:opencv-matrix", MatStorage.class);

            // Read the string
            Map map = (Map) reader.read();

            // In file, the variable name for the Mat is "M"
            MatStorage data = (MatStorage) map.get("M");

            // Create a new Mat to hold the extracted data
            Mat m = new Mat(data.rows, data.cols, CvType.CV_32FC1);
            m.put(0, 0, data.getData());
            return m;
        } catch (FileNotFoundException | YamlException e) {
            log.warning(String.format("Could not load YAML file %s: %s",path,e.toString()));
        }
        return null;
    }
}

